// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia.audio

import android.os.Handler
import android.os.Looper
import com.ichi2.anki.utils.postDelayed
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * AudioTimer class is a utility for managing timing operations when playing audio.
 * It includes a Handler for scheduling tasks, and a Runnable for incrementing the duration and
 * triggering a callback to a listener at regular intervals.
 * [OnTimerTickListener.onTimerTick] notifies components about the timer's progress.
 **/
class AudioTimer(
    listener: OnTimerTickListener,
    audioWaveListener: OnAudioTickListener,
) {
    private var audioTimeHandler = Handler(Looper.getMainLooper())
    private var audioTimeRunnable: Runnable

    // we use a different handler to audio waveform as 16L is too fast
    private var audioWaveHandler = Handler(Looper.getMainLooper())
    private var audioWaveRunnable: Runnable

    private var audioTimeDuration = 0.milliseconds
    private var audioTimeDelay = 16.milliseconds
    private var audioWaveDuration = 0L
    private var audioWaveDelay = 50L

    init {
        audioTimeRunnable =
            object : Runnable {
                override fun run() {
                    audioTimeDuration += audioTimeDelay
                    audioTimeHandler.postDelayed(this, audioTimeDelay)
                    listener.onTimerTick(audioTimeDuration)
                }
            }

        audioWaveRunnable =
            object : Runnable {
                override fun run() {
                    audioWaveDuration += audioWaveDelay
                    audioWaveHandler.postDelayed(this, audioWaveDelay)
                    audioWaveListener.onAudioTick()
                }
            }
    }

    fun start() {
        audioWaveHandler.postDelayed(audioWaveRunnable, audioWaveDelay)
        audioTimeHandler.postDelayed(audioTimeRunnable, audioTimeDelay)
    }

    fun pause() {
        audioWaveHandler.removeCallbacks(audioWaveRunnable)
        audioTimeHandler.removeCallbacks(audioTimeRunnable)
    }

    fun stop() {
        audioWaveHandler.removeCallbacks(audioWaveRunnable)
        audioTimeHandler.removeCallbacks(audioTimeRunnable)
        audioTimeDuration = 0.milliseconds
        audioWaveDuration = 0L
    }

    fun start(customDuration: Duration) {
        audioTimeHandler.removeCallbacks(audioTimeRunnable)
        audioTimeDuration = customDuration
        audioTimeHandler.postDelayed(audioTimeRunnable, audioTimeDelay)
    }

    interface OnTimerTickListener {
        fun onTimerTick(duration: Duration)
    }

    interface OnAudioTickListener {
        fun onAudioTick()
    }
}
