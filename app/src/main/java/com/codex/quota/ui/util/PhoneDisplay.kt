package com.codex.quota.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun phoneTime(epochMs: Long?): String = epochMs?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.CHINA))
} ?: "不可用"
fun phonePercent(value: Double?): String = value?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.let {
    String.format(Locale.CHINA, "%.0f%%", it)
} ?: "不可用"
fun phoneTokens(value: Long?): String = value?.let {
    when { it >= 1_000_000 -> String.format(Locale.CHINA, "%.2fM", it / 1_000_000.0)
        it >= 10_000 -> String.format(Locale.CHINA, "%.1f万", it / 10_000.0)
        else -> it.toString() }
} ?: "不可用"
