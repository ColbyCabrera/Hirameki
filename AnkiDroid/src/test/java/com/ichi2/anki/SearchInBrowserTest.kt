// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleDestroyedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.searchNode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SearchInBrowserTest : RobolectricTest() {
    private lateinit var controller: ActivityController<FragmentActivity>
    private val searchInput = searchNode { deck = "Default" }.toByteArray()

    @Before
    fun setUpActivity() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup().also(::saveControllerForCleanup)
    }

    @Test
    fun `search waits if the activity stops during collection access`() =
        runTest {
            val shadow = shadowOf(controller.get())
            controller.pause().stop()
            val request = async(start = CoroutineStart.UNDISPATCHED) { controller.get().searchInBrowser(searchInput) }

            runCurrent()
            assertNull(shadow.nextStartedActivity)
            assertFalse(request.isCompleted)

            controller.restart().start().resume()
            assertContentEquals(searchInput, request.await())
            val intent = assertNotNull(shadow.nextStartedActivity)
            assertEquals(CardBrowser::class.java.name, intent.component?.className)
            assertEquals("deck:Default", intent.getStringExtra("search_query"))
            assertTrue(intent.getBooleanExtra("all_decks", false))

            controller.pause().stop()
            controller.restart().start().resume()
            runCurrent()
            assertNull(shadow.nextStartedActivity)
        }

    @Test
    fun `destroying the activity cancels a waiting search`() =
        runTest {
            controller.pause().stop()
            val request = async(start = CoroutineStart.UNDISPATCHED) { controller.get().searchInBrowser(searchInput) }

            runCurrent()
            assertFalse(request.isCompleted)

            controller.destroy()
            assertFailsWith<LifecycleDestroyedException> { request.await() }
            assertNull(shadowOf(controller.get()).nextStartedActivity)
        }
}
