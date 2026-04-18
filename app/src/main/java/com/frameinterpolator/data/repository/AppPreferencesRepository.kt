package com.frameinterpolator.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.frameinterpolator.data.model.AppPreferences
import com.frameinterpolator.data.model.ProcessingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "frameforge_preferences")

class AppPreferencesRepository(
    private val context: Context
) {
    val preferences: Flow<AppPreferences> = context.appPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            AppPreferences(
                defaultQuality = prefs[DEFAULT_QUALITY_KEY]
                    ?.let(ProcessingConfig.QualityPreset::valueOf)
                    ?: AppPreferences().defaultQuality,
                notifyOnCompletion = prefs[NOTIFY_ON_COMPLETION_KEY] ?: true,
                rememberLastSelections = prefs[REMEMBER_LAST_SELECTIONS_KEY] ?: true,
                enableDetailedDiagnostics = prefs[DETAILED_DIAGNOSTICS_KEY] ?: true
            )
        }

    suspend fun updateDefaultQuality(quality: ProcessingConfig.QualityPreset) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[DEFAULT_QUALITY_KEY] = quality.name
        }
    }

    suspend fun updateNotifyOnCompletion(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[NOTIFY_ON_COMPLETION_KEY] = enabled
        }
    }

    suspend fun updateRememberLastSelections(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[REMEMBER_LAST_SELECTIONS_KEY] = enabled
        }
    }

    suspend fun updateDetailedDiagnostics(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[DETAILED_DIAGNOSTICS_KEY] = enabled
        }
    }

    private companion object {
        val DEFAULT_QUALITY_KEY = stringPreferencesKey("default_quality")
        val NOTIFY_ON_COMPLETION_KEY = booleanPreferencesKey("notify_on_completion")
        val REMEMBER_LAST_SELECTIONS_KEY = booleanPreferencesKey("remember_last_selections")
        val DETAILED_DIAGNOSTICS_KEY = booleanPreferencesKey("enable_detailed_diagnostics")
    }
}
