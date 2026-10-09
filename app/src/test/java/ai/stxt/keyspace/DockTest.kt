package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class DockTest {

    private fun app(pkg: String) = AppEntry(pkg, pkg, "$pkg.Main")
    private val a = app("a.app")
    private val b = app("b.app")

    @Test
    fun `place puts an app into an empty slot`() {
        assertEquals(listOf(null, a.key, null, null), Dock().place(1, a.key).slots)
    }

    @Test
    fun `place replaces an occupied slot`() {
        assertEquals(listOf(b.key, null, null, null), Dock().place(0, a.key).place(0, b.key).slots)
    }

    @Test
    fun `placing an already docked app moves it instead of duplicating it`() {
        assertEquals(listOf(null, null, null, a.key), Dock().place(0, a.key).place(3, a.key).slots)
    }

    @Test
    fun `clear empties a slot`() {
        assertEquals(listOf(null, null, null, null), Dock().place(2, a.key).clear(2).slots)
    }

    @Test
    fun `resolve returns null for apps that are no longer installed`() {
        val dock = Dock().place(0, a.key).place(1, b.key)
        assertEquals(listOf(a, null, null, null), dock.resolve(listOf(a)))
    }
}
