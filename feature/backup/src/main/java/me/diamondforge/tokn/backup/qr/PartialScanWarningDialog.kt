package me.diamondforge.tokn.backup.qr

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.diamondforge.tokn.backup.R

@Composable
internal fun PartialScanWarningDialog(
    scanned: Int,
    expectedTotal: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.migration_partial_warning_title)) },
        text = {
            Text(
                stringResource(
                    R.string.migration_partial_warning_body,
                    scanned,
                    expectedTotal,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.migration_partial_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.migration_partial_warning_keep_scanning))
            }
        },
    )
}
