package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.BackupCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomBackupAdapterTest {
    private lateinit var db: RecoFiDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RecoFiDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    private fun data(amount: Long) = LegacyRoomData(
        listOf(TransactionEntity(1, amount, "食料品", "店", 1, false, null, "rakuten", null, false)),
        listOf(PaymentSourceEntity("rakuten", "楽天カード", true, 0)), listOf(CategoryEntity("食料品", 0)), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(MonthlyBudgetEntity("2026-08", 300000)), AppBudgetSettingsEntity(defaultMonthlyBudget = 250000), "source", emptyList(),
    )

    @Test fun roomExportsExistingBackupFormatVersionOne() = runBlocking {
        RoomBackupAdapter.replace(db, data(500))
        val encoded = BackupCodec.encode(RoomBackupAdapter.exportPreferences(db), "1.0", "2026-08-13T00:00:00Z")
        val validated = BackupCodec.decodeAndValidate(encoded)
        assertEquals(1, validated.counts.transactions)
        assertTrue(encoded.contains("\"backupFormatVersion\": 1"))
    }

    @Test fun replacementChangesAllUserDataAtomically() = runBlocking {
        RoomBackupAdapter.replace(db, data(500)); RoomBackupAdapter.replace(db, data(900))
        assertEquals(900L, db.transactions().loadAll().single().amount)
    }

    @Test fun exceptionBeforeCommitKeepsPreviousDatabaseState() = runBlocking {
        RoomBackupAdapter.replace(db, data(500))
        runCatching { RoomBackupAdapter.replace(db, data(900)) { error("simulated") } }
        assertEquals(500L, db.transactions().loadAll().single().amount)
        assertEquals(300000L, db.monthlyState().loadBudgets().single().amount)
    }

    @Test fun exportedPreferencesRoundTripThroughStrictLegacyParser() = runBlocking {
        RoomBackupAdapter.replace(db, data(500))
        val result = LegacyMigrationParser().parse(LegacySnapshot(RoomBackupAdapter.exportPreferences(db)))
        assertTrue(result is LegacyParseResult.Success)
        assertEquals(500L, (result as LegacyParseResult.Success).data.transactions.single().amount)
    }
}
