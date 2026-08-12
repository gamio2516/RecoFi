package jp.knaka.cardmemo

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import jp.knaka.cardmemo.storage.LegacyMigrationParser
import jp.knaka.cardmemo.storage.LegacyParseResult
import jp.knaka.cardmemo.storage.LegacySnapshot
import jp.knaka.cardmemo.storage.RoomBackupAdapter
import jp.knaka.cardmemo.storage.StorageBootstrapResult
import jp.knaka.cardmemo.storage.StorageProvider
import kotlinx.coroutines.runBlocking

class BackupManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val appVersion: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    fun writeBackup(output: OutputStream) {
        val current = currentPreferences()
        val content = BackupCodec.encode(current, appVersion)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    fun readAndValidate(text: String): ValidatedBackup = BackupCodec.decodeAndValidate(text)

    fun restore(backup: ValidatedBackup): File {
        val storage = StorageProvider.get(context)
        val current = currentPreferences()
        val safetyBackup = writeSafetyBackup(current)
        if (storage is StorageBootstrapResult.RoomReady) {
            val parsed = LegacyMigrationParser().parse(LegacySnapshot(backup.preferences))
            val data = when (parsed) {
                is LegacyParseResult.Success -> parsed.data
                is LegacyParseResult.Failure -> throw IllegalArgumentException(parsed.errors.joinToString("\n"))
            }
            runBlocking { RoomBackupAdapter.replace(storage.database, data) }
            return safetyBackup
        }
        val editor = preferences.edit().clear().also { replacement -> backup.preferences.forEach { (key, value) -> replacement.putValue(key, value) } }
        val replaced = runCatching { editor.commit() }.getOrElse { error -> rollback(safetyBackup); throw IllegalStateException("データの置換に失敗したため、元のデータを維持しました", error) }
        if (!replaced) { rollback(safetyBackup); throw IllegalStateException("データの置換に失敗したため、元のデータを維持しました") }
        return safetyBackup
    }

    private fun currentPreferences(): Map<String, Any> = when (val storage = StorageProvider.get(context)) {
        is StorageBootstrapResult.RoomReady -> RoomBackupAdapter.exportPreferences(storage.database)
        is StorageBootstrapResult.LegacyRecovery -> preferences.all.mapValues { (_, value) -> copyValue(value) }
    }

    private fun writeSafetyBackup(current: Map<String, Any>): File {
        val directory = File(context.filesDir, "restore-safety-backups")
        check((directory.exists() || directory.mkdirs()) && directory.isDirectory) { "復元前バックアップ用フォルダを作成できません" }
        val timestamp = Instant.now().toString()
        val target = File(directory, "RecoFi_before_restore_${timestamp.replace(':', '-')}.json")
        val temporary = File(directory, ".${target.name}.tmp")
        runCatching {
            FileOutputStream(temporary).use { output ->
                output.write(BackupCodec.encode(current, appVersion, timestamp).toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            check(temporary.length() > 0L) { "復元前バックアップが空です" }
            check(temporary.renameTo(target)) { "復元前バックアップを確定できません" }
        }.onFailure { temporary.delete() }.getOrThrow()
        check(target.isFile && target.length() > 0L) { "復元前バックアップの作成を確認できません" }
        BackupCodec.decodeAndValidate(target.readText(Charsets.UTF_8))
        return target
    }

    private fun rollback(safetyBackup: File) {
        val current = BackupCodec.decodeAndValidate(safetyBackup.readText(Charsets.UTF_8)).preferences
        val rollback = preferences.edit().clear().also { editor -> current.forEach { (key, value) -> editor.putValue(key, value) } }
        check(rollback.commit()) { "復元失敗後に元データを再適用できませんでした" }
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor = when (value) {
        is String -> putString(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Boolean -> putBoolean(key, value)
        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, (value as Set<String>).toSet())
        else -> throw IllegalArgumentException("対応していない設定値です: $key")
    }

    private fun copyValue(value: Any?): Any = when (value) {
        is Set<*> -> value.map { it as String }.toSet()
        is String, is Int, is Long, is Float, is Boolean -> value
        else -> throw IllegalArgumentException("現在データに対応していない値があります")
    }

    companion object { private const val PREFERENCES_NAME = "card_memo" }
}
