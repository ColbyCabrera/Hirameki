// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.utils.ext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// TODO: move this to the common module
@RunWith(AndroidJUnit4::class) // This is necessary, android and JVM differ on JSONObject.NULL
@Config(application = EmptyApplication::class)
class JSONObjectTest : AndroidTest {
    @Test
    fun `test getStringOrNull`() {
        fun test(value: Any) = JSONObject().apply { put("test", value) }.getStringOrNull("test")

        assertNull(JSONObject().getStringOrNull("test"), message = "{}")
        assertNull(test(JSONObject.NULL), message = "{ test: null }")
        // WARN: this differs between pure JVM and Robolectric/Android
        // On Robolectric/Android, this is {}.
        // On JVM following using the standard implementation it's null.
        assertNotNull(test(JSONObject()), message = "test: { }")
        assertNotNull(test("null"), message = """{ test: "null" }""")
        assertNotNull(test("1"), message = """{ test: "1" }""")
    }
}
