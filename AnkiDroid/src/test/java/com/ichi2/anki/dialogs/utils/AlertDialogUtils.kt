// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.dialogs.utils

import android.content.DialogInterface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.utils.HandlerUtils.executeFunctionUsingHandler
import com.ichi2.utils.getInputField
import org.hamcrest.MatcherAssert.assertThat
import kotlin.test.assertNotNull

var AlertDialog.input
    get() = getInputField().text.toString()
    set(value) {
        getInputField().setText(value)
    }

val AlertDialog.title
    get() =
        requireNotNull(this.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)) {
            "androidx.appcompat.R.id.alertTitle not found"
        }.text.toString()

fun AlertDialog.performPositiveClick() {
    // This exists as callOnClick did not call the listener
    val positiveButton = assertNotNull(getButton(DialogInterface.BUTTON_POSITIVE), message = "positive button")
    assertThat("button is visible", positiveButton.isVisible)
    assertThat("button is enabled", positiveButton.isEnabled)
    executeFunctionUsingHandler { positiveButton.callOnClick() }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
