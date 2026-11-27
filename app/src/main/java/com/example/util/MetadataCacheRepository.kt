package com.example.util

import android.content.Context
import android.util.Log
import com.example.model.AtomicMediaMetadata
import com.example.model.MovieMetadata
import com.example.model.TvShowMetadata
import com.example.model.TvSeasonMetadata
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class MetadataCacheRepository(context: Context) {

    private val prefs = context.getSharedPreferences("infuse_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val atomicAdapter = moshi.adapter(AtomicMediaMetadata::class.java)

    companion object {
        private const val TAG = "MEDIA_METADATA"
        private const val ARTWORK_DEBUG = "ARTWORK_MATCH_DEBUG"
        private const val ATOMIC_KEY_PREFIX = "atomic_meta_v4_"
        private const val TV_SEASON_KEY_PREFIX = "tv_season_meta_v4_"
        private const val TV_EPISODE_KEY_PREFIX = "tv_ep_meta_v4_"
        private const val MIGRATION_KEY = "genre_migration_v9"
    }

    private val seasonAdapter = moshi.adapter(TvSeasonMetadata::class.java)
    private val atomicMemoryCache = java.util.concurrent.ConcurrentHashMap<String, AtomicMediaMetadata>()
    private val tvEpisodeMemoryCache = java.util.concurrent.ConcurrentHashMap<String, AtomicMediaMetadata>()
    private val tvSeasonMemoryCache = java.util.concurrent.ConcurrentHashMap<String, TvSeasonMetadata>()

    init {
        performMigrationIfNeeded()
    }

    /**
     * Cache migration: Invalidates and removes all old legacy/title-based genre keys while preserving local files and watch history.
     */
    private fun performMigrationIfNeeded() {
        val migrated = prefs.getBoolean(MIGRATION_KEY, false)
        if (!migrated) {
            try {
                val editor = prefs.edit()
                val allKeys = prefs.all.keys.toList()
                var removedCount = 0
                for (key in allKeys) {
                    if (key.startsWith("tmdb_genre_") ||
                        key.endsWith(":genres") ||
                        key.startsWith("atomic_meta_v3_") ||
                        key.startsWith("tv_ep_meta_v3_")) {
                        if (!key.contains("/") && !key.contains("content:") && !key.contains("file:") && !key.contains("http")) {
                            editor.remove(key)
                            removedCount++
                        }
                    }
                }
                editor.putBoolean(MIGRATION_KEY, true)
                editor.apply()
                Log.d(TAG, "Cache migration v9 completed: removed $removedCount legacy/genre cache entries.")
            } catch (e: Exception) {
                Log.e(TAG, "Migration error: ${e.message}")
            }
        }
    }

    /**
     * Saves genre list explicitly keyed by media type and TMDB ID (movie:<tmdbId>:genres or tv:<tmdbId>:genres).
     */
    fun saveTmdbGenres(tmdbId: Int, isTv: Boolean, genres: List<String>) {
        if (tmdbId <= 0 || genres.isEmpty()) return
        val key = if (isTv) "tv:$tmdbId:genres" else "movie:$tmdbId:genres"
        try {
            val json = genres.joinToString("|||")
            prefs.edit().putString(key, json).apply()
            Log.d("GENRE_DEBUG", "Saved genres to cache key '$key': $genres")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save genres for key '$key': ${e.message}")
        }
    }

    /**
     * Retrieves genre list explicitly keyed by media type and TMDB ID.
     */
    fun getTmdbGenres(tmdbId: Int, isTv: Boolean): List<String> {
        if (tmdbId <= 0) return emptyList()
        val key = if (isTv) "tv:$tmdbId:genres" else "movie:$tmdbId:genres"
        val raw = prefs.getString(key, null) ?: return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    /**
     * Saves episode metadata isolated by TMDB TV ID, season, and episode numbers.
     */
    fun saveTvEpisodeMetadata(tvId: Int, seasonNumber: Int, episodeNumber: Int, metadata: AtomicMediaMetadata) {
        if (tvId <= 0) return
        val key = "$TV_EPISODE_KEY_PREFIX${tvId}_s${seasonNumber}_e${episodeNumber}"
        tvEpisodeMemoryCache[key] = metadata
        try {
            val json = atomicAdapter.toJson(metadata)
            prefs.edit().putString(key, json).apply()
            Log.d(TAG, "Saved TV episode metadata for cache key='$key', epName='${metadata.episode?.episodeTitle}'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save TV episode metadata for cache key='$key': ${e.message}")
        }
    }

    /**
     * Retrieves episode metadata isolated by TMDB TV ID, season, and episode numbers.
     */
    fun getTvEpisodeMetadata(tvId: Int, seasonNumber: Int, episodeNumber: Int): AtomicMediaMetadata? {
        if (tvId <= 0) return null
        val key = "$TV_EPISODE_KEY_PREFIX${tvId}_s${seasonNumber}_e${episodeNumber}"
        tvEpisodeMemoryCache[key]?.let { return it }
        val json = prefs.getString(key, null) ?: return null
        return try {
            val parsed = atomicAdapter.fromJson(json)
            if (parsed != null) {
                tvEpisodeMemoryCache[key] = parsed
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TV episode metadata for cache key='$key': ${e.message}")
            null
        }
    }

    /**
     * Saves season metadata isolated by TMDB TV ID and season number.
     */
    fun saveTvSeasonMetadata(tvId: Int, seasonNumber: Int, metadata: TvSeasonMetadata) {
        if (tvId <= 0 || seasonNumber <= 0) return
        val key = "$TV_SEASON_KEY_PREFIX${tvId}_s${seasonNumber}"
        tvSeasonMemoryCache[key] = metadata
        try {
            val json = seasonAdapter.toJson(metadata)
            prefs.edit().putString(key, json).apply()
            Log.d(ARTWORK_DEBUG, "Saved TV season metadata for cache key='$key', poster='${metadata.posterPath}'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save TV season metadata for cache key='$key': ${e.message}")
        }
    }

    /**
     * Retrieves season metadata isolated by TMDB TV ID and season number.
     */
    fun getTvSeasonMetadata(tvId: Int, seasonNumber: Int): TvSeasonMetadata? {
        if (tvId <= 0 || seasonNumber <= 0) {
            return null
        }
        val key = "$TV_SEASON_KEY_PREFIX${tvId}_s${seasonNumber}"
        tvSeasonMemoryCache[key]?.let { return it }
        val json = prefs.getString(key, null)
        if (json.isNullOrBlank()) {
            return null
        }
        return try {
            val meta = seasonAdapter.fromJson(json)
            if (meta != null && meta.showTmdbId == tvId && meta.seasonNumber == seasonNumber) {
                tvSeasonMemoryCache[key] = meta
                meta
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TV season metadata for cache key='$key': ${e.message}")
            null
        }
    }

    /**
     * Saves an atomic metadata structure for a media item.
     */
    fun saveAtomicMetadata(mediaId: String, metadata: AtomicMediaMetadata) {
        if (mediaId.isBlank()) return
        try {
            // Load existing to preserve fields if partial update
            val existing = getAtomicMetadata(mediaId)
            val merged = if (existing != null) {
                existing.copy(
                    tmdbId = if (metadata.tmdbId > 0) metadata.tmdbId else existing.tmdbId,
                    mediaType = metadata.mediaType,
                    movie = metadata.movie ?: existing.movie,
                    show = metadata.show ?: existing.show,
                    season = metadata.season ?: existing.season,
                    episode = metadata.episode ?: existing.episode,
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                metadata
            }

            atomicMemoryCache[mediaId] = merged
            val json = atomicAdapter.toJson(merged)
            val editor = prefs.edit()
            editor.putString("$ATOMIC_KEY_PREFIX$mediaId", json)

            // Also save by canonical TMDB ID if valid (only for movies and tv shows, not individual episodes)
            if (merged.tmdbId > 0) {
                val canonicalKey = when (merged.mediaType) {
                    com.example.model.MediaType.TV_SHOW -> "tv:${merged.tmdbId}"
                    com.example.model.MediaType.MOVIE -> "movie:${merged.tmdbId}"
                    else -> null
                }
                if (canonicalKey != null) {
                    atomicMemoryCache[canonicalKey] = merged
                    editor.putString("$ATOMIC_KEY_PREFIX$canonicalKey", json)
                }
            }

            // Clear legacy fragmented keys if present
            editor.remove("tmdb_poster_$mediaId")
            editor.remove("tmdb_cast_$mediaId")
            editor.remove("tmdb_overview_$mediaId")
            editor.remove("tmdb_id_$mediaId")

            editor.apply()
            Log.d(TAG, "Saved atomic metadata for id='$mediaId', tmdbId=${merged.tmdbId}, type=${merged.mediaType}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save atomic metadata for id='$mediaId': ${e.message}", e)
        }
    }

    /**
     * Retrieves the canonical atomic metadata for a media item or canonical TMDB key.
     */
    fun getAtomicMetadata(mediaId: String): AtomicMediaMetadata? {
        if (mediaId.isBlank()) return null
        atomicMemoryCache[mediaId]?.let { return it }
        val json = prefs.getString("$ATOMIC_KEY_PREFIX$mediaId", null)
            ?: prefs.getString("$ATOMIC_KEY_PREFIX/movie:$mediaId", null)
            ?: return null
        return try {
            val meta = atomicAdapter.fromJson(json)
            if (meta != null) {
                atomicMemoryCache[mediaId] = meta
            }
            meta
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse atomic metadata for id='$mediaId': ${e.message}")
            null
        }
    }

    fun getAtomicMetadataByCanonicalId(tmdbId: Int, isTv: Boolean): AtomicMediaMetadata? {
        if (tmdbId <= 0) return null
        val canonicalKey = if (isTv) "tv:$tmdbId" else "movie:$tmdbId"
        atomicMemoryCache[canonicalKey]?.let { return it }
        val json = prefs.getString("$ATOMIC_KEY_PREFIX$canonicalKey", null) ?: return null
        return try {
            val meta = atomicAdapter.fromJson(json)
            if (meta != null) {
                atomicMemoryCache[canonicalKey] = meta
            }
            meta
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clears cached metadata for a media item.
     */
    fun invalidateMetadata(mediaId: String) {
        if (mediaId.isBlank()) return
        atomicMemoryCache.remove(mediaId)
        val editor = prefs.edit()
        editor.remove("$ATOMIC_KEY_PREFIX$mediaId")
        editor.apply()
        Log.d(TAG, "Invalidated metadata cache for id='$mediaId'")
    }
}

