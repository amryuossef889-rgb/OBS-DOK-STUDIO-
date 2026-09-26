package com.dokstudio.obs.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "studio_preferences")

class StudioPreferences(private val context: Context) {
    private object Keys {
        val lastProject = stringPreferencesKey("last_project")
        val defaultWidth = intPreferencesKey("default_width")
        val defaultHeight = intPreferencesKey("default_height")
        val defaultFps = intPreferencesKey("default_fps")
        val performanceProfile = stringPreferencesKey("performance_profile")
        val privacyTelemetry = booleanPreferencesKey("privacy_telemetry")
    }

    val lastProject: Flow<String?> = context.studioDataStore.data.map { it[Keys.lastProject] }
    val performanceProfile: Flow<String> =
        context.studioDataStore.data.map { it[Keys.performanceProfile] ?: "Balanced" }

    suspend fun setLastProject(id: String) = context.studioDataStore.edit { it[Keys.lastProject] = id }

    suspend fun setVideoDefaults(width: Int, height: Int, fps: Int) =
        context.studioDataStore.edit {
            it[Keys.defaultWidth] = width
            it[Keys.defaultHeight] = height
            it[Keys.defaultFps] = fps
        }

    suspend fun setPerformanceProfile(profile: String) =
        context.studioDataStore.edit { it[Keys.performanceProfile] = profile }

    suspend fun setTelemetryConsent(enabled: Boolean) =
        context.studioDataStore.edit { it[Keys.privacyTelemetry] = enabled }
}
