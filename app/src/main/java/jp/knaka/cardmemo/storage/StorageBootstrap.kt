package jp.knaka.cardmemo.storage

import android.content.Context
import kotlinx.coroutines.runBlocking

sealed interface StorageBootstrapResult {
    data class RoomReady(val database: RecoFiDatabase, val migratedNow: Boolean, val corrections: List<String>) : StorageBootstrapResult
    data class LegacyRecovery(val reason: String) : StorageBootstrapResult
}

/**
 * Opens Room and migrates the retained legacy source. Any database/open/metadata/parser failure
 * leaves SharedPreferences untouched and returns an explicit legacy recovery path.
 */
class StorageBootstrap(private val context: Context) {
    fun open(): StorageBootstrapResult {
        val preferences = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        val snapshot = LegacySnapshot(preferences.all.toMap())
        val parsed = LegacyMigrationParser().parse(snapshot)
        if (parsed is LegacyParseResult.Failure) return StorageBootstrapResult.LegacyRecovery(parsed.errors.joinToString("\n"))
        parsed as LegacyParseResult.Success

        val database = runCatching { RecoFiDatabase.open(context).also { it.openHelper.writableDatabase } }
            .getOrElse { return StorageBootstrapResult.LegacyRecovery("Room DBを開けません: ${it.message}") }
        return try {
            val prior = runCatching { database.migrations().find(LegacyRoomMigrator.MIGRATION_VERSION) }
                .getOrElse { database.close(); return StorageBootstrapResult.LegacyRecovery("移行管理情報を読めません: ${it.message}") }
            if (prior != null) {
                if (prior.legacyFingerprint != snapshot.fingerprint || !prior.sourceRetained) {
                    database.close()
                    StorageBootstrapResult.LegacyRecovery("移行管理情報とLegacy原本が一致しません")
                } else StorageBootstrapResult.RoomReady(database, migratedNow = false, parsed.data.corrections)
            } else {
                when (val migration = runBlocking { LegacyRoomMigrator().migrate(database, parsed.data) }) {
                    StorageMigrationResult.Migrated -> StorageBootstrapResult.RoomReady(database, migratedNow = true, parsed.data.corrections)
                    StorageMigrationResult.AlreadyMigrated -> StorageBootstrapResult.RoomReady(database, migratedNow = false, parsed.data.corrections)
                    is StorageMigrationResult.Failed -> { database.close(); StorageBootstrapResult.LegacyRecovery(migration.reason) }
                }
            }
        } catch (error: Throwable) {
            database.close()
            StorageBootstrapResult.LegacyRecovery(error.message ?: error::class.java.simpleName)
        }
    }

    companion object { const val LEGACY_PREFERENCES = "card_memo" }
}
