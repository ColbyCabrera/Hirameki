// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import com.ichi2.anki.R
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.withoutUnicodeIsolation
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.Destination

data class CardInfoDestination(
    val cardId: CardId,
    val title: String,
) : Destination {
    override fun toIntent(context: Context): Intent {
        // title contains FSI and PDI character types
        val simplifiedTitle = withoutUnicodeIsolation(title)
        val sentenceStrings =
            listOf(
                simplifiedTitle.toSentenceCase(context, R.string.sentence_card_stats_current_card_study),
                simplifiedTitle.toSentenceCase(context, R.string.sentence_card_stats_current_card_browse),
            )
        return PageFragment.getIntent(
            context,
            "card-info/$cardId",
            sentenceStrings.firstOrNull { it != simplifiedTitle } ?: title,
        )
    }
}
