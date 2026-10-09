package ai.stxt.keyspace

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
