/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.scheduling.Fsrs
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.ankiweb.rsdroid.BuildConfig as BackendBuildConfig

class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            // Dispose of the Composition when the view's LifecycleOwner
            // is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val apkBuildDate =
                        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM yyyy"))
                            .format(Date(BuildConfig.BUILD_TIME))

                    val backendText =
                        "(anki " + BackendBuildConfig.ANKI_DESKTOP_VERSION + " / " + BackendBuildConfig.ANKI_COMMIT_HASH.subSequence(0, 8) +
                            ")"

                    val fsrsText = Fsrs.displayVersion?.let { "($it)" } ?: ""

                    val contributorsLink = getString(R.string.link_contributors)
                    val contributingGuideLink = getString(R.string.link_contribution)
                    val contributorsText = getString(R.string.about_contributors_description, contributorsLink, contributingGuideLink)

                    val gplLicenseLink = getString(R.string.licence_wiki)
                    val agplLicenseLink = getString(R.string.link_agpl_wiki)
                    val sourceCodeLink = getString(R.string.link_source)
                    val dependencyLicenseLink = getString(R.string.dependency_license_wiki)
                    val licenseText = (
                        getString(R.string.license_description, gplLicenseLink, agplLicenseLink, sourceCodeLink) + "<br>" +
                            getString(R.string.other_licenses, dependencyLicenseLink)
                    )

                    AboutScreen(
                        versionText = pkgVersionName,
                        buildDateText = apkBuildDate,
                        backendText = backendText,
                        fsrsText = fsrsText,
                        contributorsText = contributorsText,
                        licenseText = licenseText,
                        onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onCopyDebugClick = { copyDebugInfo() },
                    )
                }
            }
        }

    /**
     * Copies debug info (from [DebugInfoService.getDebugInfo]) to the clipboard
     */
    private fun copyDebugInfo() {
        launchCatchingTask {
            val debugInfo =
                withContext(Dispatchers.IO) {
                    DebugInfoService.getDebugInfo(requireContext())
                }
            requireContext().copyToClipboard(
                debugInfo,
                failureMessageId = R.string.about_ankidroid_error_copy_debug_info,
            )
        }
    }
}
