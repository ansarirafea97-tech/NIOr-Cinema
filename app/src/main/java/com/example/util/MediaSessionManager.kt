package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.model.Movie
import com.example.network.GeminiService
import com.example.service.PlaybackMediaSessionService
import com.example.viewmodel.PlayerEpisode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@OptIn(UnstableApi::class)
object MediaSessionManager {

    private const val SESSION_ID = "InfuseUniversalMediaSession"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var artworkLoadJob: Job? = null

    @Volatile
    private var mediaSession: MediaSession? = null

    @Volatile
    private var forwardingPlayer: InfuseForwardingPlayer? = null

    @Volatile
    private var onNextCallback: (() -> Unit)? = null

    @Volatile
    private var onPrevCallback: (() -> Unit)? = null

    private val artworkBitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val artworkBytesCache = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    internal var latestArtworkBitmap: Bitmap? = null

    internal class InfuseForwardingPlayer(
        wrappedPlayer: Player
    ) : ForwardingPlayer(wrappedPlayer) {

        @Volatile
        var isReleased: Boolean = false

        @Volatile
        var customMetadata: MediaMetadata = MediaMetadata.EMPTY

        private val registeredListeners = CopyOnWriteArraySet<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            registeredListeners.add(listener)
            if (!isReleased) {
                try {
                    super.addListener(listener)
                } catch (_: Exception) {}
            }
        }

        override fun removeListener(listener: Player.Listener) {
            registeredListeners.remove(listener)
            if (!isReleased) {
                try {
                    super.removeListener(listener)
                } catch (_: Exception) {}
            }
        }

        override fun getMediaMetadata(): MediaMetadata {
            val meta = customMetadata
            if (meta != MediaMetadata.EMPTY && !meta.title.isNullOrBlank()) {
                return meta
            }
            return if (!isReleased) {
                try {
                    super.getMediaMetadata()
                } catch (_: Exception) {
                    MediaMetadata.EMPTY
                }
            } else {
                MediaMetadata.EMPTY
            }
        }

        override fun getPlaylistMetadata(): MediaMetadata {
            val meta = customMetadata
            if (meta != MediaMetadata.EMPTY && !meta.title.isNullOrBlank()) {
                return meta
            }
            return if (!isReleased) {
                try {
                    super.getPlaylistMetadata()
                } catch (_: Exception) {
                    MediaMetadata.EMPTY
                }
            } else {
                MediaMetadata.EMPTY
            }
        }

