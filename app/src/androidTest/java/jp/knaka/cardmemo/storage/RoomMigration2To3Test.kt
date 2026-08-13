package jp.knaka.cardmemo.storage

import android.database.sqlite.SQLiteDatabase
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigration2To3Test {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val name = "migration-2-3"

    private fun createV2Database(): SQLiteDatabase {
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile?.mkdirs()
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val schema = testContext.assets.open("jp.knaka.cardmemo.storage.RecoFiDatabase/2.json").bufferedReader().use { JSONObject(it.readText()) }
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        db.execSQL("PRAGMA foreign_keys=OFF")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
        }
        db.version = 2
        db.execSQL("PRAGMA foreign_keys=ON")
        return db
    }

    @Test fun migratesV2DataAndCreatesReconciliationTables() {
        createV2Database().apply {
            execSQL("INSERT INTO payment_sources(id,name,isCard,sortOrder) VALUES('card','楽天カード',1,0)")
            execSQL("INSERT INTO payment_sources(id,name,isCard,sortOrder) VALUES('cash','現金',0,1)")
            execSQL("INSERT INTO categories(name,sortOrder) VALUES('外食',0)")
            execSQL("INSERT INTO recurring_expenses(id,amount,category,merchant,description,billingDay,startMonth,contractDate,paymentSourceId,intervalMonths,endDate) VALUES(10,980,'外食','Netflix','動画',28,'2026-01','2026-01-01','card',1,NULL)")
            execSQL("INSERT INTO recurring_price_revisions(recurringId,effectiveDate,amount) VALUES(10,'2026-07-01',1280)")
            execSQL("INSERT INTO transactions(id,amount,category,merchant,description,usedAt,recurringId,paymentSourceId,confirmed,reconciledMonth,suggested) VALUES(1,500,'外食','スターバックス','コーヒー',0,NULL,'card',1,'2026-08',0)")
            execSQL("INSERT INTO imported_statements(fileHash,statementMonth,paymentSourceId,fileName) VALUES('hash','2026-08','card','statement.csv')")
            execSQL("INSERT INTO statement_entries(id,fileHash,rowOrder,date,amount,merchant,rawText) VALUES(100,'hash',0,'2026-08-10',500,'STARBUCKS','raw')")
            execSQL("INSERT INTO monthly_budgets(month,amount) VALUES('2026-08',300000)")
            execSQL("INSERT INTO app_budget_settings(id,defaultMonthlyBudget) VALUES(1,250000)")
            execSQL("INSERT INTO monthly_locks(month,lockedAt) VALUES('2026-08',123)")
            close()
        }

        val room = Room.databaseBuilder(context, RecoFiDatabase::class.java, name).addMigrations(RecoFiDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        val migrated = room.openHelper.writableDatabase
        migrated.query("SELECT type FROM payment_sources WHERE id='card'").use { it.moveToFirst(); assertEquals("CREDIT_CARD", it.getString(0)) }
        migrated.query("SELECT type FROM payment_sources WHERE id='cash'").use { it.moveToFirst(); assertEquals("OTHER", it.getString(0)) }
        migrated.query("SELECT merchant,description FROM transactions WHERE id=1").use { it.moveToFirst(); assertEquals("スターバックス", it.getString(0)); assertEquals("コーヒー", it.getString(1)) }
        migrated.query("PRAGMA table_info(transactions)").use { cursor -> val names=mutableSetOf<String>();while(cursor.moveToNext())names+=cursor.getString(cursor.getColumnIndexOrThrow("name"));assertEquals(false,"confirmed" in names);assertEquals(false,"reconciledMonth" in names);assertEquals(false,"suggested" in names) }
        migrated.query("SELECT amount,merchant,description FROM recurring_expenses WHERE id=10").use { it.moveToFirst();assertEquals(980,it.getInt(0));assertEquals("Netflix",it.getString(1));assertEquals("動画",it.getString(2)) }
        migrated.query("SELECT amount FROM recurring_price_revisions WHERE recurringId=10").use { it.moveToFirst();assertEquals(1280,it.getInt(0)) }
        migrated.query("SELECT name FROM categories").use { it.moveToFirst();assertEquals("外食",it.getString(0)) }
        migrated.query("SELECT fileName FROM imported_statements WHERE fileHash='hash'").use { it.moveToFirst();assertEquals("statement.csv",it.getString(0)) }
        migrated.query("SELECT status,transactionId FROM reconciliation_matches WHERE statementEntryId=100").use { it.moveToFirst();assertEquals("PENDING",it.getString(0));assertEquals(true,it.isNull(1)) }
        migrated.query("SELECT amount FROM monthly_budgets WHERE month='2026-08'").use { it.moveToFirst();assertEquals(300000,it.getInt(0)) }
        migrated.query("SELECT defaultMonthlyBudget FROM app_budget_settings WHERE id=1").use { it.moveToFirst();assertEquals(250000,it.getInt(0)) }
        migrated.query("SELECT COUNT(*) FROM monthly_locks").use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
        migrated.query("PRAGMA foreign_key_list(reconciliation_matches)").use { cursor -> var count=0;while(cursor.moveToNext())count++;assertEquals(2,count) }
        migrated.execSQL("INSERT INTO transactions(id,amount,category,merchant,description,usedAt,recurringId,paymentSourceId) VALUES(2,500,'外食','別候補','',0,NULL,'card')")
        migrated.execSQL("UPDATE reconciliation_matches SET transactionId=1,status='CONFIRMED' WHERE statementEntryId=100")
        migrated.execSQL("INSERT INTO statement_entries(id,fileHash,rowOrder,date,amount,merchant,rawText) VALUES(101,'hash',1,'2026-08-11',500,'STARBUCKS','raw2')")
        migrated.execSQL("INSERT INTO reconciliation_matches(statementEntryId,transactionId,status,createdAt,updatedAt) VALUES(101,NULL,'PENDING',0,0)")
        var uniqueRejected=false;try{migrated.execSQL("UPDATE reconciliation_matches SET transactionId=1,status='CONFIRMED' WHERE statementEntryId=101")}catch(_:Exception){uniqueRejected=true};assertEquals(true,uniqueRejected)
        room.close()

        val reopened=Room.databaseBuilder(context,RecoFiDatabase::class.java,name).addMigrations(RecoFiDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        reopened.openHelper.writableDatabase.query("SELECT COUNT(*) FROM transactions").use { it.moveToFirst();assertEquals(2,it.getInt(0)) }
        reopened.close()
    }
}
