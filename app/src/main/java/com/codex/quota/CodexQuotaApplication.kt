package com.codex.quota

import android.app.Application
import android.net.Uri
import com.codex.quota.data.local.AppDatabase
import com.codex.quota.data.local.DataStoreManager
import com.codex.quota.data.remote.MockOpenAiDataSource
import com.codex.quota.data.remote.RealOpenAiDataSource
import com.codex.quota.data.repository.CodexAccountRepositoryImpl
import com.codex.quota.data.repository.UserPreferencesRepositoryImpl
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.UserPreferencesRepository
import com.codex.quota.security.EncryptedCredentialStore
import com.codex.quota.security.KeystoreManager
import com.codex.quota.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect

class CodexQuotaApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var announcements: com.codex.quota.announcement.AnnouncementStore
        private set

    var currentOAuthCallbackUri: Uri? = null

    lateinit var database: AppDatabase
        private set

    lateinit var credentialStore: EncryptedCredentialStore
        private set

    lateinit var dataStoreManager: DataStoreManager
        private set

    lateinit var repository: CodexAccountRepository
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()

        announcements = com.codex.quota.announcement.AnnouncementStore(this)
        database = AppDatabase.getInstance(this)
        val keystoreManager = KeystoreManager(this)
        credentialStore = EncryptedCredentialStore(this, keystoreManager)
        dataStoreManager = DataStoreManager(this)

        repository = CodexAccountRepositoryImpl(
            accountDao = database.accountDao(),
            usageSnapshotDao = database.usageSnapshotDao(),
            credentialStore = credentialStore,
            realDataSource = RealOpenAiDataSource(),
            mockDataSource = MockOpenAiDataSource(),
            refreshScope = applicationScope
        )

        preferencesRepository = UserPreferencesRepositoryImpl(dataStoreManager)

        // Database changes update visible widgets, including foreground refresh and account removal.
        applicationScope.launch {
            repository.observeAccounts().debounce(500).collect {
                com.codex.quota.widget.WidgetUpdateHelper.updateAllWidgets(this@CodexQuotaApplication)
            }
        }
        // Schedule periodic background refresh
        applicationScope.launch {
            val prefs = preferencesRepository.getPreferences()
            if (prefs.backgroundSyncEnabled) WorkScheduler.schedulePeriodicRefresh(this@CodexQuotaApplication, prefs.refreshInterval.minutes)
            else WorkScheduler.cancelPeriodicRefresh(this@CodexQuotaApplication)
        }
    }

    fun markOnboardingComplete() {
        applicationScope.launch {
            preferencesRepository.setHasCompletedOnboarding(true)
        }
    }
}
