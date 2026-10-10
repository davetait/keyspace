package ai.stxt.keyspace

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The intent that asks the user to make Keyspace the home app: Android's own "Set as default" dialog
 * where available (Android 10+), otherwise the system Home app settings.
 */
fun Context.setAsHomeIntent(): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}

/** Shown until Keyspace is the home app or the user picks "Not now". */
@Composable
fun WelcomeCard(onSetAsHome: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = KeyspaceTokens.PanelShape,
        color = KeyspaceColors.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make Keyspace your home screen", style = MaterialTheme.typography.titleLarge)
            Text(
                "Keyspace opens when you press Home, so the keyboard is always ready to find an app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNotNow) { Text("Not now") }
                FilledTonalButton(onClick = onSetAsHome, shape = KeyspaceTokens.SmallShape) { Text("Set as home") }
            }
        }
    }
}
