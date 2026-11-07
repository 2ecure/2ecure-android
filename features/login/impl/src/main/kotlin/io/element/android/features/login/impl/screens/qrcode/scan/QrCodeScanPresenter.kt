/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.qrcode.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.login.impl.changeserver.AccountProviderAccessException
import io.element.android.features.login.impl.qrcode.QrCodeLoginManager
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.auth.qrlogin.MatrixQrCodeLoginData
import io.element.android.libraries.matrix.api.auth.qrlogin.MatrixQrCodeLoginDataFactory
import io.element.android.libraries.matrix.api.auth.qrlogin.QrCodeLoginStep
import io.element.android.libraries.matrix.api.auth.qrlogin.QrLoginException
import io.element.android.libraries.matrix.api.permalink.InvitationLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@Inject
class QrCodeScanPresenter(
    private val qrCodeLoginDataFactory: MatrixQrCodeLoginDataFactory,
    private val qrCodeLoginManager: QrCodeLoginManager,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val enterpriseService: EnterpriseService,
) : Presenter<QrCodeScanState> {
    private var isScanning by mutableStateOf(true)
    private var invitationCode by mutableStateOf<String?>(null)

    private val isProcessingCode = AtomicBoolean(false)

    @Composable
    override fun present(): QrCodeScanState {
        val coroutineScope = rememberCoroutineScope()
        val authenticationAction: MutableState<AsyncAction<MatrixQrCodeLoginData>> = remember { mutableStateOf(AsyncAction.Uninitialized) }

        ObserveQRCodeLoginFailures {
            authenticationAction.value = AsyncAction.Failure(it)
        }

        fun handleEvent(event: QrCodeScanEvent) {
            when (event) {
                QrCodeScanEvent.TryAgain -> {
                    isScanning = true
                    authenticationAction.value = AsyncAction.Uninitialized
                }
                is QrCodeScanEvent.QrCodeScanned -> {
                    isScanning = false
                    // 2ecure: the button scans both kinds of code, so an invitation creates the account instead of «Wrong QR code».
                    val code = InvitationLinks.invitationCode(event.code)
                    if (code != null) {
                        Timber.i("Scanned a 2ecure invitation")
                        invitationCode = code
                    } else {
                        coroutineScope.getQrCodeData(authenticationAction, event.code)
                    }
                }
            }
        }

        return QrCodeScanState(
            isScanning = isScanning,
            authenticationAction = authenticationAction.value,
            eventSink = ::handleEvent,
            invitationCode = invitationCode,
        )
    }

    @Composable
    private fun ObserveQRCodeLoginFailures(onQrCodeLoginError: (QrLoginException) -> Unit) {
        LaunchedEffect(onQrCodeLoginError) {
            qrCodeLoginManager.currentLoginStep
                .onEach { state ->
                    if (state is QrCodeLoginStep.Failed) {
                        onQrCodeLoginError(state.error)
                        // The error was handled here, reset the login state
                        qrCodeLoginManager.reset()
                    }
                }
                .launchIn(this)
        }
    }

    private fun CoroutineScope.getQrCodeData(codeScannedAction: MutableState<AsyncAction<MatrixQrCodeLoginData>>, code: ByteArray) {
        if (codeScannedAction.value.isSuccess() || isProcessingCode.compareAndSet(true, true)) return

        launch(coroutineDispatchers.computation) {
            suspend {
                val data = qrCodeLoginDataFactory.parseQrCodeData(code).onFailure {
                    Timber.e(it, "Error parsing QR code data")
                }.getOrThrow()
                assertIsAllowedServer(data)
                data
            }.runCatchingUpdatingState(codeScannedAction)
        }.invokeOnCompletion {
            isProcessingCode.set(false)
        }
    }

    /**
     * 2ecure: a code shown by a device signed in to another server must not sign this app in there. The 2025 version
     * of MSC4108 names the homeserver's base URL (which the login connects to), the 2024 version its server name. A
     * code with neither comes from a device that is not signed in, which the login reports itself.
     */
    @Throws(AccountProviderAccessException::class)
    private suspend fun assertIsAllowedServer(data: MatrixQrCodeLoginData) {
        val server = data.baseUrl() ?: data.serverName()
        if (server.isNullOrBlank()) return
        val accountProvider = AccountProvider.Generic(server)
        if (!enterpriseService.isAllowedToConnectToAccountProvider(accountProvider)) {
            Timber.w("QR code for another server: ${accountProvider.friendlyServerName()}")
            throw AccountProviderAccessException.UnauthorizedAccountProviderException(
                unauthorisedAccountProviderTitle = accountProvider.friendlyServerName(),
                authorisedAccountProviderTitles = enterpriseService.accountProviderAllowList().map { it.friendlyServerName() },
            )
        }
    }
}
