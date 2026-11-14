// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 the Anki-Android contributors
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.multimedia

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Test

class ReplayButtonBuilderTest {
    @Test
    fun `createReplayButton keeps the shared replay button contract`() {
        val html =
            ReplayButtonBuilder.createReplayButton(
                url = "playsound:q:0",
                contentDescription = ReplayButtonBuilder.TEST_DEFAULT_CONTENT_DESCRIPTION,
                extraClasses = "soundLink",
            )

        assertThat(html, containsString("href=\"playsound:q:0\""))
        assertThat(html, containsString("class=\"replay-button soundLink\""))
        assertThat(html, containsString("role=\"button\""))
        assertThat(html, containsString("class=\"play-action\""))
        assertThat(html, containsString("viewBox=\"256 -768 512 576\""))
        assertThat(html, containsString("fill=\"currentColor\""))
        assertThat(html, not(containsString("replay-button__circle")))
    }

    @Test
    fun `createReplayButton html encodes dynamic attributes and class tokens`() {
        val html =
            ReplayButtonBuilder.createReplayButton(
                url = "playsound:q:0?x=1&y=\"2\"",
                contentDescription = "Replay <audio> & \"again\"",
                extraClasses = "soundLink bad\"class amp&class",
            )

        assertThat(html, containsString("href=\"playsound:q:0?x=1&amp;y=&quot;2&quot;\""))
        assertThat(html, containsString("title=\"Replay &lt;audio&gt; &amp; &quot;again&quot;\""))
        assertThat(
            html,
            containsString("aria-label=\"Replay &lt;audio&gt; &amp; &quot;again&quot;\""),
        )
        assertThat(
            html,
            containsString("class=\"replay-button soundLink bad&quot;class amp&amp;class\""),
        )
    }
}
