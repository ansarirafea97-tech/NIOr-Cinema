package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.FrostedGlassContainer
import com.example.ui.components.SeekDiagnosticsDialog
import com.example.ui.components.TmdbAttribution
import com.example.util.infuseClickable
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.AppTheme

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentTheme by viewModel.selectedTheme.collectAsState()
    val primaryColor = currentTheme.primaryColor
    val primaryContainer = currentTheme.primaryContainer

    // Collect ViewModel settings states
    val librarySync by viewModel.librarySyncEnabled.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val continuousSeekEnabled by viewModel.continuousSeekEnabled.collectAsState()
    val seekDurationSeconds by viewModel.seekDurationSeconds.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()
    val subtitleSize by viewModel.subtitleFontSize.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()
    val encoding by viewModel.subtitleEncoding.collectAsState()
    val dynamicInterface by viewModel.dynamicInterface.collectAsState()
    val gridDensity by viewModel.gridDensity.collectAsState()

    var showEncodingDropdown by remember { mutableStateOf(false) }
    var showSeekDiagnosticsDialog by remember { mutableStateOf(false) }
    val autoImportGallery by viewModel.autoImportGallery.collectAsState()
    val movies by viewModel.moviesList.collectAsState()

    val activeArtworkUrl = remember(movies) {
        movies.firstOrNull()?.let { it.backdropUrl.ifBlank { it.posterUrl } }
    }
    val dynamicPalette = com.example.util.rememberDynamicAmbientPalette(
        artworkUrl = activeArtworkUrl,
        isDynamicEnabled = dynamicInterface,
        fallbackColor = primaryColor
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("infuse_prefs", android.content.Context.MODE_PRIVATE) }

    LaunchedEffect(autoImportGallery) {
        sharedPrefs.edit().putBoolean("auto_import_gallery", autoImportGallery).apply()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121414))
    ) {
        // Subtle Dynamic Ambient glow in Settings if enabled
        com.example.util.DynamicAmbientBackgroundLayer(palette = dynamicPalette)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .infuseClickable { viewModel.navigateBack() }
                            .testTag("settings_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryColor
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Help",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Scrollable settings body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Large Centered INFUSE Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NOIR CINEMA",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            fontSize = 38.sp
                        ),
                        modifier = Modifier.testTag("infuse_banner_title")
                    )
                }

                // Section 1: Playback Settings
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSectionTitle("Playback", primaryColor)

                    FrostedGlassContainer(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column {
                            // Library Sync switch
                            SettingsSwitchRow(
                                title = "Library Sync",
                                description = "Automatically keep progress in sync",
                                icon = Icons.Default.Sync,
                                checked = librarySync,
                                onCheckedChange = { viewModel.toggleLibrarySync() },
                                tag = "sync_switch"
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Auto-Scan Device Gallery switch
                            SettingsSwitchRow(
                                title = "Auto-Scan Device Gallery",
                                description = "Auto-scan and load all videos from device storage",
                                icon = Icons.Default.PhotoLibrary,
                                checked = autoImportGallery,
                                onCheckedChange = { viewModel.setAutoImportGallery(it) },
                                tag = "auto_import_gallery_switch"
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Default speed slider row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .infuseClickable {
                                        // Cycles speed: 1.0 -> 1.25 -> 1.5 -> 2.0 -> 1.0
                                        val nextSpeed = when (speed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        viewModel.setPlaybackSpeed(nextSpeed)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Default Playback Speed",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        color = primaryColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Expand speed selector",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Continuous Seek Mode toggle (DataStore-backed)
                            SettingsSwitchRow(
                                title = "Continuous Seek Mode",
                                description = "Keep tapping after a double-tap to continuously seek",
                                icon = Icons.Default.FastForward,
                                checked = continuousSeekEnabled,
                                onCheckedChange = { viewModel.setContinuousSeekEnabled(it) },
                                tag = "continuous_seek_switch"
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Seek Duration selector (5s, 10s, 30s - DataStore-backed)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay10,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = "Double-Tap Seek Duration",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "Interval for double-tap and continuous seeking",
                                                color = Color.White.copy(alpha = 0.4f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    com.example.util.SeekPreferencesRepository.ALLOWED_SEEK_DURATIONS.forEach { seconds ->
                                        val isSelected = seekDurationSeconds == seconds
                                        Surface(
                                            color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.06f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .infuseClickable { viewModel.setSeekDurationSeconds(seconds) }
                                                .testTag("seek_duration_${seconds}s")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            ) {
                                                Text(
                                                    text = "${seconds}s",
                                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Volume & Brightness Swipe Gesture Sensitivity Slider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = "Swipe Gesture Sensitivity",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "Threshold & response for volume and brightness gestures",
                                                color = Color.White.copy(alpha = 0.4f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    val sensitivityLabel = when {
                                        gestureSensitivity <= 0.65f -> "Low"
                                        gestureSensitivity >= 1.4f -> "High"
                                        gestureSensitivity in 0.9f..1.1f -> "Normal"
                                        else -> "Custom"
                                    }
                                    Text(
                                        text = "$sensitivityLabel (${String.format(java.util.Locale.US, "%.1f", gestureSensitivity)}x)",
                                        color = primaryColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }

                                Slider(
                                    value = gestureSensitivity,
                                    onValueChange = { viewModel.setGestureSensitivity(it) },
                                    valueRange = 0.5f..2.0f,
                                    steps = 5,
                                    colors = SliderDefaults.colors(
                                        thumbColor = primaryColor,
                                        activeTrackColor = primaryColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                        activeTickColor = Color.Black.copy(alpha = 0.35f),
                                        inactiveTickColor = Color.White.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("gesture_sensitivity_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Low (0.5x)",
                                        color = Color.White.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "Normal (1.0x)",
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "High (2.0x)",
                                        color = Color.White.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Bug Report / Seek Diagnostics
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .infuseClickable { showSeekDiagnosticsDialog = true }
                                    .padding(16.dp)
                                    .testTag("settings_bug_report_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Text(
                                            text = "Bug Report & Seek Diagnostics",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "View and copy playback & seek diagnostic logs",
                                            color = Color.White.copy(alpha = 0.4f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Bug Report",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Section 3: Subtitles Customization
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSectionTitle("Subtitles", primaryColor)

                    FrostedGlassContainer(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Subtitle font size control row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Font size",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .infuseClickable { viewModel.decreaseSubtitleFontSize() }
                                            .testTag("subtitle_size_minus"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Remove, "Decrease font size", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    Text(
                                        text = "$subtitleSize sp",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .testTag("subtitle_size_label")
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .infuseClickable { viewModel.increaseSubtitleFontSize() }
                                            .testTag("subtitle_size_plus"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, "Increase font size", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Subtitle color indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Color",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SubtitleColorCircle(
                                        color = Color.White,
                                        label = "White",
                                        isSelected = subtitleColor.equals("White", ignoreCase = true),
                                        onClick = { viewModel.setSubtitleColor("White") }
                                    )
                                    SubtitleColorCircle(
                                        color = Color(0xFFFACC15), // Yellow
                                        label = "Yellow",
                                        isSelected = subtitleColor.equals("Yellow", ignoreCase = true),
                                        onClick = { viewModel.setSubtitleColor("Yellow") }
                                    )
                                    SubtitleColorCircle(
                                        color = Color(0xFF22D3EE), // Cyan
                                        label = "Cyan",
                                        isSelected = subtitleColor.equals("Cyan", ignoreCase = true),
                                        onClick = { viewModel.setSubtitleColor("Cyan") }
                                    )
                                    SubtitleColorCircle(
                                        color = Color(0xFF4ADE80), // Green
                                        label = "Green",
                                        isSelected = subtitleColor.equals("Green", ignoreCase = true),
                                        onClick = { viewModel.setSubtitleColor("Green") }
                                    )
                                    SubtitleColorCircle(
                                        color = Color(0xFFE879F9), // Magenta
                                        label = "Magenta",
                                        isSelected = subtitleColor.equals("Magenta", ignoreCase = true),
                                        onClick = { viewModel.setSubtitleColor("Magenta") }
                                    )
                                }
                            }

                            // Subtitle encoding dropdown selector
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .infuseClickable { showEncodingDropdown = true }
                                        .padding(vertical = 4.dp)
                                        .testTag("subtitle_encoding_selector"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Encoding",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = encoding,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Icon(
                                            Icons.Default.ExpandMore,
                                            "Options",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                val encodingList = listOf(
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
                                    expanded = showEncodingDropdown,
                                    onDismissRequest = { showEncodingDropdown = false },
                                    modifier = Modifier
                                        .background(Color(0xFF1C1C1E))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                ) {
                                    encodingList.forEach { enc ->
                                        val isCurrent = encoding == enc
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
                                                        fontSize = 14.sp
                                                    )
                                                    if (isCurrent) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = primaryColor,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.setSubtitleEncoding(enc)
                                                showEncodingDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Appearance custom presentation box
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSectionTitle("Appearance", primaryColor)

                    // Dynamic theme poster preview matching selected theme
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        currentTheme.startGradient,
                                        currentTheme.primaryColor,
                                        currentTheme.endGradient
                                    )
                                )
                            )
                            .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        // Ambient darken filter
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Theme: ${currentTheme.themeName}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTheme.description,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Theme selectors row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppTheme.values().forEach { theme ->
                            val isSelected = theme == currentTheme
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) theme.primaryColor.copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.03f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) theme.primaryColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .infuseClickable {
                                        viewModel.selectTheme(theme)
                                        val appThemeEnum = when (theme) {
                                            AppTheme.MIDNIGHT_ORANGE -> com.example.util.AppThemeManager.AppTheme.ORANGE
                                            AppTheme.NEON_CYBER -> com.example.util.AppThemeManager.AppTheme.CYBERPUNK
                                            AppTheme.NORDIC_FROST -> com.example.util.AppThemeManager.AppTheme.FROST
                                            AppTheme.EMERALD_FOREST -> com.example.util.AppThemeManager.AppTheme.FOREST
                                            AppTheme.SOLAR_FLARE -> com.example.util.AppThemeManager.AppTheme.FLARE
                                        }
                                        com.example.util.AppThemeManager.setSelectedTheme(context, appThemeEnum)
                                        (context as? android.app.Activity)?.window?.decorView?.let { decorView ->
                                            com.example.util.AppThemeManager.applyThemeToBottomNav(context, decorView)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    theme.primaryColor,
                                                    theme.primaryContainer,
                                                    theme.primaryColor
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = theme.themeName.split(" ").last(),
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Theme parameters
                    FrostedGlassContainer(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column {
                            // Dynamic Interface
                            SettingsSwitchRow(
                                title = "Dynamic Interface",
                                description = "Apply background gradients responsively",
                                icon = Icons.Default.BrightnessMedium,
                                checked = dynamicInterface,
                                onCheckedChange = { viewModel.toggleDynamicInterface() },
                                tag = "dynamic_switch"
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Grid Density switcher
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .infuseClickable {
                                        val nextDensity = if (gridDensity == "Comfortable") "Compact" else "Comfortable"
                                        viewModel.setGridDensity(nextDensity)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Text(
                                            text = "Grid Density",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Change layout spacing of Library grid",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                Text(
                                    text = gridDensity,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.testTag("density_value_label")
                                )
                            }
                        }
                    }
                }
                // Section 6: App Version and Credits Footer
                Column(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Version ${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Made with ❤️ for Cinephiles",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TmdbAttribution()
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        if (showSeekDiagnosticsDialog) {
            SeekDiagnosticsDialog(
                onDismiss = { showSeekDiagnosticsDialog = false }
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(
    text: String,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        color = primaryColor,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp
        ),
        modifier = modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
fun SubtitleColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    label: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .infuseClickable(onClick = onClick)
            .padding(if (isSelected) 4.dp else 2.dp)
            .background(color, CircleShape)
            .testTag(if (label.isNotBlank()) "subtitle_color_${label.lowercase()}" else "subtitle_color")
    )
}
