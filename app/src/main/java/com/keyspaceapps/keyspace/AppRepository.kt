package com.keyspaceapps.keyspace

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app icon rendered at a fixed size. [needsPlate]: a legacy icon that gets a round plate.
 * [fromPack]: drawn by an icon pack, so its outline can be any shape (not the system circle).
 */
class LoadedIcon(val bitmap: Bitmap, val needsPlate: Boolean, val fromPack: Boolean = false) {
    /** For drawing in Compose; wraps [bitmap] once rather than on every recomposition. */
    val image: ImageBitmap = bitmap.asImageBitmap()
}

/**
 * Apps across all of the user's profiles (personal, work, …), via [LauncherApps], the API Android
 * provides for home screens. Also launches apps and their shortcuts and opens App info / Uninstall.
 */
enum class UninstallKind { None, App, Updates }

class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val ownProfile = Process.myUserHandle()

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        launcherApps.profiles.flatMap { profile ->
            val serial = if (profile == ownProfile) null else userManager.getSerialNumberForUser(profile)
            launcherApps.getActivityList(null, profile).map { info ->
                AppEntry(
                    label = info.label.toString(),
                    packageName = info.componentName.packageName,
                    className = info.componentName.className,
                    profileSerial = serial,
                )
            }
        }.forHomeScreen(selfPackage = context.packageName)
    }

    fun userOf(app: AppEntry): UserHandle =
        app.profileSerial?.let { userManager.getUserForSerialNumber(it) } ?: ownProfile

    /**
     * Calls [onChange] (on the main thread) when apps are installed, removed or updated in any profile,
     * or a profile is turned on or off. Returns a function that stops listening.
     */
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()

            private fun changed() {
                // An update can change an icon, so drop cached icons too.
                icons.evictAll()
                onChange()
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return { launcherApps.unregisterCallback(callback) }
    }

    // Read from the icon prefetch thread as well as the main thread.
    @Volatile private var iconPack: IconPackLoader? = null
    /** Background and glyph colors for themed icons; null when themed icons are off. */
    @Volatile private var themeColors: Pair<Int, Int>? = null

    /** Turns themed icons on (with these wallpaper-derived colors) or off (null). */
    fun setThemedIcons(colors: Pair<Int, Int>?) {
        if (colors == themeColors) return
        themeColors = colors
        icons.evictAll()
    }
    /** Icons are rendered at the size they're drawn: 48dp, lifted (see LIFT_SCALE). */
    private val iconPx = ceil(48 * context.resources.displayMetrics.density * LIFT_SCALE).toInt()

    /** Cached icons, limited by memory (1/8 of what the app may use) rather than by count. */
    private val icons = object : LruCache<String, LoadedIcon>(cacheBytes(context)) {
        override fun sizeOf(key: String, value: LoadedIcon) = value.bitmap.allocationByteCount
    }

    /** The icon if it's already rendered; never does any work, so it's safe to call while drawing. */
    fun cachedIcon(app: AppEntry): LoadedIcon? = icons.get(app.key)

    /**
     * Renders every app's icon in the background (drawer order), so scrolling never waits for one. Call after the
     * app list or the icon style changes. Stops early if [isActive] turns false (a newer prefetch replaced it).
     */
    fun prefetchIcons(apps: List<AppEntry>, isActive: () -> Boolean = { true }) {
        for (app in apps) {
            if (!isActive()) return
            if (icons.get(app.key) == null) loadIcon(app)
        }
    }

    /** Switches icon pack (null: system icons). Returns false if the pack couldn't be loaded. */
    fun setIconPack(packageName: String?): Boolean {
        if (packageName == iconPack?.packageName) return true
        icons.evictAll()
        iconPack = packageName?.let { IconPackLoader.load(context, it) }
        return packageName == null || iconPack != null
    }

    fun loadIcon(app: AppEntry): LoadedIcon {
        icons.get(app.key)?.let { return it }
        val original = pm.getActivityIcon(app.component)
        // Icon packs are third-party apps, so anything they supply is untrusted: a drawable with no size,
        // a malformed resource or an enormous image must not take down the home screen. Any failure falls
        // back to the app's own icon.
        val packIcon = iconPack?.let { pack ->
            try {
                pack.iconFor(app, original)
            } catch (e: Exception) {
                Log.w(TAG, "Icon pack ${pack.packageName} failed for ${app.key}", e)
                null
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Icon pack ${pack.packageName} image too large for ${app.key}", e)
                null
            }
        }
        // Always drawn at a fixed size, never at the drawable's own reported size (which can be -1 or huge).
        // Pack icons are drawn to their own shape; only original legacy icons need the round plate.
        // Precedence: icon pack (mapped or styled) > themed icon > the app's own icon.
        val loaded = if (packIcon != null) {
            render(packIcon, app)?.let { LoadedIcon(it, needsPlate = false, fromPack = true) }
        } else {
            themed(original)?.let { render(it, app) }?.let { LoadedIcon(it, needsPlate = false) }
        } ?: LoadedIcon(
            // A blank icon is better than a crash if even the app's own icon can't be drawn.
            render(original, app) ?: createBitmap(iconPx, iconPx),
            needsPlate = original !is AdaptiveIconDrawable,
        )
        icons.put(app.key, loaded)
        return loaded
    }

    /**
     * The app's monochrome icon (Android 13+) drawn in the theme colors on a round plate; null when themed
     * icons are off or the app has no monochrome icon (it then keeps its own icon, as on Pixel).
     */
    private fun themed(original: Drawable): Drawable? {
        val (background, glyph) = themeColors ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || original !is AdaptiveIconDrawable) return null
        val mono = original.monochrome ?: return null
        val size = iconPx
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        // Adaptive icon layers are drawn 50% larger than the icon, centered, with the edges masked off.
        val extra = size / 4
        mono.mutate().setTint(glyph)
        mono.setBounds(-extra, -extra, size + extra, size + extra)
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        mono.draw(canvas)
        canvas.restore()
        return BitmapDrawable(context.resources, out)
    }

    /** Renders at the fixed icon size, adding the work badge for other profiles. Null if the drawable fails. */
    private fun render(drawable: Drawable, app: AppEntry): Bitmap? = try {
        val badged = if (app.isOtherProfile) pm.getUserBadgedIcon(drawable, userOf(app)) else drawable
        badged.toBitmap(iconPx, iconPx)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't render icon for ${app.key}", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Icon too large for ${app.key}", e)
        null
    }

    fun launch(app: AppEntry) {
        val user = userOf(app)
        // A paused work profile: ask Android to turn it back on (allowed for the default home app).
        if (app.isOtherProfile && userManager.isQuietModeEnabled(user)) {
            runCatching { userManager.requestQuietModeEnabled(false, user) }
                .onFailure { Log.w(TAG, "Couldn't resume profile for ${app.key}", it) }
            return
        }
        try {
            launcherApps.startMainActivity(app.component, user, null, null)
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException: the app went away or was disabled.
            Log.w(TAG, "Couldn't launch ${app.key}", e)
        }
    }

    /** The app's own shortcuts (e.g. "New incognito tab"); empty unless Keyspace is the default home app. */
    fun shortcuts(app: AppEntry): List<ShortcutInfo> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(query, userOf(app)).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            // Manifest (static) shortcuts first, as the app ordered them, then dynamic ones.
            .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .take(MAX_SHORTCUTS)
    }

    fun shortcutIcon(shortcut: ShortcutInfo): Bitmap? = try {
        launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)?.toBitmap(iconPx, iconPx)
    } catch (e: Exception) {
        null
    }

    fun launchShortcut(shortcut: ShortcutInfo) {
        try {
            launcherApps.startShortcut(shortcut, null, null)
        } catch (e: Exception) {
            // The shortcut was removed or disabled since the menu opened.
            Log.w(TAG, "Couldn't start shortcut ${shortcut.id}", e)
        }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, userOf(app), null, null) }
            .onFailure { Log.w(TAG, "Couldn't open app info for ${app.key}", it) }
    }

    /**
     * What "Uninstall" would do: remove the app, remove only its updates (a built-in app that was updated),
     * or nothing (a built-in app that was never updated).
     */
    fun uninstallKind(app: AppEntry): UninstallKind {
        val info = runCatching { launcherApps.getApplicationInfo(app.packageName, 0, userOf(app)) }.getOrNull()
            ?: return UninstallKind.None
        return when {
            info.flags and ApplicationInfo.FLAG_SYSTEM == 0 -> UninstallKind.App
            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> UninstallKind.Updates
            else -> UninstallKind.None
        }
    }

    fun uninstall(app: AppEntry) {
        // Android shows its own confirmation; nothing is removed without the user agreeing there.
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, userOf(app))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No uninstaller for ${app.key}", e)
        }
    }

    private companion object {
        fun cacheBytes(context: Context): Int {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java).memoryClass
            return memoryClassMb * 1024 * 1024 / 8
        }

        const val MAX_SHORTCUTS = 4
        const val TAG = "AppRepository"
    }
}
