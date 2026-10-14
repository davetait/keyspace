package com.keyspaceapps.keyspace

import kotlin.math.ln
import kotlin.math.pow

/**
 * How much an app has been launched from search, weighted toward recent use ("frecency").
 *
 * Each launch adds 1, and the score halves every `halfLifeDays` without use (set by [AdaptSpeed]). Because idle apps all
 * decay by the same factor, their order relative to each other never changes over time: rarely used
 * apps keep their earned ranking, and are only passed by apps that are actually being used.
 */
data class AppUsage(val score: Double, val updatedAtMillis: Long) {

    fun recordLaunch(nowMillis: Long, halfLifeDays: Double = DEFAULT_HALF_LIFE_DAYS): AppUsage =
        AppUsage(score * 0.5.pow(halfLives(nowMillis, halfLifeDays)) + 1, nowMillis)

    /**
     * Log of the score decayed to [nowMillis]. Comparing logs instead of decayed scores avoids underflow:
     * after years idle a decayed score would round to 0 and tie, erasing the history this exists to keep.
     */
    fun rankAt(nowMillis: Long, halfLifeDays: Double = DEFAULT_HALF_LIFE_DAYS): Double =
        ln(score) - halfLives(nowMillis, halfLifeDays) * LN2

    /** Clamped at 0 so a clock set backwards can't inflate scores. */
    private fun halfLives(nowMillis: Long, halfLifeDays: Double): Double =
        (nowMillis - updatedAtMillis).coerceAtLeast(0) / DAY_MILLIS / halfLifeDays

    companion object {
        /** [AdaptSpeed.Normal]: a new app used more than an old one overtakes it in about this many days. */
        const val DEFAULT_HALF_LIFE_DAYS = 3.0
        const val DAY_MILLIS = 24.0 * 60 * 60 * 1000
        private val LN2 = ln(2.0)

        fun firstLaunch(nowMillis: Long) = AppUsage(1.0, nowMillis)
    }
}
