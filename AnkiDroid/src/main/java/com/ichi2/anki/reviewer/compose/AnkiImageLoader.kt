// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.compose

import android.content.Context
import coil.ImageLoader

fun ankiImageLoader(context: Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .build()
