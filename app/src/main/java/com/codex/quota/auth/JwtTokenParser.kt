package com.codex.quota.auth
import com.codex.quota.domain.model.PlanType
import kotlinx.serialization.json.*
import java.util.Base64
import java.time.Instant
data class DecodedTokenInfo(val email: String?, val userId: String?, val organizationId: String?,
    val chatgptAccountId: String? = null, val planType: PlanType = PlanType.UNKNOWN,
    val expiresAtEpochMs: Long? = null, val subscriptionExpiresAtEpochMs: Long? = null,
    val subscriptionStartedAtEpochMs: Long? = null, val name: String? = null, val rawClaims: Map<String, Any?> = emptyMap())
object JwtTokenParser {
    // Claims provide display/routing hints; only the HTTPS server can validate authorization.
    fun parseToken(token: String): DecodedTokenInfo? = runCatching {
        val parts = token.trim().removePrefix("Bearer ").removePrefix("bearer ").split(".")
        require(parts.size == 3)
        val obj = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).jsonObject
        val auth = obj["https://api.openai.com/auth"]?.jsonObject
        val profile = obj["https://api.openai.com/profile"]?.jsonObject
        fun JsonObject?.s(key: String): String? = this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        fun date(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        DecodedTokenInfo(email = obj.s("email") ?: profile.s("email"),
            userId = auth.s("chatgpt_user_id") ?: obj.s("sub"), organizationId = auth.s("poid"),
            chatgptAccountId = auth.s("chatgpt_account_id"),
            planType = when(auth.s("chatgpt_plan_type")) {
                "free" -> PlanType.FREE; "go" -> PlanType.GO; "plus" -> PlanType.PLUS; "pro" -> PlanType.PRO; "team", "business" -> PlanType.TEAM; "enterprise" -> PlanType.ENTERPRISE
                else -> PlanType.UNKNOWN
            },
            expiresAtEpochMs = obj["exp"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }?.times(1000L),
            subscriptionExpiresAtEpochMs = date(auth.s("chatgpt_subscription_active_until")),
            subscriptionStartedAtEpochMs = date(auth.s("chatgpt_subscription_active_start")),
            name = obj.s("name") ?: profile.s("name"))
    }.getOrNull()
}
