/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.CollectionOpenFailure
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.getStartupFailureType
import com.ichi2.anki.InitialActivityWithConflictTest.Companion.setupForDatabaseConflict
import com.ichi2.anki.InitialActivityWithConflictTest.Companion.setupForDefault
import com.ichi2.anki.backend.DatabaseCorruption
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Failures raised while opening the collection.
 */
@RunWith(AndroidJUnit4::class)
class InitialActivityDatabaseFailureTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        DatabaseCorruption.isDetected = false
        setupForDatabaseConflict()
    }

    @After
    override fun tearDown() {
        CollectionManager.emulatedOpenFailure = null
        DatabaseCorruption.isDetected = false
        super.tearDown()
        setupForDefault()
    }

    @Test
    fun `a full disk is reported as DiskFull`() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.DISK_FULL

        val failure = getStartupFailureType(targetContext)

        assertThat("a full disk should be reported", failure, equalTo(StartupFailure.DiskFull))
    }

    @Test
    fun `a corrupt collection is flagged as corruption`() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.DATABASE_CORRUPT

        val failure = getStartupFailureType(targetContext)

        assertThat("a corrupt collection is a DB error", failure, instanceOf(StartupFailure.DBError::class.java))
        assertThat("corruption should be detected", DatabaseCorruption.isDetected, equalTo(true))
    }
}
