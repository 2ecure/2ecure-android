/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.qrcode.scan

import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.changeserver.AccountProviderAccessException
import io.element.android.features.login.impl.qrcode.FakeQrCodeLoginManager
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.auth.qrlogin.QrCodeLoginStep
import io.element.android.libraries.matrix.api.auth.qrlogin.QrLoginException
import io.element.android.libraries.matrix.test.auth.qrlogin.FakeMatrixQrCodeLoginData
import io.element.android.libraries.matrix.test.auth.qrlogin.FakeMatrixQrCodeLoginDataFactory
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QrCodeScanPresenterTest {
    @Test
    fun `present - initial state`() = runTest {
        val presenter = createQrCodeScanPresenter()
        presenter.test {
            awaitItem().run {
                assertThat(isScanning).isTrue()
                assertThat(authenticationAction.isUninitialized()).isTrue()
            }
        }
    }

    @Test
    fun `present - scanned QR code successfully`() = runTest {
        val qrCodeLoginDataFactory = FakeMatrixQrCodeLoginDataFactory(
            parseQrCodeLoginDataResult = {
                Result.success(
                    FakeMatrixQrCodeLoginData(
                        serverNameResult = { "example.com" }
                    )
                )
            }
        )
        val presenter = createQrCodeScanPresenter(
            qrCodeLoginDataFactory = qrCodeLoginDataFactory,
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(QrCodeScanEvent.QrCodeScanned(byteArrayOf()))
            assertThat(awaitItem().isScanning).isFalse()
            assertThat(awaitItem().authenticationAction.isLoading()).isTrue()
            assertThat(awaitItem().authenticationAction.isSuccess()).isTrue()
        }
    }

    @Test
    fun `present - scanned QR code failed and can be retried`() = runTest {
        val qrCodeLoginDataFactory = FakeMatrixQrCodeLoginDataFactory(
            parseQrCodeLoginDataResult = { Result.failure(Exception("Failed to parse QR code")) }
        )
        val presenter = createQrCodeScanPresenter(qrCodeLoginDataFactory = qrCodeLoginDataFactory)
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(QrCodeScanEvent.QrCodeScanned(byteArrayOf()))
            assertThat(awaitItem().isScanning).isFalse()
            assertThat(awaitItem().authenticationAction.isLoading()).isTrue()

            val errorState = awaitItem()
            assertThat(errorState.authenticationAction.isFailure()).isTrue()

            errorState.eventSink(QrCodeScanEvent.TryAgain)
            assertThat(awaitItem().isScanning).isTrue()
            assertThat(awaitItem().authenticationAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - login failed with so we display the error and recover from it`() = runTest {
        val qrCodeLoginDataFactory = FakeMatrixQrCodeLoginDataFactory()
        val qrCodeLoginManager = FakeQrCodeLoginManager()
        val resetAction = lambdaRecorder<Unit> {
            qrCodeLoginManager.currentLoginStep.value = QrCodeLoginStep.Uninitialized
        }
        qrCodeLoginManager.resetAction = resetAction
        val presenter = createQrCodeScanPresenter(qrCodeLoginDataFactory = qrCodeLoginDataFactory, qrCodeLoginManager = qrCodeLoginManager)
        presenter.test {
            // Skip initial item
            skipItems(1)

            qrCodeLoginManager.currentLoginStep.value = QrCodeLoginStep.Failed(QrLoginException.OtherDeviceNotSignedIn)

            val errorState = awaitItem()
            // The state for this screen is failure
            assertThat(errorState.authenticationAction.isFailure()).isTrue()
            // However, the QrCodeLoginManager is reset
            resetAction.assertions().isCalledOnce()
            assertThat(qrCodeLoginManager.currentLoginStep.value).isEqualTo(QrCodeLoginStep.Uninitialized)
        }
    }

    @Test
    fun `present - QR code from a device signed in to another server is refused`() = runTest {
        val presenter = createQrCodeScanPresenter(
            qrCodeLoginDataFactory = qrCodeLoginDataFactory(FakeMatrixQrCodeLoginData(serverNameResult = { "matrix.org" })),
            enterpriseService = twoecureOnlyEnterpriseService(),
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(QrCodeScanEvent.QrCodeScanned(byteArrayOf()))
            assertThat(awaitItem().isScanning).isFalse()
            assertThat(awaitItem().authenticationAction.isLoading()).isTrue()
            val errorState = awaitItem()
            assertThat(errorState.authenticationAction.errorOrNull()).isEqualTo(
                AccountProviderAccessException.UnauthorizedAccountProviderException(
                    unauthorisedAccountProviderTitle = "matrix.org",
                    authorisedAccountProviderTitles = listOf("2ecure.com"),
                )
            )
            errorState.eventSink(QrCodeScanEvent.TryAgain)
            assertThat(awaitItem().isScanning).isTrue()
            assertThat(awaitItem().authenticationAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - the base URL of a QR code decides over its server name`() = runTest {
        val presenter = createQrCodeScanPresenter(
            qrCodeLoginDataFactory = qrCodeLoginDataFactory(
                FakeMatrixQrCodeLoginData(serverNameResult = { "2ecure.com" }, baseUrlResult = { "https://matrix-client.matrix.org/" })
            ),
            enterpriseService = twoecureOnlyEnterpriseService(),
        )
        presenter.test {
            awaitItem().eventSink(QrCodeScanEvent.QrCodeScanned(byteArrayOf()))
            skipItems(2)
            assertThat(awaitItem().authenticationAction.errorOrNull()).isEqualTo(
                AccountProviderAccessException.UnauthorizedAccountProviderException(
                    unauthorisedAccountProviderTitle = "matrix-client.matrix.org",
                    authorisedAccountProviderTitles = listOf("2ecure.com"),
                )
            )
        }
    }

    @Test
    fun `present - QR code from a device signed in to 2ecure is accepted by server name or base URL`() = runTest {
        listOf(
            FakeMatrixQrCodeLoginData(serverNameResult = { "2ecure.com" }),
            FakeMatrixQrCodeLoginData(baseUrlResult = { "https://matrix.2ecure.com/" }),
        ).forEach { data ->
            val presenter = createQrCodeScanPresenter(
                qrCodeLoginDataFactory = qrCodeLoginDataFactory(data),
                enterpriseService = twoecureOnlyEnterpriseService(),
            )
            presenter.test {
                awaitItem().eventSink(QrCodeScanEvent.QrCodeScanned(byteArrayOf()))
                skipItems(2)
                assertThat(awaitItem().authenticationAction.isSuccess()).isTrue()
            }
        }
    }

    @Test
    fun `present - QR code without a server is left to the login`() = runTest {
        val presenter = createQrCodeScanPresenter(
            qrCodeLoginDataFactory = qrCodeLoginDataFactory(FakeMatrixQrCodeLoginData(serverNameResult = { null })),
            enterpriseService = FakeEnterpriseService(),
        )
        presenter.test {
            awaitItem().eventSink(QrCodeScanEvent.QrCodeScanned(byteArrayOf()))
            skipItems(2)
            assertThat(awaitItem().authenticationAction.isSuccess()).isTrue()
        }
    }

    private fun qrCodeLoginDataFactory(data: FakeMatrixQrCodeLoginData) = FakeMatrixQrCodeLoginDataFactory(
        parseQrCodeLoginDataResult = { Result.success(data) }
    )

    // Like 2ecure's DefaultEnterpriseService: one account provider, reachable by its server name or its base URL.
    private fun twoecureOnlyEnterpriseService(): FakeEnterpriseService {
        val twoecure = AccountProvider.Managed(serverName = "2ecure.com", baseUrl = "https://matrix.2ecure.com", canUseServerName = true)
        return FakeEnterpriseService(
            accountProviderAllowListResult = { listOf(twoecure) },
            isAllowedToConnectToAccountProviderResult = {
                it.matches(twoecure) || it.matches(AccountProvider.Generic(twoecure.baseUrl))
            },
        )
    }

    private fun TestScope.createQrCodeScanPresenter(
        qrCodeLoginDataFactory: FakeMatrixQrCodeLoginDataFactory = FakeMatrixQrCodeLoginDataFactory(),
        coroutineDispatchers: CoroutineDispatchers = testCoroutineDispatchers(),
        qrCodeLoginManager: FakeQrCodeLoginManager = FakeQrCodeLoginManager(),
        enterpriseService: FakeEnterpriseService = FakeEnterpriseService(isAllowedToConnectToAccountProviderResult = { true }),
    ) = QrCodeScanPresenter(
        qrCodeLoginDataFactory = qrCodeLoginDataFactory,
        qrCodeLoginManager = qrCodeLoginManager,
        coroutineDispatchers = coroutineDispatchers,
        enterpriseService = enterpriseService,
    )
}
