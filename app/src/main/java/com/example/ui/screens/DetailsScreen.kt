package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentUris
import android.app.RecoverableSecurityException
import android.util.Base64
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import android.widget.Toast
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.model.Movie
import com.example.model.CastMember
import com.example.model.filePath
import com.example.model.fileName
import com.example.util.infuseClickable
import com.example.util.infusePressEffect
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

// Infuse Signature Accent Colors
private val InfuseOrange = Color(0xFFFF5B00)
private val InfuseOrangeLight = Color(0xFFFF7A28)

fun String?.toTmdbUrl(size: String = "w500"): String {
    return com.example.util.PosterCardUIHelper.formatTmdbUrl(this, size)
}

data class ParsedEpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val formattedSE: String,
    val cleanEpisodeTitle: String,
    val fullFormattedTitle: String
)

private fun cleanEpisodeNameWithTags(name: String, episodeNumber: Int): String {
    var clean = name
    val tags = listOf(
        "MoviezFlix", "Ocean", "PSA", "GalaxyRG", "MeGusta", "SARTRE", "RMTeam", "FGO", "x264", "x265", "HEVC",
        "1080p", "720p", "480p", "BluRay", "WEBRip", "WEB-DL", "AMZN", "NF", "DDP5", "DDP", "H264", "H265",
        "10bit", "Dual-Audio", "Dual Audio", "Multi-Audio", "Multi Audio", "Sub", "Softsub", "ESub", "Eng", "Hin"
    )
    for (tag in tags) {
        clean = clean.replace(Regex("(?i)\\b$tag\\b"), "")
    }
    clean = clean.replace(Regex("""[._\-\s|/]+"""), " ").trim()
    if (clean.contains(".")) {
        clean = clean.substringBeforeLast(".")
    }
    if (clean.equals("Episode $episodeNumber", ignoreCase = true) || clean.equals("Episode", ignoreCase = true)) {
        return "Episode $episodeNumber"
    }
    return clean.ifBlank { "Episode $episodeNumber" }
}

private fun parseEpisodeDetails(movie: Movie, index: Int, viewModel: com.example.viewmodel.MainViewModel? = null): ParsedEpisodeInfo {
    val title = movie.title
    val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(title, movie.filePath ?: "")
    val sNum = seInfo.season ?: com.example.util.LocalSeasonParser.parseSeasonNumber(movie.filePath ?: "", title)
    val eNum = seInfo.episode ?: (index + 1)
    val showTitle = com.example.network.GeminiService.cleanShowTitle(title)

    val tmdbName = viewModel?.getSavedTmdbEpisodeName(movie.id, showTitle, sNum, eNum)
    val hasTmdbName = !tmdbName.isNullOrBlank()
    val rawName = if (hasTmdbName) {
        tmdbName!!
    } else {
        val extracted = com.example.util.EpisodeUtils.cleanEpisodeTitle(title)
        if (extracted.isNotBlank() && !extracted.equals(movie.title, ignoreCase = true)) extracted else ""
    }

    val displayTitle = if (rawName.isNotBlank()) {
        com.example.util.EpisodeMetadataFixer.getCleanEpisodeTitle(rawName, eNum, showTitle, isFromTmdbApi = hasTmdbName)
    } else {
        "Episode $eNum"
    }
    val formattedSE = "S$sNum • E$eNum"
    val fullFormattedTitle = "S$sNum • E$eNum - $displayTitle"

    return ParsedEpisodeInfo(
        seasonNumber = sNum,
        episodeNumber = eNum,
        formattedSE = formattedSE,
        cleanEpisodeTitle = displayTitle,
        fullFormattedTitle = fullFormattedTitle
    )
}

private fun parseDurationMs(durationStr: String): Long {
    var totalMs = 0L
    if (durationStr.contains("h", ignoreCase = true)) {
        val h = durationStr.substringBefore("h").trim().toLongOrNull() ?: 0L
        totalMs += h * 3600_000L
        val remaining = durationStr.substringAfter("h").trim()
        if (remaining.contains("m", ignoreCase = true)) {
            val m = remaining.substringBefore("m").trim().toLongOrNull() ?: 0L
            totalMs += m * 60_000L
        }
    } else if (durationStr.contains("m", ignoreCase = true)) {
        val m = durationStr.substringBefore("m").trim().toLongOrNull() ?: 0L
        totalMs += m * 60_000L
        val remaining = durationStr.substringAfter("m").trim()
        if (remaining.contains("s", ignoreCase = true)) {
            val s = remaining.substringBefore("s").trim().toLongOrNull() ?: 0L
            totalMs += s * 1000L
        }
    } else if (durationStr.contains("s", ignoreCase = true)) {
        val s = durationStr.substringBefore("s").trim().toLongOrNull() ?: 0L
        totalMs += s * 1000L
    } else if (durationStr.contains(":")) {
        val parts = durationStr.split(":")
        if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val s = parts[2].toLongOrNull() ?: 0L
            totalMs = (h * 3600 + m * 60 + s) * 1000L
        } else if (parts.size == 2) {
            val m = parts[0].toLongOrNull() ?: 0L
            val s = parts[1].toLongOrNull() ?: 0L
            totalMs = (m * 60 + s) * 1000L
        }
    }
    return if (totalMs > 0L) totalMs else 45 * 60_000L
}

private val cleanDurationCache = java.util.concurrent.ConcurrentHashMap<String, String>()

private fun formatCleanDuration(movieDuration: String, movieId: String, context: android.content.Context): String {
    val needsLocalRetrieve = movieDuration.isBlank() ||
            movieDuration.equals("Local", ignoreCase = true) ||
            movieDuration.equals("Video", ignoreCase = true)
    if (needsLocalRetrieve && (movieId.startsWith("content://") || movieId.startsWith("file://"))) {
        cleanDurationCache[movieId]?.let { return it }
        try {
            val retriever = android.media.MediaMetadataRetriever()
            if (movieId.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(movieId))
            } else {
                retriever.setDataSource(Uri.parse(movieId).path)
            }
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val ms = time?.toLongOrNull() ?: 0L
            if (ms > 0L) {
                val totalSecs = ms / 1000
                val hours = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                val formatted = when {
                    hours > 0 -> if (mins > 0) "${hours} hr ${mins} min" else "${hours} hr"
                    mins > 0 -> "${mins} min"
                    else -> "${secs}s"
                }
                cleanDurationCache[movieId] = formatted
                return formatted
            }
        } catch (e: Exception) {
            // fallback
        }
        cleanDurationCache[movieId] = "Video"
    }

    if (movieDuration.isBlank() || movieDuration.equals("Local", ignoreCase = true)) {
        return "Video"
    }

    if (movieDuration.contains("m") && movieDuration.contains("s")) {
        val mins = movieDuration.substringBefore("m").trim().toLongOrNull() ?: 0L
        if (mins >= 60) {
            val h = mins / 60
            val m = mins % 60
            return if (m > 0) "${h} hr ${m} min" else "${h} hr"
        } else if (mins > 0) {
            return "${mins} min"
        }
    } else if (movieDuration.contains("h") || movieDuration.contains("m")) {
        // Parse strings like "1h 35m", "95m", "120m", etc.
        var totalMinutes = 0
        if (movieDuration.contains("h", ignoreCase = true)) {
            val h = movieDuration.substringBefore("h").trim().toIntOrNull() ?: 0
            totalMinutes += h * 60
            val remainder = movieDuration.substringAfter("h").trim()
            if (remainder.contains("m", ignoreCase = true)) {
                totalMinutes += remainder.substringBefore("m").trim().toIntOrNull() ?: 0
            }
        } else if (movieDuration.contains("m", ignoreCase = true)) {
            totalMinutes += movieDuration.substringBefore("m").trim().toIntOrNull() ?: 0
        }
        if (totalMinutes > 0) {
            return com.example.util.EpisodeMetadataParser.formatRuntime(totalMinutes)
        }
    }

    return movieDuration
}

data class EpisodeCardData(
    val movie: Movie,
    val info: ParsedEpisodeInfo,
    val savedPosMs: Long,
    val totalDurationMs: Long,
    val watchedRatio: Float,
    val stillPath: String? = null,
    val isWatched: Boolean = false
)

