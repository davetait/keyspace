package com.keyspaceapps.keyspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Renames an app. The one place the system keyboard appears: names need capitals and spaces, which
 * Keyspace's own keyboard doesn't have. [onRename] gets null to restore the app's own name.
 */
@Composable
fun RenameDialog(app: AppEntry, onRename: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(app.key, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(app.label, selection = TextRange(0, app.label.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { onRename(text.text.takeIf { it.isNotBlank() && it.trim() != app.originalLabel }) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = KeyspaceTokens.PanelShape, color = KeyspaceColors.panel, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Rename app", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Name") },
                    supportingText = { Text("Original name: ${app.originalLabel}") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    shape = KeyspaceTokens.SmallShape,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(Modifier.fillMaxWidth()) {
                    if (app.label != app.originalLabel) {
                        TextButton(onClick = { onRename(null) }) { Text("Reset to original") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = save) { Text("Save") }
                }
            }
        }
    }
}
