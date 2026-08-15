package jp.knaka.cardmemo.storage
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class BackupManagerRoomTest {
 private lateinit var context:Context
 @Before fun setup(){context=ApplicationProvider.getApplicationContext();StorageProvider.resetForTest();context.deleteDatabase(RecoFiDatabase.DATABASE_NAME);context.filesDir.resolve("restore-safety-backups").deleteRecursively()}
 @After fun cleanup(){StorageProvider.resetForTest();context.deleteDatabase(RecoFiDatabase.DATABASE_NAME);context.filesDir.resolve("restore-safety-backups").deleteRecursively()}
 @Test fun restoreCreatesVerifiedSafetyBackupAndReplacesRoom(){val db=StorageProvider.database(context);seed(db);val repo=TransactionRepository(context);repo.save(listOf(Transaction(1,100L,"食料品","before","old",1L,paymentSourceId="rakuten")));val target=BackupCodec.decodeAndValidate(BackupCodec.encode(db,"1.0"));val changed=target.copy(snapshot=target.snapshot.copy(transactions=listOf(target.snapshot.transactions.single().copy(merchant="after",description="new"))));val safety=BackupManager(context).restore(changed);Assert.assertTrue(safety.isFile&&safety.length()>0);Assert.assertEquals("after",repo.load().single().merchant);Assert.assertEquals("new",repo.load().single().description)}
 @Test fun invalidBackupCannotChangeRoom(){val db=StorageProvider.database(context);seed(db);val repo=TransactionRepository(context);repo.save(listOf(Transaction(1,100L,"食料品","safe","keep",1L,paymentSourceId="rakuten")));val valid=BackupCodec.decodeAndValidate(BackupCodec.encode(db,"1.0"));val invalid=valid.copy(snapshot=valid.snapshot.copy(transactions=listOf(valid.snapshot.transactions.single().copy(category="missing"))));Assert.assertTrue(runCatching{BackupManager(context).restore(invalid)}.isFailure);Assert.assertEquals("safe",repo.load().single().merchant)}
 private fun seed(db:RecoFiDatabase){db.referenceData().upsertPaymentSources(listOf(PaymentSourceEntity("rakuten","楽天カード","CREDIT_CARD",0)));db.referenceData().upsertCategories(listOf(CategoryEntity("食料品",0)));db.monthlyState().upsertBudgetSettings(AppBudgetSettingsEntity(defaultMonthlyBudget=0L))}
}
