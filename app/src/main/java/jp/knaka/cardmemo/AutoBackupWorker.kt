package jp.knaka.cardmemo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AutoBackupWorkSpec(val repeatHours: Long = 24, val requiresBatteryNotLow: Boolean = true, val requiresStorageNotLow: Boolean = true)

object AutoBackupScheduler {
    const val PERIODIC_WORK_NAME = "recofi-auto-backup-daily"
    const val IMMEDIATE_WORK_NAME = "recofi-auto-backup-now"
    const val FORCE_INPUT_KEY = "force"
    val spec = AutoBackupWorkSpec()

    fun shouldSchedule(settings: AutoBackupSettings) = settings.enabled && !settings.destinationUri.isNullOrBlank() && !settings.permissionNeedsReselection

    fun apply(context: Context, settings: AutoBackupSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!shouldSchedule(settings)) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(spec.requiresBatteryNotLow).setRequiresStorageNotLow(spec.requiresStorageNotLow).build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(spec.repeatHours, TimeUnit.HOURS).setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().setInputData(workDataOf(FORCE_INPUT_KEY to true)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class AutoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = AutoBackupSettingsRepository(applicationContext)
        val settings = repository.settings.first()
        val forced = inputData.getBoolean(AutoBackupScheduler.FORCE_INPUT_KEY, false)
        if ((!forced && !AutoBackupScheduler.shouldSchedule(settings)) || settings.destinationUri.isNullOrBlank() || settings.permissionNeedsReselection) return@withContext Result.success()
        val runner = AutoBackupRunner(
            settingsRepository = repository,
            manager = BackupManager(applicationContext),
            destinationFactory = { uri ->
                DocumentTreeBackupDestination(applicationContext, uri)
            },
        )
        try {
            runner.runConfiguredBackup()
            Result.success()
        } catch (_: AutoBackupPermissionException) {
            AutoBackupScheduler.apply(applicationContext, repository.settings.first())
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }
}

class AutoBackupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AutoBackupSettingsRepository(application)
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupSettings())

    init { viewModelScope.launch { AutoBackupScheduler.apply(getApplication<Application>(), repository.settings.first()) } }

    fun selectDestination(uri: Uri) = viewModelScope.launch {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            repository.setDestination(uri.toString())
            AutoBackupScheduler.apply(getApplication<Application>(), repository.settings.first())
        } catch (error: Exception) {
            repository.recordPermissionLoss("保存先の権限を保持できませんでした。保存先を再選択してください。")
        }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        val current = repository.settings.first()
        if (enabled && current.destinationUri.isNullOrBlank()) {
            repository.recordFailure("先に自動バックアップの保存先を選択してください。")
            return@launch
        }
        repository.setEnabled(enabled)
        AutoBackupScheduler.apply(getApplication<Application>(), repository.settings.first())
    }

    fun runNow() = viewModelScope.launch {
        val current = repository.settings.first()
        if (current.destinationUri.isNullOrBlank()) repository.recordFailure("先に自動バックアップの保存先を選択してください。")
        else AutoBackupScheduler.runNow(getApplication<Application>())
    }
}
