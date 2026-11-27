package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.model.Movie
import com.example.model.CastMember
import com.example.model.filePath
import com.example.model.fileName
import com.example.model.mockMovies
import com.example.model.OnlineStream
import com.example.model.defaultStreams
import com.example.model.AtomicMediaMetadata
import com.example.model.MediaType
import com.example.model.MovieMetadata
import com.example.model.TvShowMetadata
import com.example.model.TvSeasonMetadata
import com.example.model.TvEpisodeMetadata
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import coil.Coil
import coil.request.ImageRequest
import com.example.model.GridMode

enum class AppTheme(
    val themeName: String,
    val primaryColor: Color,
    val primaryContainer: Color,
    val onPrimaryColor: Color,
    val startGradient: Color,
    val endGradient: Color,
    val description: String
) {
    MIDNIGHT_ORANGE(
        "Noir Orange",
        Color(0xFFFF9500),
        Color(0xFFFF8200),
        Color(0xFF000000),
        Color(0xFFFF9500),
        Color(0xFFFF8200),
        "Official Noir Cinema pure orange theme"
    ),
    NEON_CYBER(
        "Neon Cyberpunk",
        Color(0xFF00F0FF),
        Color(0xFFFF007F),
        Color(0xFF001A2F),
        Color(0xFF002244),
        Color(0xFFFF007F),
        "Cyberpunk electric blue & magenta"
    ),
    NORDIC_FROST(
        "Nordic Frost",
        Color(0xFF81E6D9),
        Color(0xFF319795),
        Color(0xFF002C2B),
        Color(0xFF003E3C),
        Color(0xFF319795),
        "Clean arctic mint and frosty teal"
    ),
    EMERALD_FOREST(
        "Emerald Forest",
        Color(0xFF4ADE80),
        Color(0xFF059669),
        Color(0xFF022C1A),
        Color(0xFF064E3B),
        Color(0xFF059669),
        "Lush emerald and forest greens"
    ),
    SOLAR_FLARE(
        "Solar Flare",
        Color(0xFFFBBF24),
        Color(0xFFEF4444),
        Color(0xFF3C0E00),
        Color(0xFF7C2D12),
        Color(0xFFEF4444),
        "Hot amber, yellow and deep red hues"
    )
}

sealed interface AppScreen {
    object Library : AppScreen
    data class Details(val movieId: String) : AppScreen
    data class Player(val movieId: String, val startPositionMs: Long = 0L) : AppScreen
    object Settings : AppScreen
}

