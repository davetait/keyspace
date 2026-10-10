package ai.stxt.keyspace

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/** Google's picker ("Wallpaper & style"); preferred when several built-in pickers exist. */
private const val GOOGLE_WALLPAPER_PICKER = "com.google.android.apps.wallpaper"
private const val LIVE_WALLPAPER_PICKER = "com.android.wallpaper.livepicker"

/**
 * Opens the device's own wallpaper screen directly instead of an "open with" sheet.
 * Prefers Google's picker, then any other built-in (system) picker; falls back to a chooser.
 */
fun Context.openWallpaperPicker() {
    val base = Intent(Intent.ACTION_SET_WALLPAPER)
    val systemPickers = packageManager.queryIntentActivities(base, 0)
        .map { it.activityInfo }
        .filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 && it.packageName != LIVE_WALLPAPER_PICKER }
    val picker = systemPickers.firstOrNull { it.packageName == GOOGLE_WALLPAPER_PICKER } ?: systemPickers.firstOrNull()
    val intent = if (picker != null) {
        Intent(base).setComponent(ComponentName(picker.packageName, picker.name))
    } else {
        Intent.createChooser(base, "Choose wallpaper")
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
