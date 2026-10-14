package com.keyspaceapps.keyspace

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    private fun app(label: String) = AppEntry(label, "pkg.${label.replace(" ", "")}", "Main")
    private val apps = listOf("YT Music", "Maps", "Messages", "Phone", "Google Photos", "Play Store", "Gmail", "Clock").map(::app)
    private val now = 1_000L * AppUsage.DAY_MILLIS.toLong()
    private fun labels(query: String, usage: Map<String, AppUsage> = emptyMap()) =
        searchApps(apps, query, usage, now).map { it.label }

    /** Usage from [launches] launches made [daysAgo] days before [now]. */
    private fun usage(launches: Int, daysAgo: Double): AppUsage {
        val at = now - (daysAgo * AppUsage.DAY_MILLIS).toLong()
        return (1 until launches).fold(AppUsage.firstLaunch(at)) { u, _ -> u.recordLaunch(at) }
    }
    private fun key(label: String) = app(label).key

    @Test
    fun `first letter matches label starts and later word starts, case-insensitive`() {
        assertEquals(listOf("Maps", "Messages", "YT Music"), labels("m"))
        assertEquals(listOf("Maps", "Messages", "YT Music"), labels("M"))
    }

    @Test
    fun `second letter narrows the results`() {
        assertEquals(listOf("Maps"), labels("ma"))
    }

    @Test
    fun `whole label matches rank before later word matches`() {
        assertEquals(listOf("Phone", "Play Store", "Google Photos"), labels("p"))
    }

    @Test
    fun `most launched apps rank first`() {
        val usage = mapOf(key("Google Photos") to usage(3, daysAgo = 0.0), key("Play Store") to usage(1, daysAgo = 0.0))
        assertEquals(listOf("Google Photos", "Play Store", "Phone"), labels("p", usage))
    }

    @Test
    fun `a recently used app outranks one with more launches long ago`() {
        val usage = mapOf(key("Phone") to usage(50, daysAgo = 60.0), key("Play Store") to usage(3, daysAgo = 1.0))
        assertEquals(listOf("Play Store", "Phone", "Google Photos"), labels("p", usage))
    }

    @Test
    fun `multi-word queries match across words`() {
        assertEquals(listOf("Play Store"), labels("play st"))
    }

    @Test
    fun `spaces are ignored, since the keyboard has no space key`() {
        assertEquals(listOf("Play Store"), labels("playst"))
        assertEquals(listOf("Play Store"), labels("play store"))
    }

    @Test
    fun `matches only start at word starts`() {
        assertEquals(emptyList<String>(), labels("ays"))
    }

    @Test
    fun `matched indices point at the label characters that matched`() {
        fun indices(query: String, label: String) =
            matchApps(apps, query, emptyMap(), now).first { it.app.label == label }.matchedIndices
        assertEquals(listOf(0, 1), indices("ma", "Maps"))
        assertEquals(listOf(3, 4), indices("mu", "YT Music"))
        assertEquals(listOf(0, 1, 2, 3, 5, 6), indices("playst", "Play Store"))
    }

    @Test
    fun `next letters are the ones that continue a match`() {
        // "g": Gmail -> m, Google Photos -> o.
        assertEquals(setOf('m', 'o'), nextLetters(apps, "g"))
        // "play" -> Play Store continues with s (across the space).
        assertEquals(setOf('s'), nextLetters(apps, "play"))
    }

    @Test
    fun `next letters are null when nothing is typed and empty when nothing matches`() {
        assertEquals(null, nextLetters(apps, ""))
        assertEquals(emptySet<Char>(), nextLetters(apps, "zz"))
    }

    @Test
    fun `blank query matches nothing`() {
        assertEquals(emptyList<String>(), labels(""))
        assertEquals(emptyList<String>(), labels("  "))
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList<String>(), labels("zz"))
    }

    @Test
    fun `bottomUpRows puts the best results in the last row`() {
        val results = (1..7).map { app("App$it") }
        val rows = bottomUpRows(results, columns = 5, rows = 2)
        assertEquals(listOf("App6", "App7", null, null, null), rows[0].map { it?.label })
        assertEquals(listOf("App1", "App2", "App3", "App4", "App5"), rows[1].map { it?.label })
    }

    @Test
    fun `bottomUpRows drops results that don't fit`() {
        val results = (1..12).map { app("App$it") }
        val shown = bottomUpRows(results, columns = 5, rows = 2).flatten().mapNotNull { it?.label }
        assertEquals(10, shown.size)
        assertEquals(false, "App11" in shown)
    }
}
