// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.multimediacard

import android.content.Context
import android.media.MediaRecorder
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.File
import java.io.IOException

class AudioRecorder {
    private lateinit var recorder: MediaRecorder
    private var onRecordingInitialized: Runnable? = null
    private var previousNonZeroAmplitude = 0

    private fun initMediaRecorder(
        context: Context,
        audioPath: File,
    ): MediaRecorder {
        val mr = CompatHelper.compat.getMediaRecorder(context)
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        onRecordingInitialized()
        mr.setOutputFile(audioPath.absolutePath) // audioPath could change
        return mr
    }

    private fun onRecordingInitialized() {
        onRecordingInitialized?.run()
    }

    @Throws(IOException::class)
    fun startRecording(
        context: Context,
        audioPath: String,
    ) {
        startRecording(context, File(audioPath))
    }

    @Throws(IOException::class)
    fun startRecording(
        context: Context,
        audioPath: File,
    ) {
        var highSampling = false
        try {
            // try high quality AAC @ 44.1kHz / 192kbps first
            // can throw IllegalArgumentException if codec isn't supported
            recorder = initMediaRecorder(context, audioPath)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(2)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(192000)
            // this can also throw IOException if output path is invalid
            recorder.prepare()
            recorder.start()
            highSampling = true
        } catch (e: Exception) {
            Timber.w(e)
            // in all cases, fall back to low sampling
        }
        if (!highSampling) {
            // if we are here, either the codec didn't work or output file was invalid
            // fall back on default
            recorder = initMediaRecorder(context, audioPath)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.prepare()
            recorder.start()
        }
    }

    fun stopRecording() {
        if (this::recorder.isInitialized) {
            recorder.stop()
        }
    }

    fun setOnRecordingInitializedHandler(onRecordingInitialized: Runnable?) {
        this.onRecordingInitialized = onRecordingInitialized
    }

    fun release() {
        if (this::recorder.isInitialized) {
            recorder.release()
        }
    }

    fun maxAmplitude(): Int {
        val currentAmplitude =
            if (this::recorder.isInitialized) {
                recorder.maxAmplitude
            } else {
                0
            }
        return if (currentAmplitude == 0) {
            previousNonZeroAmplitude
        } else {
            previousNonZeroAmplitude = currentAmplitude
            currentAmplitude
        }
    }

    fun pause() {
        if (!this::recorder.isInitialized) return
        recorder.pause()
    }

    fun resume() {
        if (!this::recorder.isInitialized) return
        recorder.resume()
    }
}
