// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import com.ichi2.anki.common.json.JSONObjectHolder
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.json.JSONObject

fun isJsonEqual(value: JSONObject) = IsJsonEqual(value)

fun isJsonEqual(value: String) = IsJsonEqual(JSONObject(value))

private fun matchesJsonValue(
    expectedValue: JSONObject,
    actualValue: JSONObject,
): Boolean {
    // Checks the objects have the same keys
    if (expectedValue.keys().asSequence().toSet() != actualValue.keys().asSequence().toSet()) {
        return false
    }
    // And that each key have the same associated values in both object.
    for (key in expectedValue.keys()) {
        if (expectedValue[key] != actualValue[key]) {
            return false
        }
    }
    return true
}

// TODO: This doesn't describe the inputs in the correct order
// TODO: This should return the keys which do not match
class IsJsonEqual(
    private val expectedValue: JSONObject,
) : BaseMatcher<JSONObject>() {
    override fun describeTo(description: Description?) {
        description?.appendValue(expectedValue)
    }

    override fun matches(item: Any?): Boolean {
        if (item !is JSONObject) return false
        return matchesJsonValue(expectedValue, item)
    }
}

class IsJsonHolderEqual(
    private val expectedValue: JSONObject,
) : BaseMatcher<JSONObjectHolder>() {
    override fun matches(item: Any?): Boolean {
        if (item !is JSONObjectHolder) return false
        return matchesJsonValue(expectedValue, item.jsonObject)
    }

    override fun describeTo(description: Description?) {
        description?.appendValue(expectedValue)
    }
}

fun isJsonHolderEqual(expectedValue: String) = IsJsonHolderEqual(JSONObject(expectedValue))

private fun jsonObjectOf(vararg pairs: Pair<String, Any>): JSONObject =
    JSONObject().apply {
        for ((key, value) in pairs) {
            put(key, value)
        }
    }
