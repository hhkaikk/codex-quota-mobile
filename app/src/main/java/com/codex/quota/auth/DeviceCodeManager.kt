package com.codex.quota.auth
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

data class DeviceCodeSession(val deviceAuthId: String, val userCode: String,
    val verificationUri: String = "https://auth.openai.com/codex/device",
    val expiresInSeconds: Int = 900, val intervalSeconds: Int = 5,
    val createdAtEpochMs: Long = System.currentTimeMillis())
sealed class DevicePollResult {
    data class Success(val tokenResult: OAuthTokenResult) : DevicePollResult()
    data object Pending : DevicePollResult()
    data object SlowDown : DevicePollResult()
    data class Error(val message: String) : DevicePollResult()
    data object Expired : DevicePollResult()
}
object DeviceCodeManager {
    const val USER_CODE_ENDPOINT = "https://auth.openai.com/api/accounts/deviceauth/usercode"
    const val TOKEN_POLL_ENDPOINT = "https://auth.openai.com/api/accounts/deviceauth/token"
    const val OAUTH_TOKEN_ENDPOINT = "https://auth.openai.com/oauth/token"
    const val VERIFICATION_URL = "https://auth.openai.com/codex/device"
    const val OFFICIAL_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val OFFICIAL_REDIRECT_URI = "https://auth.openai.com/deviceauth/callback"
    private val media = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private fun request(url: String, body: RequestBody) = Request.Builder().url(url).post(body)
        .header("Accept", "application/json").header("User-Agent", "CodexQuotaMobile/0.1").build()
    suspend fun requestDeviceCode(client: OkHttpClient = http): Result<DeviceCodeSession> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject { put("client_id", OFFICIAL_CLIENT_ID) }
            client.newCall(request(USER_CODE_ENDPOINT, payload.toString().toRequestBody(media))).execute().use { response ->
                if (!response.isSuccessful) error("生成设备码失败（HTTP ${response.code}）")
                val obj = Json.parseToJsonElement(response.body!!.string()).jsonObject
                val expires = obj["expires_at"]?.jsonPrimitive?.contentOrNull?.let {
                    runCatching { ((Instant.parse(it).toEpochMilli() - System.currentTimeMillis()) / 1000).toInt().coerceIn(1, 900) }.getOrNull()
                } ?: 900
                Result.success(DeviceCodeSession(
                    obj.getValue("device_auth_id").jsonPrimitive.content.also { require(it.isNotBlank()) },
                    obj.getValue("user_code").jsonPrimitive.content.also { require(it.isNotBlank()) },
                    expiresInSeconds = expires,
                    intervalSeconds = (obj["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(5, 60)))
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { Result.failure(Exception("无法生成设备码，请检查网络和官网设备授权设置")) }
    }
    suspend fun pollDeviceToken(session: DeviceCodeSession, client: OkHttpClient = http): DevicePollResult = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - session.createdAtEpochMs >= session.expiresInSeconds * 1000L)
            return@withContext DevicePollResult.Expired
        try {
            val payload = buildJsonObject { put("device_auth_id", session.deviceAuthId); put("user_code", session.userCode) }
            client.newCall(request(TOKEN_POLL_ENDPOINT, payload.toString().toRequestBody(media))).execute().use { response ->
                when {
                    response.code == 403 || response.code == 404 -> DevicePollResult.Pending
                    response.code == 429 -> DevicePollResult.SlowDown
                    !response.isSuccessful -> DevicePollResult.Error("授权查询失败（HTTP ${response.code}），请重新生成设备码")
                    else -> {
                        val obj = Json.parseToJsonElement(response.body!!.string()).jsonObject
                        val result = exchangeAuthorizationCode(obj.getValue("authorization_code").jsonPrimitive.content,
                            obj.getValue("code_verifier").jsonPrimitive.content, client)
                        result.fold({ DevicePollResult.Success(it) }, { DevicePollResult.Error("官网授权交换失败，请重新生成设备码") })
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { DevicePollResult.Error("网络或授权响应异常，请检查网络后重新生成设备码") }
    }
    private fun parseTokens(raw: String): OAuthTokenResult {
        val obj = Json.parseToJsonElement(raw).jsonObject
        val access = obj.getValue("access_token").jsonPrimitive.content.also { require(it.isNotBlank()) }
        val id = obj["id_token"]?.jsonPrimitive?.contentOrNull
        return OAuthTokenResult(access, obj["refresh_token"]?.jsonPrimitive?.contentOrNull, id,
            obj["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }, JwtTokenParser.parseToken(id ?: access))
    }
    suspend fun exchangeAuthorizationCode(authorizationCode: String, codeVerifier: String,
        client: OkHttpClient = http): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", OFFICIAL_CLIENT_ID)
                .add("code", authorizationCode).add("code_verifier", codeVerifier)
                .add("redirect_uri", OFFICIAL_REDIRECT_URI).build()
            client.newCall(request(OAUTH_TOKEN_ENDPOINT, body)).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("官网授权交换失败（HTTP ${response.code}）"))
                Result.success(parseTokens(response.body!!.string()))
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { Result.failure(Exception("官网授权交换失败，请重新生成设备码")) }
    }
    suspend fun refreshSession(session: OAuthSession, client: OkHttpClient = http): OAuthSession = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", OFFICIAL_CLIENT_ID)
            .add("refresh_token", session.refreshToken ?: throw SessionExpiredException()).build()
        client.newCall(request(OAUTH_TOKEN_ENDPOINT, body)).execute().use { response ->
            if (response.code == 400 || response.code == 401) throw SessionExpiredException()
            if (!response.isSuccessful) throw java.io.IOException("登录续期暂时失败（HTTP ${response.code}）")
            val result = parseTokens(response.body!!.string())
            OAuthSession.from(result).copy(refreshToken = result.refreshToken ?: session.refreshToken)
        }
    }
    fun openBrowser(context: Context, url: String = VERIFICATION_URL) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun copyToClipboard(context: Context, text: String, label: String = "设备码") {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
