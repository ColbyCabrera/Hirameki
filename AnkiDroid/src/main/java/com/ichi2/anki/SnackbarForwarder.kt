/*
 * Copyright (c) 2026 ColbyCabrera <gdthyispro@gmail.com>
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

package com.ichi2.anki

interface SnackbarForwarder {
    fun forwardSnackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null)
}

data class SnackbarMessageEvent(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)
