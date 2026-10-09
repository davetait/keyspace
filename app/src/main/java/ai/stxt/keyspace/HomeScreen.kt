package ai.stxt.keyspace

import android.content.ClipData
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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

enum class DrawerState { Closed, Open, Dragging }

/** The two places app shortcuts can be dropped. */
enum class SlotArea { Grid, Dock }

private val IconSize = 48.dp
private val GridCellHeight = 96.dp
private const val DockSize = 4

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
    inputArea: @Composable () -> Unit,
) {
    val onDragEnded = { if (drawerState == DrawerState.Dragging) onDrawerStateChange(DrawerState.Closed) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Home area: the shortcut grid, with the drawer opening over it.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HomeGrid(
                slots = grid,
                apps = apps,
                columns = gridColumns,
                dragging = drawerState == DrawerState.Dragging,
                repository = repository,
                onPlace = { index, key -> onPlace(SlotArea.Grid, index, key) },
                onRemove = { index -> onRemove(SlotArea.Grid, index) },
                onDragEnded = onDragEnded,
            )
            DrawerOverlay(apps, drawerState, repository, onDrawerStateChange)
        }
        // Always shown between the home area and the dock.
        inputArea()
        DockBar(
            slots = dock.resolve(apps, DockSize),
            dragging = drawerState == DrawerState.Dragging,
            repository = repository,
            onDrawerToggle = {
                onDrawerStateChange(if (drawerState == DrawerState.Closed) DrawerState.Open else DrawerState.Closed)
            },
            onPlace = { index, key -> onPlace(SlotArea.Dock, index, key) },
            onRemove = { index -> onRemove(SlotArea.Dock, index) },
            onDragEnded = onDragEnded,
        )
    }
}

/** [columns] across; as many rows as fit the available height. Cells are numbered row by row. */
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
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        val rows = (maxHeight / GridCellHeight).toInt().coerceAtLeast(1)
        val cells = slots.resolve(apps, rows * columns)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth().height(GridCellHeight)) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        AppSlot(
                            index = index,
                            app = cells[index],
                            description = "Home cell ${row + 1}, ${col + 1}",
                            showLabel = true,
                            showEmptyOutline = dragging,
                            emphasizeOutline = dragging,
                            repository = repository,
                            onPlace = onPlace,
                            onRemove = onRemove,
                            onDragEnded = onDragEnded,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
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
    AnimatedVisibility(
        visible = drawerState != DrawerState.Closed,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        // While dragging, the drawer stays composed (so the drag isn't cancelled) but is invisible.
        AppDrawer(
            apps = apps,
            repository = repository,
            modifier = Modifier.alpha(if (drawerState == DrawerState.Dragging) 0f else 1f),
            onLaunch = { app ->
                repository.launch(app)
                onDrawerStateChange(DrawerState.Closed)
            },
            onDragStart = { onDrawerStateChange(DrawerState.Dragging) },
        )
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
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .semantics { contentDescription = "App drawer" },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            AppIcon(
                app = app,
                repository = repository,
                modifier = Modifier.appDragSource(
                    key = app.key,
                    label = app.label,
                    onTap = { onLaunch(app) },
                    onDragStart = onDragStart,
                ),
            )
        }
    }
}

/**
 * Tap launches; long-press starts a drag carrying [key].
 *
 * `clickable` can't be combined with `dragAndDropSource`: depending on modifier order, one consumes the
 * touch and the other never fires (verified on the API 37 emulator). So the tap is detected in the
 * Initial pass, before the drag detector sees the event, and nothing is consumed.
 */
private fun Modifier.appDragSource(key: String, label: String, onTap: () -> Unit, onDragStart: () -> Unit): Modifier =
    this
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                val quick = up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                val stayed = (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                if (quick && stayed) onTap()
            }
        }
        .semantics { onClick(label = "Open $label") { onTap(); true } }
        .dragAndDropSource { _ ->
            onDragStart()
            DragAndDropTransferData(ClipData.newPlainText("app", key))
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
                index = index,
                app = app,
                description = "Dock slot ${index + 1}",
                showLabel = false,
                // Dock slots always show, so it's clear where shortcuts go; the grid only shows them mid-drag.
                showEmptyOutline = true,
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
) {
    var highlighted by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // The target object is remembered, so read the latest callbacks rather than the first ones captured.
    val currentOnPlace by rememberUpdatedState(onPlace)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)
    val target = remember(index) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { highlighted = true }
            override fun onExited(event: DragAndDropEvent) { highlighted = false }
            override fun onEnded(event: DragAndDropEvent) {
                highlighted = false
                currentOnDragEnded()
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val key = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: return false
                currentOnPlace(index, key)
                return true
            }
        }
    }

    Box(
        modifier = modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
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
        val highlightShape = if (showLabel) RoundedCornerShape(16.dp) else CircleShape
        if (app == null) {
            if (showEmptyOutline || highlighted) EmptyIcon(ring, showLabel)
        } else {
            Box {
                AppIcon(
                    app = app,
                    repository = repository,
                    showLabel = showLabel,
                    modifier = Modifier
                        .then(if (highlighted) Modifier.border(2.dp, ring, highlightShape) else Modifier)
                        .combinedClickable(
                            onClick = { repository.launch(app) },
                            onLongClick = { menuOpen = true },
                        ),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            menuOpen = false
                            onRemove(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(IconSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "All apps" },
        contentAlignment = Alignment.Center,
    ) {
        // 3x3 dot grid icon.
        Canvas(Modifier.size(20.dp)) {
            val step = size.width / 3
            val radius = step * 0.22f
            for (row in 0..2) for (col in 0..2) {
                drawCircle(Color(0xFF202124), radius, Offset(step * (col + 0.5f), step * (row + 0.5f)))
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
) {
    val icon = remember(app.key) { repository.loadIcon(app).toBitmap().asImageBitmap() }
    IconLayout(label = app.label, showLabel = showLabel, modifier = modifier.semantics { contentDescription = app.label }) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(IconSize))
    }
}

/** Empty-cell outline, laid out exactly like an [AppIcon] so it sits where the icon will appear. */
@Composable
private fun EmptyIcon(ring: Color, showLabel: Boolean) {
    IconLayout(label = "", showLabel = showLabel) {
        Box(Modifier.size(IconSize).border(1.5.dp, ring, CircleShape))
    }
}

@Composable
private fun IconLayout(label: String, showLabel: Boolean, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
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
