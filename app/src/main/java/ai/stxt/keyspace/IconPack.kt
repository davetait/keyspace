package ai.stxt.keyspace

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.time.LocalDate

/*
 * Icon packs: ordinary apps following the convention shared by third-party launchers (ADW, Nova, Lawnchair…).
 * A pack is found by its intent filter and maps apps to its own drawables in appfilter.xml.
 */

/** Intent actions icon packs declare so launchers can find them. Most packs declare several. */
val ICON_PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES",
    "com.novalauncher.THEME",
    "com.anddoes.launcher.THEME",
    "com.gau.go.launcherex.theme",
    "ch.deletescape.lawnchair.ICONPACK",
)

data class IconPackInfo(val packageName: String, val label: String)

fun findIconPacks(context: Context): List<IconPackInfo> {
    val pm = context.packageManager
    return ICON_PACK_ACTIONS
        .flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }
        .distinctBy { it.activityInfo.packageName }
        .map { IconPackInfo(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString()) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, IconPackInfo::label))
}

/**
 * Parsed appfilter.xml. [items] and [calendars] are keyed by "package/class".
 * [backs], [mask], [upon] and [scale] style apps the pack doesn't cover.
 */
data class AppFilter(
    val items: Map<String, String> = emptyMap(),
    /** Drawable name prefix; the day of the month (1–31) is appended. */
    val calendars: Map<String, String> = emptyMap(),
    val backs: List<String> = emptyList(),
    val mask: String? = null,
    val upon: String? = null,
    val scale: Float = 1f,
)

/** "ComponentInfo{pkg/cls}" → "pkg/cls"; null if malformed. Real packs contain typos, e.g. doubled braces. */
fun parseComponent(raw: String?): String? {
    val inner = raw?.trim()?.removePrefix("ComponentInfo")?.trim('{', '}', ' ') ?: return null
    val parts = inner.split('/')
    if (parts.size != 2 || parts.any { it.isBlank() || it.contains('{') || it.contains('}') }) return null
    val (pkg, cls) = parts
    // ".Main" is shorthand for "pkg.Main", as in manifests.
    return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
}

/** Reads appfilter.xml. Unknown tags and malformed entries are skipped, never fatal. */
fun parseAppFilter(parser: XmlPullParser): AppFilter {
    val items = mutableMapOf<String, String>()
    val calendars = mutableMapOf<String, String>()
    val backs = mutableListOf<String>()
    var mask: String? = null
    var upon: String? = null
    var scale = 1f
    fun attr(name: String): String? = parser.getAttributeValue(null, name)?.takeIf { it.isNotBlank() }
    /** img1, img2, … in order. */
    fun images(): List<String> = (1..10).mapNotNull { attr("img$it") }

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) continue
        when (parser.name) {
            "item" -> {
                val component = parseComponent(attr("component")) ?: continue
                val drawable = attr("drawable") ?: continue
                // First mapping wins, matching how most launchers behave with duplicate entries.
                items.putIfAbsent(component, drawable)
            }
            "calendar" -> {
                val component = parseComponent(attr("component")) ?: continue
                calendars.putIfAbsent(component, attr("prefix") ?: continue)
            }
            "iconback" -> backs += images()
            "iconmask" -> mask = images().firstOrNull() ?: mask
            "iconupon" -> upon = images().firstOrNull() ?: upon
            "scale" -> scale = attr("factor")?.toFloatOrNull()?.takeIf { it > 0f && it <= 2f } ?: scale
        }
    }
    return AppFilter(items, calendars, backs, mask, upon, scale)
}

/** One installed icon pack, with its appfilter parsed once. */
class IconPackLoader private constructor(
    val packageName: String,
    private val res: Resources,
    private val filter: AppFilter,
) {

    /** The pack's icon for [app]; a styled version of [original] if unmapped and the pack styles those; else null. */
    fun iconFor(app: AppEntry, original: Drawable, today: LocalDate = LocalDate.now()): Drawable? {
        filter.calendars[app.key]?.let { prefix -> drawable(prefix + today.dayOfMonth)?.let { return it } }
        filter.items[app.key]?.let { name -> drawable(name)?.let { return it } }
        return styleUnthemed(app, original)
    }

    private fun styleUnthemed(app: AppEntry, original: Drawable): Drawable? {
        if (filter.backs.isEmpty()) return null
        // Several backgrounds: pick one per app, stable across restarts.
        val back = drawable(filter.backs[Math.floorMod(app.key.hashCode(), filter.backs.size)]) ?: return null
        val size = ICON_PX
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        back.setBounds(0, 0, size, size)
        back.draw(canvas)

        // The original icon, scaled down and cut by the mask, on its own layer so the mask doesn't eat the background.
        val inner = createBitmap(size, size)
        val innerCanvas = Canvas(inner)
        val scaled = (size * filter.scale).toInt()
        val offset = (size - scaled) / 2
        original.toBitmap(scaled, scaled).let { innerCanvas.drawBitmap(it, offset.toFloat(), offset.toFloat(), null) }
        filter.mask?.let(::drawable)?.let { mask ->
            val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            innerCanvas.drawBitmap(mask.toBitmap(size, size), 0f, 0f, maskPaint)
        }
        canvas.drawBitmap(inner, 0f, 0f, null)

        filter.upon?.let(::drawable)?.let { upon ->
            upon.setBounds(0, 0, size, size)
            upon.draw(canvas)
        }
        return BitmapDrawable(res, out)
    }

    @SuppressLint("DiscouragedApi") // Icon packs are addressed by resource name; there is no other way.
    private fun drawable(name: String): Drawable? = try {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
        id?.let { res.getDrawable(it, null) }
    } catch (e: Resources.NotFoundException) {
        null
    }

    companion object {
        private const val TAG = "IconPack"
        private const val ICON_PX = 192

        /** Null if the pack isn't installed or its appfilter can't be read. */
        @SuppressLint("DiscouragedApi")
        fun load(context: Context, packageName: String): IconPackLoader? = try {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            val filter = if (xmlId != 0) {
                res.getXml(xmlId).use { parseAppFilter(it) }
            } else {
                res.assets.open("appfilter.xml").use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, null)
                    parseAppFilter(parser)
                }
            }
            IconPackLoader(packageName, res, filter)
        } catch (e: Exception) {
            // Not installed, no appfilter, or malformed XML: fall back to system icons.
            Log.w(TAG, "Couldn't load icon pack $packageName", e)
            null
        }
    }
}

/** Opens a Play Store search for icon packs, or the web version if the Play Store app isn't available. */
fun Context.openIconPackStore() {
    val query = "icon pack"
    val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=${android.net.Uri.encode(query)}&c=apps"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(market)
    } catch (e: android.content.ActivityNotFoundException) {
        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/search?q=${android.net.Uri.encode(query)}&c=apps"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(web) }
    }
}
