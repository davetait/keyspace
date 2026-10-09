package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListTest {

    private fun app(label: String, pkg: String) = AppEntry(label, pkg, "$pkg.Main")

    @Test
    fun `excludes self, removes duplicates, sorts case-insensitively`() {
        val input = listOf(
            app("zebra", "z.app"),
            app("Keyspace", "ai.stxt.keyspace"),
            app("Alpha", "a.app"),
            app("Alpha", "a.app"),
            app("beta", "b.app"),
        )

        val result = input.forHomeScreen(selfPackage = "ai.stxt.keyspace")

        assertEquals(listOf("Alpha", "beta", "zebra"), result.map { it.label })
    }
}
