package com.example.model

import com.squareup.moshi.JsonClass

enum class MediaType {
    MOVIE,
    TV_SHOW,
    TV_EPISODE,
    HOME_VIDEO
}

@JsonClass(generateAdapter = true)
data class MovieMetadata(
    val tmdbId: Int = 0,
    val title: String = "",
    val posterPath: String = "",
    val backdropPath: String = "",
    val genres: List<String> = emptyList(),
    val releaseYear: String = "",
    val overview: String = "",
    val rating: Float = 0.0f,
    val tomatoScore: String = "",
    val cast: List<CastMember> = emptyList(),
    val director: String = "",
    val cleanPosterPath: String = "",
    val logoPosterPath: String = "",
    val transparentLogoPath: String = "",
    val duration: String = ""
)

@JsonClass(generateAdapter = true)
data class TvShowMetadata(
    val tmdbId: Int = 0,
    val showTitle: String = "",
    val posterPath: String = "",
    val backdropPath: String = "",
    val genres: List<String> = emptyList(),
    val releaseYear: String = "",
    val overview: String = "",
    val rating: Float = 0.0f,
    val tomatoScore: String = "",
    val cast: List<CastMember> = emptyList(),
    val cleanPosterPath: String = "",
    val logoPosterPath: String = "",
    val transparentLogoPath: String = ""
)

@JsonClass(generateAdapter = true)
data class TvSeasonMetadata(
    val showTmdbId: Int = 0,
    val seasonNumber: Int = 1,
    val posterPath: String = "",
    val overview: String = ""
)

@JsonClass(generateAdapter = true)
data class TvEpisodeMetadata(
    val showTmdbId: Int = 0,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String = "",
    val stillPath: String = "",
    val overview: String = "",
    val releaseDate: String = "",
    val duration: String = "",
    val rating: Float = 0.0f,
    val voteCount: Int = 0,
    val filePath: String = ""
)

/**
 * Atomic metadata container stored as a single JSON object per media item key.
 * Enforces atomic storage of poster_path, backdrop_path, tmdbId, and details together.
 */
@JsonClass(generateAdapter = true)
data class AtomicMediaMetadata(
    val id: String,
    val tmdbId: Int = 0,
    val mediaType: MediaType = MediaType.MOVIE,
    val movie: MovieMetadata? = null,
    val show: TvShowMetadata? = null,
    val season: TvSeasonMetadata? = null,
    val episode: TvEpisodeMetadata? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
