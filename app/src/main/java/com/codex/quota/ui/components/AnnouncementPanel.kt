package com.codex.quota.ui.components
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.ui.util.phoneTime

@Composable
fun AnnouncementPanel(refreshKey: Any = Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CodexQuotaApplication
    var cached by remember { mutableStateOf(app.announcements.cached()) }
    LaunchedEffect(refreshKey) { app.announcements.refreshIfDue(); cached = app.announcements.cached() }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tibo · 额外重置公告", style = MaterialTheme.typography.titleSmall)
            val value = cached
            if(value == null) Text("暂无可核对的预告。不会推测下一次重置。", style = MaterialTheme.typography.bodySmall)
            else {
                Text(value.timeLabel, style = MaterialTheme.typography.titleMedium)
                if(System.currentTimeMillis() > value.latestEpochMs)
                    Text("这条预告时间已过；账户是否重置，以官网额度为准。", style = MaterialTheme.typography.bodySmall)
                Text(value.originalText, style = MaterialTheme.typography.bodySmall)
                Text("第三方社区 tibo.cc 读取 · 抓取于 ${phoneTime(value.fetchedAtEpochMs)}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value.sourceUrl))) }
                }) { Text("查看 Tibo 原帖") }
            }
        }
    }
}
