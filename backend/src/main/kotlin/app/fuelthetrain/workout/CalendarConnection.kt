package app.fuelthetrain.workout

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.http.HttpClient

/** The user's private TrainingPeaks calendar link. Single-user app, so one row with id = 1. */
@Entity
class CalendarConnection(
    @Id var id: Long = 1,
    @Column(length = 1000) var icalUrl: String,
)

interface CalendarConnectionRepository : JpaRepository<CalendarConnection, Long>

@Component
class TrainingPeaksCalendarClient {
    // TrainingPeaks redirects the public feed link to its API host.
    private val http = RestClient.builder()
        .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
        .build()

    /** The feed is UTF-8 but doesn't declare a charset, so decode the bytes ourselves. */
    fun fetch(url: String): String =
        http.get().uri(toHttps(url)).retrieve().body(ByteArray::class.java)?.toString(Charsets.UTF_8).orEmpty()

    companion object {
        fun toHttps(url: String) = url.trim().replaceFirst(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")

        /** Only TrainingPeaks links, so the backend can't be pointed at arbitrary hosts. */
        fun isTrainingPeaksLink(url: String): Boolean {
            val uri = runCatching { URI(toHttps(url)) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) &&
                (host == "trainingpeaks.com" || host.endsWith(".trainingpeaks.com"))
        }
    }
}

enum class CalendarSource { APP, ENV }

data class CalendarStatus(val connected: Boolean, val source: CalendarSource?, val maskedUrl: String?, val events: Int? = null)

data class CalendarRequest(val icalUrl: String)

@Service
class CalendarConnectionService(
    private val repository: CalendarConnectionRepository,
    private val client: TrainingPeaksCalendarClient,
    /** Older setups keep the link in .env; the one saved in the app wins. */
    @Value("\${trainingpeaks.ical-url}") private val envUrl: String,
) {

    fun url(): String? = repository.findById(1).map { it.icalUrl }.orElse(null) ?: envUrl.ifBlank { null }

    fun status(): CalendarStatus {
        val saved = repository.findById(1).orElse(null)
        return when {
            saved != null -> CalendarStatus(true, CalendarSource.APP, mask(saved.icalUrl))
            envUrl.isNotBlank() -> CalendarStatus(true, CalendarSource.ENV, mask(envUrl))
            else -> CalendarStatus(false, null, null)
        }
    }

    /** Downloads the calendar once before saving, so a wrong link fails here and not on the next sync. */
    @Transactional
    fun connect(icalUrl: String): CalendarStatus {
        val url = icalUrl.trim()
        if (!TrainingPeaksCalendarClient.isTrainingPeaksLink(url)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a TrainingPeaks calendar link")
        }
        val ics = try {
            client.fetch(url)
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar could not be downloaded", e)
        }
        if (!ics.contains("BEGIN:VCALENDAR")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The link doesn't return an iCal calendar")
        }
        repository.save(CalendarConnection(icalUrl = url))
        return status().copy(events = TrainingPeaksIcalParser.parse(ics).size)
    }

    @Transactional
    fun disconnect() = repository.deleteAll()

    /** The link works without a login, so the full URL never goes back to the browser. */
    private fun mask(url: String) = TrainingPeaksCalendarClient.toHttps(url).let { full ->
        val id = full.substringAfterLast('/')
        full.substringBeforeLast('/') + "/…" + id.takeLast(6)
    }
}

@RestController
@RequestMapping("/api/calendar")
class CalendarConnectionController(private val service: CalendarConnectionService) {

    @GetMapping
    fun status() = service.status()

    @PutMapping
    fun connect(@RequestBody request: CalendarRequest) = service.connect(request.icalUrl)

    @DeleteMapping
    fun disconnect(): CalendarStatus {
        service.disconnect()
        return service.status()
    }
}
