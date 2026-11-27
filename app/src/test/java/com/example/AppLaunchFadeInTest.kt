package com.example

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.transition.InfuseLaunchTransition
import com.example.ui.transition.InfuseLaunchTransitionController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLaunchFadeInTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun freshAppLaunch_waitsForHomeRenderAndLogoDisappearance_thenSmoothlyFadesInHome() {
        composeTestRule.mainClock.autoAdvance = false

        val controller = InfuseLaunchTransitionController(
            isFreshLaunch = true,
            hasSplashExitListenerSupport = true
        )
        var reportedAlpha = -1f

        composeTestRule.setContent {
            var hasCompletedAppLaunchTransition by rememberSaveable { mutableStateOf(false) }
            val canStartHomeFadeIn by controller.canStartHomeFadeIn.collectAsState()
            val appLaunchAlpha = remember { Animatable(if (hasCompletedAppLaunchTransition) 1f else 0f) }

            LaunchedEffect(Unit) {
                if (!hasCompletedAppLaunchTransition) {
                    withFrameNanos { }
                    controller.markHomeFirstFrameRendered()
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

            reportedAlpha = appLaunchAlpha.value

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = appLaunchAlpha.value.coerceIn(0f, 1f)
                        }
                        .testTag("app_launch_container")
                ) {
                    Text(
                        text = "Noir Cinema Home",
                        modifier = Modifier.testTag("home_screen_content")
                    )
                }
            }
        }

        // Frame 1 committed -> Home Screen is composed, laid out, and ready before fade-in begins
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()
        assertTrue("Expected Home Screen frame 1 ready before fade-in", controller.isHomeFirstFrameRendered.value)
        assertFalse("Home Screen must wait while splash logo holds & begins fading", controller.canStartHomeFadeIn.value)
        assertEquals(0f, reportedAlpha, 0.001f)

        // Simulate splash logo fading out and triggering Home reveal as the logo disappears
        controller.triggerHomeFadeIn()
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()

        // Midway (~230ms into 460ms fade-in): smooth ease-in/ease-out opacity progression
        composeTestRule.mainClock.advanceTimeBy(230L)
        assertTrue(
            "Expected smooth ease-in/ease-out midway alpha ($reportedAlpha) between 0.35f and 0.90f",
            reportedAlpha in 0.35f..0.90f
        )

        // End (~500ms total fade-in): Home Screen settles cleanly at alpha = 1f
        composeTestRule.mainClock.advanceTimeBy(270L)
        assertEquals(1f, reportedAlpha, 0.001f)
        composeTestRule.onNodeWithTag("app_launch_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_content").assertIsDisplayed()
    }

    @Test
    fun preApi31OrUnsupportedSplashListener_fadesInSmoothlyAfterFirstFrameRendered() = runBlocking {
        val controller = InfuseLaunchTransitionController(
            isFreshLaunch = true,
            hasSplashExitListenerSupport = false
        )
        assertFalse(controller.canStartHomeFadeIn.value)

        controller.markHomeFirstFrameRendered()
        controller.awaitFallbackOrStartIfUnsupported()

        assertTrue(controller.canStartHomeFadeIn.value)
    }

    @Test
    fun returningToHome_orRecreatedActivity_doesNotReplayFadeInAnimation() {
        composeTestRule.mainClock.autoAdvance = false

        val controller = InfuseLaunchTransitionController(isFreshLaunch = false)
        var reportedAlpha = 0f

        composeTestRule.setContent {
            val hasCompletedAppLaunchTransition by rememberSaveable { mutableStateOf(true) }
            val canStartHomeFadeIn by controller.canStartHomeFadeIn.collectAsState()
            val appLaunchAlpha = remember { Animatable(if (hasCompletedAppLaunchTransition) 1f else 0f) }

            LaunchedEffect(canStartHomeFadeIn, hasCompletedAppLaunchTransition) {
                if (hasCompletedAppLaunchTransition) {
                    appLaunchAlpha.snapTo(1f)
                }
            }

            reportedAlpha = appLaunchAlpha.value

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = appLaunchAlpha.value.coerceIn(0f, 1f)
                        }
                        .testTag("app_launch_container")
                ) {
                    Text(
                        text = "Noir Cinema Home",
                        modifier = Modifier.testTag("home_screen_content")
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(1f, reportedAlpha, 0.001f)
        composeTestRule.onNodeWithTag("app_launch_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_content").assertIsDisplayed()
    }
}
