package app.fuelthetrain.nutrition

import app.fuelthetrain.meal.MealEntryDto
import app.fuelthetrain.meal.MealEntryRepository
import app.fuelthetrain.meal.toDto
import app.fuelthetrain.profile.ProfileService
import app.fuelthetrain.workout.Sport
import app.fuelthetrain.workout.WorkoutDto
import app.fuelthetrain.workout.WorkoutRepository
import app.fuelthetrain.workout.toDto
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class Intake(val kcal: Int, val carbsG: Int, val proteinG: Int, val fatG: Int)

data class DayDto(
    val date: LocalDate,
    val workouts: List<WorkoutDto>,
    val targets: DailyTargets,
    val meals: List<MealEntryDto>,
    val eaten: Intake,
)

@RestController
@RequestMapping("/api/days")
class DayController(
    private val profiles: ProfileService,
    private val workouts: WorkoutRepository,
    private val meals: MealEntryRepository,
) {

    @GetMapping
    fun days(@RequestParam from: LocalDate, @RequestParam to: LocalDate): List<DayDto> {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Range must be 0-62 days")
        }
        val timeline = profiles.timeline()
        // Look one day ahead so the day before a race is known even at the end of the range.
        val workoutsByDate = workouts.findByDateBetweenOrderByDate(from, to.plusDays(1)).groupBy { it.date }
        val raceDates = workoutsByDate.values.flatten().filter { it.sport == Sport.EVENT }.map { it.date }.toSet()
        val mealsByDate = meals.findByDateBetweenOrderByDateAscTypeAsc(from, to).groupBy { it.date }

        return from.datesUntil(to.plusDays(1)).map { date ->
            val profile = timeline.at(date)
            val dayWorkouts = workoutsByDate[date].orEmpty()
            val dayMeals = mealsByDate[date].orEmpty()
            DayDto(
                date = date,
                workouts = dayWorkouts.map { it.toDto() },
                targets = NutritionCalculator.dailyTargets(profile, date, dayWorkouts, raceDates),
                meals = dayMeals.map { it.toDto() },
                eaten = Intake(
                    kcal = dayMeals.sumOf { it.kcal }.roundToInt(),
                    carbsG = dayMeals.sumOf { it.carbsG }.roundToInt(),
                    proteinG = dayMeals.sumOf { it.proteinG }.roundToInt(),
                    fatG = dayMeals.sumOf { it.fatG }.roundToInt(),
                ),
            )
        }.toList()
    }
}
