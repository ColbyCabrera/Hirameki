/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.PreferenceTestUtils.getAttrsFromXml
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.settings.enums.PrefEnum
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class PrefsRobolectricTest : RobolectricTest() {
    private fun getKeysAndDefaultValues(): MutableMap<String, Any?> {
        val spy = spyk(SPMockBuilder().createSharedPreferences())
        AnkiDroidApp.sharedPreferencesTestingOverride = spy
        val keysAndDefaultValues: MutableMap<String, Any?> = mutableMapOf()

        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        mockkObject(Prefs)
        every { Prefs.resources } returns mockResources
        every { spy.getBoolean(any(), any()) } answers {
            val key = arg<String>(0)
            keysAndDefaultValues[key] = arg<Boolean>(1)
            callOriginal()
        }
        every { spy.getString(any(), any()) } answers {
            val key = arg<String>(0)
            keysAndDefaultValues[key] = arg<String?>(1)
            callOriginal()
        }
        every { spy.getInt(any(), any()) } answers {
            val key = arg<String>(0)
            keysAndDefaultValues[key] = arg<Int>(1)
            callOriginal()
        }

        val allowedNonPreferenceProperties = setOf(
            "resources", "HIRAMEKI_CSS_ALL", "HIRAMEKI_CSS_NO_FONT_SIZE", "HIRAMEKI_CSS_DISABLED"
        )
        val unexpectedExceptions = mutableListOf<Throwable>()
        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            try {
                property.getter.call(Prefs)
            } catch (e: Exception) {
                val unwrapped =
                    if (e is java.lang.reflect.InvocationTargetException) e.cause ?: e else e
                if (property.name !in allowedNonPreferenceProperties) {
                    unexpectedExceptions.add(unwrapped)
                }
            }
        }
        if (unexpectedExceptions.isNotEmpty()) {
            throw AssertionError(
                "Unexpected exceptions thrown during Prefs property inspection: $unexpectedExceptions",
                unexpectedExceptions.first()
            )
        }
        unmockkObject(Prefs)
        AnkiDroidApp.sharedPreferencesTestingOverride = null
        return keysAndDefaultValues
    }

    @Test
    fun `all default values match the preference XMLs`() {
        val keysAndDefaultValues = getKeysAndDefaultValues()
        val devOptionsKeys = PreferenceTestUtils.getDevOptionsKeys(targetContext)
        val prefs = PreferenceTestUtils.getAllPreferencesFragments(targetContext).asSequence()
            .filterIsInstance<SettingsFragment>().map { it.preferenceResource }
            .flatMap { getAttrsFromXml(targetContext, it, listOf("defaultValue", "key")) }
            .filter { it["key"] != null }.associate {
                PreferenceTestUtils.attrValueToString(
                    it["key"]!!, targetContext
                ) to it["defaultValue"]
            }

        for ((key, defaultValue) in keysAndDefaultValues.entries) {
            if (key !in prefs || key in devOptionsKeys) continue
            val prefsDefaultValue = prefs.getValue(key)
            assertThat(
                "The default value of '$key' matches the preference XML",
                defaultValue.toString(),
                equalTo(prefsDefaultValue)
            )
        }
    }

    private fun getPropertyNamesAndKeys(): MutableMap<String, String> {
        val spy = spyk(SPMockBuilder().createSharedPreferences())
        AnkiDroidApp.sharedPreferencesTestingOverride = spy
        val keys = mutableListOf<String>()

        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        mockkObject(Prefs)
        every { Prefs.resources } returns mockResources
        val captureKey = { keyParam: String ->
            val key = PreferenceTestUtils.attrValueToString("@$keyParam", targetContext)
            keys.append(key)
        }
        every { spy.getBoolean(any(), any()) } answers {
            captureKey(arg(0))
            callOriginal()
        }
        every { spy.getString(any(), any()) } answers {
            captureKey(arg(0))
            callOriginal()
        }
        every { spy.getInt(any(), any()) } answers {
            captureKey(arg(0))
            callOriginal()
        }
        val propertyNamesAndKeys = mutableMapOf<String, String>()
        val allowedNonPreferenceProperties = setOf(
            "resources", "HIRAMEKI_CSS_ALL", "HIRAMEKI_CSS_NO_FONT_SIZE", "HIRAMEKI_CSS_DISABLED"
        )
        val unexpectedExceptions = mutableListOf<Throwable>()
        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            val keysSizeBefore = keys.size
            try {
                property.getter.call(Prefs)
                if (keys.size > keysSizeBefore) {
                    propertyNamesAndKeys[property.name] = keys.last()
                }
            } catch (e: Exception) {
                val unwrapped =
                    if (e is java.lang.reflect.InvocationTargetException) e.cause ?: e else e
                if (property.name !in allowedNonPreferenceProperties) {
                    unexpectedExceptions.add(unwrapped)
                }
            }
        }
        if (unexpectedExceptions.isNotEmpty()) {
            throw AssertionError(
                "Unexpected exceptions thrown during Prefs property key mapping: $unexpectedExceptions",
                unexpectedExceptions.first()
            )
        }
        unmockkObject(Prefs)
        AnkiDroidApp.sharedPreferencesTestingOverride = null
        return propertyNamesAndKeys
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `PrefEnum values match their preference entries`() {
        val listPreferences =
            PreferenceTestUtils.getAllPreferencesFragments(targetContext).asSequence()
                .filterIsInstance<SettingsFragment>().map { it.preferenceResource }
                .flatMap { getAttrsFromXml(targetContext, it, listOf("key", "entryValues")) }
                .filter { it["key"] != null && it["entryValues"] != null }.associate {
                    PreferenceTestUtils.attrValueToString(
                        it["key"]!!, targetContext
                    ) to PreferenceTestUtils.attrToStringArray(it["entryValues"]!!, targetContext)
                        .toList()
                }

        // Prefs property name (String) -> Key (String)
        val allPropertiesAndKeys = getPropertyNamesAndKeys()
        val enumProperties = Prefs::class.memberProperties.filter {
            it.returnType.isSubtypeOf(PrefEnum::class.createType())
        }
        // Only enum-backed prefs that are exposed as list preferences in settings XML should be validated here.
        val enumPropertiesMap =
            enumProperties.associateBy { allPropertiesAndKeys.getValue(it.name) }
                .filterKeys { it in listPreferences }

        assertThat(
            "Expected at least one enum-backed list preference to be validated",
            enumPropertiesMap.isNotEmpty(),
            equalTo(true)
        )

        // Key (String) -> PrefEnum entryValues (List<String>)
        val prefsEnumKeysAndValues = mutableMapOf<String, List<String>>()
        for ((key, property) in enumPropertiesMap.entries) {
            val enumConstants =
                ((property.returnType.classifier as KClass<*>).java.enumConstants) as Array<PrefEnum>
            prefsEnumKeysAndValues[key] =
                enumConstants.map { targetContext.resources.getString(it.entryResId) }
        }

        assertThat(
            "Expected at least one enum key-value pair to be validated",
            prefsEnumKeysAndValues.isNotEmpty(),
            equalTo(true)
        )

        for ((key, enumValues) in prefsEnumKeysAndValues) {
            assertEquals(enumValues, listPreferences[key])
        }
    }
}
