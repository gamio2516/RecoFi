package jp.knaka.cardmemo.storage
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.BackupCodec
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class) class RoomBackupAdapterTest{
 private lateinit var db:RecoFiDatabase
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RecoFiDatabase::class.java).allowMainThreadQueries().build()};@After fun close(){db.close()}
 private fun snapshot(amount:Long=500)=RoomSnapshot(listOf(TransactionEntity(1,amount,"food","西友","食料品",1,null,"card")),listOf(PaymentSourceEntity("card","カード","CREDIT_CARD",0)),listOf(CategoryEntity("food","食費",0)),emptyList(),emptyList(),emptyList(),emptyList(),listOf(ImportedStatementEntity("f","2026-07","card","a.csv")),listOf(StatementEntryEntity(10,"f",0,"2026-07-01",amount,"西友","raw")),emptyList(),listOf(ReconciliationMatchEntity(10,1,"CONFIRMED","USER","HIGH",100,"reason",0,1,1,1)),emptyList(),listOf(MonthlyPaymentSourceDeclarationEntity("2026-07","card","STATEMENT_IMPORTED",1)),listOf(MonthlyLockEntity("2026-07",1)),listOf(MonthlyBudgetEntity("2026-07",300000)),AppBudgetSettingsEntity(defaultMonthlyBudget=250000))
 @Test fun v3RoundTripPreservesState(){runBlocking{RoomBackupAdapter.replace(db,snapshot());val d=BackupCodec.decodeAndValidate(BackupCodec.encode(db,"1.0"));Assert.assertEquals("CONFIRMED",d.snapshot.matches.single().status);Assert.assertEquals("CREDIT_CARD",d.snapshot.sources.single().type)}}
 @Test fun replacementChangesData(){runBlocking{RoomBackupAdapter.replace(db,snapshot());RoomBackupAdapter.replace(db,snapshot(900));Assert.assertEquals(900,db.transactions().loadAll().single().amount)}}
 @Test fun exceptionRollsBack(){runBlocking{RoomBackupAdapter.replace(db,snapshot());runCatching{RoomBackupAdapter.replace(db,snapshot(900)){error("simulated")}};Assert.assertEquals(500,db.transactions().loadAll().single().amount);Assert.assertEquals("CONFIRMED",db.reconciliation().loadMatches().single().status)}}
}
