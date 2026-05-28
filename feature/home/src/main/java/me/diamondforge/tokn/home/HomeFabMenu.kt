package me.diamondforge.tokn.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val FAB_STAGGER_MS = 50L
private const val TOTAL_FAB_ITEMS = 3

@Composable
internal fun BoxScope.HomeFabMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onScanQr: () -> Unit,
    onFromImage: () -> Unit,
    onManualEntry: () -> Unit,
) {
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onOpenChange(false) },
                ),
        )
    }

    // Bottom-up reveal on open, top-down collapse on close.
    // Aegis-style: items "expand from" / "collapse into" the FAB.
    var visibleFabItems by remember { mutableIntStateOf(0) }
    LaunchedEffect(open) {
        if (open) {
            for (c in 1..TOTAL_FAB_ITEMS) {
                visibleFabItems = c
                delay(FAB_STAGGER_MS)
            }
        } else {
            for (c in (TOTAL_FAB_ITEMS - 1) downTo 0) {
                visibleFabItems = c
                delay(FAB_STAGGER_MS)
            }
        }
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }

    val fabPadding = when {
        widthDp >= 840.dp -> 48.dp
        widthDp >= 600.dp -> 32.dp
        else -> 16.dp
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(fabPadding),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Threshold per row: top item appears last on open / disappears first on close.
        // Row index 0 (top) shows when count > 2; row 2 (bottom, closest to FAB) when count > 0.
        StaggeredFabItem(
            visible = visibleFabItems > 2,
            label = stringResource(R.string.add_scan_qr),
            icon = Icons.Default.QrCodeScanner,
        ) {
            onOpenChange(false)
            onScanQr()
        }
        StaggeredFabItem(
            visible = visibleFabItems > 1,
            label = stringResource(R.string.add_from_image),
            icon = Icons.Default.Image,
        ) {
            onOpenChange(false)
            onFromImage()
        }
        StaggeredFabItem(
            visible = visibleFabItems > 0,
            label = stringResource(R.string.add_manually),
            icon = Icons.Default.Keyboard,
        ) {
            onOpenChange(false)
            onManualEntry()
        }
        val fabRotation by animateFloatAsState(
            targetValue = if (open) 45f else 0f,
            label = "fab-rotation",
        )
        FloatingActionButton(onClick = { onOpenChange(!open) }) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.add_account),
                modifier = Modifier.rotate(fabRotation),
            )
        }
    }
}

@Composable
private fun StaggeredFabItem(
    visible: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn() + expandVertically(),
        exit = fadeOut() + scaleOut() + shrinkVertically(),
    ) {
        FabMenuItem(label = label, icon = icon, onClick = onClick)
    }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
