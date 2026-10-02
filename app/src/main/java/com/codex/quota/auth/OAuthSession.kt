package com.codex.quota.auth

import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class OAuthSession(val accessToken: String, val refreshToken: String?, val expiresAtEpochMs: Long?) {
    fun encode(): String = codec.encodeToString(serializer(), this)
    companion object {
        private val codec = Json { ignoreUnknownKeys = true }
        fun decode(raw: String): OAuthSession? = runCatching {
            codec.decodeFromString(serializer(), raw).also { require(it.accessToken.isNotBlank()) }
        }.getOrNull()
        fun from(result: OAuthTokenResult): OAuthSession = OAuthSession(
            result.accessToken, result.refreshToken,
            JwtTokenParser.parseToken(result.accessToken)?.expiresAtEpochMs
                ?: result.expiresInSeconds?.let { System.currentTimeMillis() + it * 1000L }
        )
    }
}

class SessionExpiredException : Exception("登录已失效，请重新在官网授权")

class SessionTokenProvider(
    private val store: CredentialStore,
    private val refresh: suspend (OAuthSession) -> OAuthSession = { DeviceCodeManager.refreshSession(it) },
    private val now: () -> Long = System::currentTimeMillis
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    suspend fun validAccessToken(accountId: String, rejectedAccessToken: String? = null): String =
        locks.getOrPut(accountId) { Mutex() }.withLock {
            val raw = store.getApiKey(accountId) ?: throw SessionExpiredException()
            val session = OAuthSession.decode(raw) ?: return@withLock raw
            val rejected = rejectedAccessToken != null && rejectedAccessToken == session.accessToken
            val expired = session.expiresAtEpochMs?.let { it <= now() + 60_000L } ?: false
            if (!rejected && !expired) return@withLock session.accessToken
            if (session.refreshToken.isNullOrBlank()) throw SessionExpiredException()
            val updated = refresh(session)
            // Logout or replacement during an in-flight refresh must never restore the old session.
            synchronized(store) {
                if (store.getApiKey(accountId) != raw) throw SessionExpiredException()
                store.storeApiKey(accountId, updated.encode())
            }
            updated.accessToken
        }
}
