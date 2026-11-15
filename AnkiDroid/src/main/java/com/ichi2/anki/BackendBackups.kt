// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

suspend fun performBackupInBackground() {
    // Wait a second to allow the deck list to finish loading first, or it
    // will hang until the first stage of the backup completes.
    delay(1000)
    createBackup(force = false)
}

fun <Activity> Activity.importColpkg(colpkgPath: String) where Activity : AnkiActivity, Activity : ImportColpkgListener {
    launchCatchingTask {
        withProgress(
            extractProgress = {
                if (progress.hasImporting()) {
                    text = progress.importing
                }
            },
        ) {
            CollectionManager.importColpkg(colpkgPath)
        }

        onImportColpkg(colpkgPath)
    }
}

private suspend fun createBackup(force: Boolean) {
    withCol {
        // this two-step approach releases the backend lock after the initial copy
        createBackup(
            BackupManager.getBackupDirectoryFromCollection(colDb),
            force,
            waitForCompletion = false,
        )
    }
    // move this outside 'withCol' to avoid blocking
    withContext(Dispatchers.IO) {
        CollectionManager.getBackend().awaitBackupCompletion()
    }
}
