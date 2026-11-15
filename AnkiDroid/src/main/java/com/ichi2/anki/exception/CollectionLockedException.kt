// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.exception

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException

/**
 * The collection database is locked by another process: normally a parallel Hirameki install.
 *
 * Thrown in place of [BackendDbLockedException] for a better user-facing error:
 *
 * * Inform the user two apps using the same folder are not supported.
 * * Inform them they need to change the folder (via 'Hirameki directory')
 * * Inform them that they should use 'Force close' to temporarily solve it.
 * * Inform the user the app may not be running.
 *
 * Note: "Anki already open, or media currently syncing." is currently hardcoded as an error.
 * `rslib/src/error/db.rs`
 */
class CollectionLockedException(
    cause: BackendDbLockedException,
    message: String = defaultMessage(cause),
) : BackendException(message) {
    init {
        initCause(cause)
    }

    companion object {
        /**
         * Localized, user-facing guidance shown wherever this exception is displayed.
         *
         * Falls back to the backend message if the app has not been initialized yet, for
         * example in a headless test.
         */
        private fun defaultMessage(cause: BackendDbLockedException): String =
            try {
                val app = AnkiDroidApp.instance
                app.getString(R.string.database_locked_summary_new, app.getString(R.string.col_path))
            } catch (_: Exception) {
                cause.localizedMessage ?: "Hirameki database is locked by another process."
            }
    }
}
