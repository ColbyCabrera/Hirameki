// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import anki.import_export.ExportLimit
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.ExportReadyDialog
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.exportAnkiPackage
import com.ichi2.anki.libanki.exportCardsCsv
import com.ichi2.anki.libanki.exportNotesCsv
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException

fun AnkiActivity.exportApkgPackage(
    exportPath: String,
    withScheduling: Boolean,
    withDeckConfigs: Boolean,
    withMedia: Boolean,
    limit: ExportLimit,
    legacy: Boolean,
) {
    launchCatchingTask {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = progress.exporting
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol { exportAnkiPackage(exportPath, withScheduling, withDeckConfigs, withMedia, limit, legacy) }
        }
        showAsyncDialogFragment(ExportReadyDialog.newInstance(exportPath))
    }
}

fun AnkiActivity.exportCollectionPackage(
    exportPath: String,
    withMedia: Boolean,
    legacy: Boolean,
) {
    launchCatchingTask(skipCrashReport = { it.message == TR.errorsPleaseCheckMedia() }) {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = progress.exporting
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol { exportCollectionPackage(exportPath, withMedia, legacy) }
        }
        showAsyncDialogFragment(ExportReadyDialog.newInstance(exportPath))
    }
}

fun AnkiActivity.exportSelectedNotes(
    exportPath: String,
    withHtml: Boolean,
    withTags: Boolean,
    withDeck: Boolean,
    withNotetype: Boolean,
    withGuid: Boolean,
    limit: ExportLimit,
) {
    launchCatchingTask {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = progress.exporting
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol {
                exportNotesCsv(
                    exportPath,
                    withHtml,
                    withTags,
                    withDeck,
                    withNotetype,
                    withGuid,
                    limit,
                )
            }
        }
        showAsyncDialogFragment(ExportReadyDialog.newInstance(exportPath))
    }
}

fun AnkiActivity.exportSelectedCards(
    exportPath: String,
    withHtml: Boolean,
    limit: ExportLimit,
) {
    launchCatchingTask {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = progress.exporting
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol {
                exportCardsCsv(exportPath, withHtml, limit)
            }
        }
        showAsyncDialogFragment(ExportReadyDialog.newInstance(exportPath))
    }
}

/**
 * Export the collection into a .colpkg file.
 * If legacy=false, a file targeting Anki 2.1.50+ is created. It compresses better and is faster to
 * create, but older clients can not read it.
 *
 * @throws BackendInvalidInputException - 'Check Media' required.
 *  See [anki.i18n.GeneratedTranslations.errorsPleaseCheckMedia]
 */
private fun Collection.exportCollectionPackage(
    outPath: String,
    includeMedia: Boolean,
    legacy: Boolean,
) {
    close(forFullSync = true)
    backend.exportCollectionPackage(
        outPath = outPath,
        includeMedia = includeMedia,
        legacy = legacy,
    )
    reopen()
}
