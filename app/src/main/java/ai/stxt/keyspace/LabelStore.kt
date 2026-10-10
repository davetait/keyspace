package ai.stxt.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's custom app names, keyed by [AppEntry.key]. */
class LabelStore(context: Context) {

    private val prefs = context.getSharedPreferences("labels", Context.MODE_PRIVATE)
    private val _labels = MutableStateFlow(prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap())
    val labels: StateFlow<Map<String, String>> = _labels.asStateFlow()

    /** Sets a custom name; null or blank restores the app's own name. */
    fun rename(key: String, label: String?) {
        val name = label?.trim()?.takeIf { it.isNotEmpty() }
        _labels.value = if (name == null) _labels.value - key else _labels.value + (key to name)
        prefs.edit { if (name == null) remove(key) else putString(key, name) }
    }
}
