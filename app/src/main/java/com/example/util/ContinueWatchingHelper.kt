package com.example.util

import com.example.model.Movie
import com.example.network.GeminiService

object ContinueWatchingHelper {

    /**
     * Generates a unique identifier for a TV show episode combining normalized show title,
     * season number, and episode number (e.g. "ep_progress_daredevil_s1_e2").
     */
    fun getEpisodeProgressKey(showTitle: String, seasonNumber: Int, episodeNumber: Int): String {
        val cleaned = GeminiService.cleanShowTitle(showTitle)
        val normalizedShow = EpisodeUtils.getNormalizedShowTitle(cleaned).ifBlank {
            cleaned.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        }.ifBlank { "unknown_show" }
        val s = seasonNumber.coerceAtLeast(1)
        val e = episodeNumber.coerceAtLeast(1)
        return "ep_progress_${normalizedShow}_s${s}_e${e}"
    }

    fun getEpisodeProgressKey(movie: Movie): String {
        val seInfo = EpisodeUtils.extractSeasonAndEpisode(movie.title, movie.id)
        val s = seInfo.season ?: LocalSeasonParser.parseSeasonNumber(movie.id, movie.title)
        val e = seInfo.episode ?: 1
        return getEpisodeProgressKey(movie.title, s, e)
    }

    fun isTvEpisode(movie: Movie): Boolean {
        val cat = movie.category.trim()
        if (cat.equals("Movies", ignoreCase = true) || cat.equals("Home Videos", ignoreCase = true)) {
            return false
        }
        if (cat.equals("Shows", ignoreCase = true) ||
            cat.contains("Show", ignoreCase = true) ||
            cat.contains("TV", ignoreCase = true) ||
            cat.contains("Series", ignoreCase = true)
        ) {
            return true
        }
        val seInfo = EpisodeUtils.extractSeasonAndEpisode(movie.title, movie.id)
        return seInfo.season != null || seInfo.episode != null
    }

    /**
     * Determines a unique Continue Watching item key so the same episode (same show, season, episode)
     * or the same movie is never duplicated in Continue Watching.
     */
    fun getUniqueContinueWatchingEntryKey(movie: Movie, seasonNumber: Int? = null, episodeNumber: Int? = null): String {
        if (isTvEpisode(movie)) {
            val seInfo = EpisodeUtils.extractSeasonAndEpisode(movie.title, movie.id)
            val s = seasonNumber ?: seInfo.season ?: LocalSeasonParser.parseSeasonNumber(movie.id, movie.title)
            val e = episodeNumber ?: seInfo.episode ?: 1
            return getEpisodeProgressKey(movie.title, s, e)
        }
        val cleanTitle = EpisodeMetadataFixer.sanitizeMediaTitle(movie.title)
        val normalizedMovie = EpisodeUtils.getNormalizedShowTitle(cleanTitle)
        return if (normalizedMovie.isNotBlank()) {
            "movie:$normalizedMovie"
        } else {
            "movie:${movie.id}"
        }
    }

    /**
     * Determines the grouping key for Continue Watching.
     * All episodes of the same TV series return the same key (e.g. "tv_show:daredevil").
     * Movies return a movie-specific key (e.g. "movie:inception").
     */
    fun getGroupKey(movie: Movie): String {
        val isShow = isTvEpisode(movie)

        if (isShow) {
            val showTitle = GeminiService.cleanShowTitle(movie.title)
            val normalizedShow = EpisodeUtils.getNormalizedShowTitle(showTitle)
            return if (normalizedShow.isNotBlank()) {
                "tv_show:$normalizedShow"
            } else if (showTitle.isNotBlank()) {
                "tv_show:${showTitle.lowercase().replace(Regex("""[^a-z0-9]"""), "")}"
            } else {
                "tv_show:${movie.title.lowercase().replace(Regex("""[^a-z0-9]"""), "")}"
            }
        } else {
            val cleanTitle = EpisodeMetadataFixer.sanitizeMediaTitle(movie.title)
            val normalizedMovie = EpisodeUtils.getNormalizedShowTitle(cleanTitle)
            return if (normalizedMovie.isNotBlank()) {
                "movie:$normalizedMovie"
            } else {
                "movie:${movie.id}"
            }
        }
    }

    /**
     * Deduplicates a list of movies for Continue Watching shelf.
     * Keeps ONLY the first occurrence (the single most recently played/paused item)
     * for each TV series or Movie.
     */
    fun deduplicateContinueWatching(movies: List<Movie>): List<Movie> {
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<Movie>()
        for (movie in movies) {
            val key = getGroupKey(movie)
            if (seenKeys.add(key)) {
                result.add(movie)
            }
        }
        return result
    }
}
