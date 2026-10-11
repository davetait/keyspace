package ai.stxt.keyspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Shadow keeps the line readable where the wallpaper behind it is light.
private val LineShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 4f)

/**
 * Shows what has been typed, followed by a cursor in [cursorStyle]. Plain text (not a TextField) so the system
 * keyboard can never open. Left-aligned with the keyboard so the eye runs from the text to the keys
 * and to the best result.
 *
 * - Nothing typed: a dim hint, so a first-time user knows what typing does.
 * - [status] (e.g. a speech error) replaces the text until the next input.
 * - [noMatches]: says so beside the text instead of leaving an unexplained empty space.
 */
@Composable
fun TypedLine(
    text: String,
    status: String?,
    noMatches: Boolean,
    cursorStyle: CursorStyle,
    modifier: Modifier = Modifier,
    /**
     * Shown over the home grid (the search line setting is off) rather than above the keyboard: no hint, nothing
     * at all until something is typed, and messages get a backing so they read over icons.
     */
    floating: Boolean = false,
) {
    if (floating && text.isEmpty() && status == null) return
    // A plain on/off blink (as in text fields), not a fade: a fade redraws the whole screen every frame, forever,
    // costing battery and competing with scrolling; this redraws twice a second. Solid while typing.
    // Solid and Invisible run no timer at all, so an idle home screen doesn't redraw.
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(text, cursorStyle) {
        cursorOn = cursorStyle != CursorStyle.Invisible
        if (cursorStyle != CursorStyle.Blinking) return@LaunchedEffect
        while (true) {
            delay(CURSOR_BLINK_MILLIS)
            cursorOn = !cursorOn
        }
    }
    val textColor = MaterialTheme.colorScheme.onSurface
    // Fixed white rather than a theme color: this sits on the wallpaper (under the scrim), not on a panel.
    val dimColor = Color.White.copy(alpha = 0.72f)
    val lineStyle = KeyspaceTokens.TypedLine.copy(color = textColor, shadow = LineShadow)
    val noteStyle = MaterialTheme.typography.bodyMedium.copy(color = dimColor, shadow = LineShadow)
    val noMatchText = "No apps start with “$text”"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (floating && status != null) Modifier.background(KeyspaceColors.panel) else Modifier)
            .height(SearchLineHeight)
            // Lines up with the keyboard's first key.
            .padding(horizontal = 12.dp)
            .semantics {
                contentDescription = when {
                    status != null -> status
                    text.isEmpty() -> "Type to find an app"
                    noMatches -> noMatchText
                    else -> text
                }
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            status != null -> Text(status, style = noteStyle)
            else -> {
                // Long text keeps its end visible, where typing happens.
                Text(text, style = lineStyle, maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f, fill = false))
                // Kept in the layout even when invisible, so the text doesn't shift when the style changes.
                Box(Modifier.padding(horizontal = 2.dp).width(2.dp).height(32.dp).graphicsLayer { alpha = if (cursorOn) 1f else 0f }.background(textColor))
                if (text.isEmpty()) {
                    Text("Type to find an app", style = noteStyle, modifier = Modifier.padding(start = 6.dp))
                } else if (noMatches) {
                    Spacer(Modifier.weight(1f))
                    Text(noMatchText, style = noteStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val CURSOR_BLINK_MILLIS = 530L

/** Height of the typed-text line; the grid gains a row in its place when it's hidden. */
val SearchLineHeight = 56.dp
