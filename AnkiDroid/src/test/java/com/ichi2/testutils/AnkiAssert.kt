// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import kotlin.test.junit5.JUnit5Asserter

/** Asserts that the expression is `false` with an optional [message]. */
fun assertFalse(
    message: String? = null,
    actual: Boolean,
) {
    // This exists in JUnit, but we want to avoid JUnit as their `assertNotNull` does not use contracts
    // So, we want a method in a different namespace for `assertFalse`
    // JUnitAsserter doesn't contain it, so we add it in
    JUnit5Asserter.assertTrue(message, !actual)
}
