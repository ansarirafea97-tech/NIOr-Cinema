package com.example

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.DetailsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.transition.InfuseLaunchTransition
import com.example.ui.transition.InfuseLaunchTransitionController
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// iOS UINavigationController interactive push/pop easing curve
private val IOSPushPopEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        com.example.util.AppThemeManager.applyThemeToBottomNav(this, window.decorView)

        val isFreshLaunch = savedInstanceState == null
        val launchTransitionController = InfuseLaunchTransitionController(
            isFreshLaunch = isFreshLaunch
        )

        // Native Android 12+ system splash screen sequential exit:
        // Hold orange logo briefly -> smooth opacity fade-out -> smooth Home Screen fade-in on pure black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                launchTransitionController.onSystemSplashExit(
                    splashScreenView = splashScreenView,
                    scope = lifecycleScope,
                    shouldSkipAnimation = !isFreshLaunch
                )
            }
        }

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsState()
            MyApplicationTheme(appTheme = selectedTheme) {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val currentTab by viewModel.currentTab.collectAsState()
                val isTrailerModalOpen by viewModel.isTrailerModalOpen.collectAsState()
                val transitionScope = rememberCoroutineScope()

                // Infuse-inspired fresh app-launch transition:
                // Renders Home Screen immediately at alpha = 0f over pure black, waits for frame 1 + splash
                // logo fade-out, then smoothly dissolves Home Screen in (opacity only, fresh launch only).
                var hasCompletedAppLaunchTransition by rememberSaveable { mutableStateOf(!isFreshLaunch) }
                val canStartHomeFadeIn by launchTransitionController.canStartHomeFadeIn.collectAsState()
                val appLaunchAlpha = remember { Animatable(if (hasCompletedAppLaunchTransition) 1f else 0f) }

                LaunchedEffect(Unit) {
                    if (!hasCompletedAppLaunchTransition) {
                        withFrameNanos { }
                        launchTransitionController.markHomeFirstFrameRendered()
                        launchTransitionController.awaitFallbackOrStartIfUnsupported()
                    }
                }

                LaunchedEffect(canStartHomeFadeIn, hasCompletedAppLaunchTransition) {
                    if (hasCompletedAppLaunchTransition) {
                        appLaunchAlpha.snapTo(1f)
                    } else if (canStartHomeFadeIn) {
                        appLaunchAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = InfuseLaunchTransition.HOME_FADE_IN_DURATION_MS.toInt(),
                                easing = InfuseLaunchTransition.HomeFadeInEasing
                            )
                        )
                        hasCompletedAppLaunchTransition = true
                    }
                }

                // 1f = DetailsScreen is completely off-screen at the RIGHT edge (+screenWidthPx)
                // 0f = DetailsScreen is fully settled on-screen (0f translationX)
                val animatedSlideOffset = remember { Animatable(1f) }

                // Plain synchronous state for live interactive back swipe progress
                // (avoids Animatable MutatorMutex contention during progress.collect)
                var isBackGestureActive by remember { mutableStateOf(false) }
                var backGestureFraction by remember { mutableFloatStateOf(0f) }
                var cancelRestoreJob by remember { mutableStateOf<Job?>(null) }

                var lastDetailsMovieId by remember { mutableStateOf<String?>(null) }
                var lastAnimatedMovieId by remember { mutableStateOf<String?>(null) }
                var lastPlayerState by remember { mutableStateOf<AppScreen.Player?>(null) }

                if (currentScreen is AppScreen.Details) {
                    val newId = (currentScreen as AppScreen.Details).movieId
                    if (lastDetailsMovieId != newId) {
                        lastDetailsMovieId = newId
                    }
                }
                if (currentScreen is AppScreen.Player) {
                    lastPlayerState = currentScreen as AppScreen.Player
                }

                // Drive forward RIGHT-to-LEFT push and completed LEFT-to-RIGHT pop animations
                LaunchedEffect(currentScreen) {
                    cancelRestoreJob?.cancel()
                    cancelRestoreJob = null
                    isBackGestureActive = false
                    backGestureFraction = 0f

                    when (val screen = currentScreen) {
                        is AppScreen.Details -> {
                            if (lastAnimatedMovieId != screen.movieId) {
                                // Always start incoming Detail Screen from the RIGHT edge (+width)
                                animatedSlideOffset.snapTo(1f)
                                lastAnimatedMovieId = screen.movieId
                            }
                            if (animatedSlideOffset.value > 0f) {
                                animatedSlideOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 340,
                                        easing = IOSPushPopEasing
                                    )
                                )
                            }
                            animatedSlideOffset.snapTo(0f)
                        }
                        is AppScreen.Player -> {
                            // Keep DetailsScreen settled at 0f underneath PlayerScreen
                            if (animatedSlideOffset.value != 0f) {
                                animatedSlideOffset.snapTo(0f)
                            }
                        }
                        is AppScreen.Library, is AppScreen.Settings -> {
                            lastAnimatedMovieId = null
                            val startVal = animatedSlideOffset.value.coerceIn(0f, 1f)
                            if (startVal < 0.999f) {
                                val popDuration = if (startVal < 0.05f) {
                                    300
                                } else {
                                    (260f * (1f - startVal).coerceIn(0f, 1f)).toInt().coerceIn(110, 260)
                                }
                                try {
                                    animatedSlideOffset.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = popDuration,
                                            easing = IOSPushPopEasing
                                        )
                                    )
                                } finally {
                                    if (viewModel.currentScreen.value !is AppScreen.Details &&
                                        viewModel.currentScreen.value !is AppScreen.Player
                                    ) {
                                        animatedSlideOffset.snapTo(1f)
                                    }
                                }
                            } else {
                                animatedSlideOffset.snapTo(1f)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = appLaunchAlpha.value.coerceIn(0f, 1f)
                            }
                    ) {
                    val density = LocalDensity.current
                    val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val edgeAnchorLimitPx = with(density) { 40.dp.toPx() }
                    val edgeShadowWidthPx = with(density) { 18.dp.toPx() }

                    val isEnteringNewDetail = currentScreen is AppScreen.Details &&
                            lastAnimatedMovieId != (currentScreen as AppScreen.Details).movieId
                    val slideFraction = when {
                        isEnteringNewDetail -> 1f
                        isBackGestureActive -> backGestureFraction.coerceIn(0f, 1f)
                        else -> animatedSlideOffset.value.coerceIn(0f, 1f)
                    }
                    val coverProgress = (1f - slideFraction).coerceIn(0f, 1f)
                    val isDetailsLayerVisible = lastDetailsMovieId != null &&
                            (currentScreen is AppScreen.Details || currentScreen is AppScreen.Player || slideFraction < 0.999f)

                    // Back handler for Settings, non-Home tabs, or absorbing rapid back presses while Details finishes sliding out
                    BackHandler(
                        enabled = (currentScreen is AppScreen.Settings) ||
                                (currentScreen is AppScreen.Library && isDetailsLayerVisible) ||
                                (currentScreen is AppScreen.Library && currentTab != "Home" && !isDetailsLayerVisible)
                    ) {
                        if (currentScreen is AppScreen.Library && isDetailsLayerVisible) {
                            transitionScope.launch {
                                cancelRestoreJob?.cancel()
                                isBackGestureActive = false
                                backGestureFraction = 0f
                                animatedSlideOffset.snapTo(1f)
                            }
                        } else {
                            viewModel.navigateBack()
                        }
                    }

                    // Native Android Predictive Back Gesture + System Back Button for DetailsScreen.
                    // Always enabled whenever currentScreen is AppScreen.Details (and trailer modal is closed)
                    // so repeated swipe-cancel-swipe-complete cycles never disable the callback or exit the Activity.
                    PredictiveBackHandler(
                        enabled = currentScreen is AppScreen.Details && !isTrailerModalOpen
                    ) { progress: Flow<BackEventCompat> ->
                        cancelRestoreJob?.cancel()
                        cancelRestoreJob = null

                        val startSlide = if (isBackGestureActive) {
                            backGestureFraction.coerceIn(0f, 0.95f)
                        } else {
                            animatedSlideOffset.value.coerceIn(0f, 0.95f)
                        }
                        var initialTouchX = -1f
                        var latestFraction = startSlide
                        backGestureFraction = startSlide
                        isBackGestureActive = true

                        try {
                            progress.collect { backEvent ->
                                if (initialTouchX < 0f && backEvent.touchX > 0f) {
                                    initialTouchX = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) {
                                        backEvent.touchX.coerceAtMost(edgeAnchorLimitPx)
                                    } else {
                                        backEvent.touchX
                                    }
                                }
                                val fingerFraction = if (screenWidthPx > 0f && initialTouchX >= 0f && backEvent.touchX > 0f) {
                                    val rawDelta = if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                                        (initialTouchX - backEvent.touchX).coerceAtLeast(0f)
                                    } else {
                                        (backEvent.touchX - initialTouchX).coerceAtLeast(0f)
                                    }
                                    (rawDelta / screenWidthPx).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                val rawProgress = maxOf(backEvent.progress, fingerFraction).coerceIn(0f, 0.96f)
                                latestFraction = (startSlide + (1f - startSlide) * rawProgress).coerceIn(0f, 0.96f)
                                backGestureFraction = latestFraction
                            }

                            // Gesture completed (or Android system back button pressed):
                            // Pop exactly one Detail Screen and let LaunchedEffect(currentScreen) smoothly finish sliding to 1f.
                            val completedAt = latestFraction.coerceIn(0f, 0.96f)
                            withContext(NonCancellable) {
                                animatedSlideOffset.snapTo(completedAt)
                                isBackGestureActive = false
                                backGestureFraction = 0f
                                if (viewModel.currentScreen.value is AppScreen.Details) {
                                    viewModel.navigateBack(force = true)
                                }
                            }
                        } catch (e: CancellationException) {
                            // User released before completing gesture:
                            // Restore Detail Screen smoothly to 0f, keep currentScreen & backStack untouched,
                            // and keep PredictiveBackHandler enabled for the next swipe.
                            val canceledAt = latestFraction.coerceIn(0f, 0.96f)
                            cancelRestoreJob = transitionScope.launch {
                                try {
                                    animatedSlideOffset.snapTo(canceledAt)
                                    isBackGestureActive = false
                                    backGestureFraction = 0f
                                    if (canceledAt > 0.001f) {
                                        val restoreDuration = (250f * canceledAt).toInt().coerceIn(120, 250)
                                        animatedSlideOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = restoreDuration,
                                                easing = IOSPushPopEasing
                                            )
                                        )
                                    }
                                    if (viewModel.currentScreen.value is AppScreen.Details) {
                                        animatedSlideOffset.snapTo(0f)
                                    }
                                } finally {
                                    isBackGestureActive = false
                                    backGestureFraction = 0f
                                }
                            }
                            throw e
                        }
                    }

                    // 1. PERSISTENT BASE LAYER: Home / Library Screen
                    // Stays mounted underneath DetailsScreen so scroll position, posters, and state are preserved
                    // and revealed in real time with iOS parallax during interactive push and pop transitions.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = if (isDetailsLayerVisible) {
                                    -coverProgress * (screenWidthPx * 0.28f)
                                } else {
                                    0f
                                }
                            }
                    ) {
                        LibraryScreen(viewModel = viewModel)

                        // iOS-style subtle dimming scrim over the underneath screen while Details is active/sliding
                        if (isDetailsLayerVisible && coverProgress > 0.001f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = coverProgress * 0.35f))
                                    .pointerInput(Unit) {
                                        // Block touch events from leaking to underneath screen while Details is above it
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // 2. MOVIE / TV DETAILS SCREEN LAYER (Enters from RIGHT to LEFT, Interactive Left-Edge Swipe Back to RIGHT)
                    val activeDetailsId = (currentScreen as? AppScreen.Details)?.movieId ?: lastDetailsMovieId
                    if (isDetailsLayerVisible && activeDetailsId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    // Soft iOS drop shadow along the left leading edge of DetailsScreen during push/pop
                                    if (slideFraction > 0.001f && slideFraction < 0.999f) {
                                        val leadX = slideFraction * size.width
                                        val shadowStart = (leadX - edgeShadowWidthPx).coerceAtLeast(0f)
                                        val shadowAlpha = (0.48f * coverProgress).coerceIn(0f, 0.48f)
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = shadowAlpha)),
                                                startX = shadowStart,
                                                endX = leadX
                                            ),
                                            topLeft = Offset(shadowStart, 0f),
                                            size = Size((leadX - shadowStart).coerceAtLeast(0f), size.height)
                                        )
                                    }
                                }
                                .graphicsLayer {
                                    translationX = slideFraction * screenWidthPx
                                }
                        ) {
                            key(activeDetailsId) {
                                DetailsScreen(movieId = activeDetailsId, viewModel = viewModel)
                            }
                        }
                    }

                    // 3. Player Screen with smooth fade transition (no scale transform so SurfaceView hardware overlay attaches directly)
                    val activePlayer = (currentScreen as? AppScreen.Player) ?: lastPlayerState
                    AnimatedVisibility(
                        visible = currentScreen is AppScreen.Player,
                        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
                        label = "PlayerFadeTransition"
                    ) {
                        if (activePlayer != null) {
                            PlayerScreen(
                                movieId = activePlayer.movieId,
                                startPositionMs = activePlayer.startPositionMs,
                                viewModel = viewModel
                            )
                        }
                    }

                    // 4. Settings Screen with smooth AnimatedVisibility transition
                    val defaultEnterTransition = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
                    val defaultExitTransition = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.98f, animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))

                    AnimatedVisibility(
                        visible = currentScreen is AppScreen.Settings,
                        enter = defaultEnterTransition,
                        exit = defaultExitTransition,
                        label = "SettingsFadeTransition"
                    ) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.util.AppThemeManager.applyThemeToBottomNav(this, window.decorView)
        // If returning from PiP back to app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            viewModel.setIsInPipMode(isInPictureInPictureMode)
        }
    }

    fun isPipSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPip(aspectRatio: Rational? = null): Boolean {
        if (!isPipSupported()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ratio = aspectRatio ?: Rational(16, 9)
                val clampedRatio = when {
                    ratio.toFloat() < 0.418410f -> Rational(100, 239)
                    ratio.toFloat() > 2.390000f -> Rational(239, 100)
                    else -> ratio
                }
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(clampedRatio)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        } else false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.currentScreen.value is AppScreen.Player) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setIsInPipMode(isInPictureInPictureMode)
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.currentScreen.value is AppScreen.Player) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.05f else -0.05f
                    viewModel.adjustVolume(delta)
                    viewModel.setShowRightSwipeIndicator(true)
                }
                return true // Intercept hardware key event to suppress native Android system volume overlay
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
