/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.reviewer

/**
 * Constants used across the Reviewer component.
 */
object ReviewerConstants {
    /** Default duration (ms) for action snackbars (undo, bury, suspend) */
    const val ACTION_SNACKBAR_DURATION_MS = 1000

    /** Request code for audio recording permission */
    const val REQUEST_AUDIO_PERMISSION = 0

    /** Delay (ms) before retrying displayCardAnswer when waiting for state mutation */
    const val STATE_MUTATION_RETRY_DELAY_MS = 50L
}
