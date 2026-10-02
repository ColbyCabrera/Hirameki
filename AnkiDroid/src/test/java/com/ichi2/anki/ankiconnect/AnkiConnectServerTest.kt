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

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnkiConnectServerTest {
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
}
