package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "infuse_favorites_datastore")

/**
 * DataStore-backed persistent repository for managing user's favorite movies and TV series.
 * Operates reactively and safely with coroutines and Flow.
 */
class FavoritesRepository(private val context: Context) {

    companion object {
        val KEY_FAVORITE_MOVIE_IDS = stringSetPreferencesKey("favorite_movie_ids")
    }

    /**
     * Flow emitting the set of favorite movie/series IDs whenever updated.
     */
    val favoritesFlow: Flow<Set<String>> = context.favoritesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_FAVORITE_MOVIE_IDS] ?: emptySet()
        }

    /**
     * Toggles the favorite status for a given movie/series ID.
     */
    suspend fun toggleFavorite(movieId: String) {
        if (movieId.isBlank()) return
        context.favoritesDataStore.edit { preferences ->
            val currentFavorites = preferences[KEY_FAVORITE_MOVIE_IDS] ?: emptySet()
            if (currentFavorites.contains(movieId)) {
                preferences[KEY_FAVORITE_MOVIE_IDS] = currentFavorites - movieId
            } else {
                preferences[KEY_FAVORITE_MOVIE_IDS] = currentFavorites + movieId
            }
        }
    }

    /**
     * Explicitly sets the favorite status for a movie/series ID.
     */
    suspend fun setFavorite(movieId: String, isFavorite: Boolean) {
        if (movieId.isBlank()) return
        context.favoritesDataStore.edit { preferences ->
            val currentFavorites = preferences[KEY_FAVORITE_MOVIE_IDS] ?: emptySet()
            if (isFavorite) {
                preferences[KEY_FAVORITE_MOVIE_IDS] = currentFavorites + movieId
            } else {
                preferences[KEY_FAVORITE_MOVIE_IDS] = currentFavorites - movieId
            }
        }
    }

    /**
     * Clears all stored favorites.
     */
    suspend fun clearFavorites() {
        context.favoritesDataStore.edit { preferences ->
            preferences[KEY_FAVORITE_MOVIE_IDS] = emptySet()
        }
    }
}
