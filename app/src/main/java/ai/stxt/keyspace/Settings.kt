package ai.stxt.keyspace

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How quickly search ranking follows changes in which apps are used (see [AppUsage]). */
enum class AdaptSpeed(val halfLifeDays: Double, val description: String) {
    Fast(1.5, "New favorites rise in about 2 days"),
    Normal(AppUsage.DEFAULT_HALF_LIFE_DAYS, "New favorites rise in about 3–4 days"),
    Slow(7.0, "New favorites rise in about a week"),
}

/** The cursor after the typed text. Invisible by default: a blinking cursor is distracting on a home screen. */
enum class CursorStyle { Blinking, Solid, Invisible }

data class KeyspaceSettings(
    val columns: Int = DEFAULT_COLUMNS,
    val adaptSpeed: AdaptSpeed = AdaptSpeed.Normal,
    /** Package of the chosen icon pack; null for the system icons. */
    val iconPack: String? = null,
    /** Dots on apps with notifications (also needs notification access). */
    val notificationDots: Boolean = false,
    /** Android 13+ monochrome icons, tinted to the wallpaper colors. */
    val themedIcons: Boolean = false,
    val cursorStyle: CursorStyle = CursorStyle.Invisible,
    /** The typed-text line above the keyboard. When off, that space is another row of icons. */
    val showSearchLine: Boolean = true,
) {
    companion object {
        const val DEFAULT_COLUMNS = 5
        val COLUMN_RANGE = 4..7

        /** Tolerates missing or out-of-range stored values by falling back to defaults. */
        fun parse(
            columns: Int?,
            adaptSpeed: String?,
            iconPack: String? = null,
            notificationDots: Boolean = false,
            themedIcons: Boolean = false,
            cursorStyle: String? = null,
            showSearchLine: Boolean = true,
        ) = KeyspaceSettings(
            columns = columns?.coerceIn(COLUMN_RANGE) ?: DEFAULT_COLUMNS,
            adaptSpeed = AdaptSpeed.entries.firstOrNull { it.name == adaptSpeed } ?: AdaptSpeed.Normal,
            iconPack = iconPack?.takeIf { it.isNotBlank() },
            notificationDots = notificationDots,
            themedIcons = themedIcons,
            cursorStyle = CursorStyle.entries.firstOrNull { it.name == cursorStyle } ?: CursorStyle.Invisible,
            showSearchLine = showSearchLine,
        )
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        KeyspaceSettings.parse(
            columns = if (prefs.contains(COLUMNS)) prefs.getInt(COLUMNS, 0) else null,
            adaptSpeed = prefs.getString(ADAPT_SPEED, null),
            iconPack = prefs.getString(ICON_PACK, null),
            notificationDots = prefs.getBoolean(NOTIFICATION_DOTS, false),
            themedIcons = prefs.getBoolean(THEMED_ICONS, false),
            cursorStyle = prefs.getString(CURSOR_STYLE, null),
            showSearchLine = prefs.getBoolean(SHOW_SEARCH_LINE, true),
        )
    )
    val settings: StateFlow<KeyspaceSettings> = _settings.asStateFlow()

    private val _welcomeDismissed = MutableStateFlow(prefs.getBoolean(WELCOME_DISMISSED, false))
    /** The user chose "Not now" on the set-as-home card; don't show it again. */
    val welcomeDismissed: StateFlow<Boolean> = _welcomeDismissed.asStateFlow()

    fun dismissWelcome() {
        _welcomeDismissed.value = true
        prefs.edit { putBoolean(WELCOME_DISMISSED, true) }
    }

    fun setColumns(columns: Int) = update(_settings.value.copy(columns = columns.coerceIn(KeyspaceSettings.COLUMN_RANGE)))

    fun setAdaptSpeed(speed: AdaptSpeed) = update(_settings.value.copy(adaptSpeed = speed))

    fun setIconPack(packageName: String?) = update(_settings.value.copy(iconPack = packageName))

    fun setNotificationDots(on: Boolean) = update(_settings.value.copy(notificationDots = on))

    fun setThemedIcons(on: Boolean) = update(_settings.value.copy(themedIcons = on))

    fun setCursorStyle(style: CursorStyle) = update(_settings.value.copy(cursorStyle = style))

    fun setShowSearchLine(on: Boolean) = update(_settings.value.copy(showSearchLine = on))

    private fun update(settings: KeyspaceSettings) {
        _settings.value = settings
        prefs.edit {
            putInt(COLUMNS, settings.columns)
            putString(ADAPT_SPEED, settings.adaptSpeed.name)
            if (settings.iconPack == null) remove(ICON_PACK) else putString(ICON_PACK, settings.iconPack)
            putBoolean(NOTIFICATION_DOTS, settings.notificationDots)
            putBoolean(THEMED_ICONS, settings.themedIcons)
            putString(CURSOR_STYLE, settings.cursorStyle.name)
            putBoolean(SHOW_SEARCH_LINE, settings.showSearchLine)
        }
    }

    private companion object {
        const val COLUMNS = "columns"
        const val ADAPT_SPEED = "adapt_speed"
        const val ICON_PACK = "icon_pack"
        const val WELCOME_DISMISSED = "welcome_dismissed"
        const val NOTIFICATION_DOTS = "notification_dots"
        const val THEMED_ICONS = "themed_icons"
        const val CURSOR_STYLE = "cursor_style"
        const val SHOW_SEARCH_LINE = "show_search_line"
    }
}
