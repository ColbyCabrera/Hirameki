/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.XmlRes
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.preferences.HeaderFragment.Companion.getHeaderKeyForFragment
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleReminders
import com.ichi2.anki.ui.motion.PredictiveBack
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.themes.Themes
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class PreferencesFragment :
    Fragment(R.layout.preferences),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SearchPreferenceResultListener {
    /**
     * Whether the Settings view is split in two.
     * If so, the left side contains the list of all preference categories, and the right side contains the category currently opened.
     * Otherwise, the same view is used to show the list of categories first, and then one specific category.
     */
    private val settingsIsSplit get() = !resources.isWindowCompact()

    /**
     * whether the settings search is showing. [HeaderFragment.configureSearchBar] hands the activity to
     * `SearchConfiguration`, which adds a [SearchPreferenceFragment] under its own tag to the activity's
     * manager with `addToBackStack`, so the overlay is an entry on the back stack above this fragment.
     *
     * the overlay is matched by tag, not by back stack depth: `AnkiActivity.showDialogFragment` also puts
     * entries on that stack, and any of those would otherwise take settings' own predictive back away
     * with no symptom other than the animation stopping
     */
    private val searchIsShowing get() = parentFragmentManager.findFragmentByTag(SearchPreferenceFragment.TAG) != null

    private val searchBackStackListener = FragmentManager.OnBackStackChangedListener { updateBackOwnership() }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        parentFragmentManager.addOnBackStackChangedListener(searchBackStackListener)

        // Load initial subscreen if activity is being first created
        if (savedInstanceState == null) {
            loadInitialSubscreen()
        }

        setupBigScreenLayout()
    }

    override fun onStart() {
        super.onStart()
        // the initial claim, and the retry for anything the listener could not commit while the
        // activity was stopped: see the state-saved case in [updateBackOwnership]
        updateBackOwnership()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentFragmentManager.removeOnBackStackChangedListener(searchBackStackListener)
    }

    /**
     * gives back to whichever fragment manager owns what is actually on top.
     *
     * a fragment manager handles back only while its parent fragment is the primary navigation one, so
     * making this fragment primary hands the sub-screen back stack to [childFragmentManager]. it pops
     * with the seekable animator transition of [setScreenTransition], so the gesture previews the parent
     * screen, and an empty child back stack enables no callback at all, so the system closes settings
     * with the predictive back animation. the hand-written callback that popped the stack before could
     * do neither: being enabled replaced the system animation, and it never fed the gesture.
     *
     * while the search is showing it is drawn over this fragment and is what back must close, so this
     * fragment drops the role: the activity's manager then pops the search instead of the sub-screen
     * hidden behind it.
     */
    private fun updateBackOwnership() {
        val owner = if (searchIsShowing) null else this
        // committing unconditionally would re-enter through the back stack listener
        if (parentFragmentManager.primaryNavigationFragment === owner) return
        // a commit after the state was saved would throw. the manager counts as state-saved for as long
        // as the activity is stopped, so this only defers: [onStart] re-evaluates on the way back
        if (parentFragmentManager.isStateSaved) return
        parentFragmentManager.commit { setPrimaryNavigationFragment(owner) }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val className = pref.fragment ?: return false
        val fragmentClass = FragmentFactory.loadFragmentClass(requireActivity().classLoader, className)

        // #18963: Remove any subscreens after opening a new primary screen
        if (settingsIsSplit && caller is HeaderFragment) {
            childFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.settings_container, fragmentClass, null)
            setScreenTransition(this)
            if (!settingsIsSplit || caller !is HeaderFragment) {
                addToBackStack(null)
            }
        }
        return true
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        if (result.key == getString(R.string.pref_review_reminders_screen_key)) {
            Timber.i("Preferences:: edit review reminders button pressed")
            val intent = ScheduleReminders.getIntent(requireContext(), ReviewReminderScope.Global)
            startActivity(intent)
            return
        }

        val fragment = getFragmentFromXmlRes(result.resourceFile) ?: return

        parentFragmentManager.popBackStack() // clear the search fragment from the backstack
        childFragmentManager.commit {
            // as in onPreferenceStartFragment: without it the pop cannot follow a back gesture
            setReorderingAllowed(true)
            replace(R.id.settings_container, fragment, fragment.javaClass.name)
            setScreenTransition(this)
            addToBackStack(fragment.javaClass.name)
        }

        Timber.i("Highlighting key '%s' on %s", result.key, fragment)
        result.highlight(fragment as PreferenceFragmentCompat)
    }

    private fun setupBigScreenLayout() {
        if (!settingsIsSplit) return

        // Configure the toolbars
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    // Make the collapsing toolbar look like a normal toolbar
                    view.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)?.apply {
                        updateLayoutParams<AppBarLayout.LayoutParams> {
                            scrollFlags = 0
                            val resId = Themes.getResFromAttr(requireContext(), android.R.attr.actionBarSize)
                            height = resources.getDimensionPixelSize(resId)
                        }
                        isTitleEnabled = false
                        setContentScrimResource(android.R.color.transparent) // removes the collapsed scrim
                    }

                    // remove `Back` button from the toolbar of other fragments
                    if (fragment !is HeaderFragment) {
                        view.findViewById<MaterialToolbar>(R.id.toolbar)?.navigationIcon = null
                    }
                }
            },
            false,
        )

        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    savedInstanceState: Bundle?,
                ) {
                    if (fragment is HeaderFragment) return
                    val headerFragment = childFragmentManager.findFragmentById(R.id.lateral_nav_container)
                    val key = getHeaderKeyForFragment(fragment) ?: return
                    (headerFragment as? HeaderFragment)?.highlightPreference(key)
                }
            },
            false,
        )

        // Configure headers highlight
        childFragmentManager.executePendingTransactions() // wait for the headers page creation
        childFragmentManager.findFragmentById(R.id.settings_container)?.let { fragment ->
            val headerFragment = childFragmentManager.findFragmentById(R.id.lateral_nav_container)
            if (headerFragment !is HeaderFragment) return@let
            val key = getHeaderKeyForFragment(fragment) ?: return@let
            headerFragment.highlightPreference(key)
        }
    }

    private fun setScreenTransition(fragmentTransaction: FragmentTransaction) {
        if (!sharedPrefs().getBoolean("safeDisplay", false)) {
            // the app's one back motion, see [PredictiveBack]. the fade this replaced cross-faded two
            // containers that paint no background of their own, so halfway through a back gesture both
            // screens were see-through
            fragmentTransaction.setTransition(PredictiveBack.FRAGMENT_TRANSIT)
        }
    }

    /**
     * Starts the first settings fragment, which by default is [HeaderFragment].
     * The initial fragment may be overridden by putting the java class name
     * of the fragment on an intent extra with the key [INITIAL_FRAGMENT_EXTRA]
     */
    private fun loadInitialSubscreen() {
        val fragmentClassName = arguments?.getString(INITIAL_FRAGMENT_EXTRA)
        val initialFragment =
            if (fragmentClassName == null) {
                if (!settingsIsSplit) HeaderFragment() else GeneralSettingsFragment()
            } else {
                FragmentFactoryUtils.instantiate<Fragment>(requireActivity(), fragmentClassName)
            }
        childFragmentManager.commit {
            replace(R.id.settings_container, initialFragment, initialFragment::class.java.name)
        }
    }
}

