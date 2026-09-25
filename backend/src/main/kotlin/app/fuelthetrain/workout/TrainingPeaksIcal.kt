package app.fuelthetrain.workout

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One VEVENT from the TrainingPeaks calendar feed, before intensity classification. */
data class IcalWorkout(
    val uid: String,
    val date: LocalDate,
    val title: String,
    val description: String,
    val sport: Sport,
    val plannedMinutes: Int?,
    val actualMinutes: Int?,
    val actualDistanceKm: Double?,
)

/**
 * Parses the TrainingPeaks iCal feed. The feed only has title, description and a few
 * "Key: value" lines inside the description (Workout type, Planned Time, Actual Time, ...).
 */
object TrainingPeaksIcalParser {

    private val basicDate = DateTimeFormatter.BASIC_ISO_DATE
    private val workoutType = Regex("""^Workout type: (.+)$""", RegexOption.MULTILINE)
    private val plannedTime = Regex("""Planned Time: (\d+):(\d{2})""")
    private val actualTime = Regex("""Actual Time: (\d+):(\d{2})""")
    private val actualDistance = Regex("""Actual Distance: ([\d.]+) km""")
    private val metaLine = Regex("""^(Workout type|Planned Time|Actual Time|Actual Distance|Speed|TSS|IF):.*$""")

    fun parse(ics: String): List<IcalWorkout> =
        unfold(ics).split("BEGIN:VEVENT").drop(1).mapNotNull { parseEvent(it.substringBefore("END:VEVENT")) }

    /** iCal wraps long lines; a line starting with a space continues the previous one. */
    private fun unfold(ics: String) = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")

    private fun parseEvent(block: String): IcalWorkout? {
        val props = block.lines()
            .filter { it.contains(':') }
            .associate { line -> line.substringBefore(':').substringBefore(';') to line.substringAfter(':') }
        val uid = props["UID"] ?: return null
        val start = props["DTSTART"] ?: return null
        val description = unescape(props["DESCRIPTION"].orEmpty())
        val type = workoutType.find(description)?.groupValues?.get(1)?.trim()

        return IcalWorkout(
            uid = uid,
            date = LocalDate.parse(start.take(8), basicDate),
            title = unescape(props["SUMMARY"].orEmpty()).trim(),
            description = coachText(description),
            sport = sportOf(type),
            plannedMinutes = plannedTime.find(description)?.let(::toMinutes),
            actualMinutes = actualTime.find(description)?.let(::toMinutes),
            actualDistanceKm = actualDistance.find(description)?.groupValues?.get(1)?.toDoubleOrNull(),
        )
    }

    /** Description without the generated "Key: value" lines, i.e. only what the coach wrote. */
    private fun coachText(description: String) =
        description.lines().filterNot { metaLine.matches(it.trim()) }.joinToString("\n").trim()

    private fun toMinutes(match: MatchResult) =
        match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()

    private fun unescape(value: String) =
        value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** Events without a workout type are races and other calendar events. */
    private fun sportOf(type: String?) = when (type?.lowercase()) {
        null -> Sport.EVENT
        "bike", "mtb", "cyclocross" -> Sport.BIKE
        "run" -> Sport.RUN
        "swim" -> Sport.SWIM
        "walk", "hiking" -> Sport.WALK
        "strength" -> Sport.STRENGTH
        "day off" -> Sport.DAY_OFF
        else -> Sport.OTHER
    }
}