@Composable
fun DetailsScreen(
    movieId: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme by viewModel.selectedTheme.collectAsState()
    val primaryColor = currentTheme.primaryColor
    val dynamicInterface by viewModel.dynamicInterface.collectAsState()
    val movies by viewModel.moviesList.collectAsState()

    // Dynamic active movie selection
    var activeMovieId by remember(movieId) { mutableStateOf(movieId) }
    var userHasSelectedAnEpisode by remember(movieId) { mutableStateOf(false) }

    val initialMovie = remember(movieId, movies) {
        val found = movies.firstOrNull { it.id == movieId }
        if (found != null) {
            found
        } else {
            val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
            val isEp = seInfo.season != null || seInfo.episode != null
            Movie(
                id = movieId,
                title = "Imported Video",
                posterUrl = "",
                backdropUrl = "",
                rating = 5.0f,
                category = if (isEp) "Shows" else "Movies",
                genre = emptyList(),
                duration = "Video",
                synopsis = "Local media video file:\n$movieId",
                cast = emptyList(),
                director = "Infuse Player",
                directorImageUrl = ""
            )
        }
    }

    val movie = remember(activeMovieId, movies) {
        movies.firstOrNull { it.id == activeMovieId } ?: initialMovie
    }

    val savedPositions by viewModel.savedPositions.collectAsState()
    val savedPos = savedPositions[movie.id] ?: 0L

    val watchlist by viewModel.watchlist.collectAsState()
    val favoriteMovieIds by viewModel.favoriteMovieIds.collectAsState()
    val isFavorite = remember(movie.id, favoriteMovieIds) { favoriteMovieIds.contains(movie.id) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    // Cast Member Modal Detail State
    var selectedCastMember by remember { mutableStateOf<CastMember?>(null) }

    // Official Movie Trailer State
    val trailerCoroutineScope = rememberCoroutineScope()
    var isFetchingTrailer by remember(movie.id) { mutableStateOf(false) }
    var activeTrailerCandidates by remember(movie.id) { mutableStateOf<List<com.example.viewmodel.MovieTrailerCandidate>>(emptyList()) }
    var showTrailerModal by remember(movie.id) { mutableStateOf(false) }
    var trailerStatusMessage by remember(movie.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(showTrailerModal, activeTrailerCandidates) {
        viewModel.setTrailerModalOpen(showTrailerModal && activeTrailerCandidates.isNotEmpty())
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setTrailerModalOpen(false)
        }
    }

    LaunchedEffect(trailerStatusMessage) {
        if (trailerStatusMessage != null) {
            kotlinx.coroutines.delay(2800L)
            trailerStatusMessage = null
        }
    }

    // Related Episodes for TV Show Carousel
    val showEpisodes = remember(movies, initialMovie) {
        val targetNormalized = com.example.util.EpisodeUtils.getNormalizedShowTitle(initialMovie.title)
        val isShowCategory = initialMovie.category.equals("Shows", ignoreCase = true)

        val matched = movies.filter { m ->
            if (isShowCategory) {
                m.category.equals("Shows", ignoreCase = true) &&
                        com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title) == targetNormalized
            } else {
                com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title) == targetNormalized
            }
        }

        val rawList = if (matched.isNotEmpty()) matched else listOf(initialMovie)
        com.example.util.EpisodeUtils.sortEpisodesChronologically(rawList)
    }

    val showKey = remember(initialMovie) {
        com.example.util.EpisodeUtils.getNormalizedShowTitle(initialMovie.title).ifBlank { initialMovie.id }
    }

    val savedWatchState = remember(showKey, showEpisodes, savedPositions) {
        viewModel.getSavedShowWatchState(showKey) ?: viewModel.getSavedShowWatchState(initialMovie.id)
    }

    LaunchedEffect(showEpisodes, savedWatchState) {
        if (savedWatchState != null && !userHasSelectedAnEpisode) {
            val matchedEp = showEpisodes.firstOrNull { ep ->
                ep.id == savedWatchState.episodeId
            } ?: showEpisodes.firstOrNull { ep ->
                val se = com.example.util.EpisodeUtils.extractSeasonAndEpisode(ep.title)
                (se.season ?: 1) == savedWatchState.season && (se.episode ?: 1) == savedWatchState.episode
            }
            if (matchedEp != null) {
                activeMovieId = matchedEp.id
            }
        }
    }

    LaunchedEffect(movie) {
        viewModel.fetchAndCacheTmdbDetails(movie)
    }

    val watchedEpisodes by viewModel.watchedEpisodes.collectAsState()
    val savedDurations by viewModel.savedDurations.collectAsState()

    // Process & Group Episodes into Seasons
    val episodesDataList = remember(showEpisodes, savedPositions, savedDurations, watchedEpisodes, movies) {
        showEpisodes.mapIndexed { idx, ep ->
            val info = parseEpisodeDetails(ep, idx, viewModel)
            val showClean = com.example.network.GeminiService.cleanShowTitle(ep.title)
            val pos = viewModel.getSavedEpisodePosition(showClean, info.seasonNumber, info.episodeNumber, ep.id)
            val knownDurMs = viewModel.getSavedPlaybackDuration(ep.id, showClean, info.seasonNumber, info.episodeNumber)
            val durMs = if (knownDurMs > 0L) knownDurMs else parseDurationMs(ep.duration)
            val ratio = if (durMs > 0L) (pos.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
            val still = viewModel.getSavedTmdbEpisodeStill(
                movieId = ep.id,
                showTitle = showClean,
                seasonNumber = info.seasonNumber,
                episodeNumber = info.episodeNumber
            )
            val isExplicitlyWatched = watchedEpisodes.contains(ep.id)
            val watched = isExplicitlyWatched || ratio >= 0.95f
            EpisodeCardData(
                movie = ep,
                info = info,
                savedPosMs = pos,
                totalDurationMs = durMs,
                watchedRatio = ratio,
                stillPath = still,
                isWatched = watched
            )
        }
    }

    val seasonsMap = remember(episodesDataList) {
        episodesDataList.groupBy { it.info.seasonNumber }.toSortedMap()
    }

    val isMovieWatched = remember(episodesDataList, movie, savedPositions, savedDurations, watchedEpisodes) {
        val currentEpData = episodesDataList.firstOrNull { it.movie.id == movie.id }
        currentEpData?.isWatched ?: run {
            val pos = viewModel.getSavedPlaybackPosition(movie.id)
            val knownDurMs = viewModel.getSavedPlaybackDuration(movie.id)
            val durMs = if (knownDurMs > 0L) knownDurMs else parseDurationMs(movie.duration)
            val ratio = if (durMs > 0L) (pos.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
            watchedEpisodes.contains(movie.id) || ratio >= 0.95f
        }
    }

    val activeEpisodeInfo = remember(movie, showEpisodes, movies) {
        val idx = showEpisodes.indexOfFirst { it.id == movie.id }.coerceAtLeast(0)
        parseEpisodeDetails(movie, idx, viewModel)
    }

    val isTvShow = remember(showEpisodes, movie) {
        val hasMarker = movie.title.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2}|season\s*\d+|episode\s*\d+)\b""")) ||
                movie.id.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2})\b"""))
        if (movie.category.equals("Movies", ignoreCase = true) || movie.category.equals("Home Videos", ignoreCase = true)) {
            false
        } else if (hasMarker) {
            true
        } else if (movie.category.equals("Shows", ignoreCase = true) ||
            movie.category.contains("TV", ignoreCase = true) ||
            movie.category.contains("Series", ignoreCase = true)) {
            showEpisodes.size > 1
        } else {
            false
        }
    }

    LaunchedEffect(showEpisodes, isTvShow) {
        if (isTvShow && showEpisodes.isNotEmpty()) {
            viewModel.fetchAndCacheEpisodeNames(showEpisodes)
        }
    }

    var selectedSeasonNum by remember(seasonsMap, activeEpisodeInfo, savedWatchState) {
        val savedSeason = savedWatchState?.season ?: activeEpisodeInfo.seasonNumber
        val initialSeason = if (seasonsMap.containsKey(savedSeason)) savedSeason else activeEpisodeInfo.seasonNumber
        mutableStateOf(initialSeason)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.deleteEpisodeFromDatabase(movie.id)
            viewModel.navigateTo(AppScreen.Library)
            Toast.makeText(context, "Item deleted successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Deletion canceled", Toast.LENGTH_SHORT).show()
        }
    }

    val performVersionAwareDeletion = remember(context, deleteLauncher, movie) {
        { filePath: String ->
            val uri = if (filePath.startsWith("content://")) {
                Uri.parse(filePath)
            } else {
                val file = java.io.File(filePath)
                val projection = arrayOf(MediaStore.Video.Media._ID)
                val selection = "${MediaStore.Video.Media.DATA} = ?"
                val selectionArgs = arrayOf(file.absolutePath)
                val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                var foundUri: Uri? = null
                try {
                    context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                            val id = cursor.getLong(idIndex)
                            foundUri = ContentUris.withAppendedId(queryUri, id)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore query failure
                }
                foundUri ?: Uri.parse(filePath)
            }

            val contentResolver = context.contentResolver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteLauncher.launch(request)
                } catch (e: Exception) {
                    val deleted = try {
                        contentResolver.delete(uri, null, null) > 0
                    } catch (sec: Exception) {
                        false
                    }
                    if (deleted) {
                        viewModel.deleteEpisodeFromDatabase(movie.id)
                        viewModel.navigateTo(AppScreen.Library)
                        Toast.makeText(context, "Item deleted successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    contentResolver.delete(uri, null, null)
                    viewModel.deleteEpisodeFromDatabase(movie.id)
                    viewModel.navigateTo(AppScreen.Library)
                    Toast.makeText(context, "Item deleted successfully", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    val recoverableSecurityException = e as? RecoverableSecurityException
                    if (recoverableSecurityException != null) {
                        val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        deleteLauncher.launch(request)
                    } else {
                        val file = java.io.File(filePath)
                        if (file.exists() && file.delete()) {
                            viewModel.deleteEpisodeFromDatabase(movie.id)
                            viewModel.navigateTo(AppScreen.Library)
                            Toast.makeText(context, "Item deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Permission denied for deletion", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Deletion failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    var deleted = false
                    if (filePath.startsWith("content://")) {
                        deleted = contentResolver.delete(uri, null, null) > 0
                    } else {
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            deleted = file.delete()
                            if (deleted) {
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(file.absolutePath),
                                    null,
                                    null
                                )
                            }
                        }
                    }
                    if (deleted) {
                        viewModel.deleteEpisodeFromDatabase(movie.id)
                        viewModel.navigateTo(AppScreen.Library)
                        Toast.makeText(context, "Item deleted successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.deleteEpisodeFromDatabase(movie.id)
                        viewModel.navigateTo(AppScreen.Library)
                        Toast.makeText(context, "Item removed from library", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var isSpoilerRevealed by remember { mutableStateOf(com.example.util.ShowDetailActionsHelper.isSpoilerRevealed()) }
    var manuallyRevealedIds by remember { mutableStateOf(setOf<String>()) }

    val isActiveEpWatched = remember(episodesDataList, activeMovieId, watchedEpisodes) {
        watchedEpisodes.contains(activeMovieId) || (episodesDataList.firstOrNull { it.movie.id == activeMovieId }?.isWatched ?: false)
    }

    val shouldBlurActiveSynopsis = remember(isTvShow, isActiveEpWatched, manuallyRevealedIds, activeMovieId) {
        isTvShow && !isActiveEpWatched && !manuallyRevealedIds.contains(activeMovieId)
    }

    val supportsNativeBlur = remember { android.os.Build.VERSION.SDK_INT >= 31 }

    val activeSynopsisBlurRadius by animateDpAsState(
        targetValue = if (shouldBlurActiveSynopsis) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "SynopsisBlurAnimation"
    )

    val scrollState = rememberScrollState()

    // Dynamic Artwork Ambient Background Palette
    val effectiveBackdropUrl = remember(isTvShow, initialMovie, if (isTvShow) null else movie) {
        if (isTvShow) {
            val sTitle = com.example.network.GeminiService.cleanShowTitle(initialMovie.title)
            val cachedBackdrop = if (sTitle.isNotBlank()) viewModel.getSavedTmdbBackdropUrl(sTitle, sTitle) else null
            if (!cachedBackdrop.isNullOrBlank() && !cachedBackdrop.contains("unsplash")) {
                com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedBackdrop, "w1280")
            } else {
                val seriesBackdrop = initialMovie.backdropUrl.ifBlank { initialMovie.posterUrl }
                if (seriesBackdrop.isNotBlank() && !seriesBackdrop.contains("unsplash")) {
                    com.example.util.PosterCardUIHelper.formatBackdropUrl(seriesBackdrop, "w1280")
                } else {
                    val cachedPoster = viewModel.getSavedTmdbPosterUrl(initialMovie.id, initialMovie.title)
                    if (!cachedPoster.isNullOrBlank()) {
                        com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedPoster, "w1280")
                    } else {
                        viewModel.getEffectiveBackdropUrl(initialMovie, initialMovie)
                    }
                }
            }
        } else {
            viewModel.getEffectiveBackdropUrl(movie, initialMovie)
        }
    }

    val artworkForPalette = remember(effectiveBackdropUrl, movie.posterUrl, movie.backdropUrl) {
        effectiveBackdropUrl.ifBlank { movie.backdropUrl.ifBlank { movie.posterUrl } }
    }

    val dynamicPalette = com.example.util.rememberDynamicAmbientPalette(
        artworkUrl = artworkForPalette,
        isDynamicEnabled = dynamicInterface,
        fallbackColor = primaryColor
    )

    val posterOverridesVersion by viewModel.posterOverridesVersion.collectAsState()

    val userSelectedCustomPoster = remember(isTvShow, initialMovie, movie, movies, posterOverridesVersion) {
        val targetItem = if (isTvShow) initialMovie else movie
        val sTitle = if (isTvShow) com.example.network.GeminiService.cleanShowTitle(initialMovie.title) else null
        viewModel.getDetailPosterOverride(targetItem.id, sTitle ?: targetItem.title)
    }

    val isDetailPosterTextBearing = remember(isTvShow, initialMovie, movie, movies, posterOverridesVersion, userSelectedCustomPoster) {
        val targetItem = if (isTvShow) initialMovie else movie
        if (userSelectedCustomPoster.isNullOrBlank()) {
            viewModel.isEffectiveDetailPosterTextBearing(targetItem, initialMovie)
        } else {
            val sTitle = if (isTvShow) com.example.network.GeminiService.cleanShowTitle(initialMovie.title) else null
            viewModel.isDetailPosterTextBearing(targetItem.id, sTitle ?: targetItem.title)
        }
    }

    val effectiveSeriesPoster = remember(isTvShow, initialMovie, if (isTvShow) null else movie, userSelectedCustomPoster, posterOverridesVersion) {
        if (!userSelectedCustomPoster.isNullOrBlank()) {
            com.example.util.PosterCardUIHelper.formatTmdbUrl(userSelectedCustomPoster, "w780")
        } else {
            val targetItem = if (isTvShow) initialMovie else movie
            viewModel.getDetailPosterUrl(targetItem, initialMovie)
        }
    }

    val backdropImageModel = remember(isTvShow, initialMovie, if (isTvShow) null else movie, effectiveBackdropUrl, effectiveSeriesPoster, userSelectedCustomPoster) {
        if (!userSelectedCustomPoster.isNullOrBlank()) {
            com.example.util.PosterCardUIHelper.formatTmdbUrl(userSelectedCustomPoster, "w780")
        } else if (effectiveSeriesPoster.isNotBlank() && !effectiveSeriesPoster.contains("unsplash")) {
            com.example.util.PosterCardUIHelper.formatTmdbUrl(effectiveSeriesPoster, "w780")
        } else if (effectiveBackdropUrl.isNotBlank()) {
            com.example.util.PosterCardUIHelper.formatBackdropUrl(effectiveBackdropUrl, "w1280")
        } else if (isTvShow) {
            initialMovie.backdropUrl.toTmdbUrl("w1280")
                ?: initialMovie.posterUrl.toTmdbUrl("w780")
                ?: initialMovie.backdropUrl
                ?: initialMovie.posterUrl
        } else {
            movie.backdropUrl.toTmdbUrl("w1280") 
                ?: movie.posterUrl.toTmdbUrl("w780") 
                ?: initialMovie.backdropUrl.toTmdbUrl("w1280") 
                ?: initialMovie.posterUrl.toTmdbUrl("w780") 
                ?: movie.backdropUrl
        }
    }

    val moviePosterModel = remember(backdropImageModel) {
        com.example.util.PosterCardUIHelper.formatBackdropUrl(backdropImageModel, "w1280")
    }

    val base64Bitmap = remember(backdropImageModel) {
        if (backdropImageModel.startsWith("data:image") || (backdropImageModel.length > 200 && !backdropImageModel.startsWith("http"))) {
            try {
                val cleanData = if (backdropImageModel.contains(",")) backdropImageModel.substringAfter(",") else backdropImageModel
                val decodedBytes = Base64.decode(cleanData, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val mainImageRequest = com.example.util.rememberTmdbImageRequest(moviePosterModel, "w1280")

    val dominantPosterColor = remember(dynamicPalette, primaryColor) {
        if (dynamicPalette.primary != Color.Transparent) dynamicPalette.primary else primaryColor
    }
    val secondaryPosterColor = remember(dynamicPalette, primaryColor) {
        if (dynamicPalette.secondary != Color.Transparent) dynamicPalette.secondary else dominantPosterColor.copy(alpha = 0.75f)
    }

    val entranceAnim = remember(initialMovie.id) { Animatable(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Scrollable Screen Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 1. HERO HEADER SECTION WITH CINEMATIC POSTER
            // Universal Header Lock: For TV series, strictly and permanently lock header background to Main Show data (initialMovie).

            // 1. FULL-WIDTH EDGE-TO-EDGE HEADER BANNER (iOS Infuse Dissolve into Black)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clipToBounds()
                    .graphicsLayer {
                        val progress = entranceAnim.value
                        val scrollAlpha = (1f - (scrollState.value / 850f)).coerceIn(0f, 1f)
                        alpha = progress * scrollAlpha
                        scaleX = 0.96f + (0.04f * progress)
                        scaleY = 0.96f + (0.04f * progress)
                        translationY = scrollState.value * 0.28f
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                val heroPosterModel = base64Bitmap ?: (mainImageRequest ?: moviePosterModel)

                // LAYER 1: Full-width softly blurred, enlarged ambient poster background filling the screen width if needed
                com.example.util.SharedPosterImage(
                    model = heroPosterModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(28.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )

                // Ambient darkening overlay on blurred background for rich cinematic contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f))
                )

                // LAYER 2: Large full-width sharp original poster artwork in foreground with Center-Crop scaling matching iOS framing
                com.example.util.SharedPosterImage(
                    model = heroPosterModel,
                    contentDescription = "Artwork for ${if (isTvShow) initialMovie.title else movie.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )

                // LAYER 3: Seamless Pure Black Gradient Scrim merging the poster strictly into pure #000000 background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.40f to Color.Transparent,
                                0.60f to Color.Black.copy(alpha = 0.35f),
                                0.75f to Color.Black.copy(alpha = 0.75f),
                                0.88f to Color.Black.copy(alpha = 0.95f),
                                1.00f to Color.Black
                            )
                        )
                )
            }

            // 2. UNIFIED CONTENT CONTAINER (Negative Margin Overlap pulled upwards over lower dark gradient section of poster)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val overlapPx = 110.dp.roundToPx()
                        layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
                            placeable.placeRelative(0, -overlapPx)
                        }
                    }
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Hero Cluster (TMDB Logo, Episode Subtitle, Metadata Row, Play Button, Action Buttons, Ratings)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Smooth Animated Content for metadata switching when selecting episodes
                    AnimatedContent(
                        targetState = movie.id,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(300)) + slideInVertically { it / 4 })
                                .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically { -it / 4 })
                        },
                        label = "EpisodeMetadataTransition"
                    ) { targetId ->
                        val curMovie = remember(targetId, movies) {
                            movies.firstOrNull { it.id == targetId } ?: movie
                        }
                        val curInfo = remember(curMovie, showEpisodes, movies) {
                            val idx = showEpisodes.indexOfFirst { it.id == curMovie.id }.coerceAtLeast(0)
                            parseEpisodeDetails(curMovie, idx, viewModel)
                        }
                        val formattedDuration = remember(curMovie.id, curMovie.duration) {
                            formatCleanDuration(curMovie.duration, curMovie.id, context)
                        }

                        val showTitleName = remember(curMovie.title) {
                            if (isTvShow) com.example.network.GeminiService.cleanShowTitle(curMovie.title) else curMovie.title
                        }

                        val localQuality = remember(curMovie.filePath) {
                            if (!curMovie.filePath.isNullOrEmpty()) {
                                com.example.util.EpisodeMetadataParser.getLocalVideoQuality(curMovie.filePath, context)
                            } else ""
                        }
                        val detailQuality = localQuality.ifBlank {
                            curMovie.tmdbBadge.ifBlank {
                                com.example.network.GeminiService.detectQualityBadge(curMovie.title + " " + curMovie.id)
                            }
                        }

                        val epRuntime = remember(curMovie.id, isTvShow) {
                            if (isTvShow) {
                                viewModel.getSavedTmdbEpisodeRuntime(curMovie.id, showTitleName, curInfo.seasonNumber, curInfo.episodeNumber)
                            } else {
                                null
                            }
                        }
                        val movieRuntime = remember(curMovie.id, curMovie.title, isTvShow) {
                            if (!isTvShow) {
                                viewModel.getSavedTmdbMovieRuntime(curMovie.id, curMovie.title)
                            } else {
                                null
                            }
                        }
                        val runtimeMins = if (isTvShow) {
                            if (epRuntime != null && epRuntime > 0) epRuntime.toLong() else (parseDurationMs(curMovie.duration) / 60000L)
                        } else {
                            if (movieRuntime != null && movieRuntime > 0) movieRuntime.toLong() else (parseDurationMs(curMovie.duration) / 60000L)
                        }
                        val runtimeText = remember(curMovie.duration, epRuntime, movieRuntime, isTvShow) {
                            if (runtimeMins > 0L) com.example.util.EpisodeMetadataParser.formatRuntime(runtimeMins.toInt()) else formattedDuration
                        }

                        val dateText = remember(curMovie.id, curMovie.title, curMovie.releaseYear, isTvShow) {
                            if (isTvShow) {
                                val epAirDate = viewModel.getSavedTmdbEpisodeAirDate(curMovie.id, showTitleName, curInfo.seasonNumber, curInfo.episodeNumber)
                                val displayYear = if (!epAirDate.isNullOrBlank()) epAirDate else curMovie.releaseYear.ifBlank {
                                    com.example.network.GeminiService.sanitizeFileName(curMovie.title).year ?: ""
                                }
                                val formattedAirDate = com.example.util.EpisodeMetadataParser.formatAirDate(displayYear)
                                if (formattedAirDate.isNotBlank()) formattedAirDate else {
                                    val formattedDate = com.example.util.InfuseUIHelper.formatDate(displayYear)
                                    if (formattedDate.isNotBlank()) formattedDate else displayYear
                                }
                            } else {
                                val savedYear = viewModel.getSavedTmdbReleaseYear(curMovie.id, curMovie.title)
                                val rawYear = if (!savedYear.isNullOrBlank()) {
                                    savedYear
                                } else if (curMovie.releaseYear.isNotBlank()) {
                                    curMovie.releaseYear
                                } else {
                                    com.example.network.GeminiService.sanitizeFileName(curMovie.title).year ?: ""
                                }
                                rawYear.split("-").firstOrNull()?.trim() ?: rawYear
                            }
                        }

                        val genreText = remember(curMovie.id, curMovie.genres, initialMovie.genres, isTvShow) {
                            val epGenres = viewModel.getSavedTmdbGenres(curMovie.id, curMovie.title, isTv = isTvShow)
                            val seriesGenres = if (isTvShow) viewModel.getSavedTmdbGenres(initialMovie.id, initialMovie.title, isTv = true) else emptyList()
                            val combined = if (epGenres.isNotEmpty()) epGenres else if (seriesGenres.isNotEmpty()) seriesGenres else curMovie.genres.ifEmpty { initialMovie.genres }
                            val sanitized = com.example.util.TmdbGenreMapper.sanitizeGenres(combined)
                            sanitized.filter { it.isNotBlank() }.joinToString(" • ")
                        }

                        val certification = remember(curMovie.title, isTvShow) {
                            val t = curMovie.title.lowercase()
                            if (t.contains("from") || t.contains("daredevil")) {
                                "TV-MA"
                            } else if (t.contains("loki") || t.contains("prison") || t.contains("moon knight")) {
                                "TV-14"
                            } else if (isTvShow) {
                                "TV-14"
                            } else {
                                "PG-13"
                            }
                        }

                        val displayTitle = remember(curMovie, curInfo, isTvShow) {
                            if (isTvShow) {
                                "S${curInfo.seasonNumber} • E${curInfo.episodeNumber} — ${curInfo.cleanEpisodeTitle}"
                            } else {
                                ""
                            }
                        }

                        val rawTitleText = remember(curMovie, initialMovie, isTvShow) {
                            if (isTvShow) {
                                val sTitle = com.example.network.GeminiService.cleanShowTitle(initialMovie.title)
                                if (sTitle.isNotBlank()) sTitle else initialMovie.title
                            } else {
                                curMovie.title
                            }
                        }

                        val mediaTitleText = remember(rawTitleText) {
                            rawTitleText.trim().uppercase()
                        }

                        // TMDB Official Transparent Logo URL
                        val initialLogoUrl = remember(curMovie, initialMovie, isTvShow) {
                            viewModel.getTransparentLogoUrl(curMovie, initialMovie)
                        }

                        var currentLogoUrl by remember(curMovie.id, initialMovie.id) {
                            mutableStateOf(initialLogoUrl)
                        }

                        var logoLoadFailed by remember(currentLogoUrl) {
                            mutableStateOf(false)
                        }

                        // Asynchronously fetch TMDB Logo from images endpoint if not yet cached
                        LaunchedEffect(curMovie.id, initialMovie.id, isTvShow) {
                            if (currentLogoUrl.isBlank()) {
                                val fetched = viewModel.fetchAndCacheLogoUrl(
                                    movie = if (isTvShow) initialMovie else curMovie,
                                    title = rawTitleText,
                                    isTv = isTvShow,
                                    year = if (isTvShow) null else curMovie.releaseYear
                                )
                                if (!fetched.isNullOrBlank()) {
                                    currentLogoUrl = fetched
                                }
                            }
                        }

                        val titleFontSize = when {
                            mediaTitleText.length > 36 -> 20.sp
                            mediaTitleText.length > 24 -> 24.sp
                            mediaTitleText.length > 16 -> 28.sp
                            mediaTitleText.length > 10 -> 32.sp
                            else -> 36.sp
                        }

                        val titleLineHeight = when {
                            mediaTitleText.length > 36 -> 24.sp
                            mediaTitleText.length > 24 -> 28.sp
                            mediaTitleText.length > 16 -> 32.sp
                            mediaTitleText.length > 10 -> 36.sp
                            else -> 40.sp
                        }

                        val titleLetterSpacing = when {
                            mediaTitleText.length > 24 -> 0.5.sp
                            mediaTitleText.length > 12 -> 1.0.sp
                            else -> 1.5.sp
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. TMDB OFFICIAL LOGO OR CINEMATIC FALLBACK TITLE
                            // Hidden when the selected detail poster already contains embedded title/logo text
                            if (!isDetailPosterTextBearing) {
                                if (currentLogoUrl.isNotBlank() && !logoLoadFailed) {
                                    val logoContext = LocalContext.current
                                    AsyncImage(
                                        model = remember(currentLogoUrl, logoContext) {
                                            coil.request.ImageRequest.Builder(logoContext)
                                                .data(currentLogoUrl)
                                                .crossfade(true)
                                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                                                .build()
                                        },
                                        imageLoader = coil.Coil.imageLoader(logoContext),
                                        contentDescription = "$mediaTitleText Official TMDB Logo",
                                        contentScale = ContentScale.Fit,
                                        onError = { state ->
                                            if (com.example.BuildConfig.DEBUG) {
                                                android.util.Log.e(
                                                    "TMDB_LOGO",
                                                    "Logo image load failed for '$mediaTitleText' url='$currentLogoUrl': ${state.result.throwable.message}",
                                                    state.result.throwable
                                                )
                                            }
                                            logoLoadFailed = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 32.dp, max = 68.dp)
                                            .padding(horizontal = 20.dp, vertical = 0.dp)
                                            .testTag("details_tmdb_logo")
                                    )
                                } else if (mediaTitleText.isNotBlank()) {
                                    // Fallback when TMDB has no logo or loading failed
                                    Text(
                                        text = mediaTitleText,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = titleFontSize,
                                            lineHeight = titleLineHeight,
                                            letterSpacing = titleLetterSpacing,
                                            color = Color.White,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.95f),
                                                offset = androidx.compose.ui.geometry.Offset(0f, 3.5f),
                                                blurRadius = 16f
                                            )
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 0.dp)
                                            .testTag("details_cinematic_title")
                                    )
                                }
                            }

                            // 2. TV EPISODE SUBTITLE (e.g. S1 • E10 — Sleight of Hand, kept separate below series logo/title)
                            if (isTvShow && displayTitle.isNotBlank()) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = if (displayTitle.length > 32) 15.sp else 16.5.sp,
                                        letterSpacing = (-0.2).sp,
                                        color = Color.White.copy(alpha = 0.92f),
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.90f),
                                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                            blurRadius = 8f
                                        )
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 0.dp)
                                        .testTag("details_title")
                                )
                            }

                            // Layer 5: METADATA ROW -> Runtime • Release Year • [Certification] • Resolution
                            val metadataTextColor = Color.White.copy(alpha = 0.76f)
                            val metadataDotColor = Color.White.copy(alpha = 0.45f)
                            val metadataTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                letterSpacing = (-0.1).sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                if (runtimeText.isNotBlank()) {
                                    Text(
                                        text = runtimeText,
                                        color = metadataTextColor,
                                        style = metadataTextStyle
                                    )
                                }

                                if (dateText.isNotBlank()) {
                                    Text(
                                        text = dateText,
                                        color = metadataTextColor,
                                        style = metadataTextStyle
                                    )
                                }

                                if (certification.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, Color.White.copy(alpha = 0.56f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = certification,
                                            color = Color.White.copy(alpha = 0.78f),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                letterSpacing = 0.2.sp
                                            )
                                        )
                                    }
                                }

                                if (detailQuality.isNotBlank()) {
                                    Text(
                                        text = detailQuality,
                                        color = metadataTextColor,
                                        style = metadataTextStyle
                                    )
                                }
                            }

                            // Layer 5.1: Real TMDB Genres Row -> Crime, Mystery, Thriller (iOS Infuse comma-separated style)
                            if (genreText.isNotBlank()) {
                                val formattedGenres = remember(genreText) {
                                    if (genreText.contains(" • ")) {
                                        genreText.split(" • ").joinToString(", ")
                                    } else {
                                        genreText
                                    }
                                }
                                Text(
                                    text = formattedGenres,
                                    color = Color.White.copy(alpha = 0.74f),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        letterSpacing = (-0.1).sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Layer 6: PROMINENT HERO PLAY / RESUME BUTTON (Sitting on blurred artwork background)
                    val heroTargetData = remember(episodesDataList, isTvShow, movie, initialMovie, userHasSelectedAnEpisode, savedWatchState) {
                        if (isTvShow && episodesDataList.isNotEmpty()) {
                            if (userHasSelectedAnEpisode || savedWatchState != null) {
                                episodesDataList.firstOrNull { it.movie.id == movie.id } ?: episodesDataList.first()
                            } else {
                                val partiallyWatched = episodesDataList.firstOrNull { ep ->
                                    ep.savedPosMs > 0L && ep.watchedRatio < 0.9f
                                }
                                if (partiallyWatched != null) {
                                    partiallyWatched
                                } else {
                                    val nextUnwatched = episodesDataList.firstOrNull { ep -> ep.watchedRatio < 0.9f }
                                    nextUnwatched ?: episodesDataList.first()
                                }
                            }
                        } else null
                    }

                    val heroTargetMovieId = remember(heroTargetData, movie) {
                        heroTargetData?.movie?.id ?: movie.id
                    }

                    val currentSavedPos = remember(heroTargetData, movie, savedPositions) {
                        if (heroTargetData != null) heroTargetData.savedPosMs
                        else viewModel.getSavedPlaybackPosition(movie.id)
                    }

                    val movieDurationMs = remember(heroTargetData, movie, savedDurations) {
                        if (heroTargetData != null) heroTargetData.totalDurationMs
                        else {
                            val knownDurMs = viewModel.getSavedPlaybackDuration(movie.id)
                            if (knownDurMs > 0L) knownDurMs else parseDurationMs(movie.duration)
                        }
                    }

                    val watchProgress = remember(currentSavedPos, movieDurationMs) {
                        if (movieDurationMs > 0L) {
                            (currentSavedPos.toFloat() / movieDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }

                    val formattedSavedTime = remember(currentSavedPos) {
                        com.example.util.iOSResumeButtonHelper.formatTimestamp(currentSavedPos)
                    }

                    val isResume = currentSavedPos > 0L && watchProgress > 0f

                    val heroButtonText = remember(movie, isResume, formattedSavedTime, isTvShow) {
                        if (isResume) {
                            "Resume $formattedSavedTime"
                        } else {
                            "Play"
                        }
                    }

                    val animatedWatchProgress by animateFloatAsState(
                        targetValue = if (isResume) watchProgress.coerceIn(0f, 1f) else 0f,
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing
                        ),
                        label = "ResumeButtonProgressAnimation"
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp)
                    ) {
                        // Clean iOS Infuse Rounded Play/Resume Button (Width fills max width with 24.dp horizontal padding)
                        val playButtonShape = RoundedCornerShape(percent = 50)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(48.dp)
                                .clip(playButtonShape)
                                .background(Color(0xFFBDBDBD), shape = playButtonShape)
                                .infuseClickable {
                                    viewModel.navigateTo(AppScreen.Player(heroTargetMovieId, startPositionMs = if (isResume) currentSavedPos else 0L))
                                }
                                .testTag(if (isResume) "resume_button" else "play_button"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Dynamic progress fill layer on the left with smooth transition animation
                            if (animatedWatchProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedWatchProgress)
                                        .background(Color.White)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = heroButtonText,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.5.sp,
                                        letterSpacing = (-0.2).sp,
                                        color = Color(0xFF333333)
                                    )
                                )
                            }
                        }

                        // Layer 7: Quick Action Row (5 capsule/oval buttons aligned with Play button matching reference screenshot)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Eye / Anti-Spoiler / Watched Toggle
                            val isEyeActive = if (isTvShow) {
                                isActiveEpWatched || manuallyRevealedIds.contains(activeMovieId)
                            } else {
                                isMovieWatched
                            }
                            DetailActionPillButton(
                                icon = if (isEyeActive) Icons.Filled.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isTvShow) "Anti-Spoiler" else "Toggle Watched",
                                iconSize = 22.dp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isTvShow) {
                                        viewModel.toggleWatched(activeMovieId)
                                        if (isActiveEpWatched) {
                                            viewModel.clearSavedPlaybackPosition(activeMovieId)
                                            manuallyRevealedIds = manuallyRevealedIds - activeMovieId
                                            Toast.makeText(context, "Episode marked unwatched", Toast.LENGTH_SHORT).show()
                                        } else {
                                            manuallyRevealedIds = manuallyRevealedIds + activeMovieId
                                            Toast.makeText(context, "Episode marked watched and unblurred", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        viewModel.toggleWatched(movie.id)
                                        if (isMovieWatched) {
                                            viewModel.clearSavedPlaybackPosition(movie.id)
                                            Toast.makeText(context, "Movie marked unwatched", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Movie marked watched", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            // 2. Bookmark / Ribbon Toggle
                            val isBookmarked = watchlist.contains(movie.id)
                            DetailActionPillButton(
                                icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                iconSize = 21.dp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.toggleWatchlist(movie.id)
                                    Toast.makeText(context, if (isBookmarked) "Removed from Bookmarks" else "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                                }
                            )

                            // 3. Star / Favorite Toggle
                            DetailActionPillButton(
                                icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                                iconSize = 22.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("details_favorite_button"),
                                onClick = {
                                    viewModel.toggleFavorite(movie.id)
                                    val willBeFav = !isFavorite
                                    Toast.makeText(
                                        context,
                                        if (willBeFav) "Added to Favorites" else "Removed from Favorites",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            // 4. Trash / Delete (One-time Action)
                            DetailActionPillButton(
                                icon = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete",
                                iconSize = 21.dp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    showDeleteDialog = true
                                }
                            )

                            // 5. Add to List / Playlist Toggle
                            var isAddedToList by remember(movie.id) { mutableStateOf(false) }
                            DetailActionPillButton(
                                icon = if (isAddedToList) Icons.Default.PlaylistAddCheck else Icons.Default.PlaylistAdd,
                                contentDescription = "Add to List",
                                iconSize = 23.dp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isAddedToList = !isAddedToList
                                    Toast.makeText(context, if (isAddedToList) "Added to Playlist" else "Removed from Playlist", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (!isTvShow) {
                            WatchTrailerPillButton(
                                isLoading = isFetchingTrailer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .testTag("watch_trailer_button"),
                                onClick = {
                                    if (isFetchingTrailer) return@WatchTrailerPillButton
                                    trailerStatusMessage = null
                                    isFetchingTrailer = true
                                    trailerCoroutineScope.launch {
                                        when (val result = viewModel.fetchMovieTrailer(movie)) {
                                            is com.example.viewmodel.TrailerLookupResult.Available -> {
                                                isFetchingTrailer = false
                                                activeTrailerCandidates = result.candidates
                                                showTrailerModal = true
                                            }
                                            is com.example.viewmodel.TrailerLookupResult.NotAvailable -> {
                                                isFetchingTrailer = false
                                                trailerStatusMessage = "Trailer not available"
                                                Toast.makeText(context, "Trailer not available", Toast.LENGTH_SHORT).show()
                                            }
                                            is com.example.viewmodel.TrailerLookupResult.Error -> {
                                                isFetchingTrailer = false
                                                trailerStatusMessage = result.message
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // 4. SYNOPSIS
                val effectiveSynopsis = remember(movie.id, movie.title, movie.synopsis, activeEpisodeInfo, isTvShow) {
                    if (isTvShow) {
                        val epOverview = viewModel.getSavedTmdbEpisodeOverview(
                            movieId = movie.id,
                            showTitle = com.example.network.GeminiService.cleanShowTitle(movie.title),
                            seasonNumber = activeEpisodeInfo.seasonNumber,
                            episodeNumber = activeEpisodeInfo.episodeNumber
                        )
                        if (!epOverview.isNullOrBlank()) epOverview else movie.synopsis
                    } else {
                        val movieOverview = viewModel.getSavedTmdbOverview(movie.id, movie.title)
                        if (!movieOverview.isNullOrBlank()) movieOverview else movie.synopsis
                    }
                }

                val fullSynopsisText = remember(effectiveSynopsis, movie.synopsis) {
                    effectiveSynopsis.ifBlank { movie.synopsis }
                }

                var canExpandSynopsis by remember(fullSynopsisText) { mutableStateOf(false) }
                var synopsisCutOffIndex by remember(fullSynopsisText) { mutableStateOf<Int?>(null) }

                val inlineMoreColor = Color(0xFF9E9E9E)
                val synopsisTextColor = Color(0xFFF5F5F5)

                val displayAnnotatedSynopsis = remember(fullSynopsisText, isSynopsisExpanded, canExpandSynopsis, synopsisCutOffIndex) {
                    buildAnnotatedString {
                        if (!isSynopsisExpanded && canExpandSynopsis && synopsisCutOffIndex != null) {
                            val cut = synopsisCutOffIndex!!.coerceIn(0, fullSynopsisText.length)
                            val truncatedBody = fullSynopsisText.substring(0, cut).trimEnd()
                            append(truncatedBody)
                            append("... ")
                            withStyle(
                                SpanStyle(
                                    color = inlineMoreColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.5.sp,
                                    letterSpacing = (-0.15).sp
                                )
                            ) {
                                append("MORE")
                            }
                        } else if (isSynopsisExpanded && canExpandSynopsis) {
                            append(fullSynopsisText)
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = inlineMoreColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.5.sp,
                                    letterSpacing = (-0.15).sp
                                )
                            ) {
                                append("LESS")
                            }
                        } else {
                            append(fullSynopsisText)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .testTag("read_more_button")
                ) {
                    Text(
                        text = displayAnnotatedSynopsis,
                        color = synopsisTextColor,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 21.sp,
                            letterSpacing = (-0.15).sp
                        ),
                        maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { textLayoutResult ->
                            if (!isSynopsisExpanded) {
                                if (synopsisCutOffIndex == null) {
                                    val hasOverflow = textLayoutResult.hasVisualOverflow ||
                                            textLayoutResult.lineCount > 3 ||
                                            (textLayoutResult.lineCount == 3 && textLayoutResult.getLineEnd(2, visibleEnd = true) < fullSynopsisText.length)
                                    if (hasOverflow) {
                                        canExpandSynopsis = true
                                        val line3End = textLayoutResult.getLineEnd(
                                            lineIndex = 2.coerceAtMost(textLayoutResult.lineCount - 1),
                                            visibleEnd = true
                                        )
                                        var targetCut = (line3End - 6).coerceAtLeast(0)
                                        while (targetCut > line3End - 16 && targetCut > 0 && !fullSynopsisText[targetCut].isWhitespace()) {
                                            targetCut--
                                        }
                                        synopsisCutOffIndex = targetCut.coerceAtLeast(0)
                                    }
                                } else if (textLayoutResult.lineCount > 3) {
                                    val newCut = (synopsisCutOffIndex!! - 6).coerceAtLeast(0)
                                    if (newCut != synopsisCutOffIndex) {
                                        synopsisCutOffIndex = newCut
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (supportsNativeBlur) {
                                    Modifier.blur(activeSynopsisBlurRadius)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                if (shouldBlurActiveSynopsis) {
                                    manuallyRevealedIds = manuallyRevealedIds + activeMovieId
                                    Toast.makeText(context, "Synopsis Revealed", Toast.LENGTH_SHORT).show()
                                } else if (canExpandSynopsis || fullSynopsisText.length > 120) {
                                    isSynopsisExpanded = !isSynopsisExpanded
                                }
                            }
                            .testTag("details_synopsis")
                    )
                }

                // Ratings Row (TMDB Score & Audience Trakt Badge placed below synopsis matching iOS Infuse)
                val effectiveRating = remember(movie.id, movie.rating, activeEpisodeInfo) {
                    val epRating = viewModel.getSavedTmdbEpisodeRating(
                        movieId = movie.id,
                        showTitle = com.example.network.GeminiService.cleanShowTitle(movie.title),
                        seasonNumber = activeEpisodeInfo.seasonNumber,
                        episodeNumber = activeEpisodeInfo.episodeNumber
                    )
                    if (epRating != null && epRating > 0f) {
                        epRating
                    } else {
                        val savedMovieRating = viewModel.getSavedTmdbMovieRating(movie.id, movie.title)
                        if (savedMovieRating != null && savedMovieRating > 0f) savedMovieRating else movie.rating
                    }
                }

                val audienceScoreText = remember(movie.id, movie.tomatoScore, effectiveRating) {
                    val savedTomato = viewModel.getSavedTmdbTomatoScore(movie.id, movie.title)
                    val finalTomato = if (!savedTomato.isNullOrBlank()) savedTomato else movie.tomatoScore
                    if (finalTomato.isNotBlank() && !finalTomato.equals("N/A", ignoreCase = true) && !finalTomato.equals("100%", ignoreCase = true)) {
                        if (finalTomato.endsWith("%")) finalTomato else "${finalTomato}%"
                    } else if (effectiveRating > 0f) {
                        "${(effectiveRating * 10).toInt()}%"
                    } else {
                        "—"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TMDB Score with authentic official cyan 3-tier logo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.ui.components.TmdbOfficialLogo()
                        Text(
                            text = if (effectiveRating > 0f) String.format(java.util.Locale.US, "%.1f", effectiveRating) else "—",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp
                        )
                    }

                    // Audience Trakt Checked Square Badge (matching iOS Infuse magenta/fuchsia icon + white score)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.5.dp))
                                .background(Color(0xFF8A1856).copy(alpha = 0.45f))
                                .border(1.2.dp, Color(0xFFD81B60), RoundedCornerShape(4.5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Audience Rating Checkmark",
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = audienceScoreText,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp
                        )
                    }
                }

                // 3. SEASON & EPISODE STACK CAROUSEL SECTION
                if (isTvShow && episodesDataList.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        // Season Selector Header & Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Season $selectedSeasonNum",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    )
                                )

                                Surface(
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    val count = seasonsMap[selectedSeasonNum]?.size ?: 0
                                    Text(
                                        text = "$count ${if (count == 1) "Episode" else "Episodes"}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Season Pills if multiple seasons exist
                            if (seasonsMap.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(seasonsMap.keys.toList(), key = { it }) { seasonNo ->
                                        val isSel = seasonNo == selectedSeasonNum
                                        Surface(
                                            onClick = { selectedSeasonNum = seasonNo },
                                            color = if (isSel) primaryColor else Color.White.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "S$seasonNo",
                                                color = if (isSel) Color.White else Color.White.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // HORIZONTAL EPISODE CAROUSEL STACK
                        val currentSeasonEpisodes = seasonsMap[selectedSeasonNum] ?: episodesDataList

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(end = 24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(currentSeasonEpisodes, key = { it.movie.id }) { epCard ->
                                val isSelected = epCard.movie.id == activeMovieId

                                EpisodeItemCard(
                                    episodeData = epCard,
                                    isSelected = isSelected,
                                    primaryColor = primaryColor,
                                    showBackdropPath = initialMovie.backdropUrl,
                                    showPosterPath = initialMovie.posterUrl,
                                    isSpoilerRevealed = isSpoilerRevealed,
                                    isRevealed = epCard.isWatched || manuallyRevealedIds.contains(epCard.movie.id),
                                    onToggleReveal = {
                                        viewModel.toggleWatched(epCard.movie.id)
                                        if (manuallyRevealedIds.contains(epCard.movie.id)) {
                                            manuallyRevealedIds = manuallyRevealedIds - epCard.movie.id
                                        } else {
                                            manuallyRevealedIds = manuallyRevealedIds + epCard.movie.id
                                        }
                                    },
                                    onEpisodeClick = {
                                        activeMovieId = epCard.movie.id
                                        userHasSelectedAnEpisode = true
                                    }
                                )
                            }
                        }
                    }
                }

                // 6. CAST & CREW SECTION
                val effectiveCast = remember(movie, viewModel.getSavedTmdbCastList(movie.id, movie.title)) {
                    val rawList = viewModel.getEffectiveCast(movie).toMutableList()
                    if (movie.director.isNotBlank() && !movie.director.equals("Self", ignoreCase = true) && !movie.director.equals("Infuse Player", ignoreCase = true)) {
                        val directorMember = CastMember(
                            name = movie.director,
                            character = "Director",
                            imageUrl = movie.directorImageUrl
                        )
                        if (rawList.none { it.name.equals(movie.director, ignoreCase = true) }) {
                            rawList.add(0, directorMember)
                        }
                    }
                    rawList.take(15)
                }

                if (effectiveCast.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Cast and Crew",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = (-0.2).sp
                            )
                        )

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val density = LocalDensity.current
                            val cardSpacing = 8.dp
                            val visibleCount = 5
                            val cardSpacingPx = with(density) { cardSpacing.toPx() }
                            val maxWidthPx = with(density) { maxWidth.toPx() }
                            val safetyPx = with(density) { 6.dp.toPx() }
                            val totalSpacingPx = cardSpacingPx * (visibleCount - 1)
                            val itemWidthPx = if (maxWidthPx > totalSpacingPx + safetyPx) {
                                (maxWidthPx - totalSpacingPx - safetyPx) / visibleCount.toFloat()
                            } else {
                                with(density) { 56.dp.toPx() }
                            }
                            val itemWidth = with(density) { itemWidthPx.toDp() }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clipToBounds()
                            ) {
                                items(effectiveCast, key = { "${it.name}_${it.character}" }) { member ->
                                    CastMemberCard(
                                        member = member,
                                        cardWidth = itemWidth,
                                        onTap = { selectedCastMember = member }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // 7. FLOATING ACTION BUTTONS (TOP OVERLAY)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Back Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                    .infuseClickable { viewModel.navigateBack() }
                    .testTag("back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Poster Dialog state
                var showEditPosterDialog by remember { mutableStateOf(false) }
                var customPosterInput by remember { mutableStateOf("") }
                val targetPosterMovie = if (isTvShow) initialMovie else movie
                val targetShowTitle = remember(isTvShow, initialMovie.title) {
                    if (isTvShow) com.example.network.GeminiService.cleanShowTitle(initialMovie.title) else null
                }

                val posterPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let { imageUri ->
                        try {
                            val inputStream = context.contentResolver.openInputStream(imageUri)
                            val bytes = inputStream?.use { it.readBytes() }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                val base64Url = "data:$mimeType;base64,$base64Data"
                                viewModel.saveDetailPosterOverride(
                                    movieId = targetPosterMovie.id,
                                    customUrl = base64Url,
                                    hasEmbeddedText = false,
                                    isTvShow = isTvShow,
                                    showTitle = targetShowTitle
                                )
                                Toast.makeText(context, "Detail poster updated!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Dark Translucent Circular Options Button (•••)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                        .infuseClickable {
                            customPosterInput = effectiveSeriesPoster.ifBlank { targetPosterMovie.posterUrl }
                            showEditPosterDialog = true
                        }
                        .testTag("options_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        containerColor = Color(0xFF1E2121),
                        shape = RoundedCornerShape(16.dp),
                        title = {
                            Text(
                                text = "Delete File",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to remove this item from your library?",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                    performVersionAwareDeletion(movie.id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF3B30),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Delete", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteDialog = false }
                            ) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    )
                }

                if (showEditPosterDialog) {
                    InfusePosterManagementDialog(
                        targetMovie = targetPosterMovie,
                        isTvShow = isTvShow,
                        showTitle = targetShowTitle,
                        currentPosterUrl = effectiveSeriesPoster.ifBlank { targetPosterMovie.posterUrl },
                        initialCustomUrl = customPosterInput,
                        primaryColor = primaryColor,
                        viewModel = viewModel,
                        surfaceLabel = "Detail Screen Poster",
                        onPickFromGallery = {
                            posterPickerLauncher.launch("image/*")
                            showEditPosterDialog = false
                        },
                        onApplyPosterUrl = { chosenUrl, hasEmbeddedText ->
                            viewModel.saveDetailPosterOverride(
                                movieId = targetPosterMovie.id,
                                customUrl = chosenUrl,
                                hasEmbeddedText = hasEmbeddedText,
                                isTvShow = isTvShow,
                                showTitle = targetShowTitle
                            )
                            Toast.makeText(context, "Detail poster updated!", Toast.LENGTH_SHORT).show()
                            showEditPosterDialog = false
                        },
                        onApplyPosterAndMetadata = { chosenUrl, metadata, tmdbId, hasEmbeddedText ->
                            if (metadata != null) {
                                viewModel.applyPosterAndMatchedMetadata(
                                    targetMovie = targetPosterMovie,
                                    customPosterUrl = chosenUrl,
                                    metadata = metadata,
                                    tmdbId = tmdbId,
                                    isTvShow = isTvShow,
                                    showTitle = targetShowTitle,
                                    hasEmbeddedText = hasEmbeddedText,
                                    isCardOverride = false
                                )
                                Toast.makeText(context, "Poster & metadata updated successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.saveDetailPosterOverride(
                                    movieId = targetPosterMovie.id,
                                    customUrl = chosenUrl,
                                    hasEmbeddedText = hasEmbeddedText,
                                    isTvShow = isTvShow,
                                    showTitle = targetShowTitle
                                )
                                Toast.makeText(context, "Detail poster updated!", Toast.LENGTH_SHORT).show()
                            }
                            showEditPosterDialog = false
                        },
                        onResetToDefault = if (!userSelectedCustomPoster.isNullOrBlank()) {
                            {
                                viewModel.saveDetailPosterOverride(
                                    movieId = targetPosterMovie.id,
                                    customUrl = "",
                                    hasEmbeddedText = false,
                                    isTvShow = isTvShow,
                                    showTitle = targetShowTitle
                                )
                                Toast.makeText(context, "Detail poster reset to default", Toast.LENGTH_SHORT).show()
                                showEditPosterDialog = false
                            }
                        } else null,
                        onDismiss = { showEditPosterDialog = false }
                    )
                }
            }
        }

        // 8. CAST MEMBER DETAIL DIALOG (SMOOTH SPRING ANIMATED PANEL)
        if (selectedCastMember != null) {
            val member = selectedCastMember!!
            Dialog(onDismissRequest = { selectedCastMember = null }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1B1E1F),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Cast Avatar
                        val dialogMemberReq = com.example.util.rememberTmdbImageRequest(member.imageUrl, "w185")
                        val dialogMemberImageUrl = remember(member.imageUrl) {
                            com.example.util.PosterCardUIHelper.formatTmdbUrl(member.imageUrl, "w185")
                        }

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.DarkGray)
                                .border(2.dp, primaryColor, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = dialogMemberReq ?: dialogMemberImageUrl,
                                imageLoader = coil.Coil.imageLoader(LocalContext.current),
                                contentDescription = member.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = member.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            if (member.character.isNotBlank()) {
                                Text(
                                    text = "as ${member.character}",
                                    color = primaryColor,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Text(
                            text = "${member.name} stars as ${if (member.character.isNotBlank()) member.character else "a key character"} in this production.",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { selectedCastMember = null },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Floating clean trailer unavailable/error status pill (zero layout shift)
        AnimatedVisibility(
            visible = !trailerStatusMessage.isNullOrBlank(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color(0xFF1E1E20).copy(alpha = 0.95f),
                border = BorderStroke(0.85.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = trailerStatusMessage.orEmpty(),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (showTrailerModal && activeTrailerCandidates.isNotEmpty()) {
            val modalTitle = remember(movie.title) {
                com.example.network.GeminiService.sanitizeFileName(movie.title).title.ifBlank { movie.title }
            }
            com.example.ui.components.TrailerPlayerModal(
                movieTitle = modalTitle,
                candidates = activeTrailerCandidates,
                onClose = { showTrailerModal = false }
            )
        }
    }
}

@Composable
private fun WatchTrailerPillButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val dimProgress by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isPressed) 55 else 150,
            easing = FastOutSlowInEasing
        ),
        label = "watch_trailer_press"
    )
    val pillShape = RoundedCornerShape(percent = 50)
    val baseLipHeight = (2f * (1f - dimProgress)).dp
    val surfaceGradient = Brush.verticalGradient(
        0.0f to Color(0xFF343439),
        0.48f to Color(0xFF222226),
        1.0f to Color(0xFF151518)
    )
    val topSheenGradient = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.12f * (1f - 0.65f * dimProgress)),
        0.52f to Color.Transparent
    )
    val bevelBorderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.34f * (1f - 0.55f * dimProgress)),
        0.55f to Color.White.copy(alpha = 0.12f * (1f - 0.50f * dimProgress)),
        1.0f to Color.Black.copy(alpha = 0.55f)
    )
    val contentAlpha = 1f - 0.45f * dimProgress

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(pillShape)
            .background(color = Color(0xFF0C0C0E), shape = pillShape)
            .border(
                width = 0.6.dp,
                color = Color.White.copy(alpha = 0.08f * (1f - 0.5f * dimProgress)),
                shape = pillShape
            )
            .infuseClickable(
                interactionSource = interactionSource,
                enabled = !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = baseLipHeight)
                .clip(pillShape)
                .background(brush = surfaceGradient, shape = pillShape)
                .background(brush = topSheenGradient, shape = pillShape)
                .border(width = 0.9.dp, brush = bevelBorderBrush, shape = pillShape),
            contentAlignment = Alignment.Center
        ) {
            if (dimProgress > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f * dimProgress))
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.9f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Loading Trailer...",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = "Watch Trailer",
                        tint = Color.White.copy(alpha = contentAlpha),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Watch Trailer",
                        color = Color.White.copy(alpha = contentAlpha),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailActionPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val dimProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isPressed) 55 else 150,
            easing = FastOutSlowInEasing
        ),
        label = "detail_action_3d_press"
    )
    val actionButtonShape = RoundedCornerShape(percent = 50)
    val baseLipHeight = (2.5f * (1f - dimProgress)).dp
    val surfaceGradient = Brush.verticalGradient(
        0.0f to Color(0xFF36363B),
        0.48f to Color(0xFF232327),
        1.0f to Color(0xFF151518)
    )
    val topSheenGradient = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.13f * (1f - 0.65f * dimProgress)),
        0.52f to Color.Transparent
    )
    val bevelBorderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.38f * (1f - 0.55f * dimProgress)),
        0.55f to Color.White.copy(alpha = 0.12f * (1f - 0.50f * dimProgress)),
        1.0f to Color.Black.copy(alpha = 0.55f)
    )
    val iconTint = Color.White.copy(alpha = 1f - 0.45f * dimProgress)

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(actionButtonShape)
            .background(color = Color(0xFF0C0C0E), shape = actionButtonShape)
            .border(
                width = 0.6.dp,
                color = Color.White.copy(alpha = 0.08f * (1f - 0.5f * dimProgress)),
                shape = actionButtonShape
            )
            .infuseClickable(
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Raised 3D pill face that physically depresses into the base when pressed
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = baseLipHeight)
                .clip(actionButtonShape)
                .background(brush = surfaceGradient, shape = actionButtonShape)
                .background(brush = topSheenGradient, shape = actionButtonShape)
                .border(width = 0.9.dp, brush = bevelBorderBrush, shape = actionButtonShape),
            contentAlignment = Alignment.Center
        ) {
            if (dimProgress > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f * dimProgress))
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun EpisodeItemCard(
    episodeData: EpisodeCardData,
    isSelected: Boolean,
    primaryColor: Color,
    showBackdropPath: String?,
    showPosterPath: String?,
    onEpisodeClick: () -> Unit,
    onToggleReveal: () -> Unit,
    isRevealed: Boolean,
    modifier: Modifier = Modifier,
    isSpoilerRevealed: Boolean = com.example.util.ShowDetailActionsHelper.isSpoilerRevealed()
) {
    // Unique Episode Thumbnail: Load unique still_path poster image using https://image.tmdb.org/t/p/w500 + still_path
    val stillPath = episodeData.stillPath
    val imageUrl = remember(stillPath, episodeData.movie.backdropUrl, episodeData.movie.posterUrl, showBackdropPath, showPosterPath) {
        val raw = if (!stillPath.isNullOrBlank()) stillPath else {
            episodeData.movie.backdropUrl.ifBlank {
                episodeData.movie.posterUrl.ifBlank {
                    showBackdropPath.orEmpty().ifBlank { showPosterPath.orEmpty() }
                }
            }
        }
        com.example.util.PosterCardUIHelper.formatTmdbUrl(raw, "w780")
    }

    val epReq = com.example.util.rememberTmdbImageRequest(imageUrl, "w780")

    val shouldBlur = !isRevealed
    val supportsNativeBlur = remember { android.os.Build.VERSION.SDK_INT >= 31 }
    val animatedBlurRadius by animateDpAsState(
        targetValue = if (shouldBlur) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "ThumbnailBlurAnimation"
    )

    Column(
        modifier = modifier
            .width(160.dp)
            .infuseClickable { onEpisodeClick() }
    ) {
        // Thumbnail Box (Fixed 92dp Height - Cinematic 16:9 Aspect Ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E24))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                // Anti-Spoiler Episode Blur: Apply blur effect if episode is unwatched (isWatched == false)
                AsyncImage(
                    model = epReq ?: imageUrl,
                    imageLoader = coil.Coil.imageLoader(LocalContext.current),
                    contentDescription = episodeData.info.cleanEpisodeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (supportsNativeBlur) {
                                Modifier.blur(animatedBlurRadius)
                            } else {
                                Modifier
                            }
                        )
                )
            }

            if (shouldBlur) {
                if (!supportsNativeBlur) {
                    // Fallback to masked dark overlay with a lock icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Spoiler Protected",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hidden",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .clickable { onToggleReveal() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Spoiler Protected",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Episode Number Badge (Top Start Overlay)
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
                    .background(
                        if (isSelected) primaryColor else Color.Black.copy(alpha = 0.65f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "E${episodeData.info.episodeNumber}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Progress bar indicator if partially watched
            if (episodeData.savedPosMs > 0L) {
                val progressPct = com.example.util.InfuseUIHelper.calculateProgressPercentage(episodeData.savedPosMs, episodeData.totalDurationMs)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPct / 100f)
                            .background(primaryColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Episode Title
        val titleText = if (!episodeData.info.cleanEpisodeTitle.isNullOrBlank()) episodeData.info.cleanEpisodeTitle else "Episode ${episodeData.info.episodeNumber}"
        Text(
            text = "${episodeData.info.episodeNumber}. $titleText",
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) primaryColor else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CastMemberCard(
    member: CastMember,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 60.dp
) {
    val castItemReq = com.example.util.rememberTmdbImageRequest(member.imageUrl, "w185")
    val formattedImageUrl = remember(member.imageUrl) {
        com.example.util.PosterCardUIHelper.formatTmdbUrl(member.imageUrl, "w185")
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .infuseClickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF222526))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = castItemReq ?: formattedImageUrl,
                imageLoader = coil.Coil.imageLoader(LocalContext.current),
                contentDescription = member.name,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = member.name,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (member.character.isNotBlank()) member.character else "Actor",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class MetadataMatchPreviewState(
    val posterUrl: String,
    val hasEmbeddedText: Boolean = true,
    val isLoading: Boolean = true,
    val identifiedTitle: String? = null,
    val identifiedYear: String? = null,
    val identifiedIsTv: Boolean = false,
    val candidates: List<com.example.network.GeminiService.TmdbTitleSearchCandidate> = emptyList(),
    val selectedCandidate: com.example.network.GeminiService.TmdbTitleSearchCandidate? = null,
    val metadata: com.example.network.ScrapedMediaMetadata? = null,
    val tmdbResult: com.example.network.GeminiService.TmdbMovieResult? = null,
    val errorMessage: String? = null
)

@Composable
fun MetadataMatchPreviewView(
    previewState: MetadataMatchPreviewState,
    primaryColor: Color,
    onSwitchCandidate: (com.example.network.GeminiService.TmdbTitleSearchCandidate) -> Unit,
    onConfirmSave: () -> Unit,
    onApplyPosterOnly: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (previewState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = primaryColor, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                    Text(
                        text = "Analyzing poster artwork & fetching matching metadata...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val meta = previewState.metadata
            val identifiedTitle = previewState.identifiedTitle.orEmpty().ifBlank { meta?.title.orEmpty() }
            val year = previewState.identifiedYear.orEmpty().ifBlank { meta?.releaseYear.orEmpty() }
            val isTv = previewState.identifiedIsTv || (previewState.selectedCandidate?.isTv == true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Large Poster Preview
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF222528))
                        .border(1.5.dp, primaryColor.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                ) {
                    val req = com.example.util.rememberTmdbImageRequest(previewState.posterUrl, "w500")
                    com.example.util.SharedPosterImage(
                        model = req ?: previewState.posterUrl,
                        contentDescription = "Selected Poster",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Title & Fetched Metadata Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isTv) "TV SERIES" else "MOVIE",
                                color = primaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        if (year.isNotBlank()) {
                            Text(
                                text = year,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = if (identifiedTitle.isNotBlank()) identifiedTitle else "Identified Poster Match",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val ratingVal = meta?.rating
                    if (ratingVal != null && ratingVal > 0f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f/10", ratingVal),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val tomScore = meta.tomatoScore
                            if (!tomScore.isNullOrBlank()) {
                                Text(
                                    text = "• RT $tomScore",
                                    color = Color(0xFFFF5B00),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (!meta?.genres.isNullOrEmpty()) {
                        Text(
                            text = meta!!.genres.take(3).joinToString(" • "),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Overview / Synopsis
            if (!meta?.description.isNullOrBlank()) {
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Overview", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = meta!!.description,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Cast members row
            if (!meta?.cast.isNullOrEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Top Cast", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(meta!!.cast.take(10)) { actor ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    if (actor.imageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = actor.imageUrl,
                                            contentDescription = actor.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                Text(
                                    text = actor.name,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Candidate Switcher (If multiple titles match)
            if (previewState.candidates.size > 1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Not the right match? Choose candidate:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(previewState.candidates) { cand ->
                            val isSelected = cand.tmdbId == previewState.selectedCandidate?.tmdbId
                            Surface(
                                color = if (isSelected) primaryColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) primaryColor else Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.infuseClickable { onSwitchCandidate(cand) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${cand.title} (${cand.releaseYear})",
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirmSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_save_metadata_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm & Save Poster + Metadata", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Back", fontSize = 12.5.sp)
                    }

                    OutlinedButton(
                        onClick = onApplyPosterOnly,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Use Poster Only", fontSize = 12.5.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun PosterOptionsGridSection(
    allPosters: List<com.example.network.GeminiService.TmdbPosterOption>,
    filteredPosters: List<com.example.network.GeminiService.TmdbPosterOption>,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    currentPosterUrl: String,
    primaryColor: Color,
    onSelectPosterForPreview: (com.example.network.GeminiService.TmdbPosterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Textless", "With Title").forEach { filterName ->
                    val isSelected = selectedFilter == filterName
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) primaryColor.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50)
                            )
                            .infuseClickable { onSelectFilter(filterName) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = filterName,
                            color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Text(
                text = "${filteredPosters.size} posters",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap any poster to preview it at a larger size before applying.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        val displayGridList = if (filteredPosters.isNotEmpty()) filteredPosters else allPosters

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("tmdb_poster_grid"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(
                items = displayGridList,
                key = { it.filePath }
            ) { option ->
                val isCurrentPoster = remember(currentPosterUrl, option.filePath) {
                    currentPosterUrl.isNotBlank() && currentPosterUrl.contains(option.filePath)
                }
                val thumbReq = com.example.util.rememberTmdbImageRequest(option.thumbUrl, "w500")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF202326))
                        .border(
                            width = if (isCurrentPoster) 2.dp else 1.dp,
                            color = if (isCurrentPoster) primaryColor else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .infuseClickable {
                            onSelectPosterForPreview(option)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    com.example.util.SharedPosterImage(
                        model = thumbReq ?: option.thumbUrl,
                        contentDescription = "Poster option ${option.languageLabel}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Bottom gradient label with Textless / With Title indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (option.hasEmbeddedText) "Title • ${option.languageLabel}" else "Textless",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }

                    if (isCurrentPoster) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .background(primaryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Current Poster",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfusePosterManagementDialog(
    targetMovie: Movie,
    isTvShow: Boolean,
    showTitle: String?,
    currentPosterUrl: String,
    initialCustomUrl: String,
    primaryColor: Color,
    viewModel: MainViewModel,
    surfaceLabel: String = "Poster",
    initialGalleryUri: String? = null,
    onPickFromGallery: () -> Unit,
    onApplyPosterUrl: (String, Boolean) -> Unit,
    onApplyPosterAndMetadata: ((String, com.example.network.ScrapedMediaMetadata?, Int?, Boolean) -> Unit)? = null,
    onResetToDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val displayTitle = remember(targetMovie.title, isTvShow, showTitle) {
        if (isTvShow && !showTitle.isNullOrBlank()) {
            showTitle
        } else {
            com.example.network.GeminiService.sanitizeFileName(targetMovie.title).title.ifBlank { targetMovie.title }
        }
    }

    // 0 = "Choose from TMDB", 1 = "Search Poster", 2 = "Choose from Gallery"
    var selectedTab by remember { mutableStateOf(if (!initialGalleryUri.isNullOrBlank()) 2 else 0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tmdbPosters by remember { mutableStateOf<List<com.example.network.GeminiService.TmdbPosterOption>>(emptyList()) }
    var selectedLanguageFilter by remember { mutableStateOf("All") }
    var previewPosterOption by remember { mutableStateOf<com.example.network.GeminiService.TmdbPosterOption?>(null) }
    var customUrlText by remember(initialCustomUrl) { mutableStateOf(initialCustomUrl) }
    var customUrlHasText by remember { mutableStateOf(false) }

    // Search Poster tab states
    var searchQuery by remember(displayTitle) { mutableStateOf(displayTitle) }
    var isSearchingTitles by remember { mutableStateOf(false) }
    var searchCandidates by remember { mutableStateOf<List<com.example.network.GeminiService.TmdbTitleSearchCandidate>>(emptyList()) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSearchCandidate by remember { mutableStateOf<com.example.network.GeminiService.TmdbTitleSearchCandidate?>(null) }
    var candidatePosters by remember { mutableStateOf<List<com.example.network.GeminiService.TmdbPosterOption>>(emptyList()) }
    var isLoadingCandidatePosters by remember { mutableStateOf(false) }

    // Automatic Metadata Match Preview state
    var activeMetadataPreview by remember { mutableStateOf<MetadataMatchPreviewState?>(null) }

    fun inspectPosterMetadata(
        posterUrl: String,
        hasEmbeddedText: Boolean,
        candidate: com.example.network.GeminiService.TmdbTitleSearchCandidate? = null,
        imageInput: Any? = null
    ) {
        activeMetadataPreview = MetadataMatchPreviewState(
            posterUrl = posterUrl,
            hasEmbeddedText = hasEmbeddedText,
            isLoading = true,
            selectedCandidate = candidate
        )
        coroutineScope.launch {
            try {
                if (candidate != null) {
                    val (meta, tmdbRes) = viewModel.fetchMetadataForCandidate(candidate)
                    activeMetadataPreview = MetadataMatchPreviewState(
                        posterUrl = posterUrl,
                        hasEmbeddedText = hasEmbeddedText,
                        isLoading = false,
                        identifiedTitle = candidate.title,
                        identifiedYear = candidate.releaseYear,
                        identifiedIsTv = candidate.isTv,
                        candidates = searchCandidates.ifEmpty { listOf(candidate) },
                        selectedCandidate = candidate,
                        metadata = meta,
                        tmdbResult = tmdbRes
                    )
                } else {
                    val analysis = viewModel.analyzePosterAndIdentifyMetadata(
                        imageInput = imageInput ?: posterUrl,
                        context = context,
                        fallbackHintTitle = displayTitle,
                        isTv = isTvShow
                    )
                    val topCandidate = analysis.candidates.firstOrNull()
                    activeMetadataPreview = MetadataMatchPreviewState(
                        posterUrl = posterUrl,
                        hasEmbeddedText = hasEmbeddedText,
                        isLoading = false,
                        identifiedTitle = analysis.title,
                        identifiedYear = analysis.releaseYear,
                        identifiedIsTv = analysis.isTv,
                        candidates = analysis.candidates,
                        selectedCandidate = topCandidate,
                        metadata = analysis.matchedMetadata,
                        tmdbResult = analysis.tmdbMovieResult
                    )
                }
            } catch (e: Exception) {
                activeMetadataPreview = activeMetadataPreview?.copy(
                    isLoading = false,
                    errorMessage = "Unable to load matching metadata. You can still save the poster."
                )
            }
        }
    }

    fun switchPreviewCandidate(candidate: com.example.network.GeminiService.TmdbTitleSearchCandidate) {
        val currentPoster = activeMetadataPreview?.posterUrl ?: candidate.posterFullUrl
        activeMetadataPreview = activeMetadataPreview?.copy(
            isLoading = true,
            selectedCandidate = candidate
        )
        coroutineScope.launch {
            val (meta, tmdbRes) = viewModel.fetchMetadataForCandidate(candidate)
            activeMetadataPreview = activeMetadataPreview?.copy(
                posterUrl = if (currentPoster.isNotBlank()) currentPoster else candidate.posterFullUrl,
                isLoading = false,
                identifiedTitle = candidate.title,
                identifiedYear = candidate.releaseYear,
                identifiedIsTv = candidate.isTv,
                selectedCandidate = candidate,
                metadata = meta,
                tmdbResult = tmdbRes
            )
        }
    }

    fun loadTmdbPosters() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val results = viewModel.fetchAvailableTmdbPosters(
                    movie = targetMovie,
                    isTvShow = isTvShow,
                    showTitleOverride = showTitle
                )
                tmdbPosters = results
                if (results.isEmpty()) {
                    errorMessage = "No TMDB posters found for \"$displayTitle\". Try 'Search Poster' to find a match."
                }
            } catch (e: Exception) {
                errorMessage = "Unable to load TMDB posters. Check your connection and try again."
            } finally {
                isLoading = false
            }
        }
    }

    fun performTitleSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        coroutineScope.launch {
            isSearchingTitles = true
            searchErrorMessage = null
            selectedSearchCandidate = null
            previewPosterOption = null
            try {
                val matches = viewModel.searchTmdbTitlesForPoster(trimmed, isTvShow)
                searchCandidates = matches
                if (matches.isEmpty()) {
                    searchErrorMessage = "No movies or TV series found matching \"$trimmed\"."
                }
            } catch (e: Exception) {
                searchErrorMessage = "Search failed. Please check your connection and try again."
            } finally {
                isSearchingTitles = false
            }
        }
    }

    fun selectCandidateAndLoadPosters(candidate: com.example.network.GeminiService.TmdbTitleSearchCandidate) {
        selectedSearchCandidate = candidate
        previewPosterOption = null
        coroutineScope.launch {
            isLoadingCandidatePosters = true
            try {
                val posters = viewModel.fetchAvailableTmdbPostersForCandidate(candidate)
                candidatePosters = posters
            } catch (e: Exception) {
                candidatePosters = emptyList()
            } finally {
                isLoadingCandidatePosters = false
            }
        }
    }

    LaunchedEffect(targetMovie.id, isTvShow, showTitle) {
        loadTmdbPosters()
    }

    LaunchedEffect(initialGalleryUri) {
        if (!initialGalleryUri.isNullOrBlank()) {
            inspectPosterMetadata(
                posterUrl = initialGalleryUri,
                hasEmbeddedText = false,
                imageInput = initialGalleryUri
            )
        }
    }

    val activeSourcePosters = if (selectedTab == 1 && selectedSearchCandidate != null) {
        candidatePosters
    } else {
        tmdbPosters
    }

    val filteredPosters = remember(activeSourcePosters, selectedLanguageFilter) {
        when (selectedLanguageFilter) {
            "Textless" -> activeSourcePosters.filter { !it.hasEmbeddedText }
            "With Title" -> activeSourcePosters.filter { it.hasEmbeddedText }
            else -> activeSourcePosters
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("poster_management_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141618),
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (activeMetadataPreview != null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .infuseClickable { activeMetadataPreview = null }
                                    .testTag("poster_preview_back_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to posters",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (previewPosterOption != null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .infuseClickable { previewPosterOption = null }
                                    .testTag("poster_preview_back_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to posters",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (selectedTab == 1 && selectedSearchCandidate != null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .infuseClickable { selectedSearchCandidate = null }
                                    .testTag("search_candidate_back_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to search results",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(primaryColor.copy(alpha = 0.16f), CircleShape)
                                    .border(1.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    activeMetadataPreview != null -> "Metadata Match Preview"
                                    previewPosterOption != null -> "Preview Poster"
                                    selectedTab == 1 && selectedSearchCandidate != null -> selectedSearchCandidate!!.title
                                    else -> "Select $surfaceLabel"
                                },
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$displayTitle • ${if (isTvShow) "TV Series" else "Movie"}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("poster_dialog_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3-Tab Mode Switcher: "Choose from TMDB" | "Search Poster" | "Choose from Gallery"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "Choose from TMDB", "choose_from_tmdb_button"),
                        Triple(1, "Search Poster", "search_poster_tab_button"),
                        Triple(2, "Choose from Gallery", "choose_from_gallery_tab_button")
                    )
                    tabs.forEach { (tabIdx, tabTitle, tag) ->
                        val isSelected = selectedTab == tabIdx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) primaryColor else Color.Transparent)
                                .infuseClickable {
                                    selectedTab = tabIdx
                                    previewPosterOption = null
                                    activeMetadataPreview = null
                                    if (tabIdx == 1 && searchCandidates.isEmpty() && !isSearchingTitles) {
                                        performTitleSearch(searchQuery)
                                    }
                                }
                                .padding(horizontal = 4.dp)
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val currentMetadataPreview = activeMetadataPreview
                if (currentMetadataPreview != null) {
                    MetadataMatchPreviewView(
                        previewState = currentMetadataPreview,
                        primaryColor = primaryColor,
                        onSwitchCandidate = { cand -> switchPreviewCandidate(cand) },
                        onConfirmSave = {
                            val metaToSave = currentMetadataPreview.metadata ?: com.example.network.ScrapedMediaMetadata(
                                title = currentMetadataPreview.identifiedTitle ?: displayTitle,
                                releaseYear = currentMetadataPreview.identifiedYear ?: "",
                                description = targetMovie.synopsis,
                                cast = targetMovie.cast.map { com.example.network.ScrapedCastMember(it.name, it.character, it.imageUrl) }
                            )
                            val tmdbId = currentMetadataPreview.selectedCandidate?.tmdbId ?: currentMetadataPreview.tmdbResult?.movieId
                            if (onApplyPosterAndMetadata != null) {
                                onApplyPosterAndMetadata(currentMetadataPreview.posterUrl, metaToSave, tmdbId, currentMetadataPreview.hasEmbeddedText)
                            } else {
                                onApplyPosterUrl(currentMetadataPreview.posterUrl, currentMetadataPreview.hasEmbeddedText)
                            }
                        },
                        onApplyPosterOnly = {
                            onApplyPosterUrl(currentMetadataPreview.posterUrl, currentMetadataPreview.hasEmbeddedText)
                        },
                        onBack = { activeMetadataPreview = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    val activePreview = previewPosterOption
                    if (activePreview != null && (selectedTab == 0 || selectedTab == 1)) {
                        // LARGE POSTER PREVIEW BEFORE CONFIRMING
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 6.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF202326))
                                    .border(
                                        width = 2.dp,
                                        color = primaryColor.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val previewReq = com.example.util.rememberTmdbImageRequest(activePreview.fullUrl, "w780")
                                com.example.util.SharedPosterImage(
                                    model = previewReq ?: activePreview.fullUrl,
                                    contentDescription = "Preview poster for $displayTitle",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = primaryColor.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = if (activePreview.hasEmbeddedText) "With Title (${activePreview.languageLabel})" else "Textless",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = activePreview.resolutionLabel,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { previewPosterOption = null },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preview_back_to_grid_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Back", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        inspectPosterMetadata(
                                            posterUrl = activePreview.fullUrl,
                                            hasEmbeddedText = activePreview.hasEmbeddedText,
                                            candidate = selectedSearchCandidate
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(48.dp)
                                        .testTag("apply_tmdb_poster_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryColor,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Match Metadata", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (selectedTab == 0) {
                    // TAB 0: IN-APP TMDB POSTER BROWSER FOR CURRENT MATCH
                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Loading TMDB posters for \"$displayTitle\"...",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("tmdb_poster_browser_skeleton_grid")
                            ) {
                                items(6, key = { "poster_skeleton_$it" }) {
                                    com.example.util.InfuseShimmerBox(
                                        shape = RoundedCornerShape(10.dp),
                                        accentColor = primaryColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                    )
                                }
                            }
                        }
                    } else if (tmdbPosters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = errorMessage ?: "No TMDB posters found for \"$displayTitle\".",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { loadTmdbPosters() },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedTab = 1
                                            if (searchCandidates.isEmpty()) performTitleSearch(searchQuery)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("Search Poster")
                                    }
                                }
                            }
                        }
                    } else {
                        PosterOptionsGridSection(
                            allPosters = tmdbPosters,
                            filteredPosters = filteredPosters,
                            selectedFilter = selectedLanguageFilter,
                            onSelectFilter = { selectedLanguageFilter = it },
                            currentPosterUrl = currentPosterUrl,
                            primaryColor = primaryColor,
                            onSelectPosterForPreview = { previewPosterOption = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                } else if (selectedTab == 1) {
                    // TAB 1: SEARCH POSTER BY ANY MOVIE OR TV SERIES TITLE
                    val chosenCandidate = selectedSearchCandidate
                    if (chosenCandidate == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            "Search movie or series title...",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 13.sp
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_poster_input")
                                )

                                Button(
                                    onClick = { performTitleSearch(searchQuery) },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("search_poster_submit_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search TMDB",
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isSearchingTitles) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = primaryColor, strokeWidth = 3.dp)
                                }
                            } else if (searchCandidates.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = searchErrorMessage ?: "Enter a movie or series title above to search TMDB posters.",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Tap a matching title to browse its available posters:",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("search_poster_candidates_list"),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(
                                        items = searchCandidates,
                                        key = { "${it.isTv}_${it.tmdbId}" }
                                    ) { candidate ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                                .infuseClickable { selectCandidateAndLoadPosters(candidate) }
                                                .padding(10.dp)
                                                .testTag("search_candidate_item_${candidate.tmdbId}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(54.dp)
                                                    .aspectRatio(2f / 3f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF222528)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (candidate.posterThumbUrl.isNotBlank()) {
                                                    val req = com.example.util.rememberTmdbImageRequest(candidate.posterThumbUrl, "w342")
                                                    com.example.util.SharedPosterImage(
                                                        model = req ?: candidate.posterThumbUrl,
                                                        contentDescription = candidate.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Movie,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = candidate.title,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        color = primaryColor.copy(alpha = 0.18f),
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                                                    ) {
                                                        Text(
                                                            text = if (candidate.isTv) "TV Series" else "Movie",
                                                            color = primaryColor,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    if (candidate.releaseYear.isNotBlank()) {
                                                        Text(
                                                            text = candidate.releaseYear,
                                                            color = Color.White.copy(alpha = 0.65f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                if (candidate.overview.isNotBlank()) {
                                                    Text(
                                                        text = candidate.overview,
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontSize = 11.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Browse posters",
                                                tint = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Candidate selected: show its available TMDB posters in the grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .infuseClickable { selectedSearchCandidate = null }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back to search results",
                                        tint = primaryColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Search Results",
                                        color = primaryColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = buildString {
                                        append(chosenCandidate.title)
                                        if (chosenCandidate.releaseYear.isNotBlank()) {
                                            append(" (${chosenCandidate.releaseYear})")
                                        }
                                    },
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            if (isLoadingCandidatePosters) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("tmdb_candidate_poster_skeleton_grid")
                                ) {
                                    items(6, key = { "cand_poster_skeleton_$it" }) {
                                        com.example.util.InfuseShimmerBox(
                                            shape = RoundedCornerShape(10.dp),
                                            accentColor = primaryColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f / 3f)
                                        )
                                    }
                                }
                            } else if (candidatePosters.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No posters available for \"${chosenCandidate.title}\".",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                PosterOptionsGridSection(
                                    allPosters = candidatePosters,
                                    filteredPosters = filteredPosters,
                                    selectedFilter = selectedLanguageFilter,
                                    onSelectFilter = { selectedLanguageFilter = it },
                                    currentPosterUrl = currentPosterUrl,
                                    primaryColor = primaryColor,
                                    onSelectPosterForPreview = { previewPosterOption = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    // TAB 2: GALLERY & CUSTOM URL TAB
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Device Gallery",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Choose a custom image stored on your device for \"$displayTitle\" ($surfaceLabel).",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = onPickFromGallery,
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("choose_from_gallery_action_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("Choose from Gallery", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Custom Poster URL",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = customUrlText,
                                    onValueChange = { customUrlText = it },
                                    placeholder = {
                                        Text(
                                            "https://example.com/poster.jpg",
                                            color = Color.White.copy(alpha = 0.35f)
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .infuseClickable { customUrlHasText = !customUrlHasText }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Poster image already contains title/logo text",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.5.sp
                                    )
                                    Switch(
                                        checked = customUrlHasText,
                                        onCheckedChange = { customUrlHasText = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = primaryColor
                                        )
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (customUrlText.isNotBlank()) {
                                            inspectPosterMetadata(
                                                posterUrl = customUrlText.trim(),
                                                hasEmbeddedText = customUrlHasText,
                                                imageInput = customUrlText.trim()
                                            )
                                        }
                                    },
                                    enabled = customUrlText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Match Metadata & Use URL", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (onResetToDefault != null) {
                            OutlinedButton(
                                onClick = onResetToDefault,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("reset_poster_override_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset to Default TMDB Poster", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

}
