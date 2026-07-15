package me.diamondforge.tokn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GroupColorPalette {
    val colors: List<Int> = listOf(
        0xFFEF5350.toInt(),
        0xFFFFA726.toInt(),
        0xFFFFCA28.toInt(),
        0xFF66BB6A.toInt(),
        0xFF26C6DA.toInt(),
        0xFF42A5F5.toInt(),
        0xFF7E57C2.toInt(),
        0xFFEC407A.toInt(),
        0xFF8D6E63.toInt(),
        0xFF78909C.toInt(),
    )
}

@Composable
fun GroupColorDot(
    colorArgb: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    if (colorArgb != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(colorArgb)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupColorPicker(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColorSwatch(colorArgb = null, isSelected = selected == null, onClick = { onSelect(null) })
        GroupColorPalette.colors.forEach { colorArgb ->
            ColorSwatch(
                colorArgb = colorArgb,
                isSelected = selected == colorArgb,
                onClick = { onSelect(colorArgb) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    colorArgb: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(
                if (colorArgb != null) {
                    Modifier.background(Color(colorArgb))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = colorArgb?.toString() ?: "none" },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (colorArgb != null) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
