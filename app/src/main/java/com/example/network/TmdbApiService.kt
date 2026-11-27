package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Logo(
    @Json(name = "file_path") val file_path: String?,
    @Json(name = "iso_639_1") val iso_639_1: String? = null,
    @Json(name = "aspect_ratio") val aspect_ratio: Double? = null,
    @Json(name = "height") val height: Int? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "vote_average") val vote_average: Double? = null,
    @Json(name = "vote_count") val vote_count: Int? = null
)

@JsonClass(generateAdapter = true)
data class ImagesResponse(
    val id: Int? = null,
    val logos: List<Logo> = emptyList(),
    val backdrops: List<Any>? = null,
    val posters: List<Any>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenreItem(
    val id: Int? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCastItem(
    val id: Int? = null,
    val name: String? = null,
    val character: String? = null,
    @Json(name = "profile_path") val profile_path: String? = null,
    val order: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsResponse(
    val id: Int? = null,
    val cast: List<TmdbCastItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MovieDetailResponse(
    val id: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "backdrop_path") val backdrop_path: String? = null,
    @Json(name = "release_date") val release_date: String? = null,
    @Json(name = "vote_average") val vote_average: Double? = null,
    @Json(name = "vote_count") val vote_count: Int? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenreItem> = emptyList(),
    val credits: TmdbCreditsResponse? = null,
    val images: ImagesResponse? = null
)

@JsonClass(generateAdapter = true)
data class TvDetailResponse(
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "backdrop_path") val backdrop_path: String? = null,
    @Json(name = "first_air_date") val first_air_date: String? = null,
    @Json(name = "vote_average") val vote_average: Double? = null,
    @Json(name = "vote_count") val vote_count: Int? = null,
    val genres: List<TmdbGenreItem> = emptyList(),
    val credits: TmdbCreditsResponse? = null,
    val images: ImagesResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbVideoItem(
    val id: String? = null,
    @Json(name = "iso_639_1") val iso_639_1: String? = null,
    @Json(name = "iso_3166_1") val iso_3166_1: String? = null,
    val name: String? = null,
    val key: String? = null,
    val site: String? = null,
    val size: Int? = null,
    val type: String? = null,
    val official: Boolean? = null,
    @Json(name = "published_at") val published_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(
    val id: Int? = null,
    val results: List<TmdbVideoItem> = emptyList()
)

interface TmdbApiService {
    @GET("movie/{movie_id}/images")
    suspend fun getMovieImages(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") includeImageLanguage: String? = "en,null"
    ): ImagesResponse

    @GET("tv/{tv_id}/images")
    suspend fun getTvImages(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") includeImageLanguage: String? = "en,null"
    ): ImagesResponse

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbVideosResponse
}

object TmdbApiClient {
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_ORIGINAL_URL = "https://image.tmdb.org/t/p/original"
    const val TMDB_IMAGE_W500_URL = "https://image.tmdb.org/t/p/w500"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient by lazy {
        try {
            com.example.InfuseApplication.sharedOkHttpClient
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    val apiService: TmdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApiService::class.java)
    }

    /**
     * Selects ordered official YouTube trailer/teaser candidates from a TMDB movie videos response.
     * Priority:
     *  1. Official YouTube Trailer (English preferred, then highest resolution / newest)
     *  2. Official YouTube Teaser (English preferred, then highest resolution / newest)
     * Non-official, non-YouTube, or unrelated video types are excluded.
     */
    fun selectOfficialTrailerCandidates(response: TmdbVideosResponse): List<TmdbVideoItem> {
        val validVideos = response.results.filter { item ->
            item.site.equals("YouTube", ignoreCase = true) &&
                !item.key.isNullOrBlank() &&
                item.official == true &&
                (item.type.equals("Trailer", ignoreCase = true) ||
                    item.type.equals("Teaser", ignoreCase = true))
        }

        return validVideos.sortedWith(
            compareByDescending<TmdbVideoItem> { item ->
                when {
                    item.type.equals("Trailer", ignoreCase = true) -> 2
                    item.type.equals("Teaser", ignoreCase = true) -> 1
                    else -> 0
                }
            }.thenByDescending { item ->
                if (item.iso_639_1.equals("en", ignoreCase = true)) 1 else 0
            }.thenByDescending { item ->
                item.size ?: 0
            }.thenByDescending { item ->
                item.published_at ?: ""
            }
        )
    }

    /**
     * Finds the best transparent English/primary logo from the ImagesResponse and builds the full URL
     */
    fun selectBestLogoUrl(imagesResponse: ImagesResponse, useOriginal: Boolean = true): String? {
        val logos = imagesResponse.logos
        if (logos.isEmpty()) return null

        val bestCandidate = logos.maxByOrNull { logo ->
            val path = logo.file_path ?: ""
            if (path.isBlank()) return@maxByOrNull -100.0

            val lang = logo.iso_639_1
            val voteAvg = logo.vote_average ?: 0.0
            val isPng = path.endsWith(".png", ignoreCase = true)
            val isEn = lang.equals("en", ignoreCase = true) || lang.isNullOrBlank() || lang.equals("xx", ignoreCase = true)

            val baseScore = when {
                isEn && isPng -> 100.0
                isEn -> 80.0
                isPng -> 50.0
                else -> 20.0
            }
            baseScore + (voteAvg * 2.0)
        }

        val filePath = bestCandidate?.file_path
        if (!filePath.isNullOrBlank()) {
            val base = if (useOriginal) TMDB_IMAGE_ORIGINAL_URL else TMDB_IMAGE_W500_URL
            val cleanPath = if (filePath.startsWith("/")) filePath else "/$filePath"
            return "$base$cleanPath"
        }
        return null
    }
}
