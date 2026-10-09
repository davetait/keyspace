package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedTextTest {

    @Test
    fun `type appends a letter`() {
        assertEquals("ab", TypedText.type("a", 'b'))
    }

    @Test
    fun `backspace removes the last character and is safe on empty text`() {
        assertEquals("a", TypedText.backspace("ab"))
        assertEquals("", TypedText.backspace(""))
    }

    @Test
    fun `backspace removes a whole emoji`() {
        assertEquals("a", TypedText.backspace("a😀"))
    }

    @Test
    fun `appendSpoken adds a single space between existing text and words`() {
        assertEquals("hello world", TypedText.appendSpoken("hello", " world "))
        assertEquals("world", TypedText.appendSpoken("", "world"))
        assertEquals("hello ", TypedText.appendSpoken("hello ", "   "))
    }
}
