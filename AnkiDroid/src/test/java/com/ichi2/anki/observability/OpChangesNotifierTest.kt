// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 David Allison <62114487+david-allison@users.noreply.github.com>

package com.ichi2.anki.observability

import anki.collection.OpChanges
import anki.collection.opChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class OpChangesNotifierTest : JvmTest() {
    @Test
    fun `operation returns its response after notifying subscribers with its handler`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            val changes = opChanges { tag = true }
            val handler = Any()

            assertSame(changes, undoableOp(handler) { changes })
            assertEquals(1, observer.changeCount)
            assertSame(handler, observer.lastHandler)
        }

    @Test
    fun `cancelling a queued operation skips the mutation and notification`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            var executed = false
            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                val operation =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        undoableOp {
                            executed = true
                            opChanges { tag = true }
                        }
                    }

                operation.cancelAndJoin()

                assertFalse(executed)
                assertEquals(0, observer.changeCount)
            }
        }

    @Test
    fun `cancelling during a mutation still notifies before the operation completes`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            val operation =
                launch {
                    val caller = currentCoroutineContext().job
                    undoableOp {
                        caller.cancel()
                        opChanges { tag = true }
                    }
                    fail("Cancellation must still propagate to the caller")
                }

            operation.join()

            assertTrue(operation.isCancelled)
            assertEquals(1, observer.changeCount)
        }

    @Test
    fun `cancelling while notification is queued on Main still delivers it`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            val worker = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var executed = false
            try {
                withCollectionAccessOn(worker) {
                    val operation =
                        launch(worker, start = CoroutineStart.UNDISPATCHED) {
                            undoableOp {
                                executed = true
                                opChanges { tag = true }
                            }
                            fail("Cancellation must still propagate to the caller")
                        }
                    assertTrue(executed)
                    assertEquals(0, observer.changeCount)

                    operation.cancel()
                    runCurrent()
                    operation.join()

                    assertTrue(operation.isCancelled)
                    assertEquals(1, observer.changeCount)
                }
            } finally {
                Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            }
        }

    private suspend fun withCollectionAccessOn(
        dispatcher: CoroutineDispatcher,
        block: suspend () -> Unit,
    ) {
        CollectionManager.setTestDispatcher(dispatcher)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1))
        }
    }

    private class ChangeCounter : ChangeManager.Subscriber {
        var changeCount = 0
            private set
        var lastHandler: Any? = null
            private set

        override fun opExecuted(
            changes: OpChanges,
            handler: Any?,
        ) {
            changeCount++
            lastHandler = handler
        }
    }
}
