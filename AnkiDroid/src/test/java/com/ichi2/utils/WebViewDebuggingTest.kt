// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import android.util.AndroidRuntimeException
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewDebuggingTest {
    @Before
    fun mockWebView() = mockkStatic(WebView::class)

    @After
    fun restoreWebView() = unmockkStatic(WebView::class)

    @Test
    fun `a device with no WebView doesn't crash`() {
        every { WebView.setWebContentsDebuggingEnabled(any()) } throws AndroidRuntimeException("No WebView installed")

        setWebContentsDebuggingEnabled(true)
    }
}
