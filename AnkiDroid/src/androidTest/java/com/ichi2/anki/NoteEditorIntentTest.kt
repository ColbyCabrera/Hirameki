// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity.Companion.intentLaunchedWithImage
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.utils.AssetHelper.TEXT_PLAIN
import junit.framework.TestCase.assertFalse
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorIntentTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule: TestRule? = GrantStoragePermission.instance

    // No activityRuleIntent rule here to avoid launching the activity before the collection is initialized.

    @Test
    @Flaky(OS.ALL, "Issue 15707 - java.lang.ArrayIndexOutOfBoundsException: length=0; index=0")
    fun launchActivityWithIntent() {
        col
        androidx.test.core.app.ActivityScenario.launch<NoteEditorActivity>(noteEditorTextIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            var currentFieldStrings: String? = null
            scenario.onActivity { activity ->
                currentFieldStrings = activity.currentFieldStrings[0]
            }
            MatcherAssert.assertThat(currentFieldStrings!!, Matchers.equalTo("sample text"))
        }
    }

    @Test
    fun intentLaunchedWithNonImageIntent() {
        val intent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = TEXT_PLAIN
            }
        assertFalse(intentLaunchedWithImage(intent))
    }

    private val noteEditorTextIntent: Intent
        get() {
            val bundle = Bundle().apply { putString(Intent.EXTRA_TEXT, "sample text") }
            return NoteEditorLauncher.PassArguments(bundle).toIntent(testContext, Intent.ACTION_SEND)
        }
}
