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
