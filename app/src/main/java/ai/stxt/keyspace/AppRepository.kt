package ai.stxt.keyspace

import android.content.Context
import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app icon, and whether it needs a plate to match the round icon shape. */
class LoadedIcon(val drawable: Drawable, val needsPlate: Boolean)

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
        val packIcon = iconPack?.iconFor(app, original)
        // Pack icons are drawn to their own shape; only original legacy icons need the round plate.
        val loaded = if (packIcon != null) LoadedIcon(packIcon, needsPlate = false) else LoadedIcon(original, original !is AdaptiveIconDrawable)
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
    }
}
