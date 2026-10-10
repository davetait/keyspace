package ai.stxt.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How quickly search ranking follows changes in which apps are used (see [AppUsage]). */
enum class AdaptSpeed(val halfLifeDays: Double, val description: String) {
    Fast(1.5, "New favorites rise in about 2 days"),
    Normal(AppUsage.DEFAULT_HALF_LIFE_DAYS, "New favorites rise in about 3–4 days"),
    Slow(7.0, "New favorites rise in about a week"),
}

data class KeyspaceSettings(
    val columns: Int = DEFAULT_COLUMNS,
    val adaptSpeed: AdaptSpeed = AdaptSpeed.Normal,
) {
    companion object {
        const val DEFAULT_COLUMNS = 5
        val COLUMN_RANGE = 4..7

        /** Tolerates missing or out-of-range stored values by falling back to defaults. */
        fun parse(columns: Int?, adaptSpeed: String?) = KeyspaceSettings(
            columns = columns?.coerceIn(COLUMN_RANGE) ?: DEFAULT_COLUMNS,
            adaptSpeed = AdaptSpeed.entries.firstOrNull { it.name == adaptSpeed } ?: AdaptSpeed.Normal,
        )
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        KeyspaceSettings.parse(
            columns = if (prefs.contains(COLUMNS)) prefs.getInt(COLUMNS, 0) else null,
            adaptSpeed = prefs.getString(ADAPT_SPEED, null),
        )
    )
    val settings: StateFlow<KeyspaceSettings> = _settings.asStateFlow()

    fun setColumns(columns: Int) = update(_settings.value.copy(columns = columns.coerceIn(KeyspaceSettings.COLUMN_RANGE)))

    fun setAdaptSpeed(speed: AdaptSpeed) = update(_settings.value.copy(adaptSpeed = speed))

    private fun update(settings: KeyspaceSettings) {
        _settings.value = settings
        prefs.edit {
            putInt(COLUMNS, settings.columns)
            putString(ADAPT_SPEED, settings.adaptSpeed.name)
        }
    }

    private companion object {
        const val COLUMNS = "columns"
        const val ADAPT_SPEED = "adapt_speed"
    }
}
