package me.diamondforge.tokn.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.importer.otpauth.toOtpAuthUri
import me.diamondforge.tokn.ui.qr.QrRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportQrBottomSheet(
    account: OtpAccount,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uri = remember(account) { account.toOtpAuthUri() }
    val sizePx = with(LocalDensity.current) { 240.dp.roundToPx() }
    val bitmap = remember(uri, sizePx) { QrRenderer.render(uri, sizePx).asImageBitmap() }
    val copiedMessage = stringResource(R.string.export_copied)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val label = if (account.issuer.isNotBlank()) account.issuer else account.accountName
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            if (account.issuer.isNotBlank() && account.accountName.isNotBlank()) {
                Text(
                    text = account.accountName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
                    .size(240.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.export_secret_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(uri))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.export_copy_uri))
            }
        }
    }
}
