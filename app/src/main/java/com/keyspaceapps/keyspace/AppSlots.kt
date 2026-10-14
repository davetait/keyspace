package com.keyspaceapps.keyspace

/**
 * App shortcuts placed in numbered cells (the dock, or the home grid in row-major order).
 * Stored sparsely so the number of visible cells can change (screen size, column setting)
 * without losing placements.
 */
data class AppSlots(val cells: Map<Int, String> = emptyMap()) {

    /** Puts [key] in [index], replacing whatever was there. If [key] was in another cell, it moves. */
    fun place(index: Int, key: String): AppSlots = AppSlots(cells.filterValues { it != key } + (index to key))

    fun clear(index: Int): AppSlots = AppSlots(cells - index)

    /** The first [count] cells matched to installed apps; empty or uninstalled cells are null. */
    fun resolve(apps: List<AppEntry>, count: Int): List<AppEntry?> {
        val byKey = apps.associateBy { it.key }
        return List(count) { index -> cells[index]?.let(byKey::get) }
    }
}

/** The two places app shortcuts can be placed. */
enum class SlotArea { Grid, Dock }

/** One cell: which area, and its index there. */
data class SlotRef(val area: SlotArea, val index: Int)

/**
 * Moves the app at [from] to [to] (within an area or between grid and dock). If [to] is occupied the two
 * swap, so rearranging never deletes a shortcut. Returns the new (grid, dock).
 */
fun rearrange(grid: AppSlots, dock: AppSlots, from: SlotRef, to: SlotRef): Pair<AppSlots, AppSlots> {
    fun cellsOf(area: SlotArea) = if (area == SlotArea.Grid) grid.cells else dock.cells
    val moving = cellsOf(from.area)[from.index] ?: return grid to dock
    if (from == to) return grid to dock
    val displaced = cellsOf(to.area)[to.index]
    val updated = mutableMapOf(SlotArea.Grid to grid.cells.toMutableMap(), SlotArea.Dock to dock.cells.toMutableMap())
    updated.getValue(from.area).remove(from.index)
    updated.getValue(to.area)[to.index] = moving
    if (displaced != null) updated.getValue(from.area)[from.index] = displaced
    return AppSlots(updated.getValue(SlotArea.Grid)) to AppSlots(updated.getValue(SlotArea.Dock))
}
