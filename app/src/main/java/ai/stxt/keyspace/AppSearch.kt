package ai.stxt.keyspace

/**
 * Apps whose label, or any word in it, starts with [query] (case-insensitive).
 *
 * Ranked by: recency-weighted launches from search ([AppUsage], highest first; never-launched apps last),
 * then whole-label matches before later-word matches ("p": Phone before Google Photos), then alphabetical.
 * A blank query matches nothing.
 */
fun searchApps(
    apps: List<AppEntry>,
    query: String,
    usage: Map<String, AppUsage>,
    nowMillis: Long,
    halfLifeDays: Double = AppUsage.DEFAULT_HALF_LIFE_DAYS,
): List<AppEntry> {
    val q = query.trimStart().lowercase()
    if (q.isBlank()) return emptyList()
    return apps
        .mapNotNull { app -> matchTier(app.label.lowercase(), q)?.let { tier -> app to tier } }
        .sortedWith(
            compareByDescending<Pair<AppEntry, Int>> { (app, _) -> usage[app.key]?.rankAt(nowMillis, halfLifeDays) ?: Double.NEGATIVE_INFINITY }
                .thenBy { (_, tier) -> tier }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { (app, _) -> app.label }
        )
        .map { it.first }
}

/** 0 = the whole label starts with [query], 1 = a later word does, null = no match. */
private fun matchTier(label: String, query: String): Int? {
    if (label.startsWith(query)) return 0
    val laterWordMatch = (1 until label.length).any { i ->
        !label[i - 1].isLetterOrDigit() && label[i].isLetterOrDigit() && label.startsWith(query, startIndex = i)
    }
    return if (laterWordMatch) 1 else null
}

/**
 * Lays out [results] in [rows] rows of [columns], best result closest to the keyboard: results fill the
 * bottom row left to right, then the row above. Returned top to bottom; extra results are dropped.
 */
fun bottomUpRows(results: List<AppEntry>, columns: Int, rows: Int): List<List<AppEntry?>> =
    (rows - 1 downTo 0).map { fromBottom ->
        List(columns) { col -> results.getOrNull(fromBottom * columns + col) }
    }
