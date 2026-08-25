package jp.knaka.cardmemo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

const val AUTO_BACKUP_PREFIX = "RecoFi_auto_"
const val AUTO_BACKUP_KEEP_COUNT = 7

internal fun autoBackupNamesToDelete(names: List<String>, prefix: String = AUTO_BACKUP_PREFIX, keepCount: Int = AUTO_BACKUP_KEEP_COUNT): List<String> =
    names.filter { it.startsWith(prefix) && it.endsWith(".json") }.sortedDescending().drop(keepCount)

class AutoBackupPermissionException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class AutoBackupOutcome(val fileName: String, val rotationWarning: String? = null)

interface AutoBackupDestination {
    fun writeAndReadBack(fileName: String, payload: String): String
    fun rotate(prefix: String, keepCount: Int): String?
}

class AutoBackupEngine(
    private val createPayload: () -> String,
    private val validate: (String) -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    fun run(destination: AutoBackupDestination): AutoBackupOutcome {
        val payload = createPayload()
        validate(payload)
        val fileName = AUTO_BACKUP_PREFIX + FILE_TIME.format(now().atZone(ZoneId.systemDefault())) + ".json"
        val readBack = destination.writeAndReadBack(fileName, payload)
        validate(readBack)
        require(readBack == payload) { "書き込んだバックアップが生成内容と一致しません。" }
        return AutoBackupOutcome(fileName, destination.rotate(AUTO_BACKUP_PREFIX, AUTO_BACKUP_KEEP_COUNT))
    }

    private companion object { val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss") }
}

class DocumentTreeBackupDestination(private val context: Context, private val treeUri: Uri) : AutoBackupDestination {
    private val resolver get() = context.contentResolver

    override fun writeAndReadBack(fileName: String, payload: String): String {
        ensurePersistedWritePermission()
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw AutoBackupPermissionException("自動バックアップの保存先を開けません。保存先を再選択してください。")
        if (!root.isDirectory || !root.canWrite()) throw AutoBackupPermissionException("自動バックアップの書込権限がありません。保存先を再選択してください。")
        val target = root.createFile("application/json", fileName)
            ?: throw IOException("バックアップファイルを作成できませんでした。")
        try {
            resolver.openOutputStream(target.uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(payload) }
                ?: throw IOException("バックアップファイルを書き込めませんでした。")
            return resolver.openInputStream(target.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IOException("書き込んだバックアップを再検証できませんでした。")
        } catch (error: SecurityException) {
            runCatching { target.delete() }
            throw AutoBackupPermissionException("自動バックアップの権限が失われました。保存先を再選択してください。", error)
        } catch (error: Exception) {
            runCatching { target.delete() }
            throw error
        }
    }

    override fun rotate(prefix: String, keepCount: Int): String? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return "保存先を開けないため、古い自動バックアップを整理できませんでした。"
        val oldFiles = try {
            root.listFiles().filter { file -> file.isFile && file.name?.startsWith(prefix) == true && file.name?.endsWith(".json") == true }.sortedByDescending { it.name }
        } catch (error: SecurityException) {
            throw AutoBackupPermissionException("自動バックアップの権限が失われました。保存先を再選択してください。", error)
        }
        val deleteNames = autoBackupNamesToDelete(oldFiles.mapNotNull { it.name }, prefix, keepCount).toSet()
        val failed = oldFiles.filter { it.name in deleteNames }.count { file -> runCatching { file.delete() }.getOrDefault(false).not() }
        return if (failed == 0) null else "保存先の制約により、古い自動バックアップ${failed}件を削除できませんでした。"
    }

    private fun ensurePersistedWritePermission() {
        val allowed = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }
        if (!allowed) throw AutoBackupPermissionException("自動バックアップの保存先権限がありません。保存先を再選択してください。")
    }
}

class AutoBackupRunner(
    private val settingsRepository: AutoBackupSettingsRepository,
    private val manager: BackupManager,
    private val destinationFactory: (Uri) -> AutoBackupDestination,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun runConfiguredBackup(): AutoBackupOutcome? {
        val settings = settingsRepository.settings.first()
        val uriText = settings.destinationUri ?: return null
        return try {
            val outcome = AutoBackupEngine(manager::createValidatedPayload, { manager.readAndValidate(it) }).run(destinationFactory(Uri.parse(uriText)))
            settingsRepository.recordSuccess(nowMillis(), outcome.rotationWarning)
            outcome
        } catch (error: AutoBackupPermissionException) {
            settingsRepository.recordPermissionLoss(error.message ?: "保存先の権限が失われました。")
            throw error
        } catch (error: Exception) {
            settingsRepository.recordFailure(error.message ?: "自動バックアップに失敗しました。")
            throw error
        }
    }
}
