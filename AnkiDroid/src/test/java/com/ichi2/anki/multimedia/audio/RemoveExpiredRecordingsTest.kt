// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class RemoveExpiredRecordingsTest : RobolectricTest() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = 1_800_000_000_000L

    @Test
    fun `recordings are stored outside the cache`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recording = assertNotNull(AudioRecordingController.generateTempAudioFile(context))
        try {
            assertEquals(
                File(context.noBackupFilesDir, AudioRecordingController.RECORDINGS_DIRECTORY),
                recording.parentFile,
            )
            assertTrue(recording.exists())
        } finally {
            recording.delete()
        }
    }

    @Test
    fun `cleanup removes only recordings older than seven days`() {
        val expired = createFile("ankidroid_audiorec1.3gp", 7.days + 1.seconds)
        val atCutoff = createFile("ankidroid_audiorec2.3gp", 7.days)
        val recent = createFile("ankidroid_audiorec3.3gp", 1.days)

        AudioRecordingController.removeExpiredRecordings(temporaryFolder.root, now)

        assertFalse(expired.exists(), "expired recording")
        assertTrue(atCutoff.exists(), "recording at the cutoff")
        assertTrue(recent.exists(), "recent recording")
    }

    @Test
    fun `cleanup keeps other files and folders`() {
        val unrelated = createFile("notes.txt", 8.days)
        val folder = temporaryFolder.newFolder("ankidroid_audiorec_folder.3gp")
        assertTrue(folder.setLastModified(now - 8.days.inWholeMilliseconds))

        AudioRecordingController.removeExpiredRecordings(temporaryFolder.root, now)

        assertTrue(unrelated.exists(), "unrelated file")
        assertTrue(folder.exists(), "folder")
    }

    @Test
    fun `cleanup ignores a missing recordings folder`() {
        AudioRecordingController.removeExpiredRecordings(File(temporaryFolder.root, "missing"), now)
    }

    private fun createFile(
        name: String,
        age: Duration,
    ): File =
        temporaryFolder.newFile(name).apply {
            assertTrue(setLastModified(now - age.inWholeMilliseconds))
        }
}
