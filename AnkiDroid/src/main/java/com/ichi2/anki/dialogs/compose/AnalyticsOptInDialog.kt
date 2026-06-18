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
    onConfirm: (Boolean) -> Unit
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
                    horizontalPadding = 0.dp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(isChecked)
                }
            ) {
                Text(text = stringResource(id = R.string.dialog_continue))
            }
        }
    )
}

@Preview(name = "Analytics Opt-In Dialog")
@Composable
private fun AnalyticsOptInDialogPreview() {
    AnkiDroidTheme {
        AnalyticsOptInDialog(
            onDismissRequest = {},
            onConfirm = {}
        )
    }
}
