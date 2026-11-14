// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

package com.ichi2.anki.multimediacard

import android.media.MediaRecorder
import org.junit.Before
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class AudioRecorderTest {
    private lateinit var audioRecorder: AudioRecorder

    @Mock(name = "mRecorder")
    private val mockedMediaRecorder: MediaRecorder? = null

    @InjectMocks
    private lateinit var injectedRecorder: AudioRecorder

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        audioRecorder = AudioRecorder()
    }

    // verifies that stopRecording() and release() calls the proper methods in mRecorder
    @Test
    fun testStopAndRelease() {
        injectedRecorder.stopRecording()
        injectedRecorder.release()
        verify(mockedMediaRecorder, times(1))?.stop()
        verify(mockedMediaRecorder, times(1))?.release()
    }
}
