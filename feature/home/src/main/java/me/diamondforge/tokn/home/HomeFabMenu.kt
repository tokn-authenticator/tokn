package me.diamondforge.tokn.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.HomeFabMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onScanQr: () -> Unit,
    onFromImage: () -> Unit,
    onManualEntry: () -> Unit,
) {
    // Scrim for tap-outside-to-dismiss; the expressive FAB menu component does
    // not intercept outside touches on its own.
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onOpenChange(false) },
                ),
        )
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }

    val fabPadding = when {
        widthDp >= 840.dp -> 48.dp
        widthDp >= 600.dp -> 32.dp
        else -> 16.dp
    }

    FloatingActionButtonMenu(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(fabPadding),
        expanded = open,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = onOpenChange,
            ) {
                val icon by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
                    }
                }
                with(ToggleFloatingActionButtonDefaults) {
                    Icon(
                        painter = rememberVectorPainter(icon),
                        contentDescription = stringResource(R.string.add_account),
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onOpenChange(false)
                onScanQr()
            },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
            text = { Text(stringResource(R.string.add_scan_qr)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onOpenChange(false)
                onFromImage()
            },
            icon = { Icon(Icons.Default.Image, contentDescription = null) },
            text = { Text(stringResource(R.string.add_from_image)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onOpenChange(false)
                onManualEntry()
            },
            icon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
            text = { Text(stringResource(R.string.add_manually)) },
        )
    }
}
