package com.example.util

import android.util.Log

object TmdbGenreMapper {
    private const val TAG = "GENRE_DEBUG"

    private val MOVIE_GENRE_MAP = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Science Fiction",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western"
    )

    private val TV_GENRE_MAP = mapOf(
        10759 to "Action & Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        10762 to "Kids",
        9648 to "Mystery",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics",
        37 to "Western"
    )

    fun mapMovieGenreIds(genreIds: List<Number>?): List<String> {
        if (genreIds.isNullOrEmpty()) return emptyList()
        val mapped = genreIds.mapNotNull { num ->
            MOVIE_GENRE_MAP[num.toInt()]
        }.distinct()
        try { Log.d(TAG, "TmdbGenreMapper MOVIE: input IDs=$genreIds -> mapped genres=$mapped") } catch (_: Throwable) {}
        return mapped
    }

    fun mapTvGenreIds(genreIds: List<Number>?): List<String> {
        if (genreIds.isNullOrEmpty()) return emptyList()
        val mapped = genreIds.mapNotNull { num ->
            TV_GENRE_MAP[num.toInt()]
        }.distinct()
        try { Log.d(TAG, "TmdbGenreMapper TV: input IDs=$genreIds -> mapped genres=$mapped") } catch (_: Throwable) {}
        return mapped
    }

    fun mapGenreIds(genreIds: List<Number>?, isTv: Boolean = false): List<String> {
        return if (isTv) mapTvGenreIds(genreIds) else mapMovieGenreIds(genreIds)
    }

    fun sanitizeGenres(rawGenres: List<String>?): List<String> {
        if (rawGenres.isNullOrEmpty()) return emptyList()
        return rawGenres.filter { g ->
            g.isNotBlank() &&
            !g.equals("Local Video", ignoreCase = true) &&
            !g.equals("Gallery Video", ignoreCase = true) &&
            !g.equals("TV Series", ignoreCase = true)
        }.distinct()
    }
}
