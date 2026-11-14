// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_DEFAULTS

/** A much which is faster than mockk(relaxed = true) */
inline fun <reified T> mockIt(): T = Mockito.mock(T::class.java, RETURNS_DEFAULTS)
