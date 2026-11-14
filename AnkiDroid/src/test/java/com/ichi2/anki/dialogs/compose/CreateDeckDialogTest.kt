// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs.compose

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/**
 * Unit tests for the Compose [CreateDeckDialog] helper functions.
 *
 * Note: Compose UI tests require `ui-test-junit4` dependency which is not currently
 * configured in the project. These tests focus on the non-UI logic.
 */
class CreateDeckDialogTest {
    @Test
    fun `number larger than nine detection - empty string`() {
        assertThat("".containsNumberLargerThanNine(), equalTo(false))
    }

    @Test
    fun `number larger than nine detection - text only`() {
        assertThat("deck name".containsNumberLargerThanNine(), equalTo(false))
    }

    @Test
    fun `number larger than nine detection - less than ten`() {
        assertThat("9. - Chemicals".containsNumberLargerThanNine(), equalTo(false))
    }

    @Test
    fun `number larger than nine detection - ten or greater`() {
        assertThat("10. - Chemicals".containsNumberLargerThanNine(), equalTo(true))
        assertThat("99. - Chemicals".containsNumberLargerThanNine(), equalTo(true))
    }

    @Test
    fun `number larger than nine detection - zero prefix`() {
        assertThat("09. - Chemicals".containsNumberLargerThanNine(), equalTo(false))
    }

    @Test
    fun `number larger than nine detection - time format excluded`() {
        assertThat("10:50:59".containsNumberLargerThanNine(), equalTo(false))
        assertThat("Filtered Deck 22:34".containsNumberLargerThanNine(), equalTo(false))
    }

    @Test
    fun `number larger than nine detection - suffix number`() {
        assertThat("Deck 34".containsNumberLargerThanNine(), equalTo(true))
    }
}
