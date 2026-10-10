package ai.stxt.keyspace

import android.content.ClipData
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the app long-press menu can do, provided once for the whole home screen. */
class AppActions(
    val repository: AppRepository,
    val onRename: (AppEntry) -> Unit,
)

val LocalAppActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }

/**
 * The long-press menu for an app, wherever it appears: the app's own shortcuts (when Keyspace is the
 * default home app), App info, Uninstall, Rename, and [onRemove] for shortcuts placed on the home screen.
 */
@Composable
fun AppActionsMenu(app: AppEntry, expanded: Boolean, onDismiss: () -> Unit, onRemove: (() -> Unit)? = null) {
    val actions = LocalAppActions.current
    val repo = actions.repository
    // Loaded off the main thread each time the menu opens; shortcuts change as apps update them.
    val shortcuts by produceState(emptyList<Pair<ShortcutInfo, Bitmap?>>(), app.key, expanded) {
        value = if (expanded) {
            withContext(Dispatchers.IO) { repo.shortcuts(app).map { it to repo.shortcutIcon(it) } }
        } else {
            emptyList()
        }
    }
    val uninstall by produceState(UninstallKind.None, app.key, expanded) {
        value = if (expanded) withContext(Dispatchers.IO) { repo.uninstallKind(app) } else UninstallKind.None
    }

    fun act(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }

    KeyspaceMenu(expanded = expanded, onDismissRequest = onDismiss) {
        shortcuts.forEach { (shortcut, icon) ->
            DropdownMenuItem(
                text = { Text(shortcut.shortLabel?.toString() ?: shortcut.id) },
                leadingIcon = icon?.let { { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp)) } },
                onClick = act { repo.launchShortcut(shortcut) },
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(text = { Text("App info") }, onClick = act { repo.openAppInfo(app) })
        // Named for what Android will actually offer, so the confirmation that follows isn't a surprise.
        when (uninstall) {
            UninstallKind.App -> DropdownMenuItem(text = { Text("Uninstall") }, onClick = act { repo.uninstall(app) })
            UninstallKind.Updates -> DropdownMenuItem(text = { Text("Uninstall updates") }, onClick = act { repo.uninstall(app) })
            UninstallKind.None -> Unit
        }
        DropdownMenuItem(text = { Text("Rename") }, onClick = act { actions.onRename(app) })
        if (onRemove != null) DropdownMenuItem(text = { Text("Remove from home") }, onClick = act(onRemove))
    }
}

/**
 * Drawer gestures, matching the Pixel launcher: tap launches; long-press opens the app menu; keep holding
 * and move, and the menu closes and the app is dragged (to the grid or dock). Movement before the
 * long-press is left alone so the drawer can scroll.
 */
fun Modifier.drawerAppGestures(
    app: AppEntry,
    icon: () -> Bitmap,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    view: View,
): Modifier = this
    .pointerInput(app.key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var moved = 0f
            // Phase 1: tap, scroll, or long-press? True: lifted; false: moved (a scroll); null: timed out (a long-press).
            val released: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var lifted: Boolean? = null
                while (lifted == null) {
                    val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        lifted = true
                    } else {
                        moved += change.positionChange().getDistance()
                        // Moving before the long-press is a scroll: let the drawer have it.
                        if (moved > slop || change.isConsumed) lifted = false
                    }
                }
                lifted
            }
            when (released) {
                true -> if (moved <= slop) onTap()
                false -> Unit
                null -> {
                    // Phase 2: long-press. Show the menu; dragging on from here starts a drag.
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongPress()
                    val start = currentEvent.changes.firstOrNull()?.position ?: down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Keep the drawer from scrolling under a held icon.
                        change.consume()
                        if (!change.pressed) break
                        if ((change.position - start).getDistance() > slop) {
                            onDragStart()
                            startAppDrag(view, app, icon())
                            break
                        }
                    }
                }
            }
        }
    }
    .semantics {
        onClick(label = "Open ${app.label}") { onTap(); true }
        customActions = listOf(CustomAccessibilityAction("App options") { onLongPress(); true })
    }

/** Starts the same drag the drop targets accept (see [KeyspaceDrag]), with the icon as the drag image. */
private fun startAppDrag(view: View, app: AppEntry, icon: Bitmap) {
    val sizePx = (56 * view.resources.displayMetrics.density).toInt()
    val shadow = object : View.DragShadowBuilder(view) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(sizePx, sizePx)
            outShadowTouchPoint.set(sizePx / 2, sizePx / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.drawBitmap(icon, null, Rect(0, 0, sizePx, sizePx), null)
        }
    }
    view.startDragAndDrop(ClipData.newPlainText("app", app.key), shadow, KeyspaceDrag, 0)
}
