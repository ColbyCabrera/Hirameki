/*
 *  Copyright (c) 2023 Tomasz Garbus <tomasz.garbus1@gmail.com>
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

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.Prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LoginActivityTest : RobolectricTest() {

    @Before
    override fun setUp() {
        super.setUp()
        // Ensure the activity doesn't finish itself due to being a "test client" (monkey/firebase)
        android.provider.Settings.System.putString(
            targetContext.contentResolver, "firebase.test.lab", "false"
        )
    }

    @Test
    fun activityIsClosedIfStartedWhenLoggedIn() {
        // Effectively mocks isLoggedIn() to return true.
        Prefs.hkey = "anything not empty"

        val controller = Robolectric.buildActivity(LoginActivity::class.java).create()
        try {
            val activity = controller.get()

            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).resultCode)
            assertEquals(true, activity.isFinishing)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun activityIsNotFinishedOnStartupIfNotLoggedIn() {
        // Effectively mocks isLoggedIn() to return false.
        Prefs.hkey = ""

        val controller =
            Robolectric.buildActivity(LoginActivity::class.java).create().start().resume()
        try {
            val activity = controller.get()

            assertEquals(false, activity.isFinishing)

            activity.finish()
            // Verify that the activity does not return RESULT_OK (defaults to RESULT_CANCELED)
            assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).resultCode)
        } finally {
            controller.destroy()
        }
    }
}
