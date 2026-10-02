package com.codex.quota.ui.feature.accountdetail
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.quota.ui.components.*
import com.codex.quota.ui.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AccountDetailScreen(viewModel: AccountDetailViewModel, onNavigateBack: ()->Unit,
    modifier: Modifier = Modifier, onReauthenticate: ()->Unit = {}) {
    val item by viewModel.accountState.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val deleted by viewModel.accountDeleted.collectAsState()
    val message by viewModel.uiMessage.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var nickname by remember(item?.account?.nickname) { mutableStateOf(item?.account?.nickname.orEmpty()) }
    LaunchedEffect(deleted) { if(deleted) onNavigateBack() }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text("账户详情") }, navigationIcon = { TextButton(onClick=onNavigateBack) { Text("返回") } },
            actions = { TextButton(onClick = viewModel::refresh, enabled = !refreshing) { Text(if(refreshing) "更新中…" else "刷新") } })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item?.let { data ->
                AccountCard(data, {}, onReauthenticate)
                Text("累计 Token 精确值：${data.usage?.usedTokens?.toString() ?: "不可用"}")
                Text("活动最后获取：${phoneTime(data.usage?.activityFetchedAtEpochMs)}", style = MaterialTheme.typography.bodySmall)
                Text("活动数据统计时间：${data.usage?.activityStatsAsOf ?: "接口未提供"}", style = MaterialTheme.typography.bodySmall)
                Text("日用量保留官方接口日期，未把它换算为手机的“今日”。累计 Token 与订阅额度百分比是不同统计。", style = MaterialTheme.typography.bodySmall)
                AnnouncementPanel(refreshing)
                Button(onClick = onReauthenticate, modifier = Modifier.fillMaxWidth()) { Text("重新在官网授权") }
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("修改账户名称") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("移除账户及本机凭据") }
            } ?: Text("账户不存在或正在加载")
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest = { confirmDelete=false }, title = { Text("移除账户？") },
        text = { Text("删除本机保存的登录凭据和缓存，不影响官网账户。") },
        confirmButton = { TextButton(onClick = { confirmDelete=false; viewModel.deleteAccount() }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { confirmDelete=false }) { Text("取消") } })
    if(editing) AlertDialog(onDismissRequest={editing=false}, title={Text("账户名称")},
        text={OutlinedTextField(value=nickname,onValueChange={nickname=it},singleLine=true)},
        confirmButton={TextButton(onClick={ item?.let { viewModel.updateAccountDetails(nickname,it.account.colorHex,null) };editing=false }) { Text("保存") }},
        dismissButton={TextButton(onClick={editing=false}) { Text("取消") }})
}
