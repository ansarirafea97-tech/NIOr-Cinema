@file:Suppress("UnsafeOptInUsageError")

package com.example.ui.screens

import android.os.Build
import com.example.MainActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.hoverable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.example.model.filePath
import com.example.model.fileName
import com.example.ui.components.SeekDiagnosticsDialog
import com.example.util.SeekDiagnosticsLogger
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.view.Surface
import com.example.util.MediaCodec10BitHelper
import com.example.util.LocalMediaSeekHelper
import com.example.util.PlaybackDiagnosticInfo
import android.media.MediaPlayer
import android.net.Uri
import com.example.model.Movie
import com.example.model.mockMovies
import com.example.ui.components.FrostedGlassContainer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.snapshots.Snapshot
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.AppScreen
import com.example.viewmodel.AppTheme
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.AspectRatio

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.text.Cue
import androidx.media3.ui.AspectRatioFrameLayout

enum class AspectRatioMode(
    val displayName: String,
    val resizeMode: Int
) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    CROP("Crop", AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    fun next(): AspectRatioMode = when (this) {
        FIT -> STRETCH
        STRETCH -> CROP
        CROP -> FIT
    }

    val icon: androidx.compose.ui.graphics.vector.ImageVector
        get() = when (this) {
            FIT -> Icons.Default.AspectRatio
            STRETCH -> Icons.Default.FitScreen
            CROP -> Icons.Default.Crop
        }
}

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

private fun cleanSubtitleText(text: String): String {
    return text
        // Remove text in square brackets: e.g., [music playing] or [Gentle background score]
        .replace(Regex("""\[.*?\]"""), "")
        // Remove text in parentheses: e.g., (sighs) or (music playing)
        .replace(Regex("""\(.*?\)"""), "")
        // Remove speaker identifiers: e.g., "NARRATOR:" or "JOHN:"
        .replace(Regex("""^[A-Z\s]{2,15}:"""), "")
        // Remove HTML/XML style tags like <i>, <b>, <font color="..."> etc.
        .replace(Regex("""<.*?>"""), "")
        // Clean up common entities
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        // Trim any leftover leading/trailing newlines or spaces
        .trim()
}

