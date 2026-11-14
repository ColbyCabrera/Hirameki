// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ichi2.anki.reviewer.ReviewerEvent
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
        setupCardWithAudio("test_audio_bg.mp4")

        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<Reviewer>(Reviewer.getIntent(context)).use {
            // Trigger playback
            replayMedia(it)

            // Wait for audio to start
            checkAudioPlaying()

            // Send app to background
            device.pressHome()

            // Wait for onPause/onStop
            checkAudioStopped()

            // Assert audio is STOPPED
            assertFalse("Audio should pause in background", audioManager.isMusicActive)
        }
    }

    @Test
    fun testAudioPausesWhenScreenTurnsOff() {
        setupCardWithAudio("test_audio_screen.mp4")

        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<Reviewer>(Reviewer.getIntent(context)).use {
            replayMedia(it)

            checkAudioPlaying()

            // Turn off the screen
            device.sleep()
            checkAudioStopped()

            // Assert audio is STOPPED
            assertFalse("Audio should pause when screen is off", audioManager.isMusicActive)
        }
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

    private fun replayMedia(scenario: ActivityScenario<Reviewer>) {
        scenario.onActivity { reviewer ->
            reviewer.viewModel.onEvent(ReviewerEvent.ReplayMedia)
        }
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

    private fun checkAudioStopped(timeoutMs: Long = 5000) {
        // Wait for audio to stop (up to timeoutMs)
        var stopped = false
        val iterations = (timeoutMs / 500).toInt().coerceAtLeast(1)
        for (i in 1..iterations) {
            if (!audioManager.isMusicActive) {
                stopped = true
                break
            }
            Thread.sleep(500)
        }
        assertTrue("Audio should have stopped", stopped)
    }

    @After
    fun tearDownAudioTest() {
        if (!device.isScreenOn) {
            device.wakeUp()
        }
    }
}
