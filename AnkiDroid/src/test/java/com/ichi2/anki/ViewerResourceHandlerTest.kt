/*
 * Copyright (c) 2026 Hirameki contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.themes.ArabicScriptFont
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewerResourceHandlerTest : RobolectricTest() {
    @Test
    fun `serves the card arabic-script font on the page origin`() {
        val request =
            mockk<WebResourceRequest> {
                every { method } returns "GET"
                every { url } returns "http://127.0.0.1:1234${ArabicScriptFont.CARD_FONT_PATH}".toUri()
            }

        val response = ViewerResourceHandler(targetContext).shouldInterceptRequest(request)

        assertThat(response, notNullValue())
        assertThat(response!!.mimeType, equalTo("font/ttf"))
        // 00 01 00 00 is the truetype (sfnt version 1.0) header of res/font/vazirmatn.ttf. kotlin's readBytes, not
        // InputStream.readNBytes: that one is android api 33 and lint checks test sources against minSdk 31
        val header = response.data.use { it.readBytes() }.take(4)
        assertThat(header, equalTo(listOf<Byte>(0, 1, 0, 0)))
    }
}
