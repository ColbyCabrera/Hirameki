// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>

package com.ichi2.anki.utils

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.KeyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class KeyUtilsTest {
    @Test
    fun testIsDigitWithValidDigitShouldReturnTrue() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_5)
        assertTrue(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testIsDigitWithUnValidDigitShouldReturnFalse() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertFalse(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testIsDigitWithNonLanguageKeyShouldReturnFalse() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertFalse(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithValidDigitShouldReturnThisDigit() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_7)
        assertEquals(7, KeyUtils.getDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithNonDigitShouldReturnNull() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertNull(KeyUtils.getDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithNonLanguageKeyShouldReturnNull() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertNull(KeyUtils.getDigit(keyEvent))
    }
}
