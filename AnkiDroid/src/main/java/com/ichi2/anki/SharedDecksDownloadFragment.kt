/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.SharedDecksActivity.Companion.DOWNLOAD_FILE
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.shareddecks.DownloadIntent
import com.ichi2.anki.ui.compose.shareddecks.DownloadStatus
import com.ichi2.anki.ui.compose.shareddecks.SharedDecksDownloadScreen
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.openUrl
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.compat.CompatHelper.Companion.registerReceiverCompat
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.URLConnection

/**
 * Used when a download is captured from AnkiWeb shared decks WebView.
 * Only for downloads started via [SharedDecksActivity].
 *
 * Only one download is supported at a time, since importing multiple decks
 * simultaneously is not supported.
 */
class SharedDecksDownloadFragment : Fragment() {

    private val viewModel: SharedDecksDownloadViewModel by viewModels()

    /**
     * Android's DownloadManager - Used here to manage the functionality of downloading decks, one
     * at a time. Responsible for enqueuing a download and generating the corresponding download ID,
     * removing a download from the queue and providing cursor using a query related to the download ID.
     * Since only one download is supported at a time, the DownloadManager's queue is expected to
     * have a single request at a time.
     */
    private lateinit var downloadManager: DownloadManager

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showCancelConfirmationDialog()
        }
    }

    companion object {
        const val EXTRA_IS_SHARED_DOWNLOAD = "extra_is_shared_download"

        /**
         * The folder on the app's external storage([Context.getExternalFilesDir]) where downloaded
         * decks will be temporarily stored before importing.
         *
         * Note: when changing this constant make sure to also change the associated entry in filepaths.xml
         * so our FileProvider can actually serve the file!
         */
        const val SHARED_DECKS_DOWNLOAD_FOLDER = "shared_decks"

        private val deckIdRegex = "download-deck/(\\d+)".toRegex()

        /**
         * Given the URI of a deck's download URL such as
         * https://ankiweb.net/svc/shared/download-deck/1104981491?t=eyJvcCI6InNkZCIsImlhdCI6MTc0MTUyNjQ0OSwianYiOjF9.hr4a_G-LAqMVBAp5_95l60_2lEtYxodGl4DrJ6dT2WI
         * returns the deck's id, in this case "1104981491" if it can be found.
         */
        @VisibleForTesting
        fun getDeckIdFromDownloadURL(downloadUrl: String) =
            deckIdRegex.find(downloadUrl)?.groups?.get(1)?.value

        /**
         * Given the URI of a deck's download URL such as
         * https://ankiweb.net/svc/shared/download-deck/1104981491?t=eyJvcCI6InNkZCIsImlhdCI6MTc0MTUyNjQ0OSwianYiOjF9.hr4a_G-LAqMVBAp5_95l60_2lEtYxodGl4DrJ6dT2WI
         * returns the deck's page URL such as https://ankiweb.net/shared/info/1104981491
         * If the deck id can't be found, returns the ankiweb's shared deck's main page.
         */
        @VisibleForTesting
        fun Context.getDeckPageUri(deckDownloadURL: String): String {
            val deckId = getDeckIdFromDownloadURL(deckDownloadURL)
            return if (deckId != null) {
                getString(R.string.shared_deck_info) + deckId
            } else {
                getString(R.string.shared_decks_url)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val fileToBeDownloaded = arguments?.getSerializableCompat<DownloadFile>(DOWNLOAD_FILE)
        if (fileToBeDownloaded == null) {
            Timber.w("SharedDecksDownloadFragment started without DOWNLOAD_FILE argument")
            parentFragmentManager.popBackStack()
            return View(requireContext())
        }

        return ComposeView(requireContext()).apply {
            setContent {
                AnkiDroidTheme {
                    val state by viewModel.uiState.collectAsState()
                    SharedDecksDownloadScreen(
                        state = state,
                        onNavigateUp = { activity?.onBackPressedDispatcher?.onBackPressed() },
                        onIntent = { intent ->
                            when (intent) {
                                DownloadIntent.ConfirmCancel -> {
                                    viewModel.onIntent(intent, downloadManager)
                                    resetDownloadState()
                                    parentFragmentManager.popBackStack()
                                }

                                DownloadIntent.RetryClicked -> {
                                    viewModel.onIntent(intent)
                                    downloadManager.remove(viewModel.downloadId)
                                    downloadFile(fileToBeDownloaded)
                                }

                                DownloadIntent.ImportClicked -> openDownloadedDeck(context)
                                DownloadIntent.OpenInBrowserClicked -> {
                                    viewModel.onIntent(intent)
                                    downloadManager.remove(viewModel.downloadId)
                                    openUrl(
                                        requireContext().getDeckPageUri(fileToBeDownloaded.url)
                                            .toUri()
                                    )
                                    parentFragmentManager.popBackStack()
                                }

                                else -> viewModel.onIntent(intent)
                            }
                        })
                }
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, onBackPressedCallback
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    onBackPressedCallback.isEnabled =
                        state.status == DownloadStatus.Downloading || state.status == DownloadStatus.WaitingForNetwork
                }
            }
        }

        val fileToBeDownloaded =
            arguments?.getSerializableCompat<DownloadFile>(DOWNLOAD_FILE) ?: return
        downloadManager = (activity as SharedDecksActivity).downloadManager

        if (viewModel.uiState.value.status == DownloadStatus.Idle) {
            downloadFile(fileToBeDownloaded)
        } else {
            // Re-register receiver and resume polling if we're still downloading/failed/complete
            if (viewModel.uiState.value.status == DownloadStatus.Downloading || viewModel.uiState.value.status == DownloadStatus.WaitingForNetwork || viewModel.uiState.value.status == DownloadStatus.Failed) {
                Timber.d("Resuming polling and re-registering receiver after config change")
                activity?.registerReceiverCompat(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                if (viewModel.uiState.value.status != DownloadStatus.Failed) {
                    startPolling(viewModel.downloadId)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterReceiver()
    }

    private fun startPolling(downloadId: Long) {
        viewModel.startPolling(downloadManager, downloadId)
    }

    /**
     * Register broadcast receiver for listening to download completion.
     * Set the request for downloading a deck, enqueue it in DownloadManager, store download ID and
     * file name, mark download to be in progress, set the title of the download screen and start
     * the download progress checker.
     */
    private fun downloadFile(fileToBeDownloaded: DownloadFile) {
        val externalFilesFolder = requireContext().getExternalFilesDir(null)
        if (externalFilesFolder == null) {
            showSnackbar(R.string.external_storage_unavailable)
            parentFragmentManager.popBackStack()
            return
        }
        // ensure the "shared_decks" folder exists
        val decksDownloadFolder = File(externalFilesFolder, SHARED_DECKS_DOWNLOAD_FOLDER)
        if (!decksDownloadFolder.exists() && !decksDownloadFolder.mkdirs()) {
            Timber.e(
                "Failed to create shared decks download folder: %s",
                decksDownloadFolder.absolutePath
            )
            showSnackbar(R.string.external_storage_unavailable)
            parentFragmentManager.popBackStack()
            return
        }
        // Register broadcast receiver for download completion.
        Timber.d("Registering broadcast receiver for download completion")
        activity?.registerReceiverCompat(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )

        val currentFileName = fileToBeDownloaded.toFileName(extension = "apkg")

        val downloadRequest = generateDeckDownloadRequest(fileToBeDownloaded, currentFileName)

        // Store unique download ID to be used when onReceive() of BroadcastReceiver gets executed.
        val downloadId = downloadManager.enqueue(downloadRequest)
        viewModel.setDownloadId(downloadId)
        Timber.d("Download ID -> $downloadId")
        Timber.d("File name -> $currentFileName")
        viewModel.setFileName(currentFileName)
        startPolling(downloadId)
    }

    private fun generateDeckDownloadRequest(
        fileToBeDownloaded: DownloadFile,
        currentFileName: String,
    ): DownloadManager.Request {
        val request: DownloadManager.Request =
            DownloadManager.Request(fileToBeDownloaded.url.toUri())
        request.setMimeType(fileToBeDownloaded.mimeType)

        val cookies = CookieManager.getInstance().getCookie(fileToBeDownloaded.url)

        request.addRequestHeader("Cookie", cookies)
        request.addRequestHeader("User-Agent", fileToBeDownloaded.userAgent)

        request.setTitle(currentFileName)

        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(
            context,
            null,
            "$SHARED_DECKS_DOWNLOAD_FOLDER/$currentFileName",
        )

        return request
    }

    /**
     * Registered in downloadFile() method.
     * When onReceive() is called, open the deck file in AnkiDroid to import it.
     */
    private var onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent?,
        ) {
            Timber.i("Download might be complete now, verify and continue with import")

            /**
             * @return Whether the data in the received data is an importable deck
             */
            fun verifyDeckIsImportable(): Boolean {
                val fileName = viewModel.uiState.value.fileName
                if (fileName.isEmpty()) {
                    // Send ACRA report
                    CrashReportService.sendExceptionReport(
                        "File name is empty",
                        "SharedDecksDownloadFragment::verifyDeckIsImportable",
                    )
                    return false
                }

                // Return if mDownloadId does not match with the ID of the completed download.
                if (viewModel.downloadId != intent?.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, 0
                    )
                ) {
                    Timber.w("Download id did not match expected id. Ignoring this download completion")
                    return false
                }

                // Halt execution if file doesn't have extension as 'apkg' or 'colpkg'
                if (!ImportUtils.isFileAValidDeck(fileName)) {
                    Timber.i("File does not have 'apkg' or 'colpkg' extension, abort the deck opening task")
                    checkDownloadStatusAndUnregisterReceiver(
                        isSuccessful = false, isInvalidDeckFile = true
                    )
                    return false
                }

                val query = DownloadManager.Query()
                query.setFilterById(viewModel.downloadId)
                val cursor = downloadManager.query(query)

                cursor.use {
                    // Return if cursor is empty.
                    if (!it.moveToFirst()) {
                        Timber.i("Empty cursor, cannot continue further with success check and deck import")
                        checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                        return false
                    }

                    val columnStatusIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val columnReasonIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_REASON)

                    // Return if download was not successful.
                    if (it.getInt(columnStatusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                        Timber.i("Download could not be successful, update UI and unregister receiver")
                        Timber.d(
                            "Status code -> ${it.getIntOrNull(columnStatusIndex)}, reason ${
                                it.getIntOrNull(
                                    columnReasonIndex
                                )
                            }"
                        )
                        checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                        return false
                    }
                }
                return true
            }

            val verified = try {
                verifyDeckIsImportable()
            } catch (exception: Exception) {
                Timber.w(exception)
                checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                return
            }

            if (!verified) {
                // Could be a retryable fault (we received notification of another file)
                // Otherwise, checkDownloadStatusAndUnregisterReceiver should have been called
                // to update the UI
                return
            }

            // Setting these since progress checker can stop before progress is updated to represent 100%
            viewModel.onDownloadComplete()

            Timber.i("Opening downloaded deck for import")
            openDownloadedDeck(context)

            Timber.d("Checking download status and unregistering receiver")
            checkDownloadStatusAndUnregisterReceiver(isSuccessful = true)
        }
    }

    /**
     * Open the downloaded deck using 'fileName'.
     */
    private fun openDownloadedDeck(context: Context?) {
        if (context == null) {
            Timber.w("Context is null, cannot open deck for import.")
            return
        }
        val fileName = viewModel.uiState.value.fileName
        val mimeType = URLConnection.guessContentTypeFromName(fileName)
        val fileIntent = Intent(context, IntentHandler::class.java)
        fileIntent.action = Intent.ACTION_VIEW

        val fileUri = run {
            if (fileName.isEmpty()) return@run null
            val sharedDecksPath =
                File(context.getExternalFilesDir(null), SHARED_DECKS_DOWNLOAD_FOLDER)
            FileProvider.getUriForFile(
                context,
                context.applicationContext?.packageName + ".apkgfileprovider",
                File(sharedDecksPath, fileName),
            )
        }

        if (fileUri == null) {
            Timber.w("fileUri is null, cannot open deck for import. fileName is empty.")
            return
        }

        Timber.d("File URI -> $fileUri")
        fileIntent.setDataAndType(fileUri, mimeType)
        fileIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        fileIntent.putExtra(EXTRA_IS_SHARED_DOWNLOAD, true)
        try {
            context.startActivity(fileIntent)
        } catch (e: ActivityNotFoundException) {
            showThemedToast(context, R.string.something_wrong, false)
            Timber.w(e)
        }
    }

    /**
     * Safely retrieves the integer value from the cursor at the specified column index.
     *
     * @param columnIndex The index of the column from which to retrieve the integer value.
     * @return The integer value from the cursor at the specified column index, or null if invalid or undefined.
     */
    private fun Cursor?.getIntOrNull(columnIndex: Int): Int? = try {
        if (columnIndex != -1) {
            this?.getInt(columnIndex)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Resets the download state by unregistering the receiver and updating progress tracking flags.
     */
    private fun resetDownloadState() {
        unregisterReceiver()
        viewModel.stopPolling()
    }

    /**
     * Unregister the mOnComplete broadcast receiver.
     */
    private fun unregisterReceiver() {
        Timber.d("Unregistering receiver")
        try {
            activity?.unregisterReceiver(onComplete)
        } catch (exception: IllegalArgumentException) {
            // This might throw an exception in cases where the receiver is already in unregistered state.
            // Log the exception in such cases, there is nothing else to do.
            Timber.w(exception)
            return
        }
    }

    /**
     * Handle download error scenarios.
     *
     * If there are any pending downloads, continue with them.
     * Else, set mIsPreviousDownloadOngoing as false and unregister mOnComplete broadcast receiver.
     */
    private fun checkDownloadStatusAndUnregisterReceiver(
        isSuccessful: Boolean,
        isInvalidDeckFile: Boolean = false,
    ) {
        if (!isSuccessful) {
            if (isInvalidDeckFile) {
                Timber.i("File is not a valid deck, hence return from the download screen")
                context?.let { showThemedToast(it, R.string.import_log_no_apkg, false) }
                // Go back if file is not a deck and cannot be imported
                parentFragmentManager.popBackStack()
            } else {
                Timber.i("Download failed, update UI and provide option to retry")
                context?.let { showThemedToast(it, R.string.something_wrong, false) }
                // Update UI if download could not be successful
                viewModel.onDownloadFailed()
            }
        }

        resetDownloadState()
    }

    private fun showCancelConfirmationDialog() {
        viewModel.showCancelDialog()
    }
}
