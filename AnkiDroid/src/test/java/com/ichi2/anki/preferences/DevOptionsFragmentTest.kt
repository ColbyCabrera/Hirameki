/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

import android.webkit.WebView
import androidx.fragment.app.commitNow
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevOptionsFragmentTest : RobolectricTest() {
    @Before
    fun mockWebView() {
        mockkStatic(WebView::class)
        every { WebView.setWebContentsDebuggingEnabled(any()) } returns Unit
    }

    @After
    fun restoreWebView() = unmockkStatic(WebView::class)

    @Test
    fun `changing JavaScript debugging applies it without a restart`() {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).onActivity { activity ->
            val devOptions = DevOptionsFragment()
            activity.supportFragmentManager.commitNow { add(R.id.settings_container, devOptions) }
            val debuggingSwitch = devOptions.requirePreference<SwitchPreferenceCompat>(R.string.html_javascript_debugging_key)

            debuggingSwitch.callChangeListener(true)
            verify { WebView.setWebContentsDebuggingEnabled(true) }

            debuggingSwitch.callChangeListener(false)
            verify { WebView.setWebContentsDebuggingEnabled(false) }
        }
    }
}
