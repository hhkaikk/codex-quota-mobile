package com.codex.quota.ui.feature.addaccount
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.quota.auth.*
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.usecase.AddAccountUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AddAccountUiState(val nickname: String = "", val isLoading: Boolean = false,
    val errorMessage: String? = null, val isSuccess: Boolean = false, val createdAccount: CodexAccount? = null,
    val deviceSession: DeviceCodeSession? = null, val isRequestingDeviceCode: Boolean = false,
    val isPollingDeviceCode: Boolean = false, val deviceCodeCopied: Boolean = false, val deviceStatusMessage: String? = null)

class AddAccountViewModel(
    private val addAccountUseCase: AddAccountUseCase,
    private val requestCode: suspend () -> Result<DeviceCodeSession> = { DeviceCodeManager.requestDeviceCode() },
    private val pollCode: suspend (DeviceCodeSession) -> DevicePollResult = { DeviceCodeManager.pollDeviceToken(it) },
    autoStart: Boolean = true,
    private val reauthenticate: (suspend (OAuthTokenResult) -> Result<CodexAccount>)? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState = _uiState.asStateFlow()
    private var authJob: Job? = null
    private var generation = 0
    init { if (autoStart) initDeviceAuth(true) }
    fun onNicknameChange(value: String) { _uiState.update { it.copy(nickname = value) } }
    fun initDeviceAuth(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isSuccess) return
        if (!forceRefresh && _uiState.value.deviceSession != null) return
        authJob?.cancel()
        val current = ++generation
        authJob = viewModelScope.launch {
            _uiState.update { it.copy(deviceSession = null, isRequestingDeviceCode = true,
                isPollingDeviceCode = false, deviceCodeCopied = false, errorMessage = null) }
            val result = requestCode()
            ensureActive()
            if (current != generation) return@launch
            result.onFailure { _uiState.update { it.copy(isRequestingDeviceCode = false, errorMessage = "无法生成设备码。请检查网络；官网安全设置中需允许 Codex 设备码登录。") } }
            val session = result.getOrNull() ?: return@launch
            _uiState.update { it.copy(deviceSession = session, isRequestingDeviceCode = false, isPollingDeviceCode = true,
                deviceStatusMessage = "请在官网输入设备码，完成后回到此处") }
            var interval = session.intervalSeconds.coerceAtLeast(5) * 1000L
            try {
                while (System.currentTimeMillis() - session.createdAtEpochMs < session.expiresInSeconds * 1000L) {
                    delay(interval)
                    val poll = pollCode(session)
                    ensureActive()
                    if (current != generation) return@launch
                    when(poll) {
                        is DevicePollResult.Success -> { saveTokenAccount(poll.tokenResult); return@launch }
                        is DevicePollResult.Pending -> Unit
                        is DevicePollResult.SlowDown -> interval = (interval + 5000).coerceAtMost(60000)
                        is DevicePollResult.Expired -> break
                        is DevicePollResult.Error -> {
                            _uiState.update { it.copy(errorMessage = poll.message) }; return@launch
                        }
                    }
                }
                _uiState.update { it.copy(deviceStatusMessage = "设备码已过期，请重新生成") }
            } finally { if (current == generation) _uiState.update { it.copy(isPollingDeviceCode = false) } }
        }
    }
    // Manual action never polls concurrently or manufactures a session.
    fun completeDeviceAuthManually() {
        _uiState.update { it.copy(deviceStatusMessage = "正在等待官网确认，授权成功后会自动保存") }
    }
    fun copyDeviceCode(context: Context) {
        _uiState.value.deviceSession?.let { DeviceCodeManager.copyToClipboard(context, it.userCode) }
        _uiState.update { it.copy(deviceCodeCopied = true) }
    }
    fun openDeviceAuthUrl(context: Context) {
        runCatching { DeviceCodeManager.openBrowser(context) }.onFailure {
            _uiState.update { it.copy(errorMessage = "无法打开浏览器，请手动访问 auth.openai.com/codex/device") }
        }
    }
    private suspend fun saveTokenAccount(token: OAuthTokenResult) {
        _uiState.update { it.copy(isLoading = true, isPollingDeviceCode = false) }
        val info = token.decodedInfo
        val result = reauthenticate?.invoke(token) ?: addAccountUseCase(
            nickname = _uiState.value.nickname.ifBlank { info?.email?.substringBefore('@') ?: "我的 Codex" },
            email = info?.email, apiKey = OAuthSession.from(token).encode(),
            planType = info?.planType ?: com.codex.quota.domain.model.PlanType.UNKNOWN,
            organizationId = JwtTokenParser.parseToken(token.accessToken)?.chatgptAccountId,
            colorHex = "#10B981", isDemoAccount = false)
        _uiState.update { it.copy(isLoading = false, isSuccess = result.isSuccess,
            createdAccount = result.getOrNull(), errorMessage = if (result.isFailure) "无法保存登录，请重试" else null) }
    }
    fun handleOAuthCallbackUri(uri: Uri) {
        _uiState.update { it.copy(errorMessage = "仅支持官网设备码授权，请重新生成设备码") }
    }
    fun stopAuthorization() { generation++; authJob?.cancel() }
    override fun onCleared() { stopAuthorization(); super.onCleared() }
}
