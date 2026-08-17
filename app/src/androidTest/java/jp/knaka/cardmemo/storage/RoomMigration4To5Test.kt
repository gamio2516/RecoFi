package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.knaka.cardmemo.stableCategoryId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigration4To5Test {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get()=InstrumentationRegistry.getInstrumentation().context
    private val name="migration-4-5"

    private fun createV4(){
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile?.mkdirs()
        val db=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        val schema=testContext.assets.open("jp.knaka.cardmemo.storage.RecoFiDatabase/4.json").bufferedReader().use{JSONObject(it.readText())}
        val entities=schema.getJSONObject("database").getJSONArray("entities")
        db.execSQL("PRAGMA foreign_keys=OFF")
        for(i in 0 until entities.length()){
            val entity=entities.getJSONObject(i)
            val table=entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indexes=entity.optJSONArray("indices")?:continue
            for(j in 0 until indexes.length())db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        db.execSQL("INSERT INTO payment_sources(id,name,type,sortOrder,isCard) VALUES('card','楽天カード','CREDIT_CARD',0,1)")
        db.execSQL("INSERT INTO categories(name,sortOrder) VALUES('外食',0)")
        db.execSQL("INSERT INTO recurring_expenses(id,amount,category,merchant,description,billingDay,startMonth,contractDate,paymentSourceId,intervalMonths,endDate) VALUES(10,980,'外食','Netflix','動画',31,'2026-01','2026-01-20','card',1,NULL)")
        db.execSQL("INSERT INTO recurring_price_revisions(recurringId,effectiveDate,amount) VALUES(10,'2026-07-01',1280)")
        db.execSQL("INSERT INTO transactions(id,amount,category,merchant,description,usedAt,recurringId,paymentSourceId) VALUES(1,500,'外食','スターバックス','コーヒー',0,NULL,'card')")
        db.execSQL("INSERT INTO imported_statements(fileHash,statementMonth,paymentSourceId,fileName) VALUES('hash','2026-08','card','statement.csv')")
        db.execSQL("INSERT INTO statement_entries(id,fileHash,rowOrder,date,amount,merchant,rawText) VALUES(100,'hash',0,'2026-08-10',500,'STARBUCKS','raw')")
        db.execSQL("INSERT INTO reconciliation_matches(statementEntryId,transactionId,status,matchSource,confidence,score,reasonCode,dayDifference,createdAt,updatedAt,confirmedAt) VALUES(100,1,'CONFIRMED','USER','HIGH',100,'manual',0,1,1,1)")
        db.version=4
        db.close()
    }

    @Test fun preservesCategoriesTransactionsRecurringAndReferences(){
        createV4()
        val room=Room.databaseBuilder(context,RecoFiDatabase::class.java,name).addMigrations(RecoFiDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        val category=room.referenceData().loadCategories().single()
        assertEquals(stableCategoryId("外食"),category.id)
        assertEquals("外食",category.name)
        val transaction=room.transactions().loadAll().single()
        assertEquals(category.id,transaction.categoryId)
        val recurring=room.recurringExpenses().loadAll().single()
        assertEquals(category.id,recurring.categoryId)
        assertEquals(31,recurring.paymentDay)
        val revision=room.recurringExpenses().loadRevisions().single()
        assertEquals(10L,revision.recurringId)
        assertEquals(1280L,revision.amount)
        assertEquals(1,room.statements().loadEntries().size)
        val match=room.reconciliation().loadMatches().single()
        assertEquals("CONFIRMED",match.status)
        assertEquals(transaction.id,match.transactionId)
        room.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(false,it.moveToFirst()) }
        room.close()
        val reopened=Room.databaseBuilder(context,RecoFiDatabase::class.java,name).addMigrations(RecoFiDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        assertEquals(1,reopened.referenceData().loadCategories().size)
        assertEquals(1,reopened.transactions().loadAll().size)
        assertEquals(1,reopened.recurringExpenses().loadAll().size)
        assertEquals(1,reopened.recurringExpenses().loadRevisions().size)
        assertEquals(1,reopened.statements().loadEntries().size)
        assertEquals(1,reopened.reconciliation().loadMatches().size)
        reopened.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(false,it.moveToFirst()) }
        reopened.close()
    }
}
