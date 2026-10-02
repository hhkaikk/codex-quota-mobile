package com.codex.quota.ui.feature.about
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AboutScreen(onNavigateBack:()->Unit,modifier:Modifier=Modifier) {
    Scaffold(modifier=modifier.fillMaxSize(),topBar={TopAppBar(title={Text("数据来源与开源信息")},navigationIcon={TextButton(onClick=onNavigateBack){Text("返回")}})}) { padding ->
        Column(Modifier.padding(padding).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Codex 额度 · 0.1.3",style=MaterialTheme.typography.headlineSmall)
            Text("个人额度、重置时间、Token 活动：手机直接查询 OpenAI 账户接口。累计 Token 与订阅剩余额度分开显示。接口缺失时显示不可用。")
            Text("Tibo 公告：通过第三方社区 tibo.cc 读取已公布原帖，附原文链接和抓取时间。不是官方保证或重置预测。")
            Text("基于 boudywho/codex-quota-android（MIT）改造，原作者及完整许可证随源码提供。")
            Text("本应用与 OpenAI 无隶属关系。不会自动提交模型任务或使用重置卡。")
        }
    }
}
