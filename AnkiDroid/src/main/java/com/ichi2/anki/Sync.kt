/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import anki.backend.backendError
import anki.collection.Progress
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.SyncStatusResponse
import anki.sync.syncAuth
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.observability.ChangeManager.notifySubscribersAllValuesChanged
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import com.ichi2.utils.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private const val SYNC_DIALOG_MINIMUM_INTERVAL_SECONDS = 5L

/**
 * the automatic sync on app start is skipped when the last sync is more recent than this.
 * the automatic sync preference summary (automatic_sync_choice_summ) states this number
 */
val AUTOMATIC_SYNC_MINIMAL_INTERVAL = 10.minutes

/**
 * collection syncs running in this process, started from the deck list or by [SyncWorker].
 * a count rather than a flag: both can run at once (one waits for the other's collection lock),
 * and the first to end must not report that nothing is running
 */
private val runningSyncs = AtomicInteger(0)

/** whether a collection sync is running in this process. automatic syncs never start on top of one */
val isSyncRunning: Boolean
    get() = runningSyncs.get() > 0

/** runs [block], a collection sync, with [isSyncRunning] reporting it */
suspend fun <T> trackRunningSync(block: suspend () -> T): T {
    runningSyncs.incrementAndGet()
    try {
        return block()
    } finally {
        runningSyncs.decrementAndGet()
    }
}

object SyncPreferences {
    const val CURRENT_SYNC_URI = "currentSyncUri"
    const val CUSTOM_SYNC_URI = "syncBaseUrl"
    const val CUSTOM_SYNC_ENABLED =
        CUSTOM_SYNC_URI + VersatileTextWithASwitchPreference.SWITCH_SUFFIX
}

enum class ConflictResolution {
    FULL_DOWNLOAD, FULL_UPLOAD,
}

fun syncAuth(): SyncAuth? {
    // Grab custom sync certificate from preferences (default is the empty string) and set it in CollectionManager
    val currentSyncCertificate = Prefs.customSyncCertificate ?: ""
    CollectionManager.updateCustomCertificate(currentSyncCertificate)

    val resolvedEndpoint = getEndpoint()
    return Prefs.hkey?.let {
        syncAuth {
            this.hkey = it
            if (resolvedEndpoint != null) {
                this.endpoint = resolvedEndpoint
            }
        }
    }
}

fun getEndpoint(): String? {
    val currentEndpoint = Prefs.currentSyncUri?.ifEmpty { null }
    val customEndpoint = if (Prefs.isCustomSyncEnabled) {
        Prefs.customSyncUri
    } else {
        null
    }
    return currentEndpoint ?: customEndpoint
}

/**
 * Whether the user has a sync account.
 * Returning true does not guarantee that the user actually synced recently,
 * or even that the ankiweb account is still valid.
 */
fun isLoggedIn(): Boolean = !Prefs.hkey.isNullOrEmpty()

fun millisecondsSinceLastSync() = TimeManager.time.intTimeMS() - Prefs.lastSyncTime

/** what an automatic sync found: the login to sync with and what the collection needs */
data class AutomaticSyncStatus(
    val auth: SyncAuth,
    val required: SyncStatusResponse.Required,
)

/**
 * checks the conditions of an automatic sync, e.g. auto sync is enabled and the user is logged in, then
 * asks the backend what the collection needs. shared by the sync on app start and the sync on leaving
 * the app ([AutomaticSyncOnLeave]), so both follow the same rules.
 *
 * the status check reaches the server only when there are no local changes, and the backend reuses the
 * server's answer for 5 minutes, so checking on every leave costs at most one request in that time
 *
 * @param checkInterval whether to skip the sync when the last one was less than
 * [AUTOMATIC_SYNC_MINIMAL_INTERVAL] ago
 * @return null when a condition holds the sync back
 */
