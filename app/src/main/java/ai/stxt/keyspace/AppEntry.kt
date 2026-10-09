package ai.stxt.keyspace

import android.content.ComponentName

/** One launchable activity shown on the home screen. Plain strings keep it JVM-testable. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
) {
    val component: ComponentName get() = ComponentName(packageName, className)
    val key: String get() = "$packageName/$className"
}

/** Removes this launcher and duplicates, then sorts by label (case-insensitive). */
fun List<AppEntry>.forHomeScreen(selfPackage: String): List<AppEntry> =
    filter { it.packageName != selfPackage }
        .distinctBy { it.key }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
