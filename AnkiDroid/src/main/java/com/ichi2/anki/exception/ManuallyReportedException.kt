// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.exception

import java.lang.RuntimeException

/** An exception for manual reporting to ACRA  */
class ManuallyReportedException(
    message: String?,
) : RuntimeException(message)