/**
 * Host activity for [PreferencesFragment].
 *
 * Only necessary because [SearchConfiguration] demands an activity that implements
 * [SearchPreferenceResultListener].
 */
class PreferencesActivity :
    SingleFragmentActivity(),
    SearchPreferenceResultListener {

    override val applyInsetsPadding: Boolean = false

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (fragment is SearchPreferenceResultListener) {
            fragment.onSearchResultClicked(result)
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            initialFragment: KClass<out SettingsFragment>? = null,
        ): Intent {
            val arguments = Bundle().apply { putString(INITIAL_FRAGMENT_EXTRA, initialFragment?.jvmName) }
            return Intent(context, PreferencesActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, PreferencesFragment::class.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}

// Only enable AnkiDroid notifications unrelated to due reminders
const val PENDING_NOTIFICATIONS_ONLY = 1000000

const val INITIAL_FRAGMENT_EXTRA = "initial_fragment"

/**
 * @return the [SettingsFragment] which uses the given [screen] resource.
 * i.e. [SettingsFragment.preferenceResource] value is the same of [screen]
 */
fun getFragmentFromXmlRes(
    @XmlRes screen: Int,
): SettingsFragment? =
    when (screen) {
        R.xml.preferences_general -> GeneralSettingsFragment()
        R.xml.preferences_reviewing -> ReviewingSettingsFragment()
        R.xml.preferences_sync -> SyncSettingsFragment()
        R.xml.preferences_backup_limits -> BackupLimitsSettingsFragment()
        R.xml.preferences_custom_sync_server -> CustomSyncServerSettingsFragment()
        R.xml.preferences_notifications -> NotificationsSettingsFragment()
        R.xml.preferences_appearance -> AppearanceSettingsFragment()
        R.xml.preferences_controls -> ControlsSettingsFragment()
        R.xml.preferences_advanced -> AdvancedSettingsFragment()
        R.xml.preferences_accessibility -> AccessibilitySettingsFragment()
        R.xml.preferences_dev_options -> DevOptionsFragment()
        R.xml.preferences_custom_buttons -> CustomButtonsSettingsFragment()
        else -> null
    }
