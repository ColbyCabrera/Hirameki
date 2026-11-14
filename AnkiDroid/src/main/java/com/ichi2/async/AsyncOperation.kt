// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.async

import com.ichi2.anki.Channel
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.DialogHandlerMessage

abstract class AsyncOperation {
    abstract val notificationMessage: String?
    abstract val notificationTitle: String
    abstract val handlerMessage: DialogHandlerMessage
}

fun DeckPicker.sendNotificationForAsyncOperation(
    operation: AsyncOperation,
    channel: Channel,
) {
    // Store a persistent message instructing AnkiDroid to perform the operation
    DialogHandler.storeMessage(operation.handlerMessage.toMessage())
    // Show a basic notification to the user in the notification bar in the meantime
    val title = operation.notificationTitle
    val message = operation.notificationMessage
    showSimpleNotification(title, message, channel)
}

@Suppress("unused")
fun DeckPicker.performAsyncOperation(
    operation: AsyncOperation,
    channel: Channel,
) {
    if (activityPaused) {
        sendNotificationForAsyncOperation(operation, channel)
        return
    }

    operation.handlerMessage.handleAsyncMessage(this)
}
