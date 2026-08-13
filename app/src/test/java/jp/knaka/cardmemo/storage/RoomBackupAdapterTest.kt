package jp.knaka.cardmemo.storage
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.BackupCodec
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class RoomBackupAdapterTest {
 private lateinit var db:RecoFiDatabase
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RecoFiDatabase::class.java).allowMainThreadQueries().build()}
 @After fun close(){db.close()}
 private fun snapshot(amount:Long=500L)=RoomSnapshot(listOf(TransactionEntity(1,amount,"食費","西友","食料品",1L,true,null,"card","2026-07",false)),listOf(PaymentSourceEntity("card","カード",true,0)),listOf(CategoryEntity("食費",0)),listOf(MerchantTemplateEntity("西友",0)),listOf(DescriptionTemplateEntity("食料品",0)),emptyList(),emptyList(),emptyList(),emptyList(),listOf(ImportedFingerprintEntity("fp",1L)),listOf(ReconciliationProgressEntity("2026-07","card",1,1,0,1)),listOf(MonthlyLockEntity("2026-07",1L)),listOf(MonthlyBudgetEntity("2026-07",300000L)),AppBudgetSettingsEntity(defaultMonthlyBudget=250000L))
 @Test fun v2RoundTripPreservesMerchantDescriptionAndState(){runBlocking{RoomBackupAdapter.replace(db,snapshot());val decoded=BackupCodec.decodeAndValidate(BackupCodec.encode(db,"1.0","2026-08-13T00:00:00Z"));Assert.assertEquals("西友",decoded.snapshot.transactions.single().merchant);Assert.assertEquals("食料品",decoded.snapshot.transactions.single().description);Assert.assertEquals(1,decoded.snapshot.progress.single().matched);Assert.assertEquals("2026-07",decoded.snapshot.locks.single().month);Assert.assertTrue(BackupCodec.encode(db,"1.0").contains("\"backupFormatVersion\": 2"))}}
 @Test fun replacementChangesData(){runBlocking{RoomBackupAdapter.replace(db,snapshot());RoomBackupAdapter.replace(db,snapshot(900L));Assert.assertEquals(900L,db.transactions().loadAll().single().amount)}}
 @Test fun exceptionRollsBackEntireReplacement(){runBlocking{RoomBackupAdapter.replace(db,snapshot());runCatching{RoomBackupAdapter.replace(db,snapshot(900L)){error("simulated")}};Assert.assertEquals(500L,db.transactions().loadAll().single().amount);Assert.assertEquals(300000L,db.monthlyState().loadBudgets().single().amount)}}
 @Test fun invalidRequiredFieldDoesNotDecode(){runBlocking{RoomBackupAdapter.replace(db,snapshot());val root=JSONObject(BackupCodec.encode(db,"1.0"));root.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).remove("merchant");Assert.assertTrue(runCatching{BackupCodec.decodeAndValidate(root.toString())}.isFailure)}}
}
