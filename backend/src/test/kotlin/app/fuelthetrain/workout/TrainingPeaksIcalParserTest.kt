package app.fuelthetrain.workout

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TrainingPeaksIcalParserTest {

    private val entries = TrainingPeaksIcalParser.parse(
        javaClass.getResource("/trainingpeaks-sample.ics")!!.readText(),
    ).associateBy { it.uid }

    @Test
    fun `parses completed workout with folded lines`() {
        val ride = entries.getValue("bike-endurance-1")
        assertEquals(LocalDate.of(2026, 9, 22), ride.date)
        assertEquals("Bike: Wytrzymałość tlenowa LT1 2h", ride.title)
        assertEquals(Sport.BIKE, ride.sport)
        assertEquals(120, ride.plannedMinutes)
        assertEquals(132, ride.actualMinutes)
        assertEquals(70.45, ride.actualDistanceKm)
        assertEquals("Jazda tlenowa w terenie płaskim, rozgrzewka 10min S1 + piramida S2-S4.\nRozjazd 10min S1", ride.description)
    }

    @Test
    fun `parses planned workout without actual data`() {
        val ride = entries.getValue("bike-ftp-1")
        assertEquals(250, ride.plannedMinutes)
        assertNull(ride.actualMinutes)
        assertFalse(ride.description.contains("Planned Time"))
    }

    @Test
    fun `recognises day off, other and race events`() {
        assertEquals(Sport.DAY_OFF, entries.getValue("day-off-1").sport)
        assertEquals(Sport.OTHER, entries.getValue("other-1").sport)
        val race = entries.getValue("race-1")
        assertEquals(Sport.EVENT, race.sport)
        assertEquals(LocalDate.of(2026, 10, 3), race.date)
    }

    @Test
    fun `classifies intensity from title first and description as fallback`() {
        fun intensityOf(uid: String) = entries.getValue(uid).let { IntensityClassifier.classify(it.sport, it.title, it.description) }

        assertEquals(Intensity.REST, intensityOf("day-off-1"))
        assertEquals(Intensity.REST, intensityOf("other-1"))
        assertEquals(Intensity.RACE, intensityOf("race-1"))
        assertEquals(Intensity.ENDURANCE, intensityOf("bike-endurance-1"))
        assertEquals(Intensity.TEMPO, intensityOf("bike-tempo-1"))
        assertEquals(Intensity.THRESHOLD, intensityOf("bike-ftp-1"))
        assertEquals(Intensity.RECOVERY, intensityOf("bike-recovery-1"))
    }

    @Test
    fun `description fallback does not pick recovery just because of an S1 cool-down`() {
        val intensity = IntensityClassifier.classify(Sport.BIKE, "Bike: Trening", "Rozgrzewka S1, potem 3x10min S3, rozjazd S1")
        assertEquals(Intensity.TEMPO, intensity)
    }
}
