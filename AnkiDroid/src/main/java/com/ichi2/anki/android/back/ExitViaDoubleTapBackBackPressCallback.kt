// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.android.back

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.HandlerUtils
import timber.log.Timber

/**
 * Note: this uses sharedPreferences via AnkiDroidApp, so must be called after
 * [AnkiActivity.showedActivityFailedScreen]
 *
 * @see Prefs.exitViaDoubleTapBack
 */
// TODO: Convert this to a class when context parameters are usable
fun AnkiActivity.exitViaDoubleTapBackCallback(): OnBackPressedCallback =
    object : OnBackPressedCallback(enabled = Prefs.exitViaDoubleTapBack) {
        lateinit var strongListenerReference: OnSharedPreferenceChangeListener

        override fun handleOnBackPressed() {
            showSnackbar(R.string.back_pressed_once, Snackbar.LENGTH_SHORT)
            this.isEnabled = false
            HandlerUtils.executeFunctionWithDelay(Consts.SHORT_TOAST_DURATION) {
                this.isEnabled = true
            }
        }
    }.also { callback ->
        // PreferenceManager uses weak references, so we need our own strong reference which
        // will go out of scope
        callback.strongListenerReference =
            OnSharedPreferenceChangeListener { prefs, key ->
                if (key == getString(R.string.exit_via_double_tap_back_key)) {
                    callback.isEnabled =
                        Prefs.exitViaDoubleTapBack.also {
                            Timber.i("exit via double tap callback -> %b", it)
                        }
                }
            }

        PreferenceManager
            .getDefaultSharedPreferences(AnkiDroidApp.instance)
            .registerOnSharedPreferenceChangeListener(callback.strongListenerReference)
    }
