package ai.stxt.keyspace

import android.content.ClipData
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import android.os.Build
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import android.provider.Settings
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import android.view.View
import android.appwidget.AppWidgetManager
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.ClipDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** Which panel covers the home area. */
enum class Overlay { None, Apps, Widgets }

/**
 * [dragging]: a drag from the open panel is in progress. The panel then stays composed (so the drag isn't
 * cancelled) but is invisible, revealing the drop targets.
 */
data class DrawerState(val overlay: Overlay = Overlay.None, val dragging: Boolean = false) {
    val isOpen: Boolean get() = overlay != Overlay.None && !dragging

    companion object {
        val Closed = DrawerState()
    }
}


private val IconSize = 48.dp
private val GridCellHeight = 96.dp
private const val DockSize = 4
private const val ResultRows = 2

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    grid: AppSlots,
    gridColumns: Int,
    dock: AppSlots,
    drawerState: DrawerState,
    repository: AppRepository,
    onDrawerStateChange: (DrawerState) -> Unit,
    onPlace: (area: SlotArea, index: Int, key: String) -> Unit,
    onRemove: (area: SlotArea, index: Int) -> Unit,
    /** A placed icon was dragged from one cell to another (move, or swap if occupied). */
    onRearrange: (from: SlotRef, to: SlotRef) -> Unit,
    results: List<AppMatch>?,
    onLaunchResult: (AppEntry) -> Unit,
    homeMenu: HomeMenuActions,
    widgets: WidgetsUi,
    inputArea: @Composable () -> Unit,
) {
    // The placed icon being dragged, so its cell can dim; null for drawer drags or no drag.
    var draggingFrom by remember { mutableStateOf<SlotRef?>(null) }
    val onDragEnded = {
        draggingFrom = null
        if (drawerState.dragging) onDrawerStateChange(DrawerState.Closed)
    }
    val slotDrag = SlotDrag(
        from = draggingFrom,
        onStart = { from ->
            draggingFrom = from
            // No panel to hide, but "dragging" shows every drop outline, as for drawer drags.
            onDrawerStateChange(DrawerState(Overlay.None, dragging = true))
        },
        onRearrange = onRearrange,
    )
    CompositionLocalProvider(LocalSlotDrag provides slotDrag) {
        Column(
            Modifier
                .fillMaxSize()
                // One scrim behind the results rows, typed line, keyboard and dock, so they read on any wallpaper.
                .drawBehind {
                    // Ramps up quickly so the typed line and keys are already well covered on light wallpapers.
                    drawRect(
                        Brush.verticalGradient(
                            0.40f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black.copy(alpha = 0.6f),
                        )
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Home area: the shortcut grid, with the drawer opening over it.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HomeGrid(
                    slots = grid,
                    apps = apps,
                    columns = gridColumns,
                    dragging = drawerState.dragging,
                    repository = repository,
                    onPlace = { index, key -> onPlace(SlotArea.Grid, index, key) },
                    onRemove = { index -> onRemove(SlotArea.Grid, index) },
                    onDragEnded = onDragEnded,
                    // A drag needs the grid's drop targets, so it takes priority over search results.
                    results = results.takeIf { !drawerState.dragging },
                    onLaunchResult = onLaunchResult,
                    homeMenu = homeMenu,
                    widgets = widgets,
                )
                DrawerOverlay(apps, drawerState, repository, onDrawerStateChange)
                PanelOverlay(visible = drawerState.overlay == Overlay.Widgets, dragging = drawerState.dragging) { modifier ->
                    WidgetPicker(
                        choices = widgets.choices,
                        grid = widgets.metrics,
                        onDragStart = { onDrawerStateChange(DrawerState(Overlay.Widgets, dragging = true)) },
                        modifier = modifier,
                    )
                }
            }
            // Always shown between the home area and the dock.
            inputArea()
            DockBar(
                slots = dock.resolve(apps, DockSize),
                dragging = drawerState.dragging,
                repository = repository,
                onDrawerToggle = {
                    onDrawerStateChange(if (drawerState.overlay == Overlay.None) DrawerState(Overlay.Apps) else DrawerState.Closed)
                },
                onPlace = { index, key -> onPlace(SlotArea.Dock, index, key) },
                onRemove = { index -> onRemove(SlotArea.Dock, index) },
                onDragEnded = onDragEnded,
            )
        }
    }
}

/** Dragging placed icons around the grid and dock. */
class SlotDrag(
    val from: SlotRef?,
    val onStart: (SlotRef) -> Unit,
    val onRearrange: (from: SlotRef, to: SlotRef) -> Unit,
)

private val LocalSlotDrag = staticCompositionLocalOf<SlotDrag?> { null }

/**
 * [columns] across; as many rows as fit the available height. Cells are numbered row by row.
 * While searching ([results] non-null), shortcuts are hidden and the bottom [ResultRows] rows show results,
 * best match in the bottom row, closest to the keyboard.
 */
@Composable
private fun HomeGrid(
    slots: AppSlots,
    apps: List<AppEntry>,
    columns: Int,
    dragging: Boolean,
    repository: AppRepository,
    onPlace: (index: Int, key: String) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDragEnded: () -> Unit,
    results: List<AppMatch>?,
    onLaunchResult: (AppEntry) -> Unit,
    homeMenu: HomeMenuActions,
    widgets: WidgetsUi,
) {
    // Where the home menu was requested; null when closed.
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Long-press on empty space. Icons consume their own long-press (Remove), so they never reach this.
            .pointerInput(Unit) { detectTapGestures(onLongPress = { menuAt = it }) }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Fixed geometry (rows share the height evenly) so widgets can be positioned by cell.
        val rows = (maxHeight / GridCellHeight).toInt().coerceAtLeast(1)
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows
        val metrics = GridMetrics(columns, rows, cellWidth.value, cellHeight.value)
        LaunchedEffect(metrics) { widgets.onMetrics(metrics) }

        val cells = slots.resolve(apps, rows * columns)
        val resultRows = results?.let { bottomUpRows(it, columns, minOf(ResultRows, rows)) }
        val searching = resultRows != null
        val shownWidgets = visibleWidgets(widgets.placed, columns, rows)
        val coveredCells = shownWidgets.flatMap { it.cells(columns) }.toSet()

        // The shortcut grid always stays composed, even while searching (then invisible and inert): a drag
        // only reaches drop targets that exist when it starts, and a drag can start while text is typed.
        val hidden = if (searching) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier
        GridRows(rows, hidden) { row ->
            for (col in 0 until columns) {
                val index = row * columns + col
                AppSlot(
                    area = SlotArea.Grid,
                    index = index,
                    // Cells under a widget hide their app (kept in storage) and refuse app drops.
                    app = cells[index].takeIf { index !in coveredCells },
                    description = "Home cell ${row + 1}, ${col + 1}",
                    showLabel = true,
                    showEmptyOutline = dragging && index !in coveredCells,
                    emphasizeOutline = dragging,
                    interactive = !searching,
                    acceptsApps = index !in coveredCells,
                    onWidgetDrop = { provider -> widgets.onDrop(row, col, provider, metrics) },
                    repository = repository,
                    onPlace = onPlace,
                    onRemove = onRemove,
                    onDragEnded = onDragEnded,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        shownWidgets.forEach { widget ->
            key(widget.id) {
                WidgetCell(
                    widget = widget,
                    host = widgets.host,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    // Hidden (and untouchable) behind search results.
                    visible = !searching,
                    onRemove = { widgets.onRemove(widget.id) },
                    onEdit = { widgets.onEdit(widget.id) },
                )
            }
        }
        shownWidgets.firstOrNull { it.id == widgets.editingId && !searching }?.let { editing ->
            key(editing) {
                WidgetEditor(
                    widget = editing,
                    limits = rememberSizeLimits(editing, metrics),
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    columns = columns,
                    rows = rows,
                    widgets = widgets.placed,
                    appCells = cells.indices.filter { cells[it] != null }.toSet(),
                    onCommit = widgets.onUpdate,
                    onNoRoom = widgets.onNoRoom,
                    onExit = { widgets.onEdit(null) },
                )
            }
        }
        if (resultRows != null) {
            // Same row layout as the grid; only the bottom rows get results, right above the keyboard.
            GridRows(rows) { row ->
                resultRows.getOrNull(row - (rows - resultRows.size))?.forEach { match ->
                    ResultCell(match, isTop = match != null && match == results.firstOrNull(), repository, onLaunchResult, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
    HomeMenu(at = menuAt, actions = homeMenu, onDismiss = { menuAt = null })
}

/** The active icon pack (null: system icons). Icons redraw when it changes. */
/** Identifies the current icon look (pack, themed colors); icons redraw when it changes. */
val LocalIconStyle = compositionLocalOf<String?> { null }

/** Apps (by [dotKey]) that have notifications; null when notification dots are off. */
val LocalNotificationDots = compositionLocalOf<Set<String>?> { null }

/** What the home long-press menu offers. */
class HomeMenuActions(
    val onWallpaper: () -> Unit,
    val onWidgets: () -> Unit,
    val onSettings: () -> Unit,
)

@Composable
private fun HomeMenu(at: Offset?, actions: HomeMenuActions, onDismiss: () -> Unit) {
    // Anchor the dropdown at the press point.
    Box(Modifier.offset { at?.round() ?: IntOffset.Zero }) {
        KeyspaceMenu(expanded = at != null, onDismissRequest = onDismiss) {
            listOf(
                "Wallpaper" to actions.onWallpaper,
                "Widgets" to actions.onWidgets,
                "Settings" to actions.onSettings,
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onDismiss()
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun GridRows(rows: Int, modifier: Modifier = Modifier, row: @Composable RowScope.(Int) -> Unit) {
    Column(modifier.fillMaxSize()) {
        for (index in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) { row(index) }
        }
    }
}

/** One search result: tap launches. Empty cells keep the row's spacing. */
@Composable
private fun ResultCell(
    match: AppMatch?,
    isTop: Boolean,
    repository: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (match != null) {
            // The one ambient motion: a result that newly appears in this cell rises 6dp and fades in.
            // Skipped when the system "Remove animations" setting is on.
            val context = LocalContext.current
            val reduceMotion = remember {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
            val appear = remember(match.app.key) { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(match.app.key) { appear.animateTo(1f, tween(durationMillis = 160)) }
            val rise = with(LocalDensity.current) { 6.dp.toPx() }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                AppIcon(
                    app = match.app,
                    repository = repository,
                    highlight = match.matchedIndices,
                    ring = isTop,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = appear.value
                            translationY = (1f - appear.value) * rise
                        }
                        .combinedClickable(onClick = { onLaunch(match.app) }, onLongClick = { menuOpen = true }),
                )
                AppActionsMenu(match.app, expanded = menuOpen, onDismiss = { menuOpen = false })
            }
        }
    }
}

@Composable
private fun DrawerOverlay(
    apps: List<AppEntry>,
    drawerState: DrawerState,
    repository: AppRepository,
    onDrawerStateChange: (DrawerState) -> Unit,
) {
    PanelOverlay(visible = drawerState.overlay == Overlay.Apps, dragging = drawerState.dragging) { modifier ->
        AppDrawer(
            apps = apps,
            repository = repository,
            modifier = modifier,
            onLaunch = { app ->
                repository.launch(app)
                onDrawerStateChange(DrawerState.Closed)
            },
            onDragStart = { onDrawerStateChange(DrawerState(Overlay.Apps, dragging = true)) },
        )
    }
}

/** Slides a panel over the home area. While [dragging], it stays composed but invisible (see [DrawerState]). */
@Composable
private fun PanelOverlay(visible: Boolean, dragging: Boolean, content: @Composable (Modifier) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        content(Modifier.alpha(if (dragging) 0f else 1f))
    }
}

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    repository: AppRepository,
    modifier: Modifier,
    onLaunch: (AppEntry) -> Unit,
    onDragStart: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .clip(KeyspaceTokens.PanelShape)
            .background(KeyspaceColors.panel)
            .semantics { contentDescription = "App drawer" },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            var menuOpen by remember { mutableStateOf(false) }
            val view = LocalView.current
            Box {
                AppIcon(
                    app = app,
                    repository = repository,
                    modifier = Modifier.appIconGestures(
                        app = app,
                        icon = { repository.loadIcon(app).bitmap },
                        onTap = { onLaunch(app) },
                        onLongPress = { menuOpen = true },
                        onDragStart = {
                            menuOpen = false
                            onDragStart()
                        },
                        view = view,
                    ),
                )
                AppActionsMenu(app, expanded = menuOpen, onDismiss = { menuOpen = false })
            }
        }
    }
}

@Composable
private fun DockBar(
    slots: List<AppEntry?>,
    dragging: Boolean,
    repository: AppRepository,
    onDrawerToggle: () -> Unit,
    onPlace: (index: Int, key: String) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDragEnded: () -> Unit,
) {
    val half = slots.size / 2
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, app ->
            if (index == half) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DrawerButton(onClick = onDrawerToggle)
                }
            }
            AppSlot(
                area = SlotArea.Dock,
                index = index,
                app = app,
                description = "Dock slot ${index + 1}",
                showLabel = false,
                // Like the grid: empty slots only show while something is being dragged.
                showEmptyOutline = dragging,
                emphasizeOutline = dragging,
                repository = repository,
                onPlace = onPlace,
                onRemove = onRemove,
                onDragEnded = onDragEnded,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One drop target cell: shows an app (tap launches, long-press offers Remove) or an empty outline. */
@Composable
private fun AppSlot(
    area: SlotArea,
    index: Int,
    app: AppEntry?,
    description: String,
    showLabel: Boolean,
    showEmptyOutline: Boolean,
    repository: AppRepository,
    onPlace: (index: Int, key: String) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDragEnded: () -> Unit,
    modifier: Modifier,
    emphasizeOutline: Boolean = false,
    /** False while hidden behind search results: stays a drop target but ignores taps. */
    interactive: Boolean = true,
    /** False for cells under a widget. */
    acceptsApps: Boolean = true,
    /** Handles a widget dropped with its top-left on this cell; null where widgets can't go (the dock). */
    onWidgetDrop: ((provider: String) -> Boolean)? = null,
) {
    var highlighted by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // The target object is remembered, so read the latest callbacks rather than the first ones captured.
    val currentOnPlace by rememberUpdatedState(onPlace)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)
    val currentAcceptsApps by rememberUpdatedState(acceptsApps)
    val currentOnWidgetDrop by rememberUpdatedState(onWidgetDrop)
    val slotDrag = LocalSlotDrag.current
    val currentSlotDrag by rememberUpdatedState(slotDrag)
    val here = SlotRef(area, index)
    val target = remember(here) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { highlighted = true }
            override fun onExited(event: DragAndDropEvent) { highlighted = false }
            override fun onEnded(event: DragAndDropEvent) {
                highlighted = false
                currentOnDragEnded()
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                val text = dragEvent.clipData?.getItemAt(0)?.text?.toString() ?: return false
                if (dragEvent.clipDescription?.label == WIDGET_CLIP_LABEL) return currentOnWidgetDrop?.invoke(text) ?: false
                if (!currentAcceptsApps) return false
                // A placed icon being moved: move or swap. From the drawer: place (replacing what's there).
                val origin = (dragEvent.localState as? KeyspaceDrag)?.origin
                if (origin != null) currentSlotDrag?.onRearrange?.invoke(origin, here) ?: return false
                else currentOnPlace(index, text)
                return true
            }
        }
    }

    Box(
        modifier = modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    val dragEvent = event.toAndroidDragEvent()
                    val isWidget = dragEvent.clipDescription?.label == WIDGET_CLIP_LABEL
                    // Only Keyspace's own drags: other apps can't supply this marker (see KeyspaceDrag).
                    dragEvent.localState is KeyspaceDrag &&
                        event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                        (!isWidget || onWidgetDrop != null)
                },
                target = target,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val ring = when {
            highlighted -> Color.White
            emphasizeOutline -> Color.White.copy(alpha = 0.6f)
            else -> Color.White.copy(alpha = 0.35f)
        }
        // Labeled icons are taller than wide, so they get a rounded box rather than a circle.
        val highlightShape = if (showLabel) KeyspaceTokens.SmallShape else CircleShape
        if (app == null) {
            if (showEmptyOutline || highlighted) EmptyIcon(ring, showLabel)
        } else {
            val view = LocalView.current
            Box {
                AppIcon(
                    app = app,
                    repository = repository,
                    showLabel = showLabel,
                    modifier = Modifier
                        // The icon being dragged stays in place, dimmed, until it's dropped.
                        .alpha(if (slotDrag?.from == here) 0.3f else 1f)
                        .then(if (highlighted) Modifier.border(2.dp, ring, highlightShape) else Modifier)
                        .then(
                            if (interactive) {
                                Modifier.appIconGestures(
                                    app = app,
                                    icon = { repository.loadIcon(app).bitmap },
                                    onTap = { repository.launch(app) },
                                    onLongPress = { menuOpen = true },
                                    onDragStart = {
                                        menuOpen = false
                                        slotDrag?.onStart?.invoke(here)
                                    },
                                    view = view,
                                    origin = here,
                                )
                            } else {
                                Modifier
                            }
                        ),
                )
                AppActionsMenu(app, expanded = menuOpen, onDismiss = { menuOpen = false }, onRemove = { onRemove(index) })
            }
        }
    }
}

@Composable
private fun DrawerButton(onClick: () -> Unit) {
    // Styled as a keycap so it reads as part of the keyboard, not as another app.
    val dots = KeyspaceColors.keyLit
    Box(
        modifier = Modifier
            .size(IconSize)
            .clip(KeyspaceTokens.SmallShape)
            .background(KeyspaceColors.key)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "All apps" },
        contentAlignment = Alignment.Center,
    ) {
        // 3x3 dot grid icon.
        Canvas(Modifier.size(20.dp)) {
            val step = size.width / 3
            val radius = step * 0.22f
            for (row in 0..2) for (col in 0..2) {
                drawCircle(dots, radius, Offset(step * (col + 0.5f), step * (row + 0.5f)))
            }
        }
    }
}

@Composable
private fun AppIcon(
    app: AppEntry,
    repository: AppRepository,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    /**
     * Label characters that matched a search. Shown bold at full strength with the rest of the label
     * dimmed; not in the accent color, which can vanish against a wallpaper of the same hue.
     */
    highlight: List<Int> = emptyList(),
    /** Accent ring marking the top search result; follows the icon's outline. */
    ring: Boolean = false,
) {
    // Adaptive icons already come masked to the system shape, and icon pack icons have their own. Legacy
    // icons have neither, so they get a round plate at 72% scale: every icon then has a consistent outline.
    val loaded = remember(app.key, LocalIconStyle.current) { repository.loadIcon(app) }
    val hasDot = LocalNotificationDots.current?.contains(app.dotKey) == true
    val icon = remember(loaded) { loaded.bitmap.asImageBitmap() }
    val legacy = loaded.needsPlate
    val plate = KeyspaceColors.iconPlate
    val accent = KeyspaceColors.accent
    val label = remember(app.label, highlight) {
        buildAnnotatedString {
            append(app.label)
            if (highlight.isNotEmpty()) {
                addStyle(SpanStyle(color = Color.White.copy(alpha = 0.75f)), 0, app.label.length)
                highlight.forEach { addStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold), it, it + 1) }
            }
        }
    }
    val description = if (hasDot) "${app.label}, has notifications" else app.label
    IconLayout(label = label, showLabel = showLabel, modifier = modifier.semantics { contentDescription = description }) {
        Box {
            Box(
                modifier = Modifier
                    .size(IconSize)
                    .then(if (ring) Modifier.outlineRing(accent, rounded = loaded.fromPack) else Modifier)
                    .then(if (legacy) Modifier.clip(CircleShape).background(plate) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize(if (legacy) 0.72f else 1f))
            }
            if (hasDot) NotificationDot(Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * A 2dp ring drawn just outside the icon, so a ringed icon stays the same size as its neighbors.
 * System and plated icons are round on this launcher; icon pack icons can be any shape, so they get a
 * rounded square that encloses whatever the pack drew.
 */
private fun Modifier.outlineRing(color: Color, rounded: Boolean): Modifier = drawWithContent {
    drawContent()
    val stroke = 2.dp.toPx()
    val gap = 3.dp.toPx()
    val inset = -(gap + stroke / 2)
    val topLeft = Offset(inset, inset)
    val ringSize = Size(size.width - 2 * inset, size.height - 2 * inset)
    if (rounded) {
        val radius = KeyspaceTokens.SmallRadius.toPx() + gap
        drawRoundRect(color, topLeft, ringSize, CornerRadius(radius), style = Stroke(stroke))
    } else {
        drawOval(color, topLeft, ringSize, style = Stroke(stroke))
    }
}

/** Accent dot with a ring in the panel color, so it reads on any icon and any wallpaper. */
@Composable
private fun NotificationDot(modifier: Modifier) {
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(KeyspaceColors.panel)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(KeyspaceColors.accent)
    )
}

/** Empty-cell outline, laid out exactly like an [AppIcon] so it sits where the icon will appear. */
@Composable
private fun EmptyIcon(ring: Color, showLabel: Boolean) {
    IconLayout(label = AnnotatedString(""), showLabel = showLabel) {
        Box(Modifier.size(IconSize).border(1.5.dp, ring, CircleShape))
    }
}

@Composable
private fun IconLayout(label: AnnotatedString, showLabel: Boolean, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(4.dp)) {
        icon()
        if (showLabel) {
            Text(
                text = label,
                color = Color.White,
                // Shadow keeps labels readable on light wallpaper.
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 4f),
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Everything the home grid needs to show and accept widgets. */
class WidgetsUi(
    val host: KeyspaceWidgetHost,
    val placed: List<PlacedWidget>,
    val choices: List<WidgetChoice>,
    val metrics: GridMetrics?,
    val onMetrics: (GridMetrics) -> Unit,
    /** A widget [provider] was dropped with its top-left at [row]/[col]; returns whether it was accepted. */
    val onDrop: (row: Int, col: Int, provider: String, grid: GridMetrics) -> Boolean,
    val onRemove: (id: Int) -> Unit,
    /** The widget being moved or resized, if any. */
    val editingId: Int?,
    val onEdit: (id: Int?) -> Unit,
    val onUpdate: (PlacedWidget) -> Unit,
    /** A move or resize was released where the widget doesn't fit. */
    val onNoRoom: () -> Unit,
)

@Composable
private fun WidgetCell(
    widget: PlacedWidget,
    host: KeyspaceWidgetHost,
    cellWidth: Dp,
    cellHeight: Dp,
    visible: Boolean,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val info = remember(widget.id) { AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.id) }
    var menuOpen by remember { mutableStateOf(false) }
    val width = cellWidth * widget.spanX
    val height = cellHeight * widget.spanY
    Box(
        Modifier
            .offset(x = cellWidth * widget.col, y = cellHeight * widget.row)
            .size(width, height)
            .padding(4.dp)
    ) {
        if (info == null) {
            // The widget's app was uninstalled; leave a removable placeholder rather than a silent gap.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(KeyspaceTokens.PanelShape)
                    .background(KeyspaceColors.panel)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true }),
                contentAlignment = Alignment.Center,
            ) { Text("Widget unavailable", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall) }
        } else {
            AndroidView(
                factory = { ctx -> host.createView(ctx, widget.id, info) as KeyspaceWidgetHostView },
                update = { view ->
                    view.onLongPress = { menuOpen = true }
                    view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                    // Only tell the widget its size when it changes; each call makes the widget redraw.
                    val size = width.value to height.value
                    if (view.tag != size) {
                        view.tag = size
                        view.setSizeDp(width.value, height.value)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        KeyspaceMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (info != null) {
                DropdownMenuItem(
                    text = { Text("Move or resize") },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

/** Resize limits for [widget] from its provider, in cells of the current grid. */
@Composable
private fun rememberSizeLimits(widget: PlacedWidget, grid: GridMetrics): WidgetSizeLimits {
    val context = LocalContext.current
    return remember(widget, grid) {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.id)
        val density = context.resources.displayMetrics.density
        if (info == null) {
            WidgetSizeLimits(widget.spanX, widget.spanY, widget.spanX, widget.spanY, horizontal = false, vertical = false)
        } else {
            val maxW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.maxResizeWidth else 0
            val maxH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.maxResizeHeight else 0
            sizeLimits(
                widget = widget,
                minWidthDp = info.minWidth / density,
                minHeightDp = info.minHeight / density,
                minResizeWidthDp = info.minResizeWidth / density,
                minResizeHeightDp = info.minResizeHeight / density,
                maxResizeWidthDp = maxW / density,
                maxResizeHeightDp = maxH / density,
                horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
                vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
                grid = grid,
            )
        }
    }
}

/**
 * Edit mode for one widget: drag the frame to move it, drag an edge handle to resize it in whole cells.
 * The frame previews where it will land (red when it won't fit); nothing changes until the drag ends.
 * Tapping anywhere else finishes.
 */
@Composable
private fun WidgetEditor(
    widget: PlacedWidget,
    limits: WidgetSizeLimits,
    cellWidth: Dp,
    cellHeight: Dp,
    columns: Int,
    rows: Int,
    widgets: List<PlacedWidget>,
    appCells: Set<Int>,
    onCommit: (PlacedWidget) -> Unit,
    onNoRoom: () -> Unit,
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val cellW = with(density) { cellWidth.toPx() }
    val cellH = with(density) { cellHeight.toPx() }
    var preview by remember { mutableStateOf(widget) }
    var valid by remember { mutableStateOf(true) }
    // Dynamic color makes the error color a pale pink close to the accent, so "won't fit" uses a fixed,
    // unmistakable red instead.
    val frameColor = if (valid) KeyspaceColors.accent else NoRoomRed

    fun finish() {
        when {
            !valid -> onNoRoom()
            preview != widget -> onCommit(preview)
        }
        preview = widget
        valid = true
    }

    /** Shows [candidate] (a valid placement) or, if null, [fallback] in the "won't fit" color. */
    fun show(candidate: PlacedWidget?, fallback: PlacedWidget) {
        preview = candidate ?: fallback
        valid = candidate != null
    }

    // Tap outside the widget: done.
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onExit() } })

    Box(
        Modifier
            .offset(x = cellWidth * preview.col, y = cellHeight * preview.row)
            .size(cellWidth * preview.spanX, cellHeight * preview.spanY)
            .padding(2.dp)
            .border(2.dp, frameColor, KeyspaceTokens.SmallShape)
            .background(frameColor.copy(alpha = 0.12f), KeyspaceTokens.SmallShape)
            .pointerInput(widget) {
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDragEnd = { finish() },
                    onDragCancel = { show(widget, widget) },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                    val row = (widget.row + (drag.y / cellH).roundToInt()).coerceIn(0, rows - widget.spanY)
                    val col = (widget.col + (drag.x / cellW).roundToInt()).coerceIn(0, columns - widget.spanX)
                    show(moveWidget(widget, row, col, columns, rows, widgets, appCells), widget.copy(row = row, col = col))
                }
            }
            .semantics { contentDescription = "Widget: drag to move" },
    ) {
        WidgetEdge.entries
            .filter { if (it == WidgetEdge.Left || it == WidgetEdge.Right) limits.horizontal else limits.vertical }
            .forEach { edge ->
                ResizeHandle(
                    edge = edge,
                    color = frameColor,
                    modifier = Modifier
                        .align(
                            when (edge) {
                                WidgetEdge.Left -> Alignment.CenterStart
                                WidgetEdge.Right -> Alignment.CenterEnd
                                WidgetEdge.Top -> Alignment.TopCenter
                                WidgetEdge.Bottom -> Alignment.BottomCenter
                            }
                        )
                        .pointerInput(widget, edge) {
                            var drag = Offset.Zero
                            detectDragGestures(
                                onDragStart = { drag = Offset.Zero },
                                onDragEnd = { finish() },
                                onDragCancel = { show(widget, widget) },
                            ) { change, amount ->
                                change.consume()
                                drag += amount
                                val horizontalEdge = edge == WidgetEdge.Left || edge == WidgetEdge.Right
                                val delta = if (horizontalEdge) (drag.x / cellW).roundToInt() else (drag.y / cellH).roundToInt()
                                val resized = resizeWidget(widget, edge, delta, limits, columns, rows, widgets, appCells)
                                // Invalid sizes still preview (in the "won't fit" color), kept within the grid.
                                val naive = when (edge) {
                                    WidgetEdge.Left -> widget.copy(col = widget.col + delta, spanX = widget.spanX - delta)
                                    WidgetEdge.Right -> widget.copy(spanX = widget.spanX + delta)
                                    WidgetEdge.Top -> widget.copy(row = widget.row + delta, spanY = widget.spanY - delta)
                                    WidgetEdge.Bottom -> widget.copy(spanY = widget.spanY + delta)
                                }.takeIf { it.spanX >= 1 && it.spanY >= 1 && it.fitsIn(columns, rows) } ?: preview
                                show(resized, naive)
                            }
                        },
                )
            }
    }
}

private val NoRoomRed = Color(0xFFFF5449)

@Composable
private fun ResizeHandle(edge: WidgetEdge, color: Color, modifier: Modifier) {
    val label = "Resize from ${edge.name.lowercase()} edge"
    // A generous touch target with a small visible pill.
    Box(modifier.size(36.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        val vertical = edge == WidgetEdge.Left || edge == WidgetEdge.Right
        Box(
            Modifier
                .size(if (vertical) 6.dp else 24.dp, if (vertical) 24.dp else 6.dp)
                .background(color, CircleShape)
        )
    }
}
