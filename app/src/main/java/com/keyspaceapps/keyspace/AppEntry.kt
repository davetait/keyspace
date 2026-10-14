package com.keyspaceapps.keyspace

import android.content.ComponentName

/** One launchable activity shown on the home screen. Plain values keep it JVM-testable. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    /**
     * Serial number of the profile (e.g. work) this app belongs to; null for the user's own profile.
     * Own-profile keys stay "pkg/cls" so saved placements and history from before profiles existed still match.
     */
    val profileSerial: Long? = null,
    /** The app's own name, before any rename. */
    val originalLabel: String = label,
) {
    val component: ComponentName get() = ComponentName(packageName, className)
    val key: String get() = "$packageName/$className" + (profileSerial?.let { "#$it" } ?: "")
    val isOtherProfile: Boolean get() = profileSerial != null
}

/** Removes this launcher (in the user's own profile) and duplicates, then sorts by label (case-insensitive). */
fun List<AppEntry>.forHomeScreen(selfPackage: String): List<AppEntry> =
    filter { !(it.packageName == selfPackage && !it.isOtherProfile) }
        .distinctBy { it.key }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

/** Applies the user's custom names ([labels], keyed by [AppEntry.key]); blank names are ignored. */
fun List<AppEntry>.withCustomLabels(labels: Map<String, String>): List<AppEntry> =
    map { app -> labels[app.key]?.takeIf { it.isNotBlank() }?.let { app.copy(label = it) } ?: app }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
