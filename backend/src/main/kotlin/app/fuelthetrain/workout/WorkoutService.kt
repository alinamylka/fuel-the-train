package app.fuelthetrain.workout

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/** [activities] is null when intervals.icu isn't connected. */
data class SyncResult(val created: Int, val updated: Int, val activities: Int? = null)

@Service
class WorkoutService(
    private val repository: WorkoutRepository,
    private val calendar: CalendarConnectionService,
    private val client: TrainingPeaksCalendarClient,
    private val activitySync: ActivitySyncService,
) {

    fun between(from: LocalDate, to: LocalDate) = repository.findByDateBetweenOrderByDate(from, to)

    /** Plan from TrainingPeaks, then recorded activities from intervals.icu, whichever is connected. */
    @Transactional
    fun sync(): SyncResult {
        val url = calendar.url()
        val planned = url?.let { import(TrainingPeaksIcalParser.parse(client.fetch(it))) }
        val activities = activitySync.sync()
        if (planned == null && activities == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Nothing connected to sync from")
        }
        activitySync.reconcile()
        return SyncResult(planned?.created ?: 0, planned?.updated ?: 0, activities)
    }

    /**
     * TrainingPeaks gives every event a new UID on each download, so workouts are recognised by
     * day, sport and title instead. The feed is the whole plan for the days it covers: planned
     * workouts in that range that are no longer in it were removed by the coach and are deleted.
     */
    @Transactional
    fun import(entries: List<IcalWorkout>): SyncResult {
        if (entries.isEmpty()) return SyncResult(0, 0)
        val from = entries.minOf { it.date }
        val to = entries.maxOf { it.date }
        val existing = repository.findByDateBetweenOrderByDate(from, to).filterNot { it.unplanned }
            .sortedBy { it.id }.withPlanKeys { it.date to (it.sport to it.title) }.toMap()

        var created = 0
        var updated = 0
        val seen = mutableSetOf<String>()
        for ((key, entry) in entries.withPlanKeys { it.date to (it.sport to it.title) }) {
            val workout = existing[key]?.also { updated++ } ?: Workout(
                externalId = key, date = entry.date, title = entry.title, sport = entry.sport, intensity = Intensity.ENDURANCE,
            ).also { created++ }
            seen += key

            workout.externalId = key
            workout.description = entry.description
            workout.plannedMinutes = entry.plannedMinutes
            workout.actualMinutes = entry.actualMinutes
            workout.actualDistanceKm = entry.actualDistanceKm
            if (!workout.intensityOverridden) {
                workout.intensity = IntensityClassifier.classify(entry.sport, entry.title, entry.description)
            }
            repository.save(workout)
        }
        repository.deleteAll(existing.filterKeys { it !in seen }.values)
        return SyncResult(created, updated)
    }

    /** "2026-09-22|BIKE|Bike: TT…|0"; the counter keeps two identical sessions on one day apart. */
    private fun <T> List<T>.withPlanKeys(fields: (T) -> Pair<LocalDate, Pair<Sport, String>>): List<Pair<String, T>> {
        val counts = mutableMapOf<String, Int>()
        return map { item ->
            val (date, rest) = fields(item)
            val base = "$date|${rest.first}|${rest.second}"
            val n = counts.merge(base, 1, Int::plus)!! - 1
            "$base|$n".take(255) to item
        }
    }

    @Transactional
    fun overrideIntensity(id: Long, intensity: Intensity): Workout {
        val workout = repository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        workout.intensity = intensity
        workout.intensityOverridden = true
        return repository.save(workout)
    }
}

data class WorkoutDto(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val description: String,
    val sport: Sport,
    val plannedMinutes: Int?,
    val actualMinutes: Int?,
    val actualDistanceKm: Double?,
    val intensity: Intensity,
    val intensityOverridden: Boolean,
    val actualKj: Int?,
    val unplanned: Boolean,
)

fun Workout.toDto() = WorkoutDto(
    id!!, date, title, description, sport, plannedMinutes, actualMinutes, actualDistanceKm, intensity, intensityOverridden,
    actualKj, unplanned,
)

data class IntensityRequest(val intensity: Intensity)

@RestController
@RequestMapping("/api/workouts")
class WorkoutController(private val service: WorkoutService) {

    @GetMapping
    fun list(@RequestParam from: LocalDate, @RequestParam to: LocalDate) = service.between(from, to).map { it.toDto() }

    @PostMapping("/sync")
    fun sync() = service.sync()

    @PutMapping("/{id}/intensity")
    fun overrideIntensity(@PathVariable id: Long, @RequestBody request: IntensityRequest) =
        service.overrideIntensity(id, request.intensity).toDto()
}
