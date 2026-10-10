package ai.stxt.keyspace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app icon rendered at a fixed size. [needsPlate]: a legacy icon that gets a round plate.
 * [fromPack]: drawn by an icon pack, so its outline can be any shape (not the system circle).
 */
class LoadedIcon(val bitmap: Bitmap, val needsPlate: Boolean, val fromPack: Boolean = false)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { info ->
                AppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    className = info.activityInfo.name,
                )
            }
            .forHomeScreen(selfPackage = context.packageName)
    }

    private var iconPack: IconPackLoader? = null
    private val icons = LruCache<String, LoadedIcon>(ICON_CACHE_SIZE)

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
                pack.iconFor(app, original)?.toBitmap(ICON_PX, ICON_PX)
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
        val loaded = if (packIcon != null) {
            LoadedIcon(packIcon, needsPlate = false, fromPack = true)
        } else {
            LoadedIcon(original.toBitmap(ICON_PX, ICON_PX), needsPlate = original !is AdaptiveIconDrawable)
        }
        icons.put(app.key, loaded)
        return loaded
    }

    fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }

    private companion object {
        const val ICON_CACHE_SIZE = 200
        const val ICON_PX = 192
        const val TAG = "AppRepository"
    }
}
