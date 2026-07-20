package com.ichi2.anki.ui.compose.components

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnkiToggleViewTest {

    @Test
    fun testInitialStateAndSetChecked() {
        val toggleView = AnkiToggleView(ApplicationProvider.getApplicationContext())
        assertFalse(toggleView.isChecked)

        var listenerCalled = false
        var lastCheckedValue = false
        toggleView.setOnCheckedChangeListener { _, checked ->
            listenerCalled = true
            lastCheckedValue = checked
        }

        toggleView.isChecked = true
        assertTrue(toggleView.isChecked)
        assertTrue(listenerCalled)
        assertTrue(lastCheckedValue)
    }

    @Test
    fun testToggle() {
        val toggleView = AnkiToggleView(ApplicationProvider.getApplicationContext())
        assertFalse(toggleView.isChecked)
        toggleView.toggle()
        assertTrue(toggleView.isChecked)
        toggleView.toggle()
        assertFalse(toggleView.isChecked)
    }
}
