package com.codex.quota.ui.feature.settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codex.quota.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(viewModel: SettingsViewModel, onNavigateToAbout: ()->Unit, modifier: Modifier = Modifier) {
    val prefs by viewModel.preferencesState.collectAsState()
    val context = LocalContext.current
    Scaffold(modifier=modifier.fillMaxSize(),topBar={TopAppBar(title={Text("设置")})}) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("刷新与省电",style=MaterialTheme.typography.titleLarge)
            SettingSwitch("后台更新",prefs.backgroundSyncEnabled) { viewModel.setBackgroundSyncEnabled(context,it) }
            Text("使用系统 WorkManager；不保活、不常驻联网。低电量或休眠时可延后执行。",style=MaterialTheme.typography.bodySmall)
            Text("刷新间隔")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(RefreshIntervalMinutes.MINUTES_30,RefreshIntervalMinutes.HOURS_1,RefreshIntervalMinutes.HOURS_3).forEach {
                    FilterChip(selected=prefs.refreshInterval==it,onClick={viewModel.setRefreshInterval(context,it)},label={Text(it.label)})
                }
            }
            SettingSwitch("打开应用时刷新",prefs.refreshOnAppOpen,viewModel::setRefreshOnAppOpen)
            HorizontalDivider()
            Text("外观",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                AppThemeMode.entries.forEach {
                    val text=when(it){AppThemeMode.SYSTEM->"跟随系统";AppThemeMode.DARK->"深色";AppThemeMode.LIGHT->"浅色"}
                    FilterChip(selected=prefs.themeMode==it,onClick={viewModel.setThemeMode(it)},label={Text(text)})
                }
            }
            SettingSwitch("系统动态配色",prefs.dynamicColor,viewModel::setDynamicColor)
            SettingSwitch("小组件采用系统配色",prefs.widgetThemeMode==WidgetThemeMode.SYSTEM_MATERIAL_YOU) {
                viewModel.setWidgetThemeMode(context,if(it) WidgetThemeMode.SYSTEM_MATERIAL_YOU else WidgetThemeMode.DARK_OBSIDIAN)
            }
            HorizontalDivider()
            Text("提醒",style=MaterialTheme.typography.titleLarge)
            SettingSwitch("登录失效提醒",prefs.signedOutNotificationsEnabled,viewModel::setSignedOutNotificationsEnabled)
            SettingSwitch("低额度提醒",prefs.quotaAlertsEnabled,viewModel::setQuotaAlertsEnabled)
            SettingSwitch("同时提醒 5 小时额度",prefs.includeFiveHourQuotaAlerts,viewModel::setIncludeFiveHourQuotaAlerts)
            Text("提醒阈值")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(5,10,25).forEach {
                    FilterChip(selected=it in prefs.quotaAlertThresholds,onClick={viewModel.toggleQuotaAlertThreshold(it)},label={Text("$it%")})
                }
            }
            Text("提醒需要 Android 通知权限。仅监控数据，不调用模型或使用账户重置卡。",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onNavigateToAbout) { Text("数据来源与开源信息") }
        }
    }
}
@Composable private fun SettingSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,modifier=Modifier.weight(1f).padding(top=12.dp))
        Switch(checked=value,onCheckedChange=onChange)
    }
}
