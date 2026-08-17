package jp.knaka.cardmemo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.knaka.cardmemo.storage.RecoFiDatabase
import jp.knaka.cardmemo.storage.StorageProvider
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class TemplateRepositoryTest {
 private lateinit var context:Context
 private lateinit var repository:AppSettingsRepository
 @Before fun setup(){context=ApplicationProvider.getApplicationContext();StorageProvider.resetForTest();context.deleteDatabase(RecoFiDatabase.DATABASE_NAME);repository=AppSettingsRepository(context)}
 @After fun cleanup(){StorageProvider.resetForTest();context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)}
 @Test fun merchantAdd(){repository.saveMerchants(listOf("西友"));Assert.assertEquals(listOf("西友"),repository.loadMerchants())}
 @Test fun merchantDelete(){repository.saveMerchants(listOf("西友","Amazon"));repository.saveMerchants(repository.loadMerchants()-"西友");Assert.assertEquals(listOf("Amazon"),repository.loadMerchants())}
 @Test fun merchantReorder(){repository.saveMerchants(listOf("西友","Amazon"));repository.saveMerchants(repository.loadMerchants().reversed());Assert.assertEquals(listOf("Amazon","西友"),repository.loadMerchants())}
 @Test fun descriptionAdd(){repository.saveDescriptions(listOf("食料品"));Assert.assertEquals(listOf("食料品"),repository.loadDescriptions())}
 @Test fun descriptionDelete(){repository.saveDescriptions(listOf("食料品","日用品"));repository.saveDescriptions(repository.loadDescriptions()-"食料品");Assert.assertEquals(listOf("日用品"),repository.loadDescriptions())}
 @Test fun descriptionReorder(){repository.saveDescriptions(listOf("食料品","日用品"));repository.saveDescriptions(repository.loadDescriptions().reversed());Assert.assertEquals(listOf("日用品","食料品"),repository.loadDescriptions())}
 @Test fun sixTemplatesAreAllVisible(){val values=(1..6).map(Int::toString);Assert.assertEquals(values,visibleTemplateValues(values,false))}
 @Test fun sevenTemplatesInitiallyShowFirstSixInManualOrder(){val values=listOf("C","A","B","D","E","F","G");Assert.assertEquals(values.take(6),visibleTemplateValues(values,false))}
 @Test fun expandedTemplatesShowAllInManualOrder(){val values=(1..10).map(Int::toString);Assert.assertEquals(values,visibleTemplateValues(values,true))}
 @Test fun defaultPaymentSourceRoundTripsAndClearsWhenDeleted(){val sources=repository.loadPaymentSources();val chosen=sources.last().id;repository.saveDefaultPaymentSourceId(chosen);Assert.assertEquals(chosen,repository.loadDefaultPaymentSourceId());repository.savePaymentSources(sources.filterNot{it.id==chosen});Assert.assertNull(repository.loadDefaultPaymentSourceId())}
 @Test fun merchantAndDescriptionRenamePreserveOrder(){repository.saveMerchants(listOf("西友","Amazon"));repository.renameMerchant("西友","スーパー");Assert.assertEquals(listOf("スーパー","Amazon"),repository.loadMerchants());repository.saveDescriptions(listOf("食料品","日用品"));repository.renameDescription("日用品","生活用品");Assert.assertEquals(listOf("食料品","生活用品"),repository.loadDescriptions())}
 @Test fun categoryRenameKeepsStableIdAndReferences(){val old="食料品";val id=repository.categoryId(old);val source=repository.loadPaymentSources().first().id;TransactionRepository(context).save(listOf(Transaction(1,100,old,"西友","野菜",1,paymentSourceId=source,categoryId=id)));repository.saveRecurringExpenses(listOf(RecurringExpense(2,1000,old,"宅配","定期便",10,"2026-01","2026-01-01",source,categoryId=id)));repository.renameCategory(old,"食費");val db=StorageProvider.database(context);Assert.assertEquals(id,db.referenceData().loadCategories().first{it.name=="食費"}.id);Assert.assertEquals(id,db.transactions().loadAll().single().categoryId);Assert.assertEquals(id,db.recurringExpenses().loadAll().single().categoryId)}
 @Test fun duplicateRenameIsRejected(){repository.saveMerchants(listOf("A","B"));Assert.assertTrue(runCatching{repository.renameMerchant("A","B")}.isFailure)}
 @Test fun reorderedCategorySortOrderIsRenumberedAndReloaded(){repository.saveCategories(listOf("その他","外食","食料品"));val rows=StorageProvider.database(context).referenceData().loadCategories();Assert.assertEquals(listOf(0,1,2),rows.map{it.sortOrder});Assert.assertEquals(listOf("その他","外食","食料品"),repository.loadCategories())}
}
