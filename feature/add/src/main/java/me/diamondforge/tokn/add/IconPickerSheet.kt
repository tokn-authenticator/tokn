package me.diamondforge.tokn.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.diamondforge.tokn.data.icon.InstalledIconPack
import me.diamondforge.tokn.data.icon.suggestionsFor

private const val ICONS_PER_ROW = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    issuer: String,
    installedPacks: List<InstalledIconPack>,
    hasIcon: Boolean,
    onPickedFromGallery: (Uri) -> Unit,
    onPickedFromPack: (packUuid: String, filename: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onPickedFromGallery(uri)
            onDismiss()
        }
    }
    var query by remember { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val suggestions = remember(installedPacks, issuer) {
        installedPacks.flatMap { pack ->
            pack.suggestionsFor(issuer).map { suggestion -> pack to suggestion.icon }
        }
    }

    val filteredFlat = remember(installedPacks, query) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.lowercase()
            installedPacks.flatMap { pack ->
                pack.pack.icons.asSequence()
                    .filter { icon ->
                        icon.displayName.lowercase().contains(q) ||
                                icon.issuerMatches.any { it.lowercase().contains(q) }
                    }
                    .map { pack to it }
                    .toList()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.92f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.icon_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.icon_picker_gallery))
                    }
                    if (hasIcon) {
                        OutlinedButton(
                            onClick = { onRemove(); onDismiss() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.icon_picker_remove))
                        }
                    }
                }
                if (installedPacks.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.icon_picker_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (installedPacks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.icon_picker_no_packs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (query.isNotBlank()) {
                    if (filteredFlat.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.icon_picker_no_search_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        items(filteredFlat.chunked(ICONS_PER_ROW)) { row ->
                            IconRow(row, onPickedFromPack, onDismiss)
                        }
                    }
                } else {
                    if (suggestions.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.icon_picker_suggestions)) }
                        items(suggestions.take(60).chunked(ICONS_PER_ROW)) { row ->
                            IconRow(row, onPickedFromPack, onDismiss)
                        }
                    }
                    installedPacks.forEach { pack ->
                        val key = pack.pack.uuid
                        val isOpen = expanded[key] == true
                        item(key = "header_$key") {
                            CollapsibleHeader(
                                title = pack.pack.name,
                                count = pack.iconCount,
                                isExpanded = isOpen,
                                onToggle = { expanded[key] = !isOpen },
                            )
                        }
                        if (isOpen) {
                            items(
                                items = pack.pack.icons.map { pack to it }.chunked(ICONS_PER_ROW),
                                key = { row -> "${pack.pack.uuid}_${row.first().second.filename}" },
                            ) { row ->
                                IconRow(row, onPickedFromPack, onDismiss)
                            }
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(stringResource(R.string.icon_picker_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CollapsibleHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun IconRow(
    row: List<Pair<InstalledIconPack, me.diamondforge.tokn.data.icon.IconPackIcon>>,
    onPickedFromPack: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { (pack, icon) ->
            IconTile(
                file = pack.fileFor(icon.filename),
                modifier = Modifier.weight(1f),
                onClick = {
                    onPickedFromPack(pack.pack.uuid, icon.filename)
                    onDismiss()
                },
            )
        }
        repeat(ICONS_PER_ROW - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun IconTile(file: java.io.File, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
