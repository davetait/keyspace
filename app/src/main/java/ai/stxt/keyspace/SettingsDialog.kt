package ai.stxt.keyspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsDialog(
    settings: KeyspaceSettings,
    isDefaultHome: Boolean,
    onColumnsChange: (Int) -> Unit,
    onAdaptSpeedChange: (AdaptSpeed) -> Unit,
    onCursorStyleChange: (CursorStyle) -> Unit,
    onClearSearchHistory: () -> Unit,
    iconPacks: List<IconPackInfo>,
    onIconPackChange: (String?) -> Unit,
    onFindIconPacks: () -> Unit,
    hasNotificationAccess: Boolean,
    onNotificationDotsChange: (Boolean) -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onThemedIconsChange: (Boolean) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        // contentColor is explicit: the panel is a translucent copy, which Material can't map to a text color.
        Surface(shape = KeyspaceTokens.PanelShape, color = KeyspaceColors.panel, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                // Spacing alone separates the groups; no divider lines.
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)

                Section("Grid columns", "Icons across the home screen") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val range = KeyspaceSettings.COLUMN_RANGE
                        OutlinedButton(
                            onClick = { onColumnsChange(settings.columns - 1) },
                            shape = KeyspaceTokens.SmallShape,
                            enabled = settings.columns > range.first,
                            modifier = Modifier.semantics { contentDescription = "Fewer columns" },
                        ) { Text("−") }
                        Text(
                            settings.columns.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        OutlinedButton(
                            onClick = { onColumnsChange(settings.columns + 1) },
                            shape = KeyspaceTokens.SmallShape,
                            enabled = settings.columns < range.last,
                            modifier = Modifier.semantics { contentDescription = "More columns" },
                        ) { Text("+") }
                    }
                }

                Section("Search adapt speed", settings.adaptSpeed.description) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AdaptSpeed.entries.forEachIndexed { index, speed ->
                            SegmentedButton(
                                selected = speed == settings.adaptSpeed,
                                onClick = { onAdaptSpeedChange(speed) },
                                shape = SegmentedButtonDefaults.itemShape(index, AdaptSpeed.entries.size, KeyspaceTokens.SmallShape),
                            ) { Text(speed.name) }
                        }
                    }
                }

                Section("Cursor", "The cursor after the text you type") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CursorStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = style == settings.cursorStyle,
                                onClick = { onCursorStyleChange(style) },
                                shape = SegmentedButtonDefaults.itemShape(index, CursorStyle.entries.size, KeyspaceTokens.SmallShape),
                            ) { Text(style.name, maxLines = 1) }
                        }
                    }
                }

                Section("Icon pack", "Restyle app icons with an icon pack app") {
                    Column {
                        IconPackRow("System icons", icon = null, selected = settings.iconPack == null) { onIconPackChange(null) }
                        iconPacks.forEach { pack ->
                            val context = LocalContext.current
                            val icon = remember(pack.packageName) {
                                runCatching { context.packageManager.getApplicationIcon(pack.packageName).toBitmap().asImageBitmap() }.getOrNull()
                            }
                            IconPackRow(pack.label, icon, selected = settings.iconPack == pack.packageName) {
                                onIconPackChange(pack.packageName)
                            }
                        }
                        if (iconPacks.isEmpty()) {
                            Text(
                                "No icon packs installed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        OutlinedButton(onClick = onFindIconPacks, shape = KeyspaceTokens.SmallShape, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Find icon packs")
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SwitchSection(
                        title = "Themed icons",
                        description = "Apps that support it use a single-color icon matched to your wallpaper",
                        checked = settings.themedIcons,
                        onCheckedChange = onThemedIconsChange,
                    )
                }

                SwitchSection(
                    title = "Notification dots",
                    description = when {
                        !settings.notificationDots -> "Show a dot on apps with notifications"
                        hasNotificationAccess -> "Showing a dot on apps with notifications"
                        else -> "Needs notification access. Keyspace only checks which apps have notifications, never what they say."
                    },
                    checked = settings.notificationDots,
                    onCheckedChange = onNotificationDotsChange,
                ) {
                    if (settings.notificationDots && !hasNotificationAccess) {
                        OutlinedButton(onClick = onGrantNotificationAccess, shape = KeyspaceTokens.SmallShape) {
                            Text("Allow notification access")
                        }
                    }
                }

                var confirmClear by remember { mutableStateOf(false) }
                Section("Search history", "Forget which apps you open most, so search results go back to alphabetical") {
                    if (confirmClear) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Clear all history?", modifier = Modifier.weight(1f))
                            TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                            FilledTonalButton(shape = KeyspaceTokens.SmallShape, onClick = {
                                onClearSearchHistory()
                                confirmClear = false
                            }) { Text("Clear") }
                        }
                    } else {
                        OutlinedButton(onClick = { confirmClear = true }, shape = KeyspaceTokens.SmallShape) { Text("Clear search history") }
                    }
                }

                Section(
                    "Default home app",
                    if (isDefaultHome) "Keyspace is your home screen" else "Keyspace is not your home screen",
                ) {
                    OutlinedButton(onClick = onOpenHomeSettings, shape = KeyspaceTokens.SmallShape) { Text("Change home app") }
                }

                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, description: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(0.dp))
        content()
    }
}

@Composable
private fun IconPackRow(label: String, icon: ImageBitmap?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(KeyspaceTokens.SmallShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchSection(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
        }
        extra()
    }
}
