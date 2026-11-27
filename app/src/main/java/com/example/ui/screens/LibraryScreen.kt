package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path
import coil.compose.AsyncImage
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.model.Movie
import com.example.model.CastMember
import com.example.model.GridMode
import com.example.util.filePath
import com.example.util.fileName
import com.example.util.infuseClickable
import com.example.util.infusePressEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.example.ui.components.FrostedGlassContainer
import com.example.ui.components.LiquidGlassDockEffects
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.AppTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ContinueWatchingCardModel(
    val movie: Movie,
    val isShow: Boolean,
    val displayTitle: String,
    val subtitle: String,
    val posterUrl: String,
    val backdropUrl: String = "",
    val isLandscapeBackdrop: Boolean = true,
    val savedPosMs: Long,
    val totalDurMs: Long,
    val progressRatio: Float
)

// Reusable preset open-source videos are removed as requested.
fun getPresetMovies(): List<Movie> {
    return emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentTheme by viewModel.selectedTheme.collectAsState()
    val primaryColor = currentTheme.primaryColor
    val primaryContainer = currentTheme.primaryContainer

    val movies by viewModel.moviesList.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val density by viewModel.gridDensity.collectAsState()
    val gridMode by viewModel.gridMode.collectAsState()

    val currentTab by viewModel.currentTab.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val history by viewModel.history.collectAsState()
    val connectedServers by viewModel.connectedServers.collectAsState()
    val dynamicInterface by viewModel.dynamicInterface.collectAsState()

    val activeArtworkUrl = remember(currentTab, movies, selectedCategory) {
        when (currentTab) {
            "Home" -> {
                val candidate = movies.firstOrNull { !it.category.equals("Home Videos", ignoreCase = true) }
                candidate?.backdropUrl?.ifBlank { candidate.posterUrl } ?: movies.firstOrNull()?.posterUrl
            }
            "Library" -> {
                val candidate = movies.firstOrNull { it.category.equals(selectedCategory, ignoreCase = true) } ?: movies.firstOrNull()
                candidate?.backdropUrl?.ifBlank { candidate.posterUrl }
            }
            else -> {
                movies.firstOrNull()?.let { it.backdropUrl.ifBlank { it.posterUrl } }
            }
        }
    }

    val dynamicPalette = com.example.util.rememberDynamicAmbientPalette(
        artworkUrl = activeArtworkUrl,
        isDynamicEnabled = dynamicInterface,
        fallbackColor = primaryColor
    )

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val sharedPrefs = remember(context) { context.getSharedPreferences("infuse_prefs", android.content.Context.MODE_PRIVATE) }
    var showGalleryPromptDialog by remember { mutableStateOf(false) }
    val autoImportGallery by viewModel.autoImportGallery.collectAsState()
    var isImporting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val autoScanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val isGranted = results.values.any { it }
        if (isGranted) {
            val scanned = scanGalleryVideos(context)
            if (scanned.isNotEmpty()) {
                viewModel.syncGalleryVideos(scanned)
            }
        }
    }

    // Sync saved auto-import pref and check prompt on launch
    LaunchedEffect(Unit) {
        val savedVal = sharedPrefs.getBoolean("auto_import_gallery", false)
        viewModel.setAutoImportGallery(savedVal)
        
        val prompted = sharedPrefs.getBoolean("has_prompted_gallery", false)
        if (!prompted) {
            showGalleryPromptDialog = true
        }
    }

    // Auto-scan gallery videos when the preference is true and we have the permission
    LaunchedEffect(autoImportGallery) {
        if (autoImportGallery) {
            val hasPermission = permissionsToRequest.any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermission) {
                val scanned = scanGalleryVideos(context)
                if (scanned.isNotEmpty()) {
                    viewModel.syncGalleryVideos(scanned)
                }
            } else {
                autoScanPermissionLauncher.launch(permissionsToRequest)
            }
        }
    }

    // Permission and Gallery Launcher with TMDB Media Scraper
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { videoUri ->
            isImporting = true
            coroutineScope.launch {
                var displayName = "Gallery Video"
                try {
                    contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Call TMDB to scrape correct metadata from filename
                val metadata = com.example.network.GeminiService.scrapeMovieMetadata(displayName)
                val parsedImportSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(displayName, videoUri.toString())
                val isImportedTvEp = parsedImportSe.season != null && parsedImportSe.episode != null
                val baseTitle = metadata?.title ?: displayName.substringBeforeLast(".")
                val finalTitle = if (isImportedTvEp && !baseTitle.contains(Regex("""(?i)s\d+e\d+|\d+x\d+"""))) {
                    val sTag = "S%02dE%02d".format(parsedImportSe.season ?: 1, parsedImportSe.episode ?: 1)
                    "$baseTitle $sTag"
                } else {
                    baseTitle
                }
                val finalSynopsis = metadata?.description ?: "Local gallery video file:\n$videoUri"
                val finalCast = metadata?.cast?.map { com.example.model.CastMember(it.name, it.character, it.imageUrl) } ?: emptyList()

                var durationStr = "Video"
                var importedDurMs = 0L
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    val ms = time?.toLongOrNull() ?: 0L
                    importedDurMs = ms
                    if (ms > 0L) {
                        val totalSecs = ms / 1000
                        val hours = totalSecs / 3600
                        val mins = (totalSecs % 3600) / 60
                        val secs = totalSecs % 60
                        durationStr = when {
                            hours > 0 -> if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                            mins > 0 -> "${mins}m"
                            else -> "${secs}s"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val cachedImportRating = sharedPrefs.getFloat("tmdb_rating_$videoUri", 0.0f)
                    .takeIf { it > 0f }
                    ?: sharedPrefs.getFloat("tmdb_rating_$finalTitle", 0.0f).takeIf { it > 0f }
                    ?: metadata?.rating?.takeIf { it > 0f }
                    ?: 0.0f
                val cachedImportTomato = sharedPrefs.getString("tmdb_tomato_$videoUri", null)
                    ?: sharedPrefs.getString("tmdb_tomato_$finalTitle", null)
                    ?: metadata?.tomatoScore
                    ?: ""

                val resolvedImportCategory = when {
                    isImportedTvEp -> "Shows"
                    com.example.util.HomeVideoFilter.isHomeVideo(videoUri.toString(), displayName) ||
                        com.example.network.GeminiService.isHomeVideoFilename(displayName) -> "Home Videos"
                    selectedCategory.equals("Home", ignoreCase = true) -> determineCategory(displayName, importedDurMs, videoUri.toString())
                    else -> selectedCategory
                }

                // Add the new local movie object to the state
                val importedMovie = Movie(
                    id = videoUri.toString(),
                    title = finalTitle,
                    posterUrl = metadata?.posterUrl ?: "", // Use TMDB scraped posterUrl or empty fallback
                    backdropUrl = metadata?.posterUrl ?: "", // Use TMDB scraped posterUrl for backdrop or empty fallback
                    rating = cachedImportRating,
                    category = resolvedImportCategory,
                    genre = metadata?.genres ?: emptyList(),
                    duration = durationStr,
                    synopsis = finalSynopsis,
                    cast = finalCast,
                    director = "Self",
                    directorImageUrl = "",
                    releaseYear = metadata?.releaseYear ?: (com.example.network.GeminiService.sanitizeFileName(displayName).year ?: ""),
                    views = "Local",
                    tomatoScore = cachedImportTomato
                )
                viewModel.importMovie(importedMovie)
                isImporting = false
                Toast.makeText(context, "Local video successfully imported & cataloged with TMDB!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        galleryLauncher.launch("video/*")
    }

    val onImportClick = {
        val hasPermission = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            galleryLauncher.launch("video/*")
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // True pitch black background
    ) {
        if (isImporting) {
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color(0xFF1E2122), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "Fetching media metadata from TMDB...",
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (showGalleryPromptDialog) {
            AlertDialog(
                onDismissRequest = {
                    showGalleryPromptDialog = false
                    sharedPrefs.edit().putBoolean("has_prompted_gallery", true).apply()
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Auto-Scan Video Gallery?",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                text = {
                    Text(
                        text = "Would you like to automatically sync your device's video gallery with Infuse?\n\nThis automatically imports all of your local videos and keeps your home library up-to-date so you never have to manually import them one-by-one again.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                },
                containerColor = Color(0xFF1E2122),
                confirmButton = {
                    Button(
                        onClick = {
                            showGalleryPromptDialog = false
                            sharedPrefs.edit()
                                .putBoolean("has_prompted_gallery", true)
                                .putBoolean("auto_import_gallery", true)
                                .apply()
                            viewModel.setAutoImportGallery(true)
                            
                            // Check permission and trigger scan immediately
                            val hasPermission = permissionsToRequest.any {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            if (!hasPermission) {
                                autoScanPermissionLauncher.launch(permissionsToRequest)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("gallery_prompt_allow_btn")
                    ) {
                        Text("Allow Auto-Scan", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showGalleryPromptDialog = false
                            sharedPrefs.edit()
                                .putBoolean("has_prompted_gallery", true)
                                .putBoolean("auto_import_gallery", false)
                                .apply()
                            viewModel.setAutoImportGallery(false)
                        },
                        modifier = Modifier.testTag("gallery_prompt_dismiss_btn")
                    ) {
                        Text("Maybe Later", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            )
        }
        val backdropGraphicsLayer = rememberGraphicsLayer()
        var backdropPositionInRoot by remember { mutableStateOf(Offset.Zero) }
        val backdropDrawTick = remember { mutableLongStateOf(0L) }

        // Hoisted scroll states so Home, Library, Search & Streams scroll positions invalidate live backdrop cleanly
        val homeScrollState = rememberScrollState()
        val continueWatchingListState = rememberLazyListState()
        val favoritesListState = rememberLazyListState()
        val picksListState = rememberLazyListState()
        val libraryGridState = rememberLazyGridState()
        val searchEmptyScrollState = rememberScrollState()
        val searchGridState = rememberLazyGridState()
        val streamsGridState = rememberLazyGridState()

        val backdropScrollToken = {
            homeScrollState.value.toLong() +
                continueWatchingListState.firstVisibleItemIndex.toLong() * 10_000L +
                continueWatchingListState.firstVisibleItemScrollOffset.toLong() +
                favoritesListState.firstVisibleItemIndex.toLong() * 10_000L +
                favoritesListState.firstVisibleItemScrollOffset.toLong() +
                picksListState.firstVisibleItemIndex.toLong() * 10_000L +
                picksListState.firstVisibleItemScrollOffset.toLong() +
                libraryGridState.firstVisibleItemIndex.toLong() * 10_000L +
                libraryGridState.firstVisibleItemScrollOffset.toLong() +
                searchEmptyScrollState.value.toLong() +
                searchGridState.firstVisibleItemIndex.toLong() * 10_000L +
                searchGridState.firstVisibleItemScrollOffset.toLong() +
                streamsGridState.firstVisibleItemIndex.toLong() * 10_000L +
                streamsGridState.firstVisibleItemScrollOffset.toLong()
        }

        // Live Backdrop Layer: records the background and active scrolling tab content into a
        // hardware GraphicsLayer on every draw pass so the floating bottom dock blurs & refracts
        // the actual live content directly behind it in real time.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    backdropPositionInRoot = coords.positionInRoot()
                }
                .drawWithContent {
                    backdropScrollToken()
                    backdropGraphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(backdropGraphicsLayer)
                    val nextTick = Snapshot.withoutReadObservation {
                        backdropDrawTick.longValue + 1L
                    }
                    backdropDrawTick.longValue = nextTick
                }
                .background(Color.Black)
        ) {
            // Dynamic Ambient Background Layer (subtle cinematic backdrop glow when enabled, pure static black when disabled)
            com.example.util.DynamicAmbientBackgroundLayer(palette = dynamicPalette)

            // Render main content based on selected bottom tab
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (currentTab) {
                    "Home" -> {
                        HomeTabContent(
                            viewModel = viewModel,
                            movies = movies,
                            watchlist = watchlist,
                            history = history,
                            primaryColor = primaryColor,
                            currentTheme = currentTheme,
                            onImportClick = onImportClick,
                            scrollState = homeScrollState,
                            continueWatchingListState = continueWatchingListState,
                            favoritesListState = favoritesListState,
                            picksListState = picksListState
                        )
                    }
                    "Streams", "Files" -> {
                        StreamsTabContent(
                            viewModel = viewModel,
                            primaryColor = primaryColor,
                            currentTheme = currentTheme,
                            gridState = streamsGridState
                        )
                    }
                    "Search" -> {
                        SearchTabContent(
                            viewModel = viewModel,
                            movies = movies,
                            primaryColor = primaryColor,
                            emptyScrollState = searchEmptyScrollState,
                            gridState = searchGridState
                        )
                    }
                    "Library" -> {
                        LibraryTabContent(
                            viewModel = viewModel,
                            movies = movies,
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery,
                            density = density,
                            primaryColor = primaryColor,
                            currentTheme = currentTheme,
                            onImportClick = onImportClick,
                            gridMode = gridMode,
                            gridState = libraryGridState
                        )
                    }
                    "Settings" -> {
                        SettingsScreen(viewModel = viewModel)
                    }
                    else -> {
                        HomeTabContent(
                            viewModel = viewModel,
                            movies = movies,
                            watchlist = watchlist,
                            history = history,
                            primaryColor = primaryColor,
                            currentTheme = currentTheme,
                            onImportClick = onImportClick,
                            scrollState = homeScrollState,
                            continueWatchingListState = continueWatchingListState,
                            favoritesListState = favoritesListState,
                            picksListState = picksListState
                        )
                    }
                }
            }
        }

        // Dock is ready immediately to fade in with the main app screen
        var isDockEntered by rememberSaveable { mutableStateOf(true) }

        // Floating Bottom Tab Bar with Real-Time Liquid Glass Backdrop Blur, Refraction & Interactive Lens
        FloatingBottomBar(
            currentTab = currentTab,
            onTabSelected = { tab ->
                viewModel.selectTab(tab)
            },
            primaryColor = primaryColor,
            isVisible = isDockEntered,
            backdropGraphicsLayer = backdropGraphicsLayer,
            backdropPositionInRoot = { backdropPositionInRoot },
            backdropDrawTick = { backdropDrawTick.longValue },
            backdropScrollToken = backdropScrollToken,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )
    }
}

// Infuse Favorites Stylized Vector Icon
@Composable
fun InfuseFavoritesIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accentColor: Color = Color(0xFFFF9500)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val screenW = w * 0.44f
        val screenH = h * 0.54f
        val screenLeft = (w - screenW) / 2f
        val screenTop = (h - screenH) / 2f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(screenH * 0.22f)

        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(screenLeft, screenTop),
            size = androidx.compose.ui.geometry.Size(screenW, screenH),
            cornerRadius = cornerRadius,
            style = Fill
        )

        val playW = screenW * 0.36f
        val playH = screenH * 0.44f
        val playLeft = screenLeft + (screenW - playW) / 2f + playW * 0.15f
        val playTop = screenTop + (screenH - playH) / 2f

        val playPath = Path().apply {
            moveTo(playLeft, playTop)
            lineTo(playLeft + playW, playTop + playH / 2f)
            lineTo(playLeft, playTop + playH)
            close()
        }
        drawPath(path = playPath, color = accentColor)

        val strokeWidth = w * 0.07f
        val strokeCap = androidx.compose.ui.graphics.StrokeCap.Round

        val leftInner = Path().apply {
            moveTo(screenLeft - w * 0.10f, screenTop + screenH * 0.15f)
            quadraticTo(screenLeft - w * 0.16f, screenTop + screenH * 0.5f, screenLeft - w * 0.10f, screenTop + screenH * 0.85f)
        }
        drawPath(path = leftInner, color = tint, style = Stroke(width = strokeWidth, cap = strokeCap))

        val leftOuter = Path().apply {
            moveTo(screenLeft - w * 0.22f, screenTop + screenH * 0.05f)
            quadraticTo(screenLeft - w * 0.30f, screenTop + screenH * 0.5f, screenLeft - w * 0.22f, screenTop + screenH * 0.95f)
        }
        drawPath(path = leftOuter, color = tint, style = Stroke(width = strokeWidth, cap = strokeCap))

        val rightInner = Path().apply {
            moveTo(screenLeft + screenW + w * 0.10f, screenTop + screenH * 0.15f)
            quadraticTo(screenLeft + screenW + w * 0.16f, screenTop + screenH * 0.5f, screenLeft + screenW + w * 0.10f, screenTop + screenH * 0.85f)
        }
        drawPath(path = rightInner, color = tint, style = Stroke(width = strokeWidth, cap = strokeCap))

        val rightOuter = Path().apply {
            moveTo(screenLeft + screenW + w * 0.22f, screenTop + screenH * 0.05f)
            quadraticTo(screenLeft + screenW + w * 0.30f, screenTop + screenH * 0.5f, screenLeft + screenW + w * 0.22f, screenTop + screenH * 0.95f)
        }
        drawPath(path = rightOuter, color = tint, style = Stroke(width = strokeWidth, cap = strokeCap))
    }
}

// ==================== TAB 1: HOME CONTROLLER (iOS INFUSE STYLE) ====================
@Composable
fun HomeTabContent(
    viewModel: MainViewModel,
    movies: List<Movie>,
    watchlist: Set<String>,
    history: List<Movie>,
    primaryColor: Color,
    currentTheme: AppTheme,
    onImportClick: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    continueWatchingListState: LazyListState = rememberLazyListState(),
    favoritesListState: LazyListState = rememberLazyListState(),
    picksListState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val mutedGray = Color(0xFF8E8E93)
    val darkCard = Color(0xFF1C1C1E)

    val homeFilteredMovies = remember(movies) {
        val nonHomeCategory = movies.filter { !it.category.equals("Home Videos", ignoreCase = true) }
        com.example.util.HomeVideoFilter.filterHomeContent(nonHomeCategory, { it.filePath }, { it.fileName })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. HEADER: Bold "Home" title + iOS styled capsule with sort and refresh actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp
                )
            )

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Import / Add files button
                IconButton(
                    onClick = { onImportClick() },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("home_import_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Import Files",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Sync & Refresh media items
                IconButton(
                    onClick = {
                        viewModel.refreshMediaItems()
                        Toast.makeText(context, "Refreshing library...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("home_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync & Refresh",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 2. CONTINUE WATCHING / FEATURED PROMINENT BANNER (TOP)
        val savedPositions by viewModel.savedPositions.collectAsState()
        val savedDurations by viewModel.savedDurations.collectAsState()
        val watchedEpisodes by viewModel.watchedEpisodes.collectAsState()
        val posterOverridesVersion by viewModel.posterOverridesVersion.collectAsState()
        val tmdbLoadingIds by viewModel.tmdbLoadingIds.collectAsState()

        val continueWatchingItems = remember(history, homeFilteredMovies, savedPositions, savedDurations, watchedEpisodes, posterOverridesVersion) {
            val allCandidates = (history + homeFilteredMovies).distinctBy { it.id }
            val resumeList = mutableListOf<ContinueWatchingCardModel>()

            for (movie in allCandidates) {
                if (movie.category.equals("Home Videos", ignoreCase = true)) continue
                if (com.example.util.HomeVideoFilter.isHomeVideo(movie.filePath, movie.fileName)) continue

                val isShow = com.example.util.ContinueWatchingHelper.isTvEpisode(movie)

                if (isShow) {
                    val showTitle = com.example.network.GeminiService.cleanShowTitle(movie.title)
                    val cleanShowDisplay = com.example.util.EpisodeMetadataFixer.sanitizeMediaTitle(
                        if (showTitle.isNotBlank()) showTitle else parseShowTitleFromMovie(movie)
                    )

                    val directSe = com.example.util.EpisodeUtils.extractSeasonAndEpisode(movie.title, movie.filePath ?: movie.id)
                    val directS = directSe.season ?: 1
                    val directE = directSe.episode ?: 1
                    val directSavedPos = viewModel.getSavedEpisodePosition(cleanShowDisplay, directS, directE, movie.id)

                    val savedState = viewModel.getSavedShowWatchState(showTitle)
                        ?: viewModel.getSavedShowWatchState(cleanShowDisplay)
                        ?: viewModel.getSavedShowWatchState(movie.id)

                    val activeMovie = if (directSavedPos > 0L && !watchedEpisodes.contains(movie.id)) {
                        movie
                    } else if (savedState?.episodeId != null && !watchedEpisodes.contains(savedState.episodeId)) {
                        allCandidates.firstOrNull { it.id == savedState.episodeId } ?: movie
                    } else {
                        movie
                    }

                    val seInfo = com.example.util.EpisodeUtils.extractSeasonAndEpisode(
                        activeMovie.title,
                        activeMovie.filePath ?: activeMovie.id
                    )
                    val sNum = seInfo.season ?: (if (activeMovie.id == savedState?.episodeId) savedState.season else null) ?: 1
                    val eNum = seInfo.episode ?: (if (activeMovie.id == savedState?.episodeId) savedState.episode else null) ?: 1

                    val isWatched = watchedEpisodes.contains(activeMovie.id)
                    if (isWatched) continue

                    val savedPos = viewModel.getSavedEpisodePosition(cleanShowDisplay, sNum, eNum, activeMovie.id)
                    if (savedPos <= 0L) continue

                    val knownDurMs = viewModel.getSavedPlaybackDuration(activeMovie.id, cleanShowDisplay, sNum, eNum)
                    val totalDurMs = if (knownDurMs > 0L) {
                        knownDurMs
                    } else {
                        parseDurationMs(activeMovie.duration.ifBlank { movie.duration })
                    }
                    val ratio = if (totalDurMs > 0L) (savedPos.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f

                    val isInProgress = savedPos > 0L && (totalDurMs <= 0L || ratio < 0.95f)

                    if (isInProgress) {
                        val cachedEpName = viewModel.getSavedTmdbEpisodeName(activeMovie.id, cleanShowDisplay, sNum, eNum) ?: ""
                        val parsedFallbackEpName = com.example.util.EpisodeUtils.cleanEpisodeTitle(activeMovie.title)
                        val epName = when {
                            cachedEpName.isNotBlank() -> cachedEpName
                            parsedFallbackEpName.isNotBlank() &&
                                !parsedFallbackEpName.equals(cleanShowDisplay, ignoreCase = true) &&
                                !parsedFallbackEpName.matches(Regex("""(?i)^episode\s*\d+$""")) -> parsedFallbackEpName
                            else -> ""
                        }
                        val epSubtitle = if (epName.isNotBlank()) "S$sNum • E$eNum - $epName" else "S$sNum • E$eNum"

                        val targetNormalized = com.example.util.EpisodeUtils.getNormalizedShowTitle(cleanShowDisplay.ifBlank { showTitle })
                        val seriesCandidate = allCandidates.firstOrNull { m ->
                            (m.category.equals("Shows", ignoreCase = true) || com.example.util.ContinueWatchingHelper.isTvEpisode(m)) &&
                                    com.example.util.EpisodeUtils.getNormalizedShowTitle(m.title) == targetNormalized
                        } ?: activeMovie
                        val seriesMainPoster = viewModel.getDetailScreenMainPosterUrl(activeMovie, seriesCandidate).ifBlank {
                            viewModel.getCardPosterUrlForShow(cleanShowDisplay)
                        }
                        val seriesBackdropInfo = viewModel.getContinueWatchingBackdropInfo(
                            movie = activeMovie,
                            candidateSeriesMovie = seriesCandidate,
                            isTvShow = true,
                            showTitle = cleanShowDisplay
                        )

                        resumeList.add(
                            ContinueWatchingCardModel(
                                movie = activeMovie,
                                isShow = true,
                                displayTitle = cleanShowDisplay,
                                subtitle = epSubtitle,
                                posterUrl = seriesMainPoster,
                                backdropUrl = seriesBackdropInfo.first,
                                isLandscapeBackdrop = seriesBackdropInfo.second,
                                savedPosMs = savedPos,
                                totalDurMs = totalDurMs,
                                progressRatio = ratio.coerceAtLeast(0.02f)
                            )
                        )
                    }
                } else {
                    val isWatched = watchedEpisodes.contains(movie.id)
                    if (isWatched) continue

                    val savedPos = viewModel.getSavedPlaybackPosition(movie.id)
                    if (savedPos <= 0L) continue

                    val knownDurMs = viewModel.getSavedPlaybackDuration(movie.id)
                    val totalDurMs = if (knownDurMs > 0L) knownDurMs else parseDurationMs(movie.duration)
                    val ratio = if (totalDurMs > 0L) (savedPos.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f

                    if (savedPos > 0L && (totalDurMs <= 0L || ratio < 0.95f)) {
                        val cleanTitle = com.example.util.EpisodeMetadataFixer.sanitizeMediaTitle(movie.title)
                        val remainingMs = if (totalDurMs > savedPos && savedPos > 0L) totalDurMs - savedPos else 0L
                        val rawSubtitle = if (remainingMs > 0L) {
                            val mins = remainingMs / 60000L
                            if (mins >= 60) "${mins / 60}h ${mins % 60}m remaining" else if (mins > 0) "${mins}m remaining" else "${(remainingMs / 1000L).coerceAtLeast(1L)}s remaining"
                        } else {
                            movie.releaseYear.ifBlank { movie.duration }
                        }
                        val timeRemainingSubtitle = rawSubtitle.replace(Regex("""(?i)\bS\d+[\s•._-]*E\d+\b|\bSeason\s*\d+|\bEpisode\s*\d+"""), "").trim()

                        val movieMainPoster = viewModel.getDetailScreenMainPosterUrl(movie)
                        val movieBackdropInfo = viewModel.getContinueWatchingBackdropInfo(
                            movie = movie,
                            isTvShow = false
                        )

                        resumeList.add(
                            ContinueWatchingCardModel(
                                movie = movie,
                                isShow = false,
                                displayTitle = cleanTitle,
                                subtitle = timeRemainingSubtitle,
                                posterUrl = movieMainPoster,
                                backdropUrl = movieBackdropInfo.first,
                                isLandscapeBackdrop = movieBackdropInfo.second,
                                savedPosMs = savedPos,
                                totalDurMs = totalDurMs,
                                progressRatio = ratio.coerceAtLeast(0.02f)
                            )
                        )
                    }
                }
            }

            val seenEpisodeKeys = mutableSetOf<String>()
            val result = mutableListOf<ContinueWatchingCardModel>()
            for (item in resumeList) {
                val uniqueKey = com.example.util.ContinueWatchingHelper.getUniqueContinueWatchingEntryKey(item.movie)
                if (seenEpisodeKeys.add(uniqueKey)) {
                    result.add(item)
                }
            }
            result
        }

        if (continueWatchingItems.isNotEmpty()) {
            LazyRow(
                state = continueWatchingListState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(continueWatchingItems, key = { index, item -> "cw_${item.movie.id}_$index" }) { index, item ->
                    val (resolvedImage, isLandscape) = remember(item.movie, item.isShow, item.displayTitle, posterOverridesVersion) {
                        viewModel.getContinueWatchingBackdropInfo(
                            movie = item.movie,
                            candidateSeriesMovie = null,
                            isTvShow = item.isShow,
                            showTitle = item.displayTitle
                        )
                    }
                    val imageModel = remember(resolvedImage, item.backdropUrl, item.posterUrl) {
                        if (resolvedImage.isNotBlank()) resolvedImage
                        else if (item.backdropUrl.isNotBlank()) item.backdropUrl
                        else item.posterUrl
                    }
                    val isLandscapeImage = remember(resolvedImage, item.isLandscapeBackdrop) {
                        if (resolvedImage.isNotBlank()) isLandscape else item.isLandscapeBackdrop
                    }
                    val imageSize = if (isLandscapeImage) "w1280" else "w780"
                    val bannerReq = com.example.util.rememberTmdbImageRequest(imageModel, imageSize)

                    var showPosterMenu by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .width(310.dp)
                            .infuseClickable(
                                onLongClick = { showPosterMenu = true }
                            ) { viewModel.playContinueWatchingMedia(item.movie) }
                            .testTag("continue_watching_card_$index")
                    ) {
                        // Original Wide Horizontal 16:9 Landscape Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(178.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E1E24))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        ) {
                            if (imageModel.isNotBlank()) {
                                if (isLandscapeImage) {
                                    // 16:9 Landscape TMDB Backdrop: Fill the entire container edge-to-edge
                                    com.example.util.SharedPosterImage(
                                        model = bannerReq ?: imageModel,
                                        contentDescription = item.displayTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.Center
                                    )
                                } else {
                                    // Fallback Portrait Poster: Softly blurred background + complete portrait poster centered in foreground
                                    com.example.util.SharedPosterImage(
                                        model = bannerReq ?: imageModel,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(20.dp),
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.Center
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                    )

                                    com.example.util.SharedPosterImage(
                                        model = bannerReq ?: imageModel,
                                        contentDescription = item.displayTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.Center
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.displayTitle,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }

                            // Dark vignette overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to Color.Black.copy(alpha = 0.15f),
                                            0.40f to Color.Transparent,
                                            0.70f to Color.Black.copy(alpha = 0.20f),
                                            1.0f to Color.Black.copy(alpha = 0.60f)
                                        )
                                    )
                            )

                            // Centered Clean Play Button Overlay (White circular ring + white play triangle)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.40f))
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .offset(x = 1.5.dp)
                                )
                            }

                            // Bottom Edge Horizontal Progress Bar (Dynamic appearance accent)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .background(Color.Black.copy(alpha = 0.60f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(item.progressRatio.coerceIn(0.04f, 1f))
                                        .background(primaryColor)
                                )
                            }

                            // Long-press Card Poster Options Dialogs
                            CardPosterMenuButtonWithDialogs(
                                targetMovie = item.movie,
                                isTvShow = item.isShow,
                                showTitle = if (item.isShow) item.displayTitle else null,
                                currentPosterUrl = imageModel,
                                primaryColor = primaryColor,
                                viewModel = viewModel,
                                showActionSheet = showPosterMenu,
                                onDismissActionSheet = { showPosterMenu = false }
                            )
                        }

                        Spacer(modifier = Modifier.height(9.dp))

                        // Title & Subtitle below card
                        Text(
                            text = item.displayTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = mutedGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. FAVORITES SECTION (Home-only, using existing FavoritesRepository & suggestion logic)
        val favoriteMovieIds by viewModel.favoriteMovieIds.collectAsState()
        val favoriteMovies = remember(homeFilteredMovies, favoriteMovieIds) {
            if (homeFilteredMovies.isEmpty() || favoriteMovieIds.isEmpty()) {
                emptyList()
            } else {
                val matched = homeFilteredMovies.filter { favoriteMovieIds.contains(it.id) }
                val tvShows = matched.filter { it.category.equals("Shows", ignoreCase = true) }
                val nonShows = matched.filter { !it.category.equals("Shows", ignoreCase = true) }

                val groupedShows = com.example.util.TVShowGroupingHelper.groupSeasonsUnderSingleShow(
                    mediaList = tvShows,
                    getShowId = { com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(it)) },
                    getShowTitle = { parseShowTitleFromMovie(it) },
                    getSeasonNumber = { com.example.util.LocalSeasonParser.parseSeasonNumber(it.filePath ?: "", it.title) }
                )
                val representativeTvMovies = groupedShows.map { unified ->
                    unified.allEpisodes.firstOrNull { favoriteMovieIds.contains(it.id) } ?: unified.allEpisodes.first()
                }
                (representativeTvMovies + nonShows).distinctBy { it.id }
            }
        }
        var isFavoritesExpandedOnHome by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            fontSize = 20.sp
                        )
                    )
                    if (favoriteMovies.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(primaryColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${favoriteMovies.size}",
                                color = primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = if (isFavoritesExpandedOnHome && favoriteMovies.size > 3) "Show Less" else "See All",
                    color = primaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .infuseClickable {
                            if (favoriteMovies.isNotEmpty()) {
                                isFavoritesExpandedOnHome = !isFavoritesExpandedOnHome
                            }
                        }
                        .testTag("favorites_see_all_button")
                )
            }

            if (favoriteMovies.isNotEmpty()) {
                val displayedFavorites = if (isFavoritesExpandedOnHome) favoriteMovies else favoriteMovies
                LazyRow(
                    state = favoritesListState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(displayedFavorites, key = { "fav_${it.id}" }) { favMovie ->
                        val isTvShow = favMovie.category.equals("Shows", ignoreCase = true)
                        val displayTitle = remember(favMovie) {
                            if (isTvShow) parseShowTitleFromMovie(favMovie) else favMovie.title
                        }
                        val sub = remember(favMovie, movies) {
                            if (isTvShow) {
                                val showEpisodes = movies.filter { it.category.equals("Shows", ignoreCase = true) }
                                val grouped = com.example.util.TVShowGroupingHelper.groupSeasonsUnderSingleShow(
                                    mediaList = showEpisodes,
                                    getShowId = { com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(it)) },
                                    getShowTitle = { parseShowTitleFromMovie(it) },
                                    getSeasonNumber = { com.example.util.LocalSeasonParser.parseSeasonNumber(it.filePath ?: "", it.title) }
                                )
                                val normTitle = com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(favMovie))
                                val matchedShow = grouped.firstOrNull { it.showId == normTitle }
                                val seasonsCount = matchedShow?.seasonCount ?: 1
                                val currentSeason = com.example.util.LocalSeasonParser.parseSeasonNumber(favMovie.filePath ?: "", favMovie.title)
                                com.example.util.PosterCardUIHelper.getShowSubtitleText(seasonsCount, currentSeason)
                            } else {
                                favMovie.releaseYear.ifBlank { favMovie.category }
                            }
                        }

                        var showPosterMenu by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .width(125.dp)
                                .infuseClickable(
                                    onLongClick = { showPosterMenu = true }
                                ) { viewModel.navigateTo(AppScreen.Details(favMovie.id)) }
                                .testTag("favorite_card_${favMovie.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(188.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(darkCard)
                                    .border(1.dp, primaryColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                            ) {
                                val rawPoster = remember(favMovie, posterOverridesVersion) {
                                    viewModel.getCardPosterUrl(favMovie).ifBlank { favMovie.logoPosterUrl.ifBlank { favMovie.posterUrl.ifBlank { favMovie.cleanPosterUrl } } }
                                }
                                val formattedPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(rawPoster, "w780")
                                val posterReq = com.example.util.rememberTmdbImageRequest(formattedPoster, "w780")
                                val isFavFetching = remember(favMovie.id, favMovie.title, displayTitle, tmdbLoadingIds) {
                                    tmdbLoadingIds.contains(favMovie.id) ||
                                        tmdbLoadingIds.contains(favMovie.title) ||
                                        tmdbLoadingIds.contains(displayTitle) ||
                                        tmdbLoadingIds.contains(com.example.util.EpisodeUtils.getNormalizedShowTitle(displayTitle))
                                }
                                if (formattedPoster.isNotBlank()) {
                                    com.example.util.SharedPosterImage(
                                        model = posterReq ?: formattedPoster,
                                        contentDescription = displayTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (isFavFetching) {
                                    com.example.util.InfuseShimmerBox(
                                        shape = RoundedCornerShape(0.dp),
                                        accentColor = primaryColor,
                                        showBorder = false,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("fav_card_shimmer_${favMovie.id}")
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(darkCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displayTitle,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                // Favorite Star Badge on Card (TopStart so TopEnd is free for 3-dot menu)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .border(1.dp, primaryColor.copy(alpha = 0.75f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Favorite",
                                        tint = primaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                // Long-press Card Poster Options Dialogs
                                CardPosterMenuButtonWithDialogs(
                                    targetMovie = favMovie,
                                    isTvShow = isTvShow,
                                    showTitle = if (isTvShow) displayTitle else null,
                                    currentPosterUrl = formattedPoster,
                                    primaryColor = primaryColor,
                                    viewModel = viewModel,
                                    showActionSheet = showPosterMenu,
                                    onDismissActionSheet = { showPosterMenu = false }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = sub,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                // Empty state card using selected appearance color
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, primaryColor.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .testTag("home_favorites_empty_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(primaryColor, currentTheme.primaryContainer)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = currentTheme.onPrimaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No favorites yet",
                            color = primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap the star button on any movie or series to keep it handy in your favorites.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 4. "PICKS OF THE DAY" / RECOMMENDED SHELF
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Picks of the Day",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )
                Text(
                    text = "See All",
                    color = primaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.infuseClickable { viewModel.selectTab("Library") }
                )
            }

            val picksList = remember(homeFilteredMovies) {
                if (homeFilteredMovies.isEmpty()) {
                    emptyList()
                } else {
                    val tvShows = homeFilteredMovies.filter { it.category.equals("Shows", ignoreCase = true) }
                    val nonShows = homeFilteredMovies.filter { !it.category.equals("Shows", ignoreCase = true) }

                    val groupedShows = com.example.util.TVShowGroupingHelper.groupSeasonsUnderSingleShow(
                        mediaList = tvShows,
                        getShowId = { com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(it)) },
                        getShowTitle = { parseShowTitleFromMovie(it) },
                        getSeasonNumber = { com.example.util.LocalSeasonParser.parseSeasonNumber(it.filePath ?: "", it.title) }
                    )

                    val representativeTvMovies = groupedShows.map { unified -> unified.allEpisodes.first() }
                    (representativeTvMovies + nonShows).distinctBy { it.id }
                }
            }

            if (picksList.isNotEmpty()) {
                LazyRow(
                    state = picksListState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(picksList, key = { index, movie -> "pick_${movie.id}_$index" }) { index, movie ->
                        val isTvShow = movie.category.equals("Shows", ignoreCase = true)
                        val subtitleText = remember(movie, movies) {
                            if (isTvShow) {
                                val showEpisodes = movies.filter {
                                    it.category.equals("Shows", ignoreCase = true)
                                }
                                val grouped = com.example.util.TVShowGroupingHelper.groupSeasonsUnderSingleShow(
                                    mediaList = showEpisodes,
                                    getShowId = { com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(it)) },
                                    getShowTitle = { parseShowTitleFromMovie(it) },
                                    getSeasonNumber = { com.example.util.LocalSeasonParser.parseSeasonNumber(it.filePath ?: "", it.title) }
                                )
                                val normTitle = com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(movie))
                                val matchedShow = grouped.firstOrNull { it.showId == normTitle }
                                val seasonsCount = matchedShow?.seasonCount ?: 1
                                val currentSeason = com.example.util.LocalSeasonParser.parseSeasonNumber(movie.filePath ?: "", movie.title)
                                com.example.util.PosterCardUIHelper.getShowSubtitleText(seasonsCount, currentSeason)
                            } else {
                                if (movie.releaseYear.isNotBlank()) {
                                    movie.releaseYear
                                } else {
                                    "2023"
                                }
                            }
                        }

                        val displayTitle = remember(movie) {
                            if (isTvShow) {
                                parseShowTitleFromMovie(movie)
                            } else {
                                movie.title
                            }
                        }

                        var showPosterMenu by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .width(125.dp)
                                .infuseClickable(
                                    onLongClick = { showPosterMenu = true }
                                ) { viewModel.navigateTo(AppScreen.Details(movie.id)) }
                                .testTag("pick_card_$index")
                        ) {
                            // 2:3 Aspect Ratio Poster Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(188.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(darkCard)
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            ) {
                                val rawPoster = remember(movie, posterOverridesVersion) {
                                    viewModel.getCardPosterUrl(movie).ifBlank { movie.logoPosterUrl.ifBlank { movie.posterUrl.ifBlank { movie.cleanPosterUrl } } }
                                }
                                val formattedPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(rawPoster, "w780")
                                val homePosterReq = com.example.util.rememberTmdbImageRequest(formattedPoster, "w780")
                                val isPickFetching = remember(movie.id, movie.title, displayTitle, tmdbLoadingIds) {
                                    tmdbLoadingIds.contains(movie.id) ||
                                        tmdbLoadingIds.contains(movie.title) ||
                                        tmdbLoadingIds.contains(displayTitle) ||
                                        tmdbLoadingIds.contains(com.example.util.EpisodeUtils.getNormalizedShowTitle(displayTitle))
                                }
                                if (formattedPoster.isNotBlank()) {
                                    com.example.util.SharedPosterImage(
                                        model = homePosterReq ?: formattedPoster,
                                        contentDescription = displayTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (isPickFetching) {
                                    com.example.util.InfuseShimmerBox(
                                        shape = RoundedCornerShape(0.dp),
                                        accentColor = primaryColor,
                                        showBorder = false,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("pick_card_shimmer_${movie.id}")
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(darkCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displayTitle,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                // Long-press Card Poster Options Dialogs
                                CardPosterMenuButtonWithDialogs(
                                    targetMovie = movie,
                                    isTvShow = isTvShow,
                                    showTitle = if (isTvShow) displayTitle else null,
                                    currentPosterUrl = formattedPoster,
                                    primaryColor = primaryColor,
                                    viewModel = viewModel,
                                    showActionSheet = showPosterMenu,
                                    onDismissActionSheet = { showPosterMenu = false }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitleText,
                                color = mutedGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

// ==================== SEARCH TAB CONTROLLER ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabContent(
    viewModel: MainViewModel,
    movies: List<Movie>,
    primaryColor: Color,
    emptyScrollState: ScrollState = rememberScrollState(),
    gridState: LazyGridState = rememberLazyGridState()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val posterOverridesVersion by viewModel.posterOverridesVersion.collectAsState()
    val tmdbLoadingIds by viewModel.tmdbLoadingIds.collectAsState()
    val darkCard = Color(0xFF1C1C1E)

    val searchResults = remember(movies, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else movies.filter { movie ->
            movie.title.contains(searchQuery, ignoreCase = true) ||
                    movie.genre.any { it.contains(searchQuery, ignoreCase = true) } ||
                    movie.category.contains(searchQuery, ignoreCase = true) ||
                    movie.synopsis.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp
            )
        )

        // iOS Style Search Input
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search movies, TV shows, genres...", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = primaryColor)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                disabledContainerColor = darkCard,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_tab_input")
        )

        if (searchQuery.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(emptyScrollState)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Quick Categories",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                val tags = listOf("4K HDR", "Action", "Sci-Fi", "Drama", "Documentary", "Animation", "Comedy", "Thriller")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tags, key = { it }) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(darkCard)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .clickable { viewModel.setSearchQuery(tag) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = tag,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No media found for \"$searchQuery\"",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults, key = { it.id }) { movie ->
                        var showPosterMenu by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .infuseClickable(
                                    onLongClick = { showPosterMenu = true }
                                ) { viewModel.navigateTo(AppScreen.Details(movie.id)) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(darkCard)
                            ) {
                                val isTvShow = movie.category.equals("Shows", ignoreCase = true)
                                val showTitle = if (isTvShow) parseShowTitleFromMovie(movie) else null
                                val rawSearchPoster = remember(movie, posterOverridesVersion) {
                                    viewModel.getCardPosterUrl(movie).ifBlank { movie.logoPosterUrl.ifBlank { movie.posterUrl.ifBlank { movie.cleanPosterUrl } } }
                                }
                                val formattedGridPoster = com.example.util.PosterCardUIHelper.formatTmdbUrl(rawSearchPoster)
                                val searchGridReq = com.example.util.rememberTmdbImageRequest(formattedGridPoster, "w780")
                                val isSearchItemFetching = remember(movie.id, movie.title, showTitle, tmdbLoadingIds) {
                                    tmdbLoadingIds.contains(movie.id) ||
                                        tmdbLoadingIds.contains(movie.title) ||
                                        (!showTitle.isNullOrBlank() && (
                                            tmdbLoadingIds.contains(showTitle) ||
                                            tmdbLoadingIds.contains(com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitle))
                                        ))
                                }
                                if (formattedGridPoster.isNotBlank()) {
                                    com.example.util.SharedPosterImage(
                                        model = searchGridReq ?: formattedGridPoster,
                                        contentDescription = movie.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (isSearchItemFetching) {
                                    com.example.util.InfuseShimmerBox(
                                        shape = RoundedCornerShape(0.dp),
                                        accentColor = primaryColor,
                                        showBorder = false,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("search_card_shimmer_${movie.id}")
                                    )
                                }

                                // Long-press Card Poster Options Dialogs
                                CardPosterMenuButtonWithDialogs(
                                    targetMovie = movie,
                                    isTvShow = isTvShow,
                                    showTitle = showTitle,
                                    currentPosterUrl = formattedGridPoster,
                                    primaryColor = primaryColor,
                                    viewModel = viewModel,
                                    showActionSheet = showPosterMenu,
                                    onDismissActionSheet = { showPosterMenu = false }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = movie.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = movie.category,
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class UnifiedMediaItem {
    data class Show(val showStack: com.example.model.ShowStack) : UnifiedMediaItem()
    data class Film(val movie: Movie) : UnifiedMediaItem()
}

// ==================== TAB 2: LIBRARY CONTROLLER ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabContent(
    viewModel: MainViewModel,
    movies: List<Movie>,
    selectedCategory: String,
    searchQuery: String,
    density: String,
    primaryColor: Color,
    currentTheme: AppTheme,
    onImportClick: () -> Unit,
    gridMode: GridMode = GridMode.COMFORTABLE,
    gridState: LazyGridState = rememberLazyGridState()
) {
    var isSearching by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val isHomeVideos = selectedCategory.equals("Home Videos", ignoreCase = true)

    val moviesList = remember(movies, searchQuery) {
        movies.filter { movie ->
            val isMovie = movie.category.equals("Movies", ignoreCase = true) ||
                    (!movie.category.equals("Shows", ignoreCase = true) && !movie.category.equals("Home Videos", ignoreCase = true))
            if (!isMovie) return@filter false
            if (searchQuery.isEmpty()) return@filter true
            movie.title.contains(searchQuery, ignoreCase = true) ||
                    movie.genre.any { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    val posterOverridesVersion by viewModel.posterOverridesVersion.collectAsState()
    val isLibrarySyncing by viewModel.isLibrarySyncing.collectAsState()

    val showsList = remember(movies, searchQuery, posterOverridesVersion) {
        val tvMovies = movies.filter { movie ->
            val isShow = movie.category.equals("Shows", ignoreCase = true)
            if (!isShow) return@filter false
            if (searchQuery.isEmpty()) return@filter true
            val showTitle = parseShowTitleFromMovie(movie)
            movie.title.contains(searchQuery, ignoreCase = true) ||
                    showTitle.contains(searchQuery, ignoreCase = true) ||
                    movie.genre.any { it.contains(searchQuery, ignoreCase = true) }
        }
        val unifiedShows = com.example.util.TVShowGroupingHelper.groupSeasonsUnderSingleShow(
            mediaList = tvMovies,
            getShowId = { com.example.util.EpisodeUtils.getNormalizedShowTitle(parseShowTitleFromMovie(it)) },
            getShowTitle = { parseShowTitleFromMovie(it) },
            getSeasonNumber = { com.example.util.LocalSeasonParser.parseSeasonNumber(it.filePath ?: "", it.title) }
        )
        unifiedShows.map { unified ->
            val sortedEps = com.example.util.EpisodeUtils.sortEpisodesChronologically(unified.allEpisodes)
            val seriesPoster = viewModel.getCardPosterUrlForShow(unified.showTitle)
                .takeIf { it.isNotBlank() }
                ?: sortedEps.firstNotNullOfOrNull { ep ->
                    viewModel.getCardPosterUrl(ep).takeIf { it.isNotBlank() }
                }
                ?: sortedEps.firstOrNull { it.logoPosterUrl.isNotEmpty() || it.posterUrl.isNotEmpty() || it.cleanPosterUrl.isNotEmpty() }?.run { logoPosterUrl.ifBlank { posterUrl.ifBlank { cleanPosterUrl } } }
                ?: ""
            com.example.model.ShowStack(
                showTitle = unified.showTitle,
                totalEpisodes = sortedEps.size,
                episodes = sortedEps,
                posterUrl = seriesPoster
            )
        }.sortedBy { it.showTitle }
    }

    // Combine both lists into one unified list
    val combinedMediaList = remember(moviesList, showsList) {
        val list = mutableListOf<UnifiedMediaItem>()
        showsList.forEach { list.add(UnifiedMediaItem.Show(it)) }
        moviesList.forEach { list.add(UnifiedMediaItem.Film(it)) }
        list
    }

    val homeVideosList = remember(movies, searchQuery) {
        movies.filter { movie ->
            val isHome = movie.category.equals("Home Videos", ignoreCase = true)
            if (!isHome) return@filter false
            if (searchQuery.isEmpty()) return@filter true
            movie.title.contains(searchQuery, ignoreCase = true) ||
                    movie.genre.any { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    var selectedShowStack by remember { mutableStateOf<com.example.model.ShowStack?>(null) }

    val isEmpty = if (isHomeVideos) homeVideosList.isEmpty() else combinedMediaList.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSearching) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search title, genre...", color = Color.White.copy(alpha = 0.4f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1B1C1C),
                            unfocusedContainerColor = Color(0xFF1B1C1C).copy(alpha = 0.8f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(26.dp))
                            .testTag("search_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = primaryColor)
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.setSearchQuery("")
                                isSearching = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.White)
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { isSearching = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("search_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = primaryColor
                            )
                        }
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }

                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.Settings) },
                        modifier = Modifier.size(40.dp).testTag("settings_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Quick tune",
                            tint = primaryColor
                        )
                    }
                }
            }

            // Category Chips Row (Movies & Shows, Home Videos — single line, no wrapping)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Movies & Shows", "Home Videos").forEach { category ->
                    val isSelected = if (category == "Movies & Shows") {
                        !selectedCategory.equals("Home Videos", ignoreCase = true)
                    } else {
                        selectedCategory.equals(category, ignoreCase = true)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) primaryColor
                                else Color(0xFF1B1C1C)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .infuseClickable { viewModel.selectCategory(category) }
                            .padding(horizontal = 20.dp, vertical = 9.dp)
                            .testTag("category_chip_$category"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) currentTheme.onPrimaryColor else Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            val isCompact = gridMode == GridMode.COMPACT
            val gridCells = if (isCompact) 3 else 2
            val horizontalPadding = if (isCompact) 16.dp else 24.dp
            val spacing = if (isCompact) 10.dp else 16.dp

            // Main Grid list
            if (isEmpty && isLibrarySyncing) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridCells),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .testTag("library_skeleton_grid"),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(6, key = { "skeleton_$it" }) { index ->
                        com.example.util.MovieGridSkeletonCard(
                            isCompact = isCompact,
                            isShowStack = !isHomeVideos && index % 3 == 0,
                            primaryColor = primaryColor
                        )
                    }
                }
            } else if (isEmpty) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Empty",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "No items found in this section.",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Import media to populate your library.",
                            color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onImportClick() },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("import_gallery_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = currentTheme.onPrimaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Import from Gallery",
                                    color = currentTheme.onPrimaryColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(gridCells),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    if (isHomeVideos) {
                        items(homeVideosList, key = { it.id }) { movie ->
                            MovieGridCard(
                                movie = movie,
                                primaryColor = primaryColor,
                                viewModel = viewModel,
                                isCompact = isCompact,
                                onClick = { viewModel.navigateTo(AppScreen.Details(movie.id)) }
                            )
                        }
                    } else {
                        items(
                            items = combinedMediaList,
                            key = { item ->
                                when (item) {
                                    is UnifiedMediaItem.Show -> "show_${item.showStack.showTitle}_${item.showStack.episodes.firstOrNull()?.id ?: ""}"
                                    is UnifiedMediaItem.Film -> "film_${item.movie.id}"
                                }
                            }
                        ) { item ->
                            when (item) {
                                is UnifiedMediaItem.Show -> {
                                    ShowStackCard(
                                        showStack = item.showStack,
                                        primaryColor = primaryColor,
                                        viewModel = viewModel,
                                        isCompact = isCompact,
                                        onClick = {
                                            val targetId = item.showStack.episodes.firstOrNull()?.id ?: ""
                                            if (targetId.isNotEmpty()) {
                                                viewModel.navigateTo(AppScreen.Details(targetId))
                                            }
                                        }
                                    )
                                }
                                is UnifiedMediaItem.Film -> {
                                    MovieGridCard(
                                        movie = item.movie,
                                        primaryColor = primaryColor,
                                        viewModel = viewModel,
                                        isCompact = isCompact,
                                        onClick = { viewModel.navigateTo(AppScreen.Details(item.movie.id)) }
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

// ==================== TAB 3: FILES / CONNECTIONS CONTROLLER ====================
@Composable
fun FilesTabContent(
    viewModel: MainViewModel,
    connectedServers: Set<String>,
    primaryColor: Color,
    currentTheme: AppTheme,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Setup dialog states
    var showPlexDialog by remember { mutableStateOf(false) }
    var plexServerUrl by remember { mutableStateOf("https://plex.tv/cinephile") }
    var plexToken by remember { mutableStateOf("PROX_TKN_2026") }
    var isConnectingPlex by remember { mutableStateOf(false) }

    var showSmbDialog by remember { mutableStateOf(false) }
    var smbServerAddress by remember { mutableStateOf("192.168.1.120") }
    var smbUsername by remember { mutableStateOf("admin") }
    var isConnectingSmb by remember { mutableStateOf(false) }

    var showCloudDialog by remember { mutableStateOf(false) }
    var isConnectingCloud by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Tab Header
        Column {
            Text(
                text = "Files & Servers",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = "Stream and sync from your local devices or servers",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        // Section: Local Device
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Local Storage",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .infuseClickable { onImportClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(primaryColor.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = primaryColor)
                    }
                    Column {
                        Text("Local Photo Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Import MP4, MKV files from storage", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
            }
        }

        // Section: Network Media Servers
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Media Servers",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            // Plex Card
            val isPlexConnected = connectedServers.contains("Plex (cinephile)")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .infuseClickable {
                        if (isPlexConnected) {
                            viewModel.removeConnectedServer("Plex (cinephile)")
                            Toast.makeText(context, "Disconnected from Plex.", Toast.LENGTH_SHORT).show()
                        } else {
                            showPlexDialog = true
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isPlexConnected) Color(0xFFFF9900).copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = null,
                            tint = if (isPlexConnected) Color(0xFFFF9900) else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Column {
                        Text("Plex Media Server", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = if (isPlexConnected) "Connected • Stream Online Library" else "Sign in and browse Plex servers",
                            color = if (isPlexConnected) Color(0xFFFF9900) else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = if (isPlexConnected) "Disconnect" else "Set up",
                    color = if (isPlexConnected) Color.Red.copy(alpha = 0.7f) else primaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // SMB Network Share Card
            val isSmbConnected = connectedServers.contains("Local NAS (SMB)")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .infuseClickable {
                        if (isSmbConnected) {
                            viewModel.removeConnectedServer("Local NAS (SMB)")
                            Toast.makeText(context, "Disconnected from SMB Share.", Toast.LENGTH_SHORT).show()
                        } else {
                            showSmbDialog = true
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isSmbConnected) primaryColor.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = if (isSmbConnected) primaryColor else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Column {
                        Text("SMB / FTP Network Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = if (isSmbConnected) "Connected • SMB Share Mounted" else "Connect local NAS or network storage",
                            color = if (isSmbConnected) primaryColor else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = if (isSmbConnected) "Disconnect" else "Set up",
                    color = if (isSmbConnected) Color.Red.copy(alpha = 0.7f) else primaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Section: Cloud Storages
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Cloud Services",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            val isCloudConnected = connectedServers.contains("Google Drive")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .infuseClickable {
                        if (isCloudConnected) {
                            viewModel.removeConnectedServer("Google Drive")
                            Toast.makeText(context, "Disconnected Google Drive.", Toast.LENGTH_SHORT).show()
                        } else {
                            showCloudDialog = true
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isCloudConnected) Color(0xFF4285F4).copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (isCloudConnected) Color(0xFF4285F4) else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Column {
                        Text("Google Drive / Cloud", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = if (isCloudConnected) "Google Drive Linked" else "Stream from Google Drive or Dropbox",
                            color = if (isCloudConnected) Color(0xFF4285F4) else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = if (isCloudConnected) "Disconnect" else "Link Drive",
                    color = if (isCloudConnected) Color.Red.copy(alpha = 0.7f) else primaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // ==================== PLEX DIALOG ====================
        if (showPlexDialog) {
            AlertDialog(
                onDismissRequest = { if (!isConnectingPlex) showPlexDialog = false },
                containerColor = Color(0xFF1B1C1C),
                title = { Text("Connect to Plex Server", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connect to your Plex Media Server to sync your personal media library straight into Infuse.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        OutlinedTextField(
                            value = plexServerUrl,
                            onValueChange = { plexServerUrl = it },
                            label = { Text("Plex Server URL", color = primaryColor) },
                            singleLine = true,
                            enabled = !isConnectingPlex,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = plexToken,
                            onValueChange = { plexToken = it },
                            label = { Text("X-Plex-Token", color = primaryColor) },
                            singleLine = true,
                            enabled = !isConnectingPlex,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (isConnectingPlex) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Text("Connecting securely to server...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isConnectingPlex = true
                                delay(1200) // Beautiful simulated network connect
                                isConnectingPlex = false
                                showPlexDialog = false
                                viewModel.addConnectedServer("Plex (cinephile)")

                                Toast.makeText(context, "Plex Server Successfully Mounted!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        enabled = !isConnectingPlex
                    ) {
                        Text("Connect", color = currentTheme.onPrimaryColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPlexDialog = false },
                        enabled = !isConnectingPlex
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            )
        }

        // ==================== SMB DIALOG ====================
        if (showSmbDialog) {
            AlertDialog(
                onDismissRequest = { if (!isConnectingSmb) showSmbDialog = false },
                containerColor = Color(0xFF1B1C1C),
                title = { Text("Connect SMB Server", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Mount a local SMB/NFS network share to stream video files directly.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        OutlinedTextField(
                            value = smbServerAddress,
                            onValueChange = { smbServerAddress = it },
                            label = { Text("SMB Server IP Address", color = primaryColor) },
                            singleLine = true,
                            enabled = !isConnectingSmb,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = smbUsername,
                            onValueChange = { smbUsername = it },
                            label = { Text("Username", color = primaryColor) },
                            singleLine = true,
                            enabled = !isConnectingSmb,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (isConnectingSmb) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Text("Mounting network folder...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isConnectingSmb = true
                                delay(1200)
                                isConnectingSmb = false
                                showSmbDialog = false
                                viewModel.addConnectedServer("Local NAS (SMB)")

                                Toast.makeText(context, "SMB share mounted!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        enabled = !isConnectingSmb
                    ) {
                        Text("Mount Share", color = currentTheme.onPrimaryColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSmbDialog = false }, enabled = !isConnectingSmb) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            )
        }

        // ==================== GOOGLE DRIVE DIALOG ====================
        if (showCloudDialog) {
            AlertDialog(
                onDismissRequest = { if (!isConnectingCloud) showCloudDialog = false },
                containerColor = Color(0xFF1B1C1C),
                title = { Text("Connect Google Drive", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Link your Google account to play direct drive video links.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        if (isConnectingCloud) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                Text("Authenticating OAuth token...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        } else {
                            Text("This will mount a shared cloud folder inside your local library tabs.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isConnectingCloud = true
                                delay(1500)
                                isConnectingCloud = false
                                showCloudDialog = false
                                viewModel.addConnectedServer("Google Drive")

                                Toast.makeText(context, "Google Drive linked!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        enabled = !isConnectingCloud
                    ) {
                        Text("Authenticate Account", color = currentTheme.onPrimaryColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCloudDialog = false }, enabled = !isConnectingCloud) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            )
        }
    }
}

// ==================== TAB 4: PLAYLISTS / FAVORITES CONTROLLER ====================
@Composable
fun PlaylistsTabContent(
    viewModel: MainViewModel,
    movies: List<Movie>,
    watchlist: Set<String>,
    history: List<Movie>,
    primaryColor: Color,
    currentTheme: AppTheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Tab Header
        Column {
            Text(
                text = "Playlists & Watchlists",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = "Track your favorite movies, loops, and watch histories",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        // 1. DataStore Favorites List
        val favoriteMovieIds by viewModel.favoriteMovieIds.collectAsState()
        val favoritesList = movies.filter { favoriteMovieIds.contains(it.id) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "My Favorites (${favoritesList.size})",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                if (favoritesList.isNotEmpty()) {
                    Text(
                        text = "View on Home",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.infuseClickable {
                            viewModel.selectTab("Home")
                        }
                    )
                }
            }

            if (favoritesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No favorites saved yet. Tap the star button on any title.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            } else {
                favoritesList.forEach { movie ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .infuseClickable { viewModel.navigateTo(AppScreen.Details(movie.id)) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                val cleanP = viewModel.getCardPosterUrl(movie).ifBlank { movie.logoPosterUrl.ifBlank { movie.posterUrl.ifBlank { movie.cleanPosterUrl } } }
                                if (cleanP.isNotEmpty()) {
                                    com.example.util.SharedPosterImage(model = cleanP, contentDescription = null, contentScale = ContentScale.Fit)
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Column {
                                Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(movie.duration + " • " + movie.category, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // 2. Bookmarks group list
        val favs = movies.filter { watchlist.contains(it.id) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "My Bookmarks (${favs.size})",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            if (favs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No bookmarks added yet. Tap bookmark on details.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            } else {
                favs.forEach { movie ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .infuseClickable { viewModel.navigateTo(AppScreen.Details(movie.id)) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                if (movie.posterUrl.isNotEmpty()) {
                                    com.example.util.SharedPosterImage(model = movie.posterUrl, contentDescription = null, contentScale = ContentScale.Fit)
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Column {
                                Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(movie.duration + " • " + movie.category, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // Watch history logs list
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Watch History Logs (${history.size})",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No watch logs recorded. Start streaming videos!", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            } else {
                history.forEach { movie ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .infuseClickable { viewModel.navigateTo(AppScreen.Details(movie.id)) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                if (movie.posterUrl.isNotEmpty()) {
                                    com.example.util.SharedPosterImage(model = movie.posterUrl, contentDescription = null, contentScale = ContentScale.Fit)
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Column {
                                Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Recently Played • " + movie.duration, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // Custom loop playlists
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Featured Loop Playlists",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1C1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .clickable {
                        // Immediately play Sintel
                        viewModel.navigateTo(AppScreen.Player("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"))
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(primaryColor.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = primaryColor)
                    }
                    Column {
                        Text("Cinema Showcase Loop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Plays Sintel & Tears of Steel back-to-back", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = primaryColor)
            }
        }
    }
}

// ==================== HELPER REUSABLE MOVIE GRID CARD ====================
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

@Composable
fun MovieGridCard(
    movie: Movie,
    primaryColor: Color,
    onClick: () -> Unit,
    viewModel: MainViewModel? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val posterOverridesVersion = viewModel?.posterOverridesVersion?.collectAsState()?.value ?: 0
    val rawPoster = remember(movie, posterOverridesVersion, viewModel) {
        if (viewModel != null) viewModel.getCardPosterUrl(movie) else (movie.logoPosterUrl.ifBlank { movie.posterUrl.ifBlank { movie.cleanPosterUrl.ifBlank { movie.backdropUrl } } })
    }
    val effectivePoster = remember(rawPoster) {
        com.example.util.PosterCardUIHelper.formatTmdbUrl(rawPoster)
    }
    val isFallback = effectivePoster.isBlank()
    val isLink = movie.title.contains("link", ignoreCase = true) || movie.id.contains("link", ignoreCase = true)
    val isTvShow = movie.category.equals("Shows", ignoreCase = true)
    val showTitle = remember(movie, isTvShow) {
        if (isTvShow) parseShowTitleFromMovie(movie) else null
    }

    val tmdbLoadingIds = viewModel?.tmdbLoadingIds?.collectAsState()?.value ?: emptySet()
    val isTmdbFetching = remember(movie.id, movie.title, showTitle, tmdbLoadingIds) {
        tmdbLoadingIds.contains(movie.id) ||
            tmdbLoadingIds.contains(movie.title) ||
            (!showTitle.isNullOrBlank() && (
                tmdbLoadingIds.contains(showTitle) ||
                tmdbLoadingIds.contains(com.example.util.EpisodeUtils.getNormalizedShowTitle(showTitle))
            ))
    }

    androidx.compose.runtime.LaunchedEffect(movie.id, movie.title) {
        if (viewModel != null && (movie.posterUrl.isBlank() || !movie.posterUrl.startsWith("http"))) {
            viewModel.fetchAndCacheTmdbDetails(movie)
        }
    }

    var showPosterMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .infuseClickable(
                onLongClick = if (viewModel != null) {
                    { showPosterMenu = true }
                } else null,
                onClick = onClick
            )
            .testTag("movie_card_${movie.id}"),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
    ) {
        // Poster Card Container
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isFallback && !isTmdbFetching) Color(0xFF262626) else Color(0xFF1B1C1C)
            ),
            shape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isFallback && isTmdbFetching) {
                    com.example.util.InfuseShimmerBox(
                        shape = RoundedCornerShape(0.dp),
                        accentColor = primaryColor,
                        showBorder = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("movie_card_shimmer_${movie.id}")
                    )
                } else if (isFallback) {
                    // Fallback container with center accent icon and top-right accent ribbon
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLink) Icons.Default.Link else Icons.Default.Movie,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(if (isCompact) 32.dp else 48.dp)
                        )
                    }

                    // Top-right corner ribbon using selected appearance color
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val path = Path().apply {
                            moveTo(size.width - (if (isCompact) 16.dp else 24.dp).toPx(), 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, (if (isCompact) 16.dp else 24.dp).toPx())
                            close()
                        }
                        drawPath(path, color = primaryColor)
                    }
                } else {
                    com.example.util.SharedPosterImage(
                        model = effectivePoster,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Inner gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f)
                                    ),
                                    startY = 100f
                                )
                            )
                    )
                }

                // Favorite Star Badge on Card (TopStart so TopEnd is dedicated to the 3-dot menu)
                val favoriteMovieIds = viewModel?.favoriteMovieIds?.collectAsState()?.value ?: emptySet()
                if (favoriteMovieIds.contains(movie.id)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(if (isCompact) 4.dp else 6.dp)
                            .size(if (isCompact) 20.dp else 24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, primaryColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = primaryColor,
                            modifier = Modifier.size(if (isCompact) 11.dp else 13.dp)
                        )
                    }
                }

                // Long-press Card Poster Options Dialogs
                if (viewModel != null) {
                    CardPosterMenuButtonWithDialogs(
                        targetMovie = movie,
                        isTvShow = isTvShow,
                        showTitle = showTitle,
                        currentPosterUrl = effectivePoster,
                        primaryColor = primaryColor,
                        viewModel = viewModel,
                        showActionSheet = showPosterMenu,
                        onDismissActionSheet = { showPosterMenu = false }
                    )
                }
            }
        }

        // Text Info (Outside Card)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 1.dp else 2.dp)
        ) {
            Text(
                text = movie.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompact) 11.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = if (isFallback) "" else movie.releaseYear.ifBlank { movie.category }
            if (isFallback && isTmdbFetching) {
                Spacer(modifier = Modifier.height(2.dp))
                com.example.util.InfuseShimmerBox(
                    shape = RoundedCornerShape(4.dp),
                    accentColor = primaryColor,
                    showBorder = false,
                    modifier = Modifier
                        .width(if (isCompact) 42.dp else 56.dp)
                        .height(if (isCompact) 9.dp else 12.dp)
                )
            } else if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = if (isCompact) 9.sp else 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            val qualityBadge = remember(movie.filePath, movie.title, movie.tmdbBadge) {
                val fromFile = com.example.util.EpisodeMetadataParser.getLocalVideoQuality(movie.filePath)
                if (fromFile.isNotBlank()) fromFile
                else if (movie.tmdbBadge.isNotBlank()) movie.tmdbBadge
                else com.example.network.GeminiService.detectQualityBadge(movie.title + " " + (movie.filePath ?: ""))
            }
            if (qualityBadge.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .border(0.75.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = qualityBadge,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = if (isCompact) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ShowStackCard(
    showStack: com.example.model.ShowStack,
    primaryColor: Color,
    onClick: () -> Unit,
    viewModel: MainViewModel? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val seasonCount = remember(showStack) {
        showStack.episodes.map {
            com.example.util.LocalSeasonParser.parseSeasonNumber(it.filePath ?: "", it.title)
        }.distinct().size
    }
    val currentSeasonNumber = remember(showStack) {
        val firstEp = showStack.episodes.firstOrNull()
        if (firstEp != null) {
            com.example.util.LocalSeasonParser.parseSeasonNumber(firstEp.filePath ?: "", firstEp.title)
        } else 1
    }
    val subtitleText = com.example.util.PosterCardUIHelper.getShowSubtitleText(seasonCount, currentSeasonNumber)

    val isFallback = showStack.posterUrl.isBlank()
    val representativeEp = showStack.episodes.firstOrNull()
    var showPosterMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .infuseClickable(
                onLongClick = if (viewModel != null && representativeEp != null) {
                    { showPosterMenu = true }
                } else null,
                onClick = onClick
            )
            .testTag("show_stack_card_${showStack.showTitle}"),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isCompact) 4.dp else 8.dp) // allow some space for top stack offsets
        ) {
            // Back card (simulate stack effect)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1C1C).copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(2f / 3f)
                    .align(Alignment.TopCenter)
                    .offset(y = if (isCompact) (-4).dp else (-8).dp)
            ) {}

            // Middle card (simulate stack effect)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1C1C).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .aspectRatio(2f / 3f)
                    .align(Alignment.TopCenter)
                    .offset(y = if (isCompact) (-2).dp else (-4).dp)
            ) {}

            // Front main card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF262626)
                ),
                shape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .align(Alignment.TopCenter)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Default / fallback background with icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.15f),
                                        Color(0xFF141515)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Show Stack",
                            tint = primaryColor,
                            modifier = Modifier.size(if (isCompact) 28.dp else 40.dp)
                        )
                    }

                    val posterOverridesVersion = viewModel?.posterOverridesVersion?.collectAsState()?.value ?: 0
                    val rawShowPoster = remember(showStack, posterOverridesVersion, viewModel) {
                        if (viewModel != null) {
                            val cleanSeries = viewModel.getCardPosterUrlForShow(showStack.showTitle)
                            if (!cleanSeries.isNullOrBlank()) {
                                cleanSeries
                            } else if (showStack.posterUrl.isNotBlank() && !showStack.posterUrl.contains("unsplash")) {
                                showStack.posterUrl
                            } else if (showStack.episodes.isNotEmpty()) {
                                val firstEp = showStack.episodes.first()
                                viewModel.getCardPosterUrl(firstEp)
                            } else {
                                showStack.posterUrl
                            }
                        } else {
                            showStack.posterUrl
                        }
                    }
                    val firstEpisodePoster = remember(rawShowPoster) {
                        com.example.util.PosterCardUIHelper.formatTmdbUrl(rawShowPoster, "w780")
                    }

                    val tmdbLoadingIds = viewModel?.tmdbLoadingIds?.collectAsState()?.value ?: emptySet()
                    val isShowTmdbFetching = remember(showStack.showTitle, representativeEp?.id, tmdbLoadingIds) {
                        val normTitle = com.example.util.EpisodeUtils.getNormalizedShowTitle(showStack.showTitle)
                        tmdbLoadingIds.contains(showStack.showTitle) ||
                            tmdbLoadingIds.contains(normTitle) ||
                            (representativeEp != null && (tmdbLoadingIds.contains(representativeEp.id) || tmdbLoadingIds.contains(representativeEp.title)))
                    }

                    androidx.compose.runtime.LaunchedEffect(showStack.showTitle) {
                        if (viewModel != null && firstEpisodePoster.isBlank()) {
                            if (showStack.episodes.isNotEmpty()) {
                                viewModel.fetchAndCacheTmdbDetails(showStack.episodes.first())
                            }
                        }
                    }

                    val showImgReq = com.example.util.rememberTmdbImageRequest(firstEpisodePoster, "w780")
                    if (firstEpisodePoster.isNotEmpty()) {
                        com.example.util.SharedPosterImage(
                            model = showImgReq ?: firstEpisodePoster,
                            contentDescription = showStack.showTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (isShowTmdbFetching) {
                        com.example.util.InfuseShimmerBox(
                            shape = RoundedCornerShape(0.dp),
                            accentColor = primaryColor,
                            showBorder = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("show_stack_shimmer_${showStack.showTitle}")
                        )
                    }

                    // Inner gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f)
                                    ),
                                    startY = 100f
                                )
                            )
                    )

                    // Long-press Card Poster Options Dialogs for Show Stack
                    if (viewModel != null && representativeEp != null) {
                        CardPosterMenuButtonWithDialogs(
                            targetMovie = representativeEp,
                            isTvShow = true,
                            showTitle = showStack.showTitle,
                            currentPosterUrl = firstEpisodePoster,
                            primaryColor = primaryColor,
                            viewModel = viewModel,
                            showActionSheet = showPosterMenu,
                            onDismissActionSheet = { showPosterMenu = false }
                        )
                    }
                }
            }
        }

        // Text Info (Outside Card Stack)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 1.dp else 2.dp)
        ) {
            Text(
                text = showStack.showTitle,
                color = Color.White,
                fontSize = if (isCompact) 11.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isCompact) 9.sp else 12.sp,
                fontWeight = FontWeight.Medium
            )

            val qualityBadge = remember(showStack) {
                val firstEp = showStack.episodes.firstOrNull()
                val fromFile = com.example.util.EpisodeMetadataParser.getLocalVideoQuality(firstEp?.filePath)
                if (fromFile.isNotBlank()) fromFile
                else if (firstEp?.tmdbBadge?.isNotBlank() == true) firstEp.tmdbBadge
                else com.example.network.GeminiService.detectQualityBadge(showStack.showTitle + " " + (firstEp?.filePath ?: "") + " " + (firstEp?.title ?: ""))
            }
            if (qualityBadge.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .border(0.75.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = qualityBadge,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = if (isCompact) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==================== HELPER FLOATING BOTTOM BAR ====================

@Composable
fun FloatingBottomBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    backdropGraphicsLayer: GraphicsLayer? = null,
    backdropPositionInRoot: () -> Offset = { Offset.Zero },
    backdropDrawTick: () -> Long = { 0L },
    backdropScrollToken: () -> Long = { 0L }
) {
    val accentColor = if (primaryColor != Color.Unspecified && primaryColor != Color.Transparent) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Four existing navigation items: Home, Library, Settings, Search
    val items = remember {
        listOf(
            Triple("Home", Icons.Outlined.PlayArrow to Icons.Filled.PlayArrow, "Home"),
            Triple("Library", Icons.Outlined.Folder to Icons.Filled.Folder, "Library"),
            Triple("Settings", Icons.Outlined.Settings to Icons.Filled.Settings, "Settings"),
            Triple("Search", Icons.Outlined.Search to Icons.Filled.Search, "Search")
        )
    }

    val selectedIndex = remember(currentTab) {
        val idx = items.indexOfFirst {
            it.third == currentTab || (it.third == "Library" && (currentTab == "Files" || currentTab == "Streams"))
        }
        if (idx >= 0) idx else 0
    }

    // Single source of truth for press-and-hold + continuous horizontal drag Liquid Glass interaction
    var isHoldBubbleActive by remember { mutableStateOf(false) }
    var heldSlotIndex by remember { mutableStateOf<Int?>(null) }
    var lastHeldSlotIndex by remember { mutableStateOf<Int?>(null) }
    var pressedAnchorSlotIndex by remember { mutableStateOf<Int?>(null) }
    var dragPointerX by remember { mutableStateOf<Float?>(null) }
    var dragPointerBlend by remember { mutableFloatStateOf(0f) }
    var releasePointerX by remember { mutableStateOf<Float?>(null) }
    var pendingSelectedSlot by remember { mutableStateOf<Int?>(null) }

    val lensProgress by animateFloatAsState(
        targetValue = if (isHoldBubbleActive) 1f else 0f,
        animationSpec = if (isHoldBubbleActive) {
            spring(
                dampingRatio = 0.78f,
                stiffness = 700f
            )
        } else {
            spring(
                dampingRatio = 0.88f,
                stiffness = 500f
            )
        },
        label = "liquid_glass_lens_progress"
    )

    // Smooth, non-bouncy shared spring animation for the resting selection sheen (idle state only)
    val animatedSlot = remember { Animatable(selectedIndex.toFloat()) }

    val visualSelectedIndex = when {
        isHoldBubbleActive && heldSlotIndex != null -> heldSlotIndex!!
        pendingSelectedSlot != null -> pendingSelectedSlot!!
        else -> selectedIndex
    }

    LaunchedEffect(visualSelectedIndex, isHoldBubbleActive) {
        if (isHoldBubbleActive) {
            animatedSlot.snapTo(visualSelectedIndex.toFloat())
        } else {
            animatedSlot.animateTo(
                targetValue = visualSelectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 460f
                )
            )
        }
    }

    LaunchedEffect(pendingSelectedSlot, selectedIndex) {
        val pending = pendingSelectedSlot ?: return@LaunchedEffect
        if (selectedIndex == pending) {
            pendingSelectedSlot = null
        } else {
            delay(180L)
            if (pendingSelectedSlot == pending) {
                pendingSelectedSlot = null
            }
        }
    }

    val dockHeight = 62.dp
    val dockCornerRadius = 31.dp
    val dockShape = RoundedCornerShape(dockCornerRadius)
    val horizontalPadding = 5.dp
    val verticalPadding = 5.dp

    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    var dockPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var dockSizePx by remember { mutableStateOf(IntSize.Zero) }
    val supportsRealTimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdropGraphicsLayer != null

    val currentTabState by rememberUpdatedState(currentTab)
    val selectedIndexState by rememberUpdatedState(selectedIndex)
    val onTabSelectedState by rememberUpdatedState(onTabSelected)
    val dockSizeState by rememberUpdatedState(dockSizePx)

    val horizontalPaddingPx = remember(density) { with(density) { horizontalPadding.toPx() } }
    val cancelVerticalDistancePx = remember(density) { with(density) { 96.dp.toPx() } }
    val dragActivationSlopPx = remember(density) { with(density) { 13.dp.toPx() } }
    val dragRampDistancePx = remember(density) { with(density) { 14.dp.toPx() } }
    val entranceSlideDistancePx = remember(density) { with(density) { 36.dp.toPx() } }

    // Dock visibility (opacity only, zero vertical slide so it fades in cleanly with the app)
    val dockEntranceAlpha = remember { Animatable(if (isVisible) 1f else 0f) }
    val dockEntranceSlide = remember { Animatable(0f) }
    val isDockInteractive by remember {
        derivedStateOf { dockEntranceAlpha.value >= 0.05f }
    }
    val isDockInteractiveState by rememberUpdatedState(isDockInteractive)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            dockEntranceAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 340,
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            dockEntranceAlpha.snapTo(0f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(0.88f)
            .widthIn(min = 296.dp, max = 348.dp)
            .height(dockHeight)
            .onGloballyPositioned { coords ->
                dockPositionInRoot = coords.positionInRoot()
            }
            .onSizeChanged { size ->
                dockSizePx = size
            }
            .graphicsLayer {
                alpha = dockEntranceAlpha.value.coerceIn(0f, 1f)
                translationY = entranceSlideDistancePx * dockEntranceSlide.value
            }
            .pointerInput(items.size, density) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!isDockInteractiveState) return@awaitEachGesture
                        val dockW = dockSizeState.width.toFloat().takeIf { it > 1f } ?: size.width.toFloat()
                        if (dockW <= 1f) return@awaitEachGesture

                        val availW = (dockW - horizontalPaddingPx * 2f).coerceAtLeast(1f)
                        val slotW = availW / items.size

                        fun slotForX(x: Float): Int {
                            return ((x - horizontalPaddingPx) / slotW)
                                .toInt()
                                .coerceIn(0, items.size - 1)
                        }

                        val downX = down.position.x.coerceIn(0f, dockW)
                        val downY = down.position.y
                        val pressedSlot = slotForX(downX)
                        val pressedSlotCenterPx = horizontalPaddingPx + slotW * (pressedSlot + 0.5f)
                        down.consume()

                        var bubbleActivated = false
                        var gestureCancelled = false
                        var latestClampedX = downX
                        var activeSlot = pressedSlot
                        var draggedInGesture = false
                        var maxDragDist = 0f
                        var currentBlend = 0f
                        var navigationDispatched = false
                        pendingSelectedSlot = null
                        releasePointerX = null

                        fun activateHoldBubble() {
                            if (!bubbleActivated) {
                                bubbleActivated = true
                                pressedAnchorSlotIndex = pressedSlot
                                heldSlotIndex = activeSlot
                                lastHeldSlotIndex = activeSlot
                                dragPointerX = latestClampedX
                                dragPointerBlend = currentBlend
                                isHoldBubbleActive = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        val holdJob = launch {
                            delay(80L)
                            if (!gestureCancelled) {
                                activateHoldBubble()
                            }
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null) {
                                    gestureCancelled = true
                                    break
                                }

                                if (!change.pressed) {
                                    if (!change.changedToUp() || event.type != PointerEventType.Release) {
                                        gestureCancelled = true
                                    }
                                    change.consume()
                                    break
                                }

                                val pos = change.position
                                val dy = abs(pos.y - downY)

                                if (dy > cancelVerticalDistancePx) {
                                    holdJob.cancel()
                                    gestureCancelled = true
                                    isHoldBubbleActive = false
                                    dragPointerBlend = 0f
                                }

                                if (!gestureCancelled) {
                                    val clampedX = pos.x.coerceIn(0f, dockW)
                                    latestClampedX = clampedX
                                    val dx = abs(clampedX - downX)
                                    maxDragDist = maxOf(maxDragDist, dx)
                                    val newSlot = slotForX(clampedX)

                                    if (!draggedInGesture && (dx > dragActivationSlopPx || newSlot != pressedSlot)) {
                                        draggedInGesture = true
                                    }

                                    if (draggedInGesture) {
                                        val linearRamp = ((maxDragDist - dragActivationSlopPx) / dragRampDistancePx).coerceIn(0f, 1f)
                                        currentBlend = if (newSlot != pressedSlot) {
                                            maxOf(linearRamp, 0.45f)
                                        } else {
                                            linearRamp
                                        }
                                    }

                                    if (!bubbleActivated && draggedInGesture) {
                                        holdJob.cancel()
                                        activateHoldBubble()
                                    }

                                    if (bubbleActivated) {
                                        dragPointerX = clampedX
                                        dragPointerBlend = currentBlend
                                        if (newSlot != activeSlot) {
                                            activeSlot = newSlot
                                            heldSlotIndex = newSlot
                                            lastHeldSlotIndex = newSlot
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }

                                change.consume()
                            }
                        } finally {
                            holdJob.cancel()
                            val capturedReleaseCenterX = if (!gestureCancelled && bubbleActivated && draggedInGesture) {
                                pressedSlotCenterPx + (latestClampedX - pressedSlotCenterPx) * currentBlend
                            } else {
                                null
                            }
                            releasePointerX = capturedReleaseCenterX
                            isHoldBubbleActive = false
                            heldSlotIndex = null
                            pressedAnchorSlotIndex = null
                            dragPointerX = null
                            dragPointerBlend = 0f
                        }

                        // Preserve normal tap navigation when released prior to hold activation;
                        // when dragged across the dock during hold, navigate once to the final slot on release.
                        if (!gestureCancelled && !navigationDispatched) {
                            if (!bubbleActivated) {
                                navigationDispatched = true
                                val targetRoute = items[pressedSlot].third
                                if (currentTabState != targetRoute) {
                                    onTabSelectedState(targetRoute)
                                }
                            } else if (draggedInGesture) {
                                navigationDispatched = true
                                val targetRoute = items[activeSlot].third
                                if (currentTabState != targetRoute) {
                                    pendingSelectedSlot = activeSlot
                                    onTabSelectedState(targetRoute)
                                }
                            }
                        }
                    }
                }
            }
            .testTag("floating_bottom_bar"),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val availableWidth = (totalWidth - (horizontalPadding * 2)).coerceAtLeast(240.dp)
        val slotWidth = availableWidth / items.size

        val capsuleHeight = dockHeight - (verticalPadding * 2) // 52.dp
        val capsuleCornerRadius = capsuleHeight / 2f
        val currentCapsuleWidth = (slotWidth - 2.dp).coerceAtLeast(64.dp).coerceAtMost(availableWidth)

        val currentSlot = animatedSlot.value.coerceIn(0f, (items.size - 1).toFloat())
        val slotCenterX = horizontalPadding + (slotWidth * (currentSlot + 0.5f))
        val minLeft = horizontalPadding

        // 1 & 3. Resting Liquid Glass Dock Shell (0..62.dp)
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = dockShape,
                    ambientColor = Color.Black.copy(alpha = 0.55f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(dockShape)
                .then(
                    if (!supportsRealTimeBlur) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xB8181A22),
                                    Color(0xD40B0C10)
                                )
                            )
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Live Real-Time Blurred & Optically Refracted Backdrop (API 31+ RenderEffect blur, API 33+ AGSL rim refraction)
            if (supportsRealTimeBlur) {
                val dockBlurRadiusPx = remember(density) { with(density) { 5.0.dp.toPx() } }
                val dockCornerRadiusPx = remember(density) { with(density) { dockCornerRadius.toPx() } }
                val dockWidthPx = dockSizePx.width.toFloat().takeIf { it > 1f } ?: with(density) { totalWidth.toPx() }
                val dockHeightPx = dockSizePx.height.toFloat().takeIf { it > 1f } ?: with(density) { dockHeight.toPx() }

                val liveBackdropRenderEffect = remember(
                    dockBlurRadiusPx,
                    dockWidthPx,
                    dockHeightPx,
                    dockCornerRadiusPx
                ) {
                    LiquidGlassDockEffects.createDockBackdropEffect(
                        blurRadiusPx = dockBlurRadiusPx,
                        widthPx = dockWidthPx,
                        heightPx = dockHeightPx,
                        cornerRadiusPx = dockCornerRadiusPx
                    )
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = liveBackdropRenderEffect
                            clip = true
                        }
                        .drawBehind {
                            backdropScrollToken()
                            backdropDrawTick()
                            val rootBackdropPos = backdropPositionInRoot()
                            val entranceOffsetYPx = entranceSlideDistancePx * dockEntranceSlide.value
                            val offsetX = dockPositionInRoot.x - rootBackdropPos.x
                            val offsetY = (dockPositionInRoot.y + entranceOffsetYPx) - rootBackdropPos.y
                            drawRect(Color.Black)
                            translate(left = -offsetX, top = -offsetY) {
                                drawLayer(backdropGraphicsLayer)
                            }
                        }
                )

                // Clean transparent dark glass glaze (preserves balanced frosted background details)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color(0x0E161A24),
                                0.50f to Color(0x160C0E14),
                                1.0f to Color(0x1E080A0E)
                            )
                        )
                )
            }

            // Softly integrated top & bottom glass surface sheen (pure white at low opacity, zero colored glow)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(6.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.09f),
                            0.45f to Color.White.copy(alpha = 0.025f),
                            1.0f to Color.Transparent
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(5.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.55f to Color.White.copy(alpha = 0.02f),
                            1.0f to Color.White.copy(alpha = 0.07f)
                        )
                    )
            )

            // Realistic, Thin Polished Glass Light Reflections strictly along TOP and BOTTOM horizontal edges
            // (naturally varying brightness, whisper-subtle pearlescent refraction, zero left/right vertical edge reflection)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        if (w <= 4f || h <= 4f) return@drawBehind
                        val r = minOf(dockCornerRadius.toPx(), w * 0.5f, h * 0.5f)

                        fun drawTopBottomGlassReflections(
                            insetPx: Float,
                            strokeWidthPx: Float,
                            topBrush: Brush,
                            bottomBrush: Brush
                        ) {
                            val arcR = (r - insetPx).coerceAtLeast(1f)
                            val arcSpan = arcR * 2f
                            val topLeftRect = Rect(insetPx, insetPx, insetPx + arcSpan, insetPx + arcSpan)
                            val topRightRect = Rect(w - insetPx - arcSpan, insetPx, w - insetPx, insetPx + arcSpan)
                            val bottomLeftRect = Rect(insetPx, h - insetPx - arcSpan, insetPx + arcSpan, h - insetPx)
                            val bottomRightRect = Rect(w - insetPx - arcSpan, h - insetPx - arcSpan, w - insetPx, h - insetPx)

                            // Strictly limited to the top horizontal edge and upper shoulder curve (no left/right vertical edges)
                            val topPath = Path().apply {
                                arcTo(topLeftRect, startAngleDegrees = 222f, sweepAngleDegrees = 48f, forceMoveTo = true)
                                lineTo((w - insetPx - arcR).coerceAtLeast(insetPx + arcR), insetPx)
                                arcTo(topRightRect, startAngleDegrees = 270f, sweepAngleDegrees = 48f, forceMoveTo = false)
                            }
                            drawPath(
                                path = topPath,
                                brush = topBrush,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )

                            // Strictly limited to the bottom horizontal edge and lower shoulder curve (no left/right vertical edges)
                            val bottomPath = Path().apply {
                                arcTo(bottomLeftRect, startAngleDegrees = 138f, sweepAngleDegrees = -48f, forceMoveTo = true)
                                lineTo((w - insetPx - arcR).coerceAtLeast(insetPx + arcR), h - insetPx)
                                arcTo(bottomRightRect, startAngleDegrees = 90f, sweepAngleDegrees = -48f, forceMoveTo = false)
                            }
                            drawPath(
                                path = bottomPath,
                                brush = bottomBrush,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }

                        // 1. Soft inner bevel sheen integrated into the curved glass surface (~1.85dp)
                        drawTopBottomGlassReflections(
                            insetPx = 1.15.dp.toPx(),
                            strokeWidthPx = 1.85.dp.toPx(),
                            topBrush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.05f to Color.Transparent,
                                0.18f to Color(0x1CF2F8FF),
                                0.36f to Color(0x28FFFFFF),
                                0.56f to Color(0x14F6F9FF),
                                0.78f to Color(0x22FFFDF9),
                                0.95f to Color.Transparent,
                                1.0f to Color.Transparent
                            ),
                            bottomBrush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.05f to Color.Transparent,
                                0.20f to Color(0x18FFFDF9),
                                0.42f to Color(0x12F5F8FF),
                                0.66f to Color(0x24FFFFFF),
                                0.84f to Color(0x1AF2F7FF),
                                0.95f to Color.Transparent,
                                1.0f to Color.Transparent
                            )
                        )

                        // 2. Thin, crisp specular glass rim highlight with natural brightness variation (~0.95dp)
                        drawTopBottomGlassReflections(
                            insetPx = 0.50.dp.toPx(),
                            strokeWidthPx = 0.95.dp.toPx(),
                            topBrush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.03f to Color.Transparent,
                                0.14f to Color(0x78F2F8FF),
                                0.32f to Color(0xCCFFFFFF),
                                0.50f to Color(0x96F7FAFF),
                                0.68f to Color(0x5EFAF8FF),
                                0.84f to Color(0x9EFFFDF9),
                                0.97f to Color.Transparent,
                                1.0f to Color.Transparent
                            ),
                            bottomBrush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.03f to Color.Transparent,
                                0.16f to Color(0x68FFFDF9),
                                0.34f to Color(0x52F6F8FF),
                                0.54f to Color(0x88FAFCFF),
                                0.72f to Color(0xB8FFFFFF),
                                0.86f to Color(0x74F2F7FF),
                                0.97f to Color.Transparent,
                                1.0f to Color.Transparent
                            )
                        )
                    }
            )
        }

        // Precompute Hold State Wide Liquid Glass Capsule geometry so its optical backdrop sits behind
        // the original small dock icons while its specular glass sheen & thin border wrap on top.
        val activeBubbleSlot = (heldSlotIndex ?: lastHeldSlotIndex)?.coerceIn(0, items.size - 1)
        val isCapsuleRendered = lensProgress > 0.01f && activeBubbleSlot != null
        val dockWidthPx = dockSizePx.width.toFloat().takeIf { it > 1f } ?: with(density) { totalWidth.toPx() }
        val holdCapsuleWidth = (slotWidth + 42.dp).coerceIn(122.dp, 142.dp)
        val holdCapsuleHeight = 82.dp
        val holdCapsuleShape = RoundedCornerShape(holdCapsuleHeight / 2f)
        val holdCapsuleWidthPx = with(density) { holdCapsuleWidth.toPx() }
        val holdCapsuleHeightPx = with(density) { holdCapsuleHeight.toPx() }
        val holdCapsuleCornerRadiusPx = holdCapsuleHeightPx / 2f

        val minCapsuleCenterX = (holdCapsuleWidthPx * 0.46f).coerceAtMost(dockWidthPx * 0.5f)
        val maxCapsuleCenterX = (dockWidthPx - holdCapsuleWidthPx * 0.46f).coerceAtLeast(dockWidthPx * 0.5f)
        val activeSlotCenterPx = activeBubbleSlot?.let { slotIdx ->
            with(density) { (horizontalPadding + slotWidth * (slotIdx + 0.5f)).toPx() }
        } ?: 0f
        val anchorSlotCenterPx = (pressedAnchorSlotIndex ?: activeBubbleSlot)?.let { slotIdx ->
            with(density) { (horizontalPadding + slotWidth * (slotIdx + 0.5f)).toPx() }
        } ?: activeSlotCenterPx
        val rawTargetCenterX = when {
            isHoldBubbleActive && dragPointerX != null -> {
                anchorSlotCenterPx + (dragPointerX!! - anchorSlotCenterPx) * dragPointerBlend
            }
            !isHoldBubbleActive && releasePointerX != null -> {
                activeSlotCenterPx + (releasePointerX!! - activeSlotCenterPx) * lensProgress
            }
            else -> activeSlotCenterPx
        }
        val effectiveCapsuleCenterX = rawTargetCenterX.coerceIn(minCapsuleCenterX, maxCapsuleCenterX)

        val snappedCapsuleLeftPx: Float
        val snappedCapsuleTopPx: Float
        if (activeBubbleSlot != null) {
            val settledCapsuleTopPx = with(density) { (-14).dp.toPx() }
            snappedCapsuleLeftPx = (effectiveCapsuleCenterX - holdCapsuleWidthPx / 2f).roundToInt().toFloat()
            snappedCapsuleTopPx = settledCapsuleTopPx.roundToInt().toFloat()
        } else {
            snappedCapsuleLeftPx = 0f
            snappedCapsuleTopPx = 0f
        }

        val holdCapsuleAlpha = (lensProgress / 0.18f).coerceIn(0f, 1f)
        val holdCapsuleScaleX = 0.86f + 0.14f * lensProgress
        val holdCapsuleScaleY = 0.78f + 0.22f * lensProgress

        // 2a. State 2: Wide Raised Liquid Glass Capsule Optical Backdrop (genuine blur, magnification & meniscus distortion)
        if (isCapsuleRendered) {
            val lensBlurRadiusPx = remember(density) { with(density) { 0.85.dp.toPx() } }
            val lensBackdropBlurEffect = remember(lensBlurRadiusPx) {
                LiquidGlassDockEffects.createLensBackdropBlurEffect(lensBlurRadiusPx)
            }
            val lensRuntimeShader = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LiquidGlassDockEffects.createLensRuntimeShaderOrNull()
                } else {
                    null
                }
            }
            val relativeLensPointerX = if (isHoldBubbleActive && dragPointerBlend > 0.01f && dragPointerX != null) {
                (dragPointerX!! - snappedCapsuleLeftPx).coerceIn(
                    holdCapsuleWidthPx * 0.25f,
                    holdCapsuleWidthPx * 0.75f
                )
            } else {
                holdCapsuleWidthPx * 0.5f
            }
            val lensBackgroundMagnifierEffect = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                lensRuntimeShader != null
            ) {
                remember(
                    lensRuntimeShader,
                    holdCapsuleWidthPx,
                    holdCapsuleHeightPx,
                    holdCapsuleCornerRadiusPx,
                    relativeLensPointerX,
                    lensProgress,
                    lensBlurRadiusPx
                ) {
                    LiquidGlassDockEffects.createLensBackgroundMagnifierEffect(
                        shader = lensRuntimeShader,
                        widthPx = holdCapsuleWidthPx,
                        heightPx = holdCapsuleHeightPx,
                        cornerRadiusPx = holdCapsuleCornerRadiusPx,
                        pointerCoordX = relativeLensPointerX,
                        pointerCoordY = holdCapsuleHeightPx * 0.48f,
                        magnification = 1.0f + 0.52f * lensProgress,
                        lensProgress = lensProgress,
                        blurRadiusPx = lensBlurRadiusPx
                    )
                }
            } else {
                null
            }
            val useFallbackBgScaleZoom = lensBackgroundMagnifierEffect == null
            val fallbackBgZoomScale = if (useFallbackBgScaleZoom) 1.0f + 0.44f * lensProgress else 1f

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(snappedCapsuleLeftPx.roundToInt(), snappedCapsuleTopPx.roundToInt()) }
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .size(width = holdCapsuleWidth, height = holdCapsuleHeight)
                    .graphicsLayer {
                        alpha = holdCapsuleAlpha
                        scaleX = holdCapsuleScaleX
                        scaleY = holdCapsuleScaleY
                        transformOrigin = TransformOrigin(0.5f, 0.56f)
                        shadowElevation = with(density) { (18.dp * lensProgress).toPx() }
                        shape = holdCapsuleShape
                        clip = true
                    }
                    .testTag("liquid_glass_lens")
            ) {
                if (backdropGraphicsLayer != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                if (lensBackgroundMagnifierEffect != null) {
                                    renderEffect = lensBackgroundMagnifierEffect
                                } else if (lensBackdropBlurEffect != null) {
                                    renderEffect = lensBackdropBlurEffect
                                }
                                if (useFallbackBgScaleZoom) {
                                    scaleX = fallbackBgZoomScale
                                    scaleY = fallbackBgZoomScale
                                }
                                clip = true
                            }
                            .drawBehind {
                                backdropScrollToken()
                                backdropDrawTick()
                                val rootBackdropPos = backdropPositionInRoot()
                                val entranceOffsetYPx = entranceSlideDistancePx * dockEntranceSlide.value
                                val offsetX = (dockPositionInRoot.x + snappedCapsuleLeftPx) - rootBackdropPos.x
                                val offsetY = (dockPositionInRoot.y + entranceOffsetYPx + snappedCapsuleTopPx) - rootBackdropPos.y
                                drawRect(Color.Black)
                                translate(left = -offsetX, top = -offsetY) {
                                    drawLayer(backdropGraphicsLayer)
                                }
                            }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0xD410141C))
                    )
                }

                // Subtle translucent dark glass depth inside the enlarged capsule
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color(0x161B212E),
                                0.55f to Color(0x120E121A),
                                1.0f to Color(0x1E080A0F)
                            )
                        )
                )
            }
        }

        // 2b. Single Flat Rounded Selected-Tab Highlight & Navigation Items (Normal resting state)
        val selectedCapsuleWidth = (slotWidth + 4.dp).coerceAtLeast(68.dp).coerceAtMost(availableWidth)
        val maxCapsuleLeft = (totalWidth - horizontalPadding - selectedCapsuleWidth).coerceAtLeast(minLeft)
        val targetCapsuleCenterDp = if (isHoldBubbleActive && dragPointerX != null && dragPointerBlend > 0.01f) {
            val anchorIdx = (pressedAnchorSlotIndex ?: visualSelectedIndex).coerceIn(0, items.size - 1)
            val anchorCenterPx = with(density) { (horizontalPadding + slotWidth * (anchorIdx + 0.5f)).toPx() }
            val blendedCenterPx = anchorCenterPx + (dragPointerX!! - anchorCenterPx) * dragPointerBlend
            with(density) { blendedCenterPx.toDp() }
        } else {
            slotCenterX
        }
        val selectedCapsuleLeft = (targetCapsuleCenterDp - (selectedCapsuleWidth / 2f))
            .coerceIn(minLeft, maxCapsuleLeft)
        val selectedCapsuleShape = RoundedCornerShape(capsuleCornerRadius)

        Box(
            modifier = Modifier
                .matchParentSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Normal state selected tab highlight (hidden when hold lens capsule is rendered)
            val normalHighlightAlpha = (1f - lensProgress * 2.5f).coerceIn(0f, 1f)
            if (normalHighlightAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = selectedCapsuleLeft, y = 0.dp)
                        .width(selectedCapsuleWidth)
                        .height(capsuleHeight)
                        .clip(selectedCapsuleShape)
                        .background(Color.White.copy(alpha = 0.14f * normalHighlightAlpha))
                        .testTag("selected_tab_capsule")
                )
            }

            // Four Equal Navigation Items
            val slotWidthPx = with(density) { slotWidth.toPx() }.coerceAtLeast(1f)
            CompositionLocalProvider(LocalContentColor provides Color(0xFFFFFFFF)) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, (title, icons, route) ->
                        val isSelected = index == visualSelectedIndex
                        val slotCenterPxForItem = with(density) { (horizontalPadding + slotWidth * (index + 0.5f)).toPx() }
                        val lensProximity = if (isCapsuleRendered) {
                            (1f - abs(effectiveCapsuleCenterX - slotCenterPxForItem) / (slotWidthPx * 0.92f)).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        val effectiveTint by animateColorAsState(
                            targetValue = if (isSelected) accentColor else Color.White.copy(alpha = 0.84f),
                            animationSpec = tween(durationMillis = 150),
                            label = "tab_idle_tint_$title"
                        )
                        val effectiveScale = if (isCapsuleRendered) {
                            1.0f + 0.22f * lensProgress * lensProximity
                        } else {
                            1.0f
                        }
                        val effectiveLiftYPx = if (isCapsuleRendered) {
                            with(density) { (-3.0f * lensProgress * lensProximity).dp.toPx() }
                        } else {
                            0f
                        }

                        val labelShadow = remember {
                            Shadow(
                                color = Color.Black.copy(alpha = 0.72f),
                                offset = Offset(0f, 1.5f),
                                blurRadius = 7f
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    selected = isSelected
                                    onClick(label = title) {
                                        if (currentTabState != route) {
                                            onTabSelectedState(route)
                                        }
                                        true
                                    }
                                }
                                .testTag("nav_tab_$title"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = effectiveScale
                                        scaleY = effectiveScale
                                        translationY = effectiveLiftYPx
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) icons.second else icons.first,
                                    contentDescription = title,
                                    tint = effectiveTint,
                                    modifier = Modifier.size(21.dp)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = title,
                                    color = effectiveTint,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.15.sp,
                                        color = effectiveTint,
                                        shadow = labelShadow
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2c. State 2: Top Specular Glass Reflections & Thin Elegant Rim Border of the Wide Capsule
        if (isCapsuleRendered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(snappedCapsuleLeftPx.roundToInt(), snappedCapsuleTopPx.roundToInt()) }
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .size(width = holdCapsuleWidth, height = holdCapsuleHeight)
                    .graphicsLayer {
                        alpha = holdCapsuleAlpha
                        scaleX = holdCapsuleScaleX
                        scaleY = holdCapsuleScaleY
                        transformOrigin = TransformOrigin(0.5f, 0.56f)
                        shape = holdCapsuleShape
                        clip = true
                    }
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = 0.16f * lensProgress),
                            0.18f to Color.Transparent,
                            0.84f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.08f * lensProgress)
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f * lensProgress),
                                Color.White.copy(alpha = 0.15f * lensProgress),
                                Color.White.copy(alpha = 0.35f * lensProgress)
                            )
                        ),
                        shape = holdCapsuleShape
                    )
                    .testTag("liquid_glass_capsule_rim")
            )
        }
    }
}

private fun determineCategory(name: String, durationMs: Long, filePath: String? = null): String {
    val tvRegex = Regex("""(?i)(s\d{1,2}\s*e\d{1,2}|\d{1,2}x\d{1,2}|season\s*\d+|episode\s*\d+|s\d{1,2}\.?e\d{1,2}|s\d{1,2}\b|ep\d{1,2}\b)""")
    if (tvRegex.containsMatchIn(name)) {
        return "Shows"
    }
    if (com.example.util.HomeVideoFilter.isHomeVideo(filePath, name) || com.example.network.GeminiService.isHomeVideoFilename(name)) {
        return "Home Videos"
    }
    val lowerName = name.lowercase()
    if (lowerName.contains("movie") || lowerName.contains("film") || lowerName.contains("cinema")) {
        return "Movies"
    }
    if (lowerName.contains("episode") || lowerName.contains("season") || lowerName.contains("s0") || lowerName.contains("s1") || lowerName.contains("ep0") || lowerName.contains("ep1")) {
        return "Shows"
    }
    
    // Fallback/Legacy logic
    if (durationMs >= 20 * 60 * 1000) {
        return "Movies"
    }
    return "Home Videos"
}

private fun parseShowName(filename: String): String {
    return com.example.network.GeminiService.cleanShowTitle(filename)
}

private fun parseShowTitleFromMovie(movie: com.example.model.Movie): String {
    val titleClean = parseShowName(movie.title)
    if (titleClean.isNotBlank() && titleClean.toIntOrNull() == null) {
        return titleClean
    }
    val idUri = movie.id
    val filenameParam = if (idUri.contains("filename=")) {
        idUri.substringAfter("filename=").substringBefore("&")
    } else {
        ""
    }
    val cleanedParam = if (filenameParam.isNotEmpty()) parseShowName(filenameParam) else ""
    return if (cleanedParam.isNotBlank() && cleanedParam.toIntOrNull() == null) {
        cleanedParam
    } else {
        if (movie.title.isNotBlank()) movie.title else "TV Show"
    }
}

private fun scanGalleryVideos(context: android.content.Context): List<com.example.model.Movie> {
    val movies = mutableListOf<com.example.model.Movie>()
    val contentResolver = context.contentResolver
    val sharedPrefs = context.getSharedPreferences("infuse_prefs", android.content.Context.MODE_PRIVATE)
    val uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        android.provider.MediaStore.Video.Media._ID,
        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.DURATION
    )
    
    try {
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val durationMs = cursor.getLong(durationColumn)
                
                val videoUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                
                val totalSecs = durationMs / 1000
                val hours = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                val durationStr = when {
                    hours > 0 -> if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                    mins > 0 -> "${mins}m"
                    else -> "${secs}s"
                }

                val cleanTitle = com.example.network.GeminiService.sanitizeFileName(name).title
                val category = determineCategory(name, durationMs, videoUri)
                val isTvShow = category.equals("Shows", ignoreCase = true) ||
                        name.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+""")) ||
                        cleanTitle.contains(Regex("""(?i)s\d+e\d+|\d+x\d+|season\s*\d+|episode\s*\d+"""))
                val showTitleClean = if (isTvShow) com.example.network.GeminiService.cleanShowTitle(cleanTitle) else ""

                // Cache-First Instant Loading from persistent storage
                val cachedPoster = sharedPrefs.getString("tmdb_poster_$videoUri", null)
                    ?: (if (isTvShow && showTitleClean.isNotBlank()) sharedPrefs.getString("tmdb_poster_$showTitleClean", null) else null)
                    ?: sharedPrefs.getString("tmdb_poster_$cleanTitle", null)
                    ?: sharedPrefs.getString("tmdb_poster_$name", null)
                    ?: sharedPrefs.getString("tmdb_poster_$id", null)

                val cachedCastJson = sharedPrefs.getString("tmdb_cast_$videoUri", null)
                    ?: (if (isTvShow && showTitleClean.isNotBlank()) sharedPrefs.getString("tmdb_cast_$showTitleClean", null) else null)
                    ?: sharedPrefs.getString("tmdb_cast_$cleanTitle", null)
                    ?: sharedPrefs.getString("tmdb_cast_$name", null)
                    ?: sharedPrefs.getString("tmdb_cast_$id", null)

                val cachedOverview = sharedPrefs.getString("tmdb_overview_$videoUri", null)
                    ?: (if (isTvShow && showTitleClean.isNotBlank()) sharedPrefs.getString("tmdb_overview_$showTitleClean", null) else null)
                    ?: sharedPrefs.getString("tmdb_overview_$cleanTitle", null)
                    ?: sharedPrefs.getString("tmdb_overview_$name", null)
                    ?: sharedPrefs.getString("tmdb_overview_$id", null)

                val cachedCastList = if (!cachedCastJson.isNullOrBlank()) {
                    try {
                        val listAdapter = com.example.network.GeminiService.moshi.adapter(List::class.java)
                        val jsonList = listAdapter.fromJson(cachedCastJson) as? List<*>
                        jsonList?.mapNotNull { item ->
                            val map = item as? Map<*, *> ?: return@mapNotNull null
                            com.example.model.CastMember(
                                name = map["name"] as? String ?: "",
                                character = map["character"] as? String ?: "Actor",
                                imageUrl = map["imageUrl"] as? String ?: ""
                            )
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()

                val poster = cachedPoster ?: ""
                val castList = cachedCastList
                val synopsis = cachedOverview?.ifBlank { "Local media" } ?: "Local media"

                val cachedRating = sharedPrefs.getFloat("tmdb_rating_$videoUri", 0.0f)
                    .takeIf { it > 0f }
                    ?: (if (isTvShow && showTitleClean.isNotBlank()) sharedPrefs.getFloat("tmdb_rating_$showTitleClean", 0.0f).takeIf { it > 0f } else null)
                    ?: sharedPrefs.getFloat("tmdb_rating_$cleanTitle", 0.0f).takeIf { it > 0f }
                    ?: sharedPrefs.getFloat("tmdb_rating_$name", 0.0f).takeIf { it > 0f }
                    ?: sharedPrefs.getFloat("tmdb_rating_$id", 0.0f).takeIf { it > 0f }
                    ?: 0.0f
                val cachedTomato = sharedPrefs.getString("tmdb_tomato_$videoUri", null)
                    ?: (if (isTvShow && showTitleClean.isNotBlank()) sharedPrefs.getString("tmdb_tomato_$showTitleClean", null) else null)
                    ?: sharedPrefs.getString("tmdb_tomato_$cleanTitle", null)
                    ?: sharedPrefs.getString("tmdb_tomato_$name", null)
                    ?: sharedPrefs.getString("tmdb_tomato_$id", null)
                    ?: ""

                movies.add(
                    com.example.model.Movie(
                        id = videoUri,
                        title = cleanTitle,
                        posterUrl = poster,
                        backdropUrl = poster,
                        rating = cachedRating,
                        category = category,
                        genre = emptyList(),
                        duration = durationStr,
                        synopsis = synopsis,
                        cast = castList,
                        director = "Self",
                        directorImageUrl = "",
                        releaseYear = com.example.network.GeminiService.sanitizeFileName(name).year ?: "",
                        views = "Local",
                        tomatoScore = cachedTomato
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return movies
}

@Composable
fun StreamsTabContent(
    viewModel: MainViewModel,
    primaryColor: Color,
    currentTheme: AppTheme,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val context = LocalContext.current
    val customStreams by viewModel.customStreams.collectAsState()
    val allStreams = remember(customStreams) { com.example.model.defaultStreams + customStreams }

    var selectedStreamCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Live HLS", "Movies", "Animation", "News & Space", "Short Film", "Custom")

    val filteredStreams = remember(allStreams, selectedStreamCategory, searchQuery) {
        allStreams.filter { stream ->
            val matchesCategory = when (selectedStreamCategory) {
                "All" -> true
                "Custom" -> stream.isCustom
                else -> stream.category.equals(selectedStreamCategory, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    stream.title.contains(searchQuery, ignoreCase = true) ||
                    stream.category.contains(searchQuery, ignoreCase = true) ||
                    stream.url.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Title & "+ Add Stream" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Text(
                        text = "Online Stream",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = currentTheme.onPrimaryColor
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("add_stream_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add stream",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "+ Add Stream",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search streams or URLs...", color = Color.White.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = primaryColor
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E2121),
                    unfocusedContainerColor = Color(0xFF1E2121),
                    focusedBorderColor = primaryColor.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_search_input")
            )

            // Category Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories, key = { it }) { cat ->
                    val isSelected = cat == selectedStreamCategory
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedStreamCategory = cat }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) currentTheme.onPrimaryColor else Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            // Streams Grid
            if (filteredStreams.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (allStreams.isEmpty()) "No custom streams added yet. Tap '+ Add Stream' above." else "No streams found in this category",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStreams, key = { it.id }) { stream ->
                        val savedPos = viewModel.getSavedPlaybackPosition(stream.url)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C1C)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                // Thumbnail Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                ) {
                                    AsyncImage(
                                        model = stream.thumbnailUrl,
                                        imageLoader = coil.Coil.imageLoader(LocalContext.current),
                                        contentDescription = stream.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Dark gradient
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = 0.4f),
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.8f)
                                                    )
                                                )
                                            )
                                    )

                                    // Protocol badge top left
                                    val isHls = stream.url.contains(".m3u8", ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .align(Alignment.TopStart)
                                            .background(
                                                color = if (isHls) Color(0xFFEF4444) else primaryColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isHls) "HLS .m3u8" else "MP4 Stream",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Delete button for custom stream
                                    if (stream.isCustom) {
                                        IconButton(
                                            onClick = { viewModel.deleteCustomStream(stream.id) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(28.dp)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete stream",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Center Play icon
                                    IconButton(
                                        onClick = {
                                            viewModel.navigateTo(AppScreen.Player(stream.url, startPositionMs = 0L))
                                        },
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(40.dp)
                                            .background(primaryColor, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play stream",
                                            tint = currentTheme.onPrimaryColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Stream Title & Actions
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stream.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = stream.category,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )

                                    if (savedPos > 5000L) {
                                        Button(
                                            onClick = {
                                                viewModel.navigateTo(AppScreen.Player(stream.url, startPositionMs = savedPos))
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = primaryColor,
                                                contentColor = currentTheme.onPrimaryColor
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                        ) {
                                            Text(
                                                text = "Resume (${viewModel.formatPlaybackTime(savedPos)})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.navigateTo(AppScreen.Player(stream.url, startPositionMs = 0L))
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White.copy(alpha = 0.12f),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                        ) {
                                            Text(
                                                text = "Play Stream",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        // Add Stream Dialog / Modal
        if (showAddDialog) {
            AddStreamModalDialog(
                primaryColor = primaryColor,
                currentTheme = currentTheme,
                onDismiss = { showAddDialog = false },
                onAddStream = { title, url, cat, thumb ->
                    viewModel.addCustomStream(title, url, cat, thumb)
                    showAddDialog = false
                    Toast.makeText(context, "Stream added successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun AddStreamModalDialog(
    primaryColor: Color,
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onAddStream: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Live TV") }
    var thumbnailUrl by remember { mutableStateOf("") }

    val categories = listOf("Live TV", "Movies", "Sports", "News", "Music", "Custom")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1C1C),
        titleContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddLink,
                    contentDescription = null,
                    tint = primaryColor
                )
                Text("Add Custom Online Stream", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Name", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("e.g. My IPTV Channel", color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_stream_title_input")
                )

                // URL Input
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL (.m3u8 / .mp4)", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("https://example.com/stream.m3u8", color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_stream_url_input")
                )

                // Category Chips Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it }) { cat ->
                            val isSelected = cat == category
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) primaryColor else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { category = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) currentTheme.onPrimaryColor else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Thumbnail URL (Optional)
                OutlinedTextField(
                    value = thumbnailUrl,
                    onValueChange = { thumbnailUrl = it },
                    label = { Text("Thumbnail / Icon URL (Optional)", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("https://example.com/logo.jpg", color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        onAddStream(title, url, category, thumbnailUrl)
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = currentTheme.onPrimaryColor
                ),
                modifier = Modifier.testTag("confirm_add_stream_button")
            ) {
                Text("Save Stream", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun CardPosterMenuButtonWithDialogs(
    targetMovie: Movie,
    isTvShow: Boolean,
    showTitle: String?,
    currentPosterUrl: String,
    primaryColor: Color,
    viewModel: MainViewModel,
    showActionSheet: Boolean = false,
    onDismissActionSheet: () -> Unit = {}
) {
    val context = LocalContext.current
    var showPosterBrowser by remember { mutableStateOf(false) }

    val displayTitle = remember(targetMovie, isTvShow, showTitle) {
        if (isTvShow && !showTitle.isNullOrBlank()) {
            showTitle
        } else {
            com.example.util.EpisodeMetadataFixer.sanitizeMediaTitle(targetMovie.title)
        }
    }

    val cardPosterGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val uriStr = uri.toString()
            viewModel.saveCardPosterOverride(
                movieId = targetMovie.id,
                showTitle = if (isTvShow) displayTitle else null,
                customUrl = uriStr,
                hasEmbeddedText = false
            )
            Toast.makeText(context, "Card poster updated", Toast.LENGTH_SHORT).show()
        }
    }

    // iOS Infuse-styled Action Sheet ("Change Poster" / "Cancel") opened via card long-press
    if (showActionSheet) {
        val hasCustomCardOverride = remember(targetMovie.id, displayTitle, showActionSheet) {
            !viewModel.getCardPosterOverride(targetMovie.id, if (isTvShow) displayTitle else null).isNullOrBlank()
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismissActionSheet,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissActionSheet() }
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consume click inside sheet */ },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Main Action Group
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1C1C1E))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    ) {
                        // Sheet Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = displayTitle,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Card Poster Options",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // "Change Poster" option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismissActionSheet()
                                    showPosterBrowser = true
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                .testTag("card_action_change_poster"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Change Poster",
                                color = primaryColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (hasCustomCardOverride) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.saveCardPosterOverride(
                                            movieId = targetMovie.id,
                                            showTitle = if (isTvShow) displayTitle else null,
                                            customUrl = "",
                                            hasEmbeddedText = false
                                        )
                                        onDismissActionSheet()
                                        Toast.makeText(context, "Card poster reset to default", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 15.dp)
                                    .testTag("card_action_reset_poster"),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    tint = Color(0xFFFF453A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Reset to Default Poster",
                                    color = Color(0xFFFF453A),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Separate "Cancel" Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2C2C2E))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .clickable { onDismissActionSheet() }
                            .padding(vertical = 15.dp)
                            .testTag("card_action_cancel"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Native In-App TMDB Poster Browser & Search Dialog (scoped strictly to Card Poster Override)
    if (showPosterBrowser) {
        val currentCardOverride = remember(targetMovie.id, displayTitle, showPosterBrowser) {
            viewModel.getCardPosterOverride(targetMovie.id, if (isTvShow) displayTitle else null) ?: ""
        }
        InfusePosterManagementDialog(
            targetMovie = targetMovie,
            isTvShow = isTvShow,
            showTitle = if (isTvShow) displayTitle else null,
            currentPosterUrl = currentPosterUrl,
            initialCustomUrl = currentCardOverride,
            primaryColor = primaryColor,
            viewModel = viewModel,
            surfaceLabel = "Card Poster",
            onPickFromGallery = {
                showPosterBrowser = false
                cardPosterGalleryLauncher.launch("image/*")
            },
            onApplyPosterUrl = { selectedUrl, hasEmbeddedText ->
                viewModel.saveCardPosterOverride(
                    movieId = targetMovie.id,
                    showTitle = if (isTvShow) displayTitle else null,
                    customUrl = selectedUrl,
                    hasEmbeddedText = hasEmbeddedText
                )
                showPosterBrowser = false
                Toast.makeText(context, "Card poster updated", Toast.LENGTH_SHORT).show()
            },
            onApplyPosterAndMetadata = { selectedUrl, metadata, tmdbId, hasEmbeddedText ->
                if (metadata != null) {
                    viewModel.applyPosterAndMatchedMetadata(
                        targetMovie = targetMovie,
                        customPosterUrl = selectedUrl,
                        metadata = metadata,
                        tmdbId = tmdbId,
                        isTvShow = isTvShow,
                        showTitle = if (isTvShow) displayTitle else null,
                        hasEmbeddedText = hasEmbeddedText,
                        isCardOverride = true
                    )
                    Toast.makeText(context, "Card poster & metadata updated!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.saveCardPosterOverride(
                        movieId = targetMovie.id,
                        showTitle = if (isTvShow) displayTitle else null,
                        customUrl = selectedUrl,
                        hasEmbeddedText = hasEmbeddedText
                    )
                    Toast.makeText(context, "Card poster updated", Toast.LENGTH_SHORT).show()
                }
                showPosterBrowser = false
            },
            onResetToDefault = if (currentCardOverride.isNotBlank()) {
                {
                    viewModel.saveCardPosterOverride(
                        movieId = targetMovie.id,
                        showTitle = if (isTvShow) displayTitle else null,
                        customUrl = "",
                        hasEmbeddedText = false
                    )
                    showPosterBrowser = false
                    Toast.makeText(context, "Card poster reset to default", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onDismiss = { showPosterBrowser = false }
        )
    }
}

