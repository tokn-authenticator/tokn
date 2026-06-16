package me.diamondforge.tokn.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun RatingPromptDialog(
    onRate: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.rating_title)) },
        text = { Text(stringResource(R.string.rating_body)) },
        confirmButton = {
            Button(onClick = onRate) {
                Text(stringResource(R.string.rating_rate))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNever) {
                    Text(stringResource(R.string.rating_never))
                }
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.rating_later))
                }
            }
        },
    )
}
