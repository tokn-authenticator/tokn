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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import me.diamondforge.tokn.domain.model.OtpType
import sh.calvin.reorderable.ReorderableCollectionItemScope
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReorderableCollectionItemScope.AccountCard(
    item: AccountItem,
    isDragging: Boolean,
    iconFetchEnabled: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    canReorder: Boolean,
    isMasked: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)?,
    onLongPress: () -> Unit,
    onIncrementCounter: () -> Unit,
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onDoubleClick = onDoubleTap, onLongClick = onLongPress),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
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
                 Text(
                    text = item.account.issuer,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.account.accountName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
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
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!inSelectionMode) {
                when (item.account.type) {
                    OtpType.TOTP -> {
                        val progress by animateFloatAsState(
                            targetValue = item.otpResult.remainingMillis.toFloat() /
                                    item.otpResult.periodMillis.toFloat(),
                            label = "countdown",
                        )
                        val secondsRemaining = (item.otpResult.remainingMillis / 1000).toInt()
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(40.dp),
                                color = if (secondsRemaining <= 5)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
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
