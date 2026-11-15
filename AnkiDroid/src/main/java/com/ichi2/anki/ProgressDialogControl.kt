// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

/**
 * Interface to unify and decouple progress dialog updates, making it easy to transition
 * from legacy android.app.ProgressDialog to modern Jetpack Compose.
 */
interface ProgressDialogControl {
    fun setMessage(message: CharSequence)
}
