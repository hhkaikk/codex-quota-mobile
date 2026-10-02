package com.codex.quota

import com.codex.quota.data.local.entity.UsageSnapshotEntity
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.RateLimitInfo
import com.codex.quota.domain.model.DailyTokenUsage
import com.codex.quota.domain.model.DailyTokenHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSnapshotEntityTest {

    @Test
    fun domainRoundTrip_preservesSubscriptionAndAccountMetadata() {
        val usage = CodexUsage(
            accountId = "account-1",
            remainingPercent = 61.5,
            usedPercent = 38.5,
            usedTokens = 385L,
            totalLimitTokens = 1_000L,
            remainingCredits = 7.25,
            resetAtEpochMs = 2_000L,
            status = AuthStatus.AUTHENTICATED,
            fetchedAtEpochMs = 1_000L,
            rateLimitInfo = RateLimitInfo(
                limitRequests = 100L,
                remainingRequests = 61L,
                resetRequestsDuration = "15m",
                limitTokens = 1_000L,
                remainingTokens = 615L,
                resetTokensDuration = "1h"
            ),
            errorMessage = null,
            subscriptionRenewalEpochMs = 10_000L,
            subscriptionStartedAtEpochMs = 3_000L,
            billingPeriod = "Annual",
            accountCreatedEpochMs = 500L,
            willAutoRenew = false,
            hasActiveSubscription = true,
            bankedResets = 1,
            bankedResetExpiresAtEpochMs = 1_795_123_868_943L,
            fiveHourRemainingPercent = 44.0,
            fiveHourUsedPercent = 56.0,
            fiveHourResetAtEpochMs = 4_000L,
            activityDailyUsage = listOf(DailyTokenUsage("2026-09-30", 100L), DailyTokenUsage("2026-10-02", 0L))
        )

        assertEquals(usage, UsageSnapshotEntity.fromDomain(usage).toDomain())
    }
    @Test fun oldOrMalformedActivityCache_hasNoFabricatedHistory() {
        assertEquals(emptyList<DailyTokenUsage>(), DailyTokenHistory.decode(null))
        assertEquals(emptyList<DailyTokenUsage>(), DailyTokenHistory.decode("invalid"))
    }
}
