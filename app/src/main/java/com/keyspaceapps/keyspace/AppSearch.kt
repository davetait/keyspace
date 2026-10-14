package com.keyspaceapps.keyspace

/** A search hit: the app, and which characters of its label matched the query (for highlighting). */
data class AppMatch(val app: AppEntry, val matchedIndices: List<Int>)

/**
 * Apps whose label, or any word in it, starts with [query] (case-insensitive). Spaces and punctuation are
 * ignored on both sides, because the keyboard has no space key: "playst" matches "Play Store".
 *
 * Ranked by: recency-weighted launches from search ([AppUsage], highest first; never-launched apps last),
 * then whole-label matches before later-word matches ("p": Phone before Google Photos), then alphabetical.
 * A blank query matches nothing.
 */
fun matchApps(
    apps: List<AppEntry>,
    query: String,
    usage: Map<String, AppUsage>,
    nowMillis: Long,
    halfLifeDays: Double = AppUsage.DEFAULT_HALF_LIFE_DAYS,
): List<AppMatch> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    return apps
        .mapNotNull { app ->
            val label = LabelIndex(app.label)
            val start = label.matchStarts(q).firstOrNull() ?: return@mapNotNull null
            Triple(app, start, label.originalIndices(start, q.length))
        }
        .sortedWith(
            compareByDescending<Triple<AppEntry, Int, List<Int>>> { (app, _, _) ->
                usage[app.key]?.rankAt(nowMillis, halfLifeDays) ?: Double.NEGATIVE_INFINITY
            }
                // Whole-label match (start 0) before a later word.
                .thenBy { (_, start, _) -> if (start == 0) 0 else 1 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { (app, _, _) -> app.label }
        )
        .map { (app, _, indices) -> AppMatch(app, indices) }
}

fun searchApps(
    apps: List<AppEntry>,
    query: String,
    usage: Map<String, AppUsage>,
    nowMillis: Long,
    halfLifeDays: Double = AppUsage.DEFAULT_HALF_LIFE_DAYS,
): List<AppEntry> = matchApps(apps, query, usage, nowMillis, halfLifeDays).map { it.app }

/**
 * Letters that would continue at least one match if typed next, so the keyboard can dim the rest.
 * Null when nothing is typed (every key is useful).
 */
fun nextLetters(apps: List<AppEntry>, query: String): Set<Char>? {
    val q = normalize(query)
    if (q.isEmpty()) return null
    return apps.flatMapTo(mutableSetOf()) { app ->
        val label = LabelIndex(app.label)
        label.matchStarts(q).mapNotNull { start -> label.chars.getOrNull(start + q.length)?.takeIf { it.isLetter() } }
    }
}

/** Lowercase letters and digits only. */
private fun normalize(text: String): String = text.filter { it.isLetterOrDigit() }.lowercase()

/** A label reduced to lowercase letters and digits, remembering where each came from and where words start. */
private class LabelIndex(label: String) {
    val chars = StringBuilder()
    private val origin = mutableListOf<Int>()
    private val wordStarts = mutableSetOf<Int>()

    init {
        label.forEachIndexed { i, c ->
            if (!c.isLetterOrDigit()) return@forEachIndexed
            if (i == 0 || !label[i - 1].isLetterOrDigit()) wordStarts += chars.length
            chars.append(c.lowercaseChar())
            origin += i
        }
    }

    /** Positions (in [chars]) of word starts where [query] matches, in order. */
    fun matchStarts(query: String): List<Int> = wordStarts.sorted().filter { chars.startsWith(query, it) }

    fun originalIndices(start: Int, length: Int): List<Int> = origin.subList(start, start + length).toList()
}

/**
 * Lays out [results] in [rows] rows of [columns], best result closest to the keyboard: results fill the
 * bottom row left to right, then the row above. Returned top to bottom; extra results are dropped.
 */
fun <T> bottomUpRows(results: List<T>, columns: Int, rows: Int): List<List<T?>> =
    (rows - 1 downTo 0).map { fromBottom ->
        List(columns) { col -> results.getOrNull(fromBottom * columns + col) }
    }
