package jp.knaka.cardmemo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.storage.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryTest {
 private lateinit var context:Context
 @Before fun setup(){context=ApplicationProvider.getApplicationContext();StorageProvider.resetForTest();context.deleteDatabase(RecoFiDatabase.DATABASE_NAME);val db=StorageProvider.database(context);db.referenceData().upsertPaymentSources(listOf(PaymentSourceEntity("card","カード","CREDIT_CARD",0)));db.referenceData().upsertCategories(listOf(CategoryEntity("daily","日用品",0)))}
 @After fun cleanup(){StorageProvider.resetForTest();context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)}
 @Test fun roomSavesAndLoadsMerchantDescriptionAndStableCategory(){val repository=TransactionRepository(context);repository.save(listOf(Transaction(1,3000000000L,"日用品","Amazon","洗剤",1L,paymentSourceId="card",categoryId="daily")));val restored=repository.load().single();Assert.assertEquals("Amazon",restored.merchant);Assert.assertEquals("洗剤",restored.description);Assert.assertEquals("daily",restored.categoryId);Assert.assertEquals(3000000000L,restored.amount)}
}
