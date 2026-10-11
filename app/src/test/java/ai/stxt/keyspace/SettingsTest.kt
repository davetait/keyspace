package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun `missing values use defaults`() {
        assertEquals(KeyspaceSettings(5, AdaptSpeed.Normal), KeyspaceSettings.parse(null, null))
    }

    @Test
    fun `stored values are read back`() {
        assertEquals(KeyspaceSettings(7, AdaptSpeed.Fast), KeyspaceSettings.parse(7, "Fast"))
    }

    @Test
    fun `out of range or unknown values are corrected`() {
        assertEquals(KeyspaceSettings(4, AdaptSpeed.Normal), KeyspaceSettings.parse(1, "Turbo"))
        assertEquals(7, KeyspaceSettings.parse(12, null).columns)
    }

    @Test
    fun `icon pack is read back, and blank means system icons`() {
        assertEquals("com.example.pack", KeyspaceSettings.parse(5, "Normal", "com.example.pack").iconPack)
        assertEquals(null, KeyspaceSettings.parse(5, "Normal", " ").iconPack)
        assertEquals(null, KeyspaceSettings.parse(null, null).iconPack)
    }

    @Test
    fun `cursor is invisible unless a known style is stored`() {
        assertEquals(CursorStyle.Invisible, KeyspaceSettings.parse(null, null).cursorStyle)
        assertEquals(CursorStyle.Solid, KeyspaceSettings.parse(5, "Normal", cursorStyle = "Solid").cursorStyle)
        assertEquals(CursorStyle.Blinking, KeyspaceSettings.parse(5, "Normal", cursorStyle = "Blinking").cursorStyle)
        assertEquals(CursorStyle.Invisible, KeyspaceSettings.parse(5, "Normal", cursorStyle = "Flashing").cursorStyle)
    }

    @Test
    fun `search line is shown unless turned off`() {
        assertEquals(true, KeyspaceSettings.parse(null, null).showSearchLine)
        assertEquals(false, KeyspaceSettings.parse(5, "Normal", showSearchLine = false).showSearchLine)
    }
}
