/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.privacy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrivacyChoiceTest {
    @Test
    fun `2ecure - only a JSON boolean counts as a choice, as on the server`() {
        assertThat(PrivacyChoice.parseDiscoverable("""{"discoverable":true}""")).isTrue()
        assertThat(PrivacyChoice.parseDiscoverable("""{"discoverable":false}""")).isFalse()
        assertThat(PrivacyChoice.parseDiscoverable("""{"discoverable":1}""")).isNull()
        assertThat(PrivacyChoice.parseDiscoverable("""{"discoverable":"true"}""")).isNull()
        assertThat(PrivacyChoice.parseDiscoverable("{}")).isNull()
        assertThat(PrivacyChoice.parseDiscoverable("not JSON")).isNull()
    }

    @Test
    fun `2ecure - a written choice reads back the same`() {
        assertThat(PrivacyChoice.parseDiscoverable(PrivacyChoice.json(true))).isTrue()
        assertThat(PrivacyChoice.parseDiscoverable(PrivacyChoice.json(false))).isFalse()
    }
}
