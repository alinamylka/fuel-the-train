package app.fuelthetrain.workout

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import kotlin.math.roundToInt

/** A recorded session from intervals.icu (usually uploaded by Garmin Connect). */
@Entity
class Activity(
    /** intervals.icu activity id, e.g. "i123456789". */
    @Id var id: String,
    var date: LocalDate,
    @Enumerated(EnumType.STRING) var sport: Sport,
    var name: String,
    var movingMinutes: Int,
    /** Null without a power meter (strength, most runs). */
    var kj: Int?,
    var averageWatts: Int?,
    /** Whole-session intensity factor as a fraction of FTP, e.g. 0.67. */
    var intensityFactor: Double?,
)

interface ActivityRepository : JpaRepository<Activity, String>

/**
 * Links recorded activities to the plan. All activities of one sport on one day count as one session:
 * a race day with warm-up, race and cool-down is three activities but one planned race.
 */
object ActivityMatcher {

    /** What the matcher decided for one day and sport. */
    sealed interface Match {
        val activities: List<Activity>

        /** The planned workout gets the recorded totals; other planned sessions of that sport that day count as done within it. */
        data class Planned(val workout: Workout, val alsoCovered: List<Workout>, override val activities: List<Activity>) : Match

        /** Nothing was planned, so the activities become a workout of their own. */
        data class Unplanned(val date: LocalDate, val sport: Sport, override val activities: List<Activity>) : Match
    }

    fun match(activities: List<Activity>, workouts: List<Workout>): List<Match> {
        val planned = workouts.filterNot { it.unplanned }.groupBy { it.date }
        return activities.groupBy { it.date to it.sport }.map { (key, group) ->
            val (date, sport) = key
            val candidates = planned[date].orEmpty().filter { compatible(it.sport, sport) }
            if (candidates.isEmpty()) {
                Match.Unplanned(date, sport, group.sortedBy { it.id })
            } else {
                val target = candidates.maxBy { score(it, group) }
                Match.Planned(target, candidates - target, group.sortedBy { it.id })
            }
        }
    }

    /** A race from the calendar has no sport of its own, so any endurance activity that day belongs to it. */
    private fun compatible(planned: Sport, recorded: Sport) =
        planned == recorded || (planned == Sport.EVENT && recorded in setOf(Sport.BIKE, Sport.RUN, Sport.SWIM))

    /** Prefer the race, then the plan whose title Garmin copied into the activity name. */
    private fun score(workout: Workout, activities: List<Activity>): Int {
        if (workout.sport == Sport.EVENT) return 1000
        val title = workout.title.substringAfter(": ").lowercase()
        return activities.maxOf { commonWords(title, it.name.lowercase()) }
    }

    private fun commonWords(a: String, b: String): Int {
        val words = { s: String -> s.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 2 }.toSet() }
        return (words(a) intersect words(b)).size
    }

    fun sportOf(intervalsType: String?): Sport = when (intervalsType) {
        "Ride", "VirtualRide", "GravelRide", "MountainBikeRide", "EBikeRide", "TrackRide", "Velomobile" -> Sport.BIKE
        "Run", "VirtualRun", "TrailRun" -> Sport.RUN
        "Swim", "OpenWaterSwim" -> Sport.SWIM
        "Walk", "Hike" -> Sport.WALK
        "WeightTraining", "Workout", "Crossfit", "Yoga", "Pilates" -> Sport.STRENGTH
        else -> Sport.OTHER
    }

    /** Rough intensity for an unplanned session, from its whole-session intensity factor. */
    fun intensityOf(intensityFactor: Double?): Intensity = when {
        intensityFactor == null -> Intensity.ENDURANCE
        intensityFactor < 0.60 -> Intensity.RECOVERY
        intensityFactor < 0.72 -> Intensity.ENDURANCE
        intensityFactor < 0.80 -> Intensity.TEMPO
        else -> Intensity.THRESHOLD
    }

    fun totalKj(activities: List<Activity>): Int? =
        activities.mapNotNull { it.kj }.takeIf { it.isNotEmpty() }?.sum()

    fun totalMinutes(activities: List<Activity>) = activities.sumOf { it.movingMinutes }

    fun averageIntensityFactor(activities: List<Activity>): Double? {
        val weighted = activities.filter { it.intensityFactor != null && it.movingMinutes > 0 }
        if (weighted.isEmpty()) return null
        val minutes = weighted.sumOf { it.movingMinutes }
        return ((weighted.sumOf { it.intensityFactor!! * it.movingMinutes } / minutes) * 100).roundToInt() / 100.0
    }
}
