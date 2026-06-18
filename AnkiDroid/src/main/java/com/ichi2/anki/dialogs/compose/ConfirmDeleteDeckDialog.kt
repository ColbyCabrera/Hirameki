package com.ichi2.anki.dialogs.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun ConfirmDeleteDeckDialog(
    deckName: String,
    totalCards: Int,
    isFiltered: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val messageText = if (isFiltered) {
        stringResource(id = R.string.delete_cram_deck_message, "<b>$deckName</b>")
    } else {
        pluralStringResource(
            id = R.plurals.delete_deck_message,
            count = totalCards,
            "<b>$deckName</b>",
            totalCards
        )
    }

    val annotatedMessage = remember(messageText) {
        val spanned = HtmlCompat.fromHtml(messageText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        buildAnnotatedString {
            append(spanned.toString())
            val styleSpans = spanned.getSpans(0, spanned.length, android.text.style.StyleSpan::class.java)
            for (span in styleSpans) {
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                if (span.style == android.graphics.Typeface.BOLD) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = stringResource(id = R.string.delete_deck_title))
        },
        text = {
            Text(text = annotatedMessage)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.dialog_positive_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.dialog_cancel))
            }
        }
    )
}

@Preview(name = "Confirm Delete Deck Dialog - Normal")
@Composable
private fun ConfirmDeleteDeckDialogNormalPreview() {
    AnkiDroidTheme {
        ConfirmDeleteDeckDialog(
            deckName = "Default",
            totalCards = 15,
            isFiltered = false,
            onDismissRequest = {},
            onConfirm = {}
        )
    }
}

@Preview(name = "Confirm Delete Deck Dialog - Filtered")
@Composable
private fun ConfirmDeleteDeckDialogFilteredPreview() {
    AnkiDroidTheme {
        ConfirmDeleteDeckDialog(
            deckName = "Cram Deck",
            totalCards = 0,
            isFiltered = true,
            onDismissRequest = {},
            onConfirm = {}
        )
    }
}
