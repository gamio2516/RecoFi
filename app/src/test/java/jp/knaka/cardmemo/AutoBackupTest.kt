package jp.knaka.cardmemo

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.time.Instant
import jp.knaka.cardmemo.storage.CategoryEntity
import jp.knaka.cardmemo.storage.RecoFiDatabase
import jp.knaka.cardmemo.storage.StorageProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        StorageProvider.resetForTest()
        context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)
    }

    @After fun cleanup() {
        StorageProvider.resetForTest()
        context.deleteDatabase(RecoFiDatabase.DATABASE_NAME)
    }

    @Test fun settingsPersistEnabledDestinationAndPermissionLossSafely() = runBlocking {
        val file = context.cacheDir.resolve("auto-backup-${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create { file }
        val repository = AutoBackupSettingsRepository.forTest(store)
        repository.setDestination("content://provider/tree/recofi")
        repository.setEnabled(true)
        assertEquals(AutoBackupSettings(true, "content://provider/tree/recofi"), repository.settings.first())
        repository.recordPermissionLoss("permission lost")
        val stopped = repository.settings.first()
        assertFalse(stopped.enabled)
        assertTrue(stopped.permissionNeedsReselection)
        assertEquals("permission lost", stopped.lastError)
    }

    @Test fun workConditionRequiresEnabledDestinationAndValidPermission() {
        assertFalse(AutoBackupScheduler.shouldSchedule(AutoBackupSettings()))
        assertFalse(AutoBackupScheduler.shouldSchedule(AutoBackupSettings(enabled = true)))
        assertFalse(AutoBackupScheduler.shouldSchedule(AutoBackupSettings(true, "content://tree", permissionNeedsReselection = true)))
        assertTrue(AutoBackupScheduler.shouldSchedule(AutoBackupSettings(true, "content://tree")))
        assertEquals(24L, AutoBackupScheduler.spec.repeatHours)
        assertTrue(AutoBackupScheduler.spec.requiresBatteryNotLow)
        assertTrue(AutoBackupScheduler.spec.requiresStorageNotLow)
    }

    @Test fun backupManagerGeneratesValidatedV5Payload() {
        seedDatabase()
        val payload = BackupManager(context).createValidatedPayload()
        assertEquals(5, JSONObject(payload).getInt("backupFormatVersion"))
        assertNotNull(BackupCodec.decodeAndValidate(payload))
    }

    @Test fun writeFailureDoesNotChangeRoom() {
        val db = seedDatabase()
        val before = db.referenceData().loadCategories()
        val destination = FakeDestination(writeError = IOException("disk full"))
        val engine = AutoBackupEngine(BackupManager(context)::createValidatedPayload, { BackupCodec.decodeAndValidate(it) })
        assertTrue(runCatching { engine.run(destination) }.isFailure)
        assertEquals(before, db.referenceData().loadCategories())
    }

    @Test fun corruptReadBackIsNeverSuccessful() {
        seedDatabase()
        val destination = FakeDestination(readBackOverride = "{broken")
        val engine = AutoBackupEngine(BackupManager(context)::createValidatedPayload, { BackupCodec.decodeAndValidate(it) })
        assertTrue(runCatching { engine.run(destination) }.isFailure)
        assertFalse(destination.rotated)
    }

    @Test fun invalidGeneratedPayloadIsRejectedBeforeWrite() {
        val destination = FakeDestination()
        val engine = AutoBackupEngine({ "invalid" }, { require(it == "valid") })
        assertTrue(runCatching { engine.run(destination) }.isFailure)
        assertEquals(0, destination.writeCount)
    }

    @Test fun successfulBackupVerifiesAndRequestsSevenGenerationRotation() {
        val destination = FakeDestination()
        val engine = AutoBackupEngine({ "valid" }, { require(it == "valid") }, { Instant.parse("2026-08-25T14:00:00Z") })
        val result = engine.run(destination)
        assertTrue(result.fileName.startsWith(AUTO_BACKUP_PREFIX))
        assertEquals(AUTO_BACKUP_KEEP_COUNT, destination.keepCount)
        assertTrue(destination.rotated)
    }

    @Test fun rotationKeepsNewestSevenAndIgnoresUnrelatedFiles() {
        val backups = (1..10).map { "$AUTO_BACKUP_PREFIX${it.toString().padStart(2, '0')}.json" }
        assertEquals(backups.take(3).reversed(), autoBackupNamesToDelete(backups + "other.json"))
    }

    @Test fun permissionLossStopsWithoutBeingTreatedAsSuccess() {
        val destination = FakeDestination(writeError = AutoBackupPermissionException("lost"))
        val engine = AutoBackupEngine({ "valid" }, { require(it == "valid") })
        val error = runCatching { engine.run(destination) }.exceptionOrNull()
        assertTrue(error is AutoBackupPermissionException)
    }

    @Test fun runnerDisablesAutomaticBackupWhenDestinationPermissionIsLost() = runBlocking {
        seedDatabase()
        val file = context.cacheDir.resolve("auto-backup-runner-${System.nanoTime()}.preferences_pb")
        val repository = AutoBackupSettingsRepository.forTest(PreferenceDataStoreFactory.create { file })
        repository.setDestination("content://provider/tree/recofi");repository.setEnabled(true)
        val runner = AutoBackupRunner(repository, BackupManager(context), { FakeDestination(writeError = AutoBackupPermissionException("lost")) })
        assertTrue(runCatching { runner.runConfiguredBackup() }.exceptionOrNull() is AutoBackupPermissionException)
        val settings = repository.settings.first()
        assertFalse(settings.enabled)
        assertTrue(settings.permissionNeedsReselection)
    }

    @Test fun recurringAmountLabelIsUserFacingAmount() = assertEquals("金額", RECURRING_AMOUNT_LABEL)

    private fun seedDatabase(): RecoFiDatabase = StorageProvider.database(context).also { db ->
        if (db.referenceData().loadCategories().isEmpty()) db.referenceData().upsertCategories(listOf(CategoryEntity("food", "食費", 0)))
    }

    private class FakeDestination(
        private val writeError: Exception? = null,
        private val readBackOverride: String? = null,
    ) : AutoBackupDestination {
        var writeCount = 0
        var rotated = false
        var keepCount = 0
        override fun writeAndReadBack(fileName: String, payload: String): String {
            writeCount++
            writeError?.let { throw it }
            return readBackOverride ?: payload
        }
        override fun rotate(prefix: String, keepCount: Int): String? {
            rotated = true
            this.keepCount = keepCount
            return null
        }
    }
}
