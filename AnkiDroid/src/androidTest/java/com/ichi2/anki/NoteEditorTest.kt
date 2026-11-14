// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.testutil.GrantStoragePermission
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule

abstract class NoteEditorTest protected constructor() {
    /*
     * Rules mean that we get a failure on API 25.
     * Even if we ignore the tests, the rules cause a failure.
     * We can't ignore the test in @BeforeClass ("Test run failed to complete. Expected 150 tests, received 149")
     * and @Before executes after the rule.
     * So, disable the rules in the constructor, and ignore in before.
     */
    private val isInvalid = invalidSdksImpl.contains(Build.VERSION.SDK_INT)

    @get:Rule
    var runtimePermissionRule: TestRule? =
        GrantStoragePermission.instance
            .takeUnless { isInvalid }

    @get:Rule
    var activityRule: ActivityScenarioRule<NoteEditorActivity>? =
        ActivityScenarioRule<NoteEditorActivity>(
            noteEditorIntent,
        ).takeUnless { isInvalid }

    private val noteEditorIntent: Intent
        get() {
            return NoteEditorLauncher.AddNote().toIntent(targetContext)
        }

    @Before
    fun before() {
        for (invalid in invalidSdksImpl) {
            Assume.assumeThat(
                "Test fails on API $invalid",
                Build.VERSION.SDK_INT,
                not(
                    equalTo(invalid),
                ),
            )
        }
    }

    private val invalidSdksImpl: List<Int>
        /*
         java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")
         at androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:301)
         */
        get() = listOf(Build.VERSION_CODES.N_MR1) + invalidSdks

    protected open val invalidSdks: List<Int>
        get() = ArrayList()
    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
}
