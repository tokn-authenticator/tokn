package me.diamondforge.tokn.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import me.diamondforge.tokn.domain.model.Group
import me.diamondforge.tokn.ui.GroupColorDot

@Composable
internal fun AddToGroupDialog(
    groups: List<Group>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember { mutableStateOf(setOf<String>()) }
    var newGroupName by remember { mutableStateOf("") }
    val trimmedNew = newGroupName.trim()
    val canConfirm = checked.isNotEmpty() || trimmedNew.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_group_title)) },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(text = stringResource(R.string.add_to_group_empty))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(groups, key = { it.id }) { group ->
                            val isChecked = checked.any { it.equals(group.name, ignoreCase = true) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checked = if (isChecked) {
                                            checked.filterNotTo(mutableSetOf()) {
                                                it.equals(group.name, ignoreCase = true)
                                            }
                                        } else {
                                            checked + group.name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                GroupColorDot(colorArgb = group.colorArgb)
                                Spacer(Modifier.width(8.dp))
                                Text(group.name)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text(stringResource(R.string.add_to_group_new_group_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(checked + listOfNotNull(trimmedNew.ifEmpty { null })) },
                enabled = canConfirm,
            ) {
                Text(stringResource(R.string.add_to_group_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
