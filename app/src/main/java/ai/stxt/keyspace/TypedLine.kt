package ai.stxt.keyspace

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LineStyle = TextStyle(
    color = Color.White,
    fontSize = 24.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 4f),
)

/**
 * Shows what has been typed, with a blinking cursor. Plain text (not a TextField) so the system
 * keyboard can never open. [status] (e.g. a speech error) replaces the text until the next input.
 */
@Composable
fun TypedLine(text: String, status: String?, modifier: Modifier = Modifier) {
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp)
            .semantics {
                contentDescription = status ?: text.ifEmpty { "Nothing typed" }
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        if (status != null) {
            Text(status, style = LineStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.75f)))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Long text keeps its end visible, where typing happens.
                Text(text, style = LineStyle, maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.padding(start = 2.dp).width(2.dp).height(28.dp).alpha(blink).background(Color.White))
            }
        }
    }
}