        override fun getCurrentMediaItem(): MediaItem? {
            if (isReleased) return null
            val baseItem = try {
                super.getCurrentMediaItem()
            } catch (_: Exception) {
                null
            }
            val meta = customMetadata
            return if (baseItem != null) {
                if (meta != MediaMetadata.EMPTY && !meta.title.isNullOrBlank()) {
                    baseItem.buildUpon().setMediaMetadata(meta).build()
                } else {
                    baseItem
                }
            } else if (meta != MediaMetadata.EMPTY && !meta.title.isNullOrBlank()) {
                MediaItem.Builder()
                    .setMediaId("active_playback_item")
                    .setMediaMetadata(meta)
                    .build()
            } else {
                null
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            val baseBuilder = if (!isReleased) {
                try {
                    super.getAvailableCommands().buildUpon()
                } catch (_: Exception) {
                    Player.Commands.Builder()
                }
            } else {
                Player.Commands.Builder()
            }

            baseBuilder
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)

            if (onNextCallback != null) {
                baseBuilder.add(Player.COMMAND_SEEK_TO_NEXT)
                baseBuilder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                baseBuilder.remove(Player.COMMAND_SEEK_TO_NEXT)
                baseBuilder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }

            if (onPrevCallback != null) {
                baseBuilder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                baseBuilder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            } else {
                baseBuilder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                baseBuilder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }

            return baseBuilder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> onNextCallback != null
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> onPrevCallback != null
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES -> !isReleased
                else -> if (!isReleased) {
                    try {
                        super.isCommandAvailable(command)
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
        }

        override fun hasNextMediaItem(): Boolean = onNextCallback != null

        override fun hasPreviousMediaItem(): Boolean = onPrevCallback != null

        override fun seekToNext() {
            val cb = onNextCallback
            if (cb != null) {
                runOnMainThread { cb.invoke() }
            }
        }

        override fun seekToNextMediaItem() {
            val cb = onNextCallback
            if (cb != null) {
                runOnMainThread { cb.invoke() }
            }
        }

        override fun seekToPrevious() {
            val cb = onPrevCallback
            if (cb != null) {
                runOnMainThread { cb.invoke() }
            } else if (!isReleased) {
                try {
                    seekTo(0L)
                } catch (_: Exception) {}
            }
        }

        override fun seekToPreviousMediaItem() {
            val cb = onPrevCallback
            if (cb != null) {
                runOnMainThread { cb.invoke() }
            } else if (!isReleased) {
                try {
                    seekTo(0L)
                } catch (_: Exception) {}
            }
        }

        fun notifyMetadataAndCommandsChanged() {
            if (isReleased) return
            runOnMainThread {
                if (isReleased) return@runOnMainThread
                try {
                    wrappedPlayer.playlistMetadata = customMetadata
                } catch (_: Exception) {}
                val cmds = availableCommands
                val meta = customMetadata
                for (listener in registeredListeners) {
                    try {
                        listener.onAvailableCommandsChanged(cmds)
                        listener.onMediaMetadataChanged(meta)
                        listener.onPlaylistMetadataChanged(meta)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    internal class CoilBitmapLoader(private val context: Context) : BitmapLoader {

        override fun supportsMimeType(mimeType: String): Boolean = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val cached = latestArtworkBitmap
            if (cached != null && !cached.isRecycled) {
                return Futures.immediateFuture(cached)
            }
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                try {
                    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) {
                        latestArtworkBitmap = bmp
                        future.set(bmp)
                    } else {
                        future.setException(IllegalArgumentException("Failed to decode bitmap bytes"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val key = uri.toString()
            val cached = artworkBitmapCache[key] ?: latestArtworkBitmap
            if (cached != null && !cached.isRecycled) {
                return Futures.immediateFuture(cached)
            }
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                val bmp = fetchArtworkBitmap(context, key)
                if (bmp != null && !bmp.isRecycled) {
                    future.set(bmp)
                } else {
                    future.setException(IllegalArgumentException("Failed to load artwork from $key"))
                }
            }
            return future
        }
    }

    private fun resolveArtworkUrl(movie: Movie, episode: PlayerEpisode?, isMovie: Boolean): String {
        return if (isMovie) {
            listOf(
                movie.backdropUrl,
                movie.posterUrl,
                movie.logoPosterUrl,
                movie.cleanPosterUrl
            ).firstOrNull { it.isNotBlank() } ?: ""
        } else {
            listOf(
                episode?.posterUrl ?: "",
                movie.backdropUrl,
                movie.posterUrl,
                movie.logoPosterUrl,
                movie.cleanPosterUrl
            ).firstOrNull { it.isNotBlank() } ?: ""
        }
    }

    fun buildInitialMetadata(
        movie: Movie,
        episode: PlayerEpisode?,
        isMovie: Boolean
    ): MediaMetadata {
        val title: String
        val subtitle: String
        val albumTitle: String
        val artworkUrl = resolveArtworkUrl(movie, episode, isMovie)
        val seasonNum: Int?
        val epNum: Int?

        if (isMovie) {
            val sanitized = GeminiService.sanitizeFileName(movie.title)
            title = sanitized.title.ifBlank { movie.title }.ifBlank { "Movie" }
            val year = movie.releaseYear.ifBlank { sanitized.year ?: "" }
            val genres = movie.genres.take(2).joinToString(", ")
            subtitle = listOf(year, genres).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Movie" }
            albumTitle = title
            seasonNum = null
            epNum = null
        } else {
            val ep = episode
            val rawShowTitle = ep?.showTitle?.takeIf { it.isNotBlank() }
                ?: GeminiService.cleanShowTitle(movie.title).ifBlank { movie.title }
            title = rawShowTitle.ifBlank { "TV Series" }
            val s = ep?.seasonNumber ?: 1
            val e = ep?.episodeNumber ?: 1
            var cleanEpTitle = EpisodeUtils.cleanEpisodeTitle(ep?.episodeTitle ?: "")
            if (cleanEpTitle.isBlank()) {
                cleanEpTitle = EpisodeUtils.cleanEpisodeTitle(movie.title)
            }
            if (title.isNotBlank() && cleanEpTitle.lowercase().startsWith(title.lowercase())) {
                cleanEpTitle = cleanEpTitle.substring(title.length).trim()
            }
            cleanEpTitle = cleanEpTitle.removePrefix("-").removePrefix("•").removePrefix(":").removePrefix("–").trim()

            val formattedEp = "S$s · E$e"
            subtitle = if (
                cleanEpTitle.isNotBlank() &&
                !cleanEpTitle.equals(title, ignoreCase = true) &&
                !cleanEpTitle.matches(Regex("(?i)s\\d+\\s*[•\\-·]?\\s*e\\d+"))
            ) {
                "$formattedEp - $cleanEpTitle"
            } else {
                formattedEp
            }
            albumTitle = title
            seasonNum = s
            epNum = e
        }

        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setSubtitle(subtitle)
            .setArtist(subtitle)
            .setAlbumTitle(albumTitle)
            .setAlbumArtist(title)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (isMovie) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_TV_SHOW)

        if (seasonNum != null) builder.setDiscNumber(seasonNum)
        if (epNum != null) builder.setTrackNumber(epNum)

        if (artworkUrl.isNotBlank()) {
            try {
                builder.setArtworkUri(Uri.parse(artworkUrl))
            } catch (_: Exception) {}
            val cachedBytes = artworkBytesCache[artworkUrl]
            val cachedBmp = artworkBitmapCache[artworkUrl]
            if (cachedBmp != null && !cachedBmp.isRecycled) {
                latestArtworkBitmap = cachedBmp
            }
            if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                builder.setArtworkData(cachedBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }

        return builder.build()
    }

    fun initSession(
        context: Context,
        player: Player
    ) {
        val appContext = context.applicationContext
        runOnMainThread {
            try {
                val wrapper = InfuseForwardingPlayer(player)
                forwardingPlayer?.isReleased = true
                forwardingPlayer = wrapper

                val existingSession = mediaSession
                if (existingSession != null) {
                    try {
                        existingSession.player = wrapper
                        PlaybackMediaSessionService.registerSession(appContext, existingSession)
                        return@runOnMainThread
                    } catch (e: Exception) {
                        try {
                            PlaybackMediaSessionService.unregisterSession(appContext, existingSession)
                            existingSession.release()
                        } catch (_: Exception) {}
                        mediaSession = null
                    }
                }

                val callback = object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo
                    ): MediaSession.ConnectionResult {
                        val connectionResult = super.onConnect(session, controller)
                        val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                            .add(SessionCommand("ACTION_NEXT_EPISODE", Bundle.EMPTY))
                            .add(SessionCommand("ACTION_PREV_EPISODE", Bundle.EMPTY))
                            .build()

                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(sessionCommands)
                            .setAvailablePlayerCommands(wrapper.availableCommands)
                            .build()
                    }

                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int
                    ): Int {
                        if (playerCommand == Player.COMMAND_SEEK_TO_NEXT || playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                            val cb = onNextCallback
                            if (cb != null) {
                                runOnMainThread { cb.invoke() }
                                return SessionResult.RESULT_SUCCESS
                            }
                        } else if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS || playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                            val cb = onPrevCallback
                            if (cb != null) {
                                runOnMainThread { cb.invoke() }
                                return SessionResult.RESULT_SUCCESS
                            }
                        }
                        return super.onPlayerCommandRequest(session, controller, playerCommand)
                    }

                    override fun onCustomCommand(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        customCommand: SessionCommand,
                        args: Bundle
                    ): ListenableFuture<SessionResult> {
                        when (customCommand.customAction) {
                            "ACTION_NEXT_EPISODE" -> {
                                onNextCallback?.let { cb -> runOnMainThread { cb.invoke() } }
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            "ACTION_PREV_EPISODE" -> {
                                onPrevCallback?.let { cb -> runOnMainThread { cb.invoke() } }
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                        }
                        return super.onCustomCommand(session, controller, customCommand, args)
                    }
                }

                val activityIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val sessionActivityPendingIntent = PendingIntent.getActivity(
                    appContext,
                    0,
                    activityIntent,
                    pendingIntentFlags
                )

                val session = MediaSession.Builder(appContext, wrapper)
                    .setId(SESSION_ID)
                    .setCallback(callback)
                    .setSessionActivity(sessionActivityPendingIntent)
                    .setBitmapLoader(CoilBitmapLoader(appContext))
                    .build()

                mediaSession = session
                PlaybackMediaSessionService.registerSession(appContext, session)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateMetadata(
        context: Context,
        movie: Movie,
        episode: PlayerEpisode?,
        isMovie: Boolean,
        nextEpisodeAvailable: Boolean = false,
        prevEpisodeAvailable: Boolean = false,
        onNextEpisode: (() -> Unit)? = null,
        onPrevEpisode: (() -> Unit)? = null,
        videoUri: Uri? = null
    ) {
        val appContext = context.applicationContext
        onNextCallback = if (!isMovie && nextEpisodeAvailable) onNextEpisode else null
        onPrevCallback = if (!isMovie && prevEpisodeAvailable) onPrevEpisode else null

        val baseMetadata = buildInitialMetadata(movie, episode, isMovie)
        val artworkUrl = resolveArtworkUrl(movie, episode, isMovie)

        runOnMainThread {
            val wrapper = forwardingPlayer ?: return@runOnMainThread
            if (wrapper.isReleased) return@runOnMainThread

            wrapper.customMetadata = baseMetadata
            wrapper.notifyMetadataAndCommandsChanged()

            val session = mediaSession
            if (session != null) {
                PlaybackMediaSessionService.registerSession(appContext, session)
            }
        }

        // If artwork bytes are already cached, no async fetch needed
        if (artworkUrl.isNotBlank() && artworkBytesCache.containsKey(artworkUrl)) {
            return
        }

        artworkLoadJob?.cancel()
        artworkLoadJob = scope.launch {
            var bmp: Bitmap? = null
            if (artworkUrl.isNotBlank()) {
                bmp = fetchArtworkBitmap(appContext, artworkUrl)
            }
            if (bmp == null && videoUri != null && videoUri != Uri.EMPTY) {
                try {
                    val extractor = VideoFramePreviewExtractor(appContext, videoUri)
                    bmp = extractor.getFrameAtPosition(
                        positionMs = 5000L,
                        targetWidthPx = 480,
                        targetHeightPx = 270,
                        precise = false
                    )
                    extractor.release()
                } catch (_: Exception) {}
            }

            if (bmp != null && !bmp.isRecycled) {
                latestArtworkBitmap = bmp
                val byteArray = encodeBitmapToBytes(bmp, artworkUrl)
                if (byteArray != null && byteArray.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val wrapper = forwardingPlayer
                        if (wrapper != null && !wrapper.isReleased) {
                            val fullMetadata = baseMetadata.buildUpon()
                                .setArtworkData(byteArray, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                            wrapper.customMetadata = fullMetadata
                            wrapper.notifyMetadataAndCommandsChanged()
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchArtworkBitmap(context: Context, url: String): Bitmap? {
        artworkBitmapCache[url]?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        return withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val rawBitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext null

                val scaledBitmap = if (rawBitmap.width > 640 || rawBitmap.height > 640) {
                    val ratio = rawBitmap.height.toFloat() / rawBitmap.width.toFloat().coerceAtLeast(1f)
                    val targetW = 640
                    val targetH = (targetW * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
                } else {
                    rawBitmap
                }

                artworkBitmapCache[url] = scaledBitmap
                latestArtworkBitmap = scaledBitmap
                encodeBitmapToBytes(scaledBitmap, url)
                scaledBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun encodeBitmapToBytes(bitmap: Bitmap, cacheKey: String): ByteArray? {
        if (cacheKey.isNotBlank()) {
            artworkBytesCache[cacheKey]?.let { return it }
        }
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            if (cacheKey.isNotBlank() && bytes.isNotEmpty()) {
                artworkBytesCache[cacheKey] = bytes
            }
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getActiveSession(): MediaSession? = mediaSession

    fun releaseSession(context: Context) {
        val appContext = context.applicationContext
        artworkLoadJob?.cancel()
        artworkLoadJob = null
        onNextCallback = null
        onPrevCallback = null
        forwardingPlayer?.isReleased = true
        forwardingPlayer = null

        val session = mediaSession
        mediaSession = null

        runOnMainThread {
            try {
                PlaybackMediaSessionService.unregisterSession(appContext, session)
                if (session != null) {
                    try {
                        session.release()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
