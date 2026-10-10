package ai.stxt.keyspace

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Row1 = "qwertyuiop".toList()
private val Row2 = "asdfghjkl".toList()
private val Row3 = "zxcvbnm".toList()

private val KeyHeight = 48.dp

/**
 * App-drawn QWERTY keyboard: letters, backspace, microphone. Always shown; it has no hide/close action
 * and never involves the system keyboard (IME).
 */
@Composable
fun Keyboard(
    listening: Boolean,
    /** Letters that continue at least one app match; the rest are dimmed. Null: nothing typed, all lit. */
    litKeys: Set<Char>?,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // No background of its own: the home screen's bottom scrim sits behind it.
            .padding(horizontal = 3.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Rows are laid out in 10 units so every row spans the full width and keys line up.
        val isLit = { c: Char -> litKeys == null || c in litKeys }
        KeyRow { Row1.forEach { LetterKey(it, isLit(it), onLetter) } }
        KeyRow {
            Spacer(Modifier.weight(0.5f))
            Row2.forEach { LetterKey(it, isLit(it), onLetter) }
            Spacer(Modifier.weight(0.5f))
        }
        KeyRow {
            Key(
                description = if (listening) "Stop dictation" else "Dictate",
                weight = 1.5f,
                color = if (listening) KeyspaceColors.accent else null,
                onPress = onMic,
            ) { MicIcon(if (listening) MaterialTheme.colorScheme.onPrimary else KeyspaceColors.keyLit) }
            Row3.forEach { LetterKey(it, isLit(it), onLetter) }
            Key(description = "Backspace", weight = 1.5f, repeatOnHold = true, onPress = onBackspace) {
                BackspaceIcon(KeyspaceColors.keyLit)
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(KeyHeight), content = content)
}

@Composable
private fun RowScope.LetterKey(letter: Char, lit: Boolean, onLetter: (Char) -> Unit) {
    // Dimmed keys still type (the user may know better); they just signal that no app continues that way.
    val legend by animateColorAsState(if (lit) KeyspaceColors.keyLit else KeyspaceColors.keyDim, label = "legend")
    Key(
        description = letter.toString(),
        state = if (lit) null else "No matching apps",
        onPress = { onLetter(letter) },
    ) {
        Text(letter.toString(), color = legend, style = KeyspaceTokens.KeyLegend)
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
    /** Null: the standard key fill. */
    color: Color? = null,
    /** Spoken by screen readers after the key's name, e.g. that it leads nowhere. */
    state: String? = null,
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
            .clip(KeyspaceTokens.SmallShape)
            .background(if (pressed) KeyspaceColors.keyPressed else color ?: KeyspaceColors.key)
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
                if (state != null) stateDescription = state
                role = Role.Button
                onClick { currentOnPress(); true }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MicIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        // Capsule body.
        drawRoundRect(color, Offset(w * 0.33f, 0f), Size(w * 0.34f, w * 0.6f), CornerRadius(w * 0.17f))
        // Cradle arc, stem, base.
        drawArc(color, 0f, 180f, false, Offset(w * 0.2f, w * 0.2f), Size(w * 0.6f, w * 0.55f), style = stroke)
        drawLine(color, Offset(w * 0.5f, w * 0.75f), Offset(w * 0.5f, w * 0.92f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, w * 0.95f), Offset(w * 0.68f, w * 0.95f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun BackspaceIcon(color: Color) {
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
        drawPath(outline, color, style = stroke)
        drawLine(color, Offset(w * 0.48f, h * 0.38f), Offset(w * 0.72f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.72f, h * 0.38f), Offset(w * 0.48f, h * 0.62f), stroke.width, StrokeCap.Round)
    }
}
