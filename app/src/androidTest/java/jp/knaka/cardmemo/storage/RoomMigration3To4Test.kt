package jp.knaka.cardmemo.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigration3To4Test {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val name = "migration-3-4"

    private fun createV3(): SQLiteDatabase {
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile?.mkdirs()
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val schema = testContext.assets.open("jp.knaka.cardmemo.storage.RecoFiDatabase/3.json").bufferedReader().use { JSONObject(it.readText()) }
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indexes = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        db.execSQL("INSERT INTO payment_sources(id,name,type,sortOrder,isCard) VALUES('card','楽天カード','CREDIT_CARD',0,1)")
        db.execSQL("INSERT INTO app_budget_settings(id,defaultMonthlyBudget) VALUES(1,250000)")
        db.version = 3
        db.close()
        return SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
    }

    @Test fun migratesV3SettingsThroughV5AndCanReopen() {
        createV3().close()
        val room = Room.databaseBuilder(context, RecoFiDatabase::class.java, name).addMigrations(RecoFiDatabase.MIGRATION_3_4,RecoFiDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        assertEquals(250000L, room.monthlyState().loadBudgetSettings()!!.defaultMonthlyBudget)
        assertNull(room.monthlyState().loadBudgetSettings()!!.defaultPaymentSourceId)
        room.monthlyState().upsertBudgetSettings(AppBudgetSettingsEntity(defaultMonthlyBudget=250000, defaultPaymentSourceId="card"))
        room.close()
        val reopened = Room.databaseBuilder(context, RecoFiDatabase::class.java, name).addMigrations(RecoFiDatabase.MIGRATION_3_4,RecoFiDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        assertEquals("card", reopened.monthlyState().loadBudgetSettings()!!.defaultPaymentSourceId)
        reopened.close()
    }
}
