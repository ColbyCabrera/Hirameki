// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.ichi2.anki.CardBrowser

class MySearchesContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent =
        Intent(context, CardBrowser::class.java).apply {
            putExtra("target", "my_searches")
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): String? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }
        return intent?.getStringExtra("search_query")
    }
}
