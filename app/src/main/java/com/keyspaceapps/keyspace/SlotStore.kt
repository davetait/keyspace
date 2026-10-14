package com.keyspaceapps.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists one [AppSlots] area in its own SharedPreferences file (keys `slot_<index>`). */
class SlotStore(context: Context, name: String) {

    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val _slots = MutableStateFlow(load())
    val slots: StateFlow<AppSlots> = _slots.asStateFlow()

    fun place(index: Int, key: String) = update(_slots.value.place(index, key))

    fun clear(index: Int) = update(_slots.value.clear(index))

    fun set(slots: AppSlots) = update(slots)

    private fun load(): AppSlots = AppSlots(
        prefs.all.mapNotNull { (name, value) ->
            val index = name.removePrefix(PREFIX).toIntOrNull()
            if (name.startsWith(PREFIX) && index != null && value is String) index to value else null
        }.toMap()
    )

    private fun update(slots: AppSlots) {
        _slots.value = slots
        prefs.edit {
            clear()
            slots.cells.forEach { (index, key) -> putString("$PREFIX$index", key) }
        }
    }

    private companion object {
        const val PREFIX = "slot_"
    }
}
