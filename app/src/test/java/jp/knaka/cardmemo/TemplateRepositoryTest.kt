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
}
