package ai.stxt.keyspace

import androidx.compose.foundation.layout.Arrangement
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
    onClearSearchHistory: () -> Unit,
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
