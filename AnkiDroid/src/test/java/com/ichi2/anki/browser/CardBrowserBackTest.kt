/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.browser

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardBrowserBackTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun `back leaves multi-select before it leaves the browser`() {
        addBasicNote("Hello", "World")
        val browser = startRegularActivity<CardBrowser>()
        composeTestRule.waitForIdle()
        // CardBrowser created it with the default key, so this returns the browser's instance
        val viewModel = ViewModelProvider(browser)[CardBrowserViewModel::class.java]

        viewModel.selectAll()
        advanceRobolectricLooper()
        composeTestRule.waitForIdle()
        assertThat("rows are selected", viewModel.isInMultiSelectMode, equalTo(true))

        composeTestRule.runOnIdle { browser.onBackPressedDispatcher.onBackPressed() }
        advanceRobolectricLooper()
        composeTestRule.waitForIdle()
        assertThat("back ends multi-select", viewModel.isInMultiSelectMode, equalTo(false))
        assertThat("back ends multi-select first", browser.isFinishing, equalTo(false))

        composeTestRule.runOnIdle { browser.onBackPressedDispatcher.onBackPressed() }
        assertThat("the next back leaves", browser.isFinishing, equalTo(true))
    }
}
