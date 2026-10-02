package com.codex.quota.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DailyTokenUsage(val date: String, val tokens: Long)

object DailyTokenHistory {
    fun encode(days: List<DailyTokenUsage>): String = Json.encodeToString(days)
    fun decode(raw: String?): List<DailyTokenUsage> = if (raw == null) emptyList() else
        runCatching { Json.decodeFromString<List<DailyTokenUsage>>(raw) }.getOrDefault(emptyList())
}