data class ShowWatchState(
    val season: Int,
    val episode: Int,
    val episodeId: String?,
    val positionMs: Long
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("infuse_prefs", Context.MODE_PRIVATE)
    private val metadataCacheRepo = com.example.util.MetadataCacheRepository(application)
    private val gridModeRepo = GridModeRepository(application)
    private val seekPrefsRepo = com.example.util.SeekPreferencesRepository(application)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val streamsListAdapter = moshi.adapter<List<OnlineStream>>(
        Types.newParameterizedType(List::class.java, OnlineStream::class.java)
    )
    private val trailerMemoryCache = java.util.concurrent.ConcurrentHashMap<Int, List<MovieTrailerCandidate>>()
    private val trailerEmptyCache = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    // Tab Navigation State
    private val _currentTab = MutableStateFlow("Home")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    private val favoritesRepo = com.example.util.FavoritesRepository(application)

    // DataStore Persistent Favorites State (movie IDs)
    val favoriteMovieIds: StateFlow<Set<String>> = favoritesRepo.favoritesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun toggleFavorite(movieId: String) {
        viewModelScope.launch {
            favoritesRepo.toggleFavorite(movieId)
        }
    }

    fun setFavorite(movieId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            favoritesRepo.setFavorite(movieId, isFavorite)
        }
    }

    fun isFavorite(movieId: String): Boolean {
        return favoriteMovieIds.value.contains(movieId)
    }

    // Watchlist State (saves movie IDs)
    private val _watchlist = MutableStateFlow<Set<String>>(emptySet())
    val watchlist: StateFlow<Set<String>> = _watchlist.asStateFlow()

    fun toggleWatchlist(movieId: String) {
        val current = _watchlist.value
        _watchlist.value = if (current.contains(movieId)) {
            current - movieId
        } else {
            current + movieId
        }
    }

    // Watch History State
    private val _history = MutableStateFlow<List<Movie>>(emptyList())
    val history: StateFlow<List<Movie>> = _history.asStateFlow()

    fun addToHistory(movie: Movie) {
        val uniqueEntryKey = com.example.util.ContinueWatchingHelper.getUniqueContinueWatchingEntryKey(movie)
        val current = _history.value.filter {
            it.id != movie.id && com.example.util.ContinueWatchingHelper.getUniqueContinueWatchingEntryKey(it) != uniqueEntryKey
        }
        val updated = listOf(movie) + current
        _history.value = updated
        val idsJoined = updated.take(50).joinToString("|||") { it.id }
        sharedPrefs.edit().putString("persisted_history_ids", idsJoined).apply()
    }

    private fun restoreHistoryFromAllMovies(allMovies: List<Movie>) {
        if (allMovies.isEmpty()) return
        val movieById = allMovies.associateBy { it.id }
        val savedHistoryRaw = sharedPrefs.getString("persisted_history_ids", null)
        val orderedIds = LinkedHashSet<String>()
        if (!savedHistoryRaw.isNullOrBlank()) {
            savedHistoryRaw.split("|||").filter { it.isNotBlank() }.forEach { orderedIds.add(it) }
        }
        _history.value.forEach { orderedIds.add(it.id) }
        _savedPositions.value.forEach { (key, pos) ->
            if (pos > 0L && movieById.containsKey(key)) {
                orderedIds.add(key)
            }
        }
        val restored = orderedIds.mapNotNull { id ->
            movieById[id] ?: _history.value.firstOrNull { it.id == id }
        }
        if (restored.isNotEmpty()) {
            _history.value = restored
        }
    }

    // Connected Servers / Sources State
    private val _connectedServers = MutableStateFlow<Set<String>>(emptySet())
    val connectedServers: StateFlow<Set<String>> = _connectedServers.asStateFlow()

    fun addConnectedServer(server: String) {
        _connectedServers.value = _connectedServers.value + server
    }

    fun removeConnectedServer(server: String) {
        _connectedServers.value = _connectedServers.value - server
    }

    // Navigation State
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Library)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // In-window Trailer Modal open state (so PredictiveBackHandler defers to TrailerPlayerModal BackHandler when open)
    private val _isTrailerModalOpen = MutableStateFlow(false)
    val isTrailerModalOpen: StateFlow<Boolean> = _isTrailerModalOpen.asStateFlow()

    fun setTrailerModalOpen(isOpen: Boolean) {
        _isTrailerModalOpen.value = isOpen
    }

    // Screen navigation history backstack
    private val backStack = mutableListOf<AppScreen>()
    private var lastNavTimestamp = 0L

    fun navigateTo(screen: AppScreen) {
        val now = System.currentTimeMillis()
        if (_currentScreen.value == screen) {
            return
        }
        if (now - lastNavTimestamp < 220L && _currentScreen.value is AppScreen.Details && screen is AppScreen.Details) {
            return
        }
        lastNavTimestamp = now

        if (screen is AppScreen.Settings) {
            _currentTab.value = "Settings"
            _currentScreen.value = AppScreen.Library
            return
        }
        if (screen is AppScreen.Library) {
            backStack.removeAll { it is AppScreen.Details }
            _currentScreen.value = AppScreen.Library
            return
        }
        if (screen is AppScreen.Player) {
            selectEpisode(screen.movieId)
            setPlaying(true)
            if (backStack.lastOrNull() != _currentScreen.value) {
                backStack.add(_currentScreen.value)
            }
            _currentScreen.value = screen
            return
        }
        if (screen is AppScreen.Details) {
            // Prevent duplicate Detail Screen instances on the backstack and always keep Library beneath Details
            backStack.removeAll { it is AppScreen.Details }
            if (backStack.lastOrNull() != AppScreen.Library) {
                backStack.add(AppScreen.Library)
            }
            _currentScreen.value = screen
            return
        }
        if (backStack.lastOrNull() != _currentScreen.value) {
            backStack.add(_currentScreen.value)
        }
        _currentScreen.value = screen
    }

    fun navigateBack(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (_currentScreen.value is AppScreen.Details) {
            lastNavTimestamp = now
            backStack.removeAll { it is AppScreen.Details }
            val prev = if (backStack.isNotEmpty()) {
                backStack.removeAt(backStack.size - 1)
            } else {
                AppScreen.Library
            }
            _currentScreen.value = prev
            return true
        }

        if (!force && now - lastNavTimestamp < 150L) {
            return false
        }
        lastNavTimestamp = now

        if (backStack.isNotEmpty()) {
            val prev = backStack.removeAt(backStack.size - 1)
            _currentScreen.value = prev
            return true
        } else if (_currentScreen.value != AppScreen.Library) {
            _currentScreen.value = AppScreen.Library
            return true
        } else if (_currentTab.value != "Home") {
            _currentTab.value = "Home"
            return true
        }
        return false
    }

    // Library Filter States
    private val _selectedCategory = MutableStateFlow("Movies & Shows")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredMovies: StateFlow<List<Movie>> = _selectedCategory.map { category ->
        mockMovies.filter { it.category.equals(category, ignoreCase = true) }
    }.let { categoryFiltered ->
        // We can search through movies too
        MutableStateFlow(mockMovies) // Fallback or complete mapping, let's combine properly
    }

    private val _moviesList = MutableStateFlow<List<Movie>>(com.example.ui.screens.getPresetMovies())
    val moviesList: StateFlow<List<Movie>> = _moviesList.asStateFlow()

    private val _customStreams = MutableStateFlow<List<OnlineStream>>(emptyList())
    val customStreams: StateFlow<List<OnlineStream>> = _customStreams.asStateFlow()

    private val _selectedTheme = MutableStateFlow(AppTheme.MIDNIGHT_ORANGE)
    val selectedTheme = _selectedTheme.asStateFlow()

    private val _savedPositions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val savedPositions: StateFlow<Map<String, Long>> = _savedPositions.asStateFlow()

    private val _savedDurations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val savedDurations: StateFlow<Map<String, Long>> = _savedDurations.asStateFlow()

    private val _watchedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val watchedEpisodes: StateFlow<Set<String>> = _watchedEpisodes.asStateFlow()

    private val _dynamicInterface = MutableStateFlow(true)
    val dynamicInterface: StateFlow<Boolean> = _dynamicInterface.asStateFlow()

    init {
        // Load watched states from SharedPreferences
        val loadedWatched = mutableSetOf<String>()
        sharedPrefs.all.forEach { (key, value) ->
            if (key.startsWith("movie_watched_") && value is Boolean && value) {
                val movieId = key.removePrefix("movie_watched_")
                loadedWatched.add(movieId)
            }
        }
        _watchedEpisodes.value = loadedWatched

        // 1. Load saved theme on startup
        val savedAppTheme = com.example.util.AppThemeManager.getSelectedTheme(application)
        val mappedTheme = when (savedAppTheme) {
            com.example.util.AppThemeManager.AppTheme.ORANGE -> AppTheme.MIDNIGHT_ORANGE
            com.example.util.AppThemeManager.AppTheme.CYBERPUNK -> AppTheme.NEON_CYBER
            com.example.util.AppThemeManager.AppTheme.FROST -> AppTheme.NORDIC_FROST
            com.example.util.AppThemeManager.AppTheme.FOREST -> AppTheme.EMERALD_FOREST
            com.example.util.AppThemeManager.AppTheme.FLARE -> AppTheme.SOLAR_FLARE
        }
        _selectedTheme.value = mappedTheme
        val savedThemeName = sharedPrefs.getString("selected_app_theme", null)
        if (savedThemeName != null) {
            runCatching { AppTheme.valueOf(savedThemeName) }.getOrNull()?.let {
                _selectedTheme.value = it
            }
        }

        // 1b. Load saved dynamic interface setting
        _dynamicInterface.value = sharedPrefs.getBoolean("dynamic_interface_enabled", true)

        // 2. Load saved playback positions and durations from SharedPreferences
        val loadedPositions = mutableMapOf<String, Long>()
        val loadedDurations = mutableMapOf<String, Long>()
        sharedPrefs.all.forEach { (key, value) ->
            if (value is Long && value > 0L) {
                when {
                    key.startsWith("movie_progress_") -> {
                        val movieId = key.removePrefix("movie_progress_")
                        loadedPositions[movieId] = value
                    }
                    key.startsWith("ep_progress_") -> {
                        loadedPositions[key] = value
                    }
                    key.startsWith("movie_duration_") -> {
                        val movieId = key.removePrefix("movie_duration_")
                        loadedDurations[movieId] = value
                    }
                    key.startsWith("ep_duration_") -> {
                        val epKey = key.removePrefix("ep_duration_")
                        loadedDurations[epKey] = value
                    }
                }
            }
        }
        _savedPositions.value = loadedPositions
        _savedDurations.value = loadedDurations

        // 3. Load custom streams from SharedPreferences
        val savedStreamsJson = sharedPrefs.getString("custom_streams_list", null)
        if (!savedStreamsJson.isNullOrBlank()) {
            runCatching {
                streamsListAdapter.fromJson(savedStreamsJson)
            }.getOrNull()?.let { list ->
                _customStreams.value = list
            }
        }

        // 4. Default movies populated with preset shows
        _moviesList.value = com.example.ui.screens.getPresetMovies()
        restoreHistoryFromAllMovies(_moviesList.value)
        registerNetworkCallback()
        if (_moviesList.value.isNotEmpty()) {
            preloadMetadataInBackground(_moviesList.value)
        }
    }

    fun refreshMediaItems() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibrarySyncing.value = true
            try {
                _moviesList.value.forEach { movie ->
                    fetchAndCacheTmdbDetails(movie)
                }
            } finally {
                if (_tmdbLoadingIds.value.isEmpty()) {
                    _isLibrarySyncing.value = false
                }
            }
        }
    }

    private val _posterOverridesVersion = MutableStateFlow(0)
    val posterOverridesVersion: StateFlow<Int> = _posterOverridesVersion.asStateFlow()

    private fun collectPosterKeys(
        movieId: String,
        isTvShow: Boolean = false,
        showTitle: String? = null
    ): Set<String> {
        val keys = LinkedHashSet<String>()
        if (movieId.isNotBlank()) {
            keys.add(movieId)
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank()) {
                keys.add(fileId)
            }
        }
        val targetMovie = _moviesList.value.firstOrNull { it.id == movieId }
            ?: _history.value.firstOrNull { it.id == movieId }
        val resolvedIsTv = isTvShow ||
                targetMovie?.category?.equals("Shows", ignoreCase = true) == true ||
                targetMovie?.title?.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+""")) == true
        val resolvedShowTitle = when {
            !showTitle.isNullOrBlank() -> showTitle.trim()
            resolvedIsTv && targetMovie != null -> com.example.network.GeminiService.cleanShowTitle(targetMovie.title)
            resolvedIsTv && movieId.isNotBlank() -> com.example.network.GeminiService.cleanShowTitle(movieId)
            else -> ""
        }
        val normalizedTargetShow = if (resolvedIsTv && resolvedShowTitle.isNotBlank()) {
            com.example.util.EpisodeUtils.getNormalizedShowTitle(resolvedShowTitle)
        } else ""

        if (!resolvedIsTv && targetMovie != null && targetMovie.title.isNotBlank()) {
            keys.add(targetMovie.title)
            val cleanedTitle = com.example.network.GeminiService.sanitizeFileName(targetMovie.title).title
            if (cleanedTitle.isNotBlank()) {
                keys.add(cleanedTitle)
            }
        }

        if (resolvedIsTv && resolvedShowTitle.isNotBlank()) {
            keys.add(resolvedShowTitle)
            if (normalizedTargetShow.isNotBlank()) {
                keys.add(normalizedTargetShow)
            }
            _moviesList.value.forEach { m ->
                val mNorm = com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title)
                if (m.id == movieId || (normalizedTargetShow.isNotBlank() && mNorm == normalizedTargetShow)) {
                    keys.add(m.id)
                    val mFileId = m.id.substringAfterLast("/")
                    if (mFileId != m.id && mFileId.isNotBlank()) keys.add(mFileId)
                    if (m.title.isNotBlank()) keys.add(m.title)
                }
            }
        }
        return keys
    }

    /**
     * Card poster override: Updates ONLY card poster appearances across Library, Home,
     * Favorites, Picks of the Day, and Search. Never modifies the detail-screen poster.
     */
    fun saveCardPosterOverride(
        movieId: String,
        customUrl: String,
        hasEmbeddedText: Boolean = false,
        isTvShow: Boolean = false,
        showTitle: String? = null
    ) {
        val trimmed = customUrl.trim()
        val formattedPoster = if (trimmed.isNotBlank()) {
            com.example.util.PosterCardUIHelper.formatTmdbUrl(trimmed, "w780")
        } else ""

        val keys = collectPosterKeys(movieId, isTvShow, showTitle)
        val editor = sharedPrefs.edit()
        keys.forEach { key ->
            if (formattedPoster.isBlank()) {
                editor.remove("card_poster_override_$key")
                editor.remove("card_poster_has_text_$key")
            } else {
                editor.putString("card_poster_override_$key", formattedPoster)
                editor.putBoolean("card_poster_has_text_$key", hasEmbeddedText)
            }
        }
        editor.apply()

        if (formattedPoster.isNotBlank()) {
            preloadImageToCoilCache(formattedPoster)
        }
        _posterOverridesVersion.value += 1
    }

    fun getCardPosterOverride(movieId: String, title: String? = null): String? {
        if (movieId.isNotBlank()) {
            val byId = sharedPrefs.getString("card_poster_override_$movieId", null)
            if (!byId.isNullOrBlank()) return byId
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank()) {
                val byFile = sharedPrefs.getString("card_poster_override_$fileId", null)
                if (!byFile.isNullOrBlank()) return byFile
            }
            val normId = com.example.util.EpisodeUtils.getNormalizedShowTitle(movieId)
            if (normId.isNotBlank()) {
                val byNormId = sharedPrefs.getString("card_poster_override_$normId", null)
                if (!byNormId.isNullOrBlank()) return byNormId
            }
        }
        if (!title.isNullOrBlank()) {
            val byTitle = sharedPrefs.getString("card_poster_override_$title", null)
            if (!byTitle.isNullOrBlank()) return byTitle
            val cleanedTitle = com.example.network.GeminiService.sanitizeFileName(title).title
            if (cleanedTitle.isNotBlank()) {
                val byCleaned = sharedPrefs.getString("card_poster_override_$cleanedTitle", null)
                if (!byCleaned.isNullOrBlank()) return byCleaned
            }
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank()) {
                val byShow = sharedPrefs.getString("card_poster_override_$sTitle", null)
                if (!byShow.isNullOrBlank()) return byShow
                val normShow = com.example.util.EpisodeUtils.getNormalizedShowTitle(sTitle)
                if (normShow.isNotBlank()) {
                    val byNormShow = sharedPrefs.getString("card_poster_override_$normShow", null)
                    if (!byNormShow.isNullOrBlank()) return byNormShow
                }
            }
        }
        return null
    }

    fun isCardPosterTextBearing(movieId: String, title: String? = null): Boolean {
        if (movieId.isNotBlank()) {
            if (sharedPrefs.contains("card_poster_has_text_$movieId")) {
                return sharedPrefs.getBoolean("card_poster_has_text_$movieId", false)
            }
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank() && sharedPrefs.contains("card_poster_has_text_$fileId")) {
                return sharedPrefs.getBoolean("card_poster_has_text_$fileId", false)
            }
        }
        if (!title.isNullOrBlank()) {
            if (sharedPrefs.contains("card_poster_has_text_$title")) {
                return sharedPrefs.getBoolean("card_poster_has_text_$title", false)
            }
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank() && sharedPrefs.contains("card_poster_has_text_$sTitle")) {
                return sharedPrefs.getBoolean("card_poster_has_text_$sTitle", false)
            }
            val normShow = com.example.util.EpisodeUtils.getNormalizedShowTitle(sTitle.ifBlank { title })
            if (normShow.isNotBlank() && sharedPrefs.contains("card_poster_has_text_$normShow")) {
                return sharedPrefs.getBoolean("card_poster_has_text_$normShow", false)
            }
        }
        return false
    }

    /**
     * Detail poster override: Updates ONLY the main poster on that title's detail screen.
     * Never modifies any Library, Home, Favorites, Picks of the Day, or Search card posters.
     */
    fun saveDetailPosterOverride(
        movieId: String,
        customUrl: String,
        hasEmbeddedText: Boolean = true,
        isTvShow: Boolean = false,
        showTitle: String? = null
    ) {
        val trimmed = customUrl.trim()
        val formattedPoster = if (trimmed.isNotBlank()) {
            com.example.util.PosterCardUIHelper.formatTmdbUrl(trimmed, "w780")
        } else ""

        val keys = collectPosterKeys(movieId, isTvShow, showTitle)
        val editor = sharedPrefs.edit()
        keys.forEach { key ->
            if (formattedPoster.isBlank()) {
                editor.remove("detail_poster_override_$key")
                editor.remove("detail_poster_has_text_$key")
            } else {
                editor.putString("detail_poster_override_$key", formattedPoster)
                editor.putBoolean("detail_poster_has_text_$key", hasEmbeddedText)
            }
        }
        editor.apply()

        if (formattedPoster.isNotBlank()) {
            preloadImageToCoilCache(formattedPoster)
        }
        _posterOverridesVersion.value += 1
    }

    fun getDetailPosterOverride(movieId: String, title: String? = null): String? {
        if (movieId.isNotBlank()) {
            val byId = sharedPrefs.getString("detail_poster_override_$movieId", null)
            if (!byId.isNullOrBlank()) return byId
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank()) {
                val byFile = sharedPrefs.getString("detail_poster_override_$fileId", null)
                if (!byFile.isNullOrBlank()) return byFile
            }
        }
        if (!title.isNullOrBlank()) {
            val byTitle = sharedPrefs.getString("detail_poster_override_$title", null)
            if (!byTitle.isNullOrBlank()) return byTitle
            val cleanedTitle = com.example.network.GeminiService.sanitizeFileName(title).title
            if (cleanedTitle.isNotBlank()) {
                val byCleaned = sharedPrefs.getString("detail_poster_override_$cleanedTitle", null)
                if (!byCleaned.isNullOrBlank()) return byCleaned
            }
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank()) {
                val byShow = sharedPrefs.getString("detail_poster_override_$sTitle", null)
                if (!byShow.isNullOrBlank()) return byShow
                val normShow = com.example.util.EpisodeUtils.getNormalizedShowTitle(sTitle)
                if (normShow.isNotBlank()) {
                    val byNormShow = sharedPrefs.getString("detail_poster_override_$normShow", null)
                    if (!byNormShow.isNullOrBlank()) return byNormShow
                }
            }
        }
        return null
    }

    fun isDetailPosterTextBearing(movieId: String, title: String? = null): Boolean {
        if (movieId.isNotBlank()) {
            if (sharedPrefs.contains("detail_poster_has_text_$movieId")) {
                return sharedPrefs.getBoolean("detail_poster_has_text_$movieId", false)
            }
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank() && sharedPrefs.contains("detail_poster_has_text_$fileId")) {
                return sharedPrefs.getBoolean("detail_poster_has_text_$fileId", false)
            }
        }
        if (!title.isNullOrBlank()) {
            if (sharedPrefs.contains("detail_poster_has_text_$title")) {
                return sharedPrefs.getBoolean("detail_poster_has_text_$title", false)
            }
            val cleanedTitle = com.example.network.GeminiService.sanitizeFileName(title).title
            if (cleanedTitle.isNotBlank() && sharedPrefs.contains("detail_poster_has_text_$cleanedTitle")) {
                return sharedPrefs.getBoolean("detail_poster_has_text_$cleanedTitle", false)
            }
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank() && sharedPrefs.contains("detail_poster_has_text_$sTitle")) {
                return sharedPrefs.getBoolean("detail_poster_has_text_$sTitle", false)
            }
            val normShow = com.example.util.EpisodeUtils.getNormalizedShowTitle(sTitle.ifBlank { title })
            if (normShow.isNotBlank() && sharedPrefs.contains("detail_poster_has_text_$normShow")) {
                return sharedPrefs.getBoolean("detail_poster_has_text_$normShow", false)
            }
        }
        return false
    }

    fun saveCustomPosterUrl(
        movieId: String,
        customUrl: String,
        isTvShow: Boolean = false,
        showTitle: String? = null,
        hasEmbeddedText: Boolean = true
    ) {
        saveDetailPosterOverride(
            movieId = movieId,
            customUrl = customUrl,
            hasEmbeddedText = hasEmbeddedText,
            isTvShow = isTvShow,
            showTitle = showTitle
        )
    }

    fun getSavedCustomPosterUrl(movieId: String, title: String? = null): String? {
        if (movieId.isNotBlank()) {
            val byId = sharedPrefs.getString("custom_poster_$movieId", null)
            if (!byId.isNullOrBlank()) return byId
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank()) {
                val byFile = sharedPrefs.getString("custom_poster_$fileId", null)
                if (!byFile.isNullOrBlank()) return byFile
            }
        }
        if (!title.isNullOrBlank()) {
            val byTitle = sharedPrefs.getString("custom_poster_$title", null)
            if (!byTitle.isNullOrBlank()) return byTitle
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank()) {
                val byShow = sharedPrefs.getString("custom_poster_$sTitle", null)
                if (!byShow.isNullOrBlank()) return byShow
            }
        }
        return null
    }

    fun getSavedSeasonPosterUrl(tvId: Int, seasonNumber: Int): String? {
        if (tvId <= 0 || seasonNumber <= 0) {
            Log.d("ARTWORK_MATCH_DEBUG", "getSavedSeasonPosterUrl MISS: invalid tvId=$tvId or seasonNumber=$seasonNumber")
            return null
        }
        val seasonMeta = metadataCacheRepo.getTvSeasonMetadata(tvId, seasonNumber)
        if (seasonMeta != null && !seasonMeta.posterPath.isNullOrBlank()) {
            Log.d("ARTWORK_MATCH_DEBUG", "ARTWORK CACHE HIT: season poster for tvId=$tvId, season=$seasonNumber -> '${seasonMeta.posterPath}'")
            return seasonMeta.posterPath
        }
        Log.d("ARTWORK_MATCH_DEBUG", "ARTWORK CACHE MISS: season poster for tvId=$tvId, season=$seasonNumber")
        return null
    }

    fun fetchAndCacheSeasonPoster(tvId: Int, seasonNumber: Int, onResult: (String?) -> Unit = {}) {
        if (tvId <= 0 || seasonNumber <= 0) {
            onResult(null)
            return
        }
        val cached = getSavedSeasonPosterUrl(tvId, seasonNumber)
        if (!cached.isNullOrBlank()) {
            onResult(cached)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ARTWORK_MATCH_DEBUG", "Fetching fresh season poster from TMDB for tvId=$tvId, season=$seasonNumber")
            val seasonDetails = com.example.network.GeminiService.fetchTmdbSeasonDetailsByTvId(tvId, seasonNumber)
            if (seasonDetails != null && !seasonDetails.posterPath.isNullOrBlank()) {
                val formatted = com.example.util.PosterCardUIHelper.formatTmdbUrl(seasonDetails.posterPath, "w500")
                val seasonMeta = TvSeasonMetadata(
                    showTmdbId = tvId,
                    seasonNumber = seasonNumber,
                    posterPath = formatted,
                    overview = seasonDetails.overview ?: ""
                )
                metadataCacheRepo.saveTvSeasonMetadata(tvId, seasonNumber, seasonMeta)
                Log.d("ARTWORK_MATCH_DEBUG", "Successfully fetched and saved season poster: tvId=$tvId, season=$seasonNumber, poster='$formatted'")
                withContext(Dispatchers.Main) {
                    onResult(formatted)
                }
            } else {
                Log.d("ARTWORK_MATCH_DEBUG", "Failed to fetch season poster from TMDB for tvId=$tvId, season=$seasonNumber")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun getSavedTmdbPosterUrl(movieId: String, title: String? = null, expectedTmdbId: Int? = null): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic != null && !atomic.movie?.posterPath.isNullOrBlank()) {
            if (expectedTmdbId == null || expectedTmdbId <= 0 || atomic.tmdbId == expectedTmdbId) {
                return atomic.movie?.posterPath
            } else {
                Log.d("TMDB_MATCH_DEBUG", "Atomic cached poster for '$movieId' rejected: atomic TMDB ID (${atomic.tmdbId}) != expected TMDB ID ($expectedTmdbId)")
            }
        }
        if (atomic != null && !atomic.show?.posterPath.isNullOrBlank()) {
            if (expectedTmdbId == null || expectedTmdbId <= 0 || atomic.tmdbId == expectedTmdbId) {
                return atomic.show?.posterPath
            }
        }

        val url = sharedPrefs.getString("tmdb_poster_$movieId", null)
        val cachedTmdbId = sharedPrefs.getInt("tmdb_id_$movieId", 0)
        if (!url.isNullOrBlank()) {
            if (expectedTmdbId == null || expectedTmdbId <= 0 || cachedTmdbId == 0 || cachedTmdbId == expectedTmdbId) {
                return url
            } else {
                Log.d("TMDB_MATCH_DEBUG", "Cached poster for '$movieId' rejected: cached TMDB ID ($cachedTmdbId) != expected TMDB ID ($expectedTmdbId)")
            }
        }

        val fileId = movieId.substringAfterLast("/")
        val fileUrl = sharedPrefs.getString("tmdb_poster_$fileId", null)
        if (!fileUrl.isNullOrBlank()) {
            val fileTmdbId = sharedPrefs.getInt("tmdb_id_$fileId", 0)
            if (expectedTmdbId == null || expectedTmdbId <= 0 || fileTmdbId == 0 || fileTmdbId == expectedTmdbId) {
                return fileUrl
            }
        }
        return null
    }

    fun getSavedTmdbBackdropUrl(movieId: String, title: String? = null): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic != null) {
            val path = atomic.show?.backdropPath?.takeIf { it.isNotBlank() } ?: atomic.movie?.backdropPath?.takeIf { it.isNotBlank() }
            if (!path.isNullOrBlank()) return path
        }
        val pref = sharedPrefs.getString("tmdb_backdrop_$movieId", null)
        if (!pref.isNullOrBlank()) return pref
        if (!title.isNullOrBlank()) {
            val titlePref = sharedPrefs.getString("tmdb_backdrop_$title", null)
            if (!titlePref.isNullOrBlank()) return titlePref
        }
        val fileId = movieId.substringAfterLast("/")
        val filePref = sharedPrefs.getString("tmdb_backdrop_$fileId", null)
        if (!filePref.isNullOrBlank()) return filePref
        return null
    }

    fun saveTmdbPosterUrl(
        movieId: String,
        tmdbUrl: String,
        title: String? = null,
        tmdbId: Int = 0,
        backdropUrl: String? = null
    ) {
        if (tmdbUrl.isNotBlank()) {
            val effectivePosterToSave = tmdbUrl

            val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
            val resolvedTmdbId = if (tmdbId > 0) tmdbId else currentAtomic.tmdbId
            val isTv = currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE
            
            val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(
                tmdbId = resolvedTmdbId,
                title = title ?: currentAtomic.movie?.title ?: "",
                posterPath = effectivePosterToSave,
                backdropPath = backdropUrl ?: currentAtomic.movie?.backdropPath ?: effectivePosterToSave
            )
            val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(
                tmdbId = resolvedTmdbId,
                showTitle = title ?: currentAtomic.show?.showTitle ?: "",
                posterPath = effectivePosterToSave,
                backdropPath = backdropUrl ?: currentAtomic.show?.backdropPath ?: effectivePosterToSave
            )
            val updatedAtomic = currentAtomic.copy(
                tmdbId = resolvedTmdbId,
                movie = if (!isTv) updatedMovie else currentAtomic.movie,
                show = if (isTv) updatedShow else currentAtomic.show
            )
            metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)

            val editor = sharedPrefs.edit()
            editor.putString("tmdb_poster_$movieId", effectivePosterToSave)
            if (tmdbId > 0) editor.putInt("tmdb_id_$movieId", tmdbId)
            if (!backdropUrl.isNullOrBlank()) {
                editor.putString("tmdb_backdrop_$movieId", backdropUrl)
            }
            if (!title.isNullOrBlank()) {
                editor.putString("tmdb_poster_$title", effectivePosterToSave)
                if (tmdbId > 0) editor.putInt("tmdb_id_$title", tmdbId)
                if (!backdropUrl.isNullOrBlank()) {
                    editor.putString("tmdb_backdrop_$title", backdropUrl)
                }
            }
            val fileId = movieId.substringAfterLast("/")
            if (fileId != movieId && fileId.isNotBlank()) {
                editor.putString("tmdb_poster_$fileId", effectivePosterToSave)
                if (tmdbId > 0) editor.putInt("tmdb_id_$fileId", tmdbId)
                if (!backdropUrl.isNullOrBlank()) {
                    editor.putString("tmdb_backdrop_$fileId", backdropUrl)
                }
            }
            editor.apply()

            // Update in active movies list and history immediately
            _moviesList.value = _moviesList.value.map { movie ->
                if (movie.id == movieId || (title != null && movie.title == title)) {
                    movie.copy(
                        posterUrl = effectivePosterToSave,
                        backdropUrl = if (backdropUrl != null && backdropUrl.isNotBlank()) backdropUrl else if (movie.backdropUrl.isBlank() || movie.backdropUrl.contains("unsplash")) effectivePosterToSave else movie.backdropUrl
                    )
                } else {
                    movie
                }
            }
            _history.value = _history.value.map { movie ->
                if (movie.id == movieId || (title != null && movie.title == title)) {
                    movie.copy(
                        posterUrl = effectivePosterToSave,
                        backdropUrl = if (backdropUrl != null && backdropUrl.isNotBlank()) backdropUrl else if (movie.backdropUrl.isBlank() || movie.backdropUrl.contains("unsplash")) effectivePosterToSave else movie.backdropUrl
                    )
                } else {
                    movie
                }
            }
        }
    }

    fun saveTmdbPosterVariants(
        movieId: String,
        cleanUrl: String?,
        logoUrl: String?,
        title: String? = null,
        transparentLogoUrl: String? = null
    ) {
        val effectiveCleanUrl = cleanUrl
        val effectiveLogoUrl = logoUrl

        val editor = sharedPrefs.edit()
        val fileId = movieId.substringAfterLast("/")
        if (!effectiveCleanUrl.isNullOrBlank()) {
            editor.putString("tmdb_clean_poster_$movieId", effectiveCleanUrl)
            if (!title.isNullOrBlank()) editor.putString("tmdb_clean_poster_$title", effectiveCleanUrl)
            if (fileId != movieId && fileId.isNotBlank()) editor.putString("tmdb_clean_poster_$fileId", effectiveCleanUrl)
        }
        if (!effectiveLogoUrl.isNullOrBlank()) {
            editor.putString("tmdb_logo_poster_$movieId", effectiveLogoUrl)
            if (!title.isNullOrBlank()) editor.putString("tmdb_logo_poster_$title", effectiveLogoUrl)
            if (fileId != movieId && fileId.isNotBlank()) editor.putString("tmdb_logo_poster_$fileId", effectiveLogoUrl)
        }
        if (!transparentLogoUrl.isNullOrBlank()) {
            editor.putString("tmdb_transparent_logo_$movieId", transparentLogoUrl)
            if (!title.isNullOrBlank()) editor.putString("tmdb_transparent_logo_$title", transparentLogoUrl)
            if (fileId != movieId && fileId.isNotBlank()) editor.putString("tmdb_transparent_logo_$fileId", transparentLogoUrl)
        }
        val sTitle = if (!title.isNullOrBlank()) com.example.network.GeminiService.cleanShowTitle(title) else null
        if (!sTitle.isNullOrBlank()) {
            if (!effectiveCleanUrl.isNullOrBlank()) editor.putString("tmdb_clean_poster_$sTitle", effectiveCleanUrl)
            if (!effectiveLogoUrl.isNullOrBlank()) editor.putString("tmdb_logo_poster_$sTitle", effectiveLogoUrl)
            if (!transparentLogoUrl.isNullOrBlank()) editor.putString("tmdb_transparent_logo_$sTitle", transparentLogoUrl)
        }
        editor.apply()

        _moviesList.value = _moviesList.value.map { movie ->
            if (movie.id == movieId || (title != null && movie.title == title)) {
                movie.copy(
                    cleanPosterUrl = effectiveCleanUrl ?: movie.cleanPosterUrl,
                    logoPosterUrl = effectiveLogoUrl ?: movie.logoPosterUrl,
                    transparentLogoUrl = transparentLogoUrl ?: movie.transparentLogoUrl
                )
            } else {
                movie
            }
        }
    }

    fun getSavedCleanPosterUrl(movieId: String, title: String? = null): String? {
        val cleanById = sharedPrefs.getString("tmdb_clean_poster_$movieId", null)
        if (!cleanById.isNullOrBlank()) return cleanById
        if (!title.isNullOrBlank()) {
            val cleanByTitle = sharedPrefs.getString("tmdb_clean_poster_$title", null)
            if (!cleanByTitle.isNullOrBlank()) return cleanByTitle
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank()) {
                val cleanByShow = sharedPrefs.getString("tmdb_clean_poster_$sTitle", null)
                if (!cleanByShow.isNullOrBlank()) return cleanByShow
            }
        }
        val sMovieId = com.example.network.GeminiService.cleanShowTitle(movieId)
        if (sMovieId.isNotBlank() && sMovieId != movieId) {
            val cleanByShowId = sharedPrefs.getString("tmdb_clean_poster_$sMovieId", null)
            if (!cleanByShowId.isNullOrBlank()) return cleanByShowId
        }
        val fileId = movieId.substringAfterLast("/")
        if (fileId != movieId && fileId.isNotBlank()) {
            val cleanByFile = sharedPrefs.getString("tmdb_clean_poster_$fileId", null)
            if (!cleanByFile.isNullOrBlank()) return cleanByFile
        }
        return null
    }

    fun getSavedLogoPosterUrl(movieId: String, title: String? = null): String? {
        val logoById = sharedPrefs.getString("tmdb_logo_poster_$movieId", null)
        if (!logoById.isNullOrBlank()) return logoById
        if (!title.isNullOrBlank()) {
            val logoByTitle = sharedPrefs.getString("tmdb_logo_poster_$title", null)
            if (!logoByTitle.isNullOrBlank()) return logoByTitle
            val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
            if (sTitle.isNotBlank()) {
                val logoByShow = sharedPrefs.getString("tmdb_logo_poster_$sTitle", null)
                if (!logoByShow.isNullOrBlank()) return logoByShow
            }
        }
        val sMovieId = com.example.network.GeminiService.cleanShowTitle(movieId)
        if (sMovieId.isNotBlank() && sMovieId != movieId) {
            val logoByShowId = sharedPrefs.getString("tmdb_logo_poster_$sMovieId", null)
            if (!logoByShowId.isNullOrBlank()) return logoByShowId
        }
        val fileId = movieId.substringAfterLast("/")
        if (fileId != movieId && fileId.isNotBlank()) {
            val logoByFile = sharedPrefs.getString("tmdb_logo_poster_$fileId", null)
            if (!logoByFile.isNullOrBlank()) return logoByFile
        }
        return null
    }

    fun getSavedTransparentLogoUrl(movieId: String, title: String? = null): String? {
        val logoById = sharedPrefs.getString("tmdb_transparent_logo_$movieId", null)
        if (!logoById.isNullOrBlank()) return logoById
        if (!title.isNullOrBlank()) {
            val logoByTitle = sharedPrefs.getString("tmdb_transparent_logo_$title", null)
            if (!logoByTitle.isNullOrBlank()) return logoByTitle
        }
        val fileId = movieId.substringAfterLast("/")
        if (fileId != movieId && fileId.isNotBlank()) {
            val logoByFile = sharedPrefs.getString("tmdb_transparent_logo_$fileId", null)
            if (!logoByFile.isNullOrBlank()) return logoByFile
        }
        return null
    }

    fun saveTmdbCastList(movieId: String, castList: List<CastMember>, title: String? = null) {
        if (castList.isNotEmpty()) {
            try {
                val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
                val isTv = currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE
                
                val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(cast = castList)
                val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(cast = castList)
                val updatedAtomic = currentAtomic.copy(
                    movie = if (!isTv) updatedMovie else currentAtomic.movie,
                    show = if (isTv) updatedShow else currentAtomic.show
                )
                metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)

                _moviesList.value = _moviesList.value.map { m ->
                    if (m.id == movieId || (title != null && m.title == title)) {
                        m.copy(cast = castList)
                    } else m
                }
                _history.value = _history.value.map { m ->
                    if (m.id == movieId || (title != null && m.title == title)) {
                        m.copy(cast = castList)
                    } else m
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getSavedTmdbCastList(movieId: String, title: String? = null): List<CastMember> {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.movie?.cast?.isNotEmpty() == true) return atomic.movie.cast
        if (atomic?.show?.cast?.isNotEmpty() == true) return atomic.show.cast
        return emptyList()
    }

    fun getEffectiveCast(movie: Movie): List<CastMember> {
        val cachedCast = getSavedTmdbCastList(movie.id, movie.title)
        if (cachedCast.isNotEmpty()) {
            return cachedCast
        }
        if (movie.cast.isNotEmpty()) {
            return movie.cast
        }
        return emptyList()
    }

    fun saveTmdbOverview(movieId: String, overview: String, title: String? = null) {
        if (overview.isNotBlank()) {
            sharedPrefs.edit().putString("tmdb_overview_$movieId", overview).apply()
            if (!title.isNullOrBlank()) {
                sharedPrefs.edit().putString("tmdb_overview_$title", overview).apply()
            }
            val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
            val isTv = currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE
            
            val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(overview = overview)
            val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(overview = overview)
            val updatedAtomic = currentAtomic.copy(
                movie = if (!isTv) updatedMovie else currentAtomic.movie,
                show = if (isTv) updatedShow else currentAtomic.show
            )
            metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)

            _moviesList.value = _moviesList.value.map { m ->
                if (m.id == movieId || (title != null && m.title == title)) {
                    m.copy(synopsis = overview)
                } else m
            }
        }
    }

    fun saveTmdbGenres(movieId: String, genres: List<String>, title: String? = null, tmdbId: Int = 0, isTv: Boolean = false) {
        val sanitized = com.example.util.TmdbGenreMapper.sanitizeGenres(genres)
        if (sanitized.isNotEmpty()) {
            val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
            val resolvedTmdbId = if (tmdbId > 0) tmdbId else currentAtomic.tmdbId
            val resolvedIsTv = isTv || currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE

            if (resolvedTmdbId > 0) {
                metadataCacheRepo.saveTmdbGenres(resolvedTmdbId, resolvedIsTv, sanitized)
            }

            val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(genres = sanitized, tmdbId = resolvedTmdbId)
            val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(genres = sanitized, tmdbId = resolvedTmdbId)
            val updatedAtomic = currentAtomic.copy(
                tmdbId = resolvedTmdbId,
                mediaType = if (resolvedIsTv) MediaType.TV_SHOW else MediaType.MOVIE,
                movie = if (!resolvedIsTv) updatedMovie else currentAtomic.movie,
                show = if (resolvedIsTv) updatedShow else currentAtomic.show
            )
            metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)

            _moviesList.value = _moviesList.value.map { m ->
                if (m.id == movieId || (title != null && m.title == title)) {
                    m.copy(genre = sanitized)
                } else m
            }
            _history.value = _history.value.map { m ->
                if (m.id == movieId || (title != null && m.title == title)) {
                    m.copy(genre = sanitized)
                } else m
            }
        }
    }

    fun getSavedTmdbGenres(movieId: String, title: String? = null, tmdbId: Int = 0, isTv: Boolean = false): List<String> {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        val resolvedTmdbId = if (tmdbId > 0) tmdbId else (atomic?.tmdbId ?: sharedPrefs.getInt("tmdb_id_$movieId", 0))
        val resolvedIsTv = isTv || atomic?.mediaType == MediaType.TV_SHOW || atomic?.mediaType == MediaType.TV_EPISODE

        if (resolvedTmdbId > 0) {
            val cached = metadataCacheRepo.getTmdbGenres(resolvedTmdbId, resolvedIsTv)
            if (cached.isNotEmpty()) return cached
        }

        val movieGenres = atomic?.movie?.genres
        if (!movieGenres.isNullOrEmpty()) {
            val sanitized = com.example.util.TmdbGenreMapper.sanitizeGenres(movieGenres)
            if (sanitized.isNotEmpty()) return sanitized
        }
        val showGenres = atomic?.show?.genres
        if (!showGenres.isNullOrEmpty()) {
            val sanitized = com.example.util.TmdbGenreMapper.sanitizeGenres(showGenres)
            if (sanitized.isNotEmpty()) return sanitized
        }
        return emptyList()
    }

    fun saveTmdbMovieRating(movieId: String, rating: Float, title: String? = null, tmdbId: Int = 0, isTv: Boolean = false) {
        if (rating <= 0f) return
        sharedPrefs.edit().putFloat("tmdb_rating_$movieId", rating).apply()
        if (!title.isNullOrBlank()) {
            sharedPrefs.edit().putFloat("tmdb_rating_$title", rating).apply()
            val norm = title.lowercase().replace("marvel's", "").replace("marvels", "").replace("[^a-z0-9]".toRegex(), " ").trim()
            if (norm.isNotBlank()) {
                sharedPrefs.edit().putFloat("tmdb_rating_$norm", rating).apply()
            }
        }
        if (tmdbId > 0) {
            sharedPrefs.edit().putFloat("tmdb_rating_id_$tmdbId", rating).apply()
        }
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
        val resolvedIsTv = isTv || currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE
        val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(rating = rating)
        val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(rating = rating)
        val updatedAtomic = currentAtomic.copy(
            movie = if (!resolvedIsTv) updatedMovie else currentAtomic.movie,
            show = if (resolvedIsTv) updatedShow else currentAtomic.show
        )
        metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)
    }

    fun getSavedTmdbMovieRating(movieId: String, title: String? = null): Float? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.movie != null && atomic.movie.rating > 0f) {
            return atomic.movie.rating
        }
        if (atomic?.show != null && atomic.show.rating > 0f) {
            return atomic.show.rating
        }
        val pref = sharedPrefs.getFloat("tmdb_rating_$movieId", 0.0f)
        if (pref > 0f) return pref

        if (!title.isNullOrBlank()) {
            val titlePref = sharedPrefs.getFloat("tmdb_rating_$title", 0.0f)
            if (titlePref > 0f) return titlePref
            val norm = title.lowercase().replace("marvel's", "").replace("marvels", "").replace("[^a-z0-9]".toRegex(), " ").trim()
            if (norm.isNotBlank()) {
                val normPref = sharedPrefs.getFloat("tmdb_rating_$norm", 0.0f)
                if (normPref > 0f) return normPref
            }
        }
        val tmdbId = atomic?.tmdbId ?: sharedPrefs.getInt("tmdb_id_$movieId", 0)
        if (tmdbId > 0) {
            val idPref = sharedPrefs.getFloat("tmdb_rating_id_$tmdbId", 0.0f)
            if (idPref > 0f) return idPref
        }
        return null
    }

    fun saveTmdbTomatoScore(movieId: String, tomatoScore: String, title: String? = null) {
        if (tomatoScore.isBlank()) return
        sharedPrefs.edit().putString("tmdb_tomato_$movieId", tomatoScore).apply()
        if (!title.isNullOrBlank()) {
            sharedPrefs.edit().putString("tmdb_tomato_$title", tomatoScore).apply()
        }
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
        val isTv = currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE
        val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(tomatoScore = tomatoScore)
        val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(tomatoScore = tomatoScore)
        val updatedAtomic = currentAtomic.copy(
            movie = if (!isTv) updatedMovie else currentAtomic.movie,
            show = if (isTv) updatedShow else currentAtomic.show
        )
        metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)
    }

    fun getSavedTmdbTomatoScore(movieId: String, title: String? = null): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (!atomic?.movie?.tomatoScore.isNullOrBlank()) return atomic!!.movie!!.tomatoScore
        if (!atomic?.show?.tomatoScore.isNullOrBlank()) return atomic!!.show!!.tomatoScore
        val pref = sharedPrefs.getString("tmdb_tomato_$movieId", null)
        if (!pref.isNullOrBlank()) return pref
        if (!title.isNullOrBlank()) {
            val titlePref = sharedPrefs.getString("tmdb_tomato_$title", null)
            if (!titlePref.isNullOrBlank()) return titlePref
        }
        return null
    }

    fun saveTmdbReleaseYear(movieId: String, releaseYear: String, title: String? = null) {
        if (releaseYear.isBlank()) return
        sharedPrefs.edit().putString("tmdb_year_$movieId", releaseYear).apply()
        if (!title.isNullOrBlank()) {
            sharedPrefs.edit().putString("tmdb_year_$title", releaseYear).apply()
        }
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId)
        val isTv = currentAtomic.mediaType == MediaType.TV_SHOW || currentAtomic.mediaType == MediaType.TV_EPISODE
        val updatedMovie = (currentAtomic.movie ?: MovieMetadata()).copy(releaseYear = releaseYear)
        val updatedShow = (currentAtomic.show ?: TvShowMetadata()).copy(releaseYear = releaseYear)
        val updatedAtomic = currentAtomic.copy(
            movie = if (!isTv) updatedMovie else currentAtomic.movie,
            show = if (isTv) updatedShow else currentAtomic.show
        )
        metadataCacheRepo.saveAtomicMetadata(movieId, updatedAtomic)
    }

    fun getSavedTmdbReleaseYear(movieId: String, title: String? = null): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (!atomic?.movie?.releaseYear.isNullOrBlank()) return atomic!!.movie!!.releaseYear
        if (!atomic?.show?.releaseYear.isNullOrBlank()) return atomic!!.show!!.releaseYear
        val pref = sharedPrefs.getString("tmdb_year_$movieId", null)
        if (!pref.isNullOrBlank()) return pref
        if (!title.isNullOrBlank()) {
            val titlePref = sharedPrefs.getString("tmdb_year_$title", null)
            if (!titlePref.isNullOrBlank()) return titlePref
        }
        return null
    }

    fun getSavedTmdbEpisodeOverview(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.overview.isNotBlank()) {
            return atomic.episode.overview
        }
        val pref = sharedPrefs.getString("tmdb_ep_overview_$movieId", null)
        if (!pref.isNullOrBlank()) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.overview.isNotBlank()) {
                    return isoMeta.episode.overview
                }
                val isoPref = sharedPrefs.getString("tmdb_ep_overview_tv_${tvId}_s${sNum}_e${eNum}", null)
                if (!isoPref.isNullOrBlank()) return isoPref
            }
        }
        return null
    }

    fun saveTmdbEpisodeOverview(movieId: String, overview: String) {
        if (overview.isBlank()) return
        sharedPrefs.edit().putString("tmdb_ep_overview_$movieId", overview).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(overview = overview)
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun getSavedTmdbOverview(movieId: String, title: String? = null): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (!atomic?.movie?.overview.isNullOrBlank()) return atomic!!.movie!!.overview
        if (!atomic?.show?.overview.isNullOrBlank()) return atomic!!.show!!.overview
        val pref = sharedPrefs.getString("tmdb_overview_$movieId", null)
        if (!pref.isNullOrBlank()) return pref
        if (!title.isNullOrBlank()) {
            val titlePref = sharedPrefs.getString("tmdb_overview_$title", null)
            if (!titlePref.isNullOrBlank()) return titlePref
        }
        val epOverview = getSavedTmdbEpisodeOverview(movieId)
        if (!epOverview.isNullOrBlank()) return epOverview
        return null
    }

    fun getSavedTmdbEpisodeName(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.episodeTitle.isNotBlank()) {
            return atomic.episode.episodeTitle
        }
        val pref = sharedPrefs.getString("tmdb_ep_name_$movieId", null)
        if (!pref.isNullOrBlank()) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.episodeTitle.isNotBlank()) {
                    return isoMeta.episode.episodeTitle
                }
                val isoPref = sharedPrefs.getString("tmdb_ep_name_tv_${tvId}_s${sNum}_e${eNum}", null)
                if (!isoPref.isNullOrBlank()) return isoPref
            }
        }
        return null
    }

    fun saveTmdbEpisodeName(movieId: String, episodeName: String) {
        if (episodeName.isBlank()) return
        sharedPrefs.edit().putString("tmdb_ep_name_$movieId", episodeName).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(episodeTitle = episodeName)
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun getSavedTmdbEpisodeStill(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.stillPath.isNotBlank()) {
            return atomic.episode.stillPath
        }
        val pref = sharedPrefs.getString("tmdb_ep_still_$movieId", null)
        if (!pref.isNullOrBlank()) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.stillPath.isNotBlank()) {
                    return isoMeta.episode.stillPath
                }
                val isoPref = sharedPrefs.getString("tmdb_ep_still_tv_${tvId}_s${sNum}_e${eNum}", null)
                if (!isoPref.isNullOrBlank()) return isoPref
            }
        }
        return null
    }

    fun saveTmdbEpisodeStill(movieId: String, stillPath: String) {
        if (stillPath.isBlank()) return
        sharedPrefs.edit().putString("tmdb_ep_still_$movieId", stillPath).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(stillPath = stillPath)
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun getSavedTmdbEpisodeRating(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Float? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.rating > 0f) {
            return atomic.episode.rating
        }
        val pref = sharedPrefs.getFloat("tmdb_ep_rating_$movieId", 0.0f)
        if (pref > 0f) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.rating > 0f) {
                    return isoMeta.episode.rating
                }
            }
        }
        return null
    }

    fun saveTmdbEpisodeRating(movieId: String, rating: Float) {
        if (rating <= 0f) return
        sharedPrefs.edit().putFloat("tmdb_ep_rating_$movieId", rating).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(rating = rating)
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun getSavedTmdbEpisodeAirDate(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.releaseDate.isNotBlank()) {
            return atomic.episode.releaseDate
        }
        val pref = sharedPrefs.getString("tmdb_ep_airdate_$movieId", null)
        if (!pref.isNullOrBlank()) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.releaseDate.isNotBlank()) {
                    return isoMeta.episode.releaseDate
                }
            }
        }
        return null
    }

    fun saveTmdbEpisodeAirDate(movieId: String, airDate: String) {
        if (airDate.isBlank()) return
        sharedPrefs.edit().putString("tmdb_ep_airdate_$movieId", airDate).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(releaseDate = airDate)
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun getSavedTmdbEpisodeRuntime(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Int? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.duration.isNotBlank()) {
            val mins = atomic.episode.duration.replace("m", "").trim().toIntOrNull()
            if (mins != null && mins > 0) return mins
        }
        val pref = sharedPrefs.getInt("tmdb_ep_runtime_$movieId", 0)
        if (pref > 0) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.duration.isNotBlank()) {
                    val mins = isoMeta.episode.duration.replace("m", "").trim().toIntOrNull()
                    if (mins != null && mins > 0) return mins
                }
            }
        }
        return null
    }

    fun saveTmdbEpisodeRuntime(movieId: String, runtimeMins: Int) {
        if (runtimeMins <= 0) return
        sharedPrefs.edit().putInt("tmdb_ep_runtime_$movieId", runtimeMins).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(duration = "${runtimeMins}m")
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun saveTmdbMovieRuntime(movieId: String, runtimeMins: Int, title: String? = null) {
        if (runtimeMins <= 0) return
        sharedPrefs.edit().putInt("tmdb_movie_runtime_$movieId", runtimeMins).apply()
        if (!title.isNullOrBlank()) {
            sharedPrefs.edit().putInt("tmdb_movie_runtime_$title", runtimeMins).apply()
            val norm = com.example.util.EpisodeUtils.getNormalizedShowTitle(title)
            if (norm.isNotBlank()) {
                sharedPrefs.edit().putInt("tmdb_movie_runtime_$norm", runtimeMins).apply()
            }
        }
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.MOVIE)
        val movieMeta = (currentAtomic.movie ?: MovieMetadata()).copy(duration = "${runtimeMins}m")
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(movie = movieMeta))
    }

    fun getSavedTmdbMovieRuntime(movieId: String, title: String? = null): Int? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.movie != null && atomic.movie.duration.isNotBlank()) {
            val mins = atomic.movie.duration.replace("m", "").trim().toIntOrNull()
            if (mins != null && mins > 0) return mins
        }
        val pref = sharedPrefs.getInt("tmdb_movie_runtime_$movieId", 0)
        if (pref > 0) return pref

        if (!title.isNullOrBlank()) {
            val titlePref = sharedPrefs.getInt("tmdb_movie_runtime_$title", 0)
            if (titlePref > 0) return titlePref
            val norm = com.example.util.EpisodeUtils.getNormalizedShowTitle(title)
            if (norm.isNotBlank()) {
                val normPref = sharedPrefs.getInt("tmdb_movie_runtime_$norm", 0)
                if (normPref > 0) return normPref
            }
        }
        return null
    }

    fun getSavedTmdbEpisodeVoteCount(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Int? {
        val atomic = metadataCacheRepo.getAtomicMetadata(movieId)
        if (atomic?.episode != null && atomic.episode.voteCount > 0) {
            return atomic.episode.voteCount
        }
        val pref = sharedPrefs.getInt("tmdb_ep_votecount_$movieId", 0)
        if (pref > 0) return pref

        val parsedSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movieId)
        val sNum = seasonNumber ?: parsedSe.season
        val eNum = episodeNumber ?: parsedSe.episode
        val sTitle = showTitle ?: com.example.network.GeminiService.cleanShowTitle(movieId)

        if (sNum != null && eNum != null) {
            val tvId = getTvIdForShow(sTitle)
            if (tvId > 0) {
                val isoMeta = metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum)
                if (isoMeta?.episode != null && isoMeta.episode.voteCount > 0) {
                    return isoMeta.episode.voteCount
                }
            }
        }
        return null
    }

    fun saveTmdbEpisodeVoteCount(movieId: String, voteCount: Int) {
        if (voteCount <= 0) return
        sharedPrefs.edit().putInt("tmdb_ep_votecount_$movieId", voteCount).apply()
        val currentAtomic = metadataCacheRepo.getAtomicMetadata(movieId) ?: AtomicMediaMetadata(id = movieId, mediaType = MediaType.TV_EPISODE)
        val epMeta = (currentAtomic.episode ?: TvEpisodeMetadata()).copy(voteCount = voteCount)
        metadataCacheRepo.saveAtomicMetadata(movieId, currentAtomic.copy(episode = epMeta))
    }

    fun getTvIdForShow(showTitle: String): Int {
        if (showTitle.isBlank()) return 0
        val savedId = sharedPrefs.getInt("tmdb_id_$showTitle", 0)
        if (savedId > 0) return savedId
        val norm = showTitle.lowercase().replace("marvel's", "").replace("marvels", "").replace("[^a-z0-9]".toRegex(), " ").trim()
        val normId = sharedPrefs.getInt("tmdb_id_$norm", 0)
        if (normId > 0) return normId
        val atomic = metadataCacheRepo.getAtomicMetadata(showTitle)
        if (atomic != null && atomic.tmdbId > 0) return atomic.tmdbId
        return 0
    }

    fun fetchAndCacheEpisodeNames(showEpisodes: List<Movie>) {
        if (showEpisodes.isEmpty()) return
        val sampleCandidate = showEpisodes.first()
        val hasTvMarker = sampleCandidate.title.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2}|season\s*\d+|episode\s*\d+)\b""")) ||
                sampleCandidate.id.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2})\b"""))
        val isTvTarget = if (sampleCandidate.category.equals("Movies", ignoreCase = true) || sampleCandidate.category.equals("Home Videos", ignoreCase = true)) {
            false
        } else if (hasTvMarker) {
            true
        } else {
            sampleCandidate.category.equals("Shows", ignoreCase = true) && showEpisodes.size > 1
        }
        if (!isTvTarget) {
            Log.d("EPISODE_DEBUG", "Skipping fetchAndCacheEpisodeNames because '${sampleCandidate.title}' is a movie/single video.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sampleEp = showEpisodes.first()
            val cleanedInfo = com.example.network.GeminiService.sanitizeFileName(sampleEp.title)
            val showTitle = com.example.network.GeminiService.parseShowTitleFromMovieTitle(cleanedInfo.title)

            // 1. Resolve canonical TMDB TV ID for show stack
            var tvId = getTvIdForShow(showTitle)
            if (tvId <= 0) {
                val tvMatch = com.example.network.GeminiService.fetchTmdbTvAndCredits(showTitle, cleanedInfo.year)
                tvId = tvMatch?.movieId ?: 0
                if (tvId > 0) {
                    sharedPrefs.edit().putInt("tmdb_id_$showTitle", tvId).apply()
                    val norm = showTitle.lowercase().replace("marvel's", "").replace("marvels", "").replace("[^a-z0-9]".toRegex(), " ").trim()
                    if (norm.isNotBlank()) {
                        sharedPrefs.edit().putInt("tmdb_id_$norm", tvId).apply()
                    }
                }
            }

            Log.d("EPISODE_DEBUG", "==================================================")
            Log.d("EPISODE_DEBUG", "FETCH EPISODE DETAILS FOR SHOW: '$showTitle'")
            Log.d("EPISODE_DEBUG", "RESOLVED CANONICAL TMDB TV ID: $tvId")
            Log.d("EPISODE_DEBUG", "EPISODES TO PROCESS           : ${showEpisodes.size}")
            Log.d("EPISODE_DEBUG", "==================================================")

            showEpisodes.forEachIndexed { idx, ep ->
                val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(ep.title, ep.filePath ?: "")
                val sNum = seInfo.season ?: com.example.util.LocalSeasonParser.parseSeasonNumber(ep.filePath ?: "", ep.title)
                val eNum = seInfo.episode ?: (idx + 1)

                val cachedIsoMeta = if (tvId > 0) metadataCacheRepo.getTvEpisodeMetadata(tvId, sNum, eNum) else null
                val cachedName = cachedIsoMeta?.episode?.episodeTitle ?: getSavedTmdbEpisodeName(ep.id, showTitle, sNum, eNum)
                val cachedStill = cachedIsoMeta?.episode?.stillPath ?: getSavedTmdbEpisodeStill(ep.id, showTitle, sNum, eNum)
                val cachedOverview = cachedIsoMeta?.episode?.overview ?: getSavedTmdbEpisodeOverview(ep.id, showTitle, sNum, eNum)

                if (cachedName.isNullOrBlank() || cachedStill.isNullOrBlank() || cachedOverview.isNullOrBlank()) {
                    val details = if (tvId > 0) {
                        com.example.network.GeminiService.fetchTmdbEpisodeDetailsByTvId(tvId, sNum, eNum)
                    } else {
                        com.example.network.GeminiService.fetchTmdbEpisodeDetails(showTitle, sNum, eNum)
                    }

                    if (details != null) {
                        val epName = details.name ?: ""
                        val epStill = details.stillPath ?: ""
                        val epOverview = details.overview ?: ""
                        val epRating = details.voteAverage ?: 0.0f
                        val epAirDate = details.airDate ?: ""
                        val epRuntime = details.runtime ?: 0
                        val epVoteCount = details.voteCount ?: 0

                        if (epName.isNotBlank()) {
                            saveTmdbEpisodeName(ep.id, epName)
                            if (tvId > 0) {
                                sharedPrefs.edit().putString("tmdb_ep_name_tv_${tvId}_s${sNum}_e${eNum}", epName).apply()
                            }
                        }
                        if (epStill.isNotBlank()) {
                            saveTmdbEpisodeStill(ep.id, epStill)
                            preloadImageToCoilCache(com.example.util.PosterCardUIHelper.formatTmdbUrl(epStill, "w500"))
                            if (tvId > 0) {
                                sharedPrefs.edit().putString("tmdb_ep_still_tv_${tvId}_s${sNum}_e${eNum}", epStill).apply()
                            }
                        }
                        if (epOverview.isNotBlank()) {
                            saveTmdbEpisodeOverview(ep.id, epOverview)
                            if (tvId > 0) {
                                sharedPrefs.edit().putString("tmdb_ep_overview_tv_${tvId}_s${sNum}_e${eNum}", epOverview).apply()
                            }
                        }
                        if (epRating > 0f) {
                            saveTmdbEpisodeRating(ep.id, epRating)
                        }
                        if (epAirDate.isNotBlank()) {
                            saveTmdbEpisodeAirDate(ep.id, epAirDate)
                        }
                        if (epRuntime > 0) {
                            saveTmdbEpisodeRuntime(ep.id, epRuntime)
                        }
                        if (epVoteCount > 0) {
                            saveTmdbEpisodeVoteCount(ep.id, epVoteCount)
                        }

                        if (tvId > 0) {
                            val atomicEp = com.example.model.AtomicMediaMetadata(
                                id = ep.id,
                                tmdbId = tvId,
                                mediaType = com.example.model.MediaType.TV_EPISODE,
                                episode = com.example.model.TvEpisodeMetadata(
                                    showTmdbId = tvId,
                                    seasonNumber = sNum,
                                    episodeNumber = eNum,
                                    episodeTitle = epName,
                                    stillPath = epStill,
                                    overview = epOverview,
                                    releaseDate = epAirDate,
                                    duration = if (epRuntime > 0) "${epRuntime}m" else "",
                                    rating = epRating,
                                    voteCount = epVoteCount,
                                    filePath = ep.filePath ?: ep.id
                                )
                            )
                            metadataCacheRepo.saveTvEpisodeMetadata(tvId, sNum, eNum, atomicEp)
                            metadataCacheRepo.saveAtomicMetadata(ep.id, atomicEp)
                        }
                    }
                }
            }
            // Compute cached models on background IO dispatcher first to avoid Main Thread jank
            val updatedMovies = _moviesList.value.map { populateMovieFromCache(it) }
            val updatedHistory = _history.value.map { populateMovieFromCache(it) }
            withContext(Dispatchers.Main) {
                _moviesList.value = updatedMovies
                _history.value = updatedHistory

                // Safely update episodes queue metadata if player is open without disrupting active playback
                val currentUiState = _playerUiState.value
                if (currentUiState != null) {
                    val updatedQueue = currentUiState.episodesQueue.map { qEp ->
                        val qName = getSavedTmdbEpisodeName(qEp.id, qEp.showTitle, qEp.seasonNumber, qEp.episodeNumber)
                        val qStill = getSavedTmdbEpisodeStill(qEp.id, qEp.showTitle, qEp.seasonNumber, qEp.episodeNumber)
                        val resolvedTitle = if (!qName.isNullOrBlank()) {
                            com.example.util.EpisodeMetadataFixer.getCleanEpisodeTitle(qName, qEp.episodeNumber, qEp.showTitle, isFromTmdbApi = true)
                        } else {
                            qEp.episodeTitle
                        }
                        qEp.copy(
                            episodeTitle = resolvedTitle,
                            posterUrl = if (!qStill.isNullOrBlank()) qStill else qEp.posterUrl
                        )
                    }
                    _playerUiState.value = currentUiState.copy(
                        episodesQueue = updatedQueue
                    )
                }
            }
        }
    }

    fun populateMovieFromCache(movie: Movie): Movie {
        val isTvShow = movie.category.equals("Shows", ignoreCase = true) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
        val atomic = metadataCacheRepo.getAtomicMetadata(movie.id)
        val cachedTmdbId = atomic?.tmdbId ?: sharedPrefs.getInt("tmdb_id_${movie.id}", 0)

        val cachedPoster = getSavedTmdbPosterUrl(movie.id, movie.title)
        val cachedBackdrop = getSavedTmdbBackdropUrl(movie.id, movie.title)
        val cachedCast = getSavedTmdbCastList(movie.id, movie.title)
        val cachedOverview = getSavedTmdbOverview(movie.id, movie.title)
        val cachedGenres = getSavedTmdbGenres(movie.id, movie.title, cachedTmdbId, isTvShow)
        val cachedRating = getSavedTmdbMovieRating(movie.id, movie.title)
        val cachedTomato = getSavedTmdbTomatoScore(movie.id, movie.title)
        val cachedYear = getSavedTmdbReleaseYear(movie.id, movie.title)

        val formattedCachedPoster = if (!cachedPoster.isNullOrBlank()) com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedPoster, "w780") else ""
        val finalPoster = if (formattedCachedPoster.isNotBlank()) formattedCachedPoster else com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.posterUrl, "w780")
        val formattedCachedBackdrop = if (!cachedBackdrop.isNullOrBlank()) com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedBackdrop, "w1280") else ""
        val finalBackdrop = if (formattedCachedBackdrop.isNotBlank()) {
            formattedCachedBackdrop
        } else if (movie.backdropUrl.isNotBlank() && !movie.backdropUrl.contains("unsplash") && !movie.backdropUrl.contains("t3")) {
            com.example.util.PosterCardUIHelper.formatBackdropUrl(movie.backdropUrl, "w1280")
        } else {
            finalPoster
        }
        val finalCast = if (cachedCast.isNotEmpty()) cachedCast else movie.cast
        val finalSynopsis = if (!cachedOverview.isNullOrBlank()) cachedOverview else movie.synopsis
        val finalGenres = if (cachedGenres.isNotEmpty()) cachedGenres else com.example.util.TmdbGenreMapper.sanitizeGenres(movie.genre)
        val finalRating = if (cachedRating != null && cachedRating > 0f) cachedRating else movie.rating
        val finalTomato = if (!cachedTomato.isNullOrBlank()) cachedTomato else movie.tomatoScore
        val finalYear = if (!cachedYear.isNullOrBlank()) cachedYear else movie.releaseYear

        val cachedClean = getSavedCleanPosterUrl(movie.id, movie.title)
        val cachedLogo = getSavedLogoPosterUrl(movie.id, movie.title)
        val cachedTransparentLogo = getSavedTransparentLogoUrl(movie.id, movie.title)

        val finalClean = if (!cachedClean.isNullOrBlank()) com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedClean, "w780") else movie.cleanPosterUrl
        val finalLogo = if (!cachedLogo.isNullOrBlank()) com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedLogo, "w780") else movie.logoPosterUrl
        val finalTransparentLogo = if (!cachedTransparentLogo.isNullOrBlank()) com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedTransparentLogo, "w500") else movie.transparentLogoUrl

        if (finalPoster != movie.posterUrl || finalBackdrop != movie.backdropUrl || finalCast != movie.cast || finalSynopsis != movie.synopsis || finalGenres != movie.genre || finalClean != movie.cleanPosterUrl || finalLogo != movie.logoPosterUrl || finalTransparentLogo != movie.transparentLogoUrl || finalRating != movie.rating || finalTomato != movie.tomatoScore || finalYear != movie.releaseYear) {
            return movie.copy(
                posterUrl = finalPoster,
                backdropUrl = finalBackdrop,
                cast = finalCast,
                synopsis = finalSynopsis,
                genre = finalGenres,
                cleanPosterUrl = finalClean,
                logoPosterUrl = finalLogo,
                transparentLogoUrl = finalTransparentLogo,
                rating = finalRating,
                tomatoScore = finalTomato,
                releaseYear = finalYear
            )
        }
        return movie
    }

    // Background Metadata Preloader Pipeline & Shimmer Loading State
    private val prefetchSemaphore = Semaphore(2)
    private val activePrefetchingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val completedPrefetchIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingRetryMovies = java.util.concurrent.ConcurrentLinkedQueue<Movie>()

    private val _tmdbLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val tmdbLoadingIds: StateFlow<Set<String>> = _tmdbLoadingIds.asStateFlow()

    private val _isLibrarySyncing = MutableStateFlow(false)
    val isLibrarySyncing: StateFlow<Boolean> = _isLibrarySyncing.asStateFlow()

    private fun markTmdbLoading(keys: Collection<String>) {
        val valid = keys.map { it.trim() }.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        synchronized(_tmdbLoadingIds) {
            _tmdbLoadingIds.value = _tmdbLoadingIds.value + valid
        }
    }

    private fun unmarkTmdbLoading(keys: Collection<String>) {
        val valid = keys.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (valid.isEmpty()) return
        synchronized(_tmdbLoadingIds) {
            val next = _tmdbLoadingIds.value - valid
            _tmdbLoadingIds.value = next
            if (next.isEmpty()) {
                _isLibrarySyncing.value = false
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        val app = getApplication<Application>()
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true

        // 1. Check primary active network
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return true
            }
        }

        // 2. Mobile data & secondary network interfaces
        for (net in cm.allNetworks) {
            val capabilities = cm.getNetworkCapabilities(net) ?: continue
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return true
            }
        }

        // 3. Fallback for older Android OS versions
        @Suppress("DEPRECATION")
        val activeInfo = cm.activeNetworkInfo
        @Suppress("DEPRECATION")
        if (activeInfo != null && activeInfo.isConnectedOrConnecting) {
            return true
        }

        // Default to true so cellular or unmetered connections are never blocked
        return true
    }

    fun preloadImageToCoilCache(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val app = getApplication<Application>()
            val request = ImageRequest.Builder(app)
                .data(url)
                .dispatcher(Dispatchers.IO)
                .build()
            Coil.imageLoader(app).enqueue(request)
        } catch (e: Exception) {
            // Non-critical background task
        }
    }

    fun drainPendingRetryMovies() {
        val toRetry = LinkedHashMap<String, Movie>()
        while (!pendingRetryMovies.isEmpty()) {
            pendingRetryMovies.poll()?.let { toRetry[it.id] = it }
        }
        // Also check loaded library items that still lack a resolved TMDB poster
        _moviesList.value.forEach { movie ->
            if (!activePrefetchingIds.contains(movie.id)) {
                val hasPoster = movie.posterUrl.isNotBlank() &&
                    !movie.posterUrl.contains("unsplash") &&
                    !movie.posterUrl.contains("t3")
                if (!hasPoster) {
                    completedPrefetchIds.remove(movie.id)
                    toRetry[movie.id] = movie
                }
            }
        }
        if (toRetry.isNotEmpty()) {
            Log.d("PREFETCH", "Draining ${toRetry.size} pending/incomplete movies upon network availability")
            preloadMetadataInBackground(toRetry.values.toList())
        }
    }

    private fun scheduleNetworkSwitchRecovery(reason: String) {
        com.example.InfuseApplication.evictIdleConnections(reason)
        drainPendingRetryMovies()
        // Cellular 4G/5G bearers and NAT64/CLAT routes often take ~1s after onAvailable to settle
        viewModelScope.launch(Dispatchers.IO) {
            delay(1200L)
            drainPendingRetryMovies()
        }
    }

    private fun registerNetworkCallback() {
        try {
            val app = getApplication<Application>()
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

            // 1. Default network callback for system-level switchovers (Wi-Fi <-> Cellular)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        scheduleNetworkSwitchRecovery("default_network_available")
                    }
                    override fun onLost(network: Network) {
                        com.example.InfuseApplication.evictIdleConnections("default_network_lost")
                    }
                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            if (com.example.BuildConfig.DEBUG) {
                                val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                                val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                Log.d("TMDB_NET_DEBUG", "Default network capabilities: cellular=$isCellular, wifi=$isWifi")
                            }
                            drainPendingRetryMovies()
                        }
                    }
                })
            }

            // 2. General internet callback matching cellular, Wi-Fi, Ethernet, VPN (including metered connections)
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scheduleNetworkSwitchRecovery("internet_network_available")
                }
                override fun onLost(network: Network) {
                    com.example.InfuseApplication.evictIdleConnections("internet_network_lost")
                }
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        drainPendingRetryMovies()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("PREFETCH", "Failed to register network callback: ${e.message}", e)
        }
    }

    /**
     * Resolves and preloads TMDB metadata and poster/backdrop images completely in the background
     * as soon as local media is detected/imported, saving everything directly to cache.
     */
    fun preloadMetadataInBackground(items: List<Movie>) {
        if (items.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val itemsToProcess = items.filter { movie ->
                if (com.example.util.HomeVideoFilter.isHomeVideo(movie.filePath, movie.title) ||
                    com.example.network.GeminiService.isHomeVideoFilename(movie.title) ||
                    movie.category.equals("Home Videos", ignoreCase = true)) {
                    return@filter false
                }
                val atomic = metadataCacheRepo.getAtomicMetadata(movie.id)
                val cachedTmdbId = atomic?.tmdbId ?: sharedPrefs.getInt("tmdb_id_${movie.id}", 0)
                val isTvShow = movie.category.equals("Shows", ignoreCase = true) ||
                        movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
                val cachedPoster = atomic?.movie?.posterPath ?: getSavedTmdbPosterUrl(movie.id, movie.title)
                val customPoster = getSavedCustomPosterUrl(movie.id)
                val cachedCast = getSavedTmdbCastList(movie.id, movie.title)
                val cachedGenres = getSavedTmdbGenres(movie.id, movie.title, cachedTmdbId, isTvShow)
                val hasValidPoster = (!cachedPoster.isNullOrBlank() && !cachedPoster.contains("t3") && !cachedPoster.contains("unsplash")) || !customPoster.isNullOrBlank()
                val hasValidCast = cachedCast.isNotEmpty()
                val hasValidGenres = cachedGenres.isNotEmpty()

                if (hasValidPoster && hasValidCast && hasValidGenres && cachedTmdbId > 0) {
                    if (!cachedPoster.isNullOrBlank()) {
                        preloadImageToCoilCache(cachedPoster)
                    }
                    val backdrop = getSavedTmdbBackdropUrl(movie.id, movie.title)
                    if (!backdrop.isNullOrBlank()) {
                        preloadImageToCoilCache(backdrop)
                    }
                    return@filter false
                }

                val stableId = movie.id
                if (activePrefetchingIds.contains(stableId) || (completedPrefetchIds.contains(stableId) && hasValidPoster)) {
                    return@filter false
                }
                true
            }

            if (itemsToProcess.isEmpty()) return@launch

            _isLibrarySyncing.value = true

            val (tvEpisodes, regularMovies) = itemsToProcess.partition { m ->
                val hasMarker = m.title.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2}|season\s*\d+|episode\s*\d+)\b""")) ||
                        m.id.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2})\b"""))
                if (m.category.equals("Movies", ignoreCase = true) || m.category.equals("Home Videos", ignoreCase = true)) {
                    false
                } else if (hasMarker) {
                    true
                } else {
                    m.category.equals("Shows", ignoreCase = true)
                }
            }

            val initialKeys = mutableListOf<String>()
            regularMovies.forEach { m ->
                initialKeys.add(m.id)
                initialKeys.add(m.title)
            }
            tvEpisodes.forEach { ep ->
                initialKeys.add(ep.id)
                val cleaned = com.example.network.GeminiService.sanitizeFileName(ep.title)
                val sTitle = com.example.network.GeminiService.parseShowTitleFromMovieTitle(cleaned.title)
                initialKeys.add(sTitle)
                initialKeys.add(com.example.util.EpisodeUtils.getNormalizedShowTitle(sTitle))
            }
            markTmdbLoading(initialKeys)

            // 1. Process regular movies
            regularMovies.forEach { movie ->
                val stableId = movie.id
                activePrefetchingIds.add(stableId)
                markTmdbLoading(listOf(movie.id, movie.title))
                prefetchSemaphore.acquire()
                try {
                    val cleanedInfo = com.example.network.GeminiService.sanitizeFileName(movie.title)
                    val tmdbResult = com.example.network.GeminiService.fetchTmdbMovieAndCredits(cleanedInfo.title, cleanedInfo.year)
                    if (tmdbResult != null) {
                        val freshTmdbId = tmdbResult.movieId ?: 0
                        val freshPosterUrl = tmdbResult.posterUrl ?: ""
                        val freshBackdropUrl = tmdbResult.backdropUrl ?: freshPosterUrl

                        if (freshPosterUrl.isNotBlank()) {
                            val formattedPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(freshPosterUrl, "w780")
                            val formattedBackdrop = com.example.util.PosterCardUIHelper.formatBackdropUrl(freshBackdropUrl, "w1280")
                            saveTmdbPosterUrl(movie.id, formattedPoster, movie.title, freshTmdbId, formattedBackdrop)
                            preloadImageToCoilCache(formattedPoster)
                            preloadImageToCoilCache(formattedBackdrop)
                        }
                        if (!tmdbResult.cleanPosterUrl.isNullOrBlank() || !tmdbResult.logoPosterUrl.isNullOrBlank() || !tmdbResult.transparentLogoUrl.isNullOrBlank()) {
                            saveTmdbPosterVariants(
                                movieId = movie.id,
                                cleanUrl = tmdbResult.cleanPosterUrl,
                                logoUrl = tmdbResult.logoPosterUrl,
                                title = movie.title,
                                transparentLogoUrl = tmdbResult.transparentLogoUrl
                            )
                            if (!tmdbResult.cleanPosterUrl.isNullOrBlank()) {
                                preloadImageToCoilCache(com.example.util.PosterCardUIHelper.formatTmdbUrl(tmdbResult.cleanPosterUrl, "w780"))
                            }
                        }
                        if (tmdbResult.cast.isNotEmpty()) {
                            saveTmdbCastList(movie.id, tmdbResult.cast, movie.title)
                        }
                        if (!tmdbResult.overview.isNullOrBlank()) {
                            saveTmdbOverview(movie.id, tmdbResult.overview, movie.title)
                        }
                        if (tmdbResult.genres.isNotEmpty()) {
                            saveTmdbGenres(movie.id, tmdbResult.genres, movie.title, freshTmdbId, false)
                        }
                        if (tmdbResult.voteAverage != null && tmdbResult.voteAverage > 0f) {
                            saveTmdbMovieRating(movie.id, tmdbResult.voteAverage, movie.title, freshTmdbId, false)
                        }
                        if (!tmdbResult.tomatoScore.isNullOrBlank()) {
                            saveTmdbTomatoScore(movie.id, tmdbResult.tomatoScore, movie.title)
                        }
                        if (!tmdbResult.releaseYear.isNullOrBlank()) {
                            saveTmdbReleaseYear(movie.id, tmdbResult.releaseYear, movie.title)
                        }
                        if (tmdbResult.runtime != null && tmdbResult.runtime > 0) {
                            saveTmdbMovieRuntime(movie.id, tmdbResult.runtime, movie.title)
                        }
                        sharedPrefs.edit()
                            .remove("tmdb_ep_runtime_${movie.id}")
                            .remove("tmdb_ep_airdate_${movie.id}")
                            .remove("tmdb_ep_overview_${movie.id}")
                            .apply()

                        withContext(Dispatchers.Main) {
                            _moviesList.value = _moviesList.value.map { m ->
                                if (m.id == movie.id || (movie.title.isNotBlank() && m.title == movie.title)) populateMovieFromCache(m) else m
                            }
                        }
                        completedPrefetchIds.add(stableId)
                    } else {
                        if (com.example.BuildConfig.DEBUG) {
                            Log.w("PREFETCH", "TMDB movie prefetch returned null for '${movie.title}', queuing for retry")
                        }
                        pendingRetryMovies.offer(movie)
                    }
                } catch (e: Exception) {
                    Log.e("PREFETCH", "Error prefetching movie '${movie.title}': ${e.message}", e)
                    pendingRetryMovies.offer(movie)
                } finally {
                    activePrefetchingIds.remove(stableId)
                    unmarkTmdbLoading(listOf(movie.id, movie.title))
                    prefetchSemaphore.release()
                }
                delay(60)
            }

            // 2. Process TV shows consolidated by show title
            val showsGrouped = tvEpisodes.groupBy { ep ->
                val cleaned = com.example.network.GeminiService.sanitizeFileName(ep.title)
                com.example.network.GeminiService.parseShowTitleFromMovieTitle(cleaned.title)
            }

            showsGrouped.forEach { (showTitle, episodes) ->
                val groupKeys = episodes.map { it.id } + listOf(
                    showTitle,
                    com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitle)
                )
                markTmdbLoading(groupKeys)
                prefetchSemaphore.acquire()
                try {
                    val sampleEp = episodes.first()
                    val sampleCleaned = com.example.network.GeminiService.sanitizeFileName(sampleEp.title)
                    val tmdbResult = com.example.network.GeminiService.fetchTmdbTvAndCredits(showTitle, sampleCleaned.year)

                        if (tmdbResult != null) {
                            val freshTvId = tmdbResult.movieId ?: 0
                            val freshPosterUrl = tmdbResult.posterUrl ?: ""
                            val freshBackdropUrl = tmdbResult.backdropUrl ?: freshPosterUrl

                            val formattedPoster = if (freshPosterUrl.isNotBlank()) com.example.util.PosterCardUIHelper.formatTmdbUrl(freshPosterUrl, "w780") else ""
                            val formattedBackdrop = if (freshBackdropUrl.isNotBlank()) com.example.util.PosterCardUIHelper.formatBackdropUrl(freshBackdropUrl, "w1280") else ""

                            if (formattedPoster.isNotBlank()) {
                                preloadImageToCoilCache(formattedPoster)
                            }
                            if (formattedBackdrop.isNotBlank()) {
                                preloadImageToCoilCache(formattedBackdrop)
                            }

                            if (freshTvId > 0) {
                                sharedPrefs.edit().putInt("tmdb_id_$showTitle", freshTvId).apply()
                            }
                            if (formattedPoster.isNotBlank()) {
                                saveTmdbPosterUrl(showTitle, formattedPoster, showTitle, freshTvId, formattedBackdrop)
                            }
                            if (!tmdbResult.cleanPosterUrl.isNullOrBlank() || !tmdbResult.logoPosterUrl.isNullOrBlank() || !tmdbResult.transparentLogoUrl.isNullOrBlank()) {
                                saveTmdbPosterVariants(
                                    movieId = showTitle,
                                    cleanUrl = tmdbResult.cleanPosterUrl,
                                    logoUrl = tmdbResult.logoPosterUrl,
                                    title = showTitle,
                                    transparentLogoUrl = tmdbResult.transparentLogoUrl
                                )
                            }

                            episodes.forEach { ep ->
                                if (formattedPoster.isNotBlank()) {
                                    saveTmdbPosterUrl(ep.id, formattedPoster, ep.title, freshTvId, formattedBackdrop)
                                }
                                if (!tmdbResult.cleanPosterUrl.isNullOrBlank() || !tmdbResult.logoPosterUrl.isNullOrBlank() || !tmdbResult.transparentLogoUrl.isNullOrBlank()) {
                                    saveTmdbPosterVariants(
                                        movieId = ep.id,
                                        cleanUrl = tmdbResult.cleanPosterUrl,
                                        logoUrl = tmdbResult.logoPosterUrl,
                                        title = ep.title,
                                        transparentLogoUrl = tmdbResult.transparentLogoUrl
                                    )
                                }
                                if (tmdbResult.cast.isNotEmpty()) {
                                    saveTmdbCastList(ep.id, tmdbResult.cast, ep.title)
                                }
                                if (!tmdbResult.overview.isNullOrBlank()) {
                                    saveTmdbOverview(ep.id, tmdbResult.overview, ep.title)
                                }
                                if (tmdbResult.genres.isNotEmpty()) {
                                    saveTmdbGenres(ep.id, tmdbResult.genres, ep.title, freshTvId, true)
                                }
                                if (tmdbResult.voteAverage != null && tmdbResult.voteAverage > 0f) {
                                    saveTmdbMovieRating(ep.id, tmdbResult.voteAverage, ep.title, freshTvId, true)
                                }
                                if (!tmdbResult.releaseYear.isNullOrBlank()) {
                                    saveTmdbReleaseYear(ep.id, tmdbResult.releaseYear, ep.title)
                                }
                                completedPrefetchIds.add(ep.id)
                            }

                            withContext(Dispatchers.Main) {
                                _moviesList.value = _moviesList.value.map { m ->
                                    if (episodes.any { it.id == m.id } || com.example.network.GeminiService.cleanShowTitle(m.title) == showTitle) {
                                        populateMovieFromCache(m)
                                    } else m
                                }
                            }

                            fetchAndCacheEpisodeNames(episodes)
                        } else {
                            if (com.example.BuildConfig.DEBUG) {
                                Log.w("PREFETCH", "TMDB TV prefetch returned null for '$showTitle', queuing ${episodes.size} episodes for retry")
                            }
                            episodes.forEach { pendingRetryMovies.offer(it) }
                        }
                } catch (e: Exception) {
                    Log.e("PREFETCH", "Error prefetching TV show '$showTitle': ${e.message}", e)
                    episodes.forEach { pendingRetryMovies.offer(it) }
                } finally {
                    unmarkTmdbLoading(groupKeys)
                    prefetchSemaphore.release()
                }
                delay(60)
            }
            if (_tmdbLoadingIds.value.isEmpty()) {
                _isLibrarySyncing.value = false
            }
        }
    }

    fun fetchAndCacheTmdbDetails(movie: Movie) {
        val hasTvMarker = movie.title.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2}|season\s*\d+|episode\s*\d+)\b""")) ||
                movie.id.contains(Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2})\b"""))
        val isTvShow = if (movie.category.equals("Movies", ignoreCase = true) || movie.category.equals("Home Videos", ignoreCase = true)) {
            false
        } else if (hasTvMarker) {
            true
        } else {
            movie.category.equals("Shows", ignoreCase = true)
        }

        val atomic = metadataCacheRepo.getAtomicMetadata(movie.id)
        val cachedTmdbId = atomic?.tmdbId ?: sharedPrefs.getInt("tmdb_id_${movie.id}", 0)
        val cachedPoster = atomic?.movie?.posterPath ?: getSavedTmdbPosterUrl(movie.id, movie.title)
        val customPoster = getSavedCustomPosterUrl(movie.id)
        val cachedCast = getSavedTmdbCastList(movie.id, movie.title)
        val cachedGenres = getSavedTmdbGenres(movie.id, movie.title, cachedTmdbId, isTvShow)

        // 1. Immediately populate state from local cache
        _moviesList.value = _moviesList.value.map { m ->
            if (m.id == movie.id) populateMovieFromCache(m) else m
        }

        val hasValidPoster = (!cachedPoster.isNullOrBlank() && !cachedPoster.contains("t3") && !cachedPoster.contains("unsplash")) || !customPoster.isNullOrBlank()
        val hasValidCast = cachedCast.isNotEmpty()
        val hasValidGenres = cachedGenres.isNotEmpty()

        // 2. Cache-first optimization: only skip if we have valid cached poster, cast, genres AND valid tmdbId > 0
        if (hasValidPoster && hasValidCast && hasValidGenres && cachedTmdbId > 0) {
            if (!cachedPoster.isNullOrBlank()) {
                preloadImageToCoilCache(cachedPoster)
            }
            Log.d("TMDB_MATCH_DEBUG", "fetchAndCacheTmdbDetails: Skipped search for '${movie.title}' because valid cached poster, cast, genres and TMDB ID ($cachedTmdbId) already exist.")
            return
        }

        val cleanedInfo = com.example.network.GeminiService.sanitizeFileName(movie.title)
        val showTitleKey = if (isTvShow) {
            com.example.network.GeminiService.parseShowTitleFromMovieTitle(cleanedInfo.title)
        } else ""
        val loadingKeys = buildList {
            add(movie.id)
            add(movie.title)
            if (showTitleKey.isNotBlank()) {
                add(showTitleKey)
                add(com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitleKey))
            }
        }
        markTmdbLoading(loadingKeys)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmdbResult = if (isTvShow) {
                    com.example.network.GeminiService.fetchTmdbTvAndCredits(showTitleKey, cleanedInfo.year)
                } else {
                    com.example.network.GeminiService.fetchTmdbMovieAndCredits(cleanedInfo.title, cleanedInfo.year)
                }

                if (tmdbResult != null) {
                    val freshTmdbId = tmdbResult.movieId ?: 0
                    val freshPosterUrl = tmdbResult.posterUrl ?: ""
                    val freshBackdropUrl = tmdbResult.backdropUrl ?: freshPosterUrl

                    Log.d("TMDB_MATCH_DEBUG", "FETCHED TMDB RESULT FOR '${movie.title}': ID=$freshTmdbId, posterUrl='$freshPosterUrl'")

                    // REQUIREMENT 6 & 7: If cached TMDB ID differs from fresh TMDB ID, invalidate cached wrong artwork
                    if (cachedTmdbId > 0 && freshTmdbId > 0 && cachedTmdbId != freshTmdbId) {
                        Log.d("TMDB_MATCH_DEBUG", "STALE ARTWORK DETECTED for '${movie.title}'! Cached TMDB ID ($cachedTmdbId) != Fresh TMDB ID ($freshTmdbId). Invalidating cached artwork.")
                        metadataCacheRepo.invalidateMetadata(movie.id)
                        sharedPrefs.edit()
                            .remove("tmdb_poster_${movie.id}")
                            .remove("tmdb_id_${movie.id}")
                            .remove("tmdb_backdrop_${movie.id}")
                            .apply()
                    }

                    if (freshPosterUrl.isNotBlank()) {
                        val formattedPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(freshPosterUrl, "w780")
                        val formattedBackdrop = com.example.util.PosterCardUIHelper.formatBackdropUrl(freshBackdropUrl, "w1280")
                        Log.d("TMDB_MATCH_DEBUG", "Saving verified TMDB poster: ID=$freshTmdbId, Poster='$formattedPoster'")
                        saveTmdbPosterUrl(movie.id, formattedPoster, movie.title, freshTmdbId, formattedBackdrop)
                        preloadImageToCoilCache(formattedPoster)
                        preloadImageToCoilCache(formattedBackdrop)
                    }
                    if (!tmdbResult.cleanPosterUrl.isNullOrBlank() || !tmdbResult.logoPosterUrl.isNullOrBlank() || !tmdbResult.transparentLogoUrl.isNullOrBlank()) {
                        saveTmdbPosterVariants(
                            movieId = movie.id,
                            cleanUrl = tmdbResult.cleanPosterUrl,
                            logoUrl = tmdbResult.logoPosterUrl,
                            title = movie.title,
                            transparentLogoUrl = tmdbResult.transparentLogoUrl
                        )
                        if (!tmdbResult.cleanPosterUrl.isNullOrBlank()) {
                            preloadImageToCoilCache(com.example.util.PosterCardUIHelper.formatTmdbUrl(tmdbResult.cleanPosterUrl, "w780"))
                        }
                    }
                    if (tmdbResult.cast.isNotEmpty()) {
                        saveTmdbCastList(movie.id, tmdbResult.cast, movie.title)
                    }
                    if (!tmdbResult.overview.isNullOrBlank()) {
                        saveTmdbOverview(movie.id, tmdbResult.overview, movie.title)
                    }
                    if (tmdbResult.genres.isNotEmpty()) {
                        saveTmdbGenres(movie.id, tmdbResult.genres, movie.title, freshTmdbId, isTvShow)
                    }
                    if (tmdbResult.voteAverage != null && tmdbResult.voteAverage > 0f) {
                        saveTmdbMovieRating(movie.id, tmdbResult.voteAverage, movie.title, freshTmdbId, isTvShow)
                    }
                    if (!tmdbResult.tomatoScore.isNullOrBlank()) {
                        saveTmdbTomatoScore(movie.id, tmdbResult.tomatoScore, movie.title)
                    }
                    if (!tmdbResult.releaseYear.isNullOrBlank()) {
                        saveTmdbReleaseYear(movie.id, tmdbResult.releaseYear, movie.title)
                    }
                    if (tmdbResult.runtime != null && tmdbResult.runtime > 0) {
                        if (isTvShow) {
                            saveTmdbEpisodeRuntime(movie.id, tmdbResult.runtime)
                        } else {
                            saveTmdbMovieRuntime(movie.id, tmdbResult.runtime, movie.title)
                        }
                    }
                    if (!isTvShow) {
                        sharedPrefs.edit()
                            .remove("tmdb_ep_runtime_${movie.id}")
                            .remove("tmdb_ep_airdate_${movie.id}")
                            .remove("tmdb_ep_overview_${movie.id}")
                            .apply()
                    }
                    withContext(Dispatchers.Main) {
                        _moviesList.value = _moviesList.value.map { m ->
                            if (m.id == movie.id || (movie.title.isNotBlank() && m.title == movie.title)) populateMovieFromCache(m) else m
                        }
                        _history.value = _history.value.map { m ->
                            if (m.id == movie.id || (movie.title.isNotBlank() && m.title == movie.title)) populateMovieFromCache(m) else m
                        }
                    }
                } else {
                    Log.w("TMDB_MATCH_DEBUG", "No TMDB result returned for '${movie.title}'; queuing for network retry")
                    pendingRetryMovies.offer(movie)
                }
            } catch (e: Exception) {
                Log.e("TMDB_MATCH_DEBUG", "Exception in fetchAndCacheTmdbDetails for '${movie.title}': ${e.message}", e)
                pendingRetryMovies.offer(movie)
            } finally {
                unmarkTmdbLoading(loadingKeys)
            }
        }
    }

    fun fetchAndCacheTmdbPoster(movie: Movie) {
        fetchAndCacheTmdbDetails(movie)
    }

    /**
     * Card poster variant: Uses the texted poster (artwork containing title) by default
     * for Home screen cards, Library cards, Continue Watching cards, Search result cards,
     * Favorite cards, and other poster card components.
     * Strictly uses Card Poster Override and never Detail Poster Override.
     */
    fun getCardPosterUrl(movie: Movie): String {
        val cardOverride = getCardPosterOverride(movie.id, movie.title)
        if (!cardOverride.isNullOrBlank()) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cardOverride, "w780")
        }
        val isShow = movie.category.equals("Shows", ignoreCase = true) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
        val sTitle = if (isShow) com.example.network.GeminiService.cleanShowTitle(movie.title) else ""

        if (sTitle.isNotBlank()) {
            val cardSeriesOverride = getCardPosterOverride(sTitle, sTitle)
            if (!cardSeriesOverride.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cardSeriesOverride, "w780")
            }
            val cachedLogoSeries = getSavedLogoPosterUrl(sTitle, sTitle)
            if (!cachedLogoSeries.isNullOrBlank() && !cachedLogoSeries.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedLogoSeries, "w780")
            }
            val cachedTmdbSeries = getSavedTmdbPosterUrl(sTitle, sTitle)
            if (!cachedTmdbSeries.isNullOrBlank() && !cachedTmdbSeries.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedTmdbSeries, "w780")
            }
        }
        if (movie.logoPosterUrl.isNotBlank() && !movie.logoPosterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.logoPosterUrl, "w780")
        }
        val cachedLogo = getSavedLogoPosterUrl(movie.id, movie.title)
        if (!cachedLogo.isNullOrBlank() && !cachedLogo.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedLogo, "w780")
        }
        val cachedTmdb = getSavedTmdbPosterUrl(movie.id, movie.title)
        if (!cachedTmdb.isNullOrBlank() && !cachedTmdb.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedTmdb, "w780")
        }
        if (movie.posterUrl.isNotBlank() && !movie.posterUrl.contains("unsplash") && !movie.posterUrl.contains("still_path")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.posterUrl, "w780")
        }

        // Graceful fallback to textless poster if texted poster is unavailable
        if (sTitle.isNotBlank()) {
            val cachedCleanSeries = getSavedCleanPosterUrl(sTitle, sTitle)
            if (!cachedCleanSeries.isNullOrBlank() && !cachedCleanSeries.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedCleanSeries, "w780")
            }
        }
        if (movie.cleanPosterUrl.isNotBlank() && !movie.cleanPosterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.cleanPosterUrl, "w780")
        }
        val cachedClean = getSavedCleanPosterUrl(movie.id, movie.title)
        if (!cachedClean.isNullOrBlank() && !cachedClean.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedClean, "w780")
        }

        return getEffectivePosterUrl(movie)
    }

    fun getCardPosterUrlForShow(showTitle: String): String {
        val cardSeriesOverride = getCardPosterOverride(showTitle, showTitle)
        if (!cardSeriesOverride.isNullOrBlank()) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cardSeriesOverride, "w780")
        }
        val logoSeries = getSavedLogoPosterUrl(showTitle, showTitle)
        if (!logoSeries.isNullOrBlank() && !logoSeries.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(logoSeries, "w780")
        }
        val seriesPoster = getSavedTmdbPosterUrl(showTitle, showTitle)
        if (!seriesPoster.isNullOrBlank() && !seriesPoster.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(seriesPoster, "w780")
        }
        val cleanSeries = getSavedCleanPosterUrl(showTitle, showTitle)
        if (!cleanSeries.isNullOrBlank() && !cleanSeries.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cleanSeries, "w780")
        }
        val backdrop = getSavedTmdbBackdropUrl(showTitle, showTitle)
        if (!backdrop.isNullOrBlank() && !backdrop.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatBackdropUrl(backdrop, "w1280")
        }
        return ""
    }

    /**
     * Backwards-compatible delegates for existing card call sites:
     * Card components display the texted poster variant by default.
     */
    fun getCleanPosterUrl(movie: Movie): String = getCardPosterUrl(movie)
    fun getCleanPosterUrlForShow(showTitle: String): String = getCardPosterUrlForShow(showTitle)

    /**
     * Detail Screen poster variant: Uses the textless poster (artwork without title text) by default
     * for both movies and TV series.
     * Strictly uses Detail Poster Override and never Card Poster Override.
     * Gracefully falls back to texted poster if textless poster is unavailable.
     */
    fun getDetailPosterUrl(movie: Movie, candidateSeriesMovie: Movie? = null): String {
        val detailOverride = getDetailPosterOverride(movie.id, movie.title)
        if (!detailOverride.isNullOrBlank()) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(detailOverride, "w780")
        }
        val isShow = movie.category.equals("Shows", ignoreCase = true) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
        val sTitle = if (isShow) com.example.network.GeminiService.cleanShowTitle(movie.title) else ""

        if (sTitle.isNotBlank()) {
            val detailSeriesOverride = getDetailPosterOverride(sTitle, sTitle)
            if (!detailSeriesOverride.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(detailSeriesOverride, "w780")
            }
            val cachedCleanSeries = getSavedCleanPosterUrl(sTitle, sTitle)
            if (!cachedCleanSeries.isNullOrBlank() && !cachedCleanSeries.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedCleanSeries, "w780")
            }
        }

        if (candidateSeriesMovie != null && candidateSeriesMovie.cleanPosterUrl.isNotBlank() && !candidateSeriesMovie.cleanPosterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(candidateSeriesMovie.cleanPosterUrl, "w780")
        }

        if (movie.cleanPosterUrl.isNotBlank() && !movie.cleanPosterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.cleanPosterUrl, "w780")
        }
        val cachedClean = getSavedCleanPosterUrl(movie.id, movie.title)
        if (!cachedClean.isNullOrBlank() && !cachedClean.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedClean, "w780")
        }

        // Graceful fallback if textless poster is unavailable: use best available texted poster
        if (sTitle.isNotBlank()) {
            val cachedLogoSeries = getSavedLogoPosterUrl(sTitle, sTitle)
            if (!cachedLogoSeries.isNullOrBlank() && !cachedLogoSeries.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedLogoSeries, "w780")
            }
            val cachedTmdbSeries = getSavedTmdbPosterUrl(sTitle, sTitle)
            if (!cachedTmdbSeries.isNullOrBlank() && !cachedTmdbSeries.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedTmdbSeries, "w780")
            }
        }
        if (candidateSeriesMovie != null && candidateSeriesMovie.logoPosterUrl.isNotBlank() && !candidateSeriesMovie.logoPosterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(candidateSeriesMovie.logoPosterUrl, "w780")
        }
        if (movie.logoPosterUrl.isNotBlank() && !movie.logoPosterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.logoPosterUrl, "w780")
        }
        val cachedLogo = getSavedLogoPosterUrl(movie.id, movie.title)
        if (!cachedLogo.isNullOrBlank() && !cachedLogo.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedLogo, "w780")
        }
        val cachedTmdb = getSavedTmdbPosterUrl(movie.id, movie.title)
        if (!cachedTmdb.isNullOrBlank() && !cachedTmdb.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedTmdb, "w780")
        }
        if (movie.posterUrl.isNotBlank() && !movie.posterUrl.contains("unsplash") && !movie.posterUrl.contains("still_path")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.posterUrl, "w780")
        }

        return getEffectivePosterUrl(movie)
    }

    /**
     * Backwards-compatible delegate for Detail Screen
     */
    fun getLogoPosterUrl(movie: Movie): String = getDetailPosterUrl(movie)

    /**
     * Determines whether the active Detail Screen poster has embedded title/logo text.
     * When textless poster is active, returns false so TMDB official logo / cinematic title displays.
     * When texted poster is active (via manual override or fallback), returns true so title is not duplicated.
     */
    fun isEffectiveDetailPosterTextBearing(movie: Movie, candidateSeriesMovie: Movie? = null): Boolean {
        val sTitle = com.example.network.GeminiService.cleanShowTitle(movie.title)
        val hasDetailOverride = getDetailPosterOverride(movie.id, movie.title) != null ||
                (sTitle.isNotBlank() && getDetailPosterOverride(sTitle, sTitle) != null)
        if (hasDetailOverride) {
            return isDetailPosterTextBearing(movie.id, sTitle.ifBlank { movie.title })
        }
        val isShow = movie.category.equals("Shows", ignoreCase = true) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
        if (isShow && sTitle.isNotBlank()) {
            val cachedCleanSeries = getSavedCleanPosterUrl(sTitle, sTitle)
            if (!cachedCleanSeries.isNullOrBlank() && !cachedCleanSeries.contains("unsplash")) {
                return false
            }
        }
        if (candidateSeriesMovie != null && candidateSeriesMovie.cleanPosterUrl.isNotBlank() && !candidateSeriesMovie.cleanPosterUrl.contains("unsplash")) {
            return false
        }
        if (movie.cleanPosterUrl.isNotBlank() && !movie.cleanPosterUrl.contains("unsplash")) {
            return false
        }
        val cachedClean = getSavedCleanPosterUrl(movie.id, movie.title)
        if (!cachedClean.isNullOrBlank() && !cachedClean.contains("unsplash")) {
            return false
        }
        return true
    }

    /**
     * Resolves the main series/movie poster for Continue Watching cards.
     * Continue Watching cards display the texted poster variant by default.
     */
    fun getDetailScreenMainPosterUrl(movie: Movie, candidateSeriesMovie: Movie? = null): String {
        val target = candidateSeriesMovie ?: movie
        return getCardPosterUrl(target)
    }

    /**
     * TMDB Transparent Title Logo for Media Detail header overlay.
     */
    fun getTransparentLogoUrl(movie: Movie, initialMovie: Movie? = null): String {
        val isShow = movie.category.equals("Shows", ignoreCase = true) ||
                (initialMovie != null && initialMovie.category.equals("Shows", ignoreCase = true)) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
        val rawTitle = if (initialMovie != null && initialMovie.title.isNotBlank()) initialMovie.title else movie.title
        val sTitle = if (isShow) com.example.network.GeminiService.cleanShowTitle(rawTitle) else ""

        if (sTitle.isNotBlank()) {
            val cachedSeriesLogo = getSavedTransparentLogoUrl(sTitle, sTitle)
            if (!cachedSeriesLogo.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedSeriesLogo, "w500")
            }
        }
        if (initialMovie != null && initialMovie.transparentLogoUrl.isNotBlank()) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(initialMovie.transparentLogoUrl, "w500")
        }
        if (movie.transparentLogoUrl.isNotBlank()) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.transparentLogoUrl, "w500")
        }
        if (initialMovie != null) {
            val cachedInitLogo = getSavedTransparentLogoUrl(initialMovie.id, initialMovie.title)
            if (!cachedInitLogo.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedInitLogo, "w500")
            }
        }
        val cachedLogo = getSavedTransparentLogoUrl(movie.id, movie.title)
        if (!cachedLogo.isNullOrBlank()) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedLogo, "w500")
        }
        return ""
    }

    suspend fun fetchAndCacheLogoUrl(movie: Movie, title: String, isTv: Boolean, year: String? = null): String? {
        val existing = getTransparentLogoUrl(movie)
        if (existing.isNotBlank()) return existing

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val fetched = com.example.network.GeminiService.fetchTmdbLogoOnlySuspend(title, isTv, year)
            if (!fetched.isNullOrBlank()) {
                val formatted = com.example.util.PosterCardUIHelper.formatTmdbUrl(fetched, "original")
                saveTmdbPosterVariants(movieId = movie.id, cleanUrl = null, logoUrl = null, transparentLogoUrl = formatted, title = title)
                if (isTv) {
                    val sTitle = com.example.network.GeminiService.cleanShowTitle(title)
                    if (sTitle.isNotBlank()) {
                        saveTmdbPosterVariants(movieId = sTitle, cleanUrl = null, logoUrl = null, transparentLogoUrl = formatted, title = sTitle)
                    }
                }
                formatted
            } else null
        }
    }

    suspend fun fetchAvailableTmdbPosters(
        movie: Movie,
        isTvShow: Boolean,
        showTitleOverride: String? = null
    ): List<com.example.network.GeminiService.TmdbPosterOption> {
        val cleanedInfo = com.example.network.GeminiService.sanitizeFileName(movie.title)
        val queryTitle = if (isTvShow) {
            if (!showTitleOverride.isNullOrBlank()) {
                showTitleOverride
            } else {
                com.example.network.GeminiService.cleanShowTitle(movie.title)
            }
        } else {
            cleanedInfo.title.ifBlank { movie.title }
        }

        var knownTmdbId = 0
        if (isTvShow) {
            knownTmdbId = getTvIdForShow(queryTitle)
        }
        if (knownTmdbId <= 0) {
            knownTmdbId = metadataCacheRepo.getAtomicMetadata(movie.id)?.tmdbId ?: 0
        }
        if (knownTmdbId <= 0) {
            knownTmdbId = sharedPrefs.getInt("tmdb_id_${movie.id}", 0)
        }
        if (knownTmdbId <= 0 && queryTitle.isNotBlank()) {
            knownTmdbId = sharedPrefs.getInt("tmdb_id_$queryTitle", 0)
        }

        val year = cleanedInfo.year
            ?: getSavedTmdbReleaseYear(movie.id, queryTitle)
            ?: movie.releaseYear.takeIf { it.isNotBlank() }

        return com.example.network.GeminiService.fetchTmdbAvailablePostersSuspend(
            cleanTitle = queryTitle,
            isTv = isTvShow,
            year = year,
            knownTmdbId = knownTmdbId
        )
    }

    suspend fun searchTmdbTitlesForPoster(
        query: String,
        isTvShow: Boolean
    ): List<com.example.network.GeminiService.TmdbTitleSearchCandidate> {
        return com.example.network.GeminiService.searchTmdbTitlesForPosterSuspend(
            query = query,
            preferTv = isTvShow
        )
    }

    suspend fun fetchAvailableTmdbPostersForCandidate(
        candidate: com.example.network.GeminiService.TmdbTitleSearchCandidate
    ): List<com.example.network.GeminiService.TmdbPosterOption> {
        val fetched = com.example.network.GeminiService.fetchTmdbAvailablePostersSuspend(
            cleanTitle = candidate.title,
            isTv = candidate.isTv,
            year = candidate.releaseYear.takeIf { it.isNotBlank() },
            knownTmdbId = candidate.tmdbId
        )
        if (fetched.isNotEmpty()) return fetched
        if (candidate.posterFullUrl.isNotBlank()) {
            return listOf(
                com.example.network.GeminiService.TmdbPosterOption(
                    filePath = candidate.posterFullUrl.substringAfterLast("/"),
                    thumbUrl = candidate.posterThumbUrl.ifBlank { candidate.posterFullUrl },
                    fullUrl = candidate.posterFullUrl,
                    languageLabel = "Primary",
                    resolutionLabel = "HD",
                    voteAverage = 10.0,
                    tmdbId = candidate.tmdbId,
                    hasEmbeddedText = true
                )
            )
        }
        return emptyList()
    }

    suspend fun analyzePosterAndIdentifyMetadata(
        imageInput: Any?,
        context: Context?,
        fallbackHintTitle: String? = null,
        isTv: Boolean = false
    ): com.example.network.GeminiService.PosterAnalysisResult {
        return com.example.network.GeminiService.analyzePosterImageAndIdentifyMetadata(
            imageInput = imageInput,
            context = context,
            fallbackHintTitle = fallbackHintTitle,
            preferTv = isTv
        )
    }

    suspend fun fetchMetadataForCandidate(
        candidate: com.example.network.GeminiService.TmdbTitleSearchCandidate
    ): Pair<com.example.network.ScrapedMediaMetadata?, com.example.network.GeminiService.TmdbMovieResult?> {
        return com.example.network.GeminiService.fetchMetadataForCandidate(candidate)
    }

    fun applyPosterAndMatchedMetadata(
        targetMovie: Movie,
        customPosterUrl: String,
        metadata: com.example.network.ScrapedMediaMetadata,
        tmdbId: Int? = null,
        isTvShow: Boolean = false,
        showTitle: String? = null,
        hasEmbeddedText: Boolean = true,
        isCardOverride: Boolean = false
    ) {
        val trimmedUrl = customPosterUrl.trim()
        val formattedUrl = if (trimmedUrl.isNotBlank()) {
            com.example.util.PosterCardUIHelper.formatTmdbUrl(trimmedUrl, "w780")
        } else ""

        // 1. Save Poster Override (Card or Detail)
        if (isCardOverride) {
            saveCardPosterOverride(
                movieId = targetMovie.id,
                showTitle = if (isTvShow) showTitle else null,
                customUrl = formattedUrl,
                hasEmbeddedText = hasEmbeddedText
            )
        } else {
            saveDetailPosterOverride(
                movieId = targetMovie.id,
                customUrl = formattedUrl,
                hasEmbeddedText = hasEmbeddedText,
                isTvShow = isTvShow,
                showTitle = showTitle
            )
        }

        val finalTitle = metadata.title.ifBlank { targetMovie.title }
        val finalPoster = if (formattedUrl.isNotBlank()) formattedUrl else (metadata.posterUrl ?: targetMovie.posterUrl)

        // 2. Persistent Atomic Metadata Storage
        val castMembers = metadata.cast.map { CastMember(it.name, it.character, it.imageUrl) }
        val atomic = AtomicMediaMetadata(
            id = targetMovie.id,
            tmdbId = tmdbId ?: 0,
            mediaType = if (isTvShow) com.example.model.MediaType.TV_SHOW else com.example.model.MediaType.MOVIE,
            movie = if (!isTvShow) MovieMetadata(
                tmdbId = tmdbId ?: 0,
                title = finalTitle,
                overview = metadata.description,
                releaseYear = metadata.releaseYear,
                posterPath = finalPoster,
                rating = metadata.rating ?: 0f,
                tomatoScore = metadata.tomatoScore ?: "",
                genres = metadata.genres,
                cast = castMembers
            ) else null,
            show = if (isTvShow) TvShowMetadata(
                tmdbId = tmdbId ?: 0,
                showTitle = finalTitle,
                overview = metadata.description,
                releaseYear = metadata.releaseYear,
                posterPath = finalPoster,
                rating = metadata.rating ?: 0f,
                tomatoScore = metadata.tomatoScore ?: "",
                genres = metadata.genres,
                cast = castMembers
            ) else null
        )
        metadataCacheRepo.saveAtomicMetadata(targetMovie.id, atomic)

        val sTitle = if (isTvShow) (showTitle ?: com.example.network.GeminiService.cleanShowTitle(targetMovie.title)) else null
        if (!sTitle.isNullOrBlank()) {
            metadataCacheRepo.saveAtomicMetadata(sTitle, atomic)
        }

        // Save TMDB ID & Genres persistently
        if (tmdbId != null && tmdbId > 0) {
            sharedPrefs.edit().putInt("tmdb_id_${targetMovie.id}", tmdbId).apply()
            if (!sTitle.isNullOrBlank()) {
                sharedPrefs.edit().putInt("tmdb_id_$sTitle", tmdbId).apply()
            }
            if (metadata.genres.isNotEmpty()) {
                metadataCacheRepo.saveTmdbGenres(tmdbId, isTvShow, metadata.genres)
            }
        }

        // 3. In-memory update of Movies List & Watch History
        val currentMovies = _moviesList.value
        val updatedMovies = currentMovies.map { movie ->
            val isTarget = if (isTvShow && !sTitle.isNullOrBlank()) {
                movie.id == targetMovie.id || com.example.network.GeminiService.cleanShowTitle(movie.title).equals(sTitle, ignoreCase = true)
            } else {
                movie.id == targetMovie.id
            }
            if (isTarget) {
                movie.copy(
                    title = if (!isTvShow) finalTitle else movie.title, // Preserve episode titles for TV shows!
                    posterUrl = finalPoster,
                    synopsis = if (metadata.description.isNotBlank()) metadata.description else movie.synopsis,
                    cast = if (castMembers.isNotEmpty()) castMembers else movie.cast,
                    releaseYear = if (metadata.releaseYear.isNotBlank()) metadata.releaseYear else movie.releaseYear,
                    rating = metadata.rating ?: movie.rating,
                    genre = if (metadata.genres.isNotEmpty()) metadata.genres else movie.genre
                )
            } else movie
        }
        _moviesList.value = updatedMovies

        val currentHistory = _history.value
        _history.value = currentHistory.map { movie ->
            val isTarget = if (isTvShow && !sTitle.isNullOrBlank()) {
                movie.id == targetMovie.id || com.example.network.GeminiService.cleanShowTitle(movie.title).equals(sTitle, ignoreCase = true)
            } else {
                movie.id == targetMovie.id
            }
            if (isTarget) {
                movie.copy(
                    title = if (!isTvShow) finalTitle else movie.title,
                    posterUrl = finalPoster,
                    synopsis = if (metadata.description.isNotBlank()) metadata.description else movie.synopsis,
                    cast = if (castMembers.isNotEmpty()) castMembers else movie.cast,
                    releaseYear = if (metadata.releaseYear.isNotBlank()) metadata.releaseYear else movie.releaseYear,
                    rating = metadata.rating ?: movie.rating,
                    genre = if (metadata.genres.isNotEmpty()) metadata.genres else movie.genre
                )
            } else movie
        }
        Log.d("MANUAL_POSTER", "Successfully applied poster and matched metadata for '${targetMovie.title}': tmdbId=$tmdbId, title='$finalTitle'")
    }

    fun getEffectivePosterUrl(movie: Movie): String {
        // Default TMDB / Series poster resolution (without mixing card vs detail overrides)
        val isShow = movie.category.equals("Shows", ignoreCase = true) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
        val sTitle = if (isShow) com.example.network.GeminiService.cleanShowTitle(movie.title) else ""
        if (sTitle.isNotBlank()) {
            val cachedSeriesPoster = getSavedTmdbPosterUrl(sTitle, sTitle)
            if (!cachedSeriesPoster.isNullOrBlank() && !cachedSeriesPoster.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedSeriesPoster, "w780")
            }
        }
        val cachedTmdbPoster = getSavedTmdbPosterUrl(movie.id, movie.title)
        if (!cachedTmdbPoster.isNullOrBlank() && !cachedTmdbPoster.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedTmdbPoster, "w780")
        }
        if (movie.posterUrl.isNotBlank() && !movie.posterUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.posterUrl, "w780")
        }
        if (!isShow && movie.backdropUrl.isNotBlank() && !movie.backdropUrl.contains("unsplash")) {
            return com.example.util.PosterCardUIHelper.formatBackdropUrl(movie.backdropUrl, "w1280")
        }
        return ""
    }

    fun getEffectiveBackdropUrl(movie: Movie, initialMovie: Movie? = null): String {
        val isEpisode = movie.category.equals("Shows", ignoreCase = true) ||
                movie.title.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))

        if (isEpisode) {
            // For EPISODE / SERIES: Priority 1 = Series backdrop, Priority 2 = Series poster, Priority 3 = Movie backdrop
            val seriesBackdrop = initialMovie?.backdropUrl?.takeIf { it.isNotBlank() && !it.contains("unsplash") }
            if (!seriesBackdrop.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatBackdropUrl(seriesBackdrop, "w1280")
            }
            val seriesPoster = initialMovie?.posterUrl?.takeIf { it.isNotBlank() && !it.contains("unsplash") }
            if (!seriesPoster.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(seriesPoster, "w780")
            }
            val sTitle = com.example.network.GeminiService.cleanShowTitle(movie.title)
            val cachedSeriesBackdrop = if (sTitle.isNotBlank()) getSavedTmdbBackdropUrl(sTitle, sTitle) else null
            if (!cachedSeriesBackdrop.isNullOrBlank() && !cachedSeriesBackdrop.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedSeriesBackdrop, "w1280")
            }
            val cachedSeriesPoster = if (sTitle.isNotBlank()) getSavedTmdbPosterUrl(sTitle, sTitle) else null
            if (!cachedSeriesPoster.isNullOrBlank() && !cachedSeriesPoster.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedSeriesPoster, "w780")
            }
            if (movie.backdropUrl.isNotBlank() && !movie.backdropUrl.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatBackdropUrl(movie.backdropUrl, "w1280")
            }
        } else {
            // For MOVIE / SERIES: Priority 1 = Backdrop, Priority 2 = Poster
            if (movie.backdropUrl.isNotBlank() && !movie.backdropUrl.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatBackdropUrl(movie.backdropUrl, "w1280")
            }
            val cachedBackdrop = getSavedTmdbBackdropUrl(movie.id, movie.title)
            if (!cachedBackdrop.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedBackdrop, "w1280")
            }
            val cachedPoster = getSavedTmdbPosterUrl(movie.id, movie.title)
            if (!cachedPoster.isNullOrBlank()) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(cachedPoster, "w780")
            }
            if (movie.posterUrl.isNotBlank() && !movie.posterUrl.contains("unsplash")) {
                return com.example.util.PosterCardUIHelper.formatTmdbUrl(movie.posterUrl, "w780")
            }
        }
        return ""
    }

    /**
     * Resolves the landscape backdrop URL for Continue Watching cards.
     * Prefers original TMDB landscape backdrop artwork (backdrop_path).
     * Never uses individual episode backdrop/poster for TV Series.
     * Preserves manually selected card poster/backdrop overrides.
     * Returns Pair(formattedUrl, isLandscapeBackdrop). If no landscape backdrop is available,
     * falls back to the portrait poster URL with isLandscapeBackdrop = false.
     */
    fun getContinueWatchingBackdropInfo(
        movie: Movie,
        candidateSeriesMovie: Movie? = null,
        isTvShow: Boolean = false,
        showTitle: String? = null
    ): Pair<String, Boolean> {
        val sTitle = if (isTvShow) (showTitle?.takeIf { it.isNotBlank() } ?: com.example.network.GeminiService.cleanShowTitle(movie.title)).ifBlank { movie.title } else ""

        // 1. Check manual card poster/backdrop override
        val override = if (isTvShow && sTitle.isNotBlank()) {
            getCardPosterOverride(sTitle, sTitle) ?: getCardPosterOverride(movie.id, movie.title)
        } else {
            getCardPosterOverride(movie.id, movie.title)
        }
        if (!override.isNullOrBlank()) {
            val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(override, "w1280")
            return Pair(formatted, true)
        }

        // 2. Resolve landscape TMDB backdrop path
        if (isTvShow) {
            val candidateBackdrop = candidateSeriesMovie?.backdropUrl?.takeIf { it.isNotBlank() && !it.contains("unsplash") }
            if (!candidateBackdrop.isNullOrBlank()) {
                val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(candidateBackdrop, "w1280")
                return Pair(formatted, true)
            }
            if (sTitle.isNotBlank()) {
                val cachedSeriesBackdrop = getSavedTmdbBackdropUrl(sTitle, sTitle)
                if (!cachedSeriesBackdrop.isNullOrBlank() && !cachedSeriesBackdrop.contains("unsplash")) {
                    val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedSeriesBackdrop, "w1280")
                    return Pair(formatted, true)
                }
            }
            val cachedMovieBackdrop = getSavedTmdbBackdropUrl(movie.id, movie.title)
            if (!cachedMovieBackdrop.isNullOrBlank() && !cachedMovieBackdrop.contains("unsplash")) {
                val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedMovieBackdrop, "w1280")
                return Pair(formatted, true)
            }
            if (movie.backdropUrl.isNotBlank() && !movie.backdropUrl.contains("unsplash")) {
                val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(movie.backdropUrl, "w1280")
                return Pair(formatted, true)
            }

            // Fallback: Return series portrait poster with isLandscapeBackdrop = false
            val fallbackPoster = getDetailScreenMainPosterUrl(movie, candidateSeriesMovie).ifBlank {
                getCardPosterUrlForShow(sTitle)
            }
            val formattedPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(fallbackPoster, "w780")
            return Pair(formattedPoster, false)
        } else {
            val movieBackdrop = movie.backdropUrl.takeIf { it.isNotBlank() && !it.contains("unsplash") }
            if (!movieBackdrop.isNullOrBlank()) {
                val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(movieBackdrop, "w1280")
                return Pair(formatted, true)
            }
            val cachedBackdrop = getSavedTmdbBackdropUrl(movie.id, movie.title)
            if (!cachedBackdrop.isNullOrBlank() && !cachedBackdrop.contains("unsplash")) {
                val formatted = com.example.util.PosterCardUIHelper.formatBackdropUrl(cachedBackdrop, "w1280")
                return Pair(formatted, true)
            }

            // Fallback: Return movie portrait poster with isLandscapeBackdrop = false
            val fallbackPoster = getDetailScreenMainPosterUrl(movie)
            val formattedPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(fallbackPoster, "w780")
            return Pair(formattedPoster, false)
        }
    }

    fun importMovie(movie: Movie) {
        val populated = populateMovieFromCache(movie)
        val savedPoster = getSavedCustomPosterUrl(movie.id)
        val finalMovie = if (!savedPoster.isNullOrBlank()) {
            populated.copy(posterUrl = savedPoster, backdropUrl = savedPoster)
        } else {
            populated
        }
        _moviesList.value = _moviesList.value + finalMovie
        preloadMetadataInBackground(listOf(finalMovie))
    }

    fun updateMovieMetadata(
        movieId: String,
        title: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        synopsis: String = "",
        cast: List<CastMember> = emptyList(),
        releaseYear: String = ""
    ) {
        val currentMovies = _moviesList.value
        val updatedMovies = currentMovies.map { movie ->
            if (movie.id == movieId) {
                movie.copy(
                    title = if (title.isNotBlank()) title else movie.title,
                    posterUrl = if (posterUrl.isNotBlank()) posterUrl else movie.posterUrl,
                    backdropUrl = if (backdropUrl.isNotBlank()) backdropUrl else movie.backdropUrl,
                    synopsis = if (synopsis.isNotBlank()) synopsis else movie.synopsis,
                    cast = if (cast.isNotEmpty()) cast else movie.cast,
                    releaseYear = if (releaseYear.isNotBlank()) releaseYear else movie.releaseYear
                )
            } else {
                movie
            }
        }
        _moviesList.value = updatedMovies

        val currentHistory = _history.value
        _history.value = currentHistory.map { movie ->
            if (movie.id == movieId) {
                movie.copy(
                    title = if (title.isNotBlank()) title else movie.title,
                    posterUrl = if (posterUrl.isNotBlank()) posterUrl else movie.posterUrl,
                    backdropUrl = if (backdropUrl.isNotBlank()) backdropUrl else movie.backdropUrl,
                    synopsis = if (synopsis.isNotBlank()) synopsis else movie.synopsis,
                    cast = if (cast.isNotEmpty()) cast else movie.cast,
                    releaseYear = if (releaseYear.isNotBlank()) releaseYear else movie.releaseYear
                )
            } else {
                movie
            }
        }
    }

    fun updateMovieWithScannedResult(movieId: String, thumbnailUrl: String, scannedCharacters: List<com.example.network.ScannedCharacter>) {
        val currentMovies = _moviesList.value
        val updatedMovies = currentMovies.map { movie ->
            if (movie.id == movieId) {
                val newCast = scannedCharacters.map {
                    CastMember(
                        name = it.name,
                        character = it.character,
                        imageUrl = it.imageUrl
                    )
                }
                movie.copy(
                    posterUrl = thumbnailUrl,
                    backdropUrl = thumbnailUrl,
                    cast = newCast
                )
            } else {
                movie
            }
        }
        _moviesList.value = updatedMovies
        
        // Also update watch history if this movie is in history
        val currentHistory = _history.value
        _history.value = currentHistory.map { movie ->
            if (movie.id == movieId) {
                val newCast = scannedCharacters.map {
                    CastMember(
                        name = it.name,
                        character = it.character,
                        imageUrl = it.imageUrl
                    )
                }
                movie.copy(
                    posterUrl = thumbnailUrl,
                    backdropUrl = thumbnailUrl,
                    cast = newCast
                )
            } else {
                movie
            }
        }
    }

    fun updateMovieWithGeminiResult(movieId: String, thumbnailUrl: String, scannedCharacters: List<com.example.network.ScannedCharacter>) {
        updateMovieWithScannedResult(movieId, thumbnailUrl, scannedCharacters)
    }

    fun updateMovieCategory(movieId: String, newCategory: String) {
        sharedPrefs.edit().putString("movie_category_$movieId", newCategory).apply()
        val currentMovies = _moviesList.value
        _moviesList.value = currentMovies.map { movie ->
            if (movie.id == movieId) {
                movie.copy(category = newCategory)
            } else {
                movie
            }
        }

        // Also update history
        val currentHistory = _history.value
        _history.value = currentHistory.map { movie ->
            if (movie.id == movieId) {
                movie.copy(category = newCategory)
            } else {
                movie
            }
        }
    }

    private val _scannedMovieIds = MutableStateFlow<Set<String>>(emptySet())
    val scannedMovieIds: StateFlow<Set<String>> = _scannedMovieIds.asStateFlow()

    fun isMovieScanned(movieId: String): Boolean {
        return _scannedMovieIds.value.contains(movieId)
    }

    fun markMovieAsScanned(movieId: String) {
        _scannedMovieIds.value = _scannedMovieIds.value + movieId
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Settings States
    private val _isPlexConnected = MutableStateFlow(true)
    val isPlexConnected = _isPlexConnected.asStateFlow()

    private val _isTraktConnected = MutableStateFlow(true)
    val isTraktConnected = _isTraktConnected.asStateFlow()

    private val _librarySyncEnabled = MutableStateFlow(true)
    val librarySyncEnabled = _librarySyncEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _subtitleFontSize = MutableStateFlow(sharedPrefs.getInt("pref_subtitle_font_size", 18))
    val subtitleFontSize = _subtitleFontSize.asStateFlow()

    private val _subtitleColor = MutableStateFlow(sharedPrefs.getString("pref_subtitle_color", "White") ?: "White")
    val subtitleColor = _subtitleColor.asStateFlow()

    private val _subtitleEncoding = MutableStateFlow(sharedPrefs.getString("pref_subtitle_encoding", "UTF-8 (Auto)") ?: "UTF-8 (Auto)")
    val subtitleEncoding = _subtitleEncoding.asStateFlow()

    val gridMode: StateFlow<GridMode> = gridModeRepo.gridModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = GridMode.COMFORTABLE
        )

    val gridDensity: StateFlow<String> = gridModeRepo.gridModeFlow
        .map { mode ->
            if (mode == GridMode.COMPACT) "Compact" else "Comfortable"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "Comfortable"
        )

    // DataStore Persistent Continuous Double-Tap Seek & Seek Duration (5s, 10s, 30s) Preferences
    val continuousSeekEnabled: StateFlow<Boolean> = seekPrefsRepo.continuousSeekEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.example.util.SeekPreferencesRepository.DEFAULT_CONTINUOUS_SEEK_ENABLED
        )

    val seekDurationSeconds: StateFlow<Int> = seekPrefsRepo.seekDurationSecondsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.example.util.SeekPreferencesRepository.DEFAULT_SEEK_DURATION_SECONDS
        )

    val gestureSensitivity: StateFlow<Float> = seekPrefsRepo.gestureSensitivityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.example.util.SeekPreferencesRepository.DEFAULT_GESTURE_SENSITIVITY
        )

    // Settings Actions
    fun togglePlex() { _isPlexConnected.value = !_isPlexConnected.value }
    fun toggleTrakt() { _isTraktConnected.value = !_isTraktConnected.value }
    fun toggleLibrarySync() { _librarySyncEnabled.value = !_librarySyncEnabled.value }
    fun setPlaybackSpeed(speed: Float) { _playbackSpeed.value = speed }
    fun setContinuousSeekEnabled(enabled: Boolean) {
        viewModelScope.launch {
            seekPrefsRepo.setContinuousSeekEnabled(enabled)
        }
    }
    fun toggleContinuousSeekEnabled() {
        setContinuousSeekEnabled(!continuousSeekEnabled.value)
    }
    fun setSeekDurationSeconds(seconds: Int) {
        val validSeconds = com.example.util.SeekPreferencesRepository.normalizeSeekDuration(seconds)
        viewModelScope.launch {
            seekPrefsRepo.setSeekDurationSeconds(validSeconds)
        }
    }
    fun setGestureSensitivity(sensitivity: Float) {
        val validSensitivity = com.example.util.SeekPreferencesRepository.normalizeGestureSensitivity(sensitivity)
        viewModelScope.launch {
            seekPrefsRepo.setGestureSensitivity(validSensitivity)
        }
    }
    fun increaseSubtitleFontSize() {
        val newSize = (_subtitleFontSize.value + 1).coerceAtMost(48)
        _subtitleFontSize.value = newSize
        sharedPrefs.edit().putInt("pref_subtitle_font_size", newSize).apply()
    }
    fun decreaseSubtitleFontSize() {
        val newSize = (_subtitleFontSize.value - 1).coerceAtLeast(8)
        _subtitleFontSize.value = newSize
        sharedPrefs.edit().putInt("pref_subtitle_font_size", newSize).apply()
    }
    fun setSubtitleFontSize(size: Int) {
        val newSize = size.coerceIn(8, 48)
        _subtitleFontSize.value = newSize
        sharedPrefs.edit().putInt("pref_subtitle_font_size", newSize).apply()
    }
    fun setSubtitleColor(color: String) {
        _subtitleColor.value = color
        sharedPrefs.edit().putString("pref_subtitle_color", color).apply()
    }
    fun setSubtitleEncoding(encoding: String) {
        _subtitleEncoding.value = encoding
        sharedPrefs.edit().putString("pref_subtitle_encoding", encoding).apply()
    }
    fun toggleDynamicInterface() {
        val newState = !_dynamicInterface.value
        _dynamicInterface.value = newState
        sharedPrefs.edit().putBoolean("dynamic_interface_enabled", newState).apply()
    }
    fun setDynamicInterface(enabled: Boolean) {
        _dynamicInterface.value = enabled
        sharedPrefs.edit().putBoolean("dynamic_interface_enabled", enabled).apply()
    }
    fun setGridDensity(density: String) {
        val mode = if (density.equals("Compact", ignoreCase = true)) GridMode.COMPACT else GridMode.COMFORTABLE
        setGridMode(mode)
    }
    fun setGridMode(mode: GridMode) {
        viewModelScope.launch {
            gridModeRepo.saveGridMode(mode)
        }
    }
    fun selectTheme(theme: AppTheme) {
        _selectedTheme.value = theme
        sharedPrefs.edit().putString("selected_app_theme", theme.name).apply()

        val appThemeEnum = when (theme) {
            AppTheme.MIDNIGHT_ORANGE -> com.example.util.AppThemeManager.AppTheme.ORANGE
            AppTheme.NEON_CYBER -> com.example.util.AppThemeManager.AppTheme.CYBERPUNK
            AppTheme.NORDIC_FROST -> com.example.util.AppThemeManager.AppTheme.FROST
            AppTheme.EMERALD_FOREST -> com.example.util.AppThemeManager.AppTheme.FOREST
            AppTheme.SOLAR_FLARE -> com.example.util.AppThemeManager.AppTheme.FLARE
        }
        com.example.util.AppThemeManager.setSelectedTheme(getApplication(), appThemeEnum)
    }

    // Online Stream Management
    fun addCustomStream(title: String, url: String, category: String, thumbnailUrl: String = "") {
        val thumb = if (thumbnailUrl.isNotBlank()) thumbnailUrl else "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=800&auto=format&fit=crop"
        val newStream = OnlineStream(
            id = "custom_" + System.currentTimeMillis(),
            title = title.ifBlank { "Custom Stream" },
            url = url,
            category = category.ifBlank { "Live TV" },
            thumbnailUrl = thumb,
            isCustom = true
        )
        val updatedList = _customStreams.value + newStream
        _customStreams.value = updatedList
        runCatching {
            val json = streamsListAdapter.toJson(updatedList)
            sharedPrefs.edit().putString("custom_streams_list", json).apply()
        }
    }

    fun deleteCustomStream(streamId: String) {
        val updatedList = _customStreams.value.filter { it.id != streamId }
        _customStreams.value = updatedList
        runCatching {
            val json = streamsListAdapter.toJson(updatedList)
            sharedPrefs.edit().putString("custom_streams_list", json).apply()
        }
    }

    fun deleteMovie(movieId: String) {
        _moviesList.value = _moviesList.value.filter { it.id != movieId }
        _history.value = _history.value.filter { it.id != movieId }
        if (_watchlist.value.contains(movieId)) {
            toggleWatchlist(movieId)
        }
        clearSavedPlaybackPosition(movieId)
        if (_watchedEpisodes.value.contains(movieId)) {
            _watchedEpisodes.value = _watchedEpisodes.value - movieId
        }
        runCatching {
            val editor = sharedPrefs.edit()
            editor.remove("movie_progress_$movieId")
            editor.remove("movie_watched_$movieId")
            val cachedKeys = sharedPrefs.all.keys.filter { it.contains(movieId) }
            cachedKeys.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun deleteEpisodeFromDatabase(episodeId: String) {
        deleteMovie(episodeId)
    }

    private val _autoImportGallery = MutableStateFlow(false)
    val autoImportGallery = _autoImportGallery.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    fun setIsInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun setAutoImportGallery(enabled: Boolean) {
        _autoImportGallery.value = enabled
    }

    fun syncGalleryVideos(videos: List<Movie>) {
        val existingIds = _moviesList.value.map { it.id }.toSet()
        val populatedVideos = videos.map { video ->
            val savedCategory = sharedPrefs.getString("movie_category_${video.id}", null)
            val withCat = if (!savedCategory.isNullOrBlank()) video.copy(category = savedCategory) else video
            populateMovieFromCache(withCat)
        }
        val newVideos = populatedVideos.filter { it.id !in existingIds }
        if (newVideos.isNotEmpty()) {
            _moviesList.value = _moviesList.value.map { populateMovieFromCache(it) } + newVideos
            restoreHistoryFromAllMovies(_moviesList.value)
            preloadMetadataInBackground(newVideos)
        } else {
            _moviesList.value = _moviesList.value.map { populateMovieFromCache(it) }
            restoreHistoryFromAllMovies(_moviesList.value)
            val unpopulated = _moviesList.value.filter { m ->
                val atomic = metadataCacheRepo.getAtomicMetadata(m.id)
                val cachedTmdbId = atomic?.tmdbId ?: sharedPrefs.getInt("tmdb_id_${m.id}", 0)
                cachedTmdbId <= 0 || m.posterUrl.isBlank()
            }
            if (unpopulated.isNotEmpty()) {
                preloadMetadataInBackground(unpopulated)
            }
        }
    }

    // Video Player State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0.0f) // Progress slider value 0.0 to 1.0
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _volumeLevel = MutableStateFlow(0.40f) // Volume slider value 0.0 to 1.0
    val volumeLevel = _volumeLevel.asStateFlow()

    private val _brightnessLevel = MutableStateFlow(0.65f) // Brightness slider value 0.0 to 1.0
    val brightnessLevel = _brightnessLevel.asStateFlow()

    private val _showLeftSwipeIndicator = MutableStateFlow(false)
    val showLeftSwipeIndicator = _showLeftSwipeIndicator.asStateFlow()

    private val _showRightSwipeIndicator = MutableStateFlow(false)
    val showRightSwipeIndicator = _showRightSwipeIndicator.asStateFlow()

    // Player Actions
    fun setPlaying(playing: Boolean) { _isPlaying.value = playing }
    fun togglePlayPause() { _isPlaying.value = !_isPlaying.value }
    fun rewind10Seconds() {
        val next = _playbackProgress.value - 0.05f
        _playbackProgress.value = if (next < 0f) 0f else next
    }
    fun forward30Seconds() {
        val next = _playbackProgress.value + 0.15f
        _playbackProgress.value = if (next > 1f) 1f else next
    }
    fun setPlaybackProgress(progress: Float) { _playbackProgress.value = progress }
    
    fun setVolume(volume: Float) {
        _volumeLevel.value = volume.coerceIn(0f, 1f)
    }

    fun adjustVolume(delta: Float) {
        val next = _volumeLevel.value + delta
        _volumeLevel.value = next.coerceIn(0f, 1f)
    }

    fun adjustBrightness(delta: Float) {
        val next = _brightnessLevel.value + delta
        _brightnessLevel.value = next.coerceIn(0f, 1f)
    }

    fun setShowLeftSwipeIndicator(show: Boolean) { _showLeftSwipeIndicator.value = show }
    fun setShowRightSwipeIndicator(show: Boolean) { _showRightSwipeIndicator.value = show }

    fun toggleWatched(movieId: String) {
        val current = _watchedEpisodes.value
        val next = if (current.contains(movieId)) {
            sharedPrefs.edit().putBoolean("movie_watched_$movieId", false).apply()
            current - movieId
        } else {
            sharedPrefs.edit().putBoolean("movie_watched_$movieId", true).apply()
            current + movieId
        }
        _watchedEpisodes.value = next
    }

    fun markAsWatched(movieId: String) {
        val current = _watchedEpisodes.value
        if (!current.contains(movieId)) {
            sharedPrefs.edit().putBoolean("movie_watched_$movieId", true).apply()
            _watchedEpisodes.value = current + movieId
        }
    }

    fun clearShowWatchState(showKey: String) {
        if (showKey.isBlank()) return
        val normalizedKey = com.example.util.EpisodeUtils.getNormalizedShowTitle(showKey).ifBlank { showKey }
        sharedPrefs.edit()
            .remove("show_last_season_$normalizedKey")
            .remove("show_last_episode_$normalizedKey")
            .remove("show_last_episode_id_$normalizedKey")
            .remove("show_last_pos_$normalizedKey")
            .apply()
    }

    fun onEpisodeCompleted(movieId: String) {
        markAsWatched(movieId)
        clearSavedPlaybackPosition(movieId)

        // Identify if this belongs to a TV series and find the next episode
        val currentMovie = _moviesList.value.firstOrNull { it.id == movieId }
            ?: _history.value.firstOrNull { it.id == movieId }

        if (currentMovie != null) {
            val showTitle = com.example.network.GeminiService.cleanShowTitle(currentMovie.title)
            val normalizedShow = com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitle).ifBlank {
                com.example.util.EpisodeUtils.getNormalizedShowTitle(currentMovie.title)
            }

            val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(currentMovie.title, currentMovie.filePath ?: "")
            val isShow = currentMovie.category.equals("Shows", ignoreCase = true) ||
                    currentMovie.category.contains("Show", ignoreCase = true) ||
                    seInfo.season != null ||
                    seInfo.episode != null

            if (isShow && normalizedShow.isNotBlank()) {
                val allEpisodes = _moviesList.value.filter { m ->
                    (m.category.equals("Shows", ignoreCase = true) || m.category.contains("Show", ignoreCase = true)) &&
                            com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title) == normalizedShow
                }

                val sortedEpisodes = com.example.util.EpisodeUtils.sortEpisodesChronologically(
                    if (allEpisodes.isNotEmpty()) allEpisodes else listOf(currentMovie)
                )

                val currentIndex = sortedEpisodes.indexOfFirst { it.id == movieId }
                val nextEpisode = if (currentIndex in 0 until sortedEpisodes.size - 1) {
                    sortedEpisodes[currentIndex + 1]
                } else null

                if (nextEpisode != null) {
                    val nextSeInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(nextEpisode.title, nextEpisode.filePath ?: "")
                    val nextSeason = nextSeInfo.season ?: com.example.util.LocalSeasonParser.parseSeasonNumber(nextEpisode.filePath ?: "", nextEpisode.title)
                    val nextEpNum = nextSeInfo.episode ?: (currentIndex + 2)

                    // Advance show watch state for Details screen episode selection (0L progress until played)
                    saveShowWatchState(
                        showKey = showTitle.ifBlank { currentMovie.title },
                        season = nextSeason,
                        episode = nextEpNum,
                        episodeId = nextEpisode.id,
                        positionMs = 0L
                    )
                } else {
                    // Last episode of the season/series completed: remove from continue watching entirely
                    clearShowWatchState(showTitle.ifBlank { currentMovie.title })
                    clearShowWatchState(currentMovie.id)
                }
            } else {
                // For a movie: completion clears progress and leaves history intact
                clearSavedPlaybackPosition(movieId)
            }
        }
    }

    fun savePlaybackPosition(movieId: String, positionMs: Long, durationMs: Long = 0L) {
        com.example.util.SeekDiagnosticsLogger.log(
            "LOCAL_SEEK_ROOT_CAUSE",
            "[SAVED_POS_WRITE_ATTEMPT] movieId=$movieId, positionMs=${positionMs}ms, durationMs=${durationMs}ms"
        )
        if (positionMs <= 0L) {
            // Never clear or overwrite valid saved position during transient zero states (e.g. seek / buffer / setup)
            com.example.util.SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SAVED_POS_WRITE_IGNORED_ZERO] movieId=$movieId position=$positionMs")
            return
        }
        // Completion detection: >= 95% of duration
        val isCompleted = durationMs > 0L && positionMs >= (durationMs * 0.95f)
        if (isCompleted) {
            com.example.util.SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SAVED_POS_WRITE_TRIGGER_COMPLETION] movieId=$movieId pos=$positionMs dur=$durationMs")
            onEpisodeCompleted(movieId)
            return
        }

        // If the user is actively watching an episode/movie in progress, ensure it is not marked as completed/watched
        if (_watchedEpisodes.value.contains(movieId)) {
            _watchedEpisodes.value = _watchedEpisodes.value - movieId
            sharedPrefs.edit().putBoolean("movie_watched_$movieId", false).apply()
        }

        val updatedPosMap = _savedPositions.value.toMutableMap()
        updatedPosMap[movieId] = positionMs
        val prefsEditor = sharedPrefs.edit().putLong("movie_progress_$movieId", positionMs)

        if (durationMs > 0L) {
            _savedDurations.value = _savedDurations.value + (movieId to durationMs)
            prefsEditor.putLong("movie_duration_$movieId", durationMs)
        }

        val playerEp = _playerUiState.value?.episodesQueue?.firstOrNull { it.id == movieId }
            ?: _playerUiState.value?.currentEpisode?.takeIf { it.id == movieId }

        val movie = _moviesList.value.firstOrNull { it.id == movieId }
            ?: _history.value.firstOrNull { it.id == movieId }
            ?: playerEp?.let { ep ->
                val isEp = (_playerUiState.value?.episodesQueue?.size ?: 0) > 1 ||
                        com.example.util.EpisodeUtils.extractSeasonAndEpisode(ep.episodeTitle, ep.id).let { it.season != null || it.episode != null }
                Movie(
                    id = ep.id,
                    title = if (isEp && !ep.episodeTitle.contains(Regex("""(?i)s\d+e\d+"""))) {
                        "${ep.showTitle} S%02dE%02d - ${ep.episodeTitle}".format(ep.seasonNumber, ep.episodeNumber)
                    } else ep.episodeTitle.ifBlank { ep.showTitle },
                    posterUrl = ep.posterUrl,
                    backdropUrl = ep.posterUrl,
                    rating = 0f,
                    category = if (isEp) "Shows" else "Movies",
                    genre = emptyList(),
                    duration = ep.duration,
                    synopsis = ep.synopsis,
                    cast = emptyList(),
                    director = "",
                    directorImageUrl = ""
                )
            }

        if (movie != null) {
            val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movie.title, movie.id)
            val isShow = com.example.util.ContinueWatchingHelper.isTvEpisode(movie)

            if (isShow) {
                val showTitle = (playerEp?.showTitle?.takeIf { it.isNotBlank() }
                    ?: com.example.network.GeminiService.cleanShowTitle(movie.title)).ifBlank { movie.title }
                val season = seInfo.season
                    ?: playerEp?.seasonNumber
                    ?: com.example.util.LocalSeasonParser.parseSeasonNumber(movie.id, movie.title)
                val episode = seInfo.episode
                    ?: playerEp?.episodeNumber
                    ?: 1

                val uniqueEpisodeKey = com.example.util.ContinueWatchingHelper.getEpisodeProgressKey(showTitle, season, episode)
                updatedPosMap[uniqueEpisodeKey] = positionMs
                prefsEditor.putLong(uniqueEpisodeKey, positionMs)

                if (durationMs > 0L) {
                    _savedDurations.value = _savedDurations.value + (uniqueEpisodeKey to durationMs)
                    prefsEditor.putLong("ep_duration_$uniqueEpisodeKey", durationMs)
                }

                if (showTitle.isNotBlank()) {
                    val normalizedKey = com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitle).ifBlank { showTitle }
                    prefsEditor
                        .putInt("show_last_season_$normalizedKey", season)
                        .putInt("show_last_episode_$normalizedKey", episode)
                        .putString("show_last_episode_id_$normalizedKey", movieId)
                        .putLong("show_last_pos_$normalizedKey", positionMs)
                }
            }
            addToHistory(movie)
        }

        _savedPositions.value = updatedPosMap
        prefsEditor.apply()
        com.example.util.SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SAVED_POS_WRITE_SUCCESS] movieId=$movieId positionMs=${positionMs}ms")
    }

    fun saveShowWatchState(showKey: String, season: Int, episode: Int, episodeId: String, positionMs: Long = 0L) {
        if (showKey.isBlank()) return
        val normalizedKey = com.example.util.EpisodeUtils.getNormalizedShowTitle(showKey).ifBlank { showKey }
        val uniqueEpisodeKey = com.example.util.ContinueWatchingHelper.getEpisodeProgressKey(showKey, season, episode)
        val editor = sharedPrefs.edit()
            .putInt("show_last_season_$normalizedKey", season)
            .putInt("show_last_episode_$normalizedKey", episode)
            .putString("show_last_episode_id_$normalizedKey", episodeId)
            .putLong("show_last_pos_$normalizedKey", positionMs)
        if (positionMs > 0L && episodeId.isNotBlank()) {
            _savedPositions.value = _savedPositions.value + (episodeId to positionMs) + (uniqueEpisodeKey to positionMs)
            editor.putLong("movie_progress_$episodeId", positionMs)
            editor.putLong(uniqueEpisodeKey, positionMs)
        }
        editor.apply()
    }

    fun getSavedShowWatchState(showKey: String): ShowWatchState? {
        if (showKey.isBlank()) return null
        val normalizedKey = com.example.util.EpisodeUtils.getNormalizedShowTitle(showKey).ifBlank { showKey }
        val s = sharedPrefs.getInt("show_last_season_$normalizedKey", -1)
        val e = sharedPrefs.getInt("show_last_episode_$normalizedKey", -1)
        val epId = sharedPrefs.getString("show_last_episode_id_$normalizedKey", null)
        val pos = sharedPrefs.getLong("show_last_pos_$normalizedKey", 0L)
        if (s < 0 && e < 0 && epId.isNullOrBlank()) return null
        val savedEpPos = if (!epId.isNullOrBlank()) {
            getSavedPlaybackPosition(epId)
        } else if (s > 0 && e > 0) {
            getSavedEpisodePosition(showKey, s, e)
        } else 0L
        val finalPos = if (savedEpPos > 0L) savedEpPos else pos.takeIf { it > 0L && (epId == null || !_watchedEpisodes.value.contains(epId)) } ?: 0L
        if (finalPos <= 0L) return null
        return ShowWatchState(
            season = if (s > 0) s else 1,
            episode = if (e > 0) e else 1,
            episodeId = epId,
            positionMs = finalPos
        )
    }

    fun shouldShowResume(movieId: String, durationMs: Long = 0L): Boolean {
        val pos = getSavedPlaybackPosition(movieId)
        return pos > 0L && (durationMs <= 0L || pos < (durationMs * 0.95f).toLong())
    }

    fun getSavedEpisodePosition(showTitle: String, seasonNumber: Int, episodeNumber: Int, episodeId: String? = null): Long {
        if (!episodeId.isNullOrBlank()) {
            val byId = _savedPositions.value[episodeId] ?: sharedPrefs.getLong("movie_progress_$episodeId", 0L)
            if (byId > 0L) return byId
        }
        val uniqueEpisodeKey = com.example.util.ContinueWatchingHelper.getEpisodeProgressKey(showTitle, seasonNumber, episodeNumber)
        return _savedPositions.value[uniqueEpisodeKey] ?: sharedPrefs.getLong(uniqueEpisodeKey, 0L)
    }

    fun getSavedPlaybackDuration(
        movieId: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Long {
        val byId = _savedDurations.value[movieId] ?: sharedPrefs.getLong("movie_duration_$movieId", 0L)
        if (byId > 0L) return byId
        if (!showTitle.isNullOrBlank() && seasonNumber != null && episodeNumber != null) {
            val uniqueEpisodeKey = com.example.util.ContinueWatchingHelper.getEpisodeProgressKey(showTitle, seasonNumber, episodeNumber)
            val byEp = _savedDurations.value[uniqueEpisodeKey] ?: sharedPrefs.getLong("ep_duration_$uniqueEpisodeKey", 0L)
            if (byEp > 0L) return byEp
        }
        return 0L
    }

    fun getSavedPlaybackPosition(movieId: String): Long {
        val directPos = _savedPositions.value[movieId] ?: sharedPrefs.getLong("movie_progress_$movieId", 0L)
        if (directPos > 0L) {
            com.example.util.SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SAVED_POS_RESTORE_READ] movieId=$movieId, retrievedPos=${directPos}ms")
            return directPos
        }
        val movie = _moviesList.value.firstOrNull { it.id == movieId }
            ?: _history.value.firstOrNull { it.id == movieId }
        if (movie != null && com.example.util.ContinueWatchingHelper.isTvEpisode(movie)) {
            val uniqueKey = com.example.util.ContinueWatchingHelper.getEpisodeProgressKey(movie)
            val epPos = _savedPositions.value[uniqueKey] ?: sharedPrefs.getLong(uniqueKey, 0L)
            if (epPos > 0L) {
                com.example.util.SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SAVED_POS_RESTORE_READ_EP_KEY] movieId=$movieId, key=$uniqueKey, retrievedPos=${epPos}ms")
                return epPos
            }
        }
        return 0L
    }

    fun clearSavedPlaybackPosition(movieId: String) {
        com.example.util.SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SAVED_POS_CLEAR] movieId=$movieId")
        val movie = _moviesList.value.firstOrNull { it.id == movieId }
            ?: _history.value.firstOrNull { it.id == movieId }
        val uniqueKey = if (movie != null && com.example.util.ContinueWatchingHelper.isTvEpisode(movie)) {
            com.example.util.ContinueWatchingHelper.getEpisodeProgressKey(movie)
        } else null

        val updated = _savedPositions.value.toMutableMap()
        updated.remove(movieId)
        if (uniqueKey != null) {
            updated.remove(uniqueKey)
        }
        _savedPositions.value = updated

        val editor = sharedPrefs.edit().remove("movie_progress_$movieId")
        if (uniqueKey != null) {
            editor.remove(uniqueKey)
        }
        editor.apply()
    }

    fun formatPlaybackTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Unified Player UI State for Episode and Playback Management
    private val _playerUiState = MutableStateFlow<PlayerUiState?>(null)
    val playerUiState: StateFlow<PlayerUiState?> = _playerUiState.asStateFlow()

    fun selectEpisode(episodeId: String) {
        val existingState = _playerUiState.value
        if (existingState != null && existingState.currentEpisode.id == episodeId && existingState.episodesQueue.isNotEmpty()) {
            return
        }
        val movies = _moviesList.value
        val movie = movies.firstOrNull { it.id == episodeId }
            ?: _history.value.firstOrNull { it.id == episodeId }
            ?: run {
                val titleClean = if (episodeId.startsWith("content://") || episodeId.startsWith("file://")) {
                    episodeId.substringAfterLast("/").substringBeforeLast(".")
                } else if (episodeId.startsWith("asset:///")) {
                    episodeId.substringAfter("asset:///").substringBeforeLast(".")
                } else {
                    "Imported Video"
                }
                val cachedPoster = getSavedTmdbPosterUrl(episodeId, titleClean)
                val cachedCast = getSavedTmdbCastList(episodeId, titleClean)
                val cachedOverview = getSavedTmdbOverview(episodeId, titleClean)

                val sanitized = com.example.network.GeminiService.sanitizeFileName(titleClean)
                val poster = if (!cachedPoster.isNullOrBlank()) cachedPoster else "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop"
                val castList = cachedCast
                val synopsis = if (!cachedOverview.isNullOrBlank()) cachedOverview else ""

                val cachedRating = getSavedTmdbMovieRating(episodeId, titleClean) ?: 0.0f
                val cachedTomato = getSavedTmdbTomatoScore(episodeId, titleClean) ?: ""
                val isEp = com.example.util.EpisodeUtils.extractSeasonAndEpisode(titleClean, episodeId).let { it.season != null || it.episode != null }

                Movie(
                    id = episodeId,
                    title = sanitized.title.ifBlank { titleClean },
                    posterUrl = poster,
                    backdropUrl = poster,
                    rating = cachedRating,
                    category = if (isEp) "Shows" else "Movies",
                    genre = emptyList(),
                    duration = "2h 42m",
                    synopsis = synopsis,
                    cast = castList,
                    director = "Director",
                    directorImageUrl = "",
                    releaseYear = sanitized.year ?: "",
                    views = "Local",
                    tomatoScore = cachedTomato
                )
            }

        val showTitle = com.example.network.GeminiService.cleanShowTitle(movie.title)
        val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movie.title, movie.id)
        val season = seInfo.season ?: com.example.util.LocalSeasonParser.parseSeasonNumber(movie.id, movie.title)
        val episodeNum = seInfo.episode ?: 1
        val cachedEpName = getSavedTmdbEpisodeName(movie.id, showTitle, season, episodeNum)
        val cachedEpStill = getSavedTmdbEpisodeStill(movie.id, showTitle, season, episodeNum)
        val cleanEpTitle = if (!cachedEpName.isNullOrBlank()) {
            com.example.util.EpisodeMetadataFixer.getCleanEpisodeTitle(cachedEpName, episodeNum, showTitle, isFromTmdbApi = true)
        } else {
            com.example.util.EpisodeUtils.cleanEpisodeTitle(movie.title).let {
                if (it.isBlank()) com.example.network.GeminiService.sanitizeFileName(movie.title).title else it
            }
        }

        val activeEp = PlayerEpisode(
            id = movie.id,
            showTitle = showTitle,
            seasonNumber = season,
            episodeNumber = episodeNum,
            episodeTitle = cleanEpTitle,
            mediaUrl = movie.id,
            posterUrl = if (!cachedEpStill.isNullOrBlank()) cachedEpStill else movie.posterUrl,
            synopsis = movie.synopsis,
            duration = movie.duration
        )

        val targetNormalized = com.example.util.EpisodeUtils.getNormalizedShowTitle(movie.title)
        val showEpisodes = movies.filter { m ->
            com.example.util.ContinueWatchingHelper.isTvEpisode(m) &&
                    com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title) == targetNormalized
        }
        val episodesList = if (showEpisodes.isNotEmpty()) showEpisodes else listOf(movie)
        val sortedList = com.example.util.EpisodeUtils.sortEpisodesChronologically(episodesList)

        val queue = sortedList.mapIndexed { index, m ->
            if (m.id == movie.id) {
                activeEp
            } else {
                val mSeInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(m.title, m.id)
                val mSeason = mSeInfo.season ?: com.example.util.LocalSeasonParser.parseSeasonNumber(m.id, m.title)
                val mEpisodeNum = mSeInfo.episode ?: (index + 1)
                val mShowTitle = com.example.network.GeminiService.cleanShowTitle(m.title)
                val mCachedEpName = getSavedTmdbEpisodeName(m.id, mShowTitle, mSeason, mEpisodeNum)
                val mCachedEpStill = getSavedTmdbEpisodeStill(m.id, mShowTitle, mSeason, mEpisodeNum)
                val mCleanEpTitle = if (!mCachedEpName.isNullOrBlank()) {
                    com.example.util.EpisodeMetadataFixer.getCleanEpisodeTitle(mCachedEpName, mEpisodeNum, mShowTitle, isFromTmdbApi = true)
                } else {
                    com.example.util.EpisodeUtils.cleanEpisodeTitle(m.title).let {
                        if (it.isBlank()) com.example.network.GeminiService.sanitizeFileName(m.title).title else it
                    }
                }
                PlayerEpisode(
                    id = m.id,
                    showTitle = mShowTitle,
                    seasonNumber = mSeason,
                    episodeNumber = mEpisodeNum,
                    episodeTitle = mCleanEpTitle,
                    mediaUrl = m.id,
                    posterUrl = if (!mCachedEpStill.isNullOrBlank()) mCachedEpStill else m.posterUrl,
                    synopsis = m.synopsis,
                    duration = m.duration
                )
            }
        }

        _playerUiState.value = PlayerUiState(
            currentEpisode = activeEp,
            episodesQueue = queue
        )
    }

    fun playContinueWatchingMedia(movie: Movie) {
        val isShow = com.example.util.ContinueWatchingHelper.isTvEpisode(movie)

        if (isShow) {
            val directPos = getSavedPlaybackPosition(movie.id)
            if (directPos > 0L && !_watchedEpisodes.value.contains(movie.id)) {
                selectEpisode(movie.id)
                setPlaying(true)
                navigateTo(AppScreen.Player(movie.id, startPositionMs = directPos))
                return
            }

            val showTitle = com.example.network.GeminiService.cleanShowTitle(movie.title)
            val normalizedShow = com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitle).ifBlank {
                com.example.util.EpisodeUtils.getNormalizedShowTitle(movie.title)
            }

            val allEpisodes = _moviesList.value.filter { m ->
                com.example.util.ContinueWatchingHelper.isTvEpisode(m) &&
                        com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title) == normalizedShow
            }

            val savedState = getSavedShowWatchState(showTitle) ?: getSavedShowWatchState(movie.id)

            val targetEpisode: Movie? = if (savedState?.episodeId != null) {
                allEpisodes.firstOrNull { it.id == savedState.episodeId }
                    ?: _moviesList.value.firstOrNull { it.id == savedState.episodeId }
            } else if (savedState != null) {
                allEpisodes.firstOrNull { ep ->
                    val epSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(ep.title, ep.id)
                    val s = epSe.season ?: com.example.util.LocalSeasonParser.parseSeasonNumber(ep.id, ep.title)
                    val e = epSe.episode ?: 1
                    s == savedState.season && e == savedState.episode
                }
            } else null

            val chosenEpisode = targetEpisode ?: run {
                if (allEpisodes.isNotEmpty()) {
                    val inProgress = allEpisodes.firstOrNull { ep ->
                        getSavedPlaybackPosition(ep.id) > 0L
                    }
                    inProgress ?: allEpisodes.firstOrNull() ?: movie
                } else {
                    movie
                }
            }

            val savedPos = getSavedPlaybackPosition(chosenEpisode.id).takeIf { it > 0L }
                ?: (savedState?.positionMs ?: 0L)

            selectEpisode(chosenEpisode.id)
            setPlaying(true)
            navigateTo(AppScreen.Player(chosenEpisode.id, startPositionMs = savedPos))
        } else {
            val savedPos = getSavedPlaybackPosition(movie.id)
            setPlaying(true)
            navigateTo(AppScreen.Player(movie.id, startPositionMs = savedPos))
        }
    }

    suspend fun fetchMovieTrailer(movie: Movie): TrailerLookupResult = withContext(Dispatchers.IO) {
        try {
            val atomic = metadataCacheRepo.getAtomicMetadata(movie.id)
            var tmdbId = when {
                atomic != null && atomic.tmdbId > 0 && atomic.mediaType != MediaType.TV_SHOW && atomic.mediaType != MediaType.TV_EPISODE -> atomic.tmdbId
                else -> sharedPrefs.getInt("tmdb_id_${movie.id}", 0)
            }

            if (tmdbId <= 0) {
                val cleanedInfo = com.example.network.GeminiService.sanitizeFileName(movie.title)
                val queryTitle = cleanedInfo.title.ifBlank { movie.title }
                val queryYear = cleanedInfo.year ?: movie.releaseYear.split("-").firstOrNull()?.trim()?.takeIf { it.length == 4 }
                val tmdbResult = com.example.network.GeminiService.fetchTmdbMovieAndCredits(queryTitle, queryYear)
                val resolvedId = tmdbResult?.movieId ?: 0
                if (resolvedId > 0) {
                    tmdbId = resolvedId
                    sharedPrefs.edit().putInt("tmdb_id_${movie.id}", resolvedId).apply()
                }
            }

            if (tmdbId <= 0) {
                return@withContext TrailerLookupResult.NotAvailable
            }

            trailerMemoryCache[tmdbId]?.let { cached ->
                if (cached.isNotEmpty()) {
                    return@withContext TrailerLookupResult.Available(cached, tmdbId)
                }
            }

            val now = System.currentTimeMillis()
            val emptyTs = trailerEmptyCache[tmdbId]
            if (emptyTs != null && (now - emptyTs) < 24L * 60L * 60L * 1000L) {
                return@withContext TrailerLookupResult.NotAvailable
            }

            val cacheKey = "tmdb_trailer_v1_$tmdbId"
            val cachedJson = sharedPrefs.getString(cacheKey, null)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val obj = org.json.JSONObject(cachedJson)
                    val cachedTmdbId = obj.optInt("tmdbId", 0)
                    val ts = obj.optLong("ts", 0L)
                    if (cachedTmdbId == tmdbId) {
                        val arr = obj.optJSONArray("candidates")
                        if (arr != null && arr.length() > 0 && (now - ts) < 7L * 24L * 60L * 60L * 1000L) {
                            val list = buildList {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optJSONObject(i) ?: continue
                                    val key = item.optString("key", "")
                                    if (key.isNotBlank()) {
                                        add(
                                            MovieTrailerCandidate(
                                                key = key,
                                                name = item.optString("name", "Official Trailer"),
                                                type = item.optString("type", "Trailer"),
                                                tmdbId = tmdbId
                                            )
                                        )
                                    }
                                }
                            }
                            if (list.isNotEmpty()) {
                                trailerMemoryCache[tmdbId] = list
                                return@withContext TrailerLookupResult.Available(list, tmdbId)
                            }
                        } else if (arr != null && arr.length() == 0 && (now - ts) < 24L * 60L * 60L * 1000L) {
                            trailerEmptyCache[tmdbId] = ts
                            return@withContext TrailerLookupResult.NotAvailable
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TRAILER_DEBUG", "Failed reading trailer cache for tmdbId=$tmdbId: ${e.message}")
                }
            }

            val apiKey = com.example.network.GeminiService.getTmdbApiKey()
            val response = com.example.network.TmdbApiClient.apiService.getMovieVideos(
                movieId = tmdbId,
                apiKey = apiKey
            )
            if (response.id != null && response.id != tmdbId) {
                Log.w("TRAILER_DEBUG", "Mismatched TMDB video response id=${response.id} for expected tmdbId=$tmdbId")
                return@withContext TrailerLookupResult.NotAvailable
            }

            val selected = com.example.network.TmdbApiClient.selectOfficialTrailerCandidates(response)
            val candidates = selected.mapNotNull { item ->
                val videoKey = item.key?.trim().orEmpty()
                if (videoKey.isBlank()) null else {
                    MovieTrailerCandidate(
                        key = videoKey,
                        name = item.name?.takeIf { it.isNotBlank() } ?: "Official ${item.type ?: "Trailer"}",
                        type = item.type ?: "Trailer",
                        tmdbId = tmdbId
                    )
                }
            }

            try {
                val json = org.json.JSONObject().apply {
                    put("tmdbId", tmdbId)
                    put("ts", now)
                    put("candidates", org.json.JSONArray().apply {
                        candidates.forEach { c ->
                            put(org.json.JSONObject().apply {
                                put("key", c.key)
                                put("name", c.name)
                                put("type", c.type)
                            })
                        }
                    })
                }
                sharedPrefs.edit().putString(cacheKey, json.toString()).apply()
            } catch (e: Exception) {
                Log.w("TRAILER_DEBUG", "Failed caching trailer for tmdbId=$tmdbId: ${e.message}")
            }

            if (candidates.isNotEmpty()) {
                trailerMemoryCache[tmdbId] = candidates
                trailerEmptyCache.remove(tmdbId)
                TrailerLookupResult.Available(candidates, tmdbId)
            } else {
                trailerEmptyCache[tmdbId] = now
                TrailerLookupResult.NotAvailable
            }
        } catch (e: retrofit2.HttpException) {
            Log.e("TRAILER_DEBUG", "HTTP error fetching trailer for '${movie.title}': ${e.code()} ${e.message()}", e)
            if (e.code() == 404) {
                TrailerLookupResult.NotAvailable
            } else {
                TrailerLookupResult.Error("Unable to load trailer")
            }
        } catch (e: Exception) {
            Log.e("TRAILER_DEBUG", "Error fetching trailer for '${movie.title}': ${e.message}", e)
            TrailerLookupResult.Error("Unable to load trailer")
        }
    }
}

data class MovieTrailerCandidate(
    val key: String,
    val name: String,
    val type: String,
    val tmdbId: Int
)

sealed class TrailerLookupResult {
    data class Available(val candidates: List<MovieTrailerCandidate>, val tmdbId: Int) : TrailerLookupResult()
    data object NotAvailable : TrailerLookupResult()
    data class Error(val message: String) : TrailerLookupResult()
}

data class PlayerEpisode(
    val id: String,
    val showTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val mediaUrl: String,
    val posterUrl: String = "",
    val synopsis: String = "",
    val duration: String = ""
)

data class PlayerUiState(
    val currentEpisode: PlayerEpisode,
    val episodesQueue: List<PlayerEpisode> = emptyList()
)

