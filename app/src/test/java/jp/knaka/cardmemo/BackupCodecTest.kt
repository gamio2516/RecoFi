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
@RunWith(RobolectricTestRunner::class) class BackupCodecTest{
 private lateinit var db:RecoFiDatabase
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RecoFiDatabase::class.java).allowMainThreadQueries().build();seed()};@After fun close(){db.close()}
 private fun seed(){db.referenceData().upsertPaymentSources(listOf(PaymentSourceEntity("card","カード","CREDIT_CARD",0)));db.referenceData().upsertCategories(listOf(CategoryEntity("food","食費",0)));db.recurringExpenses().upsertAll(listOf(RecurringExpenseEntity(20,1000,"food","Netflix","動画",31,"2026-01","2026-01-20","card",1,null)));db.transactions().upsertAll(listOf(TransactionEntity(1,500,"food","西友","食料品",1,null,"card")));db.statements().upsertStatements(listOf(ImportedStatementEntity("f","2026-07","card","a.csv")));db.statements().upsertEntries(listOf(StatementEntryEntity(10,"f",0,"2026-07-01",500,"西友","raw")));db.reconciliation().upsertMatch(ReconciliationMatchEntity(10,1,"CONFIRMED","USER","HIGH",95,"reason",0,1,2,2));db.monthlyState().upsertLocks(listOf(MonthlyLockEntity("2026-07",1)));db.monthlyState().upsertBudgetSettings(AppBudgetSettingsEntity(defaultMonthlyBudget=100000,defaultPaymentSourceId="card"))}
 private fun encoded()=BackupCodec.encode(db,"1.0","2026-08-13T00:00:00Z")
 @Test fun v5RoundTripPreservesTransactionMatchCategoryAndDefaultSource(){val d=BackupCodec.decodeAndValidate(encoded());assertEquals("西友",d.snapshot.transactions.single().merchant);assertEquals("food",d.snapshot.transactions.single().categoryId);assertEquals("CONFIRMED",d.snapshot.matches.single().status);assertEquals("card",d.snapshot.budgetSettings.defaultPaymentSourceId);assertEquals(5,JSONObject(encoded()).getInt("backupFormatVersion"))}
 @Test fun unsupportedVersionRejected(){assertFailure(JSONObject(encoded()).put("backupFormatVersion",3))}
 @Test fun missingMetadataRejected(){assertFailure(JSONObject(encoded()).apply{remove("createdAt")})}
 @Test fun corruptJsonRejected(){assertTrue(runCatching{BackupCodec.decodeAndValidate("{bad")}.isFailure)}
 @Test fun countMismatchRejected(){val r=JSONObject(encoded());r.getJSONObject("counts").put("reconciliationMatches",99);assertFailure(r)}
 @Test fun missingMerchantRejected(){val r=JSONObject(encoded());r.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).remove("merchant");assertFailure(r)}
 @Test fun invalidPaymentDayRejected(){val r=JSONObject(encoded());r.getJSONObject("data").getJSONArray("recurringExpenses").getJSONObject(0).put("paymentDay",0);assertFailure(r)}
 @Test fun v4BackupIsSafelyConverted(){val r=JSONObject(encoded()).put("backupFormatVersion",4);val data=r.getJSONObject("data");data.getJSONArray("categories").getJSONObject(0).remove("id");data.getJSONArray("transactions").getJSONObject(0).apply{put("category",getString("categoryName"));remove("categoryId");remove("categoryName")};data.getJSONArray("recurringExpenses").getJSONObject(0).apply{put("category",getString("categoryName"));put("billingDay",getInt("paymentDay"));remove("categoryId");remove("categoryName");remove("paymentDay")};val decoded=BackupCodec.decodeAndValidate(r.toString());assertEquals(stableCategoryId("食費"),decoded.snapshot.transactions.single().categoryId);assertEquals(31,decoded.snapshot.recurring.single().paymentDay)}
 @Test fun invalidMatchReferenceRejected(){val r=JSONObject(encoded());r.getJSONObject("data").getJSONArray("matches").getJSONObject(0).put("transactionId",999);assertFailure(r)}
 @Test fun unknownDefaultPaymentSourceRejected(){val r=JSONObject(encoded());r.getJSONObject("data").put("defaultPaymentSourceId","missing");assertFailure(r)}
 @Test fun deterministic(){assertEquals(encoded(),encoded())}
 @Test fun negativeMoneyRejected(){val r=JSONObject(encoded());r.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("amount",-1);assertFailure(r)}
 private fun assertFailure(r:JSONObject)=assertTrue(runCatching{BackupCodec.decodeAndValidate(r.toString())}.isFailure)
}
