@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package me.diamondforge.tokn.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scaffold shared by every settings screen: a large flexible (collapsing) top
 * app bar over a vertically-spaced column of grouped sections.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/** Small accent-coloured header that introduces a [SettingsGroup]. */
@Composable
internal fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Rounded tonal container that groups related rows. Rows are passed as a list
 * so hairline dividers can be inset between them automatically.
 */
@Composable
internal fun SettingsGroup(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            items.forEachIndexed { index, item ->
                item()
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        thickness = Dp.Hairline,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * One row inside a [SettingsGroup]: leading icon, title, optional supporting
 * text, an optional trailing control, and optional [belowContent] (e.g. a
 * [androidx.compose.material3.ButtonGroup]) rendered under the title block.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    belowContent: @Composable (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
        if (belowContent != null) {
            Spacer(Modifier.size(12.dp))
            belowContent()
        }
    }
}

/**
 * A [SettingsRow] whose body is a single-select group of expressive
 * [ToggleButton]s. Buttons size to their label and wrap to a new line when
 * the labels don't fit, so long translations never get clipped.
 */
@Composable
internal fun <T> SettingsToggleRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        belowContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { (value, label) ->
                    ToggleButton(
                        checked = selected == value,
                        onCheckedChange = { onSelect(value) },
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
}

/** Trailing switch with the expressive check / close thumb glyph. */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}
