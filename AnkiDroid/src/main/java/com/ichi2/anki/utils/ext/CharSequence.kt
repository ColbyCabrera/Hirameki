// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.utils.ext

fun <C : CharSequence> C?.ifNullOrEmpty(defaultValue: () -> C): C = this?.ifEmpty(defaultValue) ?: defaultValue()