suspend fun automaticSyncStatus(checkInterval: Boolean): AutomaticSyncStatus? {
    when {
        !Prefs.isAutoSyncEnabled -> Timber.d("autoSync: not enabled")
        isSyncRunning -> Timber.d("autoSync: a sync is already running")
        !Prefs.allowSyncOnMeteredConnections && NetworkUtils.isActiveNetworkMetered() ->
            Timber.d("autoSync: blocked by metered connection")
        !NetworkUtils.isOnline -> Timber.d("autoSync: offline")
        checkInterval && millisecondsSinceLastSync() <= AUTOMATIC_SYNC_MINIMAL_INTERVAL.inWholeMilliseconds ->
            Timber.d("autoSync: interval not passed")
        !isLoggedIn() -> Timber.d("autoSync: not logged in")
        // the status check needs an open collection. a closed one is not reopened for it: it may be
        // closed on purpose (a one-way sync, an import) or never opened (first run, no storage access)
        withOpenColOrNull { true } == null -> Timber.d("autoSync: collection not open")
        else -> {
            val auth = syncAuth() ?: return null
            val required =
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().syncStatus(auth)
                }.required
            Timber.d("autoSync: %s", required)
            return AutomaticSyncStatus(auth, required)
        }
    }
    return null
}

/**
 * an automatic sync found no collection changes: syncs media only (if media fetching allows it) and
 * records the sync time, as a sync that found nothing to do still counts as recent
 */
fun syncMediaWithoutCollectionChanges(
    context: Context,
    auth: SyncAuth,
) {
    Timber.d("autoSync: no collection changes to sync. Syncing media if set")
    if (shouldFetchMedia()) {
        SyncMediaWorker.start(context, auth)
    }
    setLastSyncTimeToNow()
}

/**
 * after a collection sync pulled changes: the backend does not know about the note type cache, so styles
 * changed on another device would keep rendering the old way (#14827), and open screens and the widgets
 * would keep showing old data. shared by the sync on the deck list and [SyncWorker]
 */
suspend fun refreshAfterCollectionSync(handler: Any?) {
    withCol { notetypes.clearCache() }
    // subscribers are screens: they must hear about it on the main thread
    withContext(Dispatchers.Main.immediate) {
        notifySubscribersAllValuesChanged(handler)
    }
}

fun DeckPicker.handleNewSync(
    conflict: ConflictResolution?,
    syncMedia: Boolean,
) {
    val auth = syncAuth()
    if (auth == null) {
        viewModel.isSyncing.value = false
        return
    }
    val deckPicker = this
    launchCatchingTask {
        trackRunningSync {
            try {
                val syncCompleted = when (conflict) {
                    ConflictResolution.FULL_DOWNLOAD -> {
                        handleDownload(
                            deckPicker,
                            auth,
                            deckPicker.mediaUsnOnConflict,
                        )
                        true
                    }

                    ConflictResolution.FULL_UPLOAD -> {
                        handleUpload(
                            deckPicker,
                            auth,
                            deckPicker.mediaUsnOnConflict,
                        )
                        true
                    }

                    null -> handleNormalSync(deckPicker, auth, syncMedia)
                }
                if (syncCompleted) {
                    refreshAfterCollectionSync(deckPicker)
                    setLastSyncTimeToNow()
                    refreshState()
                }
            } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
                // auth failed; log out
                updateLogin("", "")
                throw exc
            } catch (exc: BackendNetworkException) {
                Timber.w(exc, "Network error during sync")
                deckPicker.viewModel.setShowNetworkErrorDialog(true)
                deckPicker.refreshState()
            } finally {
                deckPicker.viewModel.isSyncing.value = false
            }
        }
    }
}

fun updateLogin(
    username: String,
    hkey: String,
) {
    Prefs.username = username
    Prefs.hkey = hkey
}

fun cancelSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortSync()
}

