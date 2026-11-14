// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ali Ahnaf <aliahnaf327@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.username))
            .performTextInput(testEmail)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.password))
            .performTextInput(testPassword)

        composeTestRule.onLoginButton().assertIsEnabled()
    }

    @Test
    fun testLoginFailsNoEmailProvided() {
        val testPassword = "randomStrongPassword"

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.password))
            .performTextInput(testPassword)

        composeTestRule.onLoginButton().assertIsNotEnabled()
    }

    @Test
    fun testLoginFailsNoPasswordProvided() {
        val testEmail = "random.email@example.com"

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.username))
            .performTextInput(testEmail)

        composeTestRule.onLoginButton().assertIsNotEnabled()
    }

    private fun AndroidComposeTestRule<*, *>.onLoginButton() = onNode(hasText(activity.getString(R.string.log_in)) and hasClickAction())
}
