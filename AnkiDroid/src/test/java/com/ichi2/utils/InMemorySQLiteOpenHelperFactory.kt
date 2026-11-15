// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

class InMemorySQLiteOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val inMemoryConfig =
            SupportSQLiteOpenHelper.Configuration
                .builder(configuration.context)
                .name(null) // in-memory
                .callback(configuration.callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .build()
        return FrameworkSQLiteOpenHelperFactory().create(inMemoryConfig)
    }
}
