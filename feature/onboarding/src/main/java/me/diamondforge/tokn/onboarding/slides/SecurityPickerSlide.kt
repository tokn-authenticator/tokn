package me.diamondforge.tokn.onboarding.slides

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.diamondforge.tokn.onboarding.CryptType
import me.diamondforge.tokn.onboarding.R

@Composable
fun SecurityPickerSlide(
    selected: CryptType?,
    biometricAvailable: Boolean,
    onSelect: (CryptType) -> Unit,
) {
    LaunchedEffect(biometricAvailable, selected) {
        if (selected == null) {
            onSelect(if (biometricAvailable) CryptType.BIOMETRIC else CryptType.PASSWORD)
        } else if (selected == CryptType.BIOMETRIC && !biometricAvailable) {
            onSelect(CryptType.PASSWORD)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_security_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_security_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        SecurityOption(
            icon = Icons.Default.Fingerprint,
            title = stringResource(R.string.onboarding_security_biometric),
            description = if (biometricAvailable)
                stringResource(R.string.onboarding_security_biometric_desc)
            else stringResource(R.string.onboarding_security_biometric_unavailable),
            selected = selected == CryptType.BIOMETRIC,
            enabled = biometricAvailable,
            onClick = { onSelect(CryptType.BIOMETRIC) },
        )
        Spacer(Modifier.height(12.dp))
        SecurityOption(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.onboarding_security_password),
            description = stringResource(R.string.onboarding_security_password_desc),
            selected = selected == CryptType.PASSWORD,
            enabled = true,
            onClick = { onSelect(CryptType.PASSWORD) },
        )
        Spacer(Modifier.height(12.dp))
        SecurityOption(
            icon = Icons.Default.LockOpen,
            title = stringResource(R.string.onboarding_security_none),
            description = stringResource(R.string.onboarding_security_none_desc),
            selected = selected == CryptType.NONE,
            enabled = true,
            onClick = { onSelect(CryptType.NONE) },
        )
    }
}

@Composable
private fun SecurityOption(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = if (enabled) 1f else 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = content.copy(alpha = alpha)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = content.copy(alpha = alpha),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = alpha * 0.85f),
                )
            }
        }
    }
}
