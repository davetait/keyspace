package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSlotsTest {

    private fun app(pkg: String) = AppEntry(pkg, pkg, "$pkg.Main")
    private val a = app("a.app")
    private val b = app("b.app")

    @Test
    fun `place puts an app into an empty cell`() {
        assertEquals(listOf(null, a, null, null), AppSlots().place(1, a.key).resolve(listOf(a), 4))
    }

    @Test
    fun `place replaces an occupied cell`() {
        assertEquals(mapOf(0 to b.key), AppSlots().place(0, a.key).place(0, b.key).cells)
    }

    @Test
    fun `placing an already placed app moves it instead of duplicating it`() {
        assertEquals(mapOf(3 to a.key), AppSlots().place(0, a.key).place(3, a.key).cells)
    }

    @Test
    fun `clear empties a cell`() {
        assertEquals(emptyMap<Int, String>(), AppSlots().place(2, a.key).clear(2).cells)
    }

    @Test
    fun `resolve returns null for apps that are no longer installed`() {
        val slots = AppSlots().place(0, a.key).place(1, b.key)
        assertEquals(listOf(a, null), slots.resolve(listOf(a), 2))
    }

    @Test
    fun `cells beyond the visible count are kept, not shown`() {
        val slots = AppSlots().place(0, a.key).place(9, b.key)
        assertEquals(listOf(a, null, null), slots.resolve(listOf(a, b), 3))
        assertEquals(b.key, slots.cells[9])
    }
}
