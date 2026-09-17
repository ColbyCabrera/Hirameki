/*
 * Copyright (c) 2026 Hirameki contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ioDispatcher
import com.ichi2.testutils.EmptyApplication
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class PageWebViewViewModelTest {
    private val originalIoDispatcher = ioDispatcher
    private val store = ViewModelStore()

    @After
    fun restoreIoDispatcher() {
        store.clear()
        ioDispatcher = originalIoDispatcher
    }

    @Test
    fun `the server is started off the main thread and stopped with the screen`() =
        runTest {
            ioDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = createViewModel()

            // the view model is created during composition, on the main thread, where a socket must not be bound
            assertThat("nothing is started before the io work runs", viewModel.serverState.value, equalTo(ServerState.Stopped))

            advanceUntilIdle()
            val state = viewModel.serverState.value
            assertThat(state, instanceOf(ServerState.Running::class.java))
            val port = URI((state as ServerState.Running).serverBaseUrl).port
            assertThat("the page can reach its server", acceptsConnections(port), equalTo(true))

            store.clear()
            assertThat("the server is gone with the screen", acceptsConnections(port), equalTo(false))
        }

    @Test
    fun `a screen closed while the server is starting leaves none running`() {
        // a real io thread, held until the server's socket factory is hooked, so the start can be paused inside
        val io = Executors.newSingleThreadExecutor()
        val hooked = CountDownLatch(1)
        io.execute { hooked.await() }
        ioDispatcher = io.asCoroutineDispatcher()
        try {
            val viewModel = createViewModel()
            val starting = CountDownLatch(1)
            val closed = CountDownLatch(1)
            lateinit var socket: ServerSocket
            // nanohttpd asks for its socket first thing in start(), before anything is bound: a stop() at this moment
            // finds nothing to close, and the bind that follows would stay open
            viewModel.server.serverSocketFactory =
                NanoHTTPD.ServerSocketFactory {
                    starting.countDown()
                    closed.await()
                    ServerSocket().also { socket = it }
                }
            hooked.countDown()
            assertThat("the start is under way", starting.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), equalTo(true))

            store.clear()
            closed.countDown()
            // the start, and the stop that waits for it, run as one task on the io thread
            io.shutdown()
            assertThat("the start has finished", io.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), equalTo(true))

            assertThat("the socket the start bound is closed", socket.isClosed, equalTo(true))
            assertThat("nothing listens on its port", acceptsConnections(socket.localPort), equalTo(false))
            assertThat(viewModel.serverState.value, equalTo(ServerState.Stopped))
        } finally {
            io.shutdownNow()
        }
    }

    /** a view model on the current [ioDispatcher], created the way compose's viewModel() creates it */
    private fun createViewModel(): PageWebViewViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        return ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[
            PageWebViewViewModel::class.java,
        ]
    }

    private fun acceptsConnections(port: Int): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(AnkiServer.LOCALHOST, port), CONNECT_TIMEOUT_MS) }
            true
        } catch (_: IOException) {
            false
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2000
        const val TIMEOUT_SECONDS = 10L
    }
}
