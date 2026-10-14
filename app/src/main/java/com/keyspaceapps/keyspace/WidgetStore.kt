package com.keyspaceapps.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists placed widgets: one entry per widget id, value `row;col;spanX;spanY`. */
class WidgetStore(context: Context) {

    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val _widgets = MutableStateFlow(load())
    val widgets: StateFlow<List<PlacedWidget>> = _widgets.asStateFlow()

    fun add(widget: PlacedWidget) {
        _widgets.value = _widgets.value.filter { it.id != widget.id } + widget
        prefs.edit { putString(widget.id.toString(), "${widget.row};${widget.col};${widget.spanX};${widget.spanY}") }
    }

    fun remove(id: Int) {
        _widgets.value = _widgets.value.filter { it.id != id }
        prefs.edit { remove(id.toString()) }
    }

    private fun load(): List<PlacedWidget> = prefs.all.mapNotNull { (key, value) ->
        val id = key.toIntOrNull() ?: return@mapNotNull null
        val n = (value as? String)?.split(';')?.mapNotNull { it.toIntOrNull() } ?: return@mapNotNull null
        if (n.size == 4) PlacedWidget(id, n[0], n[1], n[2], n[3]) else null
    }
}
