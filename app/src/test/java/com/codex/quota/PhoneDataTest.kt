package com.codex.quota

import com.codex.quota.data.remote.dto.AccountActivity
import com.codex.quota.announcement.ResetAnnouncement
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PhoneDataTest {
    @Test fun activityHistory_keepsRealDatesFiltersInvalidAndSortsWithoutInventingMissingDays() {
        val value = AccountActivity.parse("""{"stats":{"daily_usage_buckets":[
          {"start_date":"2026-10-02","tokens":0},{"start_date":"2026-09-30","tokens":999},
          {"start_date":"bad","tokens":4},{"start_date":"2026-10-01","tokens":-1}]}}""")
        assertEquals(listOf("2026-09-30", "2026-10-02"), value.dailyUsage.map { it.date })
        assertEquals(listOf(999L, 0L), value.dailyUsage.map { it.tokens })
        assertEquals("2026-10-02", value.latestDate)
        assertTrue(AccountActivity.parse("""{"metadata":{"stats_error":"unavailable"}}""").dailyUsage.isEmpty())
    }
    @Test fun officialActivity_preservesDateAndMissingValues() {
        val value = AccountActivity.parse("""{"stats":{"lifetime_tokens":33241694,"daily_usage_buckets":[{"start_date":"2026-10-01","tokens":123}]},"metadata":{"stats_as_of":"2026-10-02T00:00:00Z"}}""")
        assertEquals(33241694L, value.lifetimeTokens)
        assertEquals("2026-10-01", value.latestDate)
        assertEquals(123L, value.latestDateTokens)
        assertNull(AccountActivity.parse("{}").lifetimeTokens)
    }
    @Test fun activity_rejectsNegativeAndStatsError() {
        assertNull(AccountActivity.parse("""{"stats":{"lifetime_tokens":-1}}""").lifetimeTokens)
        assertNull(AccountActivity.parse("""{"stats":{"lifetime_tokens":123},"metadata":{"stats_error":"not_available"}}""").lifetimeTokens)
    }
    private fun announcement(author: String = "thsottiaux", text: String = "Global reset landing tomorrow 10am PST for all paid ChatGPT accounts.") =
        """{"data":{"scheduled_reset":{"announced_at":"2026-10-02T02:14:51Z","text":"$text","source":{"type":"x_post","author":"$author","url":"https://x.com/thsottiaux/status/2105843926221660585"}}},"fetched_at":"2026-10-02T08:00:00Z"}"""
    @Test fun tiboTomorrow_usesPacificPostingDateAndShowsDstAmbiguity() {
        val value = ResetAnnouncement.parse(announcement())!!
        assertEquals(Instant.parse("2026-10-02T17:00:00Z").toEpochMilli(), value.earliestEpochMs)
        assertEquals(Instant.parse("2026-10-02T18:00:00Z").toEpochMilli(), value.latestEpochMs)
        assertTrue(value.timeLabel.contains("10月3日"))
    }
    @Test fun announcement_rejectsWrongAuthorAndPrediction() {
        assertNull(ResetAnnouncement.parse(announcement(author = "someone")))
        assertNull(ResetAnnouncement.parse(announcement(text = "Prediction: maybe soon")))
        assertNull(ResetAnnouncement.parse("""{"data":{"active_watch":{"probability":0.9}}}"""))
    }
}
