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
package com.ichi2.anki

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.Consts
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class ReviewerBackTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `back is left to the system and still reports RESULT_DEFAULT`() {
        val reviewer = startReviewerWithACard()

        // any enabled callback would stop android 13+ from playing the predictive back animation
        assertThat("no callback intercepts back", reviewer.onBackPressedDispatcher.hasEnabledCallbacks(), equalTo(false))

        reviewer.onBackPressedDispatcher.onBackPressed()
        assertThat("back leaves the reviewer", reviewer.isFinishing, equalTo(true))
        assertThat(shadowOf(reviewer).resultCode, equalTo(AbstractFlashcardViewer.RESULT_DEFAULT))
    }

    @Test
    fun `press back twice intercepts only the first back`() {
        editPreferences { putBoolean(getResourceString(R.string.exit_via_double_tap_back_key), true) }
        val reviewer = startReviewerWithACard()

        reviewer.onBackPressedDispatcher.onBackPressed()
        assertThat("the first back stays", reviewer.isFinishing, equalTo(false))

        reviewer.onBackPressedDispatcher.onBackPressed()
        assertThat("the second back leaves", reviewer.isFinishing, equalTo(true))
        assertThat(shadowOf(reviewer).resultCode, equalTo(AbstractFlashcardViewer.RESULT_DEFAULT))
    }

    @Test
    fun `press back twice asks again once the notice has gone`() {
        editPreferences { putBoolean(getResourceString(R.string.exit_via_double_tap_back_key), true) }
        val reviewer = startReviewerWithACard()

        reviewer.onBackPressedDispatcher.onBackPressed()
        assertThat("the first back lets the next one through", reviewer.onBackPressedDispatcher.hasEnabledCallbacks(), equalTo(false))

        // the second back must follow within the notice: the passing test above could not tell a callback that
        // stands down for the notice from one that never stands up again, which would make every later back leave
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Consts.SHORT_TOAST_DURATION))
        assertThat("armed again once the notice has gone", reviewer.onBackPressedDispatcher.hasEnabledCallbacks(), equalTo(true))

        reviewer.onBackPressedDispatcher.onBackPressed()
        assertThat("a late second back is a first back again", reviewer.isFinishing, equalTo(false))
        reviewer.onBackPressedDispatcher.onBackPressed()
        assertThat("and the back after it leaves", reviewer.isFinishing, equalTo(true))
    }

    /** an empty collection closes the reviewer by itself (RESULT_NO_MORE_CARDS), before back is pressed */
    private fun startReviewerWithACard(): Reviewer {
        addBasicNote("Hello", "World")
        val reviewer = startRegularActivity<Reviewer>()
        advanceRobolectricLooper()
        assertThat("the reviewer is open before back", reviewer.isFinishing, equalTo(false))
        return reviewer
    }
}
