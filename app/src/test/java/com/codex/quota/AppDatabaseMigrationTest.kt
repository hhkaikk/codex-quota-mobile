package com.codex.quota

import androidx.sqlite.db.SupportSQLiteDatabase
import com.codex.quota.data.local.AppDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AppDatabaseMigrationTest {
    @Test fun migration7To8_addsNullableHistoryWithoutDroppingExistingLoginOrSnapshots() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)
        AppDatabase.MIGRATION_7_8.migrate(database)
        verify(exactly = 1) { database.execSQL("ALTER TABLE usage_snapshots ADD COLUMN activityDailyUsageJson TEXT") }
        verify(exactly = 0) { database.execSQL(match { it.contains("DROP", ignoreCase = true) }) }
    }

    @Test
    fun migration4To5_addsNullableBankedResetExpiryColumn() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        AppDatabase.MIGRATION_4_5.migrate(database)

        verify(exactly = 1) {
            database.execSQL(
                "ALTER TABLE usage_snapshots ADD COLUMN bankedResetExpiresAtEpochMs INTEGER"
            )
        }
    }

    @Test
    fun migration5To6_addsNullableFiveHourQuotaColumns() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        AppDatabase.MIGRATION_5_6.migrate(database)

        verify(exactly = 1) {
            database.execSQL(
                "ALTER TABLE usage_snapshots ADD COLUMN fiveHourRemainingPercent REAL"
            )
        }
        verify(exactly = 1) {
            database.execSQL(
                "ALTER TABLE usage_snapshots ADD COLUMN fiveHourUsedPercent REAL"
            )
        }
        verify(exactly = 1) {
            database.execSQL(
                "ALTER TABLE usage_snapshots ADD COLUMN fiveHourResetAtEpochMs INTEGER"
            )
        }
    }
}
