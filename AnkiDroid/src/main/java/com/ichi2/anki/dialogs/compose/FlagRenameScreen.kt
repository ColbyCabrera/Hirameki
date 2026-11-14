// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.Flag
import com.ichi2.anki.R

@Composable
fun FlagRenameScreen(
    flags: List<Pair<Flag, String>>,
    onRename: (Flag, String) -> Unit,
) {
    LazyColumn {
        items(items = flags, key = { it.first.code }) { (flag, name) ->
            FlagRow(
                flag = flag,
                name = name,
                onRename = { newName -> onRename(flag, newName) },
            )
        }
    }
}

@Composable
private fun FlagRow(
    flag: Flag,
    name: String,
    onRename: (String) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(id = flag.drawableRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp),
        )

        if (isEditing) {
            EditFlagName(initialName = name, onSave = { newName ->
                onRename(newName.ifBlank { name })
                isEditing = false
            }, onCancel = { isEditing = false })
        } else {
            DisplayFlagName(
                name = name,
                onEdit = { isEditing = true },
            )
        }
    }
}

@Composable
private fun DisplayFlagName(
    name: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        )
        IconButton(onClick = onEdit) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mode_edit_white),
                contentDescription = stringResource(id = R.string.edit),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EditFlagName(
    initialName: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    // using TextFieldValue to handle selection
    var textState by remember { mutableStateOf(TextFieldValue(initialName)) }
    val focusRequester = remember { FocusRequester() }
    val isValid = textState.text.trim().isNotEmpty()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        textState = textState.copy(selection = TextRange(textState.text.length))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = textState,
            onValueChange = { textState = it },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(onDone = {
                    if (isValid) onSave(textState.text.trim())
                }),
        )
        IconButton(onClick = onCancel) {
            Icon(
                painter = painterResource(id = R.drawable.close_icon),
                contentDescription = stringResource(id = R.string.dialog_cancel),
            )
        }
        IconButton(
            onClick = { onSave(textState.text.trim()) },
            enabled = isValid,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_done),
                contentDescription = stringResource(id = R.string.save),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlagRowPreview() {
    MaterialTheme {
        FlagRow(flag = Flag.RED, name = "Red Flag", onRename = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun FlagRowEditPreview() {
    MaterialTheme {
        FlagRow(flag = Flag.BLUE, name = "Blue Flag", onRename = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun FlagRenameScreenPreview() {
    MaterialTheme {
        FlagRenameScreen(
            flags =
                listOf(
                    Flag.RED to "Red",
                    Flag.ORANGE to "Orange",
                    Flag.GREEN to "Green",
                ),
            onRename = { _, _ -> },
        )
    }
}
