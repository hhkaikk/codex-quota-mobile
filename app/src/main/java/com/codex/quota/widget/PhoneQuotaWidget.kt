package com.codex.quota.widget
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.work.*
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.announcement.ResetAnnouncement
import com.codex.quota.domain.model.*
import com.codex.quota.ui.MainActivity
import com.codex.quota.ui.util.*
import com.codex.quota.worker.QuotaRefreshWorker

open class PhoneQuotaWidget(private val compact: Boolean) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CodexQuotaApplication
        val accounts = app.repository.getAllAccounts()
        val data = accounts.firstOrNull()
        val prefs = app.preferencesRepository.getPreferences()
        val announcement = app.announcements.cached()
        provideContent { GlanceTheme { Content(context, data, prefs.widgetThemeMode, announcement) } }
    }
    @Composable private fun Content(context: Context, data: AccountWithUsage?, mode: WidgetThemeMode, announcement: ResetAnnouncement?) {
        val colors = WidgetThemeHelper.getColors(mode)
        val size = LocalSize.current
        val dense = compact || size.width < 230.dp
        val usableHeight = size.height.value / context.resources.configuration.fontScale.coerceAtLeast(1f)
        val showReset = usableHeight >= 140
        val showTokens = usableHeight >= 160
        val expanded = !dense && usableHeight >= 235
        val usage = data?.usage
        val intent = if (data == null) Intent(context, MainActivity::class.java) else
            Intent(Intent.ACTION_VIEW, Uri.parse("codexquota://account/${data.account.id}"), context, MainActivity::class.java)
        if (!compact) {
            HorizontalContent(context, data, colors, announcement, intent)
            return
        }
        Column(GlanceModifier.fillMaxSize().background(colors.background).cornerRadius(22.dp).padding(if(dense) 12.dp else 16.dp)
            .clickable(actionStartActivity(intent))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Codex", modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Text("↻", modifier = GlanceModifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<RefreshQuotaAction>()),
                    style = TextStyle(color = colors.accentBlue, fontSize = 18.sp))
            }
            if (data == null) {
                Text("点击连接官网账户", style = TextStyle(color = colors.textSecondary, fontSize = 12.sp))
            } else {
                Column {
                    QuotaRow("5 小时", usage?.fiveHourRemainingPercent, usage?.fiveHourResetAtEpochMs, colors, dense, showReset)
                    Spacer(GlanceModifier.height(8.dp))
                    QuotaRow("每周", usage?.remainingPercent, usage?.resetAtEpochMs, colors, dense, showReset)
                }
                Spacer(GlanceModifier.height(8.dp))
                if (showTokens) Column {
                    Text("累计 Token  ${phoneTokens(usage?.usedTokens)}", style = TextStyle(color = colors.textPrimary, fontSize = if(dense) 11.sp else 13.sp))
                    if (expanded)
                        Text("活动更新 ${phoneTime(usage?.activityFetchedAtEpochMs)}",
                            style = TextStyle(color = colors.textSecondary, fontSize = 10.sp))
                }
                if (expanded) {
                    Spacer(GlanceModifier.height(8.dp))
                    Column {
                        val expired = announcement?.let { System.currentTimeMillis() > it.latestEpochMs } ?: false
                        Text(if(announcement == null) "额外重置：暂无已核对预告" else if(expired) "Tibo 预告已过期，等待新公告" else "额外重置  ${announcement.timeLabel}",
                            maxLines = 2, style = TextStyle(color = colors.accentBlue, fontSize = 11.sp))
                        Text("社区读取 · 点击查看原文", maxLines = 1,
                            modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                            style = TextStyle(color = colors.textMuted, fontSize = 9.sp))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                val status = usage?.status ?: data.account.authStatus
                val label = when(status) {
                    AuthStatus.AUTHENTICATED -> "更新 ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}"
                    AuthStatus.AUTHENTICATION_REQUIRED -> "登录失效 · 点击重新授权"
                    AuthStatus.OFFLINE -> "离线缓存 ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}"
                    else -> "查询失败 · ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}"
                }
                Text(label, maxLines = 1, style = TextStyle(color = if(status.isError) colors.error else colors.textMuted, fontSize = 9.sp))
            }
        }
    }
    @Composable private fun HorizontalContent(
        context: Context, data: AccountWithUsage?, colors: WidgetColors, announcement: ResetAnnouncement?, intent: Intent
    ) {
        val size = LocalSize.current
        val palette = listOf(colors.background, colors.textPrimary, colors.textSecondary, colors.textMuted,
            colors.accent, colors.accentBlue, colors.progressTrack, colors.error).map { it.getColor(context).toArgb() }
        val typography = WidgetTypography.rounded(context)
        val bitmap = ReferenceWidgetBitmap.render(size.width.value, size.height.value,
            context.resources.displayMetrics.density, context.resources.configuration.fontScale,
            data, palette, announcement?.let { horizontalAnnouncement(it) }, typography.first, typography.second)
        val usage = data?.usage
        val description = if (data == null) "Codex 额度，点击连接官网账户" else
            "Codex，5小时剩余 ${phonePercent(usage?.fiveHourRemainingPercent)}，重置 ${phoneTime(usage?.fiveHourResetAtEpochMs)}，" +
            "7天剩余 ${phonePercent(usage?.remainingPercent)}，重置 ${phoneTime(usage?.resetAtEpochMs)}，" +
            "累计Token ${phoneTokens(usage?.usedTokens)}，活动截至 ${usage?.activityDate ?: "不可用"}，" +
            "更新 ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}。额外重置 ${horizontalAnnouncement(announcement)}。点击查看账户"
        Box(GlanceModifier.fillMaxSize()) {
            Image(provider = ImageProvider(bitmap), contentDescription = description,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)))
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Text("↻", modifier = GlanceModifier.padding(horizontal = 14.dp, vertical = if (size.height < 140.dp) 8.dp else 14.dp)
                    .clickable(actionRunCallback<RefreshQuotaAction>()),
                    style = TextStyle(color = colors.textSecondary, fontSize = 14.sp))
            }
        }
    }
    private fun horizontalAnnouncement(value: ResetAnnouncement?): String {
        if(value == null) return "暂无已核对预告"
        if(System.currentTimeMillis() > value.latestEpochMs) return "预告已过期"
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val first = java.time.Instant.ofEpochMilli(value.earliestEpochMs).atZone(zone)
        val last = java.time.Instant.ofEpochMilli(value.latestEpochMs).atZone(zone)
        val format = java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm")
        val start = first.format(format)
        val end = if(value.earliestEpochMs == value.latestEpochMs) "" else "–" + last.format(
            java.time.format.DateTimeFormatter.ofPattern(if(first.toLocalDate() == last.toLocalDate()) "HH:mm" else "M/d HH:mm"))
        return start + end + " 北京"
    }
    @Composable private fun QuotaRow(label: String, remaining: Double?, reset: Long?, colors: WidgetColors, dense: Boolean, showReset: Boolean) {
        Column {
            Row(GlanceModifier.fillMaxWidth()) {
                Text(label, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = colors.textSecondary, fontSize = 11.sp))
                Text("余 ${phonePercent(remaining)}", style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            if (remaining != null && remaining.isFinite()) LinearProgressIndicator(
                progress = (remaining.coerceIn(0.0,100.0) / 100.0).toFloat(), modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = colors.accent, backgroundColor = colors.progressTrack)
            if (showReset) Text("重置 ${phoneTime(reset)}", style = TextStyle(color = colors.textMuted, fontSize = if(dense) 9.sp else 10.sp))
        }
    }
}
class RefreshQuotaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val work = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
            .addTag("manual_quota_refresh")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("manual_quota_refresh", ExistingWorkPolicy.KEEP, work)
    }
}
