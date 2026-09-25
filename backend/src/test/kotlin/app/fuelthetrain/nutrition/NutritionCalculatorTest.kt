package app.fuelthetrain.nutrition

import app.fuelthetrain.profile.Goal
import app.fuelthetrain.profile.Profile
import app.fuelthetrain.workout.Intensity
import app.fuelthetrain.workout.Sport
import app.fuelthetrain.workout.Workout
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutritionCalculatorTest {

    private val profile = Profile(weightKg = 60.0, heightCm = 170, birthYear = 1990, ftpWatts = 240)
    private val day = LocalDate.of(2026, 9, 26)

    private fun ride(minutes: Int, intensity: Intensity, sport: Sport = Sport.BIKE) = Workout(
        id = 1, externalId = "x", date = day, title = "", sport = sport, plannedMinutes = minutes, intensity = intensity,
    )

    @Test
    fun `bike energy comes from FTP, intensity and duration`() {
        // 240 W * 0.65 * 2 h * 3.6 = 1123 kJ ~ 1123 kcal
        assertEquals(1123, NutritionCalculator.workoutEnergy(profile, ride(120, Intensity.ENDURANCE)).kcal)
    }

    @Test
    fun `rest day gets base carbs`() {
        val targets = NutritionCalculator.dailyTargets(profile, day, emptyList(), emptySet())
        assertEquals(3.0, targets.carbsGPerKg)
        assertEquals(180, targets.carbsG)
        assertEquals(108, targets.proteinG)
        assertEquals(0, targets.workoutKcal)
    }

    @Test
    fun `long hard day gets much more carbs and fuel during the ride`() {
        val targets = NutritionCalculator.dailyTargets(profile, day, listOf(ride(250, Intensity.THRESHOLD)), emptySet())
        assertTrue(targets.carbsGPerKg in 8.0..12.0, "was ${targets.carbsGPerKg}")
        assertEquals(80, targets.workouts.single().carbsPerHourDuring)
    }

    @Test
    fun `day before a race is a carb loading day`() {
        val targets = NutritionCalculator.dailyTargets(profile, day, emptyList(), setOf(day.plusDays(1)))
        assertTrue(targets.dayBeforeRace)
        assertEquals(8.0, targets.carbsGPerKg)
    }

    @Test
    fun `weight loss deficit is skipped on big training days`() {
        val losing = Profile(weightKg = 60.0, heightCm = 170, birthYear = 1990, ftpWatts = 240, goal = Goal.LOSE)
        val easyRide = listOf(ride(60, Intensity.ENDURANCE))
        val easy = NutritionCalculator.dailyTargets(losing, day, easyRide, emptySet())
        val easyMaintain = NutritionCalculator.dailyTargets(profile, day, easyRide, emptySet())
        assertTrue(easyMaintain.kcal - easy.kcal in 299..301, "difference was ${easyMaintain.kcal - easy.kcal}")

        val hard = listOf(ride(250, Intensity.THRESHOLD))
        assertEquals(
            NutritionCalculator.dailyTargets(profile, day, hard, emptySet()).kcal,
            NutritionCalculator.dailyTargets(losing, day, hard, emptySet()).kcal,
        )
    }

    @Test
    fun `deficit never pushes fat below minimum`() {
        val losing = Profile(weightKg = 60.0, heightCm = 170, birthYear = 1990, ftpWatts = 240, goal = Goal.LOSE)
        assertEquals(48, NutritionCalculator.dailyTargets(losing, day, emptyList(), emptySet()).fatG)
    }

    @Test
    fun `fat never drops below minimum`() {
        val targets = NutritionCalculator.dailyTargets(profile, day, emptyList(), setOf(day.plusDays(1)))
        assertTrue(targets.fatG >= 48)
    }
}
