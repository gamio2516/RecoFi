package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyRoomMigratorTest {
    private lateinit var db: RecoFiDatabase
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RecoFiDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }

    private fun data(fingerprint: String = "legacy") = LegacyRoomData(
        transactions = listOf(TransactionEntity(1, 500L, "食料品", "店", 1L, false, null, "rakuten", null, false)),
        paymentSources = listOf(PaymentSourceEntity("rakuten", "楽天カード", true, 0)),
        categories = listOf(CategoryEntity("食料品", 0)), notes = emptyList(), recurringExpenses = emptyList(), priceRevisions = emptyList(),
        importedStatements = emptyList(), statementEntries = emptyList(), fingerprints = emptyList(), reconciliationProgress = emptyList(),
        monthlyLocks = emptyList(), monthlyBudgets = listOf(MonthlyBudgetEntity("2026-08", 300000L)),
        budgetSettings = AppBudgetSettingsEntity(defaultMonthlyBudget = 250000L), legacyFingerprint = fingerprint, corrections = emptyList(),
    )

    @Test fun migrationWritesAndVerifiesAllData() = runBlocking {
        assertEquals(StorageMigrationResult.Migrated, LegacyRoomMigrator { 10L }.migrate(db, data()))
        assertEquals(500L, db.transactions().loadAll().single().amount)
        assertTrue(db.migrations().find(1)!!.sourceRetained)
    }

    @Test fun sameMigrationIsIdempotent() = runBlocking {
        val migrator = LegacyRoomMigrator()
        assertEquals(StorageMigrationResult.Migrated, migrator.migrate(db, data()))
        assertEquals(StorageMigrationResult.AlreadyMigrated, migrator.migrate(db, data()))
        assertEquals(1, db.transactions().loadAll().size)
    }

    @Test fun exceptionBeforeCommitRollsBackEverything() = runBlocking {
        val result = LegacyRoomMigrator().migrate(db, data()) { error("simulated") }
        assertTrue(result is StorageMigrationResult.Failed)
        assertTrue(db.transactions().loadAll().isEmpty())
        assertTrue(db.referenceData().loadPaymentSources().isEmpty())
        assertEquals(null, db.migrations().find(1))
    }

    @Test fun differentLegacySourceCannotOverwriteMigratedDatabase() = runBlocking {
        val migrator = LegacyRoomMigrator()
        migrator.migrate(db, data("first"))
        val result = migrator.migrate(db, data("second"))
        assertTrue(result is StorageMigrationResult.Failed)
        assertEquals("first", db.migrations().find(1)!!.legacyFingerprint)
    }
}
