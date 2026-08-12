package jp.knaka.cardmemo.storage

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Schema migration test foundation. When database version 2 is introduced, add a test that
 * creates version 1 through this helper, applies MIGRATION_1_2, and validates every retained row.
 */
@RunWith(AndroidJUnit4::class)
abstract class RoomMigrationTestBase {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RecoFiDatabase::class.java,
    )
}
