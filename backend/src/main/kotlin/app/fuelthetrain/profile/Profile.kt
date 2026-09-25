package app.fuelthetrain.profile

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

enum class Sex { FEMALE, MALE }

enum class Goal { MAINTAIN, LOSE, GAIN }

/** Body and goal values in effect on one day, the input of the nutrition calculator. */
data class Profile(
    val weightKg: Double = 60.0,
    val heightCm: Int = 168,
    val birthYear: Int = 1990,
    val sex: Sex = Sex.FEMALE,
    /** Null when unknown, then FTP is estimated from body weight. */
    val ftpWatts: Int? = null,
    val goal: Goal = Goal.MAINTAIN,
    val proteinGPerKg: Double = 1.8,
)

/**
 * The profile as it was from [validFrom] until the next version. Keeping versions
 * instead of one row means a new weight or FTP doesn't rewrite the targets of past days.
 */
@Entity
class ProfileVersion(
    @Id @GeneratedValue var id: Long? = null,
    @Column(unique = true) var validFrom: LocalDate,
    var weightKg: Double,
    var heightCm: Int,
    var birthYear: Int,
    @Enumerated(EnumType.STRING) var sex: Sex,
    var ftpWatts: Int?,
    @Enumerated(EnumType.STRING) var goal: Goal,
    var proteinGPerKg: Double,
) {
    fun toProfile() = Profile(weightKg, heightCm, birthYear, sex, ftpWatts, goal, proteinGPerKg)

    fun apply(dto: ProfileDto) {
        weightKg = dto.weightKg
        heightCm = dto.heightCm
        birthYear = dto.birthYear
        sex = dto.sex
        ftpWatts = dto.ftpWatts
        goal = dto.goal
        proteinGPerKg = dto.proteinGPerKg
    }
}

/** Versions sorted by date. Days before the first version use the first one. */
class ProfileTimeline(versions: List<ProfileVersion>) {
    private val sorted = versions.sortedBy { it.validFrom }.also { require(it.isNotEmpty()) }

    fun at(date: LocalDate): Profile = (sorted.lastOrNull { !it.validFrom.isAfter(date) } ?: sorted.first()).toProfile()
}

data class ProfileDto(
    val id: Long? = null,
    /** When saving: the day the values start to apply, today if left out. */
    val validFrom: LocalDate? = null,
    @field:DecimalMin("30") @field:DecimalMax("200") val weightKg: Double,
    @field:Min(120) @field:Max(230) val heightCm: Int,
    @field:Min(1920) @field:Max(2020) val birthYear: Int,
    val sex: Sex,
    @field:Min(50) @field:Max(600) val ftpWatts: Int?,
    val goal: Goal,
    @field:DecimalMin("1.0") @field:DecimalMax("3.0") val proteinGPerKg: Double,
)

fun ProfileVersion.toDto() =
    ProfileDto(id, validFrom, weightKg, heightCm, birthYear, sex, ftpWatts, goal, proteinGPerKg)

interface ProfileVersionRepository : JpaRepository<ProfileVersion, Long> {
    fun findAllByOrderByValidFromDesc(): List<ProfileVersion>
    fun findByValidFrom(validFrom: LocalDate): ProfileVersion?
}

@Service
class ProfileService(private val repository: ProfileVersionRepository) {

    @Transactional
    fun timeline(): ProfileTimeline = ProfileTimeline(history().ifEmpty { listOf(repository.save(defaultVersion())) })

    fun history(): List<ProfileVersion> = repository.findAllByOrderByValidFromDesc()

    @Transactional
    fun current(): ProfileVersion {
        val today = LocalDate.now()
        return history().firstOrNull { !it.validFrom.isAfter(today) } ?: history().lastOrNull() ?: repository.save(defaultVersion())
    }

    /** Saving again for the same day corrects that version instead of adding a new one. */
    @Transactional
    fun save(dto: ProfileDto): ProfileVersion {
        val validFrom = dto.validFrom ?: LocalDate.now()
        val version = repository.findByValidFrom(validFrom) ?: defaultVersion(validFrom)
        version.apply(dto)
        return repository.save(version)
    }

    @Transactional
    fun delete(id: Long) {
        if (repository.count() <= 1) throw ResponseStatusException(HttpStatus.CONFLICT, "The only profile version can't be deleted")
        repository.deleteById(id)
    }

    private fun defaultVersion(validFrom: LocalDate = LocalDate.now()) = Profile().let {
        ProfileVersion(
            validFrom = validFrom, weightKg = it.weightKg, heightCm = it.heightCm, birthYear = it.birthYear,
            sex = it.sex, ftpWatts = it.ftpWatts, goal = it.goal, proteinGPerKg = it.proteinGPerKg,
        )
    }
}

/** Moves the single row of the old `profile` table into the version history, once. */
@Component
class LegacyProfileImport(private val jdbc: JdbcClient, private val repository: ProfileVersionRepository) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (repository.count() > 0) return
        val legacyTable = jdbc.sql("select count(*) from information_schema.tables where table_name = 'PROFILE'")
            .query(Int::class.java).single() > 0
        if (!legacyTable) return
        jdbc.sql("select * from profile where id = 1").query { rs, _ ->
            ProfileVersion(
                validFrom = LocalDate.now(),
                weightKg = rs.getDouble("weight_kg"),
                heightCm = rs.getInt("height_cm"),
                birthYear = rs.getInt("birth_year"),
                sex = Sex.valueOf(rs.getString("sex")),
                ftpWatts = rs.getObject("ftp_watts") as Int?,
                goal = Goal.valueOf(rs.getString("goal")),
                proteinGPerKg = rs.getDouble("proteingper_kg"),
            )
        }.optional().ifPresent { repository.save(it) }
    }
}

@RestController
@RequestMapping("/api/profile")
class ProfileController(private val service: ProfileService) {

    /** The version in effect today. */
    @GetMapping
    fun get(): ProfileDto = service.current().toDto()

    @GetMapping("/history")
    fun history(): List<ProfileDto> = service.history().map { it.toDto() }

    @PutMapping
    fun save(@Valid @RequestBody dto: ProfileDto): ProfileDto = service.save(dto).toDto()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
