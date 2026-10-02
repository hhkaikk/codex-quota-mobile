package com.codex.quota.ui.components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.quota.domain.model.*
import com.codex.quota.ui.util.*

@Composable
fun AccountCard(item: AccountWithUsage, onClick: () -> Unit, onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    val usage = item.usage
    val status = usage?.status ?: item.account.authStatus
    Card(modifier.fillMaxWidth().clickable(onClick = onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.account.nickname, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 1)
                Text(item.account.planType.displayName, style = MaterialTheme.typography.labelSmall)
            }
            QuotaLine("5 小时额度", usage?.fiveHourRemainingPercent, usage?.fiveHourResetAtEpochMs)
            QuotaLine("每周额度", usage?.remainingPercent, usage?.resetAtEpochMs)
            HorizontalDivider()
            Text("账户累计 Token", style = MaterialTheme.typography.labelMedium)
            Text("活动更新：${phoneTime(usage?.activityFetchedAtEpochMs)}", style = MaterialTheme.typography.labelSmall)
            Text(phoneTokens(usage?.usedTokens), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            usage?.activityDate?.let {
                Text("$it（官方接口日期） · ${phoneTokens(usage.activityDateTokens)} Token", style = MaterialTheme.typography.bodySmall)
            }
            Text(status.userMessage, color = if(status.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Text("上次成功更新：${phoneTime(item.account.lastSuccessfulSyncEpochMs)}", style = MaterialTheme.typography.labelSmall)
            if(status == AuthStatus.AUTHENTICATION_REQUIRED) OutlinedButton(onClick = onSignInClick) { Text("重新在官网授权") }
        }
    }
}
@Composable private fun QuotaLine(label: String, remaining: Double?, reset: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("剩余 ${phonePercent(remaining)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        if(remaining != null && remaining.isFinite()) LinearProgressIndicator(
            progress = { (remaining.coerceIn(0.0, 100.0) / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth())
        Text("重置：${phoneTime(reset)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
