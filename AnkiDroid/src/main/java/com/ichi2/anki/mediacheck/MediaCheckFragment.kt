/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.mediacheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MediaCheckFragment for displaying a list of media files that are either unused or missing.
 * It allows users to tag missing media files or delete unused ones.
 **/
class MediaCheckFragment : Fragment(R.layout.fragment_media_check) {
    private val viewModel: MediaCheckViewModel by viewModels()

    private lateinit var deleteMediaButton: MaterialButton
    private lateinit var tagMissingButton: MaterialButton
    private lateinit var webView: WebView

    private var previousMenuProvider: MenuProvider? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(TR.mediaCheckCheckMediaAction().toSentenceCase(requireContext(), R.string.check_media))
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        deleteMediaButton = view.findViewById(R.id.delete_used_media_button)
        tagMissingButton = view.findViewById(R.id.tag_missing_media_button)
        webView = view.findViewById(R.id.media_check_webview)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.mediaCheckResult.collectLatest { result ->
                        updateWebView(result?.report.orEmpty())
                        if (result != null) {
                            tagMissingButton.visibility = if (result.missingCount != 0) View.VISIBLE else View.GONE
                            deleteMediaButton.visibility = if (result.unusedCount != 0) View.VISIBLE else View.GONE
                            if (result.haveTrash) setupMenu()
                        }
                    }
                }

                launch {
                    viewModel.uiEvent.collectLatest { event ->
                        when (event) {
                            is MediaCheckViewModel.UiEvent.ShowResultDialog -> showResultDialog(event.titleRes, event.message)
                            is MediaCheckViewModel.UiEvent.ShowTrashRestoredDialog -> showTrashRestoredDialog()
                            is MediaCheckViewModel.UiEvent.ShowTrashDeletedDialog -> showTrashDeletedDialog()
                            is MediaCheckViewModel.UiEvent.ShowDeletionResult -> showDeletionResult()
                            is MediaCheckViewModel.UiEvent.ShowError -> {
                                AlertDialog.Builder(requireContext()).show {
                                    title(R.string.vague_error)
                                    message(text = event.message)
                                    positiveButton(R.string.dialog_ok)
                                }
                            }
                            is MediaCheckViewModel.UiEvent.ShowErrorRes -> {
                                AlertDialog.Builder(requireContext()).show {
                                    title(R.string.vague_error)
                                    message(text = getString(event.messageRes))
                                    positiveButton(R.string.dialog_ok)
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.progressState.collectLatest { state ->
                        val isIdle = state is MediaCheckViewModel.ProgressState.Idle
                        deleteMediaButton.isEnabled = isIdle
                        tagMissingButton.isEnabled = isIdle

                        when (state) {
                            is MediaCheckViewModel.ProgressState.ActiveRes -> {
                                withProgress(state.messageRes) {
                                    awaitCancellation()
                                }
                            }
                            MediaCheckViewModel.ProgressState.Idle -> {}
                        }
                    }
                }
            }
        }

        setupButtonListeners()

        // Start checking media automatically on launch if there is no prior result and no ongoing check
        if (viewModel.mediaCheckResult.value == null && viewModel.progressState.value is MediaCheckViewModel.ProgressState.Idle) {
            viewModel.checkMedia()
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        previousMenuProvider?.let { menuHost.removeMenuProvider(it) }

        val newProvider = object : MenuProvider {
            override fun onCreateMenu(
                menu: Menu,
                menuInflater: MenuInflater,
            ) {
                menuInflater.inflate(R.menu.media_check_menu, menu)
                menu.findItem(R.id.action_restore_trash).apply {
                    isVisible = true
                    title = TR.mediaCheckRestoreTrash().toSentenceCase(requireContext(), R.string.sentence_restore_deleted)
                }
                menu.findItem(R.id.action_empty_trash).apply {
                    isVisible = true
                    title = TR.mediaCheckEmptyTrash().toSentenceCase(requireContext(), R.string.sentence_empty_trash)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_restore_trash -> {
                        confirmMediaRestore()
                        true
                    }
                    R.id.action_empty_trash -> {
                        deleteTrash()
                        true
                    }
                    else -> false
                }
        }

        menuHost.addMenuProvider(newProvider, viewLifecycleOwner)
        previousMenuProvider = newProvider
    }

    private fun updateWebView(report: String) {
        val html =
            """
            <html>
                <body style="
                      padding: 0px 8px;
                    font-size:14px;
                    white-space: pre-wrap;">$report
                </body>
            </html>
            """.trimIndent()

        webView.webViewClient = WebViewClient()
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun setupButtonListeners() {
        tagMissingButton.apply {
            text = TR.mediaCheckAddTag().toSentenceCase(requireContext(), R.string.sentence_tag_missing)

            setOnClickListener {
                viewModel.tagMissing(TR.mediaCheckMissingMediaTag())
            }
        }

        deleteMediaButton.apply {
            text =
                TR.mediaCheckDeleteUnused().toSentenceCase(
                    requireContext(),
                    R.string.sentence_check_media_delete_unused,
                )

            setOnClickListener {
                deleteConfirmationDialog()
            }
        }
    }

    private fun confirmMediaRestore() {
        viewModel.restoreTrash()
    }

    private fun deleteTrash() {
        viewModel.deleteTrash()
    }

    private fun deleteConfirmationDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckDeleteUnusedConfirm())
            positiveButton(R.string.dialog_ok) { handleDeleteConfirmation() }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun handleDeleteConfirmation() {
        viewModel.deleteUnusedMedia()
    }

    /**
     * Displays the result of a media deletion operation and updates stored trash statistics.
     *
     * This function retrieves the previously stored trash information (if any),
     * combines it with the current deletion statistics from the ViewModel, and
     * updates the stored values accordingly.
     */
    private fun showDeletionResult() {
        showResultDialog(
            R.string.delete_media_result_title,
            resources.getQuantityString(
                R.plurals.delete_media_result_message,
                viewModel.deletedFiles,
                viewModel.deletedFiles,
            ),
        )
    }

    private fun showTrashRestoredDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckTrashRestored())
            positiveButton(R.string.dialog_ok) {
                requireActivity().finish()
            }
            cancelable(false)
        }
    }

    private fun showTrashDeletedDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckTrashEmptied())
            positiveButton(R.string.dialog_ok) {
                requireActivity().finish()
            }
            cancelable(false)
        }
    }

    private fun showResultDialog(
        titleRes: Int,
        message: String,
    ) {
        AlertDialog.Builder(requireContext()).show {
            title(titleRes)
            message(text = message)
            positiveButton(R.string.dialog_ok) {
                requireActivity().finish()
            }
            cancelable(false)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, MediaCheckFragment::class)
    }
}
