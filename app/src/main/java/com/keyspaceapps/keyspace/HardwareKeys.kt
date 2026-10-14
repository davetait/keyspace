package com.keyspaceapps.keyspace

import android.view.KeyEvent

/** What a physical keyboard key does on the home screen. */
sealed interface KeyAction {
    data class Type(val char: Char) : KeyAction
    data object Backspace : KeyAction
    /** Launch the top search result. */
    data object Launch : KeyAction
    /** Clear the typed text, or close the open panel if nothing is typed. */
    data object Escape : KeyAction
}

/**
 * Maps a key press to an action; null for keys the home screen leaves alone (navigation, volume,
 * shortcuts with Ctrl/Alt/Meta). Typed text is lowercase, like the on-screen keys.
 */
fun hardwareKeyAction(keyCode: Int, unicodeChar: Int, hasCommandModifier: Boolean): KeyAction? {
    if (hasCommandModifier) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_DEL -> KeyAction.Backspace
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyAction.Launch
        KeyEvent.KEYCODE_ESCAPE -> KeyAction.Escape
        else -> unicodeChar.takeIf { it > 0 }?.toChar()
            ?.takeIf { it.isLetterOrDigit() || it == ' ' }
            ?.let { KeyAction.Type(it.lowercaseChar()) }
    }
}
