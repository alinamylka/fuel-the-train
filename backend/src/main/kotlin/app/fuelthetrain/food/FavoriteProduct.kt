package app.fuelthetrain.food

import jakarta.persistence.Entity
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** A product the user likes to eat, with macros per 100 g. The AI prefers these when planning meals. */
@Entity
class FavoriteProduct(
    @Id @GeneratedValue var id: Long? = null,
    var name: String,
    var kcalPer100g: Double,
    var carbsPer100g: Double,
    var proteinPer100g: Double,
    var fatPer100g: Double,
    var notes: String = "",
)

data class FavoriteProductDto(
    val id: Long? = null,
    @field:NotBlank val name: String,
    @field:PositiveOrZero val kcalPer100g: Double,
    @field:PositiveOrZero val carbsPer100g: Double,
    @field:PositiveOrZero val proteinPer100g: Double,
    @field:PositiveOrZero val fatPer100g: Double,
    val notes: String = "",
)

fun FavoriteProduct.toDto() =
    FavoriteProductDto(id, name, kcalPer100g, carbsPer100g, proteinPer100g, fatPer100g, notes)

interface FavoriteProductRepository : JpaRepository<FavoriteProduct, Long> {
    fun findAllByOrderByName(): List<FavoriteProduct>
}

@RestController
@RequestMapping("/api/favorites")
class FavoriteProductController(private val repository: FavoriteProductRepository) {

    @GetMapping
    fun list() = repository.findAllByOrderByName().map { it.toDto() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody dto: FavoriteProductDto) = repository.save(
        FavoriteProduct(
            name = dto.name, kcalPer100g = dto.kcalPer100g, carbsPer100g = dto.carbsPer100g,
            proteinPer100g = dto.proteinPer100g, fatPer100g = dto.fatPer100g, notes = dto.notes,
        ),
    ).toDto()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody dto: FavoriteProductDto): FavoriteProductDto {
        val product = repository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        product.name = dto.name
        product.kcalPer100g = dto.kcalPer100g
        product.carbsPer100g = dto.carbsPer100g
        product.proteinPer100g = dto.proteinPer100g
        product.fatPer100g = dto.fatPer100g
        product.notes = dto.notes
        return repository.save(product).toDto()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = repository.deleteById(id)
}
