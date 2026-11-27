package com.example.ui.transition

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates the Infuse-inspired fresh app launch transition:
 * 1. Existing pure black system splash screen displays the orange Noir Cinema logo.
 * 2. Logo stays visible briefly, then smoothly fades out (opacity only).
 * 3. Once the Home Screen has rendered its first frame on pure black and as the logo
 *    disappears, the Home Screen smoothly fades in from transparent to visible.
 */
object InfuseLaunchTransition {
    const val LOGO_HOLD_DURATION_MS = 180L
    const val LOGO_FADE_OUT_DURATION_MS = 280L
    const val HOME_FADE_OVERLAP_INTO_LOGO_FADE_MS = 140L
    const val HOME_FADE_IN_DURATION_MS = 460L
    const val SPLASH_LISTENER_FALLBACK_TIMEOUT_MS = 320L

    // Smooth iOS-like ease-in/ease-out curve (no overshoot, no bounce)
    val HomeFadeInEasing = CubicBezierEasing(0.35f, 0.04f, 0.25f, 1.0f)

    fun createLogoFadeOutInterpolator(): PathInterpolator =
        PathInterpolator(0.42f, 0f, 0.58f, 1f)
}

class InfuseLaunchTransitionController(
    isFreshLaunch: Boolean,
    private val hasSplashExitListenerSupport: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
) {
    private val _isHomeFirstFrameRendered = MutableStateFlow(!isFreshLaunch)
    val isHomeFirstFrameRendered: StateFlow<Boolean> = _isHomeFirstFrameRendered.asStateFlow()

    private val _canStartHomeFadeIn = MutableStateFlow(!isFreshLaunch)
    val canStartHomeFadeIn: StateFlow<Boolean> = _canStartHomeFadeIn.asStateFlow()

    private val _hasSystemSplashEntered = MutableStateFlow(false)
    val hasSystemSplashEntered: StateFlow<Boolean> = _hasSystemSplashEntered.asStateFlow()

    @Volatile
    private var transitionStarted: Boolean = !isFreshLaunch

    fun markHomeFirstFrameRendered() {
        if (!_isHomeFirstFrameRendered.value) {
            _isHomeFirstFrameRendered.value = true
        }
    }

    fun triggerHomeFadeIn() {
        if (!_canStartHomeFadeIn.value) {
            _canStartHomeFadeIn.value = true
        }
    }

    /**
     * Fallback coordinator for pre-Android 12 devices or environments where Android's
     * SplashScreen.OnExitAnimationListener is not invoked (e.g., Activity restarts or tests).
     */
    suspend fun awaitFallbackOrStartIfUnsupported() {
        if (_canStartHomeFadeIn.value) return
        _isHomeFirstFrameRendered.first { it }
        if (!hasSplashExitListenerSupport) {
            triggerHomeFadeIn()
            return
        }
        delay(InfuseLaunchTransition.SPLASH_LISTENER_FALLBACK_TIMEOUT_MS)
        if (!_hasSystemSplashEntered.value) {
            triggerHomeFadeIn()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun onSystemSplashExit(
        splashScreenView: SplashScreenView,
        scope: CoroutineScope,
        shouldSkipAnimation: Boolean
    ) {
        if (shouldSkipAnimation || transitionStarted) {
            runCatching { splashScreenView.remove() }
            triggerHomeFadeIn()
            return
        }
        transitionStarted = true
        _hasSystemSplashEntered.value = true

        scope.launch {
            var activeAnimator: ObjectAnimator? = null
            try {
                // Guarantee Home Screen has composed, laid out, and committed frame 1 before reveal
                withTimeoutOrNull(180L) {
                    _isHomeFirstFrameRendered.first { it }
                }

                // Step 2a: Keep existing orange logo visible briefly on pure black
                delay(InfuseLaunchTransition.LOGO_HOLD_DURATION_MS)

                val iconView = splashScreenView.iconView
                if (iconView != null) {
                    // Reveal the identical solid #000000 Compose canvas behind the icon so the Home Screen
                    // can begin fading in smoothly as the logo finishes disappearing, with zero flicker.
                    splashScreenView.background = null
                    splashScreenView.setBackgroundColor(AndroidColor.TRANSPARENT)

                    var removed = false
                    val removeSplashSafely = {
                        if (!removed) {
                            removed = true
                            runCatching { splashScreenView.remove() }
                        }
                    }

                    val logoFadeOut = ObjectAnimator.ofFloat(
                        iconView,
                        View.ALPHA,
                        iconView.alpha.coerceIn(0f, 1f),
                        0f
                    ).apply {
                        duration = InfuseLaunchTransition.LOGO_FADE_OUT_DURATION_MS
                        interpolator = InfuseLaunchTransition.createLogoFadeOutInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                removeSplashSafely()
                            }
                        })
                    }
                    activeAnimator = logoFadeOut
                    logoFadeOut.start()

                    // Step 3: As the logo disappears, smoothly fade in the Home Screen
                    delay(InfuseLaunchTransition.HOME_FADE_OVERLAP_INTO_LOGO_FADE_MS)
                    triggerHomeFadeIn()

                    val remainingLogoMs = (InfuseLaunchTransition.LOGO_FADE_OUT_DURATION_MS -
                        InfuseLaunchTransition.HOME_FADE_OVERLAP_INTO_LOGO_FADE_MS).coerceAtLeast(0L)
                    delay(remainingLogoMs + 32L)
                    removeSplashSafely()
                } else {
                    // Fallback when OEM omits iconView: fade entire black+logo splash view over pure black Home canvas
                    var removed = false
                    val removeSplashSafely = {
                        if (!removed) {
                            removed = true
                            runCatching { splashScreenView.remove() }
                        }
                    }

                    val splashFadeOut = ObjectAnimator.ofFloat(
                        splashScreenView,
                        View.ALPHA,
                        splashScreenView.alpha.coerceIn(0f, 1f),
                        0f
                    ).apply {
                        duration = InfuseLaunchTransition.LOGO_FADE_OUT_DURATION_MS
                        interpolator = InfuseLaunchTransition.createLogoFadeOutInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                removeSplashSafely()
                            }
                        })
                    }
                    activeAnimator = splashFadeOut
                    splashFadeOut.start()

                    delay(InfuseLaunchTransition.LOGO_FADE_OUT_DURATION_MS - 30L)
                    removeSplashSafely()
                    triggerHomeFadeIn()
                }
            } finally {
                activeAnimator?.cancel()
                runCatching { splashScreenView.remove() }
                triggerHomeFadeIn()
            }
        }
    }
}
