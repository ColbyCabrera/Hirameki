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

import androidx.annotation.WorkerThread
import com.google.protobuf.kotlin.toByteString
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteType
import com.ichi2.anki.syncAuth
import com.ichi2.anki.web.HttpFetcher
import com.ichi2.anki.worker.SyncWorker
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Base64

object AnkiConnectHandler {
    private const val MAX_MEDIA_SIZE = 25 * 1024 * 1024 // 25 MB

    private val httpClient: OkHttpClient by lazy {
        HttpFetcher.getOkHttpBuilder(true).build()
    }

    val SUPPORTED_ACTIONS =
        listOf(
            "version",
            "apiReflect",
            "deckNames",
            "deckNamesAndIds",
            "createDeck",
            "changeDeck",
            "modelNames",
            "modelNamesAndIds",
            "modelFieldNames",
            "modelFieldsOnTemplates",
            "canAddNotes",
            "addNote",
            "addNotes",
            "findNotes",
            "notesInfo",
            "findCards",
            "cardsInfo",
            "storeMediaFile",
            "retrieveMediaFile",
            "deleteMediaFile",
            "sync",
        )

    fun handleAction(
        action: String,
        version: Int = 6,
        params: JSONObject? = null,
    ): Any? {
        if (action == "version") {
            return 6
        }
        if (action == "apiReflect") {
            return JSONObject().put("actions", JSONArray(SUPPORTED_ACTIONS))
        }
        if (action == "sync") {
            val auth = syncAuth() ?: throw IllegalStateException("Not logged in to AnkiWeb")
            SyncWorker.start(AnkiDroidApp.instance, auth, true)
            return null
        }

        // Perform any network downloads OUTSIDE the collection lock to prevent UI thread lockups
        preprocessMediaDownloads(action, params)

        return runBlocking {
            CollectionManager.withCol {
                handleActionWithCol(action, version, params, this)
            }
        }
    }

    private fun preprocessMediaDownloads(
        action: String,
        params: JSONObject?,
    ) {
        if (params == null) return
        when (action) {
            "storeMediaFile" -> {
                val url = params.optString("url", "")
                val data = params.optString("data", "")
                if (data.isBlank() && url.isNotBlank()) {
                    val bytes = downloadMedia(url)
                    params.put("data", Base64.getEncoder().encodeToString(bytes))
                    params.remove("url")
                }
            }
            "addNote" -> {
                val noteObj = params.optJSONObject("note") ?: return
                preprocessNoteMedia(noteObj)
            }
            "addNotes" -> {
                val notesArr = params.optJSONArray("notes") ?: return
                for (i in 0 until notesArr.length()) {
                    val noteObj = notesArr.optJSONObject(i) ?: continue
                    preprocessNoteMedia(noteObj)
                }
            }
        }
    }

    private fun preprocessNoteMedia(noteObj: JSONObject) {
        for (mediaKey in listOf("audio", "picture", "video")) {
            val arr = noteObj.optJSONArray(mediaKey) ?: continue
            for (i in 0 until arr.length()) {
                val mediaItem = arr.optJSONObject(i) ?: continue
                val data = mediaItem.optString("data", "")
                val url = mediaItem.optString("url", "")
                if (data.isBlank() && url.isNotBlank()) {
                    val bytes = downloadMedia(url)
                    mediaItem.put("data", Base64.getEncoder().encodeToString(bytes))
                    mediaItem.remove("url")
                }
            }
        }
    }

