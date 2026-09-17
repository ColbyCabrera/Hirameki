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
package com.ichi2.utils

import com.ichi2.anki.BuildConfig
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionUtilsTest {
    @Test
    fun `the build type digit decides whether a version code is a release`() {
        assertTrue(VersionUtils.isReleaseVersionCode(22300300), "2.23.00, build type 3")
        assertFalse(VersionUtils.isReleaseVersionCode(22300235), "2.23.00, build type 2")
        assertFalse(VersionUtils.isReleaseVersionCode(22300135), "2.23.00, build type 1")
        // the per-abi apks put the abi digit in front (AnkiDroid/build.gradle.kts), which must not change the answer
        assertTrue(VersionUtils.isReleaseVersionCode(3_22300300), "arm64-v8a apk of a release")
        assertFalse(VersionUtils.isReleaseVersionCode(3_22300135), "arm64-v8a apk of a non-release")
    }

    @Test
    fun `this build's version code is not read as an ankidroid release`() {
        // a release code would make DeckPicker open ankidroid's changelog after every update of hirameki
        assertFalse(
            VersionUtils.isReleaseVersionCode(BuildConfig.VERSION_CODE.toLong()),
            "baseVersionCode ${BuildConfig.VERSION_CODE} has a 3 as its third digit from the end; keep it below 3",
        )
    }
}
