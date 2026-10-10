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
}
