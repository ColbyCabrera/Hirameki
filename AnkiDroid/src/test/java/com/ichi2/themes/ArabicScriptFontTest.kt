/*
 * Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.themes

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import android.util.TypedValue
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.AppTypography
import com.ichi2.anki.ui.compose.theme.ArabicScriptTypography
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.Robolectric
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

// robolectric, not a plain unit test: the script comes from android.icu likely subtags, and the switch from Prefs
@RunWith(AndroidJUnit4::class)
class ArabicScriptFontTest : RobolectricTest() {
    @Test
    fun `arabic-script ui languages get vazirmatn`() {
        for (tag in listOf("ar", "fa", "ur-PK", "ckb", "ug")) {
            assertThat("ui language '$tag'", ArabicScriptFont.appliesToUi(configurationFor(tag)), equalTo(true))
        }
    }

    @Test
    fun `other ui languages keep the default font`() {
        // ku is kurmanji to cldr (ku_Latn_TR), which is latin script; the app's language picker labels it "Kurdî" too
        for (tag in listOf("en", "he", "hi", "ku", "zh-CN")) {
            assertThat("ui language '$tag'", ArabicScriptFont.appliesToUi(configurationFor(tag)), equalTo(false))
        }
    }

    @Test
    fun `the switch turns it off`() {
        Prefs.putBoolean(R.string.arabic_script_font_key, false)

        assertThat(ArabicScriptFont.appliesToUi(configurationFor("fa")), equalTo(false))
    }

    @Test
    @Config(qualifiers = "fa")
    fun `an arabic-script ui is drawn in vazirmatn, in views and in compose`() {
        launchActivity().use { scenario ->
            val activity = scenario.currentActivity()

            assertThat("views: the theme overlay is applied", activity.uiFontFamily(), equalTo(R.font.vazirmatn_family))
            assertThat("compose: the typography", activity.composeTypography(), sameInstance(ArabicScriptTypography))
        }
    }

    @Test
    @Config(qualifiers = "fa")
    fun `with the switch off an arabic-script ui keeps the app font`() {
        Prefs.putBoolean(R.string.arabic_script_font_key, false)

        launchActivity().use { scenario ->
            val activity = scenario.currentActivity()

            assertThat("views", activity.uiFontFamily(), not(equalTo(R.font.vazirmatn_family)))
            assertThat("compose", activity.composeTypography(), sameInstance(AppTypography))
        }
    }

    @Test
    @Config(qualifiers = "fa")
    fun `an activity stopped behind settings recreates only when the switch changed`() {
        // the deck picker and the card browser open settings with a plain startActivity, so this is their path back
        launchActivity().use { scenario ->
            val created = scenario.currentActivity()

            scenario.stopAndStartAgain()
            assertThat("unchanged switch keeps the activity", scenario.currentActivity(), sameInstance(created))

            scenario.stopAndStartAgain { Prefs.putBoolean(R.string.arabic_script_font_key, false) }
            assertThat("changed switch recreates the activity", scenario.currentActivity(), not(sameInstance(created)))
        }
    }

    private fun launchActivity(): ActivityScenario<EmptyAnkiActivity> {
        Robolectric.registerTestActivity<EmptyAnkiActivity>()
        return ActivityScenario.launch(Intent.makeMainActivity(ComponentName(targetContext, EmptyAnkiActivity::class.java)))
    }

    /** the font resource the activity's theme gives views ([ArabicScriptFont]'s overlay swaps uiFontFamily) */
    private fun Activity.uiFontFamily(): Int {
        val value = TypedValue()
        check(theme.resolveAttribute(R.attr.uiFontFamily, value, true)) { "the theme defines no uiFontFamily" }
        return value.resourceId
    }

    /** the typography [AnkiDroidTheme] hands compose content in this activity */
    private fun Activity.composeTypography(): Typography {
        var typography: Typography? = null
        (this as EmptyAnkiActivity).setContent { AnkiDroidTheme { typography = MaterialTheme.typography } }
        shadowOf(Looper.getMainLooper()).idle()
        return checkNotNull(typography) { "the content was not composed" }
    }

    private fun ActivityScenario<EmptyAnkiActivity>.currentActivity(): Activity {
        lateinit var activity: Activity
        onActivity { activity = it }
        return activity
    }

    private fun ActivityScenario<EmptyAnkiActivity>.stopAndStartAgain(whileStopped: () -> Unit = {}) {
        moveToState(Lifecycle.State.CREATED)
        whileStopped()
        moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun configurationFor(tag: String) = Configuration().apply { setLocale(Locale.forLanguageTag(tag)) }
}
