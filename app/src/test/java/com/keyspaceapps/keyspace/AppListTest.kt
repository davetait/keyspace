package com.keyspaceapps.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListTest {

    private fun app(label: String, pkg: String) = AppEntry(label, pkg, "$pkg.Main")

    @Test
    fun `excludes self, removes duplicates, sorts case-insensitively`() {
        val input = listOf(
            app("zebra", "z.app"),
            app("Keyspace", "com.keyspaceapps.keyspace"),
            app("Alpha", "a.app"),
            app("Alpha", "a.app"),
            app("beta", "b.app"),
        )

        val result = input.forHomeScreen(selfPackage = "com.keyspaceapps.keyspace")

        assertEquals(listOf("Alpha", "beta", "zebra"), result.map { it.label })
    }

    @Test
    fun `own-profile keys are unchanged, other profiles are tagged`() {
        assertEquals("a.app/a.app.Main", app("A", "a.app").key)
        assertEquals("a.app/a.app.Main#10", app("A", "a.app").copy(profileSerial = 10).key)
    }

    @Test
    fun `work copy of this launcher and of other apps is kept`() {
        val work = app("Keyspace", "com.keyspaceapps.keyspace").copy(profileSerial = 10)
        val result = listOf(app("Keyspace", "com.keyspaceapps.keyspace"), work).forHomeScreen("com.keyspaceapps.keyspace")
        assertEquals(listOf(work), result)
    }

    @Test
    fun `custom labels rename and re-sort, keeping the original`() {
        val renamed = listOf(app("Chrome", "c.app"), app("Maps", "m.app"))
            .withCustomLabels(mapOf("c.app/c.app.Main" to "Web", "m.app/m.app.Main" to " "))
        assertEquals(listOf("Maps", "Web"), renamed.map { it.label })
        assertEquals("Chrome", renamed.last().originalLabel)
    }
}
