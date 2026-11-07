/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.permalink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InvitationLinksTest {
    private fun code(text: String) = InvitationLinks.invitationCode(text.toByteArray())

    @Test
    fun `invitation QR codes give their code`() {
        assertThat(code("https://account.2ecure.com/register#code=vGj3hez2sjnk")).isEqualTo("vGj3hez2sjnk")
        assertThat(code("HTTPS://Account.2ecure.com/register/#code=abc%2Ddef")).isEqualTo("abc-def")
        assertThat(code("https://account.2ecure.com/register#lang=de&code=abc123\n")).isEqualTo("abc123")
    }

    @Test
    fun `other QR codes are not invitations`() {
        assertThat(code("https://account.2ecure.com/register")).isNull()
        assertThat(code("https://account.2ecure.com/register#code=")).isNull()
        assertThat(code("https://account.2ecure.com/login#code=abc123")).isNull()
        assertThat(code("https://example.org/register#code=abc123")).isNull()
        assertThat(code("http://account.2ecure.com/register#code=abc123")).isNull()
        assertThat(InvitationLinks.invitationCode(byteArrayOf(0x4D, 0x41, 0x54, 0x52, 0x49, 0x58, 0x03, 0x00, 0xFF.toByte()))).isNull()
    }

    @Test
    fun `the registration URL carries the code in its fragment`() {
        val url = "https://account.2ecure.com/authorize?client_id=abc&prompt=create"
        assertThat(InvitationLinks.withInvitationCode(url, "vGj3hez2sjnk")).isEqualTo("$url#code=vGj3hez2sjnk")
        assertThat(InvitationLinks.withInvitationCode("$url#old", "a b&c")).isEqualTo("$url#code=a%20b%26c")
    }
}
