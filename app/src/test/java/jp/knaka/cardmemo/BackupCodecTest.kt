package jp.knaka.cardmemo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.storage.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {
 private lateinit var db:RecoFiDatabase
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RecoFiDatabase::class.java).allowMainThreadQueries().build();seed()}
 @After fun close(){db.close()}
 private fun seed(){db.referenceData().upsertPaymentSources(listOf(PaymentSourceEntity("card","カード",true,0)));db.referenceData().upsertCategories(listOf(CategoryEntity("食費",0)));db.referenceData().upsertMerchants(listOf(MerchantTemplateEntity("西友",0)));db.referenceData().upsertDescriptions(listOf(DescriptionTemplateEntity("食料品",0)));db.transactions().upsertAll(listOf(TransactionEntity(1,500L,"食費","西友","食料品",1L,true,null,"card","2026-07",true)));db.monthlyState().upsertProgress(listOf(ReconciliationProgressEntity("2026-07","card",1,0,1,1)));db.monthlyState().upsertLocks(listOf(MonthlyLockEntity("2026-07",1L)));db.monthlyState().upsertBudgetSettings(AppBudgetSettingsEntity(defaultMonthlyBudget=100000L))}
 private fun encoded()=BackupCodec.encode(db,"1.0","2026-08-13T00:00:00Z")
 @Test fun roundTripPreservesMetadataCountsMerchantAndDescription(){val d=BackupCodec.decodeAndValidate(encoded());assertEquals("1.0",d.appVersion);assertEquals("2026-08-13T00:00:00Z",d.createdAt);assertEquals(1,d.counts.transactions);assertEquals("西友",d.snapshot.transactions.single().merchant);assertEquals("食料品",d.snapshot.transactions.single().description)}
 @Test fun unsupportedFormatVersionRejected(){val root=JSONObject(encoded()).put("backupFormatVersion",99);assertFailure(root)}
 @Test fun missingRequiredMetadataRejected(){val root=JSONObject(encoded()).apply{remove("createdAt")};assertFailure(root)}
 @Test fun corruptJsonRejected(){assertTrue(runCatching{BackupCodec.decodeAndValidate("{broken")}.isFailure)}
 @Test fun mismatchedCountsRejected(){val root=JSONObject(encoded());root.getJSONObject("counts").put("transactions",999);assertFailure(root)}
 @Test fun missingRequiredTransactionFieldRejected(){val root=JSONObject(encoded());root.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).remove("merchant");assertFailure(root)}
 @Test fun reconciliationAndLockStatePreserved(){val d=BackupCodec.decodeAndValidate(encoded());val t=d.snapshot.transactions.single();assertTrue(t.confirmed);assertTrue(t.suggested);assertEquals("2026-07",t.reconciledMonth);assertEquals(1,d.snapshot.progress.single().suggested);assertEquals("2026-07",d.snapshot.locks.single().month)}
 @Test fun missingReconciliationStateFieldRejected(){val root=JSONObject(encoded());root.getJSONObject("data").getJSONArray("progress").getJSONObject(0).remove("matched");assertFailure(root)}
 @Test fun encodingDeterministicForSameDataAndTimestamp(){assertEquals(encoded(),encoded())}
 @Test fun reconciliationCountsCannotExceedImported(){val root=JSONObject(encoded());root.getJSONObject("data").getJSONArray("progress").getJSONObject(0).put("matched",2);assertFailure(root)}
 @Test fun negativeMoneyRejected(){val root=JSONObject(encoded());root.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("amount",-1);assertFailure(root)}
 private fun assertFailure(root:JSONObject){assertTrue(runCatching{BackupCodec.decodeAndValidate(root.toString())}.isFailure)}
}
