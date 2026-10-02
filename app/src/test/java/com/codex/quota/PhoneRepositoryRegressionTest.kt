package com.codex.quota

import com.codex.quota.auth.OAuthSession
import com.codex.quota.data.remote.CodexAccountDataSource
import com.codex.quota.data.repository.CodexAccountRepositoryImpl
import com.codex.quota.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PhoneRepositoryRegressionTest {
    @Test fun failedActivity_preservesTokensWhileQuotaUpdates_andSuccessMissingFieldsClearsThem() = runTest {
        val accounts = FakeAccountDao(); val snapshots = FakeUsageSnapshotDao(); val store = FakeCredentialStore()
        var attempt = 0
        val source = object : CodexAccountDataSource {
            override suspend fun fetchUsage(account: CodexAccount, apiKey: String): Result<CodexUsage> {
                attempt++
                return Result.success(CodexUsage.empty(account.id, AuthStatus.AUTHENTICATED).copy(
                    remainingPercent = if(attempt == 1) 80.0 else 60.0,
                    usedTokens = if(attempt == 1) 400L else null,
                    activityDate = if(attempt == 1) "2026-10-01" else null,
                    activityDateTokens = if(attempt == 1) 200L else null,
                    activityFetchedAtEpochMs = if(attempt == 1) 1234L else null,
                    activityFetchSucceeded = attempt != 2,
                    activityDailyUsage = if (attempt == 1) listOf(DailyTokenUsage("2026-10-01", 200L)) else emptyList()))
            }
        }
        val repo = CodexAccountRepositoryImpl(accounts, snapshots, store, realDataSource = source, refreshCooldownMs = 0)
        val a = repo.addAccount("我", null, "test-access", PlanType.PLUS, null, "#10B981", false).getOrThrow()
        repo.refreshAccount(a.id)
        val preserved = repo.getAccount(a.id)!!.usage!!
        assertEquals(60.0, preserved.remainingPercent)
        assertEquals(400L, preserved.usedTokens)
        assertEquals(200L, preserved.activityDateTokens)
        assertEquals(1234L, preserved.activityFetchedAtEpochMs)
        assertEquals(listOf(DailyTokenUsage("2026-10-01", 200L)), preserved.activityDailyUsage)
        repo.refreshAccount(a.id)
        assertNull(repo.getAccount(a.id)!!.usage!!.usedTokens)
        assertTrue(repo.getAccount(a.id)!!.usage!!.activityDailyUsage.isEmpty())
    }
    @Test fun immediateRepeatedRefresh_reusesRealSnapshot() = runTest {
        val accounts = FakeAccountDao(); val snapshots = FakeUsageSnapshotDao(); val store = FakeCredentialStore()
        var calls = 0
        val source = object : CodexAccountDataSource {
            override suspend fun fetchUsage(account: CodexAccount, apiKey: String): Result<CodexUsage> {
                calls++; return Result.success(CodexUsage.empty(account.id, AuthStatus.AUTHENTICATED))
            }
        }
        val repo = CodexAccountRepositoryImpl(accounts, snapshots, store, realDataSource = source)
        val a = repo.addAccount("我", null, "test-access", PlanType.PLUS, null, "#10B981", false).getOrThrow()
        repeat(6) { repo.refreshAccount(a.id) }
        assertEquals(1, calls)
    }
    @Test fun refresh_passesAccessTokenAndPreservesLastRealSnapshotOnFailure() = runTest {
        val accounts = FakeAccountDao(); val usage = FakeUsageSnapshotDao(); val store = FakeCredentialStore()
        var fail = false
        val source = object : CodexAccountDataSource {
            override suspend fun fetchUsage(account: CodexAccount, apiKey: String): Result<CodexUsage> {
                assertEquals("access", apiKey)
                return Result.success(if (fail) CodexUsage.empty(account.id, AuthStatus.OFFLINE)
                    else CodexUsage.empty(account.id, AuthStatus.AUTHENTICATED).copy(
                        remainingPercent = 64.0, usedPercent = 36.0, usedTokens = 12345L, fetchedAtEpochMs = 1000L))
            }
        }
        val repo = CodexAccountRepositoryImpl(accounts, usage, store, realDataSource = source, refreshCooldownMs = 0)
        val a = repo.addAccount("我", null, OAuthSession("access", "refresh", Long.MAX_VALUE).encode(),
            PlanType.PLUS, null, "#10B981", false).getOrThrow()
        assertEquals(12345L, repo.getAccount(a.id)!!.usage!!.usedTokens)
        fail = true; repo.refreshAccount(a.id)
        val cached = repo.getAccount(a.id)!!
        assertEquals(64.0, cached.usage!!.remainingPercent)
        assertEquals(12345L, cached.usage!!.usedTokens)
        assertEquals(1000L, cached.usage!!.fetchedAtEpochMs)
        assertEquals(1000L, cached.account.lastSuccessfulSyncEpochMs)
        assertEquals(AuthStatus.OFFLINE, cached.usage!!.status)
    }
    @Test fun publicAnnouncementRequest_neverContainsAccountCredentials() {
        val request = com.codex.quota.announcement.AnnouncementStore.publicRequest()
        assertNull(request.header("Authorization")); assertNull(request.header("Cookie"))
        assertNull(request.header("ChatGPT-Account-Id"))
    }
}
