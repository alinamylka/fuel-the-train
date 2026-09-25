package app.fuelthetrain.nutrition

import app.fuelthetrain.profile.Goal
import app.fuelthetrain.profile.Profile
import app.fuelthetrain.profile.Sex
import app.fuelthetrain.workout.Intensity
import app.fuelthetrain.workout.Sport
import app.fuelthetrain.workout.Workout
import java.time.LocalDate
import kotlin.math.roundToInt

data class WorkoutEnergy(val workoutId: Long?, val kcal: Int, val carbsPerHourDuring: Int)

data class DailyTargets(
    val kcal: Int,
    val carbsG: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsGPerKg: Double,
    val workoutKcal: Int,
    val workouts: List<WorkoutEnergy>,
    val raceDay: Boolean,
    val dayBeforeRace: Boolean,
)

/**
 * Deterministic daily targets from the profile and the day's workouts.
 * All numbers are estimates based on common sports nutrition guidelines,
 * the AI only turns these targets into meals, it never does the math.
 */
object NutritionCalculator {

    /** Average intensity factor (share of FTP) over a whole session, warm-up and cool-down included. */
    private val intensityFactor = mapOf(
        Intensity.REST to 0.0,
        Intensity.RECOVERY to 0.55,
        Intensity.ENDURANCE to 0.65,
        Intensity.TEMPO to 0.72,
        Intensity.THRESHOLD to 0.75,
        Intensity.VO2MAX to 0.72,
        Intensity.RACE to 0.85,
    )

    /** Share of workout energy that comes from carbohydrate. */
    private val carbShare = mapOf(
        Intensity.REST to 0.0,
        Intensity.RECOVERY to 0.4,
        Intensity.ENDURANCE to 0.55,
        Intensity.TEMPO to 0.7,
        Intensity.THRESHOLD to 0.8,
        Intensity.VO2MAX to 0.85,
        Intensity.RACE to 0.85,
    )

    private const val BASE_CARBS_G_PER_KG = 3.0
    private const val MIN_FAT_G_PER_KG = 0.8
    private const val NON_EXERCISE_ACTIVITY = 1.4
    private const val GOAL_ADJUSTMENT_KCAL = 300

    fun dailyTargets(profile: Profile, date: LocalDate, workouts: List<Workout>, raceDates: Set<LocalDate>): DailyTargets {
        val weight = profile.weightKg
        val raceDay = date in raceDates || workouts.any { it.intensity == Intensity.RACE }
        val dayBeforeRace = date.plusDays(1) in raceDates

        val energies = workouts.map { workoutEnergy(profile, it) }
        val workoutKcal = energies.sumOf { it.kcal }
        val workoutCarbs = workouts.zip(energies).sumOf { (w, e) -> e.kcal * carbShare.getValue(w.intensity) / 4 }

        var carbsPerKg = (BASE_CARBS_G_PER_KG + workoutCarbs / weight).coerceIn(3.0, 12.0)
        if (dayBeforeRace) carbsPerKg = maxOf(carbsPerKg, 8.0)
        if (raceDay) carbsPerKg = maxOf(carbsPerKg, 7.0)

        val carbsG = carbsPerKg * weight
        val proteinG = profile.proteinGPerKg * weight

        // No deficit around races or on big days, under-fuelling there costs more than it saves.
        val adjustment = when {
            raceDay || dayBeforeRace -> 0
            profile.goal == Goal.LOSE && carbsPerKg < 7 -> -GOAL_ADJUSTMENT_KCAL
            profile.goal == Goal.GAIN -> GOAL_ADJUSTMENT_KCAL
            else -> 0
        }
        val energyKcal = restingKcal(profile, date) * NON_EXERCISE_ACTIVITY + workoutKcal + adjustment
        val minFatG = MIN_FAT_G_PER_KG * weight
        val fatG = maxOf((energyKcal - carbsG * 4 - proteinG * 4) / 9, minFatG)
        val kcal = carbsG * 4 + proteinG * 4 + fatG * 9

        return DailyTargets(
            kcal = kcal.roundToInt(),
            carbsG = carbsG.roundToInt(),
            proteinG = proteinG.roundToInt(),
            fatG = fatG.roundToInt(),
            carbsGPerKg = (carbsPerKg * 10).roundToInt() / 10.0,
            workoutKcal = workoutKcal,
            workouts = energies,
            raceDay = raceDay,
            dayBeforeRace = dayBeforeRace,
        )
    }

    /**
     * On the bike 1 kJ of mechanical work is roughly 1 kcal burned
     * (gross efficiency ~24% cancels out the 4.184 kJ/kcal conversion).
     */
    fun workoutEnergy(profile: Profile, workout: Workout): WorkoutEnergy {
        val hours = workout.minutes / 60.0
        val kcal = when (workout.sport) {
            Sport.BIKE, Sport.EVENT -> workout.actualKj?.toDouble() ?: run {
                val ftp = profile.ftpWatts?.toDouble() ?: (profile.weightKg * 3.0)
                ftp * intensityFactor.getValue(workout.intensity) * hours * 3.6
            }
            Sport.RUN -> profile.weightKg * hours * runKcalPerKgHour(workout.intensity)
            Sport.SWIM -> profile.weightKg * hours * 7.0
            Sport.WALK -> profile.weightKg * hours * 3.5
            Sport.STRENGTH -> profile.weightKg * hours * 5.0
            Sport.DAY_OFF, Sport.OTHER -> 0.0
        }
        return WorkoutEnergy(workout.id, kcal.roundToInt(), carbsPerHourDuring(workout))
    }

    private fun runKcalPerKgHour(intensity: Intensity) = when (intensity) {
        Intensity.REST, Intensity.RECOVERY -> 7.0
        Intensity.ENDURANCE -> 9.0
        Intensity.TEMPO -> 10.5
        else -> 12.0
    }

    /** Carbs to take in during the session, g/h. Short sessions don't need any. */
    private fun carbsPerHourDuring(workout: Workout): Int = when {
        workout.minutes < 75 && workout.intensity != Intensity.RACE -> 0
        workout.intensity == Intensity.RACE -> 90
        workout.intensity >= Intensity.THRESHOLD -> 80
        workout.intensity == Intensity.TEMPO || workout.minutes >= 150 -> 60
        else -> 40
    }

    /** Mifflin-St Jeor. */
    private fun restingKcal(profile: Profile, date: LocalDate): Double {
        val age = date.year - profile.birthYear
        val base = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * age
        return if (profile.sex == Sex.FEMALE) base - 161 else base + 5
    }
}
