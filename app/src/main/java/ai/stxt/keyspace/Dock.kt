package ai.stxt.keyspace

/** Shortcut slots in the bottom dock, stored as [AppEntry.key] values (null = empty). */
data class Dock(val slots: List<String?> = List(SIZE) { null }) {

    init {
        require(slots.size == SIZE) { "Dock needs $SIZE slots, got ${slots.size}" }
    }

    /** Puts [key] in [index], replacing whatever was there. If [key] was in another slot, that slot is emptied. */
    fun place(index: Int, key: String): Dock =
        Dock(slots.mapIndexed { i, existing ->
            when {
                i == index -> key
                existing == key -> null
                else -> existing
            }
        })

    fun clear(index: Int): Dock = Dock(slots.mapIndexed { i, existing -> if (i == index) null else existing })

    /** Matches slots to installed apps; a slot whose app is no longer installed resolves to null. */
    fun resolve(apps: List<AppEntry>): List<AppEntry?> {
        val byKey = apps.associateBy { it.key }
        return slots.map { key -> key?.let(byKey::get) }
    }

    companion object {
        /** 2 slots on each side of the drawer button. */
        const val SIZE = 4
    }
}
