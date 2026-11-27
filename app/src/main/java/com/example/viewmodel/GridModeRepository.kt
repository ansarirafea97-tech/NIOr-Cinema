package com.example.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GridMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "grid_settings")

class GridModeRepository(private val context: Context) {
    companion object {
        val KEY_GRID_MODE = stringPreferencesKey("grid_mode")
    }

    val gridModeFlow: Flow<GridMode> = context.dataStore.data
        .map { preferences ->
            val modeStr = preferences[KEY_GRID_MODE] ?: GridMode.COMFORTABLE.name
            try {
                GridMode.valueOf(modeStr)
            } catch (e: Exception) {
                GridMode.COMFORTABLE
            }
        }

    suspend fun saveGridMode(selectedMode: GridMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GRID_MODE] = selectedMode.name
        }
    }
}
