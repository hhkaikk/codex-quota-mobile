package com.codex.quota.ui.feature.addaccount
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(viewModel: AddAccountViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.isSuccess) { if (state.isSuccess) onNavigateBack() }
    DisposableEffect(Unit) { onDispose { viewModel.stopAuthorization() } }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text("连接我的 Codex") }, navigationIcon = {
            TextButton(onClick = onNavigateBack) { Text("返回") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("手机直接查询账户", style = MaterialTheme.typography.headlineSmall)
            Text("在 OpenAI 官网登录并授权。凭据加密保存在本机，电脑关机后仍可查询。")
            OutlinedTextField(value = state.nickname, onValueChange = viewModel::onNicknameChange,
                label = { Text("账户名称（可选）") }, modifier = Modifier.fillMaxWidth())
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isRequestingDeviceCode) { CircularProgressIndicator(); Text("正在获取官网设备码…") }
                    state.deviceSession?.let { session ->
                        Text(session.userCode, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = { viewModel.copyDeviceCode(context); viewModel.openDeviceAuthUrl(context) }) {
                            Text("复制设备码并打开官网")
                        }
                        Text("在浏览器输入上方设备码，确认登录，再回到此应用。此处会自动保存。")
                    }
                    state.deviceStatusMessage?.let { Text(it) }
                    if (state.isLoading) { CircularProgressIndicator(); Text("正在保存登录并查询额度…") }
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { viewModel.initDeviceAuth(true) },
                enabled = !state.isLoading && !state.isRequestingDeviceCode) { Text("重新生成设备码") }
            Text("如果官网禁用了设备码登录：在 ChatGPT 官网 → 设置 → 安全中开启相应 Codex 登录选项，再重试。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
