package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.BackupCodec
import jp.knaka.cardmemo.BackupManager
import jp.knaka.cardmemo.Transaction
import jp.knaka.cardmemo.TransactionRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerRoomTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); StorageProvider.resetForTest(); context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)
        context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.resolve("restore-safety-backups").deleteRecursively()
    }
    @After fun cleanup() { StorageProvider.resetForTest(); context.deleteDatabase(RecoFiDatabase.DATABASE_NAME); context.filesDir.resolve("restore-safety-backups").deleteRecursively() }

    @Test fun restoreCreatesSafetyBackupThenReplacesRoomWithoutTouchingLegacy() {
        val repository = TransactionRepository(context)
        repository.save(listOf(Transaction(1, 100, "食料品", "before", 1)))
        val legacyBefore = context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE).all.toMap()
        val room = (StorageProvider.get(context) as StorageBootstrapResult.RoomReady).database
        val preferences = RoomBackupAdapter.exportPreferences(room).toMutableMap()
        preferences["transactions"] = JSONArray().put(JSONObject().put("id", 2).put("amount", 200).put("category", "食料品").put("note", "after").put("usedAt", 2).put("confirmed", false).put("paymentSourceId", "rakuten").put("suggested", false)).toString()
        val backup = BackupCodec.decodeAndValidate(BackupCodec.encode(preferences, "1.0", "2026-08-13T00:00:00Z"))
        val safety = BackupManager(context).restore(backup)
        assertTrue(safety.isFile && safety.length() > 0)
        assertEquals("after", repository.load().single().note)
        assertEquals(legacyBefore, context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE).all.toMap())
    }

    @Test fun referentiallyInvalidBackupDoesNotChangeCurrentRoom() {
        val repository = TransactionRepository(context); repository.save(listOf(Transaction(1, 100, "食料品", "safe", 1)))
        val room = (StorageProvider.get(context) as StorageBootstrapResult.RoomReady).database
        val preferences = RoomBackupAdapter.exportPreferences(room).toMutableMap()
        preferences["transactions"] = JSONArray().put(JSONObject().put("id", 2).put("amount", 200).put("category", "missing").put("note", "bad").put("usedAt", 2).put("confirmed", false).put("paymentSourceId", "rakuten").put("suggested", false)).toString()
        val backup = BackupCodec.decodeAndValidate(BackupCodec.encode(preferences, "1.0", "2026-08-13T00:00:00Z"))
        assertTrue(runCatching { BackupManager(context).restore(backup) }.isFailure)
        assertEquals("safe", repository.load().single().note)
    }
}
