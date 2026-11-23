// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.graphics.drawable.InsetDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.preference.ListPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.shape.MaterialShapeDrawable
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.preferences.MaterialListPreferenceDialogFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class MaterialPreferenceDialogsTest : RobolectricTest() {
    @Test
    fun `stock ListPreference opens a Material dialog, including after recreation`() {
        val scenario = ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext))
        scenario.onActivity { activity ->
            val reviewingSettings = ReviewingSettingsFragment()
            activity.supportFragmentManager.commitNow { add(R.id.settings_container, reviewingSettings) }

            reviewingSettings.requirePreference<ListPreference>(R.string.apply_hirameki_css_preference).performClick()

            assertShowsMaterialListDialog(activity.supportFragmentManager)
        }

        scenario.recreate()

        scenario.onActivity { activity -> assertShowsMaterialListDialog(activity.supportFragmentManager) }
    }

    private fun assertShowsMaterialListDialog(fragmentManager: FragmentManager) {
        fragmentManager.executePendingTransactions()
        val dialogFragment = fragmentManager.findFragmentByTag(SettingsFragment.PREFERENCE_DIALOG_TAG) as DialogFragment?
        assertThat(dialogFragment, instanceOf(MaterialListPreferenceDialogFragment::class.java))

        val windowBackground = assertNotNull(dialogFragment!!.requireDialog().window).decorView.background
        assertThat((windowBackground as InsetDrawable).drawable, instanceOf(MaterialShapeDrawable::class.java))
    }
}
