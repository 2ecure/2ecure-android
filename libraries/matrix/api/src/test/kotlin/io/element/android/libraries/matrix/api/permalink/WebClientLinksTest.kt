/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.permalink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebClientLinksTest {
    @Test
    fun `user links point at the web client user route`() {
        assertThat(WebClientLinks.fromMatrixTo("https://matrix.to/#/@alice:2ecure.com"))
            .isEqualTo("https://chat.2ecure.com/#/user/@alice:2ecure.com")
        assertThat(WebClientLinks.fromMatrixTo("https://matrix.to/#/%40alice%3A2ecure.com"))
            .isEqualTo("https://chat.2ecure.com/#/user/%40alice%3A2ecure.com")
    }

    @Test
    fun `room, alias and event links point at the web client room route`() {
        assertThat(WebClientLinks.fromMatrixTo("https://matrix.to/#/!room:2ecure.com?via=2ecure.com"))
            .isEqualTo("https://chat.2ecure.com/#/room/!room:2ecure.com?via=2ecure.com")
        assertThat(WebClientLinks.fromMatrixTo("https://matrix.to/#/%23team:2ecure.com"))
            .isEqualTo("https://chat.2ecure.com/#/room/%23team:2ecure.com")
        assertThat(WebClientLinks.fromMatrixTo("https://matrix.to/#/!room:2ecure.com/\$event?via=2ecure.com"))
            .isEqualTo("https://chat.2ecure.com/#/room/!room:2ecure.com/\$event?via=2ecure.com")
    }

    @Test
    fun `other links are unchanged`() {
        assertThat(WebClientLinks.fromMatrixTo("https://example.org/page")).isEqualTo("https://example.org/page")
        assertThat(WebClientLinks.fromMatrixTo("a permalink")).isEqualTo("a permalink")
    }
}
