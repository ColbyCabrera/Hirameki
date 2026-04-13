package com.ichi2.anki.multimedia.audio

object WaveformUtils {
    private const val MAX_RAW_AMPLITUDE = 32767f
    
    /**
     * Normalizes a raw amplitude value from the audio recorder (0-32767) to a 0.0-1.0 range.
     */
    fun normalize(rawAmplitude: Int): Float {
        return (rawAmplitude / MAX_RAW_AMPLITUDE).coerceIn(0f, 1f)
    }

    /**
     * Legacy normalization for the Android View, maintaining original scaling/sensitivity.
     * Formerly: (amp.toInt() / 7).coerceAtMost(300).coerceAtLeast(6)
     */
    fun legacyNormalize(rawAmplitude: Float): Float {
        return (rawAmplitude / 7f).coerceIn(6f, 300f)
    }
}
