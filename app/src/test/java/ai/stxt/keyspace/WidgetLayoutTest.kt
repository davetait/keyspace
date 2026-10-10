package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {

    private val grid = GridMetrics(columns = 5, rows = 5, cellWidthDp = 80f, cellHeightDp = 100f)

    @Test
    fun `span rounds the minimum size up to whole cells`() {
        assertEquals(2 to 1, spanFor(minWidthDp = 110f, minHeightDp = 40f, 0, 0, grid))
        assertEquals(1 to 1, spanFor(minWidthDp = 80f, minHeightDp = 100f, 0, 0, grid))
    }

    @Test
    fun `declared target cells win over the minimum size`() {
        assertEquals(4 to 2, spanFor(minWidthDp = 50f, minHeightDp = 50f, 4, 2, grid))
    }

    @Test
    fun `span is clamped to the grid`() {
        assertEquals(5 to 5, spanFor(minWidthDp = 900f, minHeightDp = 900f, 0, 0, grid))
    }

    @Test
    fun `widget covers the right cells`() {
        assertEquals(setOf(6, 7, 11, 12), PlacedWidget(1, row = 1, col = 1, spanX = 2, spanY = 2).cells(columns = 5))
    }

    @Test
    fun `can place in free space`() {
        assertTrue(canPlace(0, 0, 2, 2, 5, 5, emptyList(), emptySet()))
    }

    @Test
    fun `cannot place outside the grid`() {
        assertFalse(canPlace(0, 4, 2, 1, 5, 5, emptyList(), emptySet()))
        assertFalse(canPlace(4, 0, 1, 2, 5, 5, emptyList(), emptySet()))
    }

    @Test
    fun `cannot overlap another widget or an app`() {
        val existing = listOf(PlacedWidget(1, row = 0, col = 0, spanX = 2, spanY = 2))
        assertFalse(canPlace(1, 1, 2, 1, 5, 5, existing, emptySet()))
        assertTrue(canPlace(0, 2, 2, 1, 5, 5, existing, emptySet()))
        assertFalse(canPlace(0, 2, 2, 1, 5, 5, existing, appCells = setOf(3)))
    }

    @Test
    fun `widgets that no longer fit after a column change are hidden but kept`() {
        val widgets = listOf(PlacedWidget(1, 0, 3, 2, 1), PlacedWidget(2, 0, 0, 2, 1))
        assertEquals(listOf(2), visibleWidgets(widgets, columns = 4, rows = 5).map { it.id })
        assertEquals(listOf(1, 2), visibleWidgets(widgets, columns = 5, rows = 5).map { it.id })
    }
}
