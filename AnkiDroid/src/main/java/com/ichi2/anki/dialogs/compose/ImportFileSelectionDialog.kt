// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportEntry
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportFileType
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
internal fun ImportFileSelectionDialog(
    entries: List<ImportEntry>,
    onDismissRequest: () -> Unit,
    onEntryClick: (ImportEntry) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.menu_import))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                entries.forEach { entry ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onEntryClick(entry) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(entry.titleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ImportFileSelectionDialogPreview() {
    AnkiDroidTheme {
        ImportFileSelectionDialog(
            entries =
                listOf(
                    ImportEntry(
                        titleRes = R.string.import_deck_package,
                        analyticsId = UsageAnalytics.Actions.IMPORT_APKG_FILE,
                        type = ImportFileType.APKG,
                    ),
                    ImportEntry(
                        titleRes = R.string.import_collection_package,
                        analyticsId = UsageAnalytics.Actions.IMPORT_COLPKG_FILE,
                        type = ImportFileType.COLPKG,
                    ),
                    ImportEntry(
                        titleRes = R.string.import_csv,
                        analyticsId = UsageAnalytics.Actions.IMPORT_CSV_FILE,
                        type = ImportFileType.CSV,
                    ),
                ),
            onDismissRequest = {},
            onEntryClick = {},
        )
    }
}
