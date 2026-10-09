package ai.stxt.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the [Dock] in SharedPreferences. */
class DockStore(context: Context) {

    private val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)
    private val _dock = MutableStateFlow(Dock(List(Dock.SIZE) { prefs.getString(slotKey(it), null) }))
    val dock: StateFlow<Dock> = _dock.asStateFlow()

    fun place(index: Int, key: String) = update(_dock.value.place(index, key))

    fun clear(index: Int) = update(_dock.value.clear(index))

    private fun update(dock: Dock) {
        _dock.value = dock
        prefs.edit {
            dock.slots.forEachIndexed { i, key -> if (key == null) remove(slotKey(i)) else putString(slotKey(i), key) }
        }
    }

    private fun slotKey(index: Int) = "slot_$index"
}
