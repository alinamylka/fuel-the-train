package app.fuelthetrain.meal

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

enum class MealType { BREAKFAST, SNACK, LUNCH, PRE_WORKOUT, DURING_WORKOUT, POST_WORKOUT, DINNER }

/** What the user actually ate. Planned meals from the AI will live next to these later. */
@Entity
class MealEntry(
    @Id @GeneratedValue var id: Long? = null,
    var date: LocalDate,
    @Enumerated(EnumType.STRING) var type: MealType,
    var description: String,
    var kcal: Double,
    var carbsG: Double,
    var proteinG: Double,
    var fatG: Double,
)

data class MealEntryDto(
    val id: Long? = null,
    val date: LocalDate,
    val type: MealType,
    @field:NotBlank val description: String,
    @field:PositiveOrZero val kcal: Double,
    @field:PositiveOrZero val carbsG: Double,
    @field:PositiveOrZero val proteinG: Double,
    @field:PositiveOrZero val fatG: Double,
)

fun MealEntry.toDto() = MealEntryDto(id, date, type, description, kcal, carbsG, proteinG, fatG)

interface MealEntryRepository : JpaRepository<MealEntry, Long> {
    fun findByDateBetweenOrderByDateAscTypeAsc(from: LocalDate, to: LocalDate): List<MealEntry>
}

@RestController
@RequestMapping("/api/meals")
class MealEntryController(private val repository: MealEntryRepository) {

    @GetMapping
    fun list(@RequestParam from: LocalDate, @RequestParam to: LocalDate) =
        repository.findByDateBetweenOrderByDateAscTypeAsc(from, to).map { it.toDto() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody dto: MealEntryDto) = repository.save(
        MealEntry(
            date = dto.date, type = dto.type, description = dto.description,
            kcal = dto.kcal, carbsG = dto.carbsG, proteinG = dto.proteinG, fatG = dto.fatG,
        ),
    ).toDto()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody dto: MealEntryDto): MealEntryDto {
        val meal = repository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        meal.date = dto.date
        meal.type = dto.type
        meal.description = dto.description
        meal.kcal = dto.kcal
        meal.carbsG = dto.carbsG
        meal.proteinG = dto.proteinG
        meal.fatG = dto.fatG
        return repository.save(meal).toDto()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = repository.deleteById(id)
}
