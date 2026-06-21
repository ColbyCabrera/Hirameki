/*
 * Copyright (c) 2026 AnkiDroid Open Source Team
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.compose

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

class ProgressDialogFragment : DialogFragment() {
    private companion object {
        const val STATE_TITLE = "title"
        const val STATE_MESSAGE = "message"
        const val STATE_CANCEL_LABEL_RES_ID = "cancelLabelResId"
        const val STATE_IS_CANCEL_BUTTON_SET = "isCancelButtonSet"
    }

    private val titleState = mutableStateOf("")
    private val messageState = mutableStateOf("")
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private var onCancelCallback: (() -> Unit)? = null
    private var cancelLabelResId: Int? = null
    private var isCancelButtonSet = false

    var title: String
        get() = titleState.value
        set(value) {
            titleState.value = value
        }

    var message: String
        get() = messageState.value
        set(value) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                updateMessageState(value)
            } else {
                mainThreadHandler.post {
                    updateMessageState(value)
                }
            }
        }

    private fun updateMessageState(value: String) {
        val lines = value.split('\n')
        if (lines.size > 1) {
            titleState.value = lines[0]
            messageState.value = lines.subList(1, lines.size).joinToString("\n")
        } else {
            titleState.value = ""
            messageState.value = value
        }
    }

    fun setOnCancel(onCancel: (() -> Unit)?) {
        onCancelCallback = onCancel
    }

    fun setCancelButton(labelResId: Int) {
        cancelLabelResId = labelResId
        isCancelButtonSet = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            titleState.value = savedInstanceState.getString(STATE_TITLE).orEmpty()
            messageState.value = savedInstanceState.getString(STATE_MESSAGE).orEmpty()
            isCancelButtonSet = savedInstanceState.getBoolean(STATE_IS_CANCEL_BUTTON_SET)
            cancelLabelResId = if (savedInstanceState.containsKey(STATE_CANCEL_LABEL_RES_ID)) {
                savedInstanceState.getInt(STATE_CANCEL_LABEL_RES_ID)
            } else {
                null
            }
        }
        // Dialog should be non-dismissable if cancel button is present or if onCancelCallback is null
        isCancelable = onCancelCallback != null && cancelLabelResId == null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TITLE, title)
        outState.putString(STATE_MESSAGE, message)
        outState.putBoolean(STATE_IS_CANCEL_BUTTON_SET, isCancelButtonSet)
        cancelLabelResId?.let { outState.putInt(STATE_CANCEL_LABEL_RES_ID, it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val title by titleState
                    val message by messageState
                    ProgressDialogContent(
                        title = title,
                        message = message,
                        onCancel = onCancelCallback,
                        cancelLabelResId = cancelLabelResId,
                        isCancelable = isCancelable,
                        showCancelButton = isCancelButtonSet,
                        onDismiss = { dismissAllowingStateLoss() })
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProgressDialogContent(
    title: String,
    message: String,
    onCancel: (() -> Unit)?,
    cancelLabelResId: Int?,
    isCancelable: Boolean,
    showCancelButton: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = {
        if (isCancelable) {
            onCancel?.invoke()
            onDismiss()
        }
    }, title = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotEmpty()) {
                Text(text = title)
            }
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
    }, text = {
        if (message.isNotEmpty()) {
            Text(text = message)
        }
    }, confirmButton = {}, dismissButton = {
        if (onCancel != null && showCancelButton) {
            val label = cancelLabelResId?.let { stringResource(id = it) }
                ?: stringResource(id = R.string.dialog_cancel)
            TextButton(onClick = {
                onCancel.invoke()
                onDismiss()
            }) {
                Text(text = label)
            }
        }
    })
}

@Preview(name = "Progress Dialog - Title and Message", heightDp = 400, widthDp = 500)
@Composable
private fun ProgressDialogContentPreview() {
    AnkiDroidTheme {
        ProgressDialogContent(
            title = "Importing...",
            message = "Please wait while the file is being imported.",
            onCancel = {},
            cancelLabelResId = null,
            isCancelable = true,
            showCancelButton = true,
            onDismiss = {})
    }
}

@Preview(name = "Progress Dialog - Message Only", heightDp = 400, widthDp = 500)
@Composable
private fun ProgressDialogContentMessageOnlyPreview() {
    AnkiDroidTheme {
        ProgressDialogContent(
            title = "",
            message = "Syncing with AnkiWeb...",
            onCancel = null,
            cancelLabelResId = null,
            isCancelable = false,
            showCancelButton = false,
            onDismiss = {})
    }
}
