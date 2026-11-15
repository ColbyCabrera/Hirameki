// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
