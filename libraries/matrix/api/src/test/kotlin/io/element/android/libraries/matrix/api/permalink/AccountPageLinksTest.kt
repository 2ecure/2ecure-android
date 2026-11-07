/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.permalink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountPageLinksTest {
    @Test
    fun `account pages are marked with the app's account`() {
        assertThat(AccountPageLinks.appUrl("https://account.2ecure.com/account/", "@anna:2ecure.com"))
            .isEqualTo("https://account.2ecure.com/account/#2ecure-app=android&user=%40anna%3A2ecure.com")
        assertThat(AccountPageLinks.appUrl("https://account.2ecure.com/account/?action=org.matrix.profile", "@a.b_c=d/e+f-g:2ecure.com"))
            .isEqualTo("https://account.2ecure.com/account/?action=org.matrix.profile#2ecure-app=android&user=%40a.b_c%3Dd%2Fe%2Bf-g%3A2ecure.com")
    }

    @Test
    fun `other pages and links with a fragment are unchanged`() {
        assertThat(AccountPageLinks.appUrl("https://example.org/account/", "@anna:2ecure.com")).isEqualTo("https://example.org/account/")
        assertThat(AccountPageLinks.appUrl("https://account.2ecure.com/account/#x", "@anna:2ecure.com")).isEqualTo("https://account.2ecure.com/account/#x")
    }

    @Test
    fun `only the sign-out hand-back counts`() {
        assertThat(AccountPageLinks.isSignedOutLink("com.2ecure:/signed-out")).isTrue()
        assertThat(AccountPageLinks.isSignedOutLink("com.2ecure.debug:/signed-out")).isTrue()
        assertThat(AccountPageLinks.isSignedOutLink("com.2ecure:/?code=abc&state=def")).isFalse()
        assertThat(AccountPageLinks.isSignedOutLink("com.2ecure:/signed-out?x=1")).isFalse()
        assertThat(AccountPageLinks.isSignedOutLink("other.app:/signed-out")).isFalse()
    }
}
