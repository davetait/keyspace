package com.keyspaceapps.keyspace

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

    private fun g(i: Int) = SlotRef(SlotArea.Grid, i)
    private fun d(i: Int) = SlotRef(SlotArea.Dock, i)

    @Test
    fun `rearrange moves within the grid to an empty cell`() {
        val (grid, dock) = rearrange(AppSlots(mapOf(0 to "a")), AppSlots(), g(0), g(7))
        assertEquals(mapOf(7 to "a"), grid.cells)
        assertEquals(emptyMap<Int, String>(), dock.cells)
    }

    @Test
    fun `rearrange swaps with an occupied cell`() {
        val (grid, _) = rearrange(AppSlots(mapOf(0 to "a", 3 to "b")), AppSlots(), g(0), g(3))
        assertEquals(mapOf(0 to "b", 3 to "a"), grid.cells)
    }

    @Test
    fun `rearrange moves and swaps between grid and dock`() {
        val (grid, dock) = rearrange(AppSlots(mapOf(2 to "a")), AppSlots(mapOf(1 to "x")), g(2), d(0))
        assertEquals(emptyMap<Int, String>(), grid.cells)
        assertEquals(mapOf(0 to "a", 1 to "x"), dock.cells)
        val (grid2, dock2) = rearrange(AppSlots(mapOf(5 to "a")), AppSlots(mapOf(1 to "x")), d(1), g(5))
        assertEquals(mapOf(5 to "x"), grid2.cells)
        assertEquals(mapOf(1 to "a"), dock2.cells)
    }

    @Test
    fun `rearrange to itself or from an empty cell changes nothing`() {
        val grid = AppSlots(mapOf(0 to "a"))
        assertEquals(grid to AppSlots(), rearrange(grid, AppSlots(), g(0), g(0)))
        assertEquals(grid to AppSlots(), rearrange(grid, AppSlots(), g(4), g(0)))
    }
}
