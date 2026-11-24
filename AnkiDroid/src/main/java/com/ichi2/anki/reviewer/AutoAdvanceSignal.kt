// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer

/** What the Compose reviewer's auto advance reacts to, emitted by [ReviewerViewModel] in the order it happened. */
sealed interface AutoAdvanceSignal {
    data class SideShown(
        val side: CardSide,
        val waitsForMedia: Boolean,
    ) : AutoAdvanceSignal

    data class MediaFinished(
        val side: CardSide,
    ) : AutoAdvanceSignal
}

/** Starts the [AutomaticAnswer] timer for each side the Compose reviewer shows. */
class AutoAdvanceSignalHandler(
    private val automaticAnswer: () -> AutomaticAnswer,
) {
    private var shown: AutoAdvanceSignal.SideShown? = null

    fun handle(signal: AutoAdvanceSignal) {
        when (signal) {
            is AutoAdvanceSignal.SideShown -> {
                shown = signal
                automaticAnswer().onSideShown()
                if (!signal.waitsForMedia) automaticAnswer().scheduleFor(signal.side)
            }
            is AutoAdvanceSignal.MediaFinished -> {
                val shown = shown ?: return
                // a cancelled playback reports the side it was playing, which may no longer be shown
                if (shown.waitsForMedia && shown.side == signal.side) automaticAnswer().scheduleFor(signal.side)
            }
        }
    }
}
