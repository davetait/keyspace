package com.keyspaceapps.keyspace

import android.view.DragEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** One place something can be dropped: its bounds (in the host view) and what happens on a drop. */
class DropTarget(
    var bounds: Rect = Rect.Zero,
    /** Handles a drop of [text] (an app key, or a widget provider when [isWidget]); returns whether it was accepted. */
    var onDrop: (text: String, isWidget: Boolean, marker: KeyspaceDrag) -> Boolean = { _, _, _ -> false },
)

/**
 * Handles every drag over the home screen in one place: finds the cell under the finger, highlights it, and
 * delivers the drop. Replaces Compose's per-target drag routing, which on the API 37 emulator often failed to
 * route a drag to any cell when the drag started from an icon on the home screen (placed icons, search
 * results); only drawer drags were reliable.
 */
class DropTargets {
    private val targets = mutableMapOf<SlotRef, DropTarget>()

    /** The cell currently under a drag, for highlighting. */
    var hovered by mutableStateOf<SlotRef?>(null)
        private set

    /** Called once when any drag ends (dropped anywhere, or cancelled). */
    var onDragEnded: () -> Unit = {}

    fun register(ref: SlotRef, target: DropTarget) {
        targets[ref] = target
    }

    fun unregister(ref: SlotRef, target: DropTarget) {
        if (targets[ref] === target) targets.remove(ref)
    }

    private fun at(x: Float, y: Float): Pair<SlotRef, DropTarget>? =
        targets.entries.firstOrNull { it.value.bounds.contains(Offset(x, y)) }?.toPair()

    val listener = View.OnDragListener { _, event ->
        // Only Keyspace's own drags: other apps can't supply this marker (see KeyspaceDrag).
        val marker = event.localState as? KeyspaceDrag
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> marker != null
            DragEvent.ACTION_DRAG_LOCATION -> {
                hovered = at(event.x, event.y)?.first
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                hovered = null
                true
            }
            DragEvent.ACTION_DROP -> {
                hovered = null
                val text = event.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                val target = at(event.x, event.y)?.second
                if (marker == null || text == null || target == null) {
                    false
                } else {
                    target.onDrop(text, event.clipDescription?.label == WIDGET_CLIP_LABEL, marker)
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                hovered = null
                onDragEnded()
                true
            }
            else -> true
        }
    }
}

val LocalDropTargets = staticCompositionLocalOf<DropTargets?> { null }
