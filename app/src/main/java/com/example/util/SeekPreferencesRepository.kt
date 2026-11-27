package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.seekPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "infuse_seek_preferences"
)

/**
 * DataStore-backed repository for continuous double-tap seek mode, seek duration, and swipe gesture sensitivity preferences.
 */
class SeekPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_CONTINUOUS_SEEK_ENABLED = booleanPreferencesKey("continuous_seek_enabled")
        val KEY_SEEK_DURATION_SECONDS = intPreferencesKey("seek_duration_seconds")
        val KEY_GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")

        val SUPPORTED_SEEK_DURATIONS = listOf(5, 10, 15)
        val ALLOWED_SEEK_DURATIONS = SUPPORTED_SEEK_DURATIONS
        const val DEFAULT_CONTINUOUS_SEEK_ENABLED = true
        const val DEFAULT_SEEK_DURATION_SECONDS = 10
        const val DEFAULT_GESTURE_SENSITIVITY = 1.0f
        const val MIN_GESTURE_SENSITIVITY = 0.5f
        const val MAX_GESTURE_SENSITIVITY = 2.0f

        fun normalizeSeekDuration(seconds: Int): Int {
            return if (seconds in SUPPORTED_SEEK_DURATIONS) seconds else DEFAULT_SEEK_DURATION_SECONDS
        }

        fun normalizeGestureSensitivity(sensitivity: Float): Float {
            return sensitivity.coerceIn(MIN_GESTURE_SENSITIVITY, MAX_GESTURE_SENSITIVITY)
        }
    }

    val continuousSeekEnabledFlow: Flow<Boolean> = context.seekPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_CONTINUOUS_SEEK_ENABLED] ?: DEFAULT_CONTINUOUS_SEEK_ENABLED
        }

    val seekDurationSecondsFlow: Flow<Int> = context.seekPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[KEY_SEEK_DURATION_SECONDS] ?: DEFAULT_SEEK_DURATION_SECONDS
            normalizeSeekDuration(raw)
        }

    val gestureSensitivityFlow: Flow<Float> = context.seekPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[KEY_GESTURE_SENSITIVITY] ?: DEFAULT_GESTURE_SENSITIVITY
            normalizeGestureSensitivity(raw)
        }

    suspend fun setContinuousSeekEnabled(enabled: Boolean) {
        context.seekPreferencesDataStore.edit { preferences ->
            preferences[KEY_CONTINUOUS_SEEK_ENABLED] = enabled
        }
    }

    suspend fun setSeekDurationSeconds(seconds: Int) {
        val validSeconds = normalizeSeekDuration(seconds)
        context.seekPreferencesDataStore.edit { preferences ->
            preferences[KEY_SEEK_DURATION_SECONDS] = validSeconds
        }
    }

    suspend fun setGestureSensitivity(sensitivity: Float) {
        val valid = normalizeGestureSensitivity(sensitivity)
        context.seekPreferencesDataStore.edit { preferences ->
            preferences[KEY_GESTURE_SENSITIVITY] = valid
        }
    }
}
