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

import com.ichi2.testutils.JvmTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnkiConnectHandlerTest : JvmTest() {
    @Test
    fun testVersion() {
        val result = AnkiConnectHandler.handleActionWithCol("version", 6, null, col)
        assertEquals(6, result)
    }

    @Test
    fun testApiReflect() {
        val result = AnkiConnectHandler.handleActionWithCol("apiReflect", 6, null, col) as JSONObject
        assertTrue(result.has("actions"))
        val actions = result.getJSONArray("actions")
        assertTrue(actions.length() > 0)
    }

    @Test
    fun testDeckNamesAndCreation() {
        val initialDecks = AnkiConnectHandler.handleActionWithCol("deckNames", 6, null, col) as JSONArray
        assertTrue(initialDecks.length() >= 1)

        val createParams = JSONObject().put("deck", "TestDeck::SubDeck")
        val deckId = AnkiConnectHandler.handleActionWithCol("createDeck", 6, createParams, col)
        assertNotNull(deckId)

        val decksAfter = AnkiConnectHandler.handleActionWithCol("deckNames", 6, null, col) as JSONArray
        var found = false
        for (i in 0 until decksAfter.length()) {
            if (decksAfter.getString(i) == "TestDeck::SubDeck") {
                found = true
                break
            }
        }
        assertTrue(found)

        val decksAndIds = AnkiConnectHandler.handleActionWithCol("deckNamesAndIds", 6, null, col) as JSONObject
        assertTrue(decksAndIds.has("TestDeck::SubDeck"))
    }

    @Test
    fun testModelNamesAndFields() {
        val models = AnkiConnectHandler.handleActionWithCol("modelNames", 6, null, col) as JSONArray
        assertTrue(models.length() > 0)
        val firstModelName = models.getString(0)

        val fieldParams = JSONObject().put("modelName", firstModelName)
        val fields = AnkiConnectHandler.handleActionWithCol("modelFieldNames", 6, fieldParams, col) as JSONArray
        assertTrue(fields.length() > 0)

        val templates = AnkiConnectHandler.handleActionWithCol("modelFieldsOnTemplates", 6, fieldParams, col) as JSONObject
        assertTrue(templates.length() > 0)
    }

    @Test
    fun testAddNoteAndFindNotes() {
        val models = AnkiConnectHandler.handleActionWithCol("modelNames", 6, null, col) as JSONArray
        val modelName = models.getString(0)
        val fieldParams = JSONObject().put("modelName", modelName)
        val fields = AnkiConnectHandler.handleActionWithCol("modelFieldNames", 6, fieldParams, col) as JSONArray
        val firstField = fields.getString(0)
        val secondField = if (fields.length() > 1) fields.getString(1) else firstField

        val noteFields = JSONObject()
        noteFields.put(firstField, "HiramekiQuestion")
        noteFields.put(secondField, "HiramekiAnswer")

        val noteObj = JSONObject()
        noteObj.put("deckName", "Default")
        noteObj.put("modelName", modelName)
        noteObj.put("fields", noteFields)
        noteObj.put("tags", JSONArray().put("test_tag"))

        val params = JSONObject().put("note", noteObj)

        // Test canAddNotes
        val canAddParams = JSONObject().put("notes", JSONArray().put(noteObj))
        val canAdd = AnkiConnectHandler.handleActionWithCol("canAddNotes", 6, canAddParams, col) as JSONArray
        assertTrue(canAdd.getBoolean(0))

        // Add note
        val noteId = AnkiConnectHandler.handleActionWithCol("addNote", 6, params, col) as Long
        assertTrue(noteId > 0)

        // Duplicate check
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.handleActionWithCol("addNote", 6, params, col)
        }

        // Find note
        val findParams = JSONObject().put("query", "tag:test_tag")
        val foundNotes = AnkiConnectHandler.handleActionWithCol("findNotes", 6, findParams, col) as JSONArray
        assertEquals(1, foundNotes.length())
        assertEquals(noteId, foundNotes.getLong(0))

        // Notes info
        val infoParams = JSONObject().put("notes", JSONArray().put(noteId))
        val notesInfo = AnkiConnectHandler.handleActionWithCol("notesInfo", 6, infoParams, col) as JSONArray
        assertEquals(1, notesInfo.length())
        val noteInfo = notesInfo.getJSONObject(0)
        assertEquals(noteId, noteInfo.getLong("noteId"))
        assertEquals(modelName, noteInfo.getString("modelName"))
    }

    @Test
    fun testMediaSafePathValidation() {
        // Valid filename should succeed
        val validFile = AnkiConnectHandler.getSafeMediaFile(col.media.dir, "test_audio.mp3")
        assertEquals("test_audio.mp3", validFile.name)

        // Path traversal attempts must be rejected
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.getSafeMediaFile(col.media.dir, "../test.txt")
        }
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.getSafeMediaFile(col.media.dir, "subdir/test.txt")
        }
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.getSafeMediaFile(col.media.dir, "..\\test.txt")
        }
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.getSafeMediaFile(col.media.dir, "")
        }
    }

    @Test
    fun testStoreRetrieveAndDeleteMedia() {
        val testContent = "hirameki audio test data"
        val base64Data = Base64.getEncoder().encodeToString(testContent.toByteArray())
        val filename = "test_sound.mp3"

        // Store media
        val storeParams =
            JSONObject()
                .put("filename", filename)
                .put("data", base64Data)
        val storedName = AnkiConnectHandler.handleActionWithCol("storeMediaFile", 6, storeParams, col)
        assertEquals(filename, storedName)

        // Retrieve media
        val retrieveParams = JSONObject().put("filename", filename)
        val retrievedData = AnkiConnectHandler.handleActionWithCol("retrieveMediaFile", 6, retrieveParams, col)
        assertEquals(base64Data, retrievedData)

        // Path traversal in retrieve must fail
        val traversalRetrieveParams = JSONObject().put("filename", "../../../evil.txt")
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.handleActionWithCol("retrieveMediaFile", 6, traversalRetrieveParams, col)
        }

        // Delete media
        val deleteParams = JSONObject().put("filename", filename)
        val deleteResult = AnkiConnectHandler.handleActionWithCol("deleteMediaFile", 6, deleteParams, col)
        assertEquals(null, deleteResult)

        // Path traversal in delete must fail
        val traversalDeleteParams = JSONObject().put("filename", "../../evil.txt")
        assertFailsWith<IllegalArgumentException> {
            AnkiConnectHandler.handleActionWithCol("deleteMediaFile", 6, traversalDeleteParams, col)
        }
    }
}
