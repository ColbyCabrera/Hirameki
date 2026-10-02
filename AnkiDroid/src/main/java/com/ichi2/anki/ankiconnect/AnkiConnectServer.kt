/*
 * Copyright (c) 2026 Colby Cabrera <gdthyispro@gmail.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ankiconnect

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

open class AnkiConnectServer(
    port: Int = DEFAULT_PORT,
    private val configuredApiKey: String? = null,
) : NanoHTTPD(LOCALHOST, port) {
    private val runner = BoundedAsyncRunner()

    init {
        setAsyncRunner(runner)
    }

    override fun stop() {
        super.stop()
        runner.closeAll()
    }

    override fun serve(session: IHTTPSession): Response =
        when (session.method) {
            Method.OPTIONS -> {
                val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                addCorsHeaders(session, response)
                response
            }
            Method.GET -> {
                val response =
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/plain; charset=utf-8",
                        "Hirameki AnkiConnect server is running.",
                    )
                addCorsHeaders(session, response)
                response
            }
            Method.POST -> {
                val response = handlePost(session)
                addCorsHeaders(session, response)
                response
            }
            else -> {
                val response =
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        MIME_PLAINTEXT,
                        "Method not allowed",
                    )
                addCorsHeaders(session, response)
                response
            }
        }

    private fun handlePost(session: IHTTPSession): Response {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: -1
        val isChunked = session.headers["transfer-encoding"]?.equals("chunked", ignoreCase = true) == true

        if (contentLength > MAX_REQUEST_SIZE) {
            Timber.w("AnkiConnect: Request body too large: %d bytes (limit: %d)", contentLength, MAX_REQUEST_SIZE)
            val errorBody = formatReply(null, "Payload too large (max $MAX_REQUEST_SIZE bytes)", 6)
            return newFixedLengthResponse(
                Response.Status.PAYLOAD_TOO_LARGE,
                "application/json; charset=utf-8",
                errorBody,
            )
        }

        if (contentLength <= 0 && !isChunked) {
            return jsonResponse(null, "Empty request body")
        }

        val postString =
            try {
                val buffer = ByteArray(8192)
                val initialCapacity = if (contentLength in 1..MAX_REQUEST_SIZE) contentLength else 8192
                val out = ByteArrayOutputStream(initialCapacity)
                var totalRead = 0
                val maxToRead = if (contentLength > 0) contentLength else (MAX_REQUEST_SIZE + 1)
                while (totalRead < maxToRead) {
                    val toRead = minOf(buffer.size, maxToRead - totalRead)
                    val read = session.inputStream.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    totalRead += read
                }
                if (totalRead > MAX_REQUEST_SIZE) {
                    val errorBody = formatReply(null, "Payload too large (max $MAX_REQUEST_SIZE bytes)", 6)
                    return newFixedLengthResponse(
                        Response.Status.PAYLOAD_TOO_LARGE,
                        "application/json; charset=utf-8",
                        errorBody,
                    )
                }
                out.toString("UTF-8")
            } catch (e: Exception) {
                Timber.w(e, "AnkiConnect: Failed to read request body")
                ""
            }

        if (postString.isBlank()) {
            return jsonResponse(null, "Empty request body")
        }

        val requestJson =
            try {
                JSONObject(postString)
            } catch (e: Exception) {
                Timber.w(e, "AnkiConnect: Failed to parse JSON body")
                return jsonResponse(null, "Invalid JSON: ${e.message}")
            }

        val version = requestJson.optInt("version", 6)

        // API Key verification if configured
        if (!configuredApiKey.isNullOrEmpty()) {
            val key = requestJson.optString("key", "")
            if (key != configuredApiKey) {
                Timber.w("AnkiConnect: Invalid API key attempt")
                return jsonResponse(null, "valid api key must be provided", version)
            }
        }

        val action = requestJson.optString("action")
        val params = requestJson.optJSONObject("params")

        if (action.isNullOrBlank()) {
            return jsonResponse(null, "Missing action in request", version)
        }

        return try {
            val result = AnkiConnectHandler.handleAction(action, version, params)
            jsonResponse(result, null, version)
        } catch (e: Exception) {
            Timber.w(e, "AnkiConnect: Action failed: %s", action)
            jsonResponse(null, e.message ?: "Action failed: $action", version)
        }
    }

    private fun jsonResponse(
        result: Any?,
        error: String?,
        version: Int = 6,
    ): Response {
        val body = formatReply(result, error, version)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            body,
        )
    }

    private fun addCorsHeaders(
        session: IHTTPSession,
        response: Response,
    ) {
        val origin = session.headers["origin"]
        if (origin != null && isAllowedOrigin(origin)) {
            response.addHeader("Access-Control-Allow-Origin", origin)
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "*")
            response.addHeader("Access-Control-Allow-Private-Network", "true")
            response.addHeader("Vary", "Origin")
        }
    }

    private fun isAllowedOrigin(origin: String): Boolean {
        if (origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://") || origin == "null") {
            return true
        }
        return try {
            val uri = URI(origin)
            val host = uri.host ?: return false
            val scheme = uri.scheme ?: return false
            (scheme == "http" || scheme == "https") && (host == "127.0.0.1" || host == "localhost")
        } catch (_: Exception) {
            false
        }
    }

    private class BoundedAsyncRunner(
        maxThreads: Int = 8,
    ) : NanoHTTPD.AsyncRunner {
        private val threadNumber = AtomicInteger(1)
        private val executor =
            ThreadPoolExecutor(
                2,
                maxThreads,
                30L,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(64),
                ThreadFactory { r ->
                    Thread(r, "AnkiConnect-Worker-${threadNumber.getAndIncrement()}").apply {
                        isDaemon = true
                    }
                },
                ThreadPoolExecutor.AbortPolicy(),
            )
        private val runningHandlers = Collections.synchronizedList(ArrayList<NanoHTTPD.ClientHandler>())

        override fun exec(code: NanoHTTPD.ClientHandler) {
            runningHandlers.add(code)
            try {
                executor.execute {
                    try {
                        code.run()
                    } finally {
                        closed(code)
                    }
                }
            } catch (e: RejectedExecutionException) {
                runningHandlers.remove(code)
                code.close()
            }
        }

        override fun closed(clientHandler: NanoHTTPD.ClientHandler) {
            runningHandlers.remove(clientHandler)
        }

        override fun closeAll() {
            for (handler in ArrayList(runningHandlers)) {
                try {
                    handler.close()
                } catch (_: Exception) {
                }
            }
            runningHandlers.clear()
            executor.shutdownNow()
        }
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"
        const val DEFAULT_PORT = 8765
        const val MAX_REQUEST_SIZE = 10 * 1024 * 1024 // 10 MB limit

        fun formatReply(
            result: Any?,
            error: String?,
            version: Int,
        ): String =
            if (error != null) {
                val json = JSONObject()
                json.put("result", JSONObject.NULL)
                json.put("error", error)
                json.toString()
            } else if (version <= 4) {
                when (result) {
                    null -> "null"
                    is JSONObject -> result.toString()
                    is JSONArray -> result.toString()
                    is String -> JSONObject.quote(result)
                    is Number, is Boolean -> result.toString()
                    else -> result.toString()
                }
            } else {
                val json = JSONObject()
                json.put("result", result ?: JSONObject.NULL)
                json.put("error", JSONObject.NULL)
                json.toString()
            }
    }
}
