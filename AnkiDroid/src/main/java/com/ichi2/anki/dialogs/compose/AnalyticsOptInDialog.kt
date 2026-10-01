/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.CheckboxPrompt
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun AnalyticsOptInDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var isChecked by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.analytics_dialog_title))
        },
        text = {
            Column {
                Text(text = stringResource(id = R.string.analytics_summ))
                Spacer(modifier = Modifier.height(16.dp))
                CheckboxPrompt(
                    text = stringResource(id = R.string.analytics_title),
                    isChecked = isChecked,
                    onCheckedChange = { isChecked = it },
                    horizontalPadding = 0.dp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(isChecked)
                },
            ) {
                Text(text = stringResource(id = R.string.dialog_continue))
            }
        },
    )
}

@Preview(name = "Analytics Opt-In Dialog")
@Composable
private fun AnalyticsOptInDialogPreview() {
    AnkiDroidTheme {
        AnalyticsOptInDialog(
            onDismissRequest = {},
            onConfirm = {},
        )
    }
}
