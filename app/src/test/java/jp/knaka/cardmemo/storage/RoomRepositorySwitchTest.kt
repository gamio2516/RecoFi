package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.AppSettingsRepository
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
class RoomRepositorySwitchTest {
    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); StorageProvider.resetForTest(); context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)
        prefs = context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE); prefs.edit().clear().commit()
    }
    @After fun cleanup() { StorageProvider.resetForTest(); context.deleteDatabase(RecoFiDatabase.DATABASE_NAME) }

    @Test fun transactionReadsAndWritesRoomWhileLegacyRemainsOriginal() {
        val legacy = JSONArray().put(JSONObject().put("id", 1L).put("amount", 100).put("category", "食料品").put("note", "旧").put("usedAt", 1L).put("paymentSourceId", "rakuten")).toString()
        prefs.edit().putString("transactions", legacy).commit()
        val repository = TransactionRepository(context)
        assertEquals("旧", repository.load().single().note)
        repository.save(listOf(Transaction(2L, 200, "食料品", "新", 2L)))
        assertEquals("新", repository.load().single().note)
        assertEquals(legacy, prefs.getString("transactions", null))
    }

    @Test fun budgetWritesOnlyRoomAfterSuccessfulSwitch() {
        prefs.edit().putInt("default_monthly_budget", 100000).commit()
        val repository = AppSettingsRepository(context)
        assertEquals(100000, repository.loadDefaultMonthlyBudget())
        repository.saveDefaultMonthlyBudget(200000)
        assertEquals(200000, repository.loadDefaultMonthlyBudget())
        assertEquals(100000, prefs.getInt("default_monthly_budget", 0))
        assertTrue(StorageProvider.get(context) is StorageBootstrapResult.RoomReady)
    }
}
