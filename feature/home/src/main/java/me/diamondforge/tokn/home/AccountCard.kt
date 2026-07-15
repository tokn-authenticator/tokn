package me.diamondforge.tokn.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.ui.readableOnColor
import sh.calvin.reorderable.ReorderableCollectionItemScope
import java.io.File
import kotlin.math.absoluteValue

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
internal fun ReorderableCollectionItemScope.AccountCard(
    item: AccountItem,
    isDragging: Boolean,
    iconFetchEnabled: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    canReorder: Boolean,
    isMasked: Boolean,
    groups: List<String>,
    groupColorFor: (String) -> Int?,
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)?,
    onLongPress: () -> Unit,
    onIncrementCounter: () -> Unit,
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onDoubleClick = onDoubleTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                SelectionCheckmark()
            } else {
                IssuerAvatar(
                    issuer = item.account.issuer,
                    customIconBytes = item.account.customIconBytes,
                    packIconPath = item.packIconPath,
                    iconFetchEnabled = iconFetchEnabled,
                )
            }

            // todo: make the spacings and sizes configurable

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.account.issuer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (groups.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        GroupChipsOverflowRow(
                            groups = groups,
                            colorFor = groupColorFor,
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
                Text(
                    text = item.account.accountName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isMasked) maskOtpCode(item.otpResult.code)
                    else formatOtpCode(item.otpResult.code),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.nextCode?.let { next ->
                    Text(
                        text = if (isMasked) maskOtpCode(next) else formatOtpCode(next),
                        modifier = Modifier.offset(y = (-3).dp),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (!inSelectionMode) {
                when (item.account.type) {
                    OtpType.TOTP -> {
                        val progress by animateFloatAsState(
                            targetValue = item.otpResult.remainingMillis.toFloat() /
                                    item.otpResult.periodMillis.toFloat(),
                            label = "countdown",
                        )
                        val secondsRemaining = (item.otpResult.remainingMillis / 1000).toInt()
                        val expiring = secondsRemaining <= 5
                        Box(contentAlignment = Alignment.Center) {
                            if (expiring) {
                                CircularWavyProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(44.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            } else {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                            Text(
                                text = secondsRemaining.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    OtpType.HOTP -> {
                        IconButton(onClick = onIncrementCounter) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (canReorder) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    modifier = Modifier.draggableHandle(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupChipsOverflowRow(
    groups: List<String>,
    colorFor: (String) -> Int?,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val spacing = 6.dp
    SubcomposeLayout(modifier = modifier) { constraints ->
        val spacingPx = spacing.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth)

        val allChips = groups.indices.map { index ->
            subcompose("g$index") {
                AccountGroupChip(label = groups[index], colorArgb = colorFor(groups[index]))
            }.first().measure(loose)
        }

        fun widthOf(placeables: List<Placeable>): Int =
            placeables.sumOf { it.width } + spacingPx * (placeables.size - 1).coerceAtLeast(0)

        fun measureBadge(count: Int, maxWidth: Int = constraints.maxWidth): Placeable =
            subcompose("overflow$count") {
                AccountGroupChip(label = "+$count", colorArgb = null)
            }.first().measure(Constraints(maxWidth = maxWidth))

        var shown = allChips
        var overflow: Placeable? = null
        var totalWidth = widthOf(shown)

        if (totalWidth > constraints.maxWidth) {
            var placed = false
            var lastBadge: Placeable? = null
            for (k in groups.size - 1 downTo 1) {
                val candidate = allChips.subList(0, k)
                val overflowCount = groups.size - k
                val badge = measureBadge(overflowCount)
                lastBadge = badge
                val width = widthOf(candidate) + spacingPx + badge.width
                if (width <= constraints.maxWidth) {
                    shown = candidate
                    overflow = badge
                    totalWidth = width
                    placed = true
                    break
                }
            }
            if (!placed) {
                val badge = requireNotNull(lastBadge)
                val chipBudget = (constraints.maxWidth - spacingPx - badge.width).coerceAtLeast(0)
                val squeezed = subcompose("g0-squeezed") {
                    AccountGroupChip(label = groups[0], colorArgb = colorFor(groups[0]))
                }.first().measure(Constraints(maxWidth = chipBudget))
                shown = listOf(squeezed)
                overflow = badge
                totalWidth = squeezed.width + spacingPx + badge.width
            }
        }

        val height = (shown + listOfNotNull(overflow)).maxOf { it.height }
        layout(totalWidth.coerceAtMost(constraints.maxWidth), height) {
            var x = 0
            shown.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + spacingPx
            }
            overflow?.placeRelative(x, (height - overflow.height) / 2)
        }
    }
}

@Composable
private fun AccountGroupChip(
    label: String,
    colorArgb: Int?,
    modifier: Modifier = Modifier,
) {
    val containerColor = colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val contentColor = colorArgb?.let { Color(it).readableOnColor() }
        ?: MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SelectionCheckmark() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun IssuerAvatar(
    issuer: String,
    customIconBytes: ByteArray?,
    packIconPath: String?,
    iconFetchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        customIconBytes != null -> AsyncIconBox(model = customIconBytes, modifier = modifier)
        packIconPath != null -> AsyncIconBox(
            model = File(packIconPath),
            modifier = modifier
        )

        iconFetchEnabled -> {
            val url = remember(issuer) {
                val slug = issuer.lowercase().replace(Regex("[^a-z0-9]"), "")
                "https://cdn.simpleicons.org/$slug"
            }
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                modifier = modifier.size(38.dp),
                contentScale = ContentScale.Fit,
            ) {
                val state = painter.state.collectAsState()
                if (state.value is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    LetterAvatarBox(issuer)
                }
            }
        }

        else -> LetterAvatarBox(issuer, modifier)
    }
}

@Composable
private fun AsyncIconBox(model: Any, modifier: Modifier = Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.size(38.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun LetterAvatarBox(issuer: String, modifier: Modifier = Modifier) {
    val avatarColors = listOf(
        Color(0xFF1976D2),
        Color(0xFF388E3C),
        Color(0xFFF57C00),
        Color(0xFF7B1FA2),
        Color(0xFFD32F2F),
        Color(0xFF0097A7),
        Color(0xFF5D4037),
        Color(0xFF455A64),
        Color(0xFF00796B),
        Color(0xFFC62828),
    )
    val color =
        remember(issuer) { avatarColors[issuer.hashCode().absoluteValue % avatarColors.size] }
    val letter = issuer.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