private suspend fun handleNormalSync(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    syncMedia: Boolean,
): Boolean {
    Timber.i("Sync: Normal collection sync")
    var auth2 = auth
    val viewModel = deckPicker.viewModel
    val backend = CollectionManager.getBackend()

    val hasChanges = try {
        withContext(Dispatchers.IO) {
            val status = backend.syncStatus(auth)
            status.required != SyncStatusResponse.Required.NO_CHANGES
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to get sync status")
        true
    }
    val showDialog = hasChanges || millisecondsSinceLastSync() > TimeUnit.SECONDS.toMillis(
        SYNC_DIALOG_MINIMUM_INTERVAL_SECONDS,
    )

    if (showDialog) {
        viewModel.showSyncDialog(deckPicker.getString(R.string.syncing), "") {
            cancelSync(backend)
        }
    }

    val syncJob = if (showDialog) {
        deckPicker.lifecycleScope.launch {
            while (isActive) {
                val progress = backend.latestProgress()
                if (progress.hasNormalSync()) {
                    val added = progress.normalSync.added
                    val removed = progress.normalSync.removed
                    viewModel.updateSyncDialog("$added\n$removed")
                }
                delay(100.milliseconds)
            }
        }
    } else {
        null
    }

    val output = try {
        withCol {
            syncCollection(auth2, syncMedia = false) // media is synced by SyncMediaWorker
        }
    } finally {
        syncJob?.cancel()
        if (showDialog) {
            viewModel.hideSyncDialog()
        }
    }

    if (output.hasNewEndpoint() && output.newEndpoint.isNotEmpty()) {
        Timber.i("sync endpoint updated")
        Prefs.currentSyncUri = output.newEndpoint
        auth2 = syncAuth {
            this.hkey = auth.hkey
            endpoint = output.newEndpoint
        }
    }
    val mediaUsn = if (syncMedia) {
        output.serverMediaUsn
    } else {
        null
    }

    Timber.i("sync result: ${output.required}")
    return when (output.required) {
        // a successful sync returns this value
        SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
            // scheduler version may have changed
            withCol { _loadScheduler() }
            if (hasChanges) {
                val message = if (syncMedia) {
                    R.string.col_synced_media_in_background
                } else {
                    R.string.sync_database_acknowledge
                }
                deckPicker.showSyncLogMessage(message, output.serverMessage)
            }
            if (syncMedia) {
                SyncMediaWorker.start(deckPicker, auth2)
            }
            true
        }

        SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
            handleDownload(deckPicker, auth2, mediaUsn)
            true
        }

        SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
            handleUpload(deckPicker, auth2, mediaUsn)
            true
        }

        SyncCollectionResponse.ChangesRequired.FULL_SYNC -> {
            deckPicker.mediaUsnOnConflict = mediaUsn
            deckPicker.showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_RESOLUTION)
            false
        }

        SyncCollectionResponse.ChangesRequired.NORMAL_SYNC,
        SyncCollectionResponse.ChangesRequired.UNRECOGNIZED,
        null,
            -> {
            Timber.e("Unexpected sync status: ${output.required}")
            throw BackendNetworkException(backendError {})
        }
    }
}

private fun fullDownloadProgress(title: String): ProgressContext.() -> Unit = {
    fun Progress.FullSync.toAmount() = ProgressContext.Amount(transferred.toLong(), total.toLong())
    text = title
    amount = if (progress.hasFullSync() && progress.fullSync.total > 0) {
        progress.fullSync.toAmount()
    } else {
        null
    }
}

private suspend fun handleDownload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    mediaUsn: Int?,
) {
    Timber.i("Sync: Full collection download requested")
    deckPicker.withProgress(
        progressContext = ProgressContext.ofBytes(context = deckPicker).copy(separator = "\n"),
        extractProgress = fullDownloadProgress(TR.syncDownloadingFromAnkiweb()),
        onCancel = ::cancelSync,
        manualCancelButton = R.string.dialog_cancel,
    ) {
        withCol {
            try {
                createBackup(
                    BackupManager.getBackupDirectoryFromCollection(colDb),
                    force = true,
                    waitForCompletion = true,
                )
                close(downgrade = false, forFullSync = true)
                fullUploadOrDownload(auth, upload = false, serverUsn = mediaUsn)
            } finally {
                reopen(afterFullSync = true)
            }
        }
        if (mediaUsn != null) {
            SyncMediaWorker.start(deckPicker, auth)
        }
    }

    Timber.i("Full Download Completed")
    deckPicker.showSyncLogMessage(R.string.backup_one_way_sync_from_server, "")
}

