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
package com.ichi2.anki.preferences

import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * which back handler each settings state hands the gesture to.
 *
 * a fragment manager only handles back while its parent is the primary navigation fragment, and that is
 * what makes settings animate: the manager seeks its own transition with the gesture. these tests pin
 * that ownership, because an app callback taking over is invisible except that the animation stops.
 */
@RunWith(AndroidJUnit4::class)
// compact: sub-screens replace the header list and go on the back stack, instead of sitting beside it
@Config(qualifiers = "w400dp-h800dp")
class PreferencesBackTest : RobolectricTest() {
    @Test
    fun `the settings list leaves back to the system`() {
        withPreferences { activity, preferences ->
            assertThat(
                "no callback intercepts back, so the system closes settings with its own animation",
                activity.onBackPressedDispatcher.hasEnabledCallbacks(),
                equalTo(false),
            )
            assertThat(
                "the sub-screen back stack belongs to the child fragment manager",
                activity.supportFragmentManager.primaryNavigationFragment,
                sameInstance<Fragment>(preferences),
            )
        }
    }

    @Test
    fun `back pops a settings sub-screen, then leaves settings`() {
        withPreferences { activity, preferences ->
            openSubScreen(preferences)

            assertThat(
                "the sub-screen is on the child back stack",
                preferences.childFragmentManager.backStackEntryCount,
                equalTo(1),
            )
            assertThat(
                "the child fragment manager intercepts back",
                activity.onBackPressedDispatcher.hasEnabledCallbacks(),
                equalTo(true),
            )

            activity.onBackPressedDispatcher.onBackPressed()
            settle(activity, preferences)

            assertThat(
                "back popped the sub-screen",
                preferences.childFragmentManager.backStackEntryCount,
                equalTo(0),
            )
            assertThat("back stayed in settings", activity.isFinishing, equalTo(false))
            assertThat(
                "with the sub-screen gone, back is left to the system again",
                activity.onBackPressedDispatcher.hasEnabledCallbacks(),
                equalTo(false),
            )
        }
    }

    @Test
    fun `the search closes before the sub-screen behind it`() {
        withPreferences { activity, preferences ->
            openSubScreen(preferences)
            showSearch(activity, preferences)

            assertThat(
                "the search drawn on top owns back, so the child manager gives up the role",
                activity.supportFragmentManager.primaryNavigationFragment,
                nullValue(),
            )

            activity.onBackPressedDispatcher.onBackPressed()
            settle(activity, preferences)

            assertThat(
                "back closed the search",
                activity.supportFragmentManager.backStackEntryCount,
                equalTo(0),
            )
            assertThat(
                "the sub-screen hidden behind the search was left alone",
                preferences.childFragmentManager.backStackEntryCount,
                equalTo(1),
            )
            assertThat(
                "the child manager owns back again",
                activity.supportFragmentManager.primaryNavigationFragment,
                sameInstance<Fragment>(preferences),
            )
        }
    }

    @Test
    fun `an unrelated entry on the activity back stack leaves settings' back alone`() {
        withPreferences { activity, preferences ->
            openSubScreen(preferences)
            // what AnkiActivity.showDialogFragment puts on that stack. only the search overlay is drawn
            // over the sub-screen, so only the search may take its back
            activity.supportFragmentManager.commit {
                add(Fragment(), "dialog")
                addToBackStack("dialog")
            }
            settle(activity, preferences)

            assertThat(
                "the child fragment manager kept the role",
                activity.supportFragmentManager.primaryNavigationFragment,
                sameInstance<Fragment>(preferences),
            )
        }
    }

    @Test
    fun `back ownership catches up after the activity was stopped`() {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val preferences = activity.fragment as PreferencesFragment
                settle(activity, preferences)
                openSubScreen(preferences)
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                activity.supportFragmentManager.commit(allowStateLoss = true) {
                    add(Fragment(), SearchPreferenceFragment.TAG)
                    addToBackStack(SearchPreferenceFragment.TAG)
                }
                activity.supportFragmentManager.executePendingTransactions()
                assertThat(
                    "a stopped manager is state saved, so the listener cannot hand back over here",
                    activity.supportFragmentManager.primaryNavigationFragment,
                    sameInstance<Fragment>(activity.fragment),
                )
            }

            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                settle(activity, activity.fragment as PreferencesFragment)
                assertThat(
                    "onStart re-evaluates it, so the search on top owns back again",
                    activity.supportFragmentManager.primaryNavigationFragment,
                    nullValue(),
                )
            }
        }
    }

    private fun withPreferences(block: (PreferencesActivity, PreferencesFragment) -> Unit) {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val preferences = activity.fragment as PreferencesFragment
                settle(activity, preferences)
                block(activity, preferences)
            }
        }
    }

    /** opens a settings sub-screen the way a tap on a header row does */
    private fun openSubScreen(preferences: PreferencesFragment) {
        val headers = preferences.childFragmentManager.findFragmentById(R.id.settings_container) as PreferenceFragmentCompat
        val generalSettings = targetContext.getString(R.string.pref_general_screen_key)
        preferences.onPreferenceStartFragment(headers, headers.findPreference<Preference>(generalSettings)!!)
        settle(preferences.requireActivity() as PreferencesActivity, preferences)
    }

    /**
     * stands in for the settings search overlay: `SearchConfiguration.showSearchFragment()` adds a
     * [SearchPreferenceFragment] under its own tag to the activity's manager with `addToBackStack`,
     * and only the tag and that entry matter here
     */
    private fun showSearch(
        activity: PreferencesActivity,
        preferences: PreferencesFragment,
    ) {
        activity.supportFragmentManager.commit {
            add(Fragment(), SearchPreferenceFragment.TAG)
            addToBackStack(SearchPreferenceFragment.TAG)
        }
        settle(activity, preferences)
    }

    /**
     * runs every queued transaction. back ownership is handed over by a transaction that a back stack
     * listener queues, so one pass is not enough: it is queued while the first pass runs
     */
    private fun settle(
        activity: PreferencesActivity,
        preferences: PreferencesFragment,
    ) {
        repeat(2) {
            activity.supportFragmentManager.executePendingTransactions()
            preferences.childFragmentManager.executePendingTransactions()
        }
        advanceRobolectricLooper()
    }
}
