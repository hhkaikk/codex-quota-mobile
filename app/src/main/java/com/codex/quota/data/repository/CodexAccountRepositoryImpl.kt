package com.codex.quota.data.repository

import com.codex.quota.auth.SessionTokenProvider
import com.codex.quota.auth.SessionExpiredException
import com.codex.quota.data.local.dao.AccountDao
import com.codex.quota.data.local.dao.UsageSnapshotDao
import com.codex.quota.data.local.entity.AccountEntity
import com.codex.quota.data.local.entity.UsageSnapshotEntity
import com.codex.quota.data.remote.CodexAccountDataSource
import com.codex.quota.data.remote.MockOpenAiDataSource
import com.codex.quota.data.remote.RealOpenAiDataSource
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.security.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class CodexAccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val usageSnapshotDao: UsageSnapshotDao,
    private val credentialStore: CredentialStore,
    private val realDataSource: CodexAccountDataSource = RealOpenAiDataSource(),
    private val mockDataSource: CodexAccountDataSource = MockOpenAiDataSource(),
    private val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val refreshCooldownMs: Long = 30_000L
) : CodexAccountRepository {

    private val lastAttempt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val tokenProvider = SessionTokenProvider(credentialStore)
    private val accountLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val refreshMutex = Mutex()
    private var activeRefresh: Deferred<Result<List<CodexUsage>>>? = null

    override fun observeAccounts(): Flow<List<AccountWithUsage>> {
        return combine(
            accountDao.observeAll(),
            usageSnapshotDao.observeAll()
        ) { accounts, snapshots ->
            val snapshotMap = snapshots.associateBy { it.accountId }
            accounts.map { accountEntity ->
                val domainAccount = accountEntity.toDomain()
                val domainUsage = snapshotMap[accountEntity.id]?.toDomain()
                AccountWithUsage(domainAccount, domainUsage)
            }
        }
    }

    override fun observeAccount(accountId: String): Flow<AccountWithUsage?> {
        return combine(
            accountDao.observeById(accountId),
            usageSnapshotDao.observeByAccountId(accountId)
        ) { accountEntity, snapshotEntity ->
            if (accountEntity == null) null
            else {
                AccountWithUsage(
                    account = accountEntity.toDomain(),
                    usage = snapshotEntity?.toDomain()
                )
            }
        }
    }

    override suspend fun getAccount(accountId: String): AccountWithUsage? {
        val accountEntity = accountDao.getById(accountId) ?: return null
        val snapshotEntity = usageSnapshotDao.getByAccountId(accountId)
        return AccountWithUsage(
            account = accountEntity.toDomain(),
            usage = snapshotEntity?.toDomain()
        )
    }

    override suspend fun getAllAccounts(): List<AccountWithUsage> {
        val accounts = accountDao.getAll()
        val snapshots = usageSnapshotDao.getAll().associateBy { it.accountId }
        return accounts.map {
            AccountWithUsage(it.toDomain(), snapshots[it.id]?.toDomain())
        }
    }

    override suspend fun addAccount(
        nickname: String,
        email: String?,
        apiKey: String,
        planType: PlanType,
        organizationId: String?,
        colorHex: String,
        isDemoAccount: Boolean
    ): Result<CodexAccount> {
        val accountId = UUID.randomUUID().toString()
        val existing = accountDao.getAll()
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val now = System.currentTimeMillis()

        val domainAccount = CodexAccount(
            id = accountId,
            nickname = nickname,
            email = email,
            planType = planType,
            organizationId = organizationId,
            colorHex = colorHex,
            authStatus = AuthStatus.REFRESHING,
            isDemoAccount = isDemoAccount,
            orderIndex = nextOrder,
            createdAtEpochMs = now,
            lastSuccessfulSyncEpochMs = null
        )

        credentialStore.storeApiKey(accountId, apiKey)
        accountDao.insert(AccountEntity.fromDomain(domainAccount))

        // Trigger initial refresh
        val refreshResult = refreshAccountInternal(domainAccount, apiKey)
        return if (refreshResult.isSuccess) {
            val updatedAccount = accountDao.getById(accountId)?.toDomain() ?: domainAccount
            Result.success(updatedAccount)
        } else {
            Result.success(domainAccount)
        }
    }

    override suspend fun updateAccount(
        accountId: String,
        nickname: String,
        colorHex: String,
        customRenewalDateEpochMs: Long?
    ): Result<Unit> {
        accountDao.updateDetails(accountId, nickname, colorHex, customRenewalDateEpochMs)
        return Result.success(Unit)
    }

    override suspend fun setAccountRenewalDate(
        accountId: String,
        renewalDateEpochMs: Long?
    ): Result<Unit> {
        accountDao.updateRenewalDate(accountId, renewalDateEpochMs)
        return Result.success(Unit)
    }

    override suspend fun reauthenticateAccount(
        accountId: String,
        newApiKey: String
    ): Result<CodexUsage> {
        val account = accountDao.getById(accountId)?.toDomain()
            ?: return Result.failure(IllegalArgumentException("Account $accountId not found"))

        val newId = com.codex.quota.auth.OAuthSession.decode(newApiKey)?.accessToken?.let {
            com.codex.quota.auth.JwtTokenParser.parseToken(it)?.chatgptAccountId
        }
        if (!account.organizationId.isNullOrBlank() && account.organizationId != newId)
            return Result.failure(IllegalArgumentException("请授权原账户；其他账户请通过新增连接"))
        credentialStore.storeApiKey(accountId, newApiKey)
        return refreshAccountInternal(account, newApiKey, force = true)
    }

    override suspend fun removeAccount(accountId: String): Result<Unit> {
        credentialStore.removeApiKey(accountId)
        accountLocks.getOrPut(accountId) { Mutex() }.withLock {
            usageSnapshotDao.deleteByAccountId(accountId)
            accountDao.deleteById(accountId)
        }
        return Result.success(Unit)
    }

    override suspend fun reorderAccounts(accountIdsInOrder: List<String>): Result<Unit> {
        accountDao.updateOrderIndices(accountIdsInOrder)
        return Result.success(Unit)
    }

    override suspend fun refreshAccount(accountId: String): Result<CodexUsage> {
        val account = accountDao.getById(accountId)?.toDomain()
            ?: return Result.failure(IllegalArgumentException("Account $accountId not found"))
        val apiKey = credentialStore.getApiKey(accountId).orEmpty()
        return refreshAccountInternal(account, apiKey)
    }

    private suspend fun refreshAccountInternal(account: CodexAccount, apiKey: String, force: Boolean = false): Result<CodexUsage> =
        accountLocks.getOrPut(account.id) { Mutex() }.withLock {
            if (accountDao.getById(account.id) == null) return@withLock Result.failure(IllegalStateException("账户已移除"))
            val now = System.currentTimeMillis()
            val previous = usageSnapshotDao.getByAccountId(account.id)?.toDomain()
            if (!force && previous != null && credentialStore.getApiKey(account.id) != null &&
                now - (lastAttempt[account.id] ?: 0L) < refreshCooldownMs) return@withLock Result.success(previous)
            lastAttempt[account.id] = now
            val source = if (account.isDemoAccount) mockDataSource else realDataSource
            val result = try {
                val access = if (account.isDemoAccount) apiKey else tokenProvider.validAccessToken(account.id)
                var response = source.fetchUsage(account, access)
                if (!account.isDemoAccount && response.getOrNull()?.status == AuthStatus.AUTHENTICATION_REQUIRED) {
                    val refreshed = tokenProvider.validAccessToken(account.id, rejectedAccessToken = access)
                    response = source.fetchUsage(account, refreshed)
                }
                response
            } catch (e: CancellationException) { throw e }
              catch (e: SessionExpiredException) {
                Result.success(CodexUsage.empty(account.id, AuthStatus.AUTHENTICATION_REQUIRED).copy(errorMessage = e.message))
            } catch (_: Exception) {
                Result.success(CodexUsage.empty(account.id, AuthStatus.TEMPORARY_ERROR).copy(errorMessage = "查询失败，保留上次数据"))
            }
            // An account removed during network I/O must not be resurrected.
            if (accountDao.getById(account.id) == null || credentialStore.getApiKey(account.id) == null)
                return@withLock Result.failure(IllegalStateException("账户已移除"))
            val fresh = result.getOrNull() ?: CodexUsage.empty(account.id, AuthStatus.TEMPORARY_ERROR)
            val old = usageSnapshotDao.getByAccountId(account.id)?.toDomain()
            val display = if (fresh.status == AuthStatus.AUTHENTICATED) {
                if (fresh.activityFetchSucceeded) fresh else fresh.copy(
                    usedTokens = old?.usedTokens, activityDate = old?.activityDate,
                    activityDateTokens = old?.activityDateTokens, activityStatsAsOf = old?.activityStatsAsOf,
                    activityFetchedAtEpochMs = old?.activityFetchedAtEpochMs,
                    activityDailyUsage = old?.activityDailyUsage.orEmpty())
            } else
                old?.copy(status = fresh.status, errorMessage = fresh.errorMessage) ?: fresh
            usageSnapshotDao.insertOrUpdate(UsageSnapshotEntity.fromDomain(display))
            val currentAccount = accountDao.getById(account.id) ?: return@withLock Result.failure(IllegalStateException("账户已移除"))
            accountDao.updateAuthStatusAndSyncTime(account.id, display.status.name,
                if (fresh.status == AuthStatus.AUTHENTICATED) fresh.fetchedAtEpochMs else currentAccount.lastSuccessfulSyncEpochMs)
            if (result.isFailure) Result.failure(result.exceptionOrNull()!!) else Result.success(display)
        }

    override suspend fun refreshAllAccounts(): Result<List<CodexUsage>> {
        val refresh = refreshMutex.withLock {
            activeRefresh?.takeIf { it.isActive } ?: refreshScope.async {
                refreshAllAccountsInternal()
            }.also { activeRefresh = it }
        }

        return try {
            refresh.await()
        } finally {
            refreshMutex.withLock {
                if (activeRefresh === refresh && refresh.isCompleted) {
                    activeRefresh = null
                }
            }
        }
    }

    private suspend fun refreshAllAccountsInternal(): Result<List<CodexUsage>> = coroutineScope {
        val accounts = accountDao.getAll()
        val concurrencyLimit = Semaphore(MAX_CONCURRENT_ACCOUNT_REFRESHES)
        val results = accounts.map { account ->
            async {
                concurrencyLimit.withPermit {
                    try {
                        val domainAccount = account.toDomain()
                        val apiKey = credentialStore.getApiKey(domainAccount.id).orEmpty()
                        refreshAccountInternal(domainAccount, apiKey).getOrNull()
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll()
        Result.success(results.filterNotNull())
    }

    override suspend fun clearAllData(): Result<Unit> {
        credentialStore.clearAll()
        usageSnapshotDao.deleteAll()
        accountDao.deleteAll()
        return Result.success(Unit)
    }

    private companion object {
        const val MAX_CONCURRENT_ACCOUNT_REFRESHES = 4
    }
}
