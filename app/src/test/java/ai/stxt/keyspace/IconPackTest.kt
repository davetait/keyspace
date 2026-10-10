package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class IconPackTest {

    private fun parse(xml: String): AppFilter =
        parseAppFilter(KXmlParser().apply { setInput(StringReader(xml)) })

    private val sample = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <!-- comment -->
            <iconback img1="back_a" img2="back_b" />
            <iconmask img1="mask" />
            <iconupon img1="shine" />
            <scale factor="0.7" />
            <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome" />
            <item component="ComponentInfo{com.example/.Main}" drawable="example" />
            <item component="ComponentInfo{{broken/oops}" drawable="bad" />
            <item component="not a component" drawable="bad2" />
            <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="duplicate" />
            <calendar component="ComponentInfo{com.google.android.calendar/com.android.calendar.AllInOneActivity}" prefix="cal_" />
            <dynamic-clock drawable="clock" />
        </resources>
    """.trimIndent()

    @Test
    fun `parses items, keeping the first mapping for a component`() {
        val filter = parse(sample)
        assertEquals("chrome", filter.items["com.android.chrome/com.google.android.apps.chrome.Main"])
    }

    @Test
    fun `expands shorthand class names`() {
        assertEquals("example", parse(sample).items["com.example/com.example.Main"])
    }

    @Test
    fun `tolerates stray braces but skips entries with no component`() {
        val filter = parse(sample)
        // Doubled brace, a typo seen in real packs: still usable.
        assertEquals("bad", filter.items["broken/oops"])
        assertEquals(false, "bad2" in filter.items.values)
        assertEquals(3, filter.items.size)
    }

    @Test
    fun `parses calendars and unthemed styling`() {
        val filter = parse(sample)
        assertEquals("cal_", filter.calendars["com.google.android.calendar/com.android.calendar.AllInOneActivity"])
        assertEquals(listOf("back_a", "back_b"), filter.backs)
        assertEquals("mask", filter.mask)
        assertEquals("shine", filter.upon)
        assertEquals(0.7f, filter.scale, 1e-6f)
    }

    @Test
    fun `defaults when styling is absent`() {
        val filter = parse("<resources><item component=\"ComponentInfo{a/a.B}\" drawable=\"b\"/></resources>")
        assertEquals(emptyList<String>(), filter.backs)
        assertNull(filter.mask)
        assertEquals(1f, filter.scale, 0f)
    }

    @Test
    fun `out of range scale is ignored`() {
        assertEquals(1f, parse("<resources><scale factor=\"-3\"/></resources>").scale, 0f)
        assertEquals(1f, parse("<resources><scale factor=\"abc\"/></resources>").scale, 0f)
    }

    @Test
    fun `parseComponent handles real-world variants`() {
        assertEquals("a.b/a.b.C", parseComponent("ComponentInfo{a.b/a.b.C}"))
        assertEquals("a.b/a.b.C", parseComponent(" ComponentInfo{a.b/a.b.C} "))
        assertNull(parseComponent("ComponentInfo{a.b}"))
        assertNull(parseComponent(null))
    }

    @Test
    fun `stops reading after the entry cap`() {
        val many = (1..MAX_APPFILTER_ELEMENTS + 50).joinToString("") {
            "<item component=\"ComponentInfo{p$it/p$it.A}\" drawable=\"d\"/>"
        }
        val filter = parse("<resources>$many</resources>")
        // The root <resources> element counts toward the cap too.
        assertEquals(MAX_APPFILTER_ELEMENTS - 1, filter.items.size)
    }
}
