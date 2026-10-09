package ai.stxt.keyspace

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Row1 = "qwertyuiop".toList()
private val Row2 = "asdfghjkl".toList()
private val Row3 = "zxcvbnm".toList()

private val KeyHeight = 48.dp
private val KeyColor = Color.White.copy(alpha = 0.16f)
private val KeyPressedColor = Color.White.copy(alpha = 0.40f)
private val MicActiveColor = Color(0xFF4285F4)

/**
 * App-drawn QWERTY keyboard: letters, backspace, microphone. Always shown; it has no hide/close action
 * and never involves the system keyboard (IME).
 */
@Composable
fun Keyboard(
    listening: Boolean,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 3.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Rows are laid out in 10 units so every row spans the full width and keys line up.
        KeyRow { Row1.forEach { LetterKey(it, onLetter) } }
        KeyRow {
            Spacer(Modifier.weight(0.5f))
            Row2.forEach { LetterKey(it, onLetter) }
            Spacer(Modifier.weight(0.5f))
        }
        KeyRow {
            Key(
                description = if (listening) "Stop dictation" else "Dictate",
                weight = 1.5f,
                color = if (listening) MicActiveColor else KeyColor,
                onPress = onMic,
            ) { MicIcon() }
            Row3.forEach { LetterKey(it, onLetter) }
            Key(description = "Backspace", weight = 1.5f, repeatOnHold = true, onPress = onBackspace) { BackspaceIcon() }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(KeyHeight), content = content)
}

@Composable
private fun RowScope.LetterKey(letter: Char, onLetter: (Char) -> Unit) {
    Key(description = letter.toString(), onPress = { onLetter(letter) }) {
        Text(letter.toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Normal)
    }
}

/**
 * One key. Fires on touch down (like system keyboards) with haptic feedback. With [repeatOnHold],
 * holding repeats after a short delay.
 */
@Composable
private fun RowScope.Key(
    description: String,
    onPress: () -> Unit,
    weight: Float = 1f,
    color: Color = KeyColor,
    repeatOnHold: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val currentOnPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxSize()
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) KeyPressedColor else color)
            .pointerInput(repeatOnHold) {
                // Gesture-handling code can't launch coroutines itself, so the repeat job runs in this outer scope.
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        currentOnPress()
                        val repeater = if (repeatOnHold) {
                            launch {
                                delay(400)
                                while (true) {
                                    currentOnPress()
                                    delay(50)
                                }
                            }
                        } else {
                            null
                        }
                        waitForUpOrCancellation()
                        repeater?.cancel()
                        pressed = false
                    }
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { currentOnPress(); true }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MicIcon() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        // Capsule body.
        drawRoundRect(Color.White, Offset(w * 0.33f, 0f), Size(w * 0.34f, w * 0.6f), CornerRadius(w * 0.17f))
        // Cradle arc, stem, base.
        drawArc(Color.White, 0f, 180f, false, Offset(w * 0.2f, w * 0.2f), Size(w * 0.6f, w * 0.55f), style = stroke)
        drawLine(Color.White, Offset(w * 0.5f, w * 0.75f), Offset(w * 0.5f, w * 0.92f), stroke.width, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.32f, w * 0.95f), Offset(w * 0.68f, w * 0.95f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun BackspaceIcon() {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val outline = Path().apply {
            moveTo(w * 0.32f, h * 0.22f)
            lineTo(w * 0.92f, h * 0.22f)
            lineTo(w * 0.92f, h * 0.78f)
            lineTo(w * 0.32f, h * 0.78f)
            lineTo(w * 0.06f, h * 0.5f)
            close()
        }
        drawPath(outline, Color.White, style = stroke)
        drawLine(Color.White, Offset(w * 0.48f, h * 0.38f), Offset(w * 0.72f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.72f, h * 0.38f), Offset(w * 0.48f, h * 0.62f), stroke.width, StrokeCap.Round)
    }
}
