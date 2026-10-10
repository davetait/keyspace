package ai.stxt.keyspace

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareKeysTest {

    @Test
    fun `letters, digits and space type lowercase`() {
        assertEquals(KeyAction.Type('g'), hardwareKeyAction(KeyEvent.KEYCODE_G, 'G'.code, false))
        assertEquals(KeyAction.Type('7'), hardwareKeyAction(KeyEvent.KEYCODE_7, '7'.code, false))
        assertEquals(KeyAction.Type(' '), hardwareKeyAction(KeyEvent.KEYCODE_SPACE, ' '.code, false))
    }

    @Test
    fun `editing keys map to actions`() {
        assertEquals(KeyAction.Backspace, hardwareKeyAction(KeyEvent.KEYCODE_DEL, 0, false))
        assertEquals(KeyAction.Launch, hardwareKeyAction(KeyEvent.KEYCODE_ENTER, '\n'.code, false))
        assertEquals(KeyAction.Escape, hardwareKeyAction(KeyEvent.KEYCODE_ESCAPE, 0, false))
    }

    @Test
    fun `shortcuts, punctuation and non-printing keys are left alone`() {
        assertEquals(null, hardwareKeyAction(KeyEvent.KEYCODE_C, 'c'.code, hasCommandModifier = true))
        assertEquals(null, hardwareKeyAction(KeyEvent.KEYCODE_PERIOD, '.'.code, false))
        assertEquals(null, hardwareKeyAction(KeyEvent.KEYCODE_VOLUME_UP, 0, false))
        assertEquals(null, hardwareKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, 0, false))
    }
}
