// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <48384865+criticalAY@users.noreply.github.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.TTSTag
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** Tests for [AndroidTtsPlayer]. */
@RunWith(AndroidJUnit4::class)
class AndroidTtsPlayerTest {
    @Test
    fun `tags with obsolete language codes play voices with current codes`() =
        runBlocking {
            val hebrew = AndroidTtsVoice(fakeVoice("hebrew", Locale.forLanguageTag("he-IL")), ENGINE_A)
            val indonesian = AndroidTtsVoice(fakeVoice("indonesian", Locale.forLanguageTag("id-ID")), ENGINE_A)
            val tts = fakeTts()
            val player = AndroidTtsPlayer(listOf(hebrew, indonesian)) { tts }
            player.init(this)

            try {
                for ((lang, voice) in listOf("iw_IL" to hebrew, "in_ID" to indonesian)) {
                    val tag =
                        TTSTag(
                            fieldText = "hello world",
                            lang = lang,
                            voices = listOf(voice.name),
                            speed = null,
                            otherArgs = emptyList(),
                        )
                    val result = withTimeout(PLAYBACK_TIMEOUT) { player.play(tag) }

                    assertThat(lang, result.success, equalTo(true))
                    verify(exactly = 1) { tts.voice = voice.voice }
                }
            } finally {
                player.close()
            }
        }

    /** A relaxed [TextToSpeech] mock whose `speak()` immediately reports completion. */
    private fun fakeTts(): TextToSpeech {
        val listenerSlot = slot<UtteranceProgressListener>()
        val utteranceSlot = slot<String>()
        return mockk(relaxed = true) {
            every { setOnUtteranceProgressListener(capture(listenerSlot)) } returns TextToSpeech.SUCCESS
            every { speak(any(), any(), any(), capture(utteranceSlot)) } answers {
                listenerSlot.captured.onDone(utteranceSlot.captured)
                TextToSpeech.SUCCESS
            }
        }
    }

    private fun fakeVoice(
        voiceName: String,
        voiceLocale: Locale,
        installed: Boolean = true,
    ): Voice =
        mockk(relaxed = true) {
            every { name } returns voiceName
            every { locale } returns voiceLocale
            every { features } returns if (installed) emptySet() else setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private val PLAYBACK_TIMEOUT = 5.seconds
    }
}
