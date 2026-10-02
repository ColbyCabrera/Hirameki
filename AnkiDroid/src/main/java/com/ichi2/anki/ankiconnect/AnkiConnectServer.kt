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

open class AnkiConnectServer(
    port: Int = DEFAULT_PORT,
) : NanoHTTPD(LOCALHOST, port) {
    override fun serve(session: IHTTPSession): Response =
        when (session.method) {
            Method.OPTIONS -> {
                val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                addCorsHeaders(response)
                response
            }
            Method.GET -> {
                val response =
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/plain; charset=utf-8",
                        "Hirameki AnkiConnect server is running.",
                    )
                addCorsHeaders(response)
                response
            }
            Method.POST -> {
                val response = handlePost(session)
                addCorsHeaders(response)
                response
            }
            else -> {
                val response =
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        MIME_PLAINTEXT,
                        "Method not allowed",
                    )
                addCorsHeaders(response)
                response
            }
        }

    private fun handlePost(session: IHTTPSession): Response {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val postString =
            if (contentLength > 0) {
                val bytes = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = session.inputStream.read(bytes, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                String(bytes, 0, totalRead, Charsets.UTF_8)
            } else {
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

        val action = requestJson.optString("action")
        val version = requestJson.optInt("version", 6)
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

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Access-Control-Allow-Private-Network", "true")
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"
        const val DEFAULT_PORT = 8765

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
