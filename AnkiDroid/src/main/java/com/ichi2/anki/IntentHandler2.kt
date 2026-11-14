// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.os.Bundle
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import timber.log.Timber

/**
 * This activity serves as an intermediate handler to process various types of intents and forward them to the NoteEditor fragment hosted within the SingleFragmentActivity.
 *
 * The main reason for using this IntentHandler2 is to avoid conflicts in the manifest file. We can't have multiple
 * ACTION_SEND intents in same activity (IntentHandler) that only differ by their labels. By using this handler, we can manage these intents
 * and make sure they are sent to the NoteEditor correctly.
 */
class IntentHandler2 : AbstractIntentHandler() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v(intent.toString())

        if (intent.extras == null) {
            Timber.w("Intent unexpectedly has no extras. Notifying user, defaulting to add note.")
            showThemedToast(this, getString(R.string.something_wrong), false)
            startActivity(NoteEditorLauncher.AddNote().toIntent(this))
            finish()
        } else {
            Timber.i("Opening Note Editor from intent")
            val noteEditorIntent =
                NoteEditorLauncher.PassArguments(intent.extras!!).toIntent(this, intent.action)
            noteEditorIntent.setDataAndType(intent.data, intent.type)
            startActivity(noteEditorIntent)
            finish()
        }
    }
}
