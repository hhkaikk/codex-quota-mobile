package com.codex.quota.ui.feature.dashboard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.quota.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DashboardScreen(viewModel: DashboardViewModel, onNavigateToAccountDetail: (String)->Unit,
    onNavigateToAddAccount: ()->Unit, modifier: Modifier = Modifier) {
    val accounts by viewModel.accountsState.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text("Codex 额度") }, actions = {
            TextButton(onClick = viewModel::refreshAll, enabled = !refreshing) { Text(if(refreshing) "更新中…" else "刷新") }
        })
    }, floatingActionButton = {
        FloatingActionButton(onClick = onNavigateToAddAccount) { Text("＋") }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if(accounts.isEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("把官网额度放到桌面", style = MaterialTheme.typography.headlineSmall)
                        Text("直接查询你的 ChatGPT 账户；无需电脑采集器。")
                        Button(onClick = onNavigateToAddAccount) { Text("连接官网账户") }
                    }
                }
            }
            items(accounts, key = { it.account.id }) { item ->
                AccountCard(item, { onNavigateToAccountDetail(item.account.id) }, { onNavigateToAccountDetail(item.account.id) })
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { AnnouncementPanel(refreshing) }
            item { Text("桌面长按空白处 → 小组件 → Codex 额度。默认每 30 分钟尝试更新；系统休眠时可能延后。",
                style = MaterialTheme.typography.bodySmall) }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
