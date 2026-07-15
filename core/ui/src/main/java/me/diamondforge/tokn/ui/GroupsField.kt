package me.diamondforge.tokn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Multi-value chip input rendered as a single outlined field that holds
 * both the existing values as Material 3 [InputChip]s and the inline
 * caret. Typing commits a new chip on Enter / IME-Done or when a comma
 * is typed; backspace on an empty caret pops the last chip. Duplicate
 * detection is case-insensitive (the first-typed casing is preserved).
 *
 * Optional [suggestions] are filtered by case-insensitive prefix against
 * the current input and shown as tappable [AssistChip]s under the field.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupsField(
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    colorFor: (String) -> Int? = { null },
) {
    var pending by rememberSaveable { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }

    fun commit(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val alreadyHas = values.any { it.equals(trimmed, ignoreCase = true) }
        if (!alreadyHas) onValuesChange(values + trimmed)
        pending = ""
    }

    val activeSuggestions = remember(suggestions, values, pending) {
        val query = pending.trim()
        suggestions
            .filter { s ->
                values.none { it.equals(s, ignoreCase = true) } &&
                        (query.isEmpty() || s.startsWith(query, ignoreCase = true)) &&
                        !s.equals(query, ignoreCase = true)
            }
            .distinctBy { it.lowercase() }
    }

    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val labelColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    border = BorderStroke(borderWidth, borderColor),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                values.forEach { value ->
                    val colorArgb = colorFor(value)
                    val chipColors = if (colorArgb != null) {
                        val base = Color(colorArgb)
                        val onBase = base.readableOnColor()
                        InputChipDefaults.inputChipColors(
                            containerColor = base,
                            labelColor = onBase,
                            trailingIconColor = onBase,
                        )
                    } else {
                        InputChipDefaults.inputChipColors()
                    }
                    InputChip(
                        selected = false,
                        onClick = { onValuesChange(values - value) },
                        label = { Text(value) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        colors = chipColors,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }

                BasicTextField(
                    value = pending,
                    onValueChange = { next ->
                        if (next.contains(',')) {
                            val parts = next.split(',')
                            parts.dropLast(1).forEach { commit(it) }
                            pending = parts.last()
                        } else {
                            pending = next
                        }
                    },
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .widthIn(min = 60.dp)
                        .heightIn(min = ChipRowHeight)
                        .align(Alignment.CenterVertically)
                        .onFocusChanged { isFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                pending.isEmpty() &&
                                values.isNotEmpty()
                            ) {
                                onValuesChange(values.dropLast(1))
                                true
                            } else false
                        },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit(pending) }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) { inner() }
                    },
                )
            }
        }

        if (activeSuggestions.isNotEmpty()) {
            val suggestionsListState = rememberLazyListState()
            LazyRow(
                state = suggestionsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFadingEdges(suggestionsListState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(activeSuggestions, key = { it.lowercase() }) { suggestion ->
                    val colorArgb = colorFor(suggestion)
                    val chipColors = if (colorArgb != null) {
                        val base = Color(colorArgb)
                        AssistChipDefaults.assistChipColors(
                            containerColor = base.copy(alpha = 0.24f),
                            labelColor = base,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                    AssistChip(
                        onClick = { commit(suggestion) },
                        label = { Text(suggestion) },
                        colors = chipColors,
                    )
                }
            }
        }
    }
}

fun Modifier.horizontalFadingEdges(
    state: LazyListState,
    edgeWidth: Dp = 48.dp,
): Modifier = graphicsLayer { alpha = 0.99f }.drawWithContent {
    drawContent()
    val edgeWidthPx = edgeWidth.toPx()
    if (state.canScrollBackward) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startX = 0f,
                endX = edgeWidthPx,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
    if (state.canScrollForward) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = size.width - edgeWidthPx,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val ChipRowHeight = 32.dp
