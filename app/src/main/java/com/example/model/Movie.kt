package com.example.model

data class CastMember(
    val name: String,
    val character: String,
    val imageUrl: String
)

data class Movie(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val rating: Float,
    val category: String, // Movies, Shows, Home Videos
    val genre: List<String>,
    val duration: String,
    val synopsis: String,
    val cast: List<CastMember>,
    val director: String,
    val directorImageUrl: String,
    val audioCodec: String = "",
    val tmdbBadge: String = "",
    val releaseYear: String = "",
    val views: String = "",
    val tomatoScore: String = "",
    val genres: List<String> = com.example.util.TmdbGenreMapper.sanitizeGenres(genre),
    val cleanPosterUrl: String = "",
    val logoPosterUrl: String = "",
    val transparentLogoUrl: String = ""
)

val mockMovies = emptyList<Movie>()

val Movie.filePath: String get() = id
val Movie.fileName: String get() = title

data class ShowStack(
    val showTitle: String,
    val totalEpisodes: Int,
    val episodes: List<Movie>,
    val posterUrl: String
) {
    constructor(showTitle: String, posterUrl: String, episodes: List<Movie>) : this(
        showTitle = showTitle,
        totalEpisodes = episodes.size,
        episodes = episodes,
        posterUrl = posterUrl
    )
}

