package ai.stxt.keyspace

import kotlin.math.ceil

/**
 * A widget on the home grid. Stored by row and column (not cell number) so it keeps its shape if the
 * column count changes.
 */
data class PlacedWidget(val id: Int, val row: Int, val col: Int, val spanX: Int, val spanY: Int) {

    fun fitsIn(columns: Int, rows: Int) = row >= 0 && col >= 0 && col + spanX <= columns && row + spanY <= rows

    /** Cell numbers (row-major, like [AppSlots]) this widget covers. */
    fun cells(columns: Int): Set<Int> =
        (row until row + spanY).flatMap { r -> (col until col + spanX).map { c -> r * columns + c } }.toSet()
}

/** Size of the home grid, used to turn widget sizes into cell spans. */
data class GridMetrics(val columns: Int, val rows: Int, val cellWidthDp: Float, val cellHeightDp: Float)

/**
 * Cells a widget needs: the provider's target cells when it declares them (Android 12+), otherwise
 * its minimum size rounded up to whole cells. Clamped to the grid.
 */
fun spanFor(minWidthDp: Float, minHeightDp: Float, targetCellsX: Int, targetCellsY: Int, grid: GridMetrics): Pair<Int, Int> {
    val x = if (targetCellsX > 0) targetCellsX else ceil(minWidthDp / grid.cellWidthDp).toInt()
    val y = if (targetCellsY > 0) targetCellsY else ceil(minHeightDp / grid.cellHeightDp).toInt()
    return x.coerceIn(1, grid.columns) to y.coerceIn(1, grid.rows)
}

/** Widgets that fit the current grid; the rest are kept in storage but not shown. */
fun visibleWidgets(widgets: List<PlacedWidget>, columns: Int, rows: Int) = widgets.filter { it.fitsIn(columns, rows) }

/** Whether a widget can go at [row]/[col]: inside the grid and not over another widget or an app icon. */
fun canPlace(
    row: Int,
    col: Int,
    spanX: Int,
    spanY: Int,
    columns: Int,
    rows: Int,
    widgets: List<PlacedWidget>,
    appCells: Set<Int>,
): Boolean {
    val candidate = PlacedWidget(id = -1, row, col, spanX, spanY)
    if (!candidate.fitsIn(columns, rows)) return false
    val wanted = candidate.cells(columns)
    val taken = visibleWidgets(widgets, columns, rows).flatMap { it.cells(columns) }.toSet() + appCells
    return wanted.none { it in taken }
}
