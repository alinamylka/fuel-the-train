package app.fuelthetrain.workout

/**
 * Rule-based first guess of how hard a workout is, from the coach's title and description.
 * Zones follow the Polish coaching notation S1-S6. The user can override the result,
 * and an AI classifier can replace this later.
 */
object IntensityClassifier {

    private val rules: List<Pair<Intensity, Regex>> = listOf(
        // Not S1 on its own: almost every workout has an S1 warm-up or cool-down.
        Intensity.RECOVERY to Regex("""regenera|recovery"""),
        Intensity.ENDURANCE to Regex("""\blt1\b|\bs2\b|tlenow|wytrzyma|wycieczk|endurance|\bz2\b"""),
        Intensity.TEMPO to Regex("""\bs3\b|tempo|sweet ?spot"""),
        Intensity.THRESHOLD to Regex("""\bftp\b|\bs4\b|próg|threshold|\blt2\b"""),
        Intensity.VO2MAX to Regex("""\bs5\b|\bs6\b|vo2|sprint|ataki"""),
    )

    fun classify(sport: Sport, title: String, description: String): Intensity {
        when (sport) {
            Sport.DAY_OFF -> return Intensity.REST
            Sport.EVENT -> return Intensity.RACE
            Sport.OTHER -> return Intensity.REST
            Sport.WALK -> return Intensity.RECOVERY
            else -> {}
        }
        // The title names the main set, so the first zone mentioned there wins
        // ("TT S3 + krótkie przyspieszenia S6" is a tempo ride with a few sprints).
        firstMentioned(title.lowercase())?.let { return it }
        // Descriptions mention many zones in passing (warm-up, terrain notes),
        // so only the easiest one is a safe guess.
        val text = description.lowercase()
        return rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first ?: Intensity.ENDURANCE
    }

    private fun firstMentioned(text: String): Intensity? =
        rules.mapNotNull { (intensity, regex) -> regex.find(text)?.let { it.range.first to intensity } }
            .minByOrNull { it.first }
            ?.second
}
