/*
 *  Copyright (c) 2024
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

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioLifecycleTest : InstrumentedTest() {

    private lateinit var device: UiDevice
    private lateinit var audioManager: AudioManager

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission)

    @Before
    fun setUpAudioTest() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = ApplicationProvider.getApplicationContext<Context>()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (!device.isScreenOn) {
            device.wakeUp()
            Thread.sleep(500)
            // Swipe up or press menu to unlock if needed
            device.pressMenu()
            Thread.sleep(500)
        }
    }

    @Test
    fun testAudioPausesWhenAppGoesToBackground() {
        setupCardWithAudio("test_audio_bg.mp3")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<Reviewer>(Reviewer.getIntent(context))

        // Trigger playback
        replayMedia()

        // Wait for audio to start
        checkAudioPlaying()

        // Send app to background
        device.pressHome()

        // Wait for onPause/onStop
        Thread.sleep(2000)

        // Assert audio is STOPPED
        assertFalse("Audio should pause in background", audioManager.isMusicActive)

        scenario.close()
    }

    @Test
    fun testAudioPausesWhenScreenTurnsOff() {
        setupCardWithAudio("test_audio_screen.mp3")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<Reviewer>(Reviewer.getIntent(context))

        replayMedia()

        checkAudioPlaying()

        // Turn off the screen
        device.sleep()
        Thread.sleep(2000)

        // Assert audio is STOPPED
        assertFalse("Audio should pause when screen is off", audioManager.isMusicActive)

        scenario.close()
    }

    private fun setupCardWithAudio(fileName: String) {
        // Use a real audio file from assets to ensure it can be played.
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val mediaDir = col.media.dir
        if (!mediaDir.exists()) mediaDir.mkdirs()
        val audioFile = File(mediaDir, fileName)

        testContext.assets.open("anki-hello-10s.mp4").use { input ->
            audioFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val note = addNoteUsingBasicNoteType("Front [sound:$fileName]", "Back")
        val card = note.firstCard(col)
        card.moveToReviewQueue()
    }

    private fun replayMedia() {
        // Press 'R' to replay media
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_R)
        Thread.sleep(500)
    }

    private fun checkAudioPlaying() {
        // Wait for audio to start (up to 5 seconds)
        var playing = false
        for (i in 1..10) {
            if (audioManager.isMusicActive) {
                playing = true
                break
            }
            Thread.sleep(500)
        }
        assertTrue("Audio should be playing initially", playing)
    }

    @After
    fun tearDownAudioTest() {
        if (!device.isScreenOn) {
            device.wakeUp()
        }
    }
}

