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

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnkiConnectServerTest {
    private var testServer: AnkiConnectServer? = null
    private val client = OkHttpClient()

    @After
    fun tearDown() {
        testServer?.stop()
        testServer = null
    }

    @Test
    fun testLegacyVersion4FormatSuccess() {
        // In API version <= 4 (used by Yomitan which defaults to version 2):
        // Raw result is returned directly without {"result": ..., "error": null} wrapper
        val numberReply = AnkiConnectServer.formatReply(6, null, 2)
        assertEquals("6", numberReply)

        val arrayReply = AnkiConnectServer.formatReply(JSONArray().put("Default"), null, 2)
        assertEquals("[\"Default\"]", arrayReply)

        val stringReply = AnkiConnectServer.formatReply("test string", null, 2)
        assertEquals("\"test string\"", stringReply)

        val nullReply = AnkiConnectServer.formatReply(null, null, 2)
        assertEquals("null", nullReply)
    }

    @Test
    fun testLegacyVersion4FormatError() {
        // In API version <= 4, errors still return standard error object
        val errorReply = AnkiConnectServer.formatReply(null, "some error", 2)
        val json = JSONObject(errorReply)
        assertEquals("some error", json.getString("error"))
        assertTrue(json.isNull("result"))
    }

    @Test
    fun testModernVersion6FormatSuccess() {
        // In API version >= 5 (standard version 6):
        // Wrapped in {"result": ..., "error": null}
        val numberReply = AnkiConnectServer.formatReply(6, null, 6)
        val json = JSONObject(numberReply)
        assertEquals(6, json.getInt("result"))
        assertTrue(json.isNull("error"))

        val arrayReply = AnkiConnectServer.formatReply(JSONArray().put("Default"), null, 6)
        val arrayJson = JSONObject(arrayReply)
        assertEquals("Default", arrayJson.getJSONArray("result").getString(0))
        assertTrue(arrayJson.isNull("error"))
    }

    @Test
    fun testModernVersion6FormatError() {
        val errorReply = AnkiConnectServer.formatReply(null, "some error", 6)
        val json = JSONObject(errorReply)
        assertEquals("some error", json.getString("error"))
        assertTrue(json.isNull("result"))
    }

    @Test
    fun testServerOptionsPreflightCorsAllowedOrigin() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort
        assertTrue(port > 0)

        val request =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .method("OPTIONS", null)
                .header("Origin", "moz-extension://bf60f7bf-90ac-46a0-87e4-2b8f70e047d8")
                .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("moz-extension://bf60f7bf-90ac-46a0-87e4-2b8f70e047d8", response.header("Access-Control-Allow-Origin"))
            assertEquals("true", response.header("Access-Control-Allow-Private-Network"))
            val methods = response.header("Access-Control-Allow-Methods")
            assertNotNull(methods)
            assertTrue(methods.contains("POST"))
        }
    }

    @Test
    fun testServerOptionsPreflightCorsDisallowedOrigin() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort

        val request =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .method("OPTIONS", null)
                .header("Origin", "https://malicious-website.com")
                .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertNull(response.header("Access-Control-Allow-Origin"))
        }
    }

    @Test
    fun testServerGetStatus() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort

        val request = Request.Builder().url("http://127.0.0.1:$port/").get().build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            val body = response.body?.string() ?: ""
            assertTrue(body.contains("Hirameki AnkiConnect server is running"))
        }
    }

    @Test
    fun testServerUnsupportedMethodReturns405() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort

        val request = Request.Builder().url("http://127.0.0.1:$port/").delete().build()
        client.newCall(request).execute().use { response ->
            assertEquals(405, response.code)
        }
    }

    @Test
    fun testServerPostVersionAction() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort

        val jsonBody = JSONObject().put("action", "version").put("version", 6).toString()
        val request =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            assertEquals(6, json.getInt("result"))
            assertTrue(json.isNull("error"))
        }
    }

    @Test
    fun testServerPostLegacyYomitanVersionAction() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort

        val jsonBody = JSONObject().put("action", "version").put("version", 2).toString()
        val request =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            val responseBody = response.body?.string()?.trim() ?: ""
            assertEquals("6", responseBody)
        }
    }

    @Test
    fun testServerPayloadTooLargeReturns413() {
        val server = AnkiConnectServer(0)
        testServer = server
        server.start()
        val port = server.listeningPort

        // Create a payload larger than 10MB limit
        val oversizedBytes = ByteArray(10 * 1024 * 1024 + 1024)
        val request =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .post(oversizedBytes.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            assertEquals(413, response.code)
        }
    }

    @Test
    fun testServerApiKeyProtection() {
        val server = AnkiConnectServer(port = 0, configuredApiKey = "superSecretKey42")
        testServer = server
        server.start()
        val port = server.listeningPort

        // Request with missing API key
        val unauthenticatedBody = JSONObject().put("action", "version").toString()
        val unauthenticatedRequest =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .post(unauthenticatedBody.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(unauthenticatedRequest).execute().use { response ->
            assertEquals(200, response.code)
            val json = JSONObject(response.body?.string() ?: "")
            assertFalse(json.isNull("error"))
            assertEquals("valid api key must be provided", json.getString("error"))
        }

        // Request with invalid API key
        val invalidKeyBody = JSONObject().put("action", "version").put("key", "wrongKey").toString()
        val invalidKeyRequest =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .post(invalidKeyBody.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(invalidKeyRequest).execute().use { response ->
            assertEquals(200, response.code)
            val json = JSONObject(response.body?.string() ?: "")
            assertFalse(json.isNull("error"))
            assertEquals("valid api key must be provided", json.getString("error"))
        }

        // Request with valid API key
        val validKeyBody = JSONObject().put("action", "version").put("key", "superSecretKey42").toString()
        val validKeyRequest =
            Request.Builder()
                .url("http://127.0.0.1:$port/")
                .post(validKeyBody.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(validKeyRequest).execute().use { response ->
            assertEquals(200, response.code)
            val json = JSONObject(response.body?.string() ?: "")
            assertTrue(json.isNull("error"))
            assertEquals(6, json.getInt("result"))
        }
    }
}
