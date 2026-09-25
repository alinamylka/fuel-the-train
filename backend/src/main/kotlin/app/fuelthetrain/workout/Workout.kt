package app.fuelthetrain.workout

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

enum class Sport { BIKE, RUN, SWIM, WALK, STRENGTH, DAY_OFF, EVENT, OTHER }

/** Ordered from easiest to hardest. */
enum class Intensity { REST, RECOVERY, ENDURANCE, TEMPO, THRESHOLD, VO2MAX, RACE }

@Entity
class Workout(
    @Id @GeneratedValue var id: Long? = null,
    /** UID from the TrainingPeaks calendar, used to update entries on re-sync. */
    @Column(unique = true) var externalId: String,
    var date: LocalDate,
    var title: String,
    @Column(length = 8000) var description: String = "",
    @Enumerated(EnumType.STRING) var sport: Sport,
    var plannedMinutes: Int? = null,
    var actualMinutes: Int? = null,
    var actualDistanceKm: Double? = null,
    @Enumerated(EnumType.STRING) var intensity: Intensity,
    /** Set when the user corrected the intensity by hand, so a re-sync keeps it. */
    var intensityOverridden: Boolean = false,
    /** Mechanical work recorded by the power meter (intervals.icu), summed over the day's matching activities. */
    var actualKj: Int? = null,
) {
    /** Created from an intervals.icu activity that had no planned workout. */
    val unplanned: Boolean get() = externalId.startsWith(UNPLANNED_PREFIX)

    val minutes: Int get() = actualMinutes ?: plannedMinutes ?: 0
    val completed: Boolean get() = actualMinutes != null

    companion object {
        const val UNPLANNED_PREFIX = "intervals:"
    }
}

interface WorkoutRepository : JpaRepository<Workout, Long> {
    fun findByExternalId(externalId: String): Workout?
    fun findByDateBetweenOrderByDate(from: LocalDate, to: LocalDate): List<Workout>
    fun findByDateIn(dates: Collection<LocalDate>): List<Workout>
    fun findByExternalIdStartingWith(prefix: String): List<Workout>
}
