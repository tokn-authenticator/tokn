package me.diamondforge.tokn.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun BulkDeleteDialog(
    count: Int,
    recycleBinEnabled: Boolean,
    onConfirm: (immediately: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var immediately by remember { mutableStateOf(false) }
    val permanent = immediately || !recycleBinEnabled
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.delete_count_confirm_title, count, count))
        },
        text = {
            Column {
                Text(
                    if (permanent) {
                        pluralStringResource(R.plurals.delete_count_immediate_message, count, count)
                    } else {
                        pluralStringResource(R.plurals.delete_count_confirm_message, count, count)
                    },
                )
                if (recycleBinEnabled) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = immediately,
                                role = Role.Switch,
                                onValueChange = { immediately = it },
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.delete_immediately),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = immediately, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(permanent) }) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
