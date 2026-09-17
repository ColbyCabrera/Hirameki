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
package com.ichi2.ui

import android.app.Activity
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedCallback
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.FilteredDeckOptions
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.DeckId
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * how [AppCompatPreferenceActivity] (filtered deck options) closes on back: a changed option has to rebuild the
 * filtered deck, an unchanged screen is left to the system and its predictive back animation.
 *
 * api 33+ never sends KEYCODE_BACK once enableOnBackInvokedCallback is on, so back arrives through the window's
 * OnBackInvokedDispatcher there; api 31 and 32 have no such dispatcher and send the key to onKeyDown
 */
@RunWith(AndroidJUnit4::class)
class AppCompatPreferenceActivityBackTest : RobolectricTest() {
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `api 33+ - back is the system's until an option changes, then it rebuilds the deck`() {
        // what android:enableOnBackInvokedCallback does on a device, set here so the test does not depend on
        // how robolectric reads the manifest
        ShadowApplication.setEnableOnBackInvokedCallback(true)
        val did = emptiedFilteredDeck()
        val options = openOptions(did)

        assertThat(
            "nothing changed: back is the platform's own callback, which plays the predictive back animation",
            options.topBackCallback(),
            sameInstance(options.platformBackCallback()),
        )

        options.changeLimit("50")
        val closeWithResult = options.topBackCallback()
        assertThat("a changed option takes over back", closeWithResult, not(sameInstance(options.platformBackCallback())))
        options.changeLimit("60")
        assertThat("a second change registers nothing new", options.topBackCallback(), sameInstance(closeWithResult))

        closeWithResult!!.onBackInvoked()
        advanceRobolectricLooper()
        assertThat("back closes the options", options.isFinishing, equalTo(true))
        assertThat("and rebuilds the filtered deck", col.decks.cardCount(did), equalTo(1))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun `api 31 and 32 - the back key rebuilds the deck after a change`() {
        val did = emptiedFilteredDeck()
        val options = openOptions(did)

        // api 32 has no OnBackInvokedDispatcher: a change must not try to register a callback
        options.changeLimit("50")
        options.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        advanceRobolectricLooper()

        assertThat("back closes the options", options.isFinishing, equalTo(true))
        assertThat("and rebuilds the filtered deck", col.decks.cardCount(did), equalTo(1))
    }

    /** a filtered deck that holds a card only once it is rebuilt */
    private fun emptiedFilteredDeck(): DeckId {
        addBasicNote()
        val did = addDynamicDeck("Filtered", search = "")
        col.sched.emptyFilteredDeck(did)
        assertThat("emptied", col.decks.cardCount(did), equalTo(0))
        return did
    }

    private fun openOptions(did: DeckId): FilteredDeckOptions {
        val controller =
            Robolectric
                .buildActivity(FilteredDeckOptions::class.java, FilteredDeckOptions.getIntent(targetContext, did))
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()
        return controller.get()
    }

    /** the activity's preferences are the filtered deck: committing one saves the deck and notifies the activity */
    private fun FilteredDeckOptions.changeLimit(limit: String) {
        getSharedPreferences("", 0).edit(commit = true) { putString("limit", limit) }
        advanceRobolectricLooper()
    }

    // the dispatcher and the platform's default callback are hidden api, so they are read by reflection

    /** the callback a back gesture or back key reaches on api 33+: the top one on the window's dispatcher */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun Activity.topBackCallback(): OnBackInvokedCallback? {
        val viewRoot = View::class.java.getMethod("getViewRootImpl").invoke(window.decorView)
        assertThat("the window is attached", viewRoot, notNullValue())
        val dispatcher = viewRoot.javaClass.getMethod("getOnBackInvokedDispatcher").invoke(viewRoot)
        return dispatcher.javaClass.getMethod("getTopCallback").invoke(dispatcher) as OnBackInvokedCallback?
    }

    /** the callback [Activity] registers for itself, which finishes it with the system's back animation */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun Activity.platformBackCallback(): OnBackInvokedCallback {
        val callback =
            Activity::class.java
                .getDeclaredField("mDefaultBackCallback")
                .apply { isAccessible = true }
                .get(this) as OnBackInvokedCallback?
        assertThat("the platform registered its own back callback", callback, notNullValue())
        return callback!!
    }
}
