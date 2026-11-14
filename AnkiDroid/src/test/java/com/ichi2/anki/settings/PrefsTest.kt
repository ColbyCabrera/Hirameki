// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.settings

import android.content.res.Resources
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PrefsTest {
    @Test
    fun `getters and setters use the same key`() {
        AnkiDroidApp.sharedPreferencesTestingOverride = SPMockBuilder().createSharedPreferences()
        var lastKey = 0
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers {
            val resId = invocation.args[0] as Int
            lastKey = resId
            resId.toString()
        }
        mockkObject(Prefs)
        every { Prefs.resources } returns mockResources

        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC || property !is KMutableProperty<*>) continue

            property.getter.call(Prefs)
            val getterKey = lastKey

            when (property.returnType.classifier) {
                Boolean::class -> property.setter.call(Prefs, false)
                String::class -> property.setter.call(Prefs, "foo")
                else -> continue
            }
            val setterKey = lastKey

            assertThat(
                "The getter and setter of '${property.name}' use the same key",
                getterKey,
                equalTo(setterKey),
            )
        }
        unmockkObject(Prefs)
        AnkiDroidApp.sharedPreferencesTestingOverride = null
    }

    @Test
    fun `applyHiramekiCss setting reads from shared preferences`() {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        AnkiDroidApp.sharedPreferencesTestingOverride = sharedPrefs
        try {
            val mockResources = mockk<Resources>()
            every { mockResources.getString(R.string.apply_hirameki_css_preference) } returns "applyHiramekiCss"
            mockkObject(Prefs)
            every { Prefs.resources } returns mockResources

            assertThat(Prefs.applyHiramekiCss, equalTo(Prefs.HIRAMEKI_CSS_ALL))

            sharedPrefs.edit(commit = true) {
                putString(
                    "applyHiramekiCss",
                    Prefs.HIRAMEKI_CSS_NO_FONT_SIZE,
                )
            }
            assertThat(Prefs.applyHiramekiCss, equalTo(Prefs.HIRAMEKI_CSS_NO_FONT_SIZE))

            sharedPrefs.edit(commit = true) {
                putString(
                    "applyHiramekiCss",
                    Prefs.HIRAMEKI_CSS_DISABLED,
                )
            }
            assertThat(Prefs.applyHiramekiCss, equalTo(Prefs.HIRAMEKI_CSS_DISABLED))
        } finally {
            unmockkObject(Prefs)
            AnkiDroidApp.sharedPreferencesTestingOverride = null
        }
    }
}
