// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.testutils

import androidx.annotation.CallSuper
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.observability.ChangeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import org.junit.After
import org.junit.Before

open class JvmTest : InMemoryAnkiTest() {
    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        ChangeManager.clearSubscribers()
    }

    @After
    @CallSuper
    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            ioDispatcher = Dispatchers.IO
        }
    }

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        ioDispatcher = dispatcher
    }
}
