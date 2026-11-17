// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.compose.ImportFileSelectionDialog
import com.ichi2.anki.onSelectedClipboardForImport
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.MimeTypeUtils
import kotlinx.parcelize.Parcelize
import timber.log.Timber

class ImportFileSelectionFragment : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val entries = buildImportEntries()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    ImportFileSelectionDialog(
                        entries = entries,
                        onDismissRequest = { dismiss() },
                        onEntryClick = { entry ->
                            UsageAnalytics.sendAnalyticsEvent(
                                UsageAnalytics.Category.LINK_CLICKED,
                                entry.analyticsId,
                            )
                            if (entry.type == ImportFileType.CLIPBOARD) {
                                requireAnkiActivity().onSelectedClipboardForImport()
                            } else {
                                openImportFilePicker(
                                    activity = requireAnkiActivity(),
                                    fileType = entry.type,
                                    multiple = entry.multiple,
                                    mimeType = entry.mimeType,
                                    extraMimes = entry.extraMimes,
                                )
                            }
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    internal fun buildImportEntries(): List<ImportEntry> {
        return arguments?.let { args ->
            args.classLoader = this@ImportFileSelectionFragment::class.java.classLoader
            val options =
                BundleCompat.getParcelable(args, ARG_IMPORT_OPTIONS, ImportOptions::class.java)
                    ?: return emptyList()
            mutableListOf<ImportEntry>().apply {
                if (options.importApkg) {
                    add(
                        ImportEntry(
                            R.string.import_deck_package,
                            UsageAnalytics.Actions.IMPORT_APKG_FILE,
                            ImportFileType.APKG,
                        ),
                    )
                }
                if (options.importColpkg) {
                    add(
                        ImportEntry(
                            R.string.import_collection_package,
                            UsageAnalytics.Actions.IMPORT_COLPKG_FILE,
                            ImportFileType.COLPKG,
                        ),
                    )
                }
                if (options.importTextFile) {
                    add(
                        ImportEntry(
                            R.string.import_csv,
                            UsageAnalytics.Actions.IMPORT_CSV_FILE,
                            ImportFileType.CSV,
                            multiple = false,
                            mimeType = "*/*",
                            extraMimes = MimeTypeUtils.CSV_TSV_MIME_TYPES,
                        ),
                    )
                }
                if (options.importClipboard) {
                    add(
                        ImportEntry(
                            R.string.import_clipboard,
                            UsageAnalytics.Actions.IMPORT_CLIPBOARD,
                            ImportFileType.CLIPBOARD,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    // safe as this data class is used as a container and it's not involved in any comparing
    @Suppress("ArrayInDataClass")
    internal data class ImportEntry(
        @StringRes val titleRes: Int,
        val analyticsId: String,
        val type: ImportFileType,
        var multiple: Boolean = false,
        val mimeType: String = "*/*",
        val extraMimes: Array<String>? = null,
    )

    @Parcelize
    data class ImportOptions(
        val importColpkg: Boolean,
        val importApkg: Boolean,
        val importTextFile: Boolean,
        val importClipboard: Boolean = importTextFile,
    ) : Parcelable

    enum class ImportFileType {
        APKG,
        COLPKG,
        CSV,
        CLIPBOARD,
    }

    interface ApkgImportResultLauncherProvider {
        fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent>
    }

    interface CsvImportResultLauncherProvider {
        fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent>
    }

    companion object {
        private const val ARG_IMPORT_OPTIONS = "arg_import_options"

        fun newInstance(options: ImportOptions) =
            ImportFileSelectionFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_IMPORT_OPTIONS, options) }
            }

        /**
         * Calls through the system with an [Intent] to pick a file to be imported.
         */
        fun openImportFilePicker(
            activity: AnkiActivity,
            fileType: ImportFileType,
            multiple: Boolean = false,
            mimeType: String = "*/*",
            extraMimes: Array<String>? = null,
        ) {
            Timber.d("openImportFilePicker() delegating to file picker intent")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = mimeType
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra("android.content.extra.FANCY", true)
            intent.putExtra("android.content.extra.SHOW_FILESIZE", true)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            extraMimes?.let { intent.putExtra(Intent.EXTRA_MIME_TYPES, it) }

            try {
                if (
                    (fileType == ImportFileType.APKG || fileType == ImportFileType.COLPKG) &&
                    activity is ApkgImportResultLauncherProvider
                ) {
                    activity.getApkgFileImportResultLauncher().launch(intent)
                } else if (fileType == ImportFileType.CSV && activity is CsvImportResultLauncherProvider) {
                    activity.getCsvFileImportResultLauncher().launch(intent)
                } else {
                    Timber.w("Activity($activity) can't handle requested import: $fileType")
                }
            } catch (ex: ActivityNotFoundException) {
                Timber.w("No activity to handle openImportFilePicker request")
                activity.showSnackbar(R.string.activity_start_failed)
            }
        }
    }
}