private fun parseSrtOrVttToCues(rawText: String): List<SubtitleCue> {
    if (rawText.isBlank()) return emptyList()
    val cues = mutableListOf<SubtitleCue>()
    val clean = rawText
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("""(?m)^WEBVTT.*$"""), "")
        .replace(Regex("""(?m)^NOTE.*$"""), "")
    val blocks = clean.split("\n\n")

    val timeRegex = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})""")

    for (block in blocks) {
        val trimmedBlock = block.trim()
        if (trimmedBlock.isBlank()) continue
        val lines = trimmedBlock.split("\n")
        if (lines.isNotEmpty()) {
            val match = timeRegex.find(trimmedBlock)
            if (match != null) {
                val groupValues = match.groupValues
                val sh = groupValues[1].toLongOrNull() ?: 0L
                val sm = groupValues[2].toLongOrNull() ?: 0L
                val ss = groupValues[3].toLongOrNull() ?: 0L
                val sms = groupValues[4].toLongOrNull() ?: 0L

                val eh = groupValues[5].toLongOrNull() ?: 0L
                val em = groupValues[6].toLongOrNull() ?: 0L
                val es = groupValues[7].toLongOrNull() ?: 0L
                val ems = groupValues[8].toLongOrNull() ?: 0L

                val start = sh * 3600000L + sm * 60000L + ss * 1000L + sms
                val end = eh * 3600000L + em * 60000L + es * 1000L + ems

                val textLines = lines.filter { !timeRegex.containsMatchIn(it) && !it.trim().all { char -> char.isDigit() } }
                val text = textLines.joinToString("\n")
                val cleanedText = cleanSubtitleText(text)
                if (cleanedText.isNotBlank()) {
                    cues.add(SubtitleCue(start, end, cleanedText))
                }
            }
        }
    }
    return cues
}

private fun getCharsetForEncodingName(encodingName: String): java.nio.charset.Charset {
    return try {
        when {
            encodingName.contains("UTF-8", ignoreCase = true) -> Charsets.UTF_8
            encodingName.contains("UTF-16LE", ignoreCase = true) -> Charsets.UTF_16LE
            encodingName.contains("UTF-16BE", ignoreCase = true) -> Charsets.UTF_16BE
            encodingName.contains("UTF-16", ignoreCase = true) -> Charsets.UTF_16
            encodingName.contains("ISO-8859-1", ignoreCase = true) || encodingName.contains("Latin-1", ignoreCase = true) -> Charsets.ISO_8859_1
            encodingName.contains("Windows-1252", ignoreCase = true) || encodingName.contains("1252", ignoreCase = true) -> java.nio.charset.Charset.forName("windows-1252")
            encodingName.contains("Windows-1256", ignoreCase = true) || encodingName.contains("1256", ignoreCase = true) -> java.nio.charset.Charset.forName("windows-1256")
            encodingName.contains("Windows-1251", ignoreCase = true) || encodingName.contains("1251", ignoreCase = true) -> java.nio.charset.Charset.forName("windows-1251")
            encodingName.contains("GBK", ignoreCase = true) || encodingName.contains("GB18030", ignoreCase = true) || encodingName.contains("GB2312", ignoreCase = true) -> java.nio.charset.Charset.forName("GB18030")
            encodingName.contains("Big5", ignoreCase = true) -> java.nio.charset.Charset.forName("Big5")
            encodingName.contains("Shift-JIS", ignoreCase = true) || encodingName.contains("SJIS", ignoreCase = true) -> java.nio.charset.Charset.forName("Shift_JIS")
            encodingName.contains("EUC-KR", ignoreCase = true) -> java.nio.charset.Charset.forName("EUC-KR")
            encodingName.contains("ISO-8859-6", ignoreCase = true) -> java.nio.charset.Charset.forName("ISO-8859-6")
            encodingName.contains("ISO-8859-9", ignoreCase = true) -> java.nio.charset.Charset.forName("ISO-8859-9")
            else -> Charsets.UTF_8
        }
    } catch (_: Exception) {
        Charsets.UTF_8
    }
}

private fun parseSubtitleBytesWithEncoding(bytes: ByteArray?, encodingName: String): List<SubtitleCue> {
    if (bytes == null || bytes.isEmpty()) return emptyList()
    return try {
        val charset = getCharsetForEncodingName(encodingName)
        val decodedText = String(bytes, charset)
        parseSrtOrVttToCues(decodedText)
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            parseSrtOrVttToCues(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private fun generateContextualSubtitles(movie: com.example.model.Movie): List<SubtitleCue> {
    val title = movie.title
    val overview = if (movie.synopsis.isNotBlank()) movie.synopsis else "Infuse Ultra HD Cinema Playback Engine"
    val cues = mutableListOf<SubtitleCue>()
    
    val totalMs = 120 * 60 * 1000L // 2 hours
    var currentMs = 1000L
    
    val script = listOf(
        "Welcome to the cinema experience.",
        "Today, we are watching $title.",
        "Let's dive into this amazing story.",
        overview.take(60) + "...",
        "The story unfolds beautifully.",
        "Stay tuned for the epic conclusion.",
        "Thank you for watching with Infuse."
    )
    
    while (currentMs < totalMs) {
        for (line in script) {
            val end = currentMs + 4000L
            val cleanLine = cleanSubtitleText(line)
            if (cleanLine.isNotBlank()) {
                cues.add(SubtitleCue(currentMs, end, cleanLine))
            }
            currentMs = end + 2000L
            if (currentMs >= totalMs) break
        }
    }
    return cues
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(
    movieId: String,
    startPositionMs: Long = 0L,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentTheme by viewModel.selectedTheme.collectAsState()
    val primaryColor = currentTheme.primaryColor
    val primaryContainer = currentTheme.primaryContainer
    val haptic = LocalHapticFeedback.current

    val movies by viewModel.moviesList.collectAsState()
    val movie = remember(movieId, movies) {
        movies.firstOrNull { it.id == movieId } ?: run {
            val titleClean = if (movieId.startsWith("content://") || movieId.startsWith("file://")) {
                movieId.substringAfterLast("/").substringBeforeLast(".")
            } else if (movieId.startsWith("asset:///")) {
                movieId.substringAfter("asset:///").substringBeforeLast(".")
            } else {
                "Imported Video"
            }
            val cachedPoster = viewModel.getSavedTmdbPosterUrl(movieId, titleClean)
            val cachedCast = viewModel.getSavedTmdbCastList(movieId, titleClean)
            val cachedOverview = viewModel.getSavedTmdbOverview(movieId, titleClean)

            val sanitized = com.example.network.GeminiService.sanitizeFileName(titleClean)
            val poster = if (!cachedPoster.isNullOrBlank()) cachedPoster else "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop"
            val castList = cachedCast
            val synopsis = if (!cachedOverview.isNullOrBlank()) cachedOverview else ""

            Movie(
                id = movieId,
                title = sanitized.title.ifBlank { titleClean },
                posterUrl = poster,
                backdropUrl = poster,
                rating = 4.8f,
                category = "Movies",
                genre = emptyList(),
                duration = "2h 42m",
                synopsis = synopsis,
                cast = castList,
                director = "Director",
                directorImageUrl = "",
                releaseYear = sanitized.year ?: "",
                views = "1.5M",
                tomatoScore = "92%"
            )
        }
    }

    val playerUiState by viewModel.playerUiState.collectAsState()
    val activeEpisode = playerUiState?.currentEpisode

    var currentPlayingEpId by remember(movieId) { mutableStateOf(movieId) }

    val currentMovie = remember(currentPlayingEpId, movies) {
        movies.firstOrNull { it.id == currentPlayingEpId } ?: movie
    }

    val currentEpisode = remember(activeEpisode, currentPlayingEpId, currentMovie) {
        if (activeEpisode != null && activeEpisode.id == currentPlayingEpId) {
            activeEpisode
        } else {
            com.example.viewmodel.PlayerEpisode(
                id = currentMovie.id,
                showTitle = com.example.network.GeminiService.cleanShowTitle(currentMovie.title),
                seasonNumber = com.example.util.EpisodeUtils.extractSeasonAndEpisode(currentMovie.title).season ?: 1,
                episodeNumber = com.example.util.EpisodeUtils.extractSeasonAndEpisode(currentMovie.title).episode ?: 1,
                episodeTitle = com.example.util.EpisodeUtils.cleanEpisodeTitle(currentMovie.title).let {
                    if (it.isBlank()) com.example.network.GeminiService.sanitizeFileName(currentMovie.title).title else it
                },
                mediaUrl = currentMovie.id,
                posterUrl = currentMovie.posterUrl,
                synopsis = currentMovie.synopsis,
                duration = currentMovie.duration
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setPlaying(true)
    }

    LaunchedEffect(currentPlayingEpId) {
        viewModel.selectEpisode(currentPlayingEpId)
    }

    val currentQueue = playerUiState?.episodesQueue ?: emptyList()

    val isMovie = remember(movie, currentEpisode) {
        val cat = movie.category
        val isMovieCategory = cat.equals("Movies", ignoreCase = true) || cat.equals("Home Videos", ignoreCase = true)
        val isTvCategory = cat.equals("Shows", ignoreCase = true) || cat.contains("TV", ignoreCase = true) || cat.contains("Series", ignoreCase = true) || cat.contains("Show", ignoreCase = true)
        
        if (isMovieCategory) {
            true
        } else if (isTvCategory) {
            false
        } else {
            val parsed = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movie.title)
            parsed.season == null && parsed.episode == null
        }
    }
    val showPlaylistButton = remember(isMovie, currentQueue) {
        !isMovie && currentQueue.size > 1
    }

    val nextEpisodeInQueue = remember(currentEpisode, currentQueue) {
        val currentIdx = currentQueue.indexOfFirst { it.id == currentEpisode.id }
        if (currentIdx in 0 until currentQueue.size - 1) {
            currentQueue[currentIdx + 1]
        } else null
    }

    val prevEpisodeInQueue = remember(currentEpisode, currentQueue) {
        val currentIdx = currentQueue.indexOfFirst { it.id == currentEpisode.id }
        if (currentIdx > 0 && currentIdx < currentQueue.size) {
            currentQueue[currentIdx - 1]
        } else null
    }

    var showEpisodeDrawer by remember { mutableStateOf(false) }

    var durationStateMs by remember { mutableStateOf(0L) }
    var positionStateMs by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var exoPlayerRef by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var activeVideoSize by remember { mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN) }
    var activeAudioSessionId by remember { mutableStateOf(0) }
    var activeVideoFormat by remember { mutableStateOf<androidx.media3.common.Format?>(null) }
    var diagnosticErrorInfo by remember { mutableStateOf<PlaybackDiagnosticInfo?>(null) }
    var audioTracks by remember { mutableStateOf<List<String>>(listOf("English - Stereo")) }
    var subtitleTracks by remember { mutableStateOf(listOf("Off")) }
    var detectedTextTracks by remember { mutableStateOf<List<Triple<androidx.media3.common.Tracks.Group, Int, String>>>(emptyList()) }
    var nativeCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var selectedSubtitleTrack by remember { mutableStateOf("Off") }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isVideoReady by remember { mutableStateOf(false) }
    var videoSurfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }
    var isSurfaceReady by remember { mutableStateOf(false) }
    var hasPlaybackStarted by remember(currentPlayingEpId) { mutableStateOf(false) }
    var currentlyLoadedMediaKey by remember { mutableStateOf<String?>(null) }
    var loadedPlayerInstance by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var loadedMediaKey by remember { mutableStateOf<String?>(null) }
    var resumeAppliedForMediaKey by remember { mutableStateOf<String?>(null) }
    var isApplyingResume by remember { mutableStateOf(false) }
    var isSeekInProgress by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember { mutableLongStateOf(androidx.media3.common.C.TIME_UNSET) }
    var isSeekingActive by remember { mutableStateOf(false) }
    var lastSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var showSeekDiagnosticsDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Immersive True Fullscreen Mode setup
    val activity = context.findActivity()

    val saveCurrentPlaybackPosition = { exo: androidx.media3.exoplayer.ExoPlayer ->
        if (hasPlaybackStarted) {
            val currentPos = exo.currentPosition
            val posToSave = if (pendingSeekPositionMs != androidx.media3.common.C.TIME_UNSET && pendingSeekPositionMs > 0L) {
                pendingSeekPositionMs
            } else if (currentPos > 0L) {
                currentPos
            } else if (lastSeekTargetMs != null && lastSeekTargetMs!! > 0L) {
                lastSeekTargetMs!!
            } else {
                positionStateMs.coerceAtLeast(0L)
            }
            val effectiveDur = when {
                exo.duration > 0L && exo.duration != androidx.media3.common.C.TIME_UNSET -> exo.duration
                durationStateMs > 0L -> durationStateMs
                else -> viewModel.getSavedPlaybackDuration(
                    currentEpisode.id,
                    currentEpisode.showTitle,
                    currentEpisode.seasonNumber,
                    currentEpisode.episodeNumber
                )
            }
            if (posToSave > 0L) {
                viewModel.savePlaybackPosition(currentEpisode.id, posToSave, effectiveDur)
            }
        }
    }

    val latestEpisode by rememberUpdatedState(currentEpisode)
    val latestMovie by rememberUpdatedState(currentMovie)
    val latestMovies by rememberUpdatedState(movies)
    val latestNextEpisode by rememberUpdatedState(nextEpisodeInQueue)
    val latestSavePlaybackPosition by rememberUpdatedState(saveCurrentPlaybackPosition)

    // Clean up player on back button press or back gesture
    androidx.activity.compose.BackHandler {
        try {
            com.example.util.VolumeBoostManager.disableVolumeBoost()
            com.example.util.MediaSessionManager.releaseSession(context)
            exoPlayerRef?.let { exo ->
                latestSavePlaybackPosition(exo)
                if (exo.isPlaying) exo.pause()
                exo.clearVideoSurface()
                exo.stop()
                exo.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModel.setPlaying(false)
        mediaPlayerRef = null
        exoPlayerRef = null
        videoSurfaceViewRef = null
        isSurfaceReady = false
        viewModel.navigateBack()
    }

    DisposableEffect(activity) {
        val originalBrightness = activity?.window?.attributes?.screenBrightness ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (activity != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (activity != null) {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                try {
                    val params = activity.window.attributes
                    params.screenBrightness = originalBrightness
                    activity.window.attributes = params
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(context) {
        if (exoPlayerRef == null) {
            try {
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                val headers = mapOf(
                    "User-Agent" to userAgent,
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )

                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

                val extractorsFactory = LocalMediaSeekHelper.createExtractorsFactory(context)

                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(defaultDataSourceFactory, extractorsFactory)

                val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15000,
                        50000,
                        100,
                        400
                    )
                    .setBackBuffer(30000, true)
                    .build()

                val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()

                val exo = androidx.media3.exoplayer.ExoPlayer.Builder(context)
                    .setRenderersFactory(MediaCodec10BitHelper.createRenderersFactory(context))
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setLoadControl(loadControl)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    .build()

                // Detailed Analytics Logging for 10-bit / Main10 playback engine
                exo.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onVideoDecoderInitialized(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        val isSoftware = com.example.util.MediaCodec10BitHelper.lastSelectedDecoderIsSoftware ||
                                decoderName.startsWith("c2.android.", ignoreCase = true) ||
                                decoderName.startsWith("omx.google.", ignoreCase = true) ||
                                decoderName.contains("ffmpeg", ignoreCase = true) ||
                                decoderName.contains("software-10bit", ignoreCase = true)
                        val streamKey = com.example.util.MediaCodec10BitHelper.currentStreamKey
                        val isFallback = com.example.util.MediaCodec10BitHelper.isStreamUsingSoftwareFallback(streamKey)
                        val fallbackReason = com.example.util.MediaCodec10BitHelper.lastFallbackReason ?: "None"
                        val activeFmt = activeVideoFormat ?: com.example.util.MediaCodec10BitHelper.currentActiveFormat
                        val is10Bit = com.example.util.MediaCodec10BitHelper.is10BitHevcFormat(activeFmt)

                        if (decoderName.contains("ffmpeg", ignoreCase = true) || decoderName.contains("software-10bit", ignoreCase = true)) {
                            android.util.Log.i("10BitPlaybackEngine", "[10BIT] FFmpeg video renderer selected: $decoderName")
                        } else {
                            android.util.Log.i("10BitPlaybackEngine", "[10BIT] platform decoder selected: $decoderName")
                        }

                        android.util.Log.i("10BitPlaybackEngine", """
                            |=== [10-BIT PLAYBACK DIAGNOSTIC] ===
                            |Video: ${if (is10Bit) "HEVC Main 10" else (activeFmt?.sampleMimeType ?: "video/hevc")}
                            |Bit depth: ${if (is10Bit) "10-bit" else "8-bit"}
                            |Selected decoder: $decoderName
                            |Decoder type: ${if (isSoftware) "Native Software" else "Hardware"}
                            |Fallback: ${if (isFallback) "Yes" else "No"}
                            |Fallback reason: $fallbackReason
                            |=====================================
                        """.trimMargin())
                    }

                    override fun onVideoInputFormatChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        format: androidx.media3.common.Format,
                        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
                    ) {
                        activeVideoFormat = format
                        com.example.util.MediaCodec10BitHelper.currentActiveFormat = format
                        val color = format.colorInfo
                        val is10BitHevc = com.example.util.MediaCodec10BitHelper.is10BitHevcFormat(format)
                        val profileName = com.example.util.MediaCodec10BitHelper.formatProfileName(format.sampleMimeType ?: "video/hevc", format)
                        val bitDepthStr = if (color?.lumaBitdepth != null && color.lumaBitdepth > 0) "${color.lumaBitdepth}-bit" else if (is10BitHevc) "10-bit" else "8-bit"

                        android.util.Log.i("10BitPlaybackEngine", "[10BIT] detected MIME: ${format.sampleMimeType}")
                        android.util.Log.i("10BitPlaybackEngine", "[10BIT] detected HEVC profile: $profileName")
                        android.util.Log.i("10BitPlaybackEngine", "[10BIT] detected bit depth: $bitDepthStr")
                        android.util.Log.i(
                            "10BitPlaybackEngine",
                            "Video Format Active -> MIME: ${format.sampleMimeType} | Codecs: ${format.codecs} | Res: ${format.width}x${format.height}@${format.frameRate}fps | BitDepth: $bitDepthStr | ColorSpace: ${color?.colorSpace} | ColorTransfer: ${color?.colorTransfer} | 10BitHEVC: $is10BitHevc"
                        )
                    }
                })

                // Configure track selection to automatically detect and prefer main spoken dialogue tracks and support 10-bit/HDR
                val trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("en", "hi")
                    .setPreferredTextRoleFlags(androidx.media3.common.C.ROLE_FLAG_MAIN)
                    .build()
                exo.trackSelectionParameters = trackSelectionParameters

                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onCues(cues: List<Cue>) {
                        nativeCues = cues
                    }

                    override fun onRenderedFirstFrame() {
                        isVideoReady = true
                        hasPlaybackStarted = true
                        val ep = latestEpisode
                        val epMovie = latestMovies.firstOrNull { it.id == ep.id } ?: latestMovie
                        viewModel.addToHistory(epMovie)
                        if (exo.currentPosition > 0L) {
                            latestSavePlaybackPosition(exo)
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        val activePlaying = playWhenReady && exo.playbackState != androidx.media3.common.Player.STATE_ENDED
                        if (viewModel.isPlaying.value != activePlaying) {
                            viewModel.setPlaying(activePlaying)
                        }
                    }

                    override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                        val activePlaying = isPlayingNow || (exo.playWhenReady && exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
                        if (viewModel.isPlaying.value != activePlaying) {
                            viewModel.setPlaying(activePlaying)
                        }
                        if (isPlayingNow && exo.playbackState == androidx.media3.common.Player.STATE_READY) {
                            hasPlaybackStarted = true
                            val ep = latestEpisode
                            val epMovie = latestMovies.firstOrNull { it.id == ep.id } ?: latestMovie
                            viewModel.addToHistory(epMovie)
                        } else if (!isPlayingNow && hasPlaybackStarted) {
                            latestSavePlaybackPosition(exo)
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        activeVideoFormat = exo.videoFormat
                        activeVideoSize = videoSize
                        android.util.Log.d("PlayerScreen", "onVideoSizeChanged -> width=${videoSize.width}, height=${videoSize.height}")
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val currentExo = exoPlayerRef ?: exo
                        val currentFormat = activeVideoFormat ?: currentExo.videoFormat

                        val mime = currentFormat?.sampleMimeType ?: currentFormat?.containerMimeType ?: ""
                        val isHevc = mime.contains("hevc", ignoreCase = true) ||
                                error.message?.contains("hevc", ignoreCase = true) == true ||
                                error.message?.contains("c2.exynos.hevc.decoder", ignoreCase = true) == true

                        val isDecoderFailure = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                                error.errorCodeName.contains("DECODING_FAILED", ignoreCase = true) ||
                                error.cause is android.media.MediaCodec.CodecException ||
                                error.cause is androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException ||
                                error.message?.contains("c2.exynos.hevc.decoder", ignoreCase = true) == true

                        val streamKey = currentEpisode.id.ifBlank { currentEpisode.mediaUrl }
                        val alreadyFallenBack = com.example.util.MediaCodec10BitHelper.isStreamUsingSoftwareFallback(streamKey)

                        if (isHevc && isDecoderFailure && !alreadyFallenBack) {
                            val failedCodecName = com.example.util.MediaCodec10BitHelper.extractCodecNameFromError(error) ?: "c2.exynos.hevc.decoder"
                            android.util.Log.i("10BitPlaybackEngine", "[10BIT] platform decoder failed: $failedCodecName (error=${error.errorCodeName})")
                            android.util.Log.w(
                                "PlayerScreen",
                                "10-Bit HEVC hardware decoding failed with $failedCodecName (${error.errorCodeName}). Automatically switching to software HEVC decoder."
                            )
                            SeekDiagnosticsLogger.log(
                                "LOCAL_SEEK_ROOT_CAUSE",
                                "[HEVC_10BIT_FALLBACK_TRIGGERED] codec=$failedCodecName, streamKey=$streamKey, error=${error.errorCodeName}"
                            )

                            // 1. Mark hardware decoder as failed for this 10-bit stream
                            com.example.util.MediaCodec10BitHelper.markHardwareDecoderFailed(failedCodecName, streamKey)

                            // 2. Preserve current playback position to resume seamlessly
                            val savedPos = if (positionStateMs > 0L) positionStateMs else currentExo.currentPosition.coerceAtLeast(0L)

                            // 3. Clear any diagnostic error modal
                            diagnosticErrorInfo = null

                            // 4. Reload the media item with the software HEVC decoder
                            val currentItem = currentExo.currentMediaItem ?: androidx.media3.common.MediaItem.fromUri(currentEpisode.mediaUrl)
                            currentExo.stop()
                            if (savedPos > 0L) {
                                currentExo.setMediaItem(currentItem, savedPos)
                            } else {
                                currentExo.setMediaItem(currentItem)
                            }
                            currentExo.prepare()
                            currentExo.playWhenReady = true
                            currentExo.play()
                            viewModel.setPlaying(true)
                            return
                        }

                        SeekDiagnosticsLogger.log(
                            "LOCAL_SEEK_ROOT_CAUSE",
                            "[EVENT_PLAYER_ERROR] errorCode=${error.errorCode}, errorCodeName=${error.errorCodeName}, message=${error.message}, cause=${error.cause}"
                        )
                        val report = com.example.util.MediaCodec10BitHelper.buildDiagnosticReport(
                            error,
                            activeVideoFormat ?: currentExo.videoFormat
                        )
                        diagnosticErrorInfo = report
                        android.util.Log.e("PlayerScreen", "ExoPlayer 10-Bit Playback Error: ${error.message}", error)
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        activeAudioSessionId = audioSessionId
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        val realTracks = mutableListOf<String>()
                        val realSubTracks = mutableListOf<String>("Off")
                        val detectedTextList = mutableListOf<Triple<androidx.media3.common.Tracks.Group, Int, String>>()

                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                val trackGroup = group.mediaTrackGroup
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getFormat(i)
                                    val lang = format.language
                                    val langLabel = if (lang.isNullOrBlank() || lang == "und") {
                                        "Track ${realTracks.size + 1}"
                                    } else {
                                        try {
                                            java.util.Locale.forLanguageTag(lang).displayLanguage
                                        } catch (e: Exception) {
                                            lang
                                        }
                                    }
                                    val codec = format.sampleMimeType?.split("/")?.lastOrNull()?.uppercase() ?: "AUDIO"
                                    val channelCount = format.channelCount
                                    val channelLabel = when (channelCount) {
                                        1 -> "1.0"
                                        2 -> "2.0"
                                        6 -> "5.1"
                                        8 -> "7.1"
                                        else -> if (channelCount > 0) "$channelCount.0" else ""
                                    }
                                    realTracks.add(listOf(langLabel, codec, channelLabel).filter { it.isNotBlank() }.joinToString(" - "))
                                }
                            } else if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                                val trackGroup = group.mediaTrackGroup
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getFormat(i)
                                    val lang = format.language
                                    val label = format.label
                                    val langLabel = if (!label.isNullOrBlank()) {
                                        label
                                    } else if (lang.isNullOrBlank() || lang == "und") {
                                        "Subtitles ${realSubTracks.size}"
                                    } else {
                                        try {
                                            java.util.Locale.forLanguageTag(lang).displayLanguage
                                        } catch (e: Exception) {
                                            lang
                                        }
                                    }
                                    val isCC = (format.roleFlags and androidx.media3.common.C.ROLE_FLAG_CAPTION) != 0 ||
                                               format.sampleMimeType?.contains("cea", true) == true ||
                                               langLabel.contains("CC", true)
                                    val finalLabel = if (isCC && !langLabel.contains("CC", true)) "$langLabel [CC]" else langLabel
                                    realSubTracks.add(finalLabel)
                                    detectedTextList.add(Triple(group, i, finalLabel))
                                }
                            }
                        }
                        if (realTracks.isNotEmpty()) {
                            audioTracks = realTracks
                        }
                        // Always update subtitle tracks to sync with the current media's actual tracks
                        subtitleTracks = realSubTracks
                        detectedTextTracks = detectedTextList
                    }

                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        val reasonStr = when (reason) {
                            androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                            androidx.media3.common.Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE -> "SOURCE_UPDATE"
                            else -> "UNKNOWN($reason)"
                        }
                        SeekDiagnosticsLogger.log(
                            "LOCAL_SEEK_ROOT_CAUSE",
                            "[TIMELINE_READY] reason=$reasonStr ($reason), isSeekable=${exo.isCurrentMediaItemSeekable}, duration=${exo.duration}ms, isLive=${exo.isCurrentMediaItemLive}, windowCount=${timeline.windowCount}, currentPos=${exo.currentPosition}ms"
                        )
                    }

                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        val reasonStr = when (reason) {
                            androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                            androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                            androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                            androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                            else -> "UNKNOWN($reason)"
                        }
                        SeekDiagnosticsLogger.log(
                            "LOCAL_SEEK_ROOT_CAUSE",
                            "[EVENT_MEDIA_ITEM_TRANSITION] uri=${mediaItem?.localConfiguration?.uri}, reason=$reasonStr ($reason), currentPos=${exo.currentPosition}ms"
                        )
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: androidx.media3.common.Player.PositionInfo,
                        newPosition: androidx.media3.common.Player.PositionInfo,
                        reason: Int
                    ) {
                        val actual = exo.currentPosition
                        val target = lastSeekTargetMs
                        val reasonStr = when (reason) {
                            androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                            androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                            androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
                            androidx.media3.common.Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
                            androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
                            androidx.media3.common.Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
                            else -> "UNKNOWN($reason)"
                        }

                        SeekDiagnosticsLogger.log(
                            "LOCAL_SEEK_ROOT_CAUSE",
                            "[EVENT_POSITION_DISCONTINUITY] reason=$reasonStr ($reason), isSeek=${reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK}, oldPos=${oldPosition.positionMs}ms, newPos=${newPosition.positionMs}ms, actualCurrentPos=${actual}ms, targetPosition=${target}ms, isSeekable=${exo.isCurrentMediaItemSeekable}, playbackState=${exo.playbackState}"
                        )

                        if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK || reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                            positionStateMs = actual
                            val duration = exo.duration
                            if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
                                viewModel.setPlaybackProgress(
                                    (actual.toFloat() / duration.toFloat())
                                        .coerceIn(0f, 1f)
                                )
                            }
                            isSeekingActive = false
                            isSeekInProgress = false
                            pendingSeekPositionMs = androidx.media3.common.C.TIME_UNSET
                            lastSeekTargetMs = null
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val target = if (pendingSeekPositionMs != androidx.media3.common.C.TIME_UNSET) pendingSeekPositionMs else lastSeekTargetMs
                        val actual = exo.currentPosition
                        val stateStr = when (playbackState) {
                            androidx.media3.common.Player.STATE_IDLE -> "STATE_IDLE"
                            androidx.media3.common.Player.STATE_BUFFERING -> "STATE_BUFFERING"
                            androidx.media3.common.Player.STATE_READY -> "STATE_READY"
                            androidx.media3.common.Player.STATE_ENDED -> "STATE_ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }

                        SeekDiagnosticsLogger.log(
                            "LOCAL_SEEK_ROOT_CAUSE",
                            "[EVENT_PLAYBACK_STATE_CHANGED] state=$stateStr ($playbackState), actualCurrentPos=${actual}ms, targetPosition=${target}ms, bufferedPos=${exo.bufferedPosition}ms, duration=${exo.duration}ms, isSeekable=${exo.isCurrentMediaItemSeekable}, isPlaying=${exo.isPlaying}"
                        )

                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                            SeekDiagnosticsLogger.log(
                                "LOCAL_SEEK_ROOT_CAUSE",
                                "[PLAYER_READY] exo.playbackState=STATE_READY, actualCurrentPos=${actual}ms, isPlaying=${exo.isPlaying}"
                            )
                            if (isSeekInProgress || isSeekingActive) {
                                isSeekingActive = false
                                isSeekInProgress = false
                                pendingSeekPositionMs = androidx.media3.common.C.TIME_UNSET
                                lastSeekTargetMs = null
                            }
                            if (exo.playWhenReady || exo.isPlaying) {
                                isVideoReady = true
                                hasPlaybackStarted = true
                                val ep = latestEpisode
                                val epMovie = latestMovies.firstOrNull { it.id == ep.id } ?: latestMovie
                                viewModel.addToHistory(epMovie)
                                if (actual > 0L) {
                                    latestSavePlaybackPosition(exo)
                                }
                            }
                            positionStateMs = actual
                            val duration = exo.duration
                            if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
                                durationStateMs = duration
                                viewModel.setPlaybackProgress(
                                    (actual.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                )
                            }
                            SeekDiagnosticsLogger.log(
                                "LOCAL_SEEK_ROOT_CAUSE",
                                "[RESUME_POSITION_APPLIED] position applied to UI: ${actual}ms / ${exo.duration}ms"
                            )
                        } else if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            val ep = latestEpisode
                            val nextEp = latestNextEpisode
                            SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[EVENT_STATE_ENDED] episodeId=${ep.id}")
                            viewModel.onEpisodeCompleted(ep.id)
                            if (nextEp != null) {
                                currentPlayingEpId = nextEp.id
                                viewModel.selectEpisode(nextEp.id)
                                viewModel.setPlaying(true)
                            } else {
                                viewModel.setPlaying(false)
                            }
                        }
                    }
                }
                exo.addListener(listener)
                videoSurfaceViewRef?.let { sv ->
                    try {
                        exo.setVideoSurfaceView(sv)
                    } catch (_: Exception) {}
                }

                exoPlayerRef = exo
                com.example.util.MediaSessionManager.initSession(
                    context = context,
                    player = exo
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else false

            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                try {
                    exoPlayerRef?.let { exo ->
                        latestSavePlaybackPosition(exo)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                try {
                    exoPlayerRef?.let { exo ->
                        latestSavePlaybackPosition(exo)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (!inPip) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || activity?.isInPictureInPictureMode != true) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            try {
                com.example.util.VolumeBoostManager.disableVolumeBoost()
                com.example.util.MediaSessionManager.releaseSession(context)
                exoPlayerRef?.let { exo ->
                    exo.volume = 0f
                    latestSavePlaybackPosition(exo)
                    if (exo.isPlaying) exo.pause()
                    SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[EVENT_LIFECYCLE_DISPOSE_STOP_RELEASE]")
                    exo.clearVideoSurface()
                    exo.stop()
                    exo.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.setPlaying(false)
            mediaPlayerRef = null
            exoPlayerRef = null
            videoSurfaceViewRef = null
            isSurfaceReady = false
            loadedPlayerInstance = null
            loadedMediaKey = null
            resumeAppliedForMediaKey = null
            isApplyingResume = false
            isSeekingActive = false
            isSeekInProgress = false
            pendingSeekPositionMs = androidx.media3.common.C.TIME_UNSET
            lastSeekTargetMs = null
        }
    }

    val isPlayingState by viewModel.isPlaying.collectAsState()

    // Dynamic Screen Wake Lock based on video playing state
    DisposableEffect(isPlayingState, activity) {
        if (activity != null) {
            if (isPlayingState) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose { }
    }

    // Periodic position saver while playing
    LaunchedEffect(isPlayingState, exoPlayerRef, isSeekingActive, isSeekInProgress, isApplyingResume, resumeAppliedForMediaKey, loadedMediaKey, currentEpisode.id) {
        while (isPlayingState) {
            exoPlayerRef?.let { exo ->
                try {
                    val currentKey = loadedMediaKey
                    if (currentKey != null
                        && resumeAppliedForMediaKey == currentKey
                        && hasPlaybackStarted
                        && exo.isPlaying
                        && !isSeekInProgress
                        && !isSeekingActive
                        && !isApplyingResume
                        && exo.playbackState == androidx.media3.common.Player.STATE_READY
                    ) {
                        val pos = exo.currentPosition
                        val effectiveDur = when {
                            exo.duration > 0L && exo.duration != androidx.media3.common.C.TIME_UNSET -> exo.duration
                            durationStateMs > 0L -> durationStateMs
                            else -> viewModel.getSavedPlaybackDuration(
                                currentEpisode.id,
                                currentEpisode.showTitle,
                                currentEpisode.seasonNumber,
                                currentEpisode.episodeNumber
                            )
                        }
                        if (pos > 0L) {
                            SeekDiagnosticsLogger.log(
                                "LOCAL_SEEK_ROOT_CAUSE",
                                "[EVENT_PERIODIC_SAVER_TICK] pos=${pos}ms, dur=${effectiveDur}ms, isSeekingActive=$isSeekingActive, isSeekInProgress=$isSeekInProgress"
                            )
                            viewModel.savePlaybackPosition(currentEpisode.id, pos, effectiveDur)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val volume by viewModel.volumeLevel.collectAsState()
    val brightness by viewModel.brightnessLevel.collectAsState()
    val showLeftOverlay by viewModel.showLeftSwipeIndicator.collectAsState()
    val showRightOverlay by viewModel.showRightSwipeIndicator.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current

    var isScrubbing by remember { mutableStateOf(false) }
    var isUserDragging by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }
    var scrubDelta by remember { mutableStateOf(0f) }
    var isHorizontalScrubbing by remember { mutableStateOf(false) }
    var scrubStartPosMs by remember { mutableLongStateOf(0L) }
    var scrubTargetPosMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var scrubTargetProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(progress) {
        if (!isUserDragging && !isScrubbing && !isHorizontalScrubbing) {
            sliderProgress = progress
        }
    }

    var showLeftSeekRipple by remember { mutableStateOf(false) }
    var showRightSeekRipple by remember { mutableStateOf(false) }
    var leftSeekSeconds by remember { mutableIntStateOf(0) }
    var rightSeekSeconds by remember { mutableIntStateOf(0) }
    var leftSeekTriggerKey by remember { mutableIntStateOf(0) }
    var rightSeekTriggerKey by remember { mutableIntStateOf(0) }
    var lastLeftTapTime by remember { mutableLongStateOf(0L) }
    var lastRightTapTime by remember { mutableLongStateOf(0L) }
    var isHolding2x by remember { mutableStateOf(false) }
    var lastLongPressReleaseTime by remember { mutableLongStateOf(0L) }
    var isNativeGesturePaused by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying && isNativeGesturePaused) {
            isNativeGesturePaused = false
        }
    }

    // iOS-Style Top-Center Volume & Brightness HUD Overlay State
    var hudOverlayVisible by remember { mutableStateOf(false) }
    var hudType by remember { mutableStateOf("volume") }
    var hudTriggerKey by remember { mutableIntStateOf(0) }
    var isDraggingHUD by remember { mutableStateOf(false) }

    var prevVolume by remember { mutableStateOf<Float?>(null) }
    var prevBrightness by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(volume) {
        if (prevVolume != null && Math.abs(prevVolume!! - volume) > 0.001f) {
            hudType = "volume"
            hudTriggerKey++
        }
        prevVolume = volume
    }

    LaunchedEffect(brightness) {
        if (prevBrightness != null && Math.abs(prevBrightness!! - brightness) > 0.001f) {
            hudType = "brightness"
            hudTriggerKey++
        }
        prevBrightness = brightness
    }

    LaunchedEffect(showLeftOverlay) {
        if (showLeftOverlay) {
            hudType = "brightness"
            hudTriggerKey++
        }
    }

    LaunchedEffect(showRightOverlay) {
        if (showRightOverlay) {
            hudType = "volume"
            hudTriggerKey++
        }
    }

    LaunchedEffect(hudTriggerKey) {
        if (hudTriggerKey > 0) {
            hudOverlayVisible = true
            kotlinx.coroutines.delay(1200L)
            if (!isDraggingHUD) {
                hudOverlayVisible = false
            }
        }
    }

    // YouTube-style Continuous Double-Tap Seek State & DataStore Preferences
    val continuousSeekEnabled by viewModel.continuousSeekEnabled.collectAsState()
    val seekDurationSeconds by viewModel.seekDurationSeconds.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()
    val currentContinuousSeekEnabled by rememberUpdatedState(continuousSeekEnabled)
    val currentSeekDurationSeconds by rememberUpdatedState(seekDurationSeconds)
    val currentGestureSensitivity by rememberUpdatedState(gestureSensitivity)

    // activeContinuousSeekDirection: -1 = Left (Rewind), 0 = Inactive (Normal Mode), +1 = Right (Forward)
    var activeContinuousSeekDirection by remember { mutableIntStateOf(0) }
    var continuousSeekActivityKey by remember { mutableIntStateOf(0) }
    var continuousSeekTargetPositionMs by remember { mutableLongStateOf(androidx.media3.common.C.TIME_UNSET) }
    var lastSeekTapTimeMs by remember { mutableLongStateOf(0L) }

    val exitContinuousSeekMode = {
        activeContinuousSeekDirection = 0
        continuousSeekTargetPositionMs = androidx.media3.common.C.TIME_UNSET
        lastSeekTapTimeMs = 0L
        showLeftSeekRipple = false
        showRightSeekRipple = false
        leftSeekSeconds = 0
        rightSeekSeconds = 0
    }

    LaunchedEffect(currentPlayingEpId) {
        exitContinuousSeekMode()
    }

    LaunchedEffect(continuousSeekEnabled) {
        if (!continuousSeekEnabled && activeContinuousSeekDirection != 0) {
            exitContinuousSeekMode()
        }
    }

    // Automatic reset after 1 second of inactivity:
    // If the user stops tapping for 1 second or longer, reset the continuous seeking sequence.
    // The next seek must require a fresh double tap and begin with 10 seconds.
    LaunchedEffect(continuousSeekActivityKey) {
        if (continuousSeekActivityKey > 0) {
            val timeoutMs = 1000L
            kotlinx.coroutines.delay(timeoutMs)
            activeContinuousSeekDirection = 0
            continuousSeekTargetPositionMs = androidx.media3.common.C.TIME_UNSET
            showLeftSeekRipple = false
            showRightSeekRipple = false
            kotlinx.coroutines.delay(200L)
            if (activeContinuousSeekDirection == 0 && !showLeftSeekRipple && !showRightSeekRipple) {
                leftSeekSeconds = 0
                rightSeekSeconds = 0
            }
        }
    }

    LaunchedEffect(showRightOverlay) {
        if (showRightOverlay) {
            kotlinx.coroutines.delay(2000L)
            viewModel.setShowRightSwipeIndicator(false)
        }
    }

    LaunchedEffect(showLeftOverlay) {
        if (showLeftOverlay) {
            kotlinx.coroutines.delay(2000L)
            viewModel.setShowLeftSwipeIndicator(false)
        }
    }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isNextEpisodeOnlyVisible by remember { mutableStateOf(false) }
    var userInteractionCount by remember { mutableStateOf(0) }
    val isInPipMode by viewModel.isInPipMode.collectAsState()

    // Synchronize Android Status Bar visibility with Player Controls lifecycle
    LaunchedEffect(areControlsVisible, isInPipMode, activity) {
        if (activity != null && !isInPipMode) {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (areControlsVisible) {
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            } else {
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    val showAndResetTimer = {
        isNextEpisodeOnlyVisible = false
        areControlsVisible = true
        userInteractionCount++
    }

    val toggleControls = {
        if (isNextEpisodeOnlyVisible) {
            isNextEpisodeOnlyVisible = false
            areControlsVisible = true
        } else {
            areControlsVisible = !areControlsVisible
        }
        userInteractionCount++
    }

    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.CROP) }
    var toastMessage by remember { mutableStateOf("") }

    val setAspectRatioMode = { mode: AspectRatioMode ->
        aspectRatioMode = mode
        toastMessage = "Aspect Ratio: ${mode.displayName}"
        android.util.Log.d(
            "PlayerScreen",
            "setAspectRatioMode: mode=${mode.displayName} (resizeMode=${mode.resizeMode}, manualScaling=${mode == AspectRatioMode.CROP})"
        )
    }

    val cycleAspectRatioMode = {
        setAspectRatioMode(aspectRatioMode.next())
    }
    val speed by viewModel.playbackSpeed.collectAsState()
    val currentSpeedState by rememberUpdatedState(speed)
    val density = LocalDensity.current

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var selectedAudioTrack by remember(audioTracks) {
        mutableStateOf(audioTracks.firstOrNull() ?: "English - Stereo")
    }
    var isLoopingEnabled by remember { mutableStateOf(false) }
    var showAudioTrackSelector by remember { mutableStateOf(false) }
    var volumeBoostFactor by remember { mutableStateOf(1.0f) }
    var loudnessEnhancerRef by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }
    var showInfuseSettingsMenu by remember { mutableStateOf(false) }
    var activeSubMenu by remember { mutableStateOf("Main") }
    var settingsButtonPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var settingsButtonSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var playlistButtonPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var playlistButtonSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    var subtitleDelayMs by remember { mutableStateOf(0L) }
    val globalSubtitleFontSizeSp by viewModel.subtitleFontSize.collectAsState()
    val globalSubtitleColorStr by viewModel.subtitleColor.collectAsState()
    val globalSubtitleEncoding by viewModel.subtitleEncoding.collectAsState()
    var subtitleFontSizeSp by remember { mutableStateOf(globalSubtitleFontSizeSp) }
    LaunchedEffect(globalSubtitleFontSizeSp) {
        subtitleFontSizeSp = globalSubtitleFontSizeSp
    }
    var showSubtitleModal by remember { mutableStateOf(false) }
    var showModalEncodingDropdown by remember { mutableStateOf(false) }

    var customSubtitleUrl by remember { mutableStateOf("") }
    var rawSubtitleBytes by remember { mutableStateOf<ByteArray?>(null) }
    var customSubtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }

    // Re-decode cues in real-time whenever encoding or rawSubtitleBytes changes
    LaunchedEffect(globalSubtitleEncoding, rawSubtitleBytes) {
        val bytes = rawSubtitleBytes
        if (bytes != null && bytes.isNotEmpty()) {
            val parsed = parseSubtitleBytesWithEncoding(bytes, globalSubtitleEncoding)
            if (parsed.isNotEmpty()) {
                customSubtitleCues = parsed
            }
        }
    }

    val parsedCues = remember(movie, selectedSubtitleTrack, customSubtitleCues) {
        if (selectedSubtitleTrack == "External Subtitle") {
            customSubtitleCues
        } else {
            emptyList()
        }
    }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Single coalesced pending audio volume StateFlow
    val pendingAudioVolume = remember { MutableStateFlow(viewModel.volumeLevel.value) }

    // Flags to track active volume gesture & prevent stale broadcast overwrites
    var isVolumeGestureActive by remember { mutableStateOf(false) }
    var lastUserVolumeUpdateTime by remember { mutableLongStateOf(0L) }

    // Helper to apply volume to AudioManager, ExoPlayer and MediaPlayer without recreation
    val applyAudioVolume: (Float) -> Unit = remember(audioManager, exoPlayerRef, mediaPlayerRef, volumeBoostFactor) {
        { targetVol: Float ->
            val clamped = targetVol.coerceIn(0f, 1f)
            try {
                audioManager?.let { am ->
                    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    if (maxVolume > 0) {
                        val targetVolume = Math.round(clamped * maxVolume).toInt().coerceIn(0, maxVolume)
                        val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        if (currentVolume != targetVolume) {
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val totalVolumeGain = (clamped * volumeBoostFactor).coerceIn(0f, 2.0f)
            exoPlayerRef?.let { exo ->
                try {
                    exo.volume = if (volumeBoostFactor > 1.0f) clamped else totalVolumeGain
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            mediaPlayerRef?.let { mp ->
                try {
                    mp.setVolume(clamped, clamped)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Single coalesced consumer pipeline for audio/system volume updates (~50Hz max rate)
    LaunchedEffect(applyAudioVolume) {
        pendingAudioVolume.collect { targetVol ->
            applyAudioVolume(targetVol)
            kotlinx.coroutines.delay(20L)
        }
    }

    // Outside-of-gesture volume changes (e.g. from ViewModel / hardware keypress) feed the pipeline
    LaunchedEffect(volume) {
        if (!isVolumeGestureActive) {
            pendingAudioVolume.value = volume
        }
    }

    // Sync initial volume level from AudioManager
    LaunchedEffect(audioManager) {
        audioManager?.let { am ->
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val initialRatio = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0.40f
            viewModel.setVolume(initialRatio)
            pendingAudioVolume.value = initialRatio
        }
    }

    // Listen to hardware volume button changes and sync them (suppressing during active gestures)
    DisposableEffect(context, audioManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    // Never allow background/stale broadcast to overwrite active or recent user gesture!
                    if (isVolumeGestureActive) return
                    if (System.currentTimeMillis() - lastUserVolumeUpdateTime < 600L) return
                    audioManager?.let { am ->
                        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val currentRatio = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0.40f
                        if (Math.abs(viewModel.volumeLevel.value - currentRatio) > 0.01f) {
                            viewModel.setVolume(currentRatio)
                            pendingAudioVolume.value = currentRatio
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Separate Volume Boost effect management - ONLY reconfigures when factor/session/player changes, NEVER on volume gesture pixels
    LaunchedEffect(volumeBoostFactor, activeAudioSessionId, exoPlayerRef, mediaPlayerRef) {
        val audioSessionId = if (activeAudioSessionId != 0) activeAudioSessionId else (exoPlayerRef?.audioSessionId ?: mediaPlayerRef?.audioSessionId ?: 0)
        if (volumeBoostFactor > 1.0f) {
            val gainMb = when (volumeBoostFactor) {
                1.25f -> 1000
                1.50f -> 1200
                1.75f -> 1400
                2.0f -> 1600
                else -> 1200
            }
            com.example.util.VolumeBoostManager.enableVolumeBoost(audioSessionId, gainMb)
        } else {
            com.example.util.VolumeBoostManager.disableVolumeBoost()
        }
    }

    // Apply screen brightness changes to actual Window
    LaunchedEffect(brightness) {
        try {
            val activity = context.findActivity()
            activity?.window?.let { win ->
                val layoutParams = win.attributes
                layoutParams.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
                win.attributes = layoutParams
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(speed, mediaPlayerRef, exoPlayerRef) {
        val mp = mediaPlayerRef
        if (mp != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    params.speed = speed
                    params.pitch = 1.0f
                    mp.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val exo = exoPlayerRef
        if (exo != null) {
            try {
                exo.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage.isNotEmpty()) {
            kotlinx.coroutines.delay(1200)
            toastMessage = ""
        }
    }

    // Auto-hide controls timer (4 seconds)
    LaunchedEffect(areControlsVisible, userInteractionCount, isPlaying, showInfuseSettingsMenu, showEpisodeDrawer) {
        if (areControlsVisible && isPlaying && !showInfuseSettingsMenu && !showEpisodeDrawer) {
            kotlinx.coroutines.delay(4000L)
            areControlsVisible = false
        }
    }

    val actualDurationMs = remember(durationStateMs) {
        if (durationStateMs > 0L) durationStateMs else null
    }

    val effectiveDisplayProgress = remember(progress, isUserDragging, isScrubbing, sliderProgress, scrubTargetProgress) {
        if (isUserDragging || isScrubbing) {
            sliderProgress
        } else {
            progress
        }
    }

    val elapsedTime = remember(effectiveDisplayProgress, actualDurationMs) {
        val totalSecs = (actualDurationMs?.div(1000)) ?: 11520L
        val elapsedSecs = (effectiveDisplayProgress * totalSecs).toLong()
        formatTime(elapsedSecs)
    }

    val totalDuration = remember(actualDurationMs) {
        if (actualDurationMs != null && actualDurationMs > 0L) {
            formatTime(actualDurationMs / 1000)
        } else {
            "3:12:00"
        }
    }

    val remainingTimeStr = remember(effectiveDisplayProgress, actualDurationMs) {
        val totalSecs = (actualDurationMs?.div(1000)) ?: 11520L
        val elapsedSecs = (effectiveDisplayProgress * totalSecs).toLong()
        val remSecs = (totalSecs - elapsedSecs).coerceAtLeast(0)
        "-${formatTime(remSecs)}"
    }

    val playerMetadata = remember(currentEpisode, isMovie, movie) {
        val s = currentEpisode.seasonNumber
        val e = currentEpisode.episodeNumber
        val rawEpTitle = currentEpisode.episodeTitle

        if (!isMovie) {
            var seriesName = currentEpisode.showTitle
            if (seriesName.isBlank()) {
                seriesName = com.example.network.GeminiService.cleanShowTitle(currentMovie.title)
                if (seriesName.isBlank()) seriesName = "TV Show"
            }

            var cleanEpName = com.example.util.EpisodeUtils.cleanEpisodeTitle(rawEpTitle)
            if (cleanEpName.isBlank()) {
                cleanEpName = com.example.util.EpisodeUtils.cleanEpisodeTitle(currentMovie.title)
            }
            if (seriesName.isNotBlank() && cleanEpName.lowercase().startsWith(seriesName.lowercase())) {
                cleanEpName = cleanEpName.substring(seriesName.length).trim()
            }
            cleanEpName = cleanEpName.removePrefix("-").removePrefix("•").removePrefix(":").removePrefix("–").trim()
            if (cleanEpName.isBlank() || cleanEpName.equals(seriesName, ignoreCase = true) || cleanEpName.matches(Regex("(?i)s\\d+\\s*[•\\-]?\\s*e\\d+"))) {
                cleanEpName = "Episode $e"
            }

            val lowerTag = if (seriesName.isNotBlank()) "$seriesName • S$s • E$e" else "S$s • E$e"
            Pair(cleanEpName, lowerTag)
        } else {
            // For movies: display strictly ONLY the Movie Title. No secondary metadata tags.
            Pair(movie.title, "")
        }
    }

    val (displayEpisodeName, showSeasonEpisodeSubtitle) = playerMetadata

    // Centralized ExoPlayer Seek Function ensuring all seeks pass through one verified path
    val performExoSeek = { player: androidx.media3.exoplayer.ExoPlayer, requestedPositionMs: Long ->
        val duration = player.duration
        val target = if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
            requestedPositionMs.coerceIn(0L, duration)
        } else {
            requestedPositionMs.coerceAtLeast(0L)
        }

        val rawUri = player.currentMediaItem?.localConfiguration?.uri
        val uriStr = rawUri?.toString() ?: currentEpisode.mediaUrl
        val scheme = rawUri?.scheme ?: runCatching { Uri.parse(uriStr).scheme }.getOrNull() ?: "unknown"
        val isSeekable = player.isCurrentMediaItemSeekable
        val posBefore = player.currentPosition
        val bufferedPos = player.bufferedPosition
        val state = player.playbackState

        val activeFormat = exoPlayerRef?.videoFormat
        val mimeType = activeFormat?.sampleMimeType ?: activeFormat?.containerMimeType ?: "unknown"
        val codecs = activeFormat?.codecs ?: "unknown"
        val bitDepth = activeFormat?.colorInfo?.lumaBitdepth?.let { if (it > 0) "$it-bit" else null }
            ?: if (codecs.contains("10", ignoreCase = true) || codecs.contains("main10", ignoreCase = true)) "10-bit" else "8/10-bit"

        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "==================== [SEEK_START] ====================")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "uri=$uriStr")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "scheme=$scheme")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "mimeType=$mimeType, codecs=$codecs, bitDepth=$bitDepth")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "mediaKey=${currentEpisode.id}#$uriStr#$customSubtitleUrl")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "currentEpisodeId=${currentEpisode.id}")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "currentPosition=$posBefore")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "duration=$duration")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "bufferedPosition=$bufferedPos")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "isSeekable=$isSeekable")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "playbackState=$state")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "targetPosition=$target (requested=$requestedPositionMs)")

        isSeekingActive = true
        isSeekInProgress = true
        pendingSeekPositionMs = target
        lastSeekTargetMs = target
        positionStateMs = target

        if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
            viewModel.setPlaybackProgress(
                (target.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            )
        }

        try {
            player.seekTo(target)
        } catch (e: Exception) {
            SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "seekTo exception: ${e.message}")
        }

        val posAfter = player.currentPosition
        val buffAfter = player.bufferedPosition
        val stateAfter = player.playbackState
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "==================== [SEEK_AFTER_CALL] ====================")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "currentPosition=$posAfter")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "bufferedPosition=$buffAfter")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "playbackState=$stateAfter")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "targetPosition=$target")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "mediaKey=${currentEpisode.id}#$uriStr#$customSubtitleUrl")
        SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "currentEpisodeId=${currentEpisode.id}")
    }

    val performRelativeSeek: (androidx.media3.exoplayer.ExoPlayer, Long) -> Unit =
        { player, deltaMs ->

            val duration = player.duration

            val basePosition =
                when {
                    pendingSeekPositionMs != androidx.media3.common.C.TIME_UNSET ->
                        pendingSeekPositionMs

                    (isSeekingActive || isSeekInProgress) && lastSeekTargetMs != null ->
                        lastSeekTargetMs!!

                    player.currentPosition >= 0L ->
                        player.currentPosition

                    else ->
                        positionStateMs
                }

            val target =
                if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
                    (basePosition + deltaMs).coerceIn(0L, duration)
                } else {
                    (basePosition + deltaMs).coerceAtLeast(0L)
                }

            performExoSeek(player, target)
        }

    val safeSeekTo = { positionMs: Long ->
        val player = exoPlayerRef
        if (player != null) {
            performExoSeek(player, positionMs)
        }
    }

    // Unified YouTube-style continuous double-tap seek step handler (-1 = Left / Rewind, +1 = Right / Forward)
    val performContinuousSeekStep: (Int) -> Unit = { zoneDirection ->
        val stepSeconds = 10
        val isContinuousAllowed = currentContinuousSeekEnabled
        val deltaMs = zoneDirection * stepSeconds * 1000L
        val now = System.currentTimeMillis()

        val isSameDirectionContinuous = (activeContinuousSeekDirection == zoneDirection) &&
            (now - lastSeekTapTimeMs < 1000L)

        if (zoneDirection < 0) {
            // Seeking backward (Left)
            if (isSameDirectionContinuous && leftSeekSeconds > 0) {
                leftSeekSeconds += stepSeconds
            } else {
                // Initial double-tap on Left, or switching direction from Right to Left, or after >= 1s timeout
                showRightSeekRipple = false
                rightSeekSeconds = 0
                continuousSeekTargetPositionMs = androidx.media3.common.C.TIME_UNSET
                leftSeekSeconds = stepSeconds
            }
            activeContinuousSeekDirection = if (isContinuousAllowed) -1 else 0
            showLeftSeekRipple = true
            lastLeftTapTime = now
            lastSeekTapTimeMs = now
            leftSeekTriggerKey++
        } else {
            // Seeking forward (Right)
            if (isSameDirectionContinuous && rightSeekSeconds > 0) {
                rightSeekSeconds += stepSeconds
            } else {
                // Initial double-tap on Right, or switching direction from Left to Right, or after >= 1s timeout
                showLeftSeekRipple = false
                leftSeekSeconds = 0
                continuousSeekTargetPositionMs = androidx.media3.common.C.TIME_UNSET
                rightSeekSeconds = stepSeconds
            }
            activeContinuousSeekDirection = if (isContinuousAllowed) 1 else 0
            showRightSeekRipple = true
            lastRightTapTime = now
            lastSeekTapTimeMs = now
            rightSeekTriggerKey++
        }

        // Restart the 1-second seek inactivity timeout
        continuousSeekActivityKey++

        // Keep controls auto-hide timer alive if controls are currently shown, without toggling visibility
        if (areControlsVisible) {
            userInteractionCount++
        }

        val view = exoPlayerRef
        if (view != null) {
            val rawDur = view.duration
            val validDuration = when {
                rawDur > 0L && rawDur != androidx.media3.common.C.TIME_UNSET -> rawDur
                durationStateMs > 0L -> durationStateMs
                else -> androidx.media3.common.C.TIME_UNSET
            }

            val basePosition = when {
                continuousSeekTargetPositionMs != androidx.media3.common.C.TIME_UNSET ->
                    continuousSeekTargetPositionMs
                pendingSeekPositionMs != androidx.media3.common.C.TIME_UNSET ->
                    pendingSeekPositionMs
                (isSeekingActive || isSeekInProgress) && lastSeekTargetMs != null ->
                    lastSeekTargetMs!!
                view.currentPosition >= 0L ->
                    view.currentPosition
                else ->
                    positionStateMs.coerceAtLeast(0L)
            }

            val targetPosition = if (validDuration > 0L && validDuration != androidx.media3.common.C.TIME_UNSET) {
                (basePosition + deltaMs).coerceIn(0L, validDuration)
            } else {
                (basePosition + deltaMs).coerceAtLeast(0L)
            }

            continuousSeekTargetPositionMs = targetPosition
            performExoSeek(view, targetPosition)
        } else {
            val mp = mediaPlayerRef
            if (mp != null && mp.duration > 0) {
                val dur = mp.duration.toLong()
                val basePos = if (continuousSeekTargetPositionMs != androidx.media3.common.C.TIME_UNSET) {
                    continuousSeekTargetPositionMs
                } else {
                    mp.currentPosition.toLong().coerceAtLeast(0L)
                }
                val targetPos = (basePos + deltaMs).coerceIn(0L, dur)
                continuousSeekTargetPositionMs = targetPos
                viewModel.setPlaybackProgress((targetPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                mp.seekTo(targetPos.toInt())
            } else {
                val totalSecs = 11520f
                val deltaProgress = (zoneDirection * stepSeconds) / totalSecs
                val currentProgress = viewModel.playbackProgress.value
                viewModel.setPlaybackProgress((currentProgress + deltaProgress).coerceIn(0f, 1f))
            }
        }
    }

    val currentPerformContinuousSeekStep by rememberUpdatedState(performContinuousSeekStep)
    val currentToggleControls by rememberUpdatedState(toggleControls)
    val currentExitContinuousSeekMode by rememberUpdatedState(exitContinuousSeekMode)

    // Background poller to keep seek bar, times, and playing state fully synchronized
    LaunchedEffect(isPlaying, isScrubbing, isUserDragging, isHorizontalScrubbing, isSeekingActive, isSeekInProgress, exoPlayerRef) {
        while (true) {
            val view = exoPlayerRef
            if (view != null) {
                val actualIsPlaying = try { view.playWhenReady && view.playbackState != androidx.media3.common.Player.STATE_ENDED } catch (e: Exception) { false }
                if (actualIsPlaying != viewModel.isPlaying.value) {
                    viewModel.setPlaying(actualIsPlaying)
                }
                val duration = try { view.duration } catch (e: Exception) { 0L }
                if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
                    durationStateMs = duration
                    val pendingTarget = if (pendingSeekPositionMs != androidx.media3.common.C.TIME_UNSET) pendingSeekPositionMs else lastSeekTargetMs

                    if ((isSeekingActive || isSeekInProgress) && pendingTarget != null) {
                        positionStateMs = pendingTarget
                        viewModel.setPlaybackProgress(
                            (pendingTarget.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        )
                    } else if (!isScrubbing && !isUserDragging && !isHorizontalScrubbing) {
                        if (view.playbackState == androidx.media3.common.Player.STATE_READY || view.isPlaying) {
                            val currentPos = try { view.currentPosition } catch (e: Exception) { 0L }
                            // When not seeking active, update current position
                            positionStateMs = currentPos
                            val calculatedProgress = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            viewModel.setPlaybackProgress(calculatedProgress)
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }

    val hudBackdropGraphicsLayer = rememberGraphicsLayer()
    val hudLoadingBackdropGraphicsLayer = rememberGraphicsLayer()
    var hudBackdropPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var hudBackdropSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val hudBackdropDrawTick = remember { mutableLongStateOf(0L) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                hudBackdropPositionInRoot = coords.positionInRoot()
                hudBackdropSize = coords.size
            }
            .background(Color.Black)
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Resolved video URI for local Uri, asset, or web stream
        val videoUri = remember(currentEpisode.id, currentEpisode.mediaUrl, currentMovie.filePath) {
            val candidate = listOf(
                currentEpisode.mediaUrl,
                currentEpisode.id,
                currentMovie.filePath,
                currentMovie.id
            ).firstOrNull { it.isNotBlank() } ?: ""

            when {
                candidate.startsWith("content://") || candidate.startsWith("http://") || candidate.startsWith("https://") -> {
                    runCatching { Uri.parse(candidate) }.getOrDefault(Uri.EMPTY)
                }
                candidate.startsWith("file://") -> {
                    runCatching { Uri.parse(candidate) }.getOrDefault(Uri.EMPTY)
                }
                candidate.startsWith("/") -> {
                    runCatching { Uri.fromFile(java.io.File(candidate)) }.getOrDefault(Uri.EMPTY)
                }
                candidate.startsWith("asset:///") -> {
                    val assetName = candidate.substring("asset:///".length)
                    val cacheFile = java.io.File(context.cacheDir, assetName)
                    if (!cacheFile.exists()) {
                        try {
                            context.assets.open(assetName).use { input ->
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    Uri.fromFile(cacheFile)
                }
                candidate.isNotBlank() -> {
                    runCatching {
                        val parsed = Uri.parse(candidate)
                        if (parsed.scheme == null) Uri.fromFile(java.io.File(candidate)) else parsed
                    }.getOrDefault(Uri.EMPTY)
                }
                else -> Uri.EMPTY
            }
        }

        val framePreviewExtractor = remember(videoUri) {
            com.example.util.VideoFramePreviewExtractor(context.applicationContext, videoUri)
        }
        DisposableEffect(framePreviewExtractor) {
            onDispose {
                framePreviewExtractor.release()
            }
        }
        var seekPreviewBitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
        val seekPreviewImageBitmap = remember(seekPreviewBitmap) {
            if (seekPreviewBitmap != null && !seekPreviewBitmap!!.isRecycled) {
                runCatching { seekPreviewBitmap!!.asImageBitmap() }.getOrNull()
            } else {
                null
            }
        }
        var hudVideoFrameBitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
        val hudBackdropImageState = remember(videoUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        val hudPoolBitmaps = remember(videoUri) { arrayOfNulls<android.graphics.Bitmap>(2) }
        val hudPoolImages = remember(videoUri) { arrayOfNulls<androidx.compose.ui.graphics.ImageBitmap>(2) }
        var hudPoolIndex by remember(videoUri) { mutableIntStateOf(0) }
        var staticPanelBlurBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var staticPanelBlurImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        var dragStartPositionMs by remember(videoUri) { mutableLongStateOf(0L) }

        val localDensityForPrewarm = LocalDensity.current
        LaunchedEffect(localDensityForPrewarm) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                with(localDensityForPrewarm) {
                    com.example.ui.components.LiquidGlassDockEffects.prewarmHudPillEffects(
                        blurRadiusPx = 5.5.dp.toPx(),
                        defaultWidthPx = 108.dp.toPx(),
                        heightPx = 38.dp.toPx(),
                        cornerRadiusPx = 19.dp.toPx()
                    )
                }
            }
        }

        DisposableEffect(videoUri) {
            onDispose {
                hudBackdropImageState.value = null
                hudVideoFrameBitmap = null
                for (i in hudPoolBitmaps.indices) {
                    hudPoolImages[i] = null
                    hudPoolBitmaps[i]?.let { bmp ->
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                    hudPoolBitmaps[i] = null
                }
            }
        }

        // Capture static background frame ONCE when Settings or Playlist panel opens
        val isPanelOpen = showInfuseSettingsMenu || showEpisodeDrawer
        LaunchedEffect(isPanelOpen) {
            if (isPanelOpen) {
                val sv = videoSurfaceViewRef
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                var capturedBmp: android.graphics.Bitmap? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    sv != null &&
                    sv.holder.surface.isValid &&
                    sv.width > 0 &&
                    sv.height > 0
                ) {
                    val targetWidth = (sv.width / 4).coerceAtLeast(160)
                    val targetHeight = (sv.height / 4).coerceAtLeast(90)
                    val bmp = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888)
                    val latch = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    try {
                        android.view.PixelCopy.request(
                            sv,
                            bmp,
                            { copyResult ->
                                latch.complete(copyResult == android.view.PixelCopy.SUCCESS)
                            },
                            handler
                        )
                        if (latch.await()) {
                            capturedBmp = bmp
                        }
                    } catch (_: Throwable) {}
                }

                if (capturedBmp == null) {
                    val posMs = exoPlayerRef?.currentPosition?.coerceAtLeast(0L)
                        ?: (sliderProgress.coerceIn(0f, 1f) * durationStateMs.coerceAtLeast(1L)).toLong()
                    capturedBmp = framePreviewExtractor.getFrameAtPosition(
                        positionMs = posMs,
                        targetWidthPx = 240,
                        targetHeightPx = 135,
                        precise = false
                    ) ?: hudVideoFrameBitmap ?: seekPreviewBitmap
                }

                if (capturedBmp != null && !capturedBmp.isRecycled) {
                    staticPanelBlurBitmap = capturedBmp
                    staticPanelBlurImageBitmap = runCatching { capturedBmp.asImageBitmap() }.getOrNull()
                }
            } else {
                staticPanelBlurBitmap?.let { bmp ->
                    val isPooledOrShared = (bmp === hudPoolBitmaps[0]) || (bmp === hudPoolBitmaps[1]) || (bmp === seekPreviewBitmap)
                    if (!isPooledOrShared && !bmp.isRecycled) bmp.recycle()
                }
                staticPanelBlurBitmap = null
                staticPanelBlurImageBitmap = null
            }
        }

        // Extract real-time frame thumbnails ONLY when the user actively drags the seek thumb
        LaunchedEffect(isUserDragging, sliderProgress, videoUri) {
            if (!isUserDragging) return@LaunchedEffect
            val exo = exoPlayerRef
            val dur = when {
                exo != null && exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET -> exo.duration
                durationStateMs > 0L -> durationStateMs
                actualDurationMs != null && actualDurationMs!! > 0L -> actualDurationMs!!
                else -> 11520000L
            }
            val targetMs = (sliderProgress.coerceIn(0f, 1f) * dur).toLong().coerceIn(0L, dur)
            val fastFrame = framePreviewExtractor.getFrameAtPosition(
                positionMs = targetMs,
                targetWidthPx = 320,
                targetHeightPx = 180,
                precise = false
            )
            if (fastFrame != null) {
                seekPreviewBitmap = fastFrame
            }
            kotlinx.coroutines.delay(110L)
            val preciseFrame = framePreviewExtractor.getFrameAtPosition(
                positionMs = targetMs,
                targetWidthPx = 320,
                targetHeightPx = 180,
                precise = true
            )
            if (preciseFrame != null) {
                seekPreviewBitmap = preciseFrame
            }
        }

        // Capture pooled downscaled live video frames (160x90 ping-pong) for HUD Liquid Glass blur.
        // Skips captures during popup enter/exit animations and stops polling while video is paused.
        LaunchedEffect(
            hudOverlayVisible,
            isHolding2x,
            isVideoReady,
            isPlaying,
            videoUri
        ) {
            val isAnyHudPillOpen = hudOverlayVisible || isHolding2x
            val needsInitialFrame = isVideoReady && hudBackdropImageState.value == null
            if (!isAnyHudPillOpen && !needsInitialFrame) return@LaunchedEffect

            // Let popup entrance animation run at full 60 FPS before refreshing backdrop if a frame already exists
            if (isAnyHudPillOpen && hudBackdropImageState.value != null) {
                kotlinx.coroutines.delay(185L)
            }

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
                val sv = videoSurfaceViewRef
                var captured = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    sv != null &&
                    sv.holder.surface.isValid &&
                    sv.width > 0 &&
                    sv.height > 0
                ) {
                    val slot = hudPoolIndex and 1
                    var bmp = hudPoolBitmaps[slot]
                    var imgBmp = hudPoolImages[slot]
                    if (bmp == null || bmp.isRecycled) {
                        bmp = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
                        imgBmp = runCatching { bmp.asImageBitmap() }.getOrNull()
                        hudPoolBitmaps[slot] = bmp
                        hudPoolImages[slot] = imgBmp
                    }
                    val latch = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    try {
                        android.view.PixelCopy.request(
                            sv,
                            bmp,
                            { copyResult ->
                                latch.complete(copyResult == android.view.PixelCopy.SUCCESS)
                            },
                            handler
                        )
                        if (latch.await()) {
                            runCatching { bmp.prepareToDraw() }
                            hudPoolIndex = (slot + 1) and 1
                            hudVideoFrameBitmap = bmp
                            hudBackdropImageState.value = imgBmp ?: runCatching { bmp.asImageBitmap() }.getOrNull()
                            captured = true
                        }
                    } catch (_: Throwable) {
                        // Fall back to cached frame if PixelCopy is unavailable
                    }
                }
                if (!captured) {
                    if (hudBackdropImageState.value == null && seekPreviewBitmap == null) {
                        val posMs = exoPlayerRef?.currentPosition?.coerceAtLeast(0L)
                            ?: (sliderProgress.coerceIn(0f, 1f) * durationStateMs.coerceAtLeast(1L)).toLong()
                        val fallbackBmp = framePreviewExtractor.getFrameAtPosition(
                            positionMs = posMs,
                            targetWidthPx = 160,
                            targetHeightPx = 90,
                            precise = false
                        )
                        if (fallbackBmp != null) {
                            runCatching { fallbackBmp.prepareToDraw() }
                            hudVideoFrameBitmap = fallbackBmp
                            hudBackdropImageState.value = runCatching { fallbackBmp.asImageBitmap() }.getOrNull()
                        }
                    }
                    // Never poll MediaMetadataRetriever repeatedly on CPU if PixelCopy is unavailable
                    break
                }
                if (!isAnyHudPillOpen || !isPlaying) {
                    break
                }
                kotlinx.coroutines.delay(200L)
            }
        }

        val mediaKey = remember(videoUri, customSubtitleUrl) {
            "${videoUri}#${customSubtitleUrl}"
        }

        LaunchedEffect(exoPlayerRef, mediaKey, isSurfaceReady) {
            val player = exoPlayerRef ?: return@LaunchedEffect
            if (!isSurfaceReady) return@LaunchedEffect
            if (videoUri == Uri.EMPTY) return@LaunchedEffect
            val stableMediaKey = mediaKey

            val alreadyLoadedIntoThisPlayer =
                loadedPlayerInstance === player &&
                loadedMediaKey == stableMediaKey

            if (alreadyLoadedIntoThisPlayer) {
                videoSurfaceViewRef?.let { sv ->
                    if (sv.holder.surface?.isValid == true) {
                        player.setVideoSurfaceView(sv)
                    }
                }
                return@LaunchedEffect
            }

            var waitAttempts = 0
            while (waitAttempts < 3) {
                val sv = videoSurfaceViewRef
                if (sv != null && sv.holder.surface?.isValid == true) {
                    break
                }
                kotlinx.coroutines.delay(8L)
                waitAttempts++
            }

            val activeSurfaceView = videoSurfaceViewRef
            if (activeSurfaceView == null || activeSurfaceView.holder.surface?.isValid != true) {
                return@LaunchedEffect
            }

            loadedPlayerInstance = player
            loadedMediaKey = stableMediaKey
            resumeAppliedForMediaKey = stableMediaKey
            isApplyingResume = false
            currentlyLoadedMediaKey = stableMediaKey
            val key = currentEpisode.id.ifBlank { currentEpisode.mediaUrl }
            com.example.util.MediaCodec10BitHelper.currentStreamKey = key

            SeekDiagnosticsLogger.log(
                "LOCAL_SEEK_ROOT_CAUSE",
                "[EVENT_MEDIA_LOAD_START] mediaKey=$stableMediaKey uri=$videoUri currentEpisodeId=${currentEpisode.id}"
            )
            try {
                isVideoReady = false
                SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[EVENT_EXO_STOP_CLEAR] Stopping player before reload")
                player.stop()
                player.clearMediaItems()
                player.setVideoSurfaceView(activeSurfaceView)
                
                val initialMetadata = com.example.util.MediaSessionManager.buildInitialMetadata(
                    movie = currentMovie,
                    episode = currentEpisode,
                    isMovie = isMovie
                )
                val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
                    .setUri(videoUri)
                    .setMediaId(currentEpisode.id)
                    .setMediaMetadata(initialMetadata)
                if (customSubtitleUrl.isNotBlank()) {
                    val subUri = Uri.parse(customSubtitleUrl)
                    val mimeType = if (customSubtitleUrl.endsWith(".vtt", true)) {
                        androidx.media3.common.MimeTypes.TEXT_VTT
                    } else {
                        androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                    }
                    val subtitleConfig = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(mimeType)
                        .setLanguage("en")
                        .setLabel("External Subtitle")
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .setRoleFlags(androidx.media3.common.C.ROLE_FLAG_MAIN)
                        .build()
                    mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
                }
                val mediaItem = mediaItemBuilder.build()

                val savedPosition =
                    viewModel.getSavedPlaybackPosition(currentEpisode.id).takeIf { it > 0L }
                        ?: (if (currentEpisode.id == movieId && startPositionMs > 0L) startPositionMs else 0L)

                if (savedPosition > 0L) {
                    positionStateMs = savedPosition
                    lastSeekTargetMs = savedPosition
                } else {
                    positionStateMs = 0L
                    lastSeekTargetMs = null
                    sliderProgress = 0f
                    viewModel.setPlaybackProgress(0f)
                }

                SeekDiagnosticsLogger.log(
                    "LOCAL_SEEK_ROOT_CAUSE",
                    "==================== [RESUME_FLOW_START] ===================="
                )
                SeekDiagnosticsLogger.log(
                    "LOCAL_SEEK_ROOT_CAUSE",
                    "[RESUME_START] currentEpisodeId=${currentEpisode.id}, movieId=$movieId, restoredSavedPos=${savedPosition}ms, startPositionMs=${startPositionMs}ms"
                )
                SeekDiagnosticsLogger.log(
                    "LOCAL_SEEK_ROOT_CAUSE",
                    "[MEDIA_ITEM_SET] uri=$videoUri, scheme=${videoUri.scheme}, mediaKey=$mediaKey, seekPos=${savedPosition}ms"
                )

                if (savedPosition > 0L) {
                    player.setMediaItem(mediaItem, savedPosition)
                } else {
                    player.setMediaItem(mediaItem)
                }
                SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[PREPARE] Calling player.prepare(), seekPos=${savedPosition}ms")
                player.prepare()
                player.playWhenReady = true
                player.play()
                viewModel.setPlaying(true)
                com.example.util.MediaSessionManager.updateMetadata(
                    context = context,
                    movie = currentMovie,
                    episode = currentEpisode,
                    isMovie = isMovie,
                    nextEpisodeAvailable = !isMovie && nextEpisodeInQueue != null,
                    prevEpisodeAvailable = !isMovie && prevEpisodeInQueue != null,
                    onNextEpisode = if (!isMovie && nextEpisodeInQueue != null) {
                        {
                            exoPlayerRef?.let { latestSavePlaybackPosition(it) }
                            currentPlayingEpId = nextEpisodeInQueue.id
                            viewModel.selectEpisode(nextEpisodeInQueue.id)
                            viewModel.setPlaying(true)
                        }
                    } else null,
                    onPrevEpisode = if (!isMovie && prevEpisodeInQueue != null) {
                        {
                            exoPlayerRef?.let { latestSavePlaybackPosition(it) }
                            currentPlayingEpId = prevEpisodeInQueue.id
                            viewModel.selectEpisode(prevEpisodeInQueue.id)
                            viewModel.setPlaying(true)
                        }
                    } else null,
                    videoUri = videoUri
                )
                SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[RESUME_PLAY] player.playWhenReady=true, player.play() invoked")
            } catch (e: Exception) {
                SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[EVENT_MEDIA_LOAD_EXCEPTION] ${e.message}")
            }
        }

        // 10-Bit Hardware Direct Video Surface Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val exo = exoPlayerRef
            val videoSize = if (activeVideoSize.width > 0) activeVideoSize else (exo?.videoSize ?: androidx.media3.common.VideoSize.UNKNOWN)
            val formatWidth = activeVideoFormat?.width ?: 0
            val formatHeight = activeVideoFormat?.height ?: 0
            val videoWidth = if (videoSize.width > 0) videoSize.width else if (formatWidth > 0) formatWidth else 1920
            val videoHeight = if (videoSize.height > 0) videoSize.height else if (formatHeight > 0) formatHeight else 1080

            val (surfaceModifier, currentScale) = if (videoWidth > 0 && videoHeight > 0 && containerWidth.value > 0 && containerHeight.value > 0) {
                val containerRatio = containerWidth.value / containerHeight.value
                val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                when (aspectRatioMode) {
                    AspectRatioMode.STRETCH -> {
                        // STRETCH (Fill): Distorts/stretches video to touch all container edges (1.0f scale, fillMaxSize)
                        Pair(Modifier.fillMaxSize(), 1.0f)
                    }
                    AspectRatioMode.CROP -> {
                        // CROP (Zoom): Programmatic uniform scale calculation.
                        // Base dimensions match natural aspect ratio, and dynamic scale factor expands video past container edges without distortion.
                        val baseModifier = if (videoRatio > containerRatio) {
                            val baseHeight = containerWidth / videoRatio
                            Modifier.size(width = containerWidth, height = baseHeight)
                        } else {
                            val baseWidth = containerHeight * videoRatio
                            Modifier.size(width = baseWidth, height = containerHeight)
                        }
                        val cropScaleFactor = if (videoRatio > containerRatio) {
                            (containerHeight.value * videoRatio) / containerWidth.value
                        } else {
                            containerWidth.value / (containerHeight.value * videoRatio)
                        }.coerceAtLeast(1.0f)
                        Pair(baseModifier, cropScaleFactor)
                    }
                    AspectRatioMode.FIT -> {
                        // FIT: Proportionally fits entire video within container with letterbox / pillarbox (1.0f scale)
                        val fitModifier = if (videoRatio > containerRatio) {
                            val scaledHeight = containerWidth / videoRatio
                            Modifier.size(width = containerWidth, height = scaledHeight)
                        } else {
                            val scaledWidth = containerHeight * videoRatio
                            Modifier.size(width = scaledWidth, height = containerHeight)
                        }
                        Pair(fitModifier, 1.0f)
                    }
                }
            } else {
                Pair(Modifier.fillMaxSize(), 1.0f)
            }

            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        holder.setKeepScreenOn(true)
                        videoSurfaceViewRef = this
                        if (holder.surface?.isValid == true) {
                            isSurfaceReady = true
                            exoPlayerRef?.setVideoSurfaceView(this)
                        }
                        holder.addCallback(object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(surfaceHolder: android.view.SurfaceHolder) {
                                videoSurfaceViewRef = this@apply
                                val valid = surfaceHolder.surface?.isValid == true
                                isSurfaceReady = valid
                                if (valid) {
                                    try {
                                        exoPlayerRef?.setVideoSurfaceView(this@apply)
                                    } catch (_: Exception) {}
                                }
                            }

                            override fun surfaceChanged(
                                surfaceHolder: android.view.SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                videoSurfaceViewRef = this@apply
                                val valid = surfaceHolder.surface?.isValid == true && width > 0 && height > 0
                                isSurfaceReady = valid
                                if (valid) {
                                    try {
                                        exoPlayerRef?.setVideoSurfaceView(this@apply)
                                    } catch (_: Exception) {}
                                }
                            }

                            override fun surfaceDestroyed(surfaceHolder: android.view.SurfaceHolder) {
                                isSurfaceReady = false
                            }
                        })
                    }
                },
                update = { surfaceView ->
                    surfaceView.scaleX = currentScale
                    surfaceView.scaleY = currentScale
                    if (videoSurfaceViewRef !== surfaceView) {
                        videoSurfaceViewRef = surfaceView
                    }
                    val valid = surfaceView.holder.surface?.isValid == true
                    if (valid != isSurfaceReady) {
                        isSurfaceReady = valid
                    }
                    if (exo != null && valid) {
                        try {
                            exo.setVideoSurfaceView(surfaceView)
                            if (exo.playWhenReady != isPlaying) {
                                exo.playWhenReady = isPlaying
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                modifier = surfaceModifier
            )
        }

        // Elegant Infuse-Style Loading State Backdrop (smoothly visible until first video frame renders)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isVideoReady,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .drawWithContent {
                        hudLoadingBackdropGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(hudLoadingBackdropGraphicsLayer)
                    },
                contentAlignment = Alignment.Center
            ) {
                val backdropImage = currentEpisode.posterUrl.ifBlank { currentMovie.backdropUrl.ifBlank { currentMovie.posterUrl } }
                if (backdropImage.isNotBlank()) {
                    AsyncImage(
                        model = backdropImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.30f)
                            .blur(radius = 28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.9f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    val loadingTitle = if (!isMovie && currentEpisode.episodeTitle.isNotBlank()) {
                        currentEpisode.episodeTitle
                    } else {
                        currentMovie.title
                    }
                    if (loadingTitle.isNotBlank()) {
                        Text(
                            text = loadingTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Dark overlay with gestures split into Left (38.5%), Center Native (23%), and Right (38.5%) zones
        if (!isInPipMode) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
            // Left Zone (~38.5%): Brightness drag & YouTube-style Continuous Double-Tap seek backward (-10s, -20s...) & Long press 2x & Single tap controls toggle
            Box(
                modifier = Modifier
                    .weight(0.385f)
                    .fillMaxHeight()
                    .pointerInput(mediaPlayerRef) {
                        val zoneDirection = -1
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            var isFirstLongPress = false
                            val firstDefaultSpeed = currentSpeedState
                            var firstUp: androidx.compose.ui.input.pointer.PointerInputChange? = null

                            val firstLongPressJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(300L)
                                if (!isHorizontalScrubbing && !isDraggingHUD && !isVolumeGestureActive) {
                                    isFirstLongPress = true
                                    isHolding2x = true
                                    viewModel.setPlaybackSpeed(2.0f)
                                }
                            }
                            try {
                                firstUp = waitForUpOrCancellation()
                            } finally {
                                firstLongPressJob.cancel()
                                if (isFirstLongPress || isHolding2x) {
                                    isHolding2x = false
                                    viewModel.setPlaybackSpeed(firstDefaultSpeed)
                                    lastLongPressReleaseTime = System.currentTimeMillis()
                                }
                            }

                            if (firstUp == null || isFirstLongPress || System.currentTimeMillis() - lastLongPressReleaseTime < 500L) {
                                return@awaitEachGesture
                            }
                            firstUp?.consume()

                            val now = System.currentTimeMillis()
                            val isContinuousActiveInSameDirection = currentContinuousSeekEnabled &&
                                (activeContinuousSeekDirection == zoneDirection) &&
                                (now - lastSeekTapTimeMs < 1000L)

                            if (isContinuousActiveInSameDirection) {
                                // Continuous seeking in the same direction: single tap seeks another 10s immediately!
                                currentPerformContinuousSeekStep(zoneDirection)
                                return@awaitEachGesture
                            }

                            // Otherwise, continuous seek is not active in this direction (idle, expired >= 1s, or opposite direction active).
                            // A double-tap in THIS direction is strictly required.
                            val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis.coerceIn(250L, 350L)
                            val secondDown = withTimeoutOrNull(doubleTapTimeoutMs) {
                                awaitFirstDown(requireUnconsumed = false)
                            }

                            if (secondDown == null) {
                                // Single tap only.
                                // Direction change protection: if opposite direction was active or gap >= 1s,
                                // a single tap must NEVER trigger an accidental seek.
                                if (activeContinuousSeekDirection == 0 && System.currentTimeMillis() - lastLongPressReleaseTime >= 500L) {
                                    currentToggleControls()
                                }
                                return@awaitEachGesture
                            }

                            var isSecondLongPress = false
                            val secondDefaultSpeed = currentSpeedState
                            var secondUp: androidx.compose.ui.input.pointer.PointerInputChange? = null

                            val secondLongPressJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(300L)
                                if (!isHorizontalScrubbing && !isDraggingHUD && !isVolumeGestureActive) {
                                    isSecondLongPress = true
                                    isHolding2x = true
                                    viewModel.setPlaybackSpeed(2.0f)
                                }
                            }
                            try {
                                secondUp = waitForUpOrCancellation()
                            } finally {
                                secondLongPressJob.cancel()
                                if (isSecondLongPress || isHolding2x) {
                                    isHolding2x = false
                                    viewModel.setPlaybackSpeed(secondDefaultSpeed)
                                    lastLongPressReleaseTime = System.currentTimeMillis()
                                }
                            }

                            if (secondUp != null && !isSecondLongPress && System.currentTimeMillis() - lastLongPressReleaseTime >= 500L) {
                                secondUp?.consume()
                                currentPerformContinuousSeekStep(zoneDirection)
                            }
                        }
                    }
                    .pointerInput(isHolding2x) {
                        var dragLockedDirection = 0 // 0: uncommitted, 1: horizontal scrub, 2: vertical brightness
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var lastHapticBrightnessPct = -1

                        detectDragGestures(
                            onDragStart = {
                                if (isHolding2x) return@detectDragGestures
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                                lastHapticBrightnessPct = -1
                            },
                            onDragEnd = {
                                if (dragLockedDirection == 1) {
                                    val exo = exoPlayerRef
                                    val dur = if (exo != null && exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET) exo.duration else (actualDurationMs ?: 11520000L)
                                    val finalSeekMs = scrubTargetPosMs.coerceIn(0L, dur)
                                    if (exo != null) {
                                        performExoSeek(exo, finalSeekMs)
                                    } else {
                                        mediaPlayerRef?.let { mp ->
                                            if (mp.duration > 0) {
                                                mp.seekTo(finalSeekMs.toInt())
                                            }
                                        }
                                        viewModel.setPlaybackProgress((finalSeekMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                                    }
                                    isHorizontalScrubbing = false
                                } else if (dragLockedDirection == 2) {
                                    viewModel.setShowLeftSwipeIndicator(false)
                                    isDraggingHUD = false
                                    hudTriggerKey++
                                }
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                                lastHapticBrightnessPct = -1
                            },
                            onDragCancel = {
                                if (dragLockedDirection == 1) {
                                    isHorizontalScrubbing = false
                                } else if (dragLockedDirection == 2) {
                                    viewModel.setShowLeftSwipeIndicator(false)
                                    isDraggingHUD = false
                                    hudTriggerKey++
                                }
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                                lastHapticBrightnessPct = -1
                            },
                            onDrag = { change, dragAmount ->
                                if (isHolding2x) return@detectDragGestures

                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y

                                if (dragLockedDirection == 0) {
                                    val absX = Math.abs(totalDragX)
                                    val absY = Math.abs(totalDragY)
                                    val swipeThreshold = (20f / currentGestureSensitivity).coerceIn(8f, 50f)
                                    if (absX > 20f || absY > swipeThreshold) {
                                        if (absX > absY) {
                                            dragLockedDirection = 1
                                            isHorizontalScrubbing = true
                                            val dur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
                                            val currentPos = exoPlayerRef?.currentPosition ?: (viewModel.playbackProgress.value * dur).toLong()
                                            scrubStartPosMs = currentPos
                                            scrubTargetPosMs = currentPos
                                            scrubDeltaMs = 0L
                                            scrubTargetProgress = (currentPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            dragLockedDirection = 2
                                            hudType = "brightness"
                                            isDraggingHUD = true
                                            hudOverlayVisible = true
                                            viewModel.setShowLeftSwipeIndicator(true)
                                            lastHapticBrightnessPct = (viewModel.brightnessLevel.value * 100).toInt()
                                            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {}
                                        }
                                    }
                                }

                                if (dragLockedDirection == 1) {
                                    val dur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
                                    val screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(800f)
                                    // Scale horizontal seek: full screen drag width corresponds to 90 seconds (bounded proportionally for shorter/longer media)
                                    val fullScreenTimeRangeMs = (dur * 0.20f).coerceIn(60_000f, 150_000f)
                                    val deltaMs = ((totalDragX / screenWidthPx) * fullScreenTimeRangeMs).toLong()
                                    val newTargetMs = (scrubStartPosMs + deltaMs).coerceIn(0L, dur)
                                    val newProgress = if (dur > 0) (newTargetMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                                    scrubTargetProgress = newProgress
                                    scrubTargetPosMs = newTargetMs
                                    scrubDeltaMs = newTargetMs - scrubStartPosMs
                                    viewModel.setPlaybackProgress(newProgress)
                                    change.consume()
                                } else if (dragLockedDirection == 2) {
                                    val deltaBrightness = (-dragAmount.y / 400f) * currentGestureSensitivity
                                    viewModel.adjustBrightness(deltaBrightness)
                                    val newBrightnessPct = (viewModel.brightnessLevel.value * 100).toInt()
                                    if (lastHapticBrightnessPct != -1 && Math.abs(newBrightnessPct - lastHapticBrightnessPct) >= 2) {
                                        try { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                        lastHapticBrightnessPct = newBrightnessPct
                                    } else if (lastHapticBrightnessPct == -1) {
                                        lastHapticBrightnessPct = newBrightnessPct
                                    }
                                    if ((newBrightnessPct == 0 || newBrightnessPct == 100) && lastHapticBrightnessPct != newBrightnessPct) {
                                        try { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
                                        lastHapticBrightnessPct = newBrightnessPct
                                    }
                                    hudType = "brightness"
                                    isDraggingHUD = true
                                    hudOverlayVisible = true
                                    change.consume()
                                }
                            }
                        )
                    }
            )

            // Center Zone (~23% Native Zone): Double-tap to Pause / Resume & Long press 2x & Single tap controls toggle
            Box(
                modifier = Modifier
                    .weight(0.23f)
                    .fillMaxHeight()
                    .pointerInput(mediaPlayerRef, isNativeGesturePaused, isPlaying) {
                        detectTapGestures(
                            onPress = {
                                var isLongPress = false
                                val defaultSpeed = currentSpeedState
                                try {
                                    kotlinx.coroutines.coroutineScope {
                                        val job = launch {
                                            kotlinx.coroutines.delay(300L)
                                            isLongPress = true
                                            isHolding2x = true
                                            viewModel.setPlaybackSpeed(2.0f)
                                        }
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            job.cancel()
                                        }
                                    }
                                } finally {
                                    if (isLongPress || isHolding2x) {
                                        isHolding2x = false
                                        viewModel.setPlaybackSpeed(defaultSpeed)
                                        lastLongPressReleaseTime = System.currentTimeMillis()
                                    }
                                }
                            },
                            onDoubleTap = {
                                if (System.currentTimeMillis() - lastLongPressReleaseTime < 500L) return@detectTapGestures
                                if (activeContinuousSeekDirection != 0) {
                                    currentExitContinuousSeekMode()
                                }
                                if (isNativeGesturePaused || !isPlaying) {
                                    isNativeGesturePaused = false
                                    viewModel.setPlaying(true)
                                    try {
                                        exoPlayerRef?.play()
                                        mediaPlayerRef?.start()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    isNativeGesturePaused = true
                                    viewModel.setPlaying(false)
                                    try {
                                        exoPlayerRef?.pause()
                                        mediaPlayerRef?.pause()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            onTap = {
                                if (System.currentTimeMillis() - lastLongPressReleaseTime < 500L) return@detectTapGestures
                                if (activeContinuousSeekDirection != 0) {
                                    currentExitContinuousSeekMode()
                                }
                                toggleControls()
                            }
                        )
                    }
                    .pointerInput(isHolding2x) {
                        var dragLockedDirection = 0 // 0: uncommitted, 1: horizontal scrub
                        var totalDragX = 0f
                        var totalDragY = 0f

                        detectDragGestures(
                            onDragStart = {
                                if (isHolding2x) return@detectDragGestures
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDragEnd = {
                                if (dragLockedDirection == 1) {
                                    val exo = exoPlayerRef
                                    val dur = if (exo != null && exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET) exo.duration else (actualDurationMs ?: 11520000L)
                                    val finalSeekMs = scrubTargetPosMs.coerceIn(0L, dur)
                                    if (exo != null) {
                                        performExoSeek(exo, finalSeekMs)
                                    } else {
                                        mediaPlayerRef?.let { mp ->
                                            if (mp.duration > 0) {
                                                mp.seekTo(finalSeekMs.toInt())
                                            }
                                        }
                                        viewModel.setPlaybackProgress((finalSeekMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                                    }
                                    isHorizontalScrubbing = false
                                }
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDragCancel = {
                                if (dragLockedDirection == 1) {
                                    isHorizontalScrubbing = false
                                }
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                if (isHolding2x) return@detectDragGestures

                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y

                                if (dragLockedDirection == 0) {
                                    val absX = Math.abs(totalDragX)
                                    val absY = Math.abs(totalDragY)
                                    if (absX > 20f && absX > absY) {
                                        dragLockedDirection = 1
                                        isHorizontalScrubbing = true
                                        val dur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
                                        val currentPos = exoPlayerRef?.currentPosition ?: (viewModel.playbackProgress.value * dur).toLong()
                                        scrubStartPosMs = currentPos
                                        scrubTargetPosMs = currentPos
                                        scrubDeltaMs = 0L
                                        scrubTargetProgress = (currentPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                                    }
                                }

                                if (dragLockedDirection == 1) {
                                    val dur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
                                    val screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(800f)
                                    val fullScreenTimeRangeMs = (dur * 0.20f).coerceIn(60_000f, 150_000f)
                                    val deltaMs = ((totalDragX / screenWidthPx) * fullScreenTimeRangeMs).toLong()
                                    val newTargetMs = (scrubStartPosMs + deltaMs).coerceIn(0L, dur)
                                    val newProgress = if (dur > 0) (newTargetMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                                    scrubTargetProgress = newProgress
                                    scrubTargetPosMs = newTargetMs
                                    scrubDeltaMs = newTargetMs - scrubStartPosMs
                                    viewModel.setPlaybackProgress(newProgress)
                                    change.consume()
                                }
                            }
                        )
                    }
            )

            // Right Zone (~38.5%): Volume drag & YouTube-style Continuous Double-Tap seek forward (+10s, +20s...) & Long press 2x & Single tap controls toggle
            Box(
                modifier = Modifier
                    .weight(0.385f)
                    .fillMaxHeight()
                    .pointerInput(mediaPlayerRef) {
                        val zoneDirection = 1
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            var isFirstLongPress = false
                            val firstDefaultSpeed = currentSpeedState
                            var firstUp: androidx.compose.ui.input.pointer.PointerInputChange? = null

                            val firstLongPressJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(300L)
                                if (!isHorizontalScrubbing && !isDraggingHUD && !isVolumeGestureActive) {
                                    isFirstLongPress = true
                                    isHolding2x = true
                                    viewModel.setPlaybackSpeed(2.0f)
                                }
                            }
                            try {
                                firstUp = waitForUpOrCancellation()
                            } finally {
                                firstLongPressJob.cancel()
                                if (isFirstLongPress || isHolding2x) {
                                    isHolding2x = false
                                    viewModel.setPlaybackSpeed(firstDefaultSpeed)
                                    lastLongPressReleaseTime = System.currentTimeMillis()
                                }
                            }

                            if (firstUp == null || isFirstLongPress || System.currentTimeMillis() - lastLongPressReleaseTime < 500L) {
                                return@awaitEachGesture
                            }
                            firstUp?.consume()

                            val now = System.currentTimeMillis()
                            val isContinuousActiveInSameDirection = currentContinuousSeekEnabled &&
                                (activeContinuousSeekDirection == zoneDirection) &&
                                (now - lastSeekTapTimeMs < 1000L)

                            if (isContinuousActiveInSameDirection) {
                                // Continuous seeking in the same direction: single tap seeks another 10s immediately!
                                currentPerformContinuousSeekStep(zoneDirection)
                                return@awaitEachGesture
                            }

                            // Otherwise, continuous seek is not active in this direction (idle, expired >= 1s, or opposite direction active).
                            // A double-tap in THIS direction is strictly required.
                            val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis.coerceIn(250L, 350L)
                            val secondDown = withTimeoutOrNull(doubleTapTimeoutMs) {
                                awaitFirstDown(requireUnconsumed = false)
                            }

                            if (secondDown == null) {
                                // Single tap only.
                                // Direction change protection: if opposite direction was active or gap >= 1s,
                                // a single tap must NEVER trigger an accidental seek.
                                if (activeContinuousSeekDirection == 0 && System.currentTimeMillis() - lastLongPressReleaseTime >= 500L) {
                                    currentToggleControls()
                                }
                                return@awaitEachGesture
                            }

                            var isSecondLongPress = false
                            val secondDefaultSpeed = currentSpeedState
                            var secondUp: androidx.compose.ui.input.pointer.PointerInputChange? = null

                            val secondLongPressJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(300L)
                                if (!isHorizontalScrubbing && !isDraggingHUD && !isVolumeGestureActive) {
                                    isSecondLongPress = true
                                    isHolding2x = true
                                    viewModel.setPlaybackSpeed(2.0f)
                                }
                            }
                            try {
                                secondUp = waitForUpOrCancellation()
                            } finally {
                                secondLongPressJob.cancel()
                                if (isSecondLongPress || isHolding2x) {
                                    isHolding2x = false
                                    viewModel.setPlaybackSpeed(secondDefaultSpeed)
                                    lastLongPressReleaseTime = System.currentTimeMillis()
                                }
                            }

                            if (secondUp != null && !isSecondLongPress && System.currentTimeMillis() - lastLongPressReleaseTime >= 500L) {
                                secondUp?.consume()
                                currentPerformContinuousSeekStep(zoneDirection)
                            }
                        }
                    }
                    .pointerInput(isHolding2x) {
                        var dragLockedDirection = 0 // 0: uncommitted, 1: horizontal scrub, 2: vertical volume
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var volumeSteps = 10 // 0 to 20 steps (each step = 5% / 0.05f)
                        var accumulatedVolumeDragY = 0f
                        var lastHapticVolumePct = -1

                        detectDragGestures(
                            onDragStart = {
                                if (isHolding2x) return@detectDragGestures
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                                volumeSteps = Math.round(viewModel.volumeLevel.value * 20f).coerceIn(0, 20)
                                accumulatedVolumeDragY = 0f
                                lastHapticVolumePct = volumeSteps * 5
                            },
                            onDragEnd = {
                                if (dragLockedDirection == 1) {
                                    val exo = exoPlayerRef
                                    val dur = if (exo != null && exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET) exo.duration else (actualDurationMs ?: 11520000L)
                                    val finalSeekMs = scrubTargetPosMs.coerceIn(0L, dur)
                                    if (exo != null) {
                                        performExoSeek(exo, finalSeekMs)
                                    } else {
                                        mediaPlayerRef?.let { mp ->
                                            if (mp.duration > 0) {
                                                mp.seekTo(finalSeekMs.toInt())
                                            }
                                        }
                                        viewModel.setPlaybackProgress((finalSeekMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                                    }
                                    isHorizontalScrubbing = false
                                } else if (dragLockedDirection == 2) {
                                    val finalVolume = (volumeSteps * 0.05f).coerceIn(0f, 1f)
                                    viewModel.setVolume(finalVolume)
                                    pendingAudioVolume.value = finalVolume
                                    applyAudioVolume(finalVolume)
                                    isVolumeGestureActive = false
                                    lastUserVolumeUpdateTime = System.currentTimeMillis()
                                    viewModel.setShowRightSwipeIndicator(false)
                                    isDraggingHUD = false
                                    hudTriggerKey++
                                }
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                                accumulatedVolumeDragY = 0f
                                lastHapticVolumePct = -1
                            },
                            onDragCancel = {
                                if (dragLockedDirection == 1) {
                                    isHorizontalScrubbing = false
                                } else if (dragLockedDirection == 2) {
                                    val finalVolume = (volumeSteps * 0.05f).coerceIn(0f, 1f)
                                    viewModel.setVolume(finalVolume)
                                    pendingAudioVolume.value = finalVolume
                                    applyAudioVolume(finalVolume)
                                    isVolumeGestureActive = false
                                    lastUserVolumeUpdateTime = System.currentTimeMillis()
                                    viewModel.setShowRightSwipeIndicator(false)
                                    isDraggingHUD = false
                                    hudTriggerKey++
                                }
                                dragLockedDirection = 0
                                totalDragX = 0f
                                totalDragY = 0f
                                accumulatedVolumeDragY = 0f
                                lastHapticVolumePct = -1
                            },
                            onDrag = { change, dragAmount ->
                                if (isHolding2x) return@detectDragGestures

                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y

                                if (dragLockedDirection == 0) {
                                    val absX = Math.abs(totalDragX)
                                    val absY = Math.abs(totalDragY)
                                    val swipeThreshold = (20f / currentGestureSensitivity).coerceIn(8f, 50f)
                                    if (absX > 20f || absY > swipeThreshold) {
                                        if (absX > absY) {
                                            dragLockedDirection = 1
                                            isHorizontalScrubbing = true
                                            val dur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
                                            val currentPos = exoPlayerRef?.currentPosition ?: (viewModel.playbackProgress.value * dur).toLong()
                                            scrubStartPosMs = currentPos
                                            scrubTargetPosMs = currentPos
                                            scrubDeltaMs = 0L
                                            scrubTargetProgress = (currentPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            dragLockedDirection = 2
                                            isVolumeGestureActive = true
                                            hudType = "volume"
                                            isDraggingHUD = true
                                            hudOverlayVisible = true
                                            viewModel.setShowRightSwipeIndicator(true)

                                            // Requirement 2: A small vertical swipe should trigger only one 5% change.
                                            // Upward swipe (totalDragY < 0): increase volume by 1 step (+5%)
                                            // Downward swipe (totalDragY > 0): decrease volume by 1 step (-5%)
                                            val initialStepDelta = if (totalDragY < 0f) 1 else -1
                                            volumeSteps = (volumeSteps + initialStepDelta).coerceIn(0, 20)
                                            val initialTargetVolume = volumeSteps * 0.05f
                                            viewModel.setVolume(initialTargetVolume)
                                            pendingAudioVolume.value = initialTargetVolume
                                            lastUserVolumeUpdateTime = System.currentTimeMillis()

                                            lastHapticVolumePct = volumeSteps * 5
                                            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}

                                            // Reset accumulated drag so subsequent steps require deliberate movement
                                            accumulatedVolumeDragY = 0f
                                        }
                                    }
                                }

                                if (dragLockedDirection == 1) {
                                    val dur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
                                    val screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(800f)
                                    // Scale horizontal seek: full screen drag width corresponds to 90 seconds (bounded proportionally for shorter/longer media)
                                    val fullScreenTimeRangeMs = (dur * 0.20f).coerceIn(60_000f, 150_000f)
                                    val deltaMs = ((totalDragX / screenWidthPx) * fullScreenTimeRangeMs).toLong()
                                    val newTargetMs = (scrubStartPosMs + deltaMs).coerceIn(0L, dur)
                                    val newProgress = if (dur > 0) (newTargetMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                                    scrubTargetProgress = newProgress
                                    scrubTargetPosMs = newTargetMs
                                    scrubDeltaMs = newTargetMs - scrubStartPosMs
                                    viewModel.setPlaybackProgress(newProgress)
                                    change.consume()
                                } else if (dragLockedDirection == 2) {
                                    accumulatedVolumeDragY += dragAmount.y
                                    val density = context.resources.displayMetrics.density
                                    // Step distance required per 5% change (~28dp adjusted by sensitivity, e.g. 75px)
                                    val stepDistancePx = (28f * density / currentGestureSensitivity).coerceIn(40f, 160f)

                                    // Upward movement (dragAmount.y < 0): increase volume in fixed 5% steps
                                    while (accumulatedVolumeDragY <= -stepDistancePx) {
                                        if (volumeSteps < 20) {
                                            volumeSteps++
                                            val targetVolume = volumeSteps * 0.05f
                                            viewModel.setVolume(targetVolume)
                                            pendingAudioVolume.value = targetVolume
                                            lastUserVolumeUpdateTime = System.currentTimeMillis()
                                            lastHapticVolumePct = volumeSteps * 5
                                            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                            accumulatedVolumeDragY += stepDistancePx
                                        } else {
                                            accumulatedVolumeDragY = 0f
                                            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
                                            break
                                        }
                                    }

                                    // Downward movement (dragAmount.y > 0): decrease volume in fixed 5% steps
                                    while (accumulatedVolumeDragY >= stepDistancePx) {
                                        if (volumeSteps > 0) {
                                            volumeSteps--
                                            val targetVolume = volumeSteps * 0.05f
                                            viewModel.setVolume(targetVolume)
                                            pendingAudioVolume.value = targetVolume
                                            lastUserVolumeUpdateTime = System.currentTimeMillis()
                                            lastHapticVolumePct = volumeSteps * 5
                                            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                            accumulatedVolumeDragY -= stepDistancePx
                                        } else {
                                            accumulatedVolumeDragY = 0f
                                            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
                                            break
                                        }
                                    }

                                    hudType = "volume"
                                    isDraggingHUD = true
                                    hudOverlayVisible = true
                                    change.consume()
                                }
                            }
                        )
                    }
            )
        }
    }

        // Perform seek delta helper & contextual button states
        val performSeekDelta = { deltaMs: Int ->
            showAndResetTimer()
            val exo = exoPlayerRef
            if (exo != null) {
                val currentPos = if (exo.currentPosition > 0L) exo.currentPosition else positionStateMs
                val dur = if (exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET) exo.duration else durationStateMs
                val hasDur = dur > 0L && dur != androidx.media3.common.C.TIME_UNSET
                val newPos = if (hasDur) (currentPos + deltaMs).coerceIn(0L, dur) else (currentPos + deltaMs).coerceAtLeast(0L)
                performExoSeek(exo, newPos)
            } else {
                val view = mediaPlayerRef
                if (view != null && view.duration > 0) {
                    val currentPos = view.currentPosition.toLong()
                    val newPos = (currentPos + deltaMs).coerceIn(0L, view.duration.toLong())
                    val calculatedProgress = newPos.toFloat() / view.duration.toFloat()
                    viewModel.setPlaybackProgress(calculatedProgress)
                    view.seekTo(newPos.toInt())
                } else {
                    val totalSecs = 11520L
                    val deltaProgress = (deltaMs / 1000f) / totalSecs
                    val currentProgress = viewModel.playbackProgress.value
                    viewModel.setPlaybackProgress((currentProgress + deltaProgress).coerceIn(0f, 1f))
                }
            }
        }

        val nextEpisodeAvailable = remember(nextEpisodeInQueue, positionStateMs, durationStateMs) {
            nextEpisodeInQueue != null && durationStateMs > 0L && (positionStateMs >= durationStateMs - 120_000L || positionStateMs >= durationStateMs * 0.9f)
        }

        LaunchedEffect(nextEpisodeAvailable) {
            if (nextEpisodeAvailable) {
                isNextEpisodeOnlyVisible = true
                areControlsVisible = false
                kotlinx.coroutines.delay(5000L)
                isNextEpisodeOnlyVisible = false
            } else {
                isNextEpisodeOnlyVisible = false
            }
        }

        val onNextEpisode = {
            showAndResetTimer()
            if (nextEpisodeInQueue != null) {
                exoPlayerRef?.let { exo ->
                    latestSavePlaybackPosition(exo)
                }
                currentPlayingEpId = nextEpisodeInQueue.id
                viewModel.selectEpisode(nextEpisodeInQueue.id)
                viewModel.setPlaying(true)
            }
        }

        val onPrevEpisode = {
            showAndResetTimer()
            if (prevEpisodeInQueue != null) {
                exoPlayerRef?.let { exo ->
                    latestSavePlaybackPosition(exo)
                }
                currentPlayingEpId = prevEpisodeInQueue.id
                viewModel.selectEpisode(prevEpisodeInQueue.id)
                viewModel.setPlaying(true)
            }
        }

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        val hasNextEpisodeForMediaSession = !isMovie && nextEpisodeInQueue != null
        val hasPrevEpisodeForMediaSession = !isMovie && prevEpisodeInQueue != null

        // Update Android MediaSession Metadata for System Media Controls (Quick Settings, Notification, Lock Screen, Samsung Now Bar)
        LaunchedEffect(
            currentMovie,
            currentEpisode,
            isMovie,
            hasNextEpisodeForMediaSession,
            hasPrevEpisodeForMediaSession,
            exoPlayerRef,
            videoUri
        ) {
            if (exoPlayerRef != null) {
                com.example.util.MediaSessionManager.updateMetadata(
                    context = context,
                    movie = currentMovie,
                    episode = currentEpisode,
                    isMovie = isMovie,
                    nextEpisodeAvailable = hasNextEpisodeForMediaSession,
                    prevEpisodeAvailable = hasPrevEpisodeForMediaSession,
                    onNextEpisode = if (hasNextEpisodeForMediaSession) { { onNextEpisode() } } else null,
                    onPrevEpisode = if (hasPrevEpisodeForMediaSession) { { onPrevEpisode() } } else null,
                    videoUri = videoUri
                )
            }
        }

        // Subtle, smooth fade animation for playback controls
        val controlsAlpha by animateFloatAsState(
            targetValue = if (areControlsVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = 260,
                easing = if (areControlsVisible) LinearOutSlowInEasing else FastOutLinearInEasing
            ),
            label = "controls_fade_alpha"
        )

        // Responsive 190ms ease-in-out morph transitions for Settings and Playlist
        val settingsMorphTransition = updateTransition(
            targetState = showInfuseSettingsMenu,
            label = "InfuseSettingsButtonMorph"
        )
        val settingsMorphProgress by settingsMorphTransition.animateFloat(
            transitionSpec = {
                tween(durationMillis = 190, easing = FastOutSlowInEasing)
            },
            label = "settings_morph_progress"
        ) { isExpanded -> if (isExpanded) 1f else 0f }

        val playlistMorphTransition = updateTransition(
            targetState = showEpisodeDrawer,
            label = "InfusePlaylistButtonMorph"
        )
        val playlistMorphProgress by playlistMorphTransition.animateFloat(
            transitionSpec = {
                tween(durationMillis = 190, easing = FastOutSlowInEasing)
            },
            label = "playlist_morph_progress"
        ) { isExpanded -> if (isExpanded) 1f else 0f }

        val isSettingsMorphing = showInfuseSettingsMenu || settingsMorphProgress > 0.004f
        val isPlaylistMorphing = showEpisodeDrawer || playlistMorphProgress > 0.004f

        // Main UI Overlay Container
        AnimatedVisibility(
            visible = (areControlsVisible || isNextEpisodeOnlyVisible) && !isInPipMode,
            enter = fadeIn(animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 260, easing = FastOutLinearInEasing)),
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    hudBackdropGraphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(hudBackdropGraphicsLayer)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp)
            ) {
            // 1. TOP CONTROL BAR (INFUSE STYLE)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = controlsAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Top-Left: Close (X) standalone, and Aspect Ratio + Picture-in-Picture (PiP) grouped in a pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlayerIconButton(
                        icon = Icons.Default.Close,
                        onClick = {
                            if (!areControlsVisible) return@PlayerIconButton
                            try {
                                com.example.util.VolumeBoostManager.disableVolumeBoost()
                                com.example.util.MediaSessionManager.releaseSession(context)
                                mediaPlayerRef?.let { mp ->
                                    if (mp.isPlaying) mp.pause()
                                    mp.stop()
                                    mp.reset()
                                    mp.release()
                                }
                                exoPlayerRef?.let { exo ->
                                    saveCurrentPlaybackPosition(exo)
                                    if (exo.isPlaying) exo.pause()
                                    exo.clearVideoSurface()
                                    exo.stop()
                                    exo.release()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            viewModel.setPlaying(false)
                            mediaPlayerRef = null
                            exoPlayerRef = null
                            viewModel.navigateBack()
                        },
                        modifier = Modifier.testTag("player_close_button")
                    )

                    // Aspect Ratio (Crop) + PiP Grouped Pill Container
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141720).copy(alpha = 0.52f))
                            .border(
                                width = 0.75.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Aspect Ratio Button
                        val aspectInteractionSource = remember { MutableInteractionSource() }
                        val isAspectPressed by aspectInteractionSource.collectIsPressedAsState()
                        val isAspectHovered by aspectInteractionSource.collectIsHoveredAsState()
                        val aspectScale by animateFloatAsState(
                            targetValue = when {
                                isAspectPressed -> 0.88f
                                isAspectHovered -> 1.08f
                                else -> 1.0f
                            },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "aspect_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 44.dp)
                                .graphicsLayer {
                                    scaleX = aspectScale
                                    scaleY = aspectScale
                                }
                                .clickable(
                                    enabled = areControlsVisible,
                                    interactionSource = aspectInteractionSource,
                                    indication = ripple(bounded = true, color = Color.White)
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAndResetTimer()
                                    cycleAspectRatioMode()
                                }
                                .testTag("player_aspect_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = aspectRatioMode.icon,
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // PiP Button
                        val pipInteractionSource = remember { MutableInteractionSource() }
                        val isPipPressed by pipInteractionSource.collectIsPressedAsState()
                        val isPipHovered by pipInteractionSource.collectIsHoveredAsState()
                        val pipScale by animateFloatAsState(
                            targetValue = when {
                                isPipPressed -> 0.88f
                                isPipHovered -> 1.08f
                                else -> 1.0f
                            },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "pip_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 44.dp)
                                .graphicsLayer {
                                    scaleX = pipScale
                                    scaleY = pipScale
                                }
                                .clickable(
                                    enabled = areControlsVisible,
                                    interactionSource = pipInteractionSource,
                                    indication = ripple(bounded = true, color = Color.White)
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAndResetTimer()
                                    val activity = context.findActivity()
                                    if (activity is MainActivity) {
                                        val exo = exoPlayerRef
                                        val videoSize = if (activeVideoSize.width > 0) activeVideoSize else (exo?.videoSize ?: androidx.media3.common.VideoSize.UNKNOWN)
                                        val vWidth = if (videoSize.width > 0) videoSize.width else 16
                                        val vHeight = if (videoSize.height > 0) videoSize.height else 9
                                        val ratio = android.util.Rational(vWidth, vHeight)
                                        val success = activity.enterPip(ratio)
                                        if (!success) {
                                            toastMessage = if (!activity.isPipSupported()) "PiP Mode not supported on this device" else "Could not enter PiP mode"
                                        }
                                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && activity != null) {
                                        try {
                                            activity.enterPictureInPictureMode(
                                                android.app.PictureInPictureParams.Builder().build()
                                            )
                                            toastMessage = "Entering Picture-in-Picture..."
                                        } catch (e: Exception) {
                                            toastMessage = "PiP Mode not supported on this device"
                                        }
                                    } else {
                                        toastMessage = "PiP mode requires Android Oreo+"
                                    }
                                }
                                .testTag("player_pip_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { alpha = controlsAlpha },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 30s Button
                    val rewindInteractionSource = remember { MutableInteractionSource() }
                    val isRewindPressed by rewindInteractionSource.collectIsPressedAsState()
                    val isRewindHovered by rewindInteractionSource.collectIsHoveredAsState()
                    val rewindScale by animateFloatAsState(
                        targetValue = when {
                            isRewindPressed -> 0.88f
                            isRewindHovered -> 1.08f
                            else -> 1.0f
                        },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "rewind_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = rewindScale
                                scaleY = rewindScale
                            }
                            .clip(CircleShape)
                            .clickable(
                                enabled = areControlsVisible,
                                interactionSource = rewindInteractionSource,
                                indication = ripple(bounded = false, radius = 24.dp, color = Color.White)
                            ) {
                                showAndResetTimer()
                                performSeekDelta(-30000)
                            }
                            .testTag("player_center_rewind_30s_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        CurvedSeekIcon(
                            isForward = false,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Play / Pause Button (Larger, central, Infuse style)
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                    val isPlayHovered by playInteractionSource.collectIsHoveredAsState()
                    val playScale by animateFloatAsState(
                        targetValue = when {
                            isPlayPressed -> 0.88f
                            isPlayHovered -> 1.08f
                            else -> 1.0f
                        },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "play_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .clip(CircleShape)
                            .clickable(
                                enabled = areControlsVisible,
                                interactionSource = playInteractionSource,
                                indication = ripple(bounded = false, radius = 30.dp, color = Color.White)
                            ) {
                                showAndResetTimer()
                                val nextPlaying = !isPlaying
                                viewModel.setPlaying(nextPlaying)
                                try {
                                    exoPlayerRef?.let { exo ->
                                        if (nextPlaying) {
                                            exo.play()
                                        } else {
                                            latestSavePlaybackPosition(exo)
                                            exo.pause()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .testTag("player_center_play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        InfusePlayPauseIcon(
                            isPlaying = isPlaying,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Forward 30s Button
                    val forwardInteractionSource = remember { MutableInteractionSource() }
                    val isForwardPressed by forwardInteractionSource.collectIsPressedAsState()
                    val isForwardHovered by forwardInteractionSource.collectIsHoveredAsState()
                    val forwardScale by animateFloatAsState(
                        targetValue = when {
                            isForwardPressed -> 0.88f
                            isForwardHovered -> 1.08f
                            else -> 1.0f
                        },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "forward_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = forwardScale
                                scaleY = forwardScale
                            }
                            .clip(CircleShape)
                            .clickable(
                                enabled = areControlsVisible,
                                interactionSource = forwardInteractionSource,
                                indication = ripple(bounded = false, radius = 24.dp, color = Color.White)
                            ) {
                                showAndResetTimer()
                                performSeekDelta(30000)
                            }
                            .testTag("player_center_forward_30s_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        CurvedSeekIcon(
                            isForward = true,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // 3. BOTTOM TIMELINE & METADATA LAYOUT (INFUSE STYLE)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row of bottom section: Title & Subtitle (Left), Settings button (Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = if (isUserDragging) 0f else 1f },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Bottom-Left Metadata docked directly above progress bar
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                            .graphicsLayer { alpha = if (isUserDragging) 0f else controlsAlpha }
                    ) {
                        if (showSeasonEpisodeSubtitle.isNotBlank()) {
                            Text(
                                text = showSeasonEpisodeSubtitle,
                                color = primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player_show_season_episode")
                            )
                        }
                        Text(
                            text = displayEpisodeName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("player_episode_name")
                        )
                    }

                    // Bottom-Right Action Controls (Next Episode at top, Playlist and Settings below it)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        // Top item: Next Episode Button (Compact liquid-glass style, when available and controls are visible)
                        if (nextEpisodeAvailable && (areControlsVisible || isNextEpisodeOnlyVisible)) {
                            val nextEpInteractionSource = remember { MutableInteractionSource() }
                            val isNextEpPressed by nextEpInteractionSource.collectIsPressedAsState()
                            val isNextEpHovered by nextEpInteractionSource.collectIsHoveredAsState()
                            val nextEpScale by animateFloatAsState(
                                targetValue = when {
                                    isNextEpPressed -> 0.90f
                                    isNextEpHovered -> 1.06f
                                    else -> 1.0f
                                },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "next_ep_scale"
                            )
                            val nextEpGlassBorder = Color.White.copy(alpha = if (isNextEpPressed || isNextEpHovered) 0.35f else 0.18f)
                            val nextEpGlassBg = Color.Black.copy(alpha = if (isNextEpPressed) 0.55f else 0.40f)

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = nextEpScale
                                        scaleY = nextEpScale
                                    }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(nextEpGlassBg, RoundedCornerShape(20.dp))
                                    .border(
                                        width = 0.5.dp,
                                        color = nextEpGlassBorder,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable(
                                        interactionSource = nextEpInteractionSource,
                                        indication = ripple(bounded = true, color = Color.White)
                                    ) {
                                        onNextEpisode()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("player_next_episode_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Next Episode",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Episode",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Bottom item: Episode Queue & Settings buttons (Grouped Pill for TV Series, Standalone Circular Button for Movies)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.graphicsLayer { alpha = controlsAlpha }
                        ) {
                            if (showPlaylistButton) {
                                // TV Series: Grouped Pill Container (Playlist + Settings)
                                Row(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF141720).copy(alpha = 0.52f))
                                        .border(
                                            width = 0.75.dp,
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.28f),
                                                    Color.White.copy(alpha = 0.08f)
                                                )
                                            ),
                                            shape = CircleShape
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Episode Queue / Playlist Button
                                    val playlistInteractionSource = remember { MutableInteractionSource() }
                                    val isPlaylistPressed by playlistInteractionSource.collectIsPressedAsState()
                                    val isPlaylistHovered by playlistInteractionSource.collectIsHoveredAsState()
                                    val playlistScale by animateFloatAsState(
                                        targetValue = when {
                                            isPlaylistPressed -> 0.88f
                                            isPlaylistHovered -> 1.08f
                                            else -> 1.0f
                                        },
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "playlist_scale"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 44.dp)
                                            .onGloballyPositioned { coords ->
                                                playlistButtonPosInRoot = coords.positionInRoot()
                                                playlistButtonSizePx = coords.size
                                            }
                                            .graphicsLayer {
                                                scaleX = playlistScale
                                                scaleY = playlistScale
                                                alpha = if (isPlaylistMorphing) 0f else 1f
                                            }
                                            .clickable(
                                                enabled = areControlsVisible && !isPlaylistMorphing,
                                                interactionSource = playlistInteractionSource,
                                                indication = ripple(bounded = true, color = Color.White)
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showAndResetTimer()
                                                showInfuseSettingsMenu = false
                                                showEpisodeDrawer = true
                                            }
                                            .testTag("player_queue_trigger_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = "Episode Queue",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Vertical Divider
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(18.dp)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    )

                                    // Settings Gear Button
                                    val settingsInteractionSource = remember { MutableInteractionSource() }
                                    val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
                                    val isSettingsHovered by settingsInteractionSource.collectIsHoveredAsState()
                                    val settingsScale by animateFloatAsState(
                                        targetValue = when {
                                            isSettingsPressed -> 0.88f
                                            isSettingsHovered -> 1.08f
                                            else -> 1.0f
                                        },
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "settings_scale"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 44.dp)
                                            .onGloballyPositioned { coords ->
                                                settingsButtonPosInRoot = coords.positionInRoot()
                                                settingsButtonSizePx = coords.size
                                            }
                                            .graphicsLayer {
                                                scaleX = settingsScale
                                                scaleY = settingsScale
                                                alpha = if (isSettingsMorphing) 0f else 1f
                                            }
                                            .clickable(
                                                enabled = areControlsVisible && !isSettingsMorphing,
                                                interactionSource = settingsInteractionSource,
                                                indication = ripple(bounded = true, color = Color.White)
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showAndResetTimer()
                                                showEpisodeDrawer = false
                                                activeSubMenu = "Main"
                                                showInfuseSettingsMenu = true
                                            }
                                            .testTag("player_settings_trigger_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings Menu",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                // Movies: Standalone Circular Settings Button
                                val settingsInteractionSource = remember { MutableInteractionSource() }
                                val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
                                val isSettingsHovered by settingsInteractionSource.collectIsHoveredAsState()
                                val settingsScale by animateFloatAsState(
                                    targetValue = when {
                                        isSettingsPressed -> 0.88f
                                        isSettingsHovered -> 1.08f
                                        else -> 1.0f
                                    },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "settings_scale"
                                )
                                val settingsBgColor = Color(0xFF141720).copy(
                                    alpha = when {
                                        isSettingsPressed -> 0.72f
                                        isSettingsHovered -> 0.62f
                                        else -> 0.52f
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .onGloballyPositioned { coords ->
                                            settingsButtonPosInRoot = coords.positionInRoot()
                                            settingsButtonSizePx = coords.size
                                        }
                                        .graphicsLayer {
                                            scaleX = settingsScale
                                            scaleY = settingsScale
                                            alpha = if (isSettingsMorphing) 0f else 1f
                                        }
                                        .clip(CircleShape)
                                        .background(settingsBgColor, CircleShape)
                                        .border(
                                            width = 0.75.dp,
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.28f),
                                                    Color.White.copy(alpha = 0.08f)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                        .clickable(
                                            enabled = areControlsVisible && !isSettingsMorphing,
                                            interactionSource = settingsInteractionSource,
                                            indication = ripple(bounded = true, color = Color.White)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showAndResetTimer()
                                            showEpisodeDrawer = false
                                            activeSubMenu = "Main"
                                            showInfuseSettingsMenu = true
                                        }
                                        .testTag("player_settings_trigger_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Ultra-thin white progress timeline bar with Floating Frame Preview Thumbnail when dragging seek thumb
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.graphicsLayer { alpha = controlsAlpha }
                ) {
                    val sliderDisplayProgress = if (isUserDragging || isScrubbing) sliderProgress else progress
                    val exoForPreview = exoPlayerRef
                    val totalDurForPreview = when {
                        exoForPreview != null && exoForPreview.duration > 0 && exoForPreview.duration != androidx.media3.common.C.TIME_UNSET -> exoForPreview.duration
                        durationStateMs > 0L -> durationStateMs
                        actualDurationMs != null && actualDurationMs!! > 0L -> actualDurationMs!!
                        else -> 11520000L
                    }
                    val previewTargetMs = (sliderDisplayProgress.coerceIn(0f, 1f) * totalDurForPreview).toLong().coerceIn(0L, totalDurForPreview)
                    val previewDeltaMs = previewTargetMs - dragStartPositionMs
                    val fallbackArtUrl = remember(currentMovie) {
                        currentMovie.backdropUrl.ifBlank { currentMovie.posterUrl }
                    }

                    // Frame Preview Thumbnail docked directly above the seekbar and aligned to the seek thumb
                    AnimatedVisibility(
                        visible = isUserDragging && !isInPipMode,
                        enter = fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.90f, animationSpec = tween(140)),
                        exit = fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.92f, animationSpec = tween(160))
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            val previewWidth = 164.dp
                            val clampedFraction = sliderDisplayProgress.coerceIn(0f, 1f)
                            val thumbX = maxWidth * clampedFraction
                            val maxLeftOffset = (maxWidth - previewWidth).coerceAtLeast(0.dp)
                            val cardOffsetX = (thumbX - (previewWidth / 2)).coerceIn(0.dp, maxLeftOffset)

                            SeekBarFramePreviewThumbnail(
                                previewBitmap = seekPreviewBitmap,
                                fallbackArtworkUrl = fallbackArtUrl,
                                targetTimeStr = elapsedTime,
                                deltaMs = previewDeltaMs,
                                primaryColor = primaryColor,
                                modifier = Modifier
                                    .offset(x = cardOffsetX)
                                    .width(previewWidth)
                                    .testTag("player_seek_frame_preview")
                            )
                        }
                    }

                    val sliderInteractionSource = remember { MutableInteractionSource() }
                    val isSliderHovered by sliderInteractionSource.collectIsHoveredAsState()
                    val isSliderPressed by sliderInteractionSource.collectIsPressedAsState()
                    val isSliderDragged by sliderInteractionSource.collectIsDraggedAsState()
                    val isSeekingInteraction = isSliderHovered || isSliderPressed || isSliderDragged || isUserDragging || isScrubbing || isHorizontalScrubbing

                    // Subtle spring expansion animation of the progress bar track when hovering or interacting
                    val animatedTrackHeight by animateDpAsState(
                        targetValue = if (isSeekingInteraction) 7.5.dp else 4.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "progress_track_height"
                    )

                    // Seek thumb smoothly scales up during interaction and stays hidden when resting
                    val animatedThumbScale by animateFloatAsState(
                        targetValue = if (isUserDragging || isSliderPressed || isSliderDragged) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "thumb_scale_anim"
                    )

                    val animatedTrackAlpha by animateFloatAsState(
                        targetValue = if (isSeekingInteraction) 0.38f else 0.25f,
                        animationSpec = tween(durationMillis = 180),
                        label = "track_bg_alpha"
                    )

                    val animatedActiveProgressColor by animateColorAsState(
                        targetValue = if (isSeekingInteraction) primaryColor else Color.White,
                        animationSpec = tween(durationMillis = 180),
                        label = "active_progress_color"
                    )

                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        enabled = areControlsVisible,
                        value = sliderDisplayProgress.coerceIn(0f, 1f),
                        interactionSource = sliderInteractionSource,
                        onValueChange = { newProgress ->
                            showAndResetTimer()
                            val exo = exoPlayerRef
                            val dur = if (exo != null && exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET) exo.duration else durationStateMs
                            if (!isUserDragging) {
                                dragStartPositionMs = if (exo != null && exo.currentPosition >= 0L) {
                                    exo.currentPosition
                                } else {
                                    positionStateMs.coerceAtLeast(0L)
                                }
                            }
                            isUserDragging = true
                            isScrubbing = true
                            sliderProgress = newProgress
                            scrubTargetProgress = newProgress
                            if (dur > 0) {
                                val newTargetMs = (newProgress * dur).toLong().coerceIn(0L, dur)
                                positionStateMs = newTargetMs
                                scrubDelta = ((newTargetMs - dragStartPositionMs).toFloat() / 11520000f)
                            }
                        },
                        onValueChangeFinished = {
                            val exo = exoPlayerRef
                            val dur = if (exo != null && exo.duration > 0 && exo.duration != androidx.media3.common.C.TIME_UNSET) exo.duration else durationStateMs
                            if (exo != null) {
                                if (dur > 0L) {
                                    val targetPosition = (sliderProgress * dur).toLong().coerceIn(0L, dur)
                                    performExoSeek(exo, targetPosition)
                                }
                            } else {
                                val view = mediaPlayerRef
                                if (view != null && view.duration > 0) {
                                    val targetPosition = (sliderProgress * view.duration).toInt()
                                    view.seekTo(targetPosition)
                                }
                            }
                            isUserDragging = false
                            isScrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        thumb = {
                            if (animatedThumbScale > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp * animatedThumbScale)
                                        .clip(CircleShape)
                                        .background(Color.White, CircleShape)
                                        .border((2.dp * animatedThumbScale).coerceAtLeast(0.5.dp), primaryColor, CircleShape)
                                )
                            } else {
                                Box(modifier = Modifier.size(0.dp))
                            }
                        },
                        track = { sliderState ->
                            val fraction = sliderState.value
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedTrackHeight)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = animatedTrackAlpha), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(CircleShape)
                                        .background(animatedActiveProgressColor)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .hoverable(sliderInteractionSource)
                            .testTag("player_seekbar")
                    )

                    // Elapsed time (left) and Remaining time (right e.g. -35:50)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = elapsedTime,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = remainingTimeStr,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    val isAnyPanelOpen = showInfuseSettingsMenu || showEpisodeDrawer
    val activeScrimAlpha = when {
        showInfuseSettingsMenu -> settingsMorphProgress
        showEpisodeDrawer -> playlistMorphProgress
        settingsMorphProgress > 0.004f -> settingsMorphProgress
        playlistMorphProgress > 0.004f -> playlistMorphProgress
        else -> 0f
    }.coerceIn(0f, 1f)

    if (activeScrimAlpha > 0.004f) {
        // Subtle outside-tap dismissal scrim while keeping video playback visible and uninterrupted
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = activeScrimAlpha }
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    enabled = isAnyPanelOpen,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showInfuseSettingsMenu = false
                    showEpisodeDrawer = false
                }
        )
    }

    if (showInfuseSettingsMenu || settingsMorphProgress > 0.004f) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val containerWidthDp = configuration.screenWidthDp.dp
        val containerHeightDp = configuration.screenHeightDp.dp

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {

            // Compute exact start offsets from the Settings gear button's measured position in root
            val buttonRightMarginDp = remember(
                settingsButtonPosInRoot,
                settingsButtonSizePx,
                hudBackdropPositionInRoot,
                hudBackdropSize,
                density
            ) {
                if (hudBackdropSize.width > 0 && settingsButtonSizePx.width > 0) {
                    val rootRight = hudBackdropPositionInRoot.x + hudBackdropSize.width
                    val btnRight = settingsButtonPosInRoot.x + settingsButtonSizePx.width
                    with(density) { (rootRight - btnRight).coerceAtLeast(12f).toDp() }
                } else {
                    24.dp
                }
            }
            val buttonBottomMarginDp = remember(
                settingsButtonPosInRoot,
                settingsButtonSizePx,
                hudBackdropPositionInRoot,
                hudBackdropSize,
                density
            ) {
                if (hudBackdropSize.height > 0 && settingsButtonSizePx.height > 0) {
                    val rootBottom = hudBackdropPositionInRoot.y + hudBackdropSize.height
                    val btnBottom = settingsButtonPosInRoot.y + settingsButtonSizePx.height
                    with(density) { (rootBottom - btnBottom).coerceAtLeast(16f).toDp() }
                } else {
                    54.dp
                }
            }

            // Responsive panel dimensions for compact/landscape/tablet screens matching iOS Infuse proportions
            val targetPanelWidth = (containerWidthDp - 32.dp).coerceIn(220.dp, 262.dp)
            val desiredSubMenuHeight = when (activeSubMenu) {
                "Main" -> 238.dp
                "VolumeBoost" -> 266.dp
                "Speed" -> 296.dp
                "Audio" -> 276.dp
                "Subtitles" -> 296.dp
                "Advanced" -> 304.dp
                else -> 252.dp
            }
            val maxAllowedPanelHeight = (containerHeightDp - buttonBottomMarginDp - 16.dp).coerceAtLeast(180.dp)
            val targetPanelHeight = desiredSubMenuHeight.coerceAtMost(maxAllowedPanelHeight)

            val targetEndPadding = buttonRightMarginDp.coerceIn(16.dp, (containerWidthDp - targetPanelWidth - 12.dp).coerceAtLeast(16.dp))
            val targetBottomPadding = buttonBottomMarginDp.coerceIn(16.dp, (containerHeightDp - targetPanelHeight - 12.dp).coerceAtLeast(16.dp))

            val btnRightMarginPx = with(density) { buttonRightMarginDp.toPx() }
            val btnBottomMarginPx = with(density) { buttonBottomMarginDp.toPx() }
            val targetEndPaddingPx = with(density) { targetEndPadding.toPx() }
            val targetBottomPaddingPx = with(density) { targetBottomPadding.toPx() }
            val targetWidthPx = with(density) { targetPanelWidth.toPx() }
            val targetHeightPx = with(density) { targetPanelHeight.toPx() }
            val buttonSizePx = with(density) { 44.dp.toPx() }

            val panelCornerRadius = androidx.compose.ui.unit.lerp(
                targetPanelWidth.coerceAtLeast(targetPanelHeight) / 2,
                20.dp,
                settingsMorphProgress.coerceIn(0f, 1f)
            )

            Box(
                modifier = Modifier
                    .padding(end = targetEndPadding, bottom = targetBottomPadding)
                    .graphicsLayer {
                        val clamped = settingsMorphProgress.coerceIn(0f, 1f)

                        // Pure 2D flat scale and translation without perspective, overshoot, or bounciness
                        scaleX = androidx.compose.ui.util.lerp(buttonSizePx / targetWidthPx, 1.0f, clamped)
                        scaleY = androidx.compose.ui.util.lerp(buttonSizePx / targetHeightPx, 1.0f, clamped)
                        translationX = (targetEndPaddingPx - btnRightMarginPx) * (1f - clamped)
                        translationY = (targetBottomPaddingPx - btnBottomMarginPx) * (1f - clamped)
                        transformOrigin = TransformOrigin(1f, 1f)
                    }
            ) {
                InfuseGlassPanel(
                    shape = RoundedCornerShape(panelCornerRadius),
                    backdropImageBitmap = staticPanelBlurImageBitmap,
                    backdropVideoBitmap = staticPanelBlurBitmap ?: seekPreviewBitmap,
                    backdropPositionInRoot = { hudBackdropPositionInRoot },
                    backdropSize = { hudBackdropSize },
                    modifier = Modifier
                        .size(width = targetPanelWidth, height = targetPanelHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consume taps inside the morphing panel */ }
                        .testTag("infuse_floating_settings_card")
                ) {
                    // Flat 2D Gear Icon fading smoothly without 3D rotation, tilt, or double-scaling
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val clamped = settingsMorphProgress.coerceIn(0f, 1f)
                                alpha = (1f - (clamped / 0.35f)).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Expanded Playback Settings Menu Content with clean 2D opacity transition
                    val settingsScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val clamped = settingsMorphProgress.coerceIn(0f, 1f)
                                alpha = ((clamped - 0.16f) / 0.68f).coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .verticalScroll(settingsScrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                            AnimatedContent(
                                targetState = activeSubMenu,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(140))
                                        .togetherWith(fadeOut(animationSpec = tween(100)))
                                },
                                label = "InfuseSettingsSubMenuTransition"
                            ) { subMenuState ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    when (subMenuState) {
                                        "Main" -> {
                                            // Compact Infuse Header with Liquid Glass Close Button
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 6.dp, end = 2.dp, top = 1.dp, bottom = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Playback Settings",
                                                    color = Color.White.copy(alpha = 0.62f),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.2.sp
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                                                        .border(
                                                            width = 0.5.dp,
                                                            color = Color.White.copy(alpha = 0.22f),
                                                            shape = CircleShape
                                                        )
                                                        .clickable { showInfuseSettingsMenu = false }
                                                        .testTag("player_settings_close_button"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close Playback Settings",
                                                        tint = Color.White.copy(alpha = 0.88f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }

                                            // 1. Volume Boost
                                            InfuseMenuRow(
                                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                                title = "Volume Boost",
                                                value = if (volumeBoostFactor == 1.0f) "Off" else "${(volumeBoostFactor * 100).toInt()}%",
                                                onClick = { activeSubMenu = "VolumeBoost" }
                                            )

                                            // 2. Playback Speed
                                            InfuseMenuRow(
                                                icon = Icons.Default.Speed,
                                                title = "Playback Speed",
                                                value = if (speed == 1.0f) "1.0x" else "${speed}x",
                                                onClick = { activeSubMenu = "Speed" }
                                            )

                                            // 3. Audio Track Selection
                                            InfuseMenuRow(
                                                icon = Icons.Default.Audiotrack,
                                                title = "Audio Track",
                                                value = selectedAudioTrack.substringBefore(" •").take(14),
                                                onClick = { activeSubMenu = "Audio" }
                                            )

                                            // 4. Subtitles
                                            InfuseMenuRow(
                                                icon = Icons.Default.Subtitles,
                                                title = "Subtitles",
                                                value = if (selectedSubtitleTrack == "Off" || selectedSubtitleTrack == "Off / None") "Off" else selectedSubtitleTrack.take(12),
                                                onClick = { activeSubMenu = "Subtitles" }
                                            )

                                            // 5. Advanced Options
                                            InfuseMenuRow(
                                                icon = Icons.Default.Settings,
                                                title = "Advanced Options",
                                                value = "",
                                                onClick = { activeSubMenu = "Advanced" }
                                            )
                                        }

                                        "VolumeBoost" -> {
                                            SubMenuHeader(
                                                title = "Volume Boost",
                                                onBack = { activeSubMenu = "Main" },
                                                onClose = { showInfuseSettingsMenu = false }
                                            )
                                            ScrollableOptionsContainer {
                                                listOf(
                                                    1.0f to "Off (100%)",
                                                    1.25f to "125% Boost",
                                                    1.50f to "150% Boost",
                                                    1.75f to "175% Boost",
                                                    2.0f to "200% Max Boost (+12 dB)"
                                                ).forEach { (factor, label) ->
                                                    val isSel = volumeBoostFactor == factor
                                                    SubMenuOptionItem(
                                                        label = label,
                                                        isSelected = isSel,
                                                        primaryColor = primaryColor,
                                                        onClick = {
                                                            volumeBoostFactor = factor
                                                            val audioSessionId = if (activeAudioSessionId != 0) activeAudioSessionId else (exoPlayerRef?.audioSessionId ?: mediaPlayerRef?.audioSessionId ?: 0)
                                                            com.example.util.VolumeBoostManager.toggleVolumeBoost(audioSessionId, factor > 1.0f)
                                                            toastMessage = "Volume Boost: $label"
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        "Speed" -> {
                                            SubMenuHeader(
                                                title = "Playback Speed",
                                                onBack = { activeSubMenu = "Main" },
                                                onClose = { showInfuseSettingsMenu = false }
                                            )
                                            ScrollableOptionsContainer {
                                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                                                    val isSel = speed == s
                                                    val label = if (s == 1.0f) "1.0x (Normal)" else "${s}x"
                                                    SubMenuOptionItem(
                                                        label = label,
                                                        isSelected = isSel,
                                                        primaryColor = primaryColor,
                                                        onClick = {
                                                            viewModel.setPlaybackSpeed(s)
                                                            activeSubMenu = "Main"
                                                            toastMessage = "Speed: ${s}x"
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        "Audio" -> {
                                            SubMenuHeader(
                                                title = "Audio Track",
                                                onBack = { activeSubMenu = "Main" },
                                                onClose = { showInfuseSettingsMenu = false }
                                            )
                                            ScrollableOptionsContainer {
                                                audioTracks.forEachIndexed { trkIndex, trk ->
                                                    val isSel = selectedAudioTrack == trk
                                                    SubMenuOptionItem(
                                                        label = trk,
                                                        isSelected = isSel,
                                                        primaryColor = primaryColor,
                                                        onClick = {
                                                            selectedAudioTrack = trk
                                                            val exo = exoPlayerRef
                                                            if (exo != null) {
                                                                try {
                                                                    val currentTracks = exo.currentTracks
                                                                    var count = 0
                                                                    var found = false
                                                                    for (group in currentTracks.groups) {
                                                                        if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                                                            val trackGroup = group.mediaTrackGroup
                                                                            for (i in 0 until trackGroup.length) {
                                                                                if (count == trkIndex) {
                                                                                    exo.trackSelectionParameters = exo.trackSelectionParameters
                                                                                        .buildUpon()
                                                                                        .setOverrideForType(androidx.media3.common.TrackSelectionOverride(trackGroup, i))
                                                                                        .build()
                                                                                    found = true
                                                                                    break
                                                                                }
                                                                                count++
                                                                            }
                                                                        }
                                                                        if (found) break
                                                                    }
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                }
                                                            }
                                                            val mp = mediaPlayerRef
                                                            if (mp != null) {
                                                                try {
                                                                    val trackInfoList = mp.trackInfo
                                                                    if (trackInfoList != null) {
                                                                        var audioCount = 0
                                                                        for (idx in trackInfoList.indices) {
                                                                            if (trackInfoList[idx].trackType == android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                                                                                if (audioCount == trkIndex) {
                                                                                    mp.selectTrack(idx)
                                                                                    break
                                                                                }
                                                                                audioCount++
                                                                            }
                                                                        }
                                                                    }
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                }
                                                            }
                                                            activeSubMenu = "Main"
                                                            toastMessage = "Audio Track: ${trk.substringBefore(" •")}"
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        "Subtitles" -> {
                                            SubMenuHeader(
                                                title = "Subtitles",
                                                onBack = { activeSubMenu = "Main" },
                                                onClose = { showInfuseSettingsMenu = false }
                                            )
                                            ScrollableOptionsContainer {
                                                subtitleTracks.forEach { sub ->
                                                    val isSel = selectedSubtitleTrack == sub
                                                    SubMenuOptionItem(
                                                        label = if (sub == "Off") "Off (Disabled)" else sub,
                                                        isSelected = isSel,
                                                        primaryColor = primaryColor,
                                                        onClick = {
                                                            selectedSubtitleTrack = sub
                                                            val exo = exoPlayerRef
                                                            if (exo != null) {
                                                                try {
                                                                    val isDisabled = (sub == "Off" || sub == "Off / None" || sub == "None")
                                                                    exo.trackSelectionParameters = exo.trackSelectionParameters
                                                                        .buildUpon()
                                                                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, isDisabled)
                                                                        .build()
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                }
                                                            }
                                                            toastMessage = if (sub == "Off") "Subtitles: Disabled" else "Subtitles: $sub"
                                                        }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Surface(
                                                    color = primaryColor.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            showInfuseSettingsMenu = false
                                                            showSubtitleModal = true
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Tune,
                                                            contentDescription = null,
                                                            tint = primaryColor,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Subtitle Style & Sync Options",
                                                            color = primaryColor,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        "Advanced" -> {
                                            SubMenuHeader(
                                                title = "Advanced Options",
                                                onBack = { activeSubMenu = "Main" },
                                                onClose = { showInfuseSettingsMenu = false }
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Aspect Ratio", color = Color.White, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    AspectRatioMode.entries.forEach { mode ->
                                                        val isSel = aspectRatioMode == mode
                                                        Surface(
                                                            color = if (isSel) primaryColor else Color.White.copy(alpha = 0.08f),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.clickable { setAspectRatioMode(mode) }
                                                        ) {
                                                            Text(
                                                                text = mode.displayName,
                                                                color = if (isSel) Color.Black else Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Seek Duration", color = Color.White, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    com.example.util.SeekPreferencesRepository.ALLOWED_SEEK_DURATIONS.forEach { seconds ->
                                                        val isSel = seekDurationSeconds == seconds
                                                        Surface(
                                                            color = if (isSel) primaryColor else Color.White.copy(alpha = 0.08f),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.clickable {
                                                                viewModel.setSeekDurationSeconds(seconds)
                                                                toastMessage = "Seek Duration: ${seconds}s"
                                                            }
                                                        ) {
                                                            Text(
                                                                text = "${seconds}s",
                                                                color = if (isSel) Color.Black else Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Continuous Seek Mode", color = Color.White, fontSize = 13.sp)
                                                Switch(
                                                    checked = continuousSeekEnabled,
                                                    onCheckedChange = {
                                                        viewModel.setContinuousSeekEnabled(it)
                                                        toastMessage = if (it) "Continuous Seek: ON" else "Continuous Seek: OFF"
                                                    },
                                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Loop Video", color = Color.White, fontSize = 13.sp)
                                                Switch(
                                                    checked = isLoopingEnabled,
                                                    onCheckedChange = {
                                                        isLoopingEnabled = it
                                                        mediaPlayerRef?.isLooping = it
                                                    },
                                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = primaryColor)
                                                )
                                            }

                                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

                                            InfuseMenuRow(
                                                icon = Icons.Default.BugReport,
                                                title = "Seek Diagnostics",
                                                value = "Logs",
                                                onClick = {
                                                    showInfuseSettingsMenu = false
                                                    showSeekDiagnosticsDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    if (showEpisodeDrawer || playlistMorphProgress > 0.004f) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val containerWidthDp = configuration.screenWidthDp.dp
        val containerHeightDp = configuration.screenHeightDp.dp

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {

            // Compute exact start offsets from the Playlist button's measured position in root
            val buttonRightMarginDp = remember(
                playlistButtonPosInRoot,
                playlistButtonSizePx,
                hudBackdropPositionInRoot,
                hudBackdropSize,
                density
            ) {
                if (hudBackdropSize.width > 0 && playlistButtonSizePx.width > 0) {
                    val rootRight = hudBackdropPositionInRoot.x + hudBackdropSize.width
                    val btnRight = playlistButtonPosInRoot.x + playlistButtonSizePx.width
                    with(density) { (rootRight - btnRight).coerceAtLeast(12f).toDp() }
                } else {
                    78.dp
                }
            }
            val buttonBottomMarginDp = remember(
                playlistButtonPosInRoot,
                playlistButtonSizePx,
                hudBackdropPositionInRoot,
                hudBackdropSize,
                density
            ) {
                if (hudBackdropSize.height > 0 && playlistButtonSizePx.height > 0) {
                    val rootBottom = hudBackdropPositionInRoot.y + hudBackdropSize.height
                    val btnBottom = playlistButtonPosInRoot.y + playlistButtonSizePx.height
                    with(density) { (rootBottom - btnBottom).coerceAtLeast(16f).toDp() }
                } else {
                    54.dp
                }
            }

            // Responsive compact panel dimensions matching iOS reference image proportions
            val targetPanelWidth = (containerWidthDp - 32.dp).coerceIn(230.dp, 255.dp)
            val queueItemCount = currentQueue.size.coerceAtLeast(1)
            val estimatedQueueContentHeight = (88 + (queueItemCount * 44)).dp
            val maxAllowedPanelHeight = (containerHeightDp - buttonBottomMarginDp - 16.dp).coerceAtLeast(130.dp)
            val targetPanelHeight = estimatedQueueContentHeight.coerceIn(125.dp, 215.dp).coerceAtMost(maxAllowedPanelHeight)

            val targetEndPadding = buttonRightMarginDp.coerceIn(16.dp, (containerWidthDp - targetPanelWidth - 12.dp).coerceAtLeast(16.dp))
            val targetBottomPadding = buttonBottomMarginDp.coerceIn(16.dp, (containerHeightDp - targetPanelHeight - 12.dp).coerceAtLeast(16.dp))

            val btnRightMarginPx = with(density) { buttonRightMarginDp.toPx() }
            val btnBottomMarginPx = with(density) { buttonBottomMarginDp.toPx() }
            val targetEndPaddingPx = with(density) { targetEndPadding.toPx() }
            val targetBottomPaddingPx = with(density) { targetBottomPadding.toPx() }
            val targetWidthPx = with(density) { targetPanelWidth.toPx() }
            val targetHeightPx = with(density) { targetPanelHeight.toPx() }
            val buttonSizePx = with(density) { 44.dp.toPx() }

            val panelCornerRadius = androidx.compose.ui.unit.lerp(
                targetPanelWidth.coerceAtLeast(targetPanelHeight) / 2,
                20.dp,
                playlistMorphProgress.coerceIn(0f, 1f)
            )

            Box(
                modifier = Modifier
                    .padding(end = targetEndPadding, bottom = targetBottomPadding)
                    .graphicsLayer {
                        val clamped = playlistMorphProgress.coerceIn(0f, 1f)

                        // Pure 2D flat scale and translation without perspective, overshoot, or bounciness
                        scaleX = androidx.compose.ui.util.lerp(buttonSizePx / targetWidthPx, 1.0f, clamped)
                        scaleY = androidx.compose.ui.util.lerp(buttonSizePx / targetHeightPx, 1.0f, clamped)
                        translationX = (targetEndPaddingPx - btnRightMarginPx) * (1f - clamped)
                        translationY = (targetBottomPaddingPx - btnBottomMarginPx) * (1f - clamped)
                        transformOrigin = TransformOrigin(1f, 1f)
                    }
            ) {
                InfuseGlassPanel(
                    shape = RoundedCornerShape(panelCornerRadius),
                    backdropImageBitmap = staticPanelBlurImageBitmap,
                    backdropVideoBitmap = staticPanelBlurBitmap ?: seekPreviewBitmap,
                    backdropPositionInRoot = { hudBackdropPositionInRoot },
                    backdropSize = { hudBackdropSize },
                    modifier = Modifier
                        .size(width = targetPanelWidth, height = targetPanelHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consume taps inside panel */ }
                        .testTag("infuse_floating_playlist_card")
                ) {
                    // Flat 2D Playlist Icon fading smoothly without 3D rotation, tilt, or double-scaling
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val clamped = playlistMorphProgress.coerceIn(0f, 1f)
                                alpha = (1f - (clamped / 0.35f)).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Expanded Episode Queue Panel Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val clamped = playlistMorphProgress.coerceIn(0f, 1f)
                                alpha = ((clamped - 0.16f) / 0.68f).coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = "Playback Queue",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    )
                                    if (currentQueue.isNotEmpty()) {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.12f),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "${currentQueue.size}",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                                        .border(
                                            width = 0.5.dp,
                                            color = Color.White.copy(alpha = 0.22f),
                                            shape = CircleShape
                                        )
                                        .clickable { showEpisodeDrawer = false }
                                        .testTag("player_drawer_close_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Queue",
                                        tint = Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

                            // Episode List
                            var selectedDrawerEpId by remember(currentEpisode.id) { mutableStateOf(currentEpisode.id) }

                            if (currentQueue.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No episodes in playback queue",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.5.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(currentQueue, key = { it.id }) { ep ->
                                        val isSelected = ep.id == selectedDrawerEpId
                                        val isCurrent = ep.id == currentEpisode.id
                                        val s = ep.seasonNumber
                                        val e = ep.episodeNumber
                                        val seriesName = ep.showTitle
                                        val cleanEpTitle = remember(ep.id, ep.episodeTitle, ep.mediaUrl, seriesName) {
                                            var title = com.example.util.EpisodeUtils.cleanEpisodeTitle(ep.episodeTitle)
                                            if (title.isBlank()) {
                                                title = com.example.util.EpisodeUtils.cleanEpisodeTitle(ep.mediaUrl)
                                            }
                                            if (seriesName.isNotBlank() && title.lowercase().startsWith(seriesName.lowercase())) {
                                                title = title.substring(seriesName.length).trim()
                                            }
                                            title = title.removePrefix("-").removePrefix("•").removePrefix(":").removePrefix("–").trim()
                                            if (title.isBlank() || title.equals(seriesName, ignoreCase = true) || title.matches(Regex("(?i)s\\d+\\s*[•\\-]?\\s*e\\d+"))) {
                                                title = "Episode $e"
                                            }
                                            title
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isCurrent) Color.White.copy(alpha = 0.12f)
                                                    else if (isSelected) Color.White.copy(alpha = 0.06f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    selectedDrawerEpId = ep.id
                                                    showAndResetTimer()
                                                    val currentExo = exoPlayerRef
                                                    if (currentPlayingEpId != ep.id) {
                                                        if (currentExo != null) {
                                                            latestSavePlaybackPosition(currentExo)
                                                        }
                                                        currentPlayingEpId = ep.id
                                                        viewModel.selectEpisode(ep.id)
                                                        viewModel.setPlaying(true)
                                                    } else {
                                                        currentExo?.let { exo ->
                                                            exo.playWhenReady = true
                                                            exo.play()
                                                        }
                                                        viewModel.setPlaying(true)
                                                    }
                                                    showEpisodeDrawer = false
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                .testTag("player_drawer_episode_item_${ep.id}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Currently Playing",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .testTag("player_current_episode_checkmark")
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }

                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "S$s · E$e - $cleanEpTitle",
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (seriesName.isNotBlank()) seriesName else "Marvel's Daredevil",
                                                    color = Color.White.copy(alpha = 0.55f),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    selectedDrawerEpId = ep.id
                                                    showAndResetTimer()
                                                    val currentExo = exoPlayerRef
                                                    if (currentPlayingEpId != ep.id) {
                                                        if (currentExo != null) {
                                                            latestSavePlaybackPosition(currentExo)
                                                        }
                                                        currentPlayingEpId = ep.id
                                                        viewModel.selectEpisode(ep.id)
                                                        viewModel.setPlaying(true)
                                                    }
                                                    showEpisodeDrawer = false
                                                },
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .testTag("player_drawer_play_button_${ep.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play Selected Episode",
                                                    tint = Color.White.copy(alpha = 0.75f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

                            // Edit Playback Queue action
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        toastMessage = "Playback Queue (${currentQueue.size} items)"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = "Edit Playback Queue",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Edit Playback Queue",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // LIVE SUBTITLE OVERLAY (INFUSE GLASS / iOS STYLE OUTLINE SUBTITLES)

        // 1. Native SubtitleView for embedded/native text tracks
        val nativeFgColor = when (globalSubtitleColorStr.lowercase()) {
            "yellow" -> android.graphics.Color.parseColor("#FACC15")
            "cyan" -> android.graphics.Color.parseColor("#22D3EE")
            "green" -> android.graphics.Color.parseColor("#4ADE80")
            "magenta" -> android.graphics.Color.parseColor("#E879F9")
            else -> android.graphics.Color.WHITE
        }

        val subtitleBottomPadding by animateDpAsState(
            targetValue = if (areControlsVisible) 110.dp else 40.dp,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "subtitle_bottom_padding"
        )

        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.SubtitleView(ctx).apply {
                    val style = androidx.media3.ui.CaptionStyleCompat(
                        nativeFgColor,                                 // foregroundColor
                        android.graphics.Color.TRANSPARENT,            // backgroundColor
                        android.graphics.Color.TRANSPARENT,            // windowColor
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, // edgeType
                        android.graphics.Color.BLACK,                  // edgeColor
                        null                                           // typeface
                    )
                    setStyle(style)
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleFontSizeSp.toFloat())
                }
            },
            update = { subtitleView ->
                if (selectedSubtitleTrack != "Off" && selectedSubtitleTrack != "External Subtitle") {
                    subtitleView.setCues(nativeCues)
                } else {
                    subtitleView.setCues(emptyList())
                }
                val style = androidx.media3.ui.CaptionStyleCompat(
                    nativeFgColor,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
                )
                subtitleView.setStyle(style)
                subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleFontSizeSp.toFloat())
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = subtitleBottomPadding)
                .padding(horizontal = 32.dp)
        )

        // 2. Custom Compose Subtitle Overlay (with delay adjustment support) for custom external srt/vtt files
        val currentPosMs = remember(progress, actualDurationMs) {
            val totalMs = actualDurationMs ?: (11520 * 1000L)
            (progress * totalMs).toLong()
        }

        val activeCue = remember(currentPosMs, parsedCues, subtitleDelayMs, selectedSubtitleTrack) {
            if (selectedSubtitleTrack != "External Subtitle") null
            else {
                val targetMs = currentPosMs + subtitleDelayMs
                parsedCues.firstOrNull { targetMs in it.startMs..it.endMs }
            }
        }

        if (activeCue != null) {
            val subtitleColor = when (globalSubtitleColorStr.lowercase()) {
                "yellow" -> Color(0xFFFACC15)
                "cyan" -> Color(0xFF22D3EE)
                "green" -> Color(0xFF4ADE80)
                "magenta" -> Color(0xFFE879F9)
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = subtitleBottomPadding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Infuse Glass Style with subtle outline / shadow
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Text(
                        text = activeCue.text,
                        color = subtitleColor,
                        fontSize = subtitleFontSizeSp.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = (subtitleFontSizeSp + 6).sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (isScrubbing && !isUserDragging) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                FrostedGlassContainer(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val secondsDelta = (scrubDelta * 11520).toInt()
                        val sign = if (secondsDelta >= 0) "+" else ""
                        Icon(
                            imageVector = if (secondsDelta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = "Scrubbing",
                            tint = primaryColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Seek: $sign${secondsDelta}s",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = elapsedTime,
                            color = primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // iOS-Style (Infuse-like) Top-Center Volume & Brightness Liquid Glass HUD Overlay
        AnimatedVisibility(
            visible = hudOverlayVisible && !isInPipMode,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        ) {
            val isVolume = hudType == "volume"
            val percentVal = if (isVolume) {
                Math.round(volume * 100f).toInt().coerceIn(0, 100)
            } else {
                Math.round(brightness * 100f).toInt().coerceIn(0, 100)
            }

            PlayerLiquidGlassHudPill(
                testTag = "player_ios_hud_pill",
                backdropGraphicsLayer = if (areControlsVisible || isNextEpisodeOnlyVisible) hudBackdropGraphicsLayer else null,
                loadingBackdropGraphicsLayer = if (!isVideoReady) hudLoadingBackdropGraphicsLayer else null,
                backdropImageProvider = { hudBackdropImageState.value ?: seekPreviewImageBitmap },
                backdropPositionInRoot = { hudBackdropPositionInRoot },
                backdropSize = { hudBackdropSize },
                reflectionSeed = if (isVolume) volume else brightness
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .align(Alignment.Center)
                ) {
                    // Dynamic live icon
                    Icon(
                        imageVector = if (isVolume) {
                            when {
                                percentVal <= 0 -> Icons.AutoMirrored.Filled.VolumeMute
                                percentVal < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            }
                        } else {
                            when {
                                percentVal < 35 -> Icons.Default.BrightnessLow
                                percentVal < 70 -> Icons.Default.BrightnessMedium
                                else -> Icons.Default.BrightnessHigh
                            }
                        },
                        contentDescription = if (isVolume) "Volume HUD" else "Brightness HUD",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )

                    // Live percentage text
                    Text(
                        text = "$percentVal%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }

        // Left Double Tap Seek (-5s / -10s / -30s...) YouTube-style Visual Indicator
        AnimatedVisibility(
            visible = showLeftSeekRipple && !isInPipMode,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            YouTubeSeekOverlay(
                isForward = false,
                seekSeconds = if (leftSeekSeconds > 0) leftSeekSeconds else seekDurationSeconds,
                triggerKey = leftSeekTriggerKey
            )
        }

        // Right Double Tap Seek (+5s / +10s / +30s...) YouTube-style Visual Indicator
        AnimatedVisibility(
            visible = showRightSeekRipple && !isInPipMode,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            YouTubeSeekOverlay(
                isForward = true,
                seekSeconds = if (rightSeekSeconds > 0) rightSeekSeconds else seekDurationSeconds,
                triggerKey = rightSeekTriggerKey
            )
        }

        // Native Gesture Paused Center Overlay (Double-tap Native Center Zone)
        AnimatedVisibility(
            visible = isNativeGesturePaused && !isPlaying && !isInPipMode,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            FrostedGlassContainer(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Paused",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }

        // Horizontal Slide-to-Seek (Scrubbing) YouTube-style Floating Preview Card
        AnimatedVisibility(
            visible = isHorizontalScrubbing && !isInPipMode,
            enter = fadeIn(animationSpec = tween(120)) + scaleIn(initialScale = 0.9f, animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val totalDur = actualDurationMs ?: (exoPlayerRef?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 11520000L)
            HorizontalScrubOverlay(
                targetTimeMs = scrubTargetPosMs,
                totalDurationMs = totalDur,
                deltaMs = scrubDeltaMs,
                progressFraction = scrubTargetProgress,
                primaryColor = primaryColor
            )
        }

        // Centered Toast Overlay for feedback (Speed, Aspect ratio, double tap seek)
        AnimatedVisibility(
            visible = toastMessage.isNotEmpty() && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            FrostedGlassContainer(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = toastMessage,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        // Active 2X Speed pill overlay - shown ONLY during long press 2x gesture
        // Matches the exact iOS-Style Top-Center Volume & Brightness Liquid Glass HUD Pill
        val isLongPress2xActive = isHolding2x
        AnimatedVisibility(
            visible = isLongPress2xActive && !isInPipMode,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        ) {
            PlayerLiquidGlassHudPill(
                testTag = "player_2x_speed_hud_pill",
                backdropGraphicsLayer = if (areControlsVisible || isNextEpisodeOnlyVisible) hudBackdropGraphicsLayer else null,
                loadingBackdropGraphicsLayer = if (!isVideoReady) hudLoadingBackdropGraphicsLayer else null,
                backdropImageProvider = { hudBackdropImageState.value ?: seekPreviewImageBitmap },
                backdropPositionInRoot = { hudBackdropPositionInRoot },
                backdropSize = { hudBackdropSize },
                reflectionSeed = 0.65f
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .align(Alignment.Center)
                ) {
                    Text(
                        text = "2×",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )

                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "2x Speed HUD",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }


        // Dedicated Playback Speed Menu Overlay
        if (showSpeedMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showSpeedMenu = false },
                contentAlignment = Alignment.Center
            ) {
                FrostedGlassContainer(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(24.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SlowMotionVideo,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Playback Speed",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showSpeedMenu = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.12f),
                                                Color.Black.copy(alpha = 0.38f)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 0.85.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.25f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close speed menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Column(
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                                val isSelected = (speed == s)
                                val label = if (s == 1.0f) "1.0x (Normal)" else "${s}x"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) primaryColor.copy(alpha = 0.18f)
                                            else Color.White.copy(alpha = 0.04f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) primaryColor.copy(alpha = 0.5f)
                                            else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.setPlaybackSpeed(s)
                                            toastMessage = "Speed: $label"
                                            showSpeedMenu = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) primaryColor else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = primaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitles Options & Sync Modal
        if (showSubtitleModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showSubtitleModal = false },
                contentAlignment = Alignment.Center
            ) {
                FrostedGlassContainer(
                    modifier = Modifier
                        .width(440.dp)
                        .padding(20.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Subtitle Options & Sync",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showSubtitleModal = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.12f),
                                                Color.Black.copy(alpha = 0.38f)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 0.85.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.25f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        // Subtitle Track Selection
                        Text(
                            text = "Subtitle Track",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subtitleTracks.forEach { track ->
                                val isSel = track == selectedSubtitleTrack
                                Surface(
                                    color = if (isSel) primaryColor else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isSel) primaryColor else Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.clickable {
                                        selectedSubtitleTrack = track
                                        toastMessage = "Subtitle: $track"
                                        val exo = exoPlayerRef
                                        if (exo != null) {
                                            try {
                                                if (track == "Off" || track == "Off / None" || track == "None") {
                                                    exo.trackSelectionParameters = exo.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                                        .build()
                                                } else {
                                                    val matched = detectedTextTracks.firstOrNull { it.third == track }
                                                    if (matched != null) {
                                                        val override = androidx.media3.common.TrackSelectionOverride(matched.first.mediaTrackGroup, matched.second)
                                                        exo.trackSelectionParameters = exo.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                                            .setOverrideForType(override)
                                                            .build()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = track,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Load External Subtitle Section
                        Text(
                            text = "Load External Subtitle (SRT or VTT URL)",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val coroutineScope = rememberCoroutineScope()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customSubtitleUrl,
                                onValueChange = { customSubtitleUrl = it },
                                placeholder = { Text("https://example.com/sub.srt", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            val isUrlValid = customSubtitleUrl.isNotBlank() && (customSubtitleUrl.startsWith("http://", true) || customSubtitleUrl.startsWith("https://", true))
                            Button(
                                onClick = {
                                    if (isUrlValid) {
                                        coroutineScope.launch {
                                            toastMessage = "Downloading subtitles..."
                                            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    java.net.URL(customSubtitleUrl).openStream().use { input ->
                                                        input.readBytes()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    null
                                                }
                                            }
                                            if (bytes != null && bytes.isNotEmpty()) {
                                                rawSubtitleBytes = bytes
                                                val parsed = parseSubtitleBytesWithEncoding(bytes, globalSubtitleEncoding)
                                                if (parsed.isNotEmpty()) {
                                                    customSubtitleCues = parsed
                                                    if (!subtitleTracks.contains("External Subtitle")) {
                                                        subtitleTracks = subtitleTracks + "External Subtitle"
                                                    }
                                                    selectedSubtitleTrack = "External Subtitle"
                                                    toastMessage = "Loaded ${parsed.size} dialogue subtitles successfully!"
                                                } else {
                                                    toastMessage = "Error: Found no dialogue lines. Please check the format."
                                                }
                                            } else {
                                                toastMessage = "Failed to download subtitle file."
                                            }
                                        }
                                    } else {
                                        toastMessage = "Please enter a valid HTTP/HTTPS URL."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isUrlValid) primaryColor else Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Load", color = if (isUrlValid) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Quick demo buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Preset Demo Subtitle:", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    customSubtitleUrl = "https://raw.githubusercontent.com/Anis9/test-files/master/sample.srt"
                                    toastMessage = "Preset Sample SRT URL selected! Press Load."
                                }
                            ) {
                                Text("Sample SRT", color = primaryColor, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    customSubtitleUrl = "https://raw.githubusercontent.com/Anis9/test-files/master/sample.vtt"
                                    toastMessage = "Preset Sample VTT URL selected! Press Load."
                                }
                            ) {
                                Text("Sample VTT", color = primaryColor, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }

                        // Subtitle Sync / Delay Controls
                        Text(
                            text = "Subtitle Sync Delay: ${(subtitleDelayMs / 1000f)}s",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { subtitleDelayMs -= 500L },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-0.5s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { subtitleDelayMs = 0L },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { subtitleDelayMs += 500L },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+0.5s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Subtitle Font Size Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Subtitle Font Size",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.decreaseSubtitleFontSize()
                                        subtitleFontSizeSp = (subtitleFontSizeSp - 1).coerceAtLeast(8)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = "$subtitleFontSizeSp sp",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                IconButton(
                                    onClick = {
                                        viewModel.increaseSubtitleFontSize()
                                        subtitleFontSizeSp = (subtitleFontSizeSp + 1).coerceAtMost(48)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mapOf("Small" to 14, "Normal" to 18, "Large" to 22, "Extra Large" to 26).forEach { (label, sz) ->
                                val isSel = subtitleFontSizeSp == sz
                                Surface(
                                    color = if (isSel) primaryColor else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isSel) primaryColor else Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            subtitleFontSizeSp = sz
                                            viewModel.setSubtitleFontSize(sz)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSel) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Subtitle Color Selection
                        Text(
                            text = "Subtitle Color",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "White" to Color.White,
                                "Yellow" to Color(0xFFFACC15),
                                "Cyan" to Color(0xFF22D3EE),
                                "Green" to Color(0xFF4ADE80),
                                "Magenta" to Color(0xFFE879F9)
                            ).forEach { (name, col) ->
                                val isSel = globalSubtitleColorStr.equals(name, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSel) 2.5.dp else 1.dp,
                                            color = if (isSel) primaryColor else Color.White.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setSubtitleColor(name) }
                                        .padding(if (isSel) 4.dp else 2.dp)
                                        .background(col, CircleShape)
                                )
                            }
                        }

                        // Subtitle Encoding Selection
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .clickable { showModalEncodingDropdown = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Text Encoding",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = globalSubtitleEncoding,
                                        color = primaryColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        "Options",
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val modalEncodingList = listOf(
                                "UTF-8 (Auto)",
                                "UTF-16",
                                "ISO-8859-1 (Latin-1)",
                                "Windows-1252 (ANSI / Western)",
                                "Windows-1256 (Arabic)",
                                "GBK / GB18030 (Simplified Chinese)",
                                "Big5 (Traditional Chinese)",
                                "Shift-JIS (Japanese)",
                                "EUC-KR (Korean)",
                                "Windows-1251 (Cyrillic)",
                                "ISO-8859-6 (Arabic)",
                                "ISO-8859-9 (Turkish)"
                            )

                            DropdownMenu(
                                expanded = showModalEncodingDropdown,
                                onDismissRequest = { showModalEncodingDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF1C1C1E))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            ) {
                                modalEncodingList.forEach { enc ->
                                    val isCurrent = globalSubtitleEncoding == enc
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = enc,
                                                    color = if (isCurrent) primaryColor else Color.White,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSubtitleEncoding(enc)
                                            showModalEncodingDropdown = false
                                            toastMessage = "Encoding set to: $enc"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Detailed 10-Bit Codec Diagnostic Dialog on Playback Failure
        diagnosticErrorInfo?.let { diag ->
            AlertDialog(
                onDismissRequest = { diagnosticErrorInfo = null },
                shape = RoundedCornerShape(18.dp),
                containerColor = Color(0xFF1C1C1E),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = diag.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = diag.errorMessage,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                        DiagnosticRow("Error Code", diag.errorCodeName)
                        DiagnosticRow("MIME Type", diag.mimeType)
                        DiagnosticRow("Active Codec", diag.codecName)
                        DiagnosticRow("Decoder Type", diag.decoderType)
                        DiagnosticRow("Bit Depth", diag.bitDepth)
                        DiagnosticRow("Profile", diag.profile)
                        DiagnosticRow("Level", diag.level)
                        DiagnosticRow("Resolution", diag.resolution)
                        DiagnosticRow("Color Space", diag.colorSpace)
                        DiagnosticRow("HDR Format", diag.hdrType)
                        DiagnosticRow("Fallback Active", if (diag.fallbackTriggered) "Yes" else "No")
                        if (diag.fallbackTriggered) {
                            DiagnosticRow("Fallback Reason", diag.fallbackReason)
                        }
                        DiagnosticRow("Media3 Engine", diag.media3Version)

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Device Decoders (${diag.mimeType}):",
                            color = primaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        diag.deviceDecoders.forEach { dec ->
                            Text(
                                text = "• $dec",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val streamKey = currentEpisode.id.ifBlank { currentEpisode.mediaUrl }
                            val failedCodec = diagnosticErrorInfo?.codecName ?: com.example.util.MediaCodec10BitHelper.lastSelectedDecoderName ?: "Hardware Decoder"
                            com.example.util.MediaCodec10BitHelper.markHardwareDecoderFailed(failedCodec, streamKey)
                            diagnosticErrorInfo = null
                            exoPlayerRef?.let { exo ->
                                val resumePos = if (positionStateMs > 0L) positionStateMs else exo.currentPosition.coerceAtLeast(0L)
                                val item = exo.currentMediaItem ?: androidx.media3.common.MediaItem.fromUri(currentEpisode.mediaUrl)
                                exo.stop()
                                if (resumePos > 0L) {
                                    exo.setMediaItem(item, resumePos)
                                } else {
                                    exo.setMediaItem(item)
                                }
                                exo.prepare()
                                exo.playWhenReady = true
                                exo.play()
                                viewModel.setPlaying(true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Retry Playback", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { diagnosticErrorInfo = null }) {
                        Text("Dismiss", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Seek Diagnostics In-App Debug Dialog
        if (showSeekDiagnosticsDialog) {
            SeekDiagnosticsDialog(
                onDismiss = { showSeekDiagnosticsDialog = false }
            )
        }
    }
}

@Composable
fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.88f
            isHovered -> 1.08f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "player_icon_scale"
    )

    val bgAlpha = when {
        isPressed -> 0.45f
        isHovered -> 0.38f
        else -> 0.30f
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                Color.Black.copy(alpha = bgAlpha),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun formatTime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

@Composable
private fun InfuseMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SubMenuHeader(
    title: String,
    onBack: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onClose)
                        .testTag("player_settings_close_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Playback Settings",
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
    }
}

@Composable
private fun SubMenuOptionItem(
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSelected) primaryColor else Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ScrollableOptionsContainer(
    maxHeight: Dp = 220.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 6.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
        if (scrollState.maxValue > 0) {
            val progress = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.35f)
                        .graphicsLayer(translationY = progress * 120f)
                        .background(Color(0xFFFF6D00).copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun YouTubeSeekOverlay(
    isForward: Boolean,
    seekSeconds: Int,
    triggerKey: Int = 0,
    modifier: Modifier = Modifier
) {
    // Semicircular curved arc overlay matching YouTube mobile player
    val arcShape = if (isForward) {
        RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
    } else {
        RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)
    }

    val density = LocalDensity.current
    val travelDistancePx = remember(density) { with(density) { 20.dp.toPx() } }
    val directionSign = if (isForward) 1f else -1f

    val animProgress = remember { Animatable(0f) }
    val totalAnimDurationMs = 700f
    val moveDurationMs = 280f
    val iOSCurve = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) }

    LaunchedEffect(triggerKey) {
        if (animProgress.value != 0f) {
            animProgress.snapTo(0f)
        }
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = totalAnimDurationMs.toInt(),
                easing = LinearEasing
            )
        )
        // Reset back to initial resting state once the sequential animation finishes
        animProgress.snapTo(0f)
    }

    val elapsedMs = animProgress.value * totalAnimDurationMs

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.48f)
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isForward) {
                        listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.18f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))
                    }
                ),
                shape = arcShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
        ) {
            // Triple sequential arrows travelling forward and fading out one after another
            Row(
                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (slot in 0 until 3) {
                    val stepIndex = if (isForward) slot else (2 - slot)
                    val arrowStartMs = when (stepIndex) {
                        0 -> 0f
                        1 -> 180f
                        else -> 380f
                    }
                    val rawProgress = if (animProgress.value > 0f && elapsedMs > arrowStartMs) {
                        ((elapsedMs - arrowStartMs) / moveDurationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val easedProgress = iOSCurve.transform(rawProgress)
                    val arrowOffset = 20.dp * (directionSign * easedProgress)
                    val arrowAlpha = 1f - easedProgress

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = arrowOffset)
                            .testTag(if (isForward) "seek_arrow_forward_$slot" else "seek_arrow_rewind_$slot")
                            .graphicsLayer {
                                alpha = arrowAlpha
                                if (!isForward) {
                                    rotationZ = 180f
                                }
                            }
                    )
                }
            }

            // Dynamic text indicator e.g. "+10s", "+20s", "-10s", "-20s"
            Text(
                text = "${if (isForward) "+" else "-"}${seekSeconds}s",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun HorizontalScrubOverlay(
    targetTimeMs: Long,
    totalDurationMs: Long,
    deltaMs: Long,
    progressFraction: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val targetTimeStr = formatTime((targetTimeMs / 1000).coerceAtLeast(0L))
    val totalTimeStr = formatTime((totalDurationMs / 1000).coerceAtLeast(1L))
    val deltaSecs = Math.abs(deltaMs / 1000)
    val deltaStr = if (deltaMs >= 0) "+${formatTime(deltaSecs)}" else "-${formatTime(deltaSecs)}"

    FrostedGlassContainer(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(min = 250.dp, max = 340.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (deltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = deltaStr,
                    color = primaryColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = targetTimeStr,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "/",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = totalTimeStr,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Preview scrub progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction.coerceIn(0.01f, 1f))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(primaryColor.copy(alpha = 0.7f), primaryColor)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun InfusePlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            // Two tall, thick vertical bars with softly rounded corners matching reference image
            val barWidth = w * 0.24f
            val barHeight = h * 0.90f
            val barGap = w * 0.19f
            val totalWidth = barWidth * 2f + barGap
            val startX = (w - totalWidth) / 2f
            val topY = (h - barHeight) / 2f
            val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.075f, w * 0.075f)

            drawRoundRect(
                color = tint,
                topLeft = Offset(startX, topY),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = corner
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(startX + barWidth + barGap, topY),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = corner
            )
        } else {
            // Bold rounded Play triangle matching Pause bar height & corner radius
            val triHeight = h * 0.82f
            val triWidth = w * 0.68f
            val leftX = (w - triWidth) / 2f + w * 0.05f
            val rightX = leftX + triWidth
            val topY = (h - triHeight) / 2f
            val bottomY = topY + triHeight
            val centerY = h / 2f
            val cornerStroke = w * 0.13f

            val playPath = Path().apply {
                moveTo(leftX + cornerStroke * 0.35f, topY + cornerStroke * 0.45f)
                lineTo(rightX - cornerStroke * 0.45f, centerY)
                lineTo(leftX + cornerStroke * 0.35f, bottomY - cornerStroke * 0.45f)
                close()
            }
            drawPath(playPath, color = tint, style = Fill)
            drawPath(
                playPath,
                color = tint,
                style = Stroke(
                    width = cornerStroke,
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Circular 30-second seek icon matching Infuse on iOS:
 * - Rewind (isForward = false): Counter-clockwise circular arc, arrowhead at 12 o'clock pointing left (backward),
 *   and thin elegant line smoothly tapering to a fine tail, with bold "30" centered inside.
 * - Forward (isForward = true): Clockwise circular arc, arrowhead at 12 o'clock pointing right (forward),
 *   and thin elegant line smoothly tapering to a fine tail, with bold "30" centered inside.
 */
@Composable
fun CurvedSeekIcon(
    isForward: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minDim = minOf(w, h)
            val center = Offset(w / 2f, h / 2f)
            val radius = minDim * 0.385f

            // Thin, elegant stroke parameters matching Infuse on iOS (not chunky or thick)
            val baseStroke = minDim * 0.060f
            val tailStroke = minDim * 0.015f

            // arrowDir: -1 for Rewind (points backward/left), +1 for Forward (points forward/right)
            val arrowDir = if (isForward) 1f else -1f

            // Arrowhead at 12 o'clock (-90°) pointing in arrowDir.
            // The body arc attaches behind the arrowhead base and sweeps around the circle,
            // leaving the opening gap in front of the arrowhead tip.
            val startAngleDeg = -90f - arrowDir * 8f
            val sweepAngleDeg = -arrowDir * 292f
            val sweepSign = -arrowDir

            val numSteps = 48
            val outerPoints = ArrayList<Offset>(numSteps + 1)
            val innerPoints = ArrayList<Offset>(numSteps + 1)

            for (i in 0..numSteps) {
                val frac = i.toFloat() / numSteps
                val angleDeg = startAngleDeg + frac * sweepAngleDeg
                val angleRad = Math.toRadians(angleDeg.toDouble())

                // Elegant taper: uniform thin stroke for the first ~35%, then smoothly tapering down to a delicate tip
                val taperFactor = if (frac < 0.35f) {
                    1f
                } else {
                    val t = (frac - 0.35f) / 0.65f
                    (0.5f * (1.0 + Math.cos(t.toDouble() * Math.PI))).toFloat()
                }
                val currentStroke = tailStroke + (baseStroke - tailStroke) * taperFactor
                val halfThickness = currentStroke / 2f

                val cosA = Math.cos(angleRad).toFloat()
                val sinA = Math.sin(angleRad).toFloat()

                outerPoints.add(Offset(center.x + (radius + halfThickness) * cosA, center.y + (radius + halfThickness) * sinA))
                innerPoints.add(Offset(center.x + (radius - halfThickness) * cosA, center.y + (radius - halfThickness) * sinA))
            }

            val arcPath = Path().apply {
                if (outerPoints.isNotEmpty()) {
                    moveTo(outerPoints[0].x, outerPoints[0].y)
                    for (i in 1 until outerPoints.size) {
                        lineTo(outerPoints[i].x, outerPoints[i].y)
                    }
                    // Delicate rounded tail cap
                    val lastOuter = outerPoints.last()
                    val lastInner = innerPoints.last()
                    val tailEndAngleRad = Math.toRadians((startAngleDeg + sweepAngleDeg).toDouble())
                    val tailDirX = -sweepSign * Math.sin(tailEndAngleRad).toFloat()
                    val tailDirY = sweepSign * Math.cos(tailEndAngleRad).toFloat()
                    val tailMid = Offset((lastOuter.x + lastInner.x) / 2f, (lastOuter.y + lastInner.y) / 2f)
                    val capTip = Offset(tailMid.x + tailDirX * (tailStroke * 0.5f), tailMid.y + tailDirY * (tailStroke * 0.5f))
                    quadraticTo(capTip.x, capTip.y, lastInner.x, lastInner.y)

                    for (i in innerPoints.size - 1 downTo 0) {
                        lineTo(innerPoints[i].x, innerPoints[i].y)
                    }
                    close()
                }
            }
            drawPath(arcPath, color = tint, style = Fill)

            // Sleek, proportional arrowhead at 12 o'clock (-90°) pointing in arrowDir
            val topX = center.x
            val topY = center.y - radius
            val arrowLen = minDim * 0.16f
            val arrowHalfWidth = minDim * 0.10f

            val tip = Offset(topX + arrowDir * arrowLen, topY)
            val baseBackX = topX - arrowDir * (baseStroke * 0.45f)
            val topWing = Offset(baseBackX, topY - arrowHalfWidth)
            val bottomWing = Offset(baseBackX, topY + arrowHalfWidth)

            val arrowPath = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(topWing.x, topWing.y)
                lineTo(baseBackX + arrowDir * (arrowLen * 0.16f), topY)
                lineTo(bottomWing.x, bottomWing.y)
                close()
            }
            drawPath(arrowPath, color = tint, style = Fill)
            drawPath(
                arrowPath,
                color = tint,
                style = Stroke(
                    width = baseStroke * 0.25f,
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
        Text(
            text = "30",
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.offset(y = 0.5.dp)
        )
    }
}

/**
 * Floating 16:9 video frame preview thumbnail displayed directly above the seekbar thumb
 * while the user scrubs through the timeline.
 */
@Composable
private fun SeekBarFramePreviewThumbnail(
    previewBitmap: android.graphics.Bitmap?,
    fallbackArtworkUrl: String,
    targetTimeStr: String,
    deltaMs: Long,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val deltaSecs = Math.abs(deltaMs / 1000L)
    val deltaFormatted = if (deltaMs >= 0L) "+${formatTime(deltaSecs)}" else "-${formatTime(deltaSecs)}"
    val cardShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 16:9 Thumbnail Frame Box with Liquid Glass Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(cardShape)
                .background(Color(0xE6101216), cardShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.24f),
                    shape = cardShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null && !previewBitmap.isRecycled) {
                Image(
                    bitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() },
                    contentDescription = "Scrub frame preview at $targetTimeStr",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (fallbackArtworkUrl.isNotBlank()) {
                AsyncImage(
                    model = fallbackArtworkUrl,
                    contentDescription = "Media preview at $targetTimeStr",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.72f }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Subtle bottom gradient scrim for timestamp legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )

            // Timestamp + Delta overlay inside bottom of thumbnail
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = targetTimeStr,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
                if (deltaSecs > 0L) {
                    Text(
                        text = deltaFormatted,
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Infuse-style real-time background blur panel with intensified, ultra-soft glassmorphism.
 * Utilizes a wide 38dp blur radius on Android 12+ (RenderEffect) with a 1.38x saturation boost,
 * paired with a deep translucent obsidian glass tint (alpha ~65%) and a delicate top specular sheen.
 */
@Composable
private fun InfuseGlassPanel(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    backdropGraphicsLayer: GraphicsLayer? = null,
    backdropVideoBitmap: android.graphics.Bitmap? = null,
    backdropImageBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    backdropPositionInRoot: () -> Offset = { Offset.Zero },
    backdropSize: () -> androidx.compose.ui.unit.IntSize = { androidx.compose.ui.unit.IntSize.Zero },
    backdropDrawTick: () -> Long = { 0L },
    content: @Composable BoxScope.() -> Unit
) {
    val panelPosRef = remember { floatArrayOf(0f, 0f) }
    val supportsRealTimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val videoImageBitmap = remember(backdropImageBitmap, backdropVideoBitmap) {
        backdropImageBitmap ?: if (backdropVideoBitmap != null && !backdropVideoBitmap.isRecycled) {
            runCatching { backdropVideoBitmap.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    Surface(
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(
            0.85.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.34f),
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                panelPosRef[0] = pos.x
                panelPosRef[1] = pos.y
            }
            .clip(shape)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 1. Static Glass Backdrop Blur Layer (Captured Snapshot + RenderEffect Blur)
            if (supportsRealTimeBlur) {
                val density = LocalDensity.current
                val blurRadiusPx = remember(density) { with(density) { 42.dp.toPx() } }
                val staticBackdropRenderEffect = remember(blurRadiusPx) {
                    val blurEffect = RenderEffect.createBlurEffect(
                        blurRadiusPx,
                        blurRadiusPx,
                        Shader.TileMode.CLAMP
                    )
                    val saturationMatrix = ColorMatrix().apply {
                        setSaturation(1.42f)
                    }
                    RenderEffect.createColorFilterEffect(
                        ColorMatrixColorFilter(saturationMatrix),
                        blurEffect
                    ).asComposeRenderEffect()
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = staticBackdropRenderEffect
                            clip = true
                        }
                        .drawBehind {
                            val rootBackdropPos = backdropPositionInRoot()
                            val rootSize = backdropSize()
                            val offsetX = panelPosRef[0] - rootBackdropPos.x
                            val offsetY = panelPosRef[1] - rootBackdropPos.y

                            drawRect(Color(0xFF0E1117))
                            translate(left = -offsetX, top = -offsetY) {
                                if (videoImageBitmap != null && rootSize.width > 0 && rootSize.height > 0) {
                                    drawImage(
                                        image = videoImageBitmap,
                                        dstSize = rootSize
                                    )
                                } else if (backdropGraphicsLayer != null) {
                                    drawLayer(backdropGraphicsLayer)
                                }
                            }
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(38.dp)
                        .background(Color.White.copy(alpha = 0.08f), shape = shape)
                )
            }

            // 2. Authentic iOS Infuse Liquid Glass Tint, Diagonal Specular Reflection & Soft Rim Highlights
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xA312151E), shape = shape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.03f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(420f, 520f)
                        ),
                        shape = shape
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            endY = 110f
                        ),
                        shape = shape
                    )
            )

            // 3. Foreground Content
            content()
        }
    }
}

private class HudPillGlassDrawCache {
    var lastW = -1f
    var lastH = -1f
    var topSheenHeight = 0f
    var bottomSheenHeight = 0f
    var topSheenBrush: Brush? = null
    var bottomSheenBrush: Brush? = null

    var innerStrokeWidthPx = 0f
    var outerStrokeWidthPx = 0f
    val innerTopPath = Path()
    val innerBottomPath = Path()
    val outerTopPath = Path()
    val outerBottomPath = Path()

    var lastMotionBucket = Int.MIN_VALUE
    var innerTopBrush: Brush? = null
    var innerBottomBrush: Brush? = null
    var outerTopBrush: Brush? = null
    var outerBottomBrush: Brush? = null

    fun ensureGeometry(
        w: Float,
        h: Float,
        cornerRadiusPx: Float,
        topSheenH: Float,
        bottomSheenH: Float,
        innerInsetPx: Float,
        innerStrokePx: Float,
        outerInsetPx: Float,
        outerStrokePx: Float
    ) {
        if (lastW == w && lastH == h) return
        lastW = w
        lastH = h
        lastMotionBucket = Int.MIN_VALUE

        topSheenHeight = topSheenH
        bottomSheenHeight = bottomSheenH
        topSheenBrush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.11f),
            0.48f to Color.White.copy(alpha = 0.032f),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = topSheenH
        )
        bottomSheenBrush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.52f to Color.White.copy(alpha = 0.024f),
            1.0f to Color.White.copy(alpha = 0.08f),
            startY = h - bottomSheenH,
            endY = h
        )

        val r = minOf(cornerRadiusPx, w * 0.5f, h * 0.5f)
        innerStrokeWidthPx = innerStrokePx
        outerStrokeWidthPx = outerStrokePx

        buildCurvedEdgePaths(w, h, r, innerInsetPx, innerTopPath, innerBottomPath)
        buildCurvedEdgePaths(w, h, r, outerInsetPx, outerTopPath, outerBottomPath)
    }

    private fun buildCurvedEdgePaths(
        w: Float,
        h: Float,
        r: Float,
        insetPx: Float,
        topOut: Path,
        bottomOut: Path
    ) {
        val arcR = (r - insetPx).coerceAtLeast(1f)
        val arcSpan = arcR * 2f

        val topLeftRect = androidx.compose.ui.geometry.Rect(
            insetPx, insetPx, insetPx + arcSpan, insetPx + arcSpan
        )
        val topRightRect = androidx.compose.ui.geometry.Rect(
            w - insetPx - arcSpan, insetPx, w - insetPx, insetPx + arcSpan
        )
        val bottomLeftRect = androidx.compose.ui.geometry.Rect(
            insetPx, h - insetPx - arcSpan, insetPx + arcSpan, h - insetPx
        )
        val bottomRightRect = androidx.compose.ui.geometry.Rect(
            w - insetPx - arcSpan, h - insetPx - arcSpan, w - insetPx, h - insetPx
        )

        topOut.reset()
        topOut.arcTo(topLeftRect, startAngleDegrees = 214f, sweepAngleDegrees = 56f, forceMoveTo = true)
        topOut.lineTo((w - insetPx - arcR).coerceAtLeast(insetPx + arcR), insetPx)
        topOut.arcTo(topRightRect, startAngleDegrees = 270f, sweepAngleDegrees = 56f, forceMoveTo = false)

        bottomOut.reset()
        bottomOut.arcTo(bottomLeftRect, startAngleDegrees = 146f, sweepAngleDegrees = -56f, forceMoveTo = true)
        bottomOut.lineTo((w - insetPx - arcR).coerceAtLeast(insetPx + arcR), h - insetPx)
        bottomOut.arcTo(bottomRightRect, startAngleDegrees = 90f, sweepAngleDegrees = -56f, forceMoveTo = false)
    }

    fun ensureReflectionBrushes(motionShift: Float) {
        val quantized = Math.round(motionShift.coerceIn(-0.14f, 0.14f) / 0.015f)
        if (quantized == lastMotionBucket && innerTopBrush != null) return
        lastMotionBucket = quantized
        val shift = (quantized * 0.015f).coerceIn(-0.14f, 0.14f)

        val s1 = (0.15f + shift * 0.5f).coerceIn(0.06f, 0.24f)
        val s2 = (0.34f + shift).coerceIn(0.25f, 0.46f)
        val s3 = (0.56f + shift * 0.7f).coerceIn(0.47f, 0.68f)
        val s4 = (0.82f + shift * 0.5f).coerceIn(0.72f, 0.92f)

        innerTopBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.03f to Color.Transparent,
            s1 to Color(0x24EAF4FF),
            s2 to Color(0x34FFFFFF),
            s3 to Color(0x1CF2F7FF),
            s4 to Color(0x28FFF8EE),
            0.97f to Color.Transparent,
            1.0f to Color.Transparent
        )
        innerBottomBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.03f to Color.Transparent,
            s1 to Color(0x20FFF8EE),
            s2 to Color(0x16F2F7FF),
            s3 to Color(0x2EFFFFFF),
            s4 to Color(0x22EAF4FF),
            0.97f to Color.Transparent,
            1.0f to Color.Transparent
        )
        outerTopBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.02f to Color.Transparent,
            s1 to Color(0x86E8F5FF),
            s2 to Color(0xD8FFFFFF),
            s3 to Color(0x9EF5FAFF),
            (s3 + 0.12f).coerceAtMost(0.71f) to Color(0x68FAF6FF),
            s4 to Color(0xACFFF9EE),
            0.98f to Color.Transparent,
            1.0f to Color.Transparent
        )
        outerBottomBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.02f to Color.Transparent,
            s1 to Color(0x74FFF8EE),
            s2 to Color(0x5CF2F7FF),
            s3 to Color(0x94F7FBFF),
            (s3 + 0.14f).coerceAtMost(0.71f) to Color(0xC6FFFFFF),
            s4 to Color(0x82EAF4FF),
            0.98f to Color.Transparent,
            1.0f to Color.Transparent
        )
    }
}

/**
 * Shared iOS-Style Liquid Glass HUD Pill used by the Volume, Brightness, and 2x Playback-Speed popups.
 * Combines genuine real-time backdrop blur, optical curved-bevel refraction, translucent dark curved-glass
 * depth, and naturally varying white/prismatic specular light reflections along the upper and lower curved edges.
 */
@Composable
internal fun PlayerLiquidGlassHudPill(
    testTag: String,
    backdropGraphicsLayer: GraphicsLayer? = null,
    loadingBackdropGraphicsLayer: GraphicsLayer? = null,
    backdropVideoBitmap: android.graphics.Bitmap? = null,
    backdropImageProvider: (() -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
    backdropPositionInRoot: () -> Offset = { Offset.Zero },
    backdropSize: () -> androidx.compose.ui.unit.IntSize = { androidx.compose.ui.unit.IntSize.Zero },
    backdropDrawTick: () -> Long = { 0L },
    reflectionSeed: Float = 0.5f,
    content: @Composable BoxScope.() -> Unit
) {
    val pillHeight = 38.dp
    val pillCornerRadius = 19.dp
    val pillShape = CircleShape
    val density = LocalDensity.current
    val pillPosRef = remember { floatArrayOf(0f, 0f) }
    var pillSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val supportsRealTimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Animatable keeps seed animation strictly inside draw/graphicsLayer phases (zero recompositions while dragging)
    val animatedReflectionSeed = remember { Animatable(reflectionSeed.coerceIn(0f, 1f)) }
    LaunchedEffect(reflectionSeed) {
        val target = reflectionSeed.coerceIn(0f, 1f)
        if (kotlin.math.abs(animatedReflectionSeed.value - target) > 0.002f) {
            animatedReflectionSeed.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
        } else {
            animatedReflectionSeed.snapTo(target)
        }
    }

    val legacyVideoImageBitmap = remember(backdropVideoBitmap) {
        if (backdropImageProvider == null && backdropVideoBitmap != null && !backdropVideoBitmap.isRecycled) {
            runCatching { backdropVideoBitmap.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    val surfaceGlazeBrush = remember {
        Brush.verticalGradient(
            0.0f to Color(0x1C1A212E),
            0.32f to Color(0x240D1018),
            0.68f to Color(0x2B090C12),
            1.0f to Color(0x3406080D)
        )
    }
    val drawCache = remember { HudPillGlassDrawCache() }

    Box(
        modifier = Modifier
            .testTag(testTag)
            .height(pillHeight)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                pillPosRef[0] = pos.x
                pillPosRef[1] = pos.y
                if (pillSizePx != coords.size) {
                    pillSizePx = coords.size
                }
            }
            .clip(pillShape)
            .background(Color(0x14080A10), shape = pillShape)
    ) {
        // 1. Real-Time Liquid Glass Backdrop Blur & Optical Curved-Bevel Refraction (API 31+ RenderEffect, API 33+ AGSL)
        if (supportsRealTimeBlur) {
            val blurRadiusPx = remember(density) { with(density) { 5.5.dp.toPx() } }
            val cornerRadiusPx = remember(density) { with(density) { pillCornerRadius.toPx() } }
            val defaultWidthPx = remember(density) { with(density) { 108.dp.toPx() } }
            val defaultHeightPx = remember(density) { with(density) { pillHeight.toPx() } }
            val widthPx = pillSizePx.width.toFloat().takeIf { it > 1f } ?: defaultWidthPx
            val heightPx = pillSizePx.height.toFloat().takeIf { it > 1f } ?: defaultHeightPx

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        val quantizedPhase = com.example.ui.components.LiquidGlassDockEffects
                            .quantizeHudLightPhase(animatedReflectionSeed.value)
                        renderEffect = com.example.ui.components.LiquidGlassDockEffects
                            .createHudPillBackdropEffect(
                                blurRadiusPx = blurRadiusPx,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                cornerRadiusPx = cornerRadiusPx,
                                lightPhase = quantizedPhase
                            )
                        clip = true
                    }
                    .drawBehind {
                        if (backdropGraphicsLayer != null) {
                            backdropDrawTick()
                        }
                        val rootBackdropPos = backdropPositionInRoot()
                        val rootSize = backdropSize()
                        val offsetX = pillPosRef[0] - rootBackdropPos.x
                        val offsetY = pillPosRef[1] - rootBackdropPos.y

                        drawRect(Color(0xFF0A0C12))

                        val videoImage = backdropImageProvider?.invoke() ?: legacyVideoImageBitmap
                        if (videoImage != null && rootSize.width > 0 && rootSize.height > 0 && size.width > 1f && size.height > 1f) {
                            val rootW = rootSize.width.toFloat()
                            val rootH = rootSize.height.toFloat()
                            val visLeft = offsetX.coerceIn(0f, rootW)
                            val visTop = offsetY.coerceIn(0f, rootH)
                            val visRight = (offsetX + size.width).coerceIn(visLeft, rootW)
                            val visBottom = (offsetY + size.height).coerceIn(visTop, rootH)
                            if (visRight > visLeft && visBottom > visTop) {
                                val imgW = videoImage.width
                                val imgH = videoImage.height
                                val srcX = ((visLeft / rootW) * imgW).toInt().coerceIn(0, (imgW - 1).coerceAtLeast(0))
                                val srcY = ((visTop / rootH) * imgH).toInt().coerceIn(0, (imgH - 1).coerceAtLeast(0))
                                val srcRight = kotlin.math.ceil((visRight / rootW) * imgW.toDouble()).toInt().coerceIn(srcX + 1, imgW)
                                val srcBottom = kotlin.math.ceil((visBottom / rootH) * imgH.toDouble()).toInt().coerceIn(srcY + 1, imgH)
                                drawImage(
                                    image = videoImage,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = androidx.compose.ui.unit.IntSize(srcRight - srcX, srcBottom - srcY),
                                    dstOffset = IntOffset((visLeft - offsetX).roundToInt(), (visTop - offsetY).roundToInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(
                                        (visRight - visLeft).roundToInt().coerceAtLeast(1),
                                        (visBottom - visTop).roundToInt().coerceAtLeast(1)
                                    ),
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                                )
                            }
                        } else if (loadingBackdropGraphicsLayer != null) {
                            translate(left = -offsetX, top = -offsetY) {
                                drawLayer(loadingBackdropGraphicsLayer)
                            }
                        }
                        if (backdropGraphicsLayer != null) {
                            translate(left = -offsetX, top = -offsetY) {
                                drawLayer(backdropGraphicsLayer)
                            }
                        }
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(6.dp)
                    .background(Color.White.copy(alpha = 0.06f), shape = pillShape)
            )
        }

        // 2. Translucent Dark Curved-Glass Surface Glaze (preserves softly blurred recognizable background)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(surfaceGlazeBrush, shape = pillShape)
        )

        // 3. Realistic Polished Glass Surface Sheen & Edge Reflections along Upper and Lower Curved Edges
        //    Uses cached Path/Brush geometry to eliminate per-frame allocations during drags and animations.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    if (w <= 4f || h <= 4f) return@drawBehind

                    drawCache.ensureGeometry(
                        w = w,
                        h = h,
                        cornerRadiusPx = pillCornerRadius.toPx(),
                        topSheenH = 6.dp.toPx().coerceAtMost(h * 0.35f),
                        bottomSheenH = 5.dp.toPx().coerceAtMost(h * 0.30f),
                        innerInsetPx = 1.05.dp.toPx(),
                        innerStrokePx = 1.65.dp.toPx(),
                        outerInsetPx = 0.45.dp.toPx(),
                        outerStrokePx = 0.92.dp.toPx()
                    )

                    drawCache.topSheenBrush?.let { topSheen ->
                        drawRect(
                            brush = topSheen,
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(w, drawCache.topSheenHeight)
                        )
                    }
                    drawCache.bottomSheenBrush?.let { bottomSheen ->
                        drawRect(
                            brush = bottomSheen,
                            topLeft = Offset(0f, h - drawCache.bottomSheenHeight),
                            size = androidx.compose.ui.geometry.Size(w, drawCache.bottomSheenHeight)
                        )
                    }

                    val rootPos = backdropPositionInRoot()
                    val relativeY = (pillPosRef[1] - rootPos.y).coerceIn(-120f, 240f)
                    val motionShift = ((relativeY / 180f) * 0.08f + (animatedReflectionSeed.value - 0.5f) * 0.12f)
                        .coerceIn(-0.14f, 0.14f)
                    drawCache.ensureReflectionBrushes(motionShift)

                    // Pass 1: Soft inner curved bevel sheen integrated into the glass surface (~1.65dp)
                    drawCache.innerTopBrush?.let { b ->
                        drawPath(
                            path = drawCache.innerTopPath,
                            brush = b,
                            style = Stroke(width = drawCache.innerStrokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                    drawCache.innerBottomBrush?.let { b ->
                        drawPath(
                            path = drawCache.innerBottomPath,
                            brush = b,
                            style = Stroke(width = drawCache.innerStrokeWidthPx, cap = StrokeCap.Round)
                        )
                    }

                    // Pass 2: Thin, crisp specular glass rim catchlight with subtle natural color refraction (~0.92dp)
                    drawCache.outerTopBrush?.let { b ->
                        drawPath(
                            path = drawCache.outerTopPath,
                            brush = b,
                            style = Stroke(width = drawCache.outerStrokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                    drawCache.outerBottomBrush?.let { b ->
                        drawPath(
                            path = drawCache.outerBottomPath,
                            brush = b,
                            style = Stroke(width = drawCache.outerStrokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }
        )

        // 5. Foreground Content Layer (preserves exact compact pill dimensions, icon & text)
        content()
    }
}






