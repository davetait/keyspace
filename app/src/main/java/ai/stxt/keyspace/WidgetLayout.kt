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

/** How far a widget can be resized, in cells, and in which directions. */
data class WidgetSizeLimits(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val horizontal: Boolean,
    val vertical: Boolean,
)

/**
 * Size limits from the provider's dp sizes. The minimum resize size falls back to the minimum size; a max
 * of 0 means "no limit" (grid size). A widget may already be smaller than its stated minimum (target cells
 * win when added), so the minimum never exceeds the current span.
 */
fun sizeLimits(
    widget: PlacedWidget,
    minWidthDp: Float,
    minHeightDp: Float,
    minResizeWidthDp: Float,
    minResizeHeightDp: Float,
    maxResizeWidthDp: Float,
    maxResizeHeightDp: Float,
    horizontal: Boolean,
    vertical: Boolean,
    grid: GridMetrics,
): WidgetSizeLimits {
    fun cells(dp: Float, cell: Float) = ceil(dp / cell).toInt().coerceAtLeast(1)
    val minX = cells(if (minResizeWidthDp > 0) minResizeWidthDp else minWidthDp, grid.cellWidthDp).coerceAtMost(widget.spanX)
    val minY = cells(if (minResizeHeightDp > 0) minResizeHeightDp else minHeightDp, grid.cellHeightDp).coerceAtMost(widget.spanY)
    val maxX = if (maxResizeWidthDp > 0) cells(maxResizeWidthDp, grid.cellWidthDp).coerceAtLeast(widget.spanX) else grid.columns
    val maxY = if (maxResizeHeightDp > 0) cells(maxResizeHeightDp, grid.cellHeightDp).coerceAtLeast(widget.spanY) else grid.rows
    return WidgetSizeLimits(minX, minY, maxX.coerceAtMost(grid.columns), maxY.coerceAtMost(grid.rows), horizontal, vertical)
}

enum class WidgetEdge { Left, Top, Right, Bottom }

/** [widget] moved so its top-left is at [row]/[col]; null if it wouldn't fit there. */
fun moveWidget(
    widget: PlacedWidget,
    row: Int,
    col: Int,
    columns: Int,
    rows: Int,
    widgets: List<PlacedWidget>,
    appCells: Set<Int>,
): PlacedWidget? {
    val moved = widget.copy(row = row, col = col)
    val others = widgets.filter { it.id != widget.id }
    return moved.takeIf { canPlace(row, col, widget.spanX, widget.spanY, columns, rows, others, appCells) }
}

/**
 * [widget] with one [edge] dragged by [delta] cells (positive = right/down). Null if the size breaks its
 * [limits] or resize directions, or the new area isn't free.
 */
fun resizeWidget(
    widget: PlacedWidget,
    edge: WidgetEdge,
    delta: Int,
    limits: WidgetSizeLimits,
    columns: Int,
    rows: Int,
    widgets: List<PlacedWidget>,
    appCells: Set<Int>,
): PlacedWidget? {
    val resized = when (edge) {
        WidgetEdge.Left -> widget.copy(col = widget.col + delta, spanX = widget.spanX - delta)
        WidgetEdge.Right -> widget.copy(spanX = widget.spanX + delta)
        WidgetEdge.Top -> widget.copy(row = widget.row + delta, spanY = widget.spanY - delta)
        WidgetEdge.Bottom -> widget.copy(spanY = widget.spanY + delta)
    }
    val horizontalEdge = edge == WidgetEdge.Left || edge == WidgetEdge.Right
    if (horizontalEdge && !limits.horizontal || !horizontalEdge && !limits.vertical) return null
    if (resized.spanX !in limits.minX..limits.maxX || resized.spanY !in limits.minY..limits.maxY) return null
    val others = widgets.filter { it.id != widget.id }
    return resized.takeIf { canPlace(it.row, it.col, it.spanX, it.spanY, columns, rows, others, appCells) }
}
