package app.fuelthetrain.workout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Base64
import kotlin.math.roundToInt

/** The user's intervals.icu API key. Single-user app, so one row with id = 1. */
@Entity
class IntervalsConnection(
    @Id var id: Long = 1,
    var apiKey: String,
    var athleteName: String?,
)

interface IntervalsConnectionRepository : JpaRepository<IntervalsConnection, Long>

@JsonIgnoreProperties(ignoreUnknown = true)
data class IntervalsAthlete(val id: String?, val name: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IntervalsActivity(
    val id: String,
    @JsonProperty("start_date_local") val startDateLocal: String,
    val type: String?,
    val name: String?,
    @JsonProperty("moving_time") val movingTime: Int?,
    @JsonProperty("icu_joules") val joules: Double?,
    @JsonProperty("icu_average_watts") val averageWatts: Double?,
    /** Percent of FTP, e.g. 67.1. */
    @JsonProperty("icu_intensity") val intensity: Double?,
)

@Component
class IntervalsClient {
    private val http = RestClient.create("https://intervals.icu/api/v1")

    /** Athlete id 0 means "the owner of the API key", so the user only needs the key. */
    fun athlete(apiKey: String): IntervalsAthlete =
        http.get().uri("/athlete/0").header("Authorization", auth(apiKey))
            .retrieve().body(IntervalsAthlete::class.java) ?: IntervalsAthlete(null, null)

    fun activities(apiKey: String, oldest: LocalDate, newest: LocalDate): List<Activity> =
        http.get().uri("/athlete/0/activities?oldest={oldest}&newest={newest}", oldest, newest)
            .header("Authorization", auth(apiKey))
            .retrieve().body(Array<IntervalsActivity>::class.java).orEmpty()
            .map { it.toActivity() }

    private fun IntervalsActivity.toActivity() = Activity(
        id = id,
        date = LocalDate.parse(startDateLocal.take(10)),
        sport = ActivityMatcher.sportOf(type),
        name = name.orEmpty(),
        movingMinutes = ((movingTime ?: 0) / 60.0).roundToInt(),
        kj = joules?.let { (it / 1000).roundToInt() },
        averageWatts = averageWatts?.roundToInt(),
        intensityFactor = intensity?.let { (it / 100 * 100).roundToInt() / 100.0 },
    )

    /** intervals.icu uses basic auth with the literal user name "API_KEY". */
    private fun auth(apiKey: String) =
        "Basic " + Base64.getEncoder().encodeToString("API_KEY:$apiKey".toByteArray())
}

data class IntervalsStatus(val connected: Boolean, val source: CalendarSource?, val athleteName: String?)

data class IntervalsRequest(val apiKey: String)

@Service
class IntervalsConnectionService(
    private val repository: IntervalsConnectionRepository,
    private val client: IntervalsClient,
    @Value("\${intervals.api-key:}") private val envKey: String,
) {

    fun apiKey(): String? = repository.findById(1).map { it.apiKey }.orElse(null) ?: envKey.ifBlank { null }

    fun status(): IntervalsStatus {
        val saved = repository.findById(1).orElse(null)
        return when {
            saved != null -> IntervalsStatus(true, CalendarSource.APP, saved.athleteName)
            envKey.isNotBlank() -> IntervalsStatus(true, CalendarSource.ENV, null)
            else -> IntervalsStatus(false, null, null)
        }
    }

    /** Calls intervals.icu once before saving, so a wrong key fails here and not on the next sync. */
    @Transactional
    fun connect(apiKey: String): IntervalsStatus {
        val key = apiKey.trim()
        if (key.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key is empty")
        val athlete = try {
            client.athlete(key)
        } catch (e: HttpClientErrorException.Unauthorized) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid API key", e)
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "intervals.icu is not reachable", e)
        }
        repository.save(IntervalsConnection(apiKey = key, athleteName = athlete.name))
        return status()
    }

    @Transactional
    fun disconnect() = repository.deleteAll()
}

@Service
class ActivitySyncService(
    private val connection: IntervalsConnectionService,
    private val client: IntervalsClient,
    private val activities: ActivityRepository,
    private val workouts: WorkoutRepository,
) {

    /** Downloads recent activities and links them to the plan. Returns the number of activities fetched. */
    @Transactional
    fun sync(days: Long = SYNC_DAYS): Int? {
        val key = connection.apiKey() ?: return null
        val today = LocalDate.now()
        val fetched = client.activities(key, today.minusDays(days), today)
        activities.saveAll(fetched)
        return fetched.size
    }

    /**
     * Rebuilds the link between activities and workouts from scratch, so it stays right
     * after either calendar or activities change. The calendar sync resets actual minutes,
     * so this always runs after it.
     */
    @Transactional
    fun reconcile() {
        val all = activities.findAll()
        val onActivityDays = workouts.findByDateIn(all.map { it.date }.toSet())
        onActivityDays.filterNot { it.unplanned }.forEach { it.actualKj = null }

        val kept = mutableSetOf<String>()
        for (match in ActivityMatcher.match(all, onActivityDays)) {
            val kj = ActivityMatcher.totalKj(match.activities)
            val minutes = ActivityMatcher.totalMinutes(match.activities)
            when (match) {
                is ActivityMatcher.Match.Planned -> {
                    match.workout.actualKj = kj
                    match.workout.actualMinutes = minutes
                    // E.g. the planned warm-up on race day: already recorded as part of the race.
                    match.alsoCovered.forEach {
                        it.actualKj = 0
                        it.actualMinutes = 0
                    }
                }
                is ActivityMatcher.Match.Unplanned -> {
                    val externalId = "${Workout.UNPLANNED_PREFIX}${match.date}:${match.sport}"
                    val workout = onActivityDays.find { it.externalId == externalId } ?: Workout(
                        externalId = externalId, date = match.date, title = "", sport = match.sport,
                        intensity = Intensity.ENDURANCE,
                    )
                    workout.title = match.activities.joinToString(" + ") { it.name }.ifBlank { match.sport.name }
                    workout.actualMinutes = minutes
                    workout.actualKj = kj
                    if (!workout.intensityOverridden) {
                        workout.intensity = ActivityMatcher.intensityOf(ActivityMatcher.averageIntensityFactor(match.activities))
                    }
                    workouts.save(workout)
                    kept += externalId
                }
            }
        }
        // A plan added later for that day, or a deleted activity, makes the stand-in workout obsolete.
        workouts.deleteAll(workouts.findByExternalIdStartingWith(Workout.UNPLANNED_PREFIX).filter { it.externalId !in kept })
    }

    companion object {
        const val SYNC_DAYS = 90L
    }
}

@RestController
@RequestMapping("/api/intervals")
class IntervalsConnectionController(private val service: IntervalsConnectionService) {

    @GetMapping
    fun status() = service.status()

    @PutMapping
    fun connect(@RequestBody request: IntervalsRequest) = service.connect(request.apiKey)

    @DeleteMapping
    fun disconnect(): IntervalsStatus {
        service.disconnect()
        return service.status()
    }
}