private suspend fun handleUpload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    mediaUsn: Int?,
) {
    Timber.i("Sync: Full collection upload requested")
    deckPicker.withProgress(
        progressContext = ProgressContext.ofBytes(context = deckPicker).copy(separator = "\n"),
        extractProgress = fullDownloadProgress(TR.syncUploadingToAnkiweb()),
        onCancel = ::cancelSync,
        manualCancelButton = R.string.dialog_cancel,
    ) {
        withCol {
            close(downgrade = false, forFullSync = true)
            try {
                fullUploadOrDownload(auth, upload = true, serverUsn = mediaUsn)
            } finally {
                reopen(afterFullSync = true)
            }
        }
        if (mediaUsn != null) {
            SyncMediaWorker.start(deckPicker, auth)
        }
    }
    Timber.i("Full Upload Completed")
    deckPicker.showSyncLogMessage(R.string.sync_log_uploading_message, "")
}

fun cancelMediaSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortMediaSync()
}

/**
 * Whether media should be fetched on sync. Options from preferences are:
 * * Always
 * * Only if unmetered
 * * Never
 */
fun shouldFetchMedia(): Boolean {
    val shouldFetchMedia = Prefs.shouldFetchMedia
    return shouldFetchMedia == ShouldFetchMedia.ALWAYS || (shouldFetchMedia == ShouldFetchMedia.ONLY_UNMETERED && !NetworkUtils.isActiveNetworkMetered())
}

suspend fun monitorMediaSync(deckPicker: DeckPicker) {
    val backend = CollectionManager.getBackend()
    val viewModel = deckPicker.viewModel

    viewModel.showSyncDialog(TR.syncMediaLogTitle(), "") {
        cancelMediaSync(backend)
    }

    suspend fun showMessage(msg: String) = viewModel.showSnackbar(msg)

    withContext(Dispatchers.IO) {
        try {
            while (isActive) {
                // this will throw if the sync exited with an error
                val resp = backend.mediaSyncStatus()
                if (!resp.active) {
                    break
                }
                val text = resp.progress.run { "$added\n$removed\n$checked" }
                viewModel.updateSyncDialog(text)
                delay(100.milliseconds)
            }
            showMessage(TR.syncMediaComplete())
        } catch (_: BackendInterruptedException) {
            showMessage(TR.syncMediaAborted())
        } catch (_: CancellationException) {
            // do nothing
        } catch (_: Exception) {
            showMessage(TR.syncMediaFailed())
        } finally {
            viewModel.hideSyncDialog()
        }
    }
}

/**
 * Show a simple snackbar message or notification if the activity is not in foreground
 * @param messageResource String resource for message
 */
suspend fun DeckPicker.showSyncLogMessage(
    @StringRes messageResource: Int,
    syncMessage: String?,
) {
    if (activityPaused) {
        val res = AnkiDroidApp.appResources
        showSimpleNotification(
            res.getString(R.string.app_name),
            res.getString(messageResource),
            Channel.SYNC,
        )
    } else {
        if (syncMessage.isNullOrEmpty()) {
            viewModel.showSnackbar(getString(messageResource))
        } else {
            val res = AnkiDroidApp.appResources
            showSimpleMessageDialog(title = res.getString(messageResource), message = syncMessage)
        }
    }
}

fun setLastSyncTimeToNow() {
    Prefs.lastSyncTime = TimeManager.time.intTimeMS()
}
