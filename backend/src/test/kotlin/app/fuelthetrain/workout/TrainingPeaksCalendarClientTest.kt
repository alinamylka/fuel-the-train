package app.fuelthetrain.workout

import app.fuelthetrain.workout.TrainingPeaksCalendarClient.Companion.isTrainingPeaksLink
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingPeaksCalendarClientTest {

    @Test
    fun `accepts the webcal and https links TrainingPeaks gives out`() {
        assertTrue(isTrainingPeaksLink("webcal://www.trainingpeaks.com/ical/ABC123.ics"))
        assertTrue(isTrainingPeaksLink("  https://api.trainingpeaks.com/ical/ABC123.ics "))
    }

    @Test
    fun `rejects other hosts and look-alikes`() {
        assertFalse(isTrainingPeaksLink("https://example.com/ical/ABC123.ics"))
        assertFalse(isTrainingPeaksLink("https://trainingpeaks.com.evil.net/ical/x.ics"))
        assertFalse(isTrainingPeaksLink("http://www.trainingpeaks.com/ical/x.ics"))
        assertFalse(isTrainingPeaksLink("file:///etc/passwd"))
        assertFalse(isTrainingPeaksLink("not a url"))
    }
}
