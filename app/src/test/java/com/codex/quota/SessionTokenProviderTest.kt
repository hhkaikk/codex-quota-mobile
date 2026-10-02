package com.codex.quota

import com.codex.quota.auth.OAuthSession
import com.codex.quota.auth.SessionTokenProvider
import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionTokenProviderTest {
    private class Store : CredentialStore {
        val values = mutableMapOf<String, String>()
        override fun storeApiKey(accountId: String, apiKey: String) { values[accountId] = apiKey }
        override fun getApiKey(accountId: String) = values[accountId]
        override fun removeApiKey(accountId: String) { values.remove(accountId) }
        override fun clearAll() { values.clear() }
    }

    @Test fun expiredSession_rotatesAndPersistsBeforeReturning() = runTest {
        val store = Store()
        store.storeApiKey("a", OAuthSession("old", "refresh-1", 10).encode())
        val provider = SessionTokenProvider(store, { old ->
            assertEquals("refresh-1", old.refreshToken)
            OAuthSession("new", "refresh-2", 900_000)
        }, { 100_000 })
        assertEquals("new", provider.validAccessToken("a"))
        assertEquals("refresh-2", OAuthSession.decode(store.values.getValue("a"))!!.refreshToken)
    }

    @Test fun concurrentExpiredReads_refreshExactlyOnce() = runTest {
        val store = Store()
        store.storeApiKey("a", OAuthSession("old", "r", 1).encode())
        var calls = 0
        val provider = SessionTokenProvider(store, {
            calls++; delay(25); OAuthSession("new", "r2", 900_000)
        }, { 100_000 })
        assertEquals(List(8) { "new" }, (1..8).map { async { provider.validAccessToken("a") } }.awaitAll())
        assertEquals(1, calls)
    }

    @Test fun failedRefresh_doesNotDestroySavedSession() = runTest {
        val store = Store()
        val original = OAuthSession("old", "r", 1).encode()
        store.storeApiKey("a", original)
        val provider = SessionTokenProvider(store, { throw java.io.IOException("offline") }, { 100_000 })
        assertTrue(runCatching { provider.validAccessToken("a") }.isFailure)
        assertEquals(original, store.values["a"])
    }

    @Test fun deletedDuringRefresh_doesNotRestoreCredentials() = runTest {
        val store = Store()
        store.storeApiKey("a", OAuthSession("old", "r", 1).encode())
        val provider = SessionTokenProvider(store, {
            store.removeApiKey("a"); OAuthSession("new", "r2", 900_000)
        }, { 100_000 })
        assertTrue(runCatching { provider.validAccessToken("a") }.isFailure)
        assertNull(store.values["a"])
    }

    @Test fun stale401_doesNotRefreshAlreadyRotatedToken() = runTest {
        val store = Store()
        store.storeApiKey("a", OAuthSession("new", "r2", 900_000).encode())
        val provider = SessionTokenProvider(store, { error("must not refresh") }, { 100_000 })
        assertEquals("new", provider.validAccessToken("a", rejectedAccessToken = "old"))
    }

    @Test fun legacyToken_isReadWithoutChangingStorage() = runTest {
        val store = Store(); store.storeApiKey("a", "sk-test")
        val provider = SessionTokenProvider(store, { error("must not refresh") })
        assertEquals("sk-test", provider.validAccessToken("a"))
        assertEquals("sk-test", store.values["a"])
    }
}
