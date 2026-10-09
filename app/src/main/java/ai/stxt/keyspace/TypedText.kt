package ai.stxt.keyspace

/** Text-editing rules for the on-screen keyboard. Plain functions so they're JVM-testable. */
object TypedText {

    fun type(text: String, letter: Char): String = text + letter

    /** Deletes the last character, keeping surrogate pairs (e.g. emoji from speech) intact. */
    fun backspace(text: String): String {
        if (text.isEmpty()) return text
        val drop = if (text.length >= 2 && text.last().isLowSurrogate() && text[text.length - 2].isHighSurrogate()) 2 else 1
        return text.dropLast(drop)
    }

    /** Appends dictated words, separated from existing text by one space. */
    fun appendSpoken(text: String, spoken: String): String {
        val words = spoken.trim()
        return when {
            words.isEmpty() -> text
            text.isEmpty() || text.endsWith(' ') -> text + words
            else -> "$text $words"
        }
    }
}
