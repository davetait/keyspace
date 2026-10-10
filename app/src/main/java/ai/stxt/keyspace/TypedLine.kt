package ai.stxt.keyspace

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Shadow keeps the line readable where the wallpaper behind it is light.
private val LineShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 4f)

/**
 * Shows what has been typed, with a blinking cursor. Plain text (not a TextField) so the system
 * keyboard can never open. Left-aligned with the keyboard so the eye runs from the text to the keys
 * and to the best result.
 *
 * - Nothing typed: a dim hint, so a first-time user knows what typing does.
 * - [status] (e.g. a speech error) replaces the text until the next input.
 * - [noMatches]: says so beside the text instead of leaving an unexplained empty space.
 */
@Composable
fun TypedLine(text: String, status: String?, noMatches: Boolean, modifier: Modifier = Modifier) {
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1000
                1f at 0
                0f at 500
            },
            RepeatMode.Restart,
        ),
        label = "cursor",
    )
    val textColor = MaterialTheme.colorScheme.onSurface
    // Fixed white rather than a theme color: this sits on the wallpaper (under the scrim), not on a panel.
    val dimColor = Color.White.copy(alpha = 0.72f)
    val lineStyle = KeyspaceTokens.TypedLine.copy(color = textColor, shadow = LineShadow)
    val noteStyle = MaterialTheme.typography.bodyMedium.copy(color = dimColor, shadow = LineShadow)
    val noMatchText = "No apps start with “$text”"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
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
                Box(Modifier.padding(horizontal = 2.dp).width(2.dp).height(32.dp).alpha(blink).background(textColor))
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
