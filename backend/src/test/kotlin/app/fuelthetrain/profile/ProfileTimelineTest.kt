package app.fuelthetrain.profile

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileTimelineTest {

    private fun version(validFrom: String, weightKg: Double) = ProfileVersion(
        validFrom = LocalDate.parse(validFrom), weightKg = weightKg, heightCm = 170, birthYear = 1990,
        sex = Sex.FEMALE, ftpWatts = null, goal = Goal.MAINTAIN, proteinGPerKg = 1.8,
    )

    private val timeline = ProfileTimeline(listOf(version("2026-09-20", 62.0), version("2026-09-01", 64.0)))

    @Test
    fun `a day uses the latest version that started on or before it`() {
        assertEquals(64.0, timeline.at(LocalDate.parse("2026-09-19")).weightKg)
        assertEquals(62.0, timeline.at(LocalDate.parse("2026-09-20")).weightKg)
        assertEquals(62.0, timeline.at(LocalDate.parse("2026-12-01")).weightKg)
    }

    @Test
    fun `days before the first version use the first version`() {
        assertEquals(64.0, timeline.at(LocalDate.parse("2026-01-01")).weightKg)
    }
}
