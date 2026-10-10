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
package com.ichi2.anki.reviewer

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class VoicePlaybackViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `clearing the view model deletes the recording`() {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[VoicePlaybackViewModel::class.java]
        viewModel.startRecordingIfIdle(context)
        assertThat(viewModel.state.value, equalTo(VoicePlaybackViewModel.RecordingState.Recording))
        assertThat(recordings(), hasSize(1))

        // what the reviewer's exit does, whether or not it paused first (see VoicePlaybackViewModel.onCleared)
        store.clear()

        assertThat("the temp recording is deleted", recordings(), empty())
    }

    private fun recordings(): List<File> =
        context.cacheDir
            .listFiles { file -> file.name.startsWith("ankidroid_audiorec") }
            .orEmpty()
            .toList()
}
