package com.keyspaceapps.keyspace

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Matches notifications to apps: the package, tagged with the profile like [AppEntry.key]. */
val AppEntry.dotKey: String get() = dotKey(packageName, profileSerial)

fun dotKey(packageName: String, profileSerial: Long?): String = packageName + (profileSerial?.let { "#$it" } ?: "")

/** Apps that currently have a notification worth a dot. Fed by [KeyspaceNotificationListener]. */
object NotificationDots {
    private val _apps = MutableStateFlow<Set<String>>(emptySet())
    val apps: StateFlow<Set<String>> = _apps.asStateFlow()

    internal fun update(apps: Set<String>) {
        _apps.value = apps
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Android's screen for granting Keyspace notification access. */
    fun accessSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, KeyspaceNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
}

/**
 * Watches notifications only to know which apps have unread ones; nothing about their content is read
 * or stored. Runs only if the user grants notification access.
 */
class KeyspaceNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() = publish()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()
    override fun onListenerDisconnected() = NotificationDots.update(emptySet())

    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val users = getSystemService(UserManager::class.java)
        val ranking = Ranking()
        val rankings = currentRanking
        NotificationDots.update(
            active
                // Ongoing ones (music, navigation, downloads) aren't "unread", and apps can opt channels out of badges.
                .filter { sbn -> !sbn.isOngoing && (rankings?.getRanking(sbn.key, ranking) != true || ranking.canShowBadge()) }
                .map { sbn ->
                    val serial = if (sbn.user == Process.myUserHandle()) null else users.getSerialNumberForUser(sbn.user)
                    dotKey(sbn.packageName, serial)
                }
                .toSet()
        )
    }
}