    internal fun downloadMedia(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download media from $url: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body from $url")
            val bytes = body.bytes()
            if (bytes.size > MAX_MEDIA_SIZE) {
                throw IOException("Media file exceeds max size of $MAX_MEDIA_SIZE bytes")
            }
            return bytes
        }
    }

    internal fun getSafeMediaFile(
        mediaDir: String,
        filename: String,
    ): File {
        require(filename.isNotBlank()) { "Missing or empty filename" }
        require(!filename.contains("..") && !filename.contains("/") && !filename.contains("\\")) {
            "Filename contains illegal characters or path traversal components: $filename"
        }
        val baseDir = File(mediaDir).canonicalFile
        val targetFile = File(baseDir, filename).canonicalFile
        if (targetFile.parentFile != baseDir) {
            throw SecurityException("Path traversal detected for filename: $filename")
        }
        return targetFile
    }

    @WorkerThread
    fun handleActionWithCol(
        action: String,
        version: Int = 6,
        params: JSONObject? = null,
        col: Collection,
    ): Any? =
        when (action) {
            "version" -> 6
            "apiReflect" -> JSONObject().put("actions", JSONArray(SUPPORTED_ACTIONS))
            "deckNames" -> {
                val names = col.decks.allNamesAndIds().map { it.name }
                JSONArray(names)
            }
            "deckNamesAndIds" -> {
                val result = JSONObject()
                for ((name, id) in col.decks.allNamesAndIds()) {
                    result.put(name, id)
                }
                result
            }
            "createDeck" -> {
                val deckName = params?.optString("deck") ?: throw IllegalArgumentException("Missing deck parameter")
                val op = col.decks.addNormalDeckWithName(deckName)
                op.id
            }
            "changeDeck" -> {
                val deckName = params?.optString("deck") ?: throw IllegalArgumentException("Missing deck parameter")
                val cardsArr = params.optJSONArray("cards") ?: throw IllegalArgumentException("Missing cards parameter")
                val did = col.decks.idForName(deckName) ?: col.decks.addNormalDeckWithName(deckName).id
                val cardIds = mutableListOf<CardId>()
                for (i in 0 until cardsArr.length()) {
                    cardIds.add(cardsArr.getLong(i))
                }
                col.setDeck(cardIds = cardIds, deckId = did)
                null
            }
            "modelNames" -> {
                val names =
                    col.notetypes
                        .allNamesAndIds()
                        .map { it.name }
                        .toList()
                JSONArray(names)
            }
            "modelNamesAndIds" -> {
                val result = JSONObject()
                for (model in col.notetypes.allNamesAndIds()) {
                    result.put(model.name, model.id)
                }
                result
            }
            "modelFieldNames" -> {
                val modelName = params?.optString("modelName") ?: throw IllegalArgumentException("Missing modelName parameter")
                val nt = col.notetypes.byName(modelName) ?: throw IllegalArgumentException("Model not found: $modelName")
                JSONArray(nt.fieldsNames)
            }
            "modelFieldsOnTemplates" -> {
                val modelName = params?.optString("modelName") ?: throw IllegalArgumentException("Missing modelName parameter")
                val nt = col.notetypes.byName(modelName) ?: throw IllegalArgumentException("Model not found: $modelName")
                val result = JSONObject()
                for (tmplName in nt.templatesNames) {
                    result.put(tmplName, JSONArray(nt.fieldsNames))
                }
                result
            }
            "canAddNotes" -> {
                val notesArr = params?.optJSONArray("notes") ?: throw IllegalArgumentException("Missing notes parameter")
                val result = JSONArray()
                for (i in 0 until notesArr.length()) {
                    val noteObj = notesArr.optJSONObject(i)
                    if (noteObj == null) {
                        result.put(false)
                        continue
                    }
                    try {
                        val modelName = noteObj.optString("modelName", "")
                        val nt = col.notetypes.byName(modelName)
                        if (nt == null) {
                            result.put(false)
                            continue
                        }
                        val fieldsObj = noteObj.optJSONObject("fields")
                        val options = noteObj.optJSONObject("options")
                        val allowDuplicate = options?.optBoolean("allowDuplicate", false) ?: false
                        if (allowDuplicate) {
                            result.put(true)
                        } else {
                            val isDupe = isDuplicateNote(col, nt, fieldsObj)
                            result.put(!isDupe)
                        }
                    } catch (_: Exception) {
                        result.put(false)
                    }
                }
                result
            }
            "addNote" -> {
                val noteObj = params?.optJSONObject("note") ?: throw IllegalArgumentException("Missing note parameter")
                createNoteInternal(noteObj, col)
            }
            "addNotes" -> {
                val notesArr = params?.optJSONArray("notes") ?: throw IllegalArgumentException("Missing notes parameter")
                val result = JSONArray()
                for (i in 0 until notesArr.length()) {
                    try {
                        val noteObj = notesArr.getJSONObject(i)
                        val nid = createNoteInternal(noteObj, col)
                        result.put(nid)
                    } catch (e: Exception) {
                        Timber.w(e, "AnkiConnect: Failed to add note at index %d", i)
                        result.put(JSONObject.NULL)
                    }
                }
                result
            }
            "findNotes" -> {
                val query = params?.optString("query") ?: throw IllegalArgumentException("Missing query parameter")
                val nids = col.findNotes(query)
                val result = JSONArray()
                for (nid in nids) {
                    result.put(nid)
                }
                result
            }
            "notesInfo" -> {
                val notesArr = params?.optJSONArray("notes") ?: throw IllegalArgumentException("Missing notes parameter")
                val result = JSONArray()
                for (i in 0 until notesArr.length()) {
                    val nid = notesArr.getLong(i)
                    try {
                        val note = col.getNote(nid)
                        val info = JSONObject()
                        info.put("noteId", note.id)
                        info.put("modelName", note.notetype.name)
                        info.put("tags", JSONArray(note.tags))
                        info.put("mod", note.mod)
                        val fieldsObj = JSONObject()
                        for ((index, fieldName) in note.notetype.fieldsNames.withIndex()) {
                            val fObj = JSONObject()
                            fObj.put("value", if (index < note.fields.size) note.fields[index] else "")
                            fObj.put("order", index)
                            fieldsObj.put(fieldName, fObj)
                        }
                        info.put("fields", fieldsObj)
                        result.put(info)
                    } catch (e: Exception) {
                        Timber.d(e, "AnkiConnect: note not found for id %d", nid)
                    }
                }
                result
            }
            "findCards" -> {
                val query = params?.optString("query") ?: throw IllegalArgumentException("Missing query parameter")
                val cids = col.findCards(query)
                val result = JSONArray()
                for (cid in cids) {
                    result.put(cid)
                }
                result
            }
            "cardsInfo" -> {
                val cardsArr = params?.optJSONArray("cards") ?: throw IllegalArgumentException("Missing cards parameter")
                val result = JSONArray()
                for (i in 0 until cardsArr.length()) {
                    val cid = cardsArr.getLong(i)
                    try {
                        val card = col.getCard(cid)
                        val note = col.getNote(card.nid)
                        val info = JSONObject()
                        info.put("cardId", card.id)
                        info.put("note", card.nid)
                        info.put("deckName", col.decks.name(card.did))
                        info.put("modelName", note.notetype.name)
                        info.put("ord", card.ord)
                        val render =
                            try {
                                card.renderOutput(col)
                            } catch (_: Exception) {
                                null
                            }
                        info.put("question", render?.questionText ?: "")
                        info.put("answer", render?.answerText ?: "")
                        val fieldsObj = JSONObject()
                        for ((index, fieldName) in note.notetype.fieldsNames.withIndex()) {
                            val fObj = JSONObject()
                            fObj.put("value", if (index < note.fields.size) note.fields[index] else "")
                            fObj.put("order", index)
                            fieldsObj.put(fieldName, fObj)
                        }
                        info.put("fields", fieldsObj)
                        result.put(info)
                    } catch (e: Exception) {
                        Timber.d(e, "AnkiConnect: card not found for id %d", cid)
                    }
                }
                result
            }
            "storeMediaFile" -> {
                val filename = params?.optString("filename") ?: throw IllegalArgumentException("Missing filename parameter")
                val data = params.optString("data", "")
                saveMedia(col, filename, data)
                filename
            }
            "retrieveMediaFile" -> {
                val filename = params?.optString("filename") ?: throw IllegalArgumentException("Missing filename parameter")
                val file = getSafeMediaFile(col.media.dir, filename)
                if (file.exists() && file.isFile) {
                    Base64.getEncoder().encodeToString(file.readBytes())
                } else {
                    false
                }
            }
            "deleteMediaFile" -> {
                val filename = params?.optString("filename") ?: throw IllegalArgumentException("Missing filename parameter")
                getSafeMediaFile(col.media.dir, filename)
                col.media.trashFiles(listOf(filename))
                null
            }
            else -> throw IllegalArgumentException("unsupported action: $action")
        }

    private fun isDuplicateNote(
        col: Collection,
        nt: NoteType,
        fieldsObj: JSONObject?,
    ): Boolean {
        if (fieldsObj == null) return false
        val firstFieldName = nt.fieldsNames.firstOrNull() ?: return false
        val firstFieldVal = fieldsObj.optString(firstFieldName, "")
        if (firstFieldVal.isBlank()) return false

        val escapedVal = firstFieldVal.replace("\\", "\\\\").replace("\"", "\\\"")
        val query = "\"mid:${nt.id}\" \"$firstFieldName:$escapedVal\""
        val dupes = col.findNotes(query)
        return dupes.isNotEmpty()
    }

    private fun createNoteInternal(
        noteObj: JSONObject,
        col: Collection,
    ): Long {
        val modelName = noteObj.getString("modelName")
        val deckName = noteObj.optString("deckName", "")
        val nt = col.notetypes.byName(modelName) ?: throw IllegalArgumentException("Model not found: $modelName")
        val did =
            if (deckName.isNotBlank()) {
                col.decks.idForName(deckName) ?: col.decks.addNormalDeckWithName(deckName).id
            } else {
                nt.did
            }

        val note = col.newNote(nt)
        val fieldsObj = noteObj.optJSONObject("fields") ?: JSONObject()

        // Handle audio attachments
        val audioArr = noteObj.optJSONArray("audio")
        if (audioArr != null) {
            for (i in 0 until audioArr.length()) {
                val audioObj = audioArr.getJSONObject(i)
                val filename = audioObj.optString("filename", "")
                if (filename.isNotBlank()) {
                    saveMedia(col, filename, audioObj.optString("data", ""))
                    val targetFields = audioObj.optJSONArray("fields")
                    if (targetFields != null) {
                        for (j in 0 until targetFields.length()) {
                            val f = targetFields.getString(j)
                            val curr = fieldsObj.optString(f, "")
                            fieldsObj.put(f, if (curr.isEmpty()) "[sound:$filename]" else "$curr [sound:$filename]")
                        }
                    }
                }
            }
        }

        // Handle picture attachments
        val picArr = noteObj.optJSONArray("picture")
        if (picArr != null) {
            for (i in 0 until picArr.length()) {
                val picObj = picArr.getJSONObject(i)
                val filename = picObj.optString("filename", "")
                if (filename.isNotBlank()) {
                    saveMedia(col, filename, picObj.optString("data", ""))
                    val targetFields = picObj.optJSONArray("fields")
                    if (targetFields != null) {
                        for (j in 0 until targetFields.length()) {
                            val f = targetFields.getString(j)
                            val curr = fieldsObj.optString(f, "")
                            fieldsObj.put(f, if (curr.isEmpty()) "<img src=\"$filename\">" else "$curr <img src=\"$filename\">")
                        }
                    }
                }
            }
        }

        for (fieldName in nt.fieldsNames) {
            val value = fieldsObj.optString(fieldName, "")
            note.setItem(fieldName, value)
        }

        val tagsArr = noteObj.optJSONArray("tags")
        if (tagsArr != null) {
            val tagsList = mutableListOf<String>()
            for (i in 0 until tagsArr.length()) {
                tagsList.add(tagsArr.getString(i))
            }
            note.setTagsFromStr(col, tagsList.joinToString(" "))
        }

        val options = noteObj.optJSONObject("options")
        val allowDuplicate = options?.optBoolean("allowDuplicate", false) ?: false
        if (!allowDuplicate && isDuplicateNote(col, nt, fieldsObj)) {
            throw IllegalArgumentException("cannot create note because it is a duplicate")
        }

        col.addNote(note, did)
        return note.id
    }

    internal fun saveMedia(
        col: Collection,
        filename: String,
        data: String,
    ) {
        getSafeMediaFile(col.media.dir, filename)
        if (data.isNotBlank()) {
            val bytes = Base64.getDecoder().decode(data)
            col.backend.addMediaFile(filename, bytes.toByteString())
        }
    }
}
