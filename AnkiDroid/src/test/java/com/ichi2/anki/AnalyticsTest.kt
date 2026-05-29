/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
import android.content.res.Resources
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.analytics.UsageAnalytics
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.validateMockitoUsage
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AnalyticsTest {
    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    private val sharedPreferences = SPMockBuilder().createSharedPreferences().apply {
        edit {
            putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, true)
        }
    }

    @Before
    fun setUp() {
        UsageAnalytics.resetForTests()
        AnkiDroidApp.sharedPreferencesTestingOverride = sharedPreferences
        MockitoAnnotations.openMocks(this)

        whenever((mockResources.getBoolean(R.bool.ga_anonymizeIp))).thenReturn(true)
        whenever(mockResources.getInteger(R.integer.ga_sampleFrequency)).thenReturn(10)
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockContext.getString(R.string.ga_trackingId)).thenReturn("Mock Tracking ID")
        whenever(mockContext.getString(R.string.app_name)).thenReturn("Mock Application Name")
        whenever(mockContext.packageName).thenReturn("mock_context")
        whenever(
            mockContext.getSharedPreferences(
                "mock_context_preferences",
                Context.MODE_PRIVATE
            )
        ).thenReturn(sharedPreferences)
    }

    @After
    fun validate() {
        AnkiDroidApp.sharedPreferencesTestingOverride = null
        validateMockitoUsage()
    }

    @Test
    fun initializeDoesNotBuildAnalyticsForFork() {
        assertFalse(UsageAnalytics.isAvailable)
        assertNull(mockContext.let { UsageAnalytics.initialize(it) })
        assertFalse(UsageAnalytics.isEnabled)

        UsageAnalytics.isEnabled = true

        assertFalse(sharedPreferences.getBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, false))
    }

    @Test
    fun getCauseReturnsRootCauseWhenAnalyticsDisabled() {
        val exception = mock(Exception::class.java)
        whenever(exception.cause).thenReturn(null)

        val cause = UsageAnalytics.getCause(exception)

        verify(exception).cause
        assertEquals(exception, cause)
    }
}
