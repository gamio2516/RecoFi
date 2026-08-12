package jp.knaka.cardmemo.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageBootstrapTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)
        context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() { context.deleteDatabase(RecoFiDatabase.DATABASE_NAME) }

    @Test fun validLegacySourceMigratesAndRemainsUntouched() {
        val prefs = context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().putInt("default_monthly_budget", 300000).commit()
        val before = prefs.all.toMap()
        val result = StorageBootstrap(context).open()
        assertTrue(result is StorageBootstrapResult.RoomReady && result.migratedNow)
        (result as StorageBootstrapResult.RoomReady).database.close()
        assertEquals(before, prefs.all.toMap())
    }

    @Test fun secondOpenUsesRoomWithoutDuplicatingMigration() {
        val first = StorageBootstrap(context).open() as StorageBootstrapResult.RoomReady
        first.database.close()
        val second = StorageBootstrap(context).open()
        assertTrue(second is StorageBootstrapResult.RoomReady && !second.migratedNow)
        (second as StorageBootstrapResult.RoomReady).database.close()
    }

    @Test fun invalidLegacyStopsBeforeRoomIsCreated() {
        val prefs = context.getSharedPreferences(StorageBootstrap.LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().putString("locked_months", "[\"bad\"]").commit()
        val result = StorageBootstrap(context).open()
        assertTrue(result is StorageBootstrapResult.LegacyRecovery)
        assertTrue(!context.getDatabasePath(RecoFiDatabase.DATABASE_NAME).exists())
        assertEquals("[\"bad\"]", prefs.getString("locked_months", null))
    }
}
