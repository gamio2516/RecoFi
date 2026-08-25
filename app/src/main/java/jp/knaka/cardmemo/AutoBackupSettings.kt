package jp.knaka.cardmemo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class AutoBackupSettings(
    val enabled: Boolean = false,
    val destinationUri: String? = null,
    val lastSuccessEpochMillis: Long? = null,
    val lastError: String? = null,
    val permissionNeedsReselection: Boolean = false,
)

private val Context.autoBackupDataStore by preferencesDataStore(name = "auto_backup_settings")

class AutoBackupSettingsRepository private constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.autoBackupDataStore)

    val settings: Flow<AutoBackupSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            AutoBackupSettings(
                enabled = preferences[ENABLED] ?: false,
                destinationUri = preferences[DESTINATION_URI],
                lastSuccessEpochMillis = preferences[LAST_SUCCESS],
                lastError = preferences[LAST_ERROR],
                permissionNeedsReselection = preferences[PERMISSION_NEEDS_RESELECTION] ?: false,
            )
        }

    suspend fun setDestination(uri: String) = store.edit {
        it[DESTINATION_URI] = uri
        it[PERMISSION_NEEDS_RESELECTION] = false
        it.remove(LAST_ERROR)
    }

    suspend fun setEnabled(enabled: Boolean) = store.edit { it[ENABLED] = enabled }

    suspend fun recordSuccess(epochMillis: Long, warning: String? = null) = store.edit {
        it[LAST_SUCCESS] = epochMillis
        it[PERMISSION_NEEDS_RESELECTION] = false
        if (warning == null) it.remove(LAST_ERROR) else it[LAST_ERROR] = warning
    }

    suspend fun recordFailure(message: String) = store.edit { it[LAST_ERROR] = message }

    suspend fun recordPermissionLoss(message: String) = store.edit {
        it[ENABLED] = false
        it[PERMISSION_NEEDS_RESELECTION] = true
        it[LAST_ERROR] = message
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("enabled")
        private val DESTINATION_URI = stringPreferencesKey("destination_uri")
        private val LAST_SUCCESS = longPreferencesKey("last_success_epoch_millis")
        private val LAST_ERROR = stringPreferencesKey("last_error")
        private val PERMISSION_NEEDS_RESELECTION = booleanPreferencesKey("permission_needs_reselection")

        internal fun forTest(store: DataStore<Preferences>) = AutoBackupSettingsRepository(store)
    }
}
