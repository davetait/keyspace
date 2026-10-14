package com.keyspaceapps.keyspace

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * Sets Keyspace's own wallpaper on the home screen only; the lock screen keeps what the user has. Only ever
 * called when the user picks it (Keyspace never replaces a wallpaper on its own). Returns whether it was set.
 */
suspend fun Context.applyKeyspaceWallpaper(): Boolean = withContext(Dispatchers.IO) {
    val manager = WallpaperManager.getInstance(this@applyKeyspaceWallpaper)
    // Some devices (work-managed, kiosks) don't allow apps to change the wallpaper.
    if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return@withContext false
    try {
        // The original JPEG, with a crop hint keeping the middle of the image (the key is centered in it), so it
        // stays centered whichever home app is used later.
        val crop = centeredCrop(keyspaceWallpaperSize(), screenSize())
        resources.openRawResource(R.raw.keyspace_wallpaper).use { stream ->
            manager.setStream(stream, crop, true, WallpaperManager.FLAG_SYSTEM)
        }
        true
    } catch (e: Exception) {
        // IOException or SecurityException: report failure rather than crash.
        Log.w("Wallpaper", "Couldn't set the Keyspace wallpaper", e)
        false
    }
}

/**
 * Keyspace has a single page, so it tells Android to show the middle of the wallpaper. Without this, Android
 * falls back to a default position, and wallpapers wider than the screen (most are) appear shifted to one side.
 * Call when the window is attached and on resume.
 */
fun Activity.centerWallpaper() {
    val token = window.decorView.windowToken ?: return
    val manager = WallpaperManager.getInstance(this)
    // No pages to scroll between: one position, the middle.
    manager.setWallpaperOffsetSteps(0f, 0f)
    manager.setWallpaperOffsets(token, 0.5f, 0.5f)
}

/** The largest rectangle with the screen's shape, centered in an image of [image] size. */
fun centeredCrop(image: Size, screen: Size): Rect {
    val screenAspect = screen.width.toFloat() / screen.height
    return if (image.width.toFloat() / image.height > screenAspect) {
        // Image is wider than the screen: keep the full height, trim the sides equally.
        val width = (image.height * screenAspect).toInt()
        val left = (image.width - width) / 2
        Rect(left, 0, left + width, image.height)
    } else {
        // Image is taller: keep the full width, trim top and bottom equally.
        val height = (image.width / screenAspect).toInt()
        val top = (image.height - height) / 2
        Rect(0, top, image.width, top + height)
    }
}

private fun Context.keyspaceWallpaperSize(): Size {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resources.openRawResource(R.raw.keyspace_wallpaper).use { BitmapFactory.decodeStream(it, null, options) }
    return Size(options.outWidth, options.outHeight)
}

/** The full screen size in portrait (wallpapers cover the whole screen, including the system bars). */
private fun Context.screenSize(): Size {
    val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    } else {
        val metrics = resources.displayMetrics
        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
    return Size(minOf(bounds.width(), bounds.height()), maxOf(bounds.width(), bounds.height()))
}
