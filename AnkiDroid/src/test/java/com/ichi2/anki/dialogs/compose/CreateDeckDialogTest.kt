/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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
