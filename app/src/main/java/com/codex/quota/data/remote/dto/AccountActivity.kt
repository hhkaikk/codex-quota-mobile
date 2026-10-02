package com.codex.quota.data.remote.dto

import kotlinx.serialization.json.*
import java.time.LocalDate
import com.codex.quota.domain.model.DailyTokenUsage

data class AccountActivity(
    val lifetimeTokens: Long?, val latestDate: String?, val latestDateTokens: Long?, val statsAsOf: String?, val available: Boolean = true,
    val dailyUsage: List<DailyTokenUsage> = emptyList()
) {
    companion object {
        fun parse(raw: String): AccountActivity {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val stats = obj["stats"] as? JsonObject
            val meta = obj["metadata"] as? JsonObject
            if (!meta?.get("stats_error")?.jsonPrimitive?.contentOrNull.isNullOrBlank())
                return AccountActivity(null, null, null, null, available = false)
            val buckets = (stats?.get("daily_usage_buckets") as? JsonArray).orEmpty().mapNotNull {
                val bucket = it as? JsonObject ?: return@mapNotNull null
                val date = bucket["start_date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (runCatching { LocalDate.parse(date) }.isFailure) return@mapNotNull null
                val tokens = bucket["tokens"]?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 } ?: return@mapNotNull null
                DailyTokenUsage(date, tokens)
            }.distinctBy { it.date }.sortedBy { it.date }.takeLast(14)
            val latest = buckets.lastOrNull()
            return AccountActivity(stats?.get("lifetime_tokens")?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 },
                latest?.date, latest?.tokens, meta?.get("stats_as_of")?.jsonPrimitive?.contentOrNull,
                dailyUsage = buckets)
        }
    }
}
