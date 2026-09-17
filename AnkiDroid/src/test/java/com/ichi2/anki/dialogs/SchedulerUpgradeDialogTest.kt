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
package com.ichi2.anki.dialogs

import androidx.activity.ComponentDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class SchedulerUpgradeDialogTest : RobolectricTest() {
    private var cancelCount = 0

    @Test
    fun `back cancels the dialog`() {
        val dialog = showSchedulerUpgradeDialog()

        // api 33+ with enableOnBackInvokedCallback: back reaches the dialog's dispatcher, and no
        // KEYCODE_BACK is sent to a key listener
        dialog.onBackPressedDispatcher.onBackPressed()

        assertThat("back cancels", cancelCount, equalTo(1))
        assertThat("back dismisses", dialog.isShowing, equalTo(false))
    }

    @Test
    fun `back key cancels the dialog on api 31 and 32`() {
        val dialog = showSchedulerUpgradeDialog()

        // api 31/32: a back key ends in Dialog.onBackPressed(), which ComponentDialog routes to the
        // same dispatcher
        @Suppress("DEPRECATION")
        dialog.onBackPressed()

        assertThat("back cancels", cancelCount, equalTo(1))
        assertThat("back dismisses", dialog.isShowing, equalTo(false))
    }

    private fun showSchedulerUpgradeDialog(): ComponentDialog {
        val activity = startRegularActivity<DeckPicker>()
        SchedulerUpgradeDialog(activity, onUpgrade = {}, onCancel = { cancelCount++ }).showDialog()
        return ShadowDialog.getLatestDialog() as ComponentDialog
    }
}
