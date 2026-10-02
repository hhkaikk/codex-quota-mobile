package com.codex.quota.ui.feature.onboarding
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun OnboardingScreen(onComplete:()->Unit,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) {
        Text("Codex 额度",style=MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        Text("手机登录官网，把账户额度、Token 活动和重置时间放在桌面。")
        Spacer(Modifier.height(16.dp))
        Text("凭据由 Android Keystore 加密，保存在本机。账号请求只发往 OpenAI；公告使用独立匿名请求读取第三方社区信息。")
        Spacer(Modifier.height(16.dp))
        Text("默认每 30 分钟尝试刷新。没有数据时显示不可用；这是独立开源工具。")
        Spacer(Modifier.height(28.dp))
        Button(onClick=onComplete) { Text("开始使用") }
    }
}
