// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.browser

import anki.search.BrowserRow

data class BrowserRowWithId(
    val browserRow: BrowserRow,
    val id: CardOrNoteId,
)
