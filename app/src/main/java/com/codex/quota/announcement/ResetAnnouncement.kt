package com.codex.quota.announcement

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class ResetAnnouncement(
    val sourceUrl: String, val originalText: String, val earliestEpochMs: Long,
    val latestEpochMs: Long, val fetchedAtEpochMs: Long
) {
    val timeLabel: String get() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = Instant.ofEpochMilli(earliestEpochMs).atZone(zone)
        val end = Instant.ofEpochMilli(latestEpochMs).atZone(zone)
        val format = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        return day.format(format) + if (earliestEpochMs != latestEpochMs) "–" + end.format(DateTimeFormatter.ofPattern("HH:mm")) + "（北京时间，原文 PST）" else "（北京时间）"
    }
    companion object {
        private val link = Regex("https://x\\.com/thsottiaux/status/[0-9]+")
        fun parse(raw: String, now: Long = System.currentTimeMillis()): ResetAnnouncement? = runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            require(root["stale"]?.jsonPrimitive?.booleanOrNull != true)
            val post = root.getValue("data").jsonObject["scheduled_reset"]?.jsonObject ?: return null
            val source = post.getValue("source").jsonObject
            require(source["type"]?.jsonPrimitive?.content == "x_post")
            require(source["author"]?.jsonPrimitive?.content == "thsottiaux")
            val url = source.getValue("url").jsonPrimitive.content
            require(link.matches(url))
            val text = post.getValue("text").jsonPrimitive.content
            require(text.contains("reset", ignoreCase = true))
            require(!text.contains("prediction", ignoreCase = true))
            val time = Regex("tomorrow\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\s+(PST|PDT)", RegexOption.IGNORE_CASE).find(text) ?: return null
            val hour = time.groupValues[1].toInt().also { require(it in 1..12) } % 12 + if (time.groupValues[3].equals("pm", true)) 12 else 0
            val minute = time.groupValues[2].ifEmpty { "0" }.toInt().also { require(it in 0..59) }
            val announced = Instant.parse(post.getValue("announced_at").jsonPrimitive.content)
            val pacific = ZoneId.of("America/Los_Angeles")
            val local = announced.atZone(pacific).toLocalDate().plusDays(1).atTime(hour, minute)
            val strictOffset = if (time.groupValues[4].equals("PST", true)) ZoneOffset.ofHours(-8) else ZoneOffset.ofHours(-7)
            val strict = local.toInstant(strictOffset).toEpochMilli()
            val regional = local.atZone(pacific).toInstant().toEpochMilli()
            ResetAnnouncement(url, text, minOf(strict, regional), maxOf(strict, regional), now)
        }.getOrNull()
    }
}

class AnnouncementStore(context: Context, private val client: OkHttpClient = anonymousClient()) {
    private val prefs = context.applicationContext.getSharedPreferences("public_announcements", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val codec = Json { ignoreUnknownKeys = true }
    fun cached(): ResetAnnouncement? = prefs.getString("announcement", null)?.let {
        runCatching { codec.decodeFromString(ResetAnnouncement.serializer(), it) }.getOrNull()
    }
    suspend fun refreshIfDue(): Unit = withContext(Dispatchers.IO) {
        lock.withLock {
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("last_attempt", 0) < 60 * 60 * 1000L) return@withLock
            prefs.edit().putLong("last_attempt", now).apply()
            try {
                client.newCall(publicRequest()).execute().use { response ->
                    if (response.isSuccessful) {
                        val raw = response.body!!.string()
                        // Successful "no scheduled reset" clears an obsolete announcement; malformed data preserves cache.
                        val obj = Json.parseToJsonElement(raw).jsonObject
                        if (obj["stale"]?.jsonPrimitive?.booleanOrNull == true) return@use
                        val data = obj["data"] as? JsonObject ?: return@use
                        if (data["scheduled_reset"] == null || data["scheduled_reset"] == JsonNull)
                            prefs.edit().remove("announcement").apply()
                        else ResetAnnouncement.parse(raw, now)?.let {
                            prefs.edit().putString("announcement", codec.encodeToString(ResetAnnouncement.serializer(), it)).apply()
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { /* Last verified public announcement remains visible with its retrieval time. */ }
        }
    }
    companion object {
        fun publicRequest(): Request = Request.Builder().url("https://tibo.cc/api/v1/status")
            .header("Accept", "application/json").header("User-Agent", "CodexQuotaMobile/0.1").build()
        fun anonymousClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
