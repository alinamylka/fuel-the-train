package app.fuelthetrain.workout

import app.fuelthetrain.workout.ActivityMatcher.Match
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ActivityMatcherTest {

    private val day = LocalDate.of(2026, 10, 3)

    private fun plan(id: Long, title: String, sport: Sport) = Workout(
        id = id, externalId = "tp-$id", date = day, title = title, sport = sport, intensity = Intensity.ENDURANCE,
    )

    private fun activity(id: String, name: String, sport: Sport = Sport.BIKE, kj: Int? = 300, minutes: Int = 30) =
        Activity(id, day, sport, name, minutes, kj, null, 0.7)

    @Test
    fun `race day warm-up, race and cool-down all count towards the race`() {
        val warmUp = plan(1, "Bike: Rozgrzewka - piramida FTP1", Sport.BIKE)
        val race = plan(2, "Varese TT", Sport.EVENT)
        val recorded = listOf(activity("a1", "Varese - Rozgrzewka"), activity("a2", "Varese TT"), activity("a3", "Cool down"))

        val match = assertIs<Match.Planned>(ActivityMatcher.match(recorded, listOf(warmUp, race)).single())

        assertEquals(race, match.workout)
        assertEquals(listOf(warmUp), match.alsoCovered)
        assertEquals(900, ActivityMatcher.totalKj(match.activities))
    }

    @Test
    fun `of two planned rides the one whose title Garmin copied wins`() {
        val endurance = plan(1, "Bike: TT Wytrzymałość tlenowa LT1 2h", Sport.BIKE)
        val recovery = plan(2, "Bike: Aktywna regeneracja w terenie płaskim", Sport.BIKE)

        val match = assertIs<Match.Planned>(
            ActivityMatcher.match(listOf(activity("a1", "Berne - TT Wytrzymałość tlenowa LT1 2h")), listOf(recovery, endurance)).single(),
        )

        assertEquals(endurance, match.workout)
    }

    @Test
    fun `an activity with nothing planned in its sport becomes its own workout`() {
        val ride = plan(1, "Bike: TT S3", Sport.BIKE)

        val matches = ActivityMatcher.match(listOf(activity("a1", "Strength", Sport.STRENGTH, kj = null)), listOf(ride))

        val unplanned = assertIs<Match.Unplanned>(matches.single())
        assertEquals(Sport.STRENGTH, unplanned.sport)
        assertEquals(null, ActivityMatcher.totalKj(unplanned.activities))
    }

    @Test
    fun `intervals icu types map to sports`() {
        assertEquals(Sport.BIKE, ActivityMatcher.sportOf("VirtualRide"))
        assertEquals(Sport.STRENGTH, ActivityMatcher.sportOf("WeightTraining"))
        assertEquals(Sport.OTHER, ActivityMatcher.sportOf("Kitesurf"))
    }
}
