// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.browser

import android.content.Intent
import androidx.annotation.CheckResult

/** How the [com.ichi2.anki.CardBrowser] can be launched */
sealed interface CardBrowserLaunchOptions {
    data class DeepLink(
        val search: String,
    ) : CardBrowserLaunchOptions

    data class SearchQueryJs(
        val search: String,
        val allDecks: Boolean,
    ) : CardBrowserLaunchOptions

    /** Opened from the 'Card Browser' system context menu ([Intent.ACTION_PROCESS_TEXT]) */
    data class SystemContextMenu(
        val search: CharSequence,
    ) : CardBrowserLaunchOptions
}

@CheckResult
fun Intent.toCardBrowserLaunchOptions(): CardBrowserLaunchOptions? {
    // search card using deep links
    data?.getQueryParameter("search")?.let {
        return CardBrowserLaunchOptions.DeepLink(it)
    }

    // for intent coming from search query js api
    getStringExtra("search_query")?.let {
        return CardBrowserLaunchOptions.SearchQueryJs(it, getBooleanExtra("all_decks", false))
    }

    // Maybe we were called from ACTION_PROCESS_TEXT
    if (Intent.ACTION_PROCESS_TEXT == action) {
        val search = getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (search.isNullOrEmpty()) {
            return null
        }
        return CardBrowserLaunchOptions.SystemContextMenu(search)
    }

    return null
}
