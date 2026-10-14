package com.keyspaceapps.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recency-weighted launches from search per app ([AppEntry.key]), used to rank search results. */
class LaunchStats(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_usage", Context.MODE_PRIVATE)
    private val _usage = MutableStateFlow(load())
    val usage: StateFlow<Map<String, AppUsage>> = _usage.asStateFlow()

    fun record(key: String, halfLifeDays: Double) {
        val now = System.currentTimeMillis()
        val updated = _usage.value[key]?.recordLaunch(now, halfLifeDays) ?: AppUsage.firstLaunch(now)
        _usage.value = _usage.value + (key to updated)
        prefs.edit { putString(key, "${updated.score};${updated.updatedAtMillis}") }
    }

    /** Forgets all search history, so ranking falls back to alphabetical. */
    fun clear() {
        _usage.value = emptyMap()
        prefs.edit { clear() }
    }

    private fun load(): Map<String, AppUsage> {
        migrateLaunchCounts()
        return prefs.all.mapNotNull { (key, value) ->
            val parts = (value as? String)?.split(';') ?: return@mapNotNull null
            val score = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            key to AppUsage(score, at)
        }.toMap()
    }

    /** Converts the earlier plain counts (file "launch_counts") into usage dated now, then deletes them. */
    private fun migrateLaunchCounts() {
        if (prefs.all.isNotEmpty()) return
        val old = context.getSharedPreferences("launch_counts", Context.MODE_PRIVATE)
        val counts = old.all.mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }
        if (counts.isEmpty()) return
        val now = System.currentTimeMillis()
        prefs.edit { counts.forEach { (key, count) -> putString(key, "${count.toDouble()};$now") } }
        context.deleteSharedPreferences("launch_counts")
    }
}
