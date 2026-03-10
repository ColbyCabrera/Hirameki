/****************************************************************************************
 * Copyright (c) 2022 Ali Ahnaf <aliahnaf327@gmail.com>                                 *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see http://www.gnu.org/licenses/>.                            *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.Prefs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyAccountTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MyAccount>()

    @Before
    fun setup() {
        Prefs.username = ""
        Prefs.hkey = ""
    }

    @Test
    fun testLoginEmailPasswordProvided() {
        val testPassword = "randomStrongPassword"
        val testEmail = "random.email@example.com"

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.username))
            .performTextInput(testEmail)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.password))
            .performTextInput(testPassword)

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.log_in))
            .assertIsEnabled()
    }

    @Test
    fun testLoginFailsNoEmailProvided() {
        val testPassword = "randomStrongPassword"

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.password))
            .performTextInput(testPassword)

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.log_in))
            .assertIsNotEnabled()
    }

    @Test
    fun testLoginFailsNoPasswordProvided() {
        val testEmail = "random.email@example.com"

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.username))
            .performTextInput(testEmail)

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.log_in))
            .assertIsNotEnabled()
    }
}
