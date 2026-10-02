package com.codex.quota

import com.codex.quota.auth.*
import com.codex.quota.domain.model.*
import com.codex.quota.domain.usecase.AddAccountUseCase
import com.codex.quota.ui.feature.addaccount.AddAccountViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun manualConfirmation_doesNotManufactureCredentialsOrStartAnotherPoll() = runTest(dispatcher) {
        val add = mockk<AddAccountUseCase>()
        var polls = 0
        val vm = AddAccountViewModel(add, requestCode = { Result.success(DeviceCodeSession("id", "code")) },
            pollCode = { polls++; DevicePollResult.Pending }, autoStart = false)
        vm.initDeviceAuth(); runCurrent()
        repeat(5) { vm.completeDeviceAuthManually() }
        runCurrent()
        assertEquals(0, polls)
        coVerify(exactly = 0) { add(any(), any(), any(), any(), any(), any(), any()) }
        assertFalse(vm.uiState.value.isSuccess)
        vm.stopAuthorization(); runCurrent()
    }
    @Test fun successfulAuthorization_persistsRefreshAndExpiryExactlyOnce() = runTest(dispatcher) {
        val add = mockk<AddAccountUseCase>()
        val account = CodexAccount("a", "我", null, PlanType.PLUS, null, "#10B981",
            AuthStatus.AUTHENTICATED, false, 0, 1, 1)
        var saved: String? = null
        coEvery { add(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            saved = thirdArg(); Result.success(account)
        }
        val vm = AddAccountViewModel(add, requestCode = { Result.success(DeviceCodeSession("id", "code")) },
            pollCode = { DevicePollResult.Success(OAuthTokenResult("access", "rotating-refresh", null, 300, null)) },
            autoStart = false)
        vm.initDeviceAuth(); runCurrent(); advanceTimeBy(5000); runCurrent()
        assertTrue(vm.uiState.value.isSuccess)
        assertEquals("rotating-refresh", OAuthSession.decode(saved!!)!!.refreshToken)
        assertNotNull(OAuthSession.decode(saved!!)!!.expiresAtEpochMs)
        vm.completeDeviceAuthManually(); runCurrent()
        coVerify(exactly = 1) { add(any(), any(), any(), any(), any(), any(), any()) }
        vm.stopAuthorization()
    }
}
