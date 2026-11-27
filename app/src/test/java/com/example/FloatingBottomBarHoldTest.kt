package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.ui.screens.FloatingBottomBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FloatingBottomBarHoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun normalState_displaysAllSmallDockItemsAndNoEnlargedCapsule_andTapNavigates() {
        var selectedTab = "Home"

        composeTestRule.setContent {
            MaterialTheme {
                var currentTab by remember { mutableStateOf("Home") }
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { newTab ->
                            currentTab = newTab
                            selectedTab = newTab
                        },
                        primaryColor = Color(0xFFFF9500),
                        isVisible = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Normal state assertions
        composeTestRule.onNodeWithTag("floating_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selected_tab_capsule").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Search").assertIsDisplayed()
        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertDoesNotExist()

        val dockBounds = composeTestRule.onNodeWithTag("floating_bottom_bar").getUnclippedBoundsInRoot()
        val homeBounds = composeTestRule.onNodeWithTag("nav_tab_Home").getUnclippedBoundsInRoot()
        val libraryBounds = composeTestRule.onNodeWithTag("nav_tab_Library").getUnclippedBoundsInRoot()
        val initialCapsuleBounds = composeTestRule.onNodeWithTag("selected_tab_capsule").getUnclippedBoundsInRoot()

        // Selected tab capsule must remain strictly INSIDE the existing dock boundaries
        assertTrue(initialCapsuleBounds.left.value >= dockBounds.left.value)
        assertTrue(initialCapsuleBounds.right.value <= dockBounds.right.value)
        assertTrue(initialCapsuleBounds.top.value > dockBounds.top.value)
        assertTrue(initialCapsuleBounds.bottom.value < dockBounds.bottom.value)
        assertEquals(52f, initialCapsuleBounds.bottom.value - initialCapsuleBounds.top.value, 1f)

        val homeCenterX = (homeBounds.left.value + homeBounds.right.value) / 2f
        val initialCapsuleCenterX = (initialCapsuleBounds.left.value + initialCapsuleBounds.right.value) / 2f
        assertTrue(abs(initialCapsuleCenterX - homeCenterX) < 4f)

        // Quick tap (< 80ms) preserves normal tap navigation and smoothly slides selected_tab_capsule to Library
        composeTestRule.onNodeWithTag("nav_tab_Library").performTouchInput {
            down(center)
            advanceEventTime(40L)
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()

        assertEquals("Library", selectedTab)
        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertDoesNotExist()
        composeTestRule.onNodeWithTag("selected_tab_capsule").assertIsDisplayed()

        val movedCapsuleBounds = composeTestRule.onNodeWithTag("selected_tab_capsule").getUnclippedBoundsInRoot()
        val libraryCenterX = (libraryBounds.left.value + libraryBounds.right.value) / 2f
        val movedCapsuleCenterX = (movedCapsuleBounds.left.value + movedCapsuleBounds.right.value) / 2f
        assertTrue(abs(movedCapsuleCenterX - libraryCenterX) < 4f)
    }

    @Test
    fun holdState_rendersWideCapsuleOverlappingDockWhileKeepingOriginalSmallItemVisible() {
        var currentTabState = "Settings"

        composeTestRule.setContent {
            MaterialTheme {
                var currentTab by remember { mutableStateOf("Settings") }
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = {
                            currentTab = it
                            currentTabState = it
                        },
                        primaryColor = Color(0xFFFF9500),
                        isVisible = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val dockBoundsBefore = composeTestRule.onNodeWithTag("floating_bottom_bar").getUnclippedBoundsInRoot()
        val settingsBoundsBefore = composeTestRule.onNodeWithTag("nav_tab_Settings").getUnclippedBoundsInRoot()

        // Press and hold "Settings" (> 140ms) without releasing
        composeTestRule.onNodeWithTag("nav_tab_Settings").performTouchInput {
            down(center)
            advanceEventTime(220L)
            moveBy(Offset(12f, 0f))
            advanceEventTime(80L)
        }
        composeTestRule.mainClock.advanceTimeBy(450L)
        composeTestRule.waitForIdle()

        // Original small dock items remain visible at their exact original positions
        composeTestRule.onNodeWithTag("nav_tab_Home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Search").assertIsDisplayed()

        // Wide softly rounded Liquid Glass capsule backdrop + specular rim appear simultaneously
        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertIsDisplayed()

        val settingsBoundsDuringHold = composeTestRule.onNodeWithTag("nav_tab_Settings").getUnclippedBoundsInRoot()
        assertEquals(settingsBoundsBefore.left.value, settingsBoundsDuringHold.left.value, 0.5f)
        assertEquals(settingsBoundsBefore.top.value, settingsBoundsDuringHold.top.value, 0.5f)
        assertEquals(settingsBoundsBefore.right.value, settingsBoundsDuringHold.right.value, 0.5f)
        assertEquals(settingsBoundsBefore.bottom.value, settingsBoundsDuringHold.bottom.value, 0.5f)

        val capsuleBounds = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
        val capsuleWidth = capsuleBounds.right.value - capsuleBounds.left.value
        val capsuleHeight = capsuleBounds.bottom.value - capsuleBounds.top.value

        // Must be a wide capsule emerging from and overlapping the dock, NOT a detached small circle
        assertTrue(
            "Expected wide capsule proportions (width=$capsuleWidth > height=$capsuleHeight * 1.35)",
            capsuleWidth > capsuleHeight * 1.35f
        )
        assertTrue(
            "Expected capsule top (${capsuleBounds.top}) above dock top (${dockBoundsBefore.top})",
            capsuleBounds.top < dockBoundsBefore.top
        )
        assertTrue(
            "Expected capsule to overlap the full dock naturally rather than detaching above it (capsuleBottom=${capsuleBounds.bottom}, dockBottom=${dockBoundsBefore.bottom})",
            capsuleBounds.bottom >= dockBoundsBefore.bottom
        )

        val capsuleCenterX = (capsuleBounds.left.value + capsuleBounds.right.value) / 2f
        val settingsCenterX = (settingsBoundsBefore.left.value + settingsBoundsBefore.right.value) / 2f
        assertTrue(
            "Expected capsule center ($capsuleCenterX) anchored to Settings slot ($settingsCenterX)",
            abs(capsuleCenterX - settingsCenterX) < 6f
        )

        // Release finger -> smoothly remove only the enlarged glass capsule and restore normal dock
        composeTestRule.onNodeWithTag("nav_tab_Settings").performTouchInput {
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_Settings").assertIsDisplayed()
        assertEquals("Settings", currentTabState)
    }

    @Test
    fun holdState_onNonSelectedItem_showsWideCapsuleWithoutChangingSelection() {
        var currentTabState = "Home"

        composeTestRule.setContent {
            MaterialTheme {
                var currentTab by remember { mutableStateOf("Home") }
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = {
                            currentTab = it
                            currentTabState = it
                        },
                        primaryColor = Color(0xFFFF9500),
                        isVisible = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val libraryBoundsBefore = composeTestRule.onNodeWithTag("nav_tab_Library").getUnclippedBoundsInRoot()

        composeTestRule.onNodeWithTag("nav_tab_Library").performTouchInput {
            down(center)
            advanceEventTime(260L)
        }
        composeTestRule.mainClock.advanceTimeBy(450L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsDisplayed()

        val libraryBoundsDuringHold = composeTestRule.onNodeWithTag("nav_tab_Library").getUnclippedBoundsInRoot()
        assertEquals(libraryBoundsBefore.left.value, libraryBoundsDuringHold.left.value, 0.5f)
        assertEquals(libraryBoundsBefore.top.value, libraryBoundsDuringHold.top.value, 0.5f)

        composeTestRule.onNodeWithTag("nav_tab_Library").performTouchInput {
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertDoesNotExist()
        assertEquals("Home", currentTabState)
    }

    @Test
    fun holdAndDragContinuously_fromHomeToLibrarySettingsAndSearch_tracksFingerUpdatesSelectionAndNavigatesOnceOnRelease() {
        val navigationCalls = mutableListOf<String>()

        composeTestRule.setContent {
            MaterialTheme {
                var currentTab by remember { mutableStateOf("Home") }
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { newTab ->
                            navigationCalls += newTab
                            currentTab = newTab
                        },
                        primaryColor = Color(0xFFFF9500),
                        isVisible = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val dockBounds = composeTestRule.onNodeWithTag("floating_bottom_bar").getUnclippedBoundsInRoot()
        val homeBoundsBefore = composeTestRule.onNodeWithTag("nav_tab_Home").getUnclippedBoundsInRoot()
        val libraryBoundsBefore = composeTestRule.onNodeWithTag("nav_tab_Library").getUnclippedBoundsInRoot()
        val settingsBoundsBefore = composeTestRule.onNodeWithTag("nav_tab_Settings").getUnclippedBoundsInRoot()
        val searchBoundsBefore = composeTestRule.onNodeWithTag("nav_tab_Search").getUnclippedBoundsInRoot()

        val density = composeTestRule.density.density
        fun toDockLocalX(bounds: androidx.compose.ui.unit.DpRect): Float {
            val centerDp = (bounds.left.value + bounds.right.value) / 2f
            return (centerDp - dockBounds.left.value) * density
        }

        val homeX = toDockLocalX(homeBoundsBefore)
        val libraryX = toDockLocalX(libraryBoundsBefore)
        val settingsX = toDockLocalX(settingsBoundsBefore)
        val searchX = toDockLocalX(searchBoundsBefore)
        val centerY = ((dockBounds.bottom.value - dockBounds.top.value) / 2f) * density

        // 1. Press and hold on Home (> 140ms)
        composeTestRule.onNodeWithTag("nav_tab_Home").performTouchInput {
            down(center)
            advanceEventTime(220L)
        }
        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Home").assertIsSelected()
        val capsuleAtHome = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
        val centerAtHome = (capsuleAtHome.left.value + capsuleAtHome.right.value) / 2f

        // 2. Drag continuously to Library without lifting finger
        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            val steps = 4
            for (i in 1..steps) {
                val x = homeX + (libraryX - homeX) * (i.toFloat() / steps)
                moveTo(Offset(x, centerY))
                advanceEventTime(16L)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsSelected()
        composeTestRule.onNodeWithTag("nav_tab_Home").assertIsNotSelected()
        assertEquals(0, navigationCalls.size)

        val capsuleAtLibrary = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
        val centerAtLibrary = (capsuleAtLibrary.left.value + capsuleAtLibrary.right.value) / 2f
        val libraryExpectedCenter = (libraryBoundsBefore.left.value + libraryBoundsBefore.right.value) / 2f
        assertTrue(
            "Expected capsule to move right onto Library ($centerAtLibrary > $centerAtHome)",
            centerAtLibrary > centerAtHome + 20f
        )
        assertTrue(
            "Expected capsule center ($centerAtLibrary) near Library ($libraryExpectedCenter)",
            abs(centerAtLibrary - libraryExpectedCenter) < 12f
        )

        // 3. Continue dragging to Settings without lifting finger
        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            val steps = 4
            for (i in 1..steps) {
                val x = libraryX + (settingsX - libraryX) * (i.toFloat() / steps)
                moveTo(Offset(x, centerY))
                advanceEventTime(16L)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Settings").assertIsSelected()
        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsNotSelected()
        assertEquals(0, navigationCalls.size)

        val capsuleAtSettings = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
        val centerAtSettings = (capsuleAtSettings.left.value + capsuleAtSettings.right.value) / 2f
        val settingsExpectedCenter = (settingsBoundsBefore.left.value + settingsBoundsBefore.right.value) / 2f
        assertTrue(
            "Expected capsule to move right onto Settings ($centerAtSettings > $centerAtLibrary)",
            centerAtSettings > centerAtLibrary + 20f
        )
        assertTrue(
            "Expected capsule center ($centerAtSettings) near Settings ($settingsExpectedCenter)",
            abs(centerAtSettings - settingsExpectedCenter) < 12f
        )

        // 4. Continue dragging to Search without lifting finger
        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            val steps = 4
            for (i in 1..steps) {
                val x = settingsX + (searchX - settingsX) * (i.toFloat() / steps)
                moveTo(Offset(x, centerY))
                advanceEventTime(16L)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_Search").assertIsSelected()
        composeTestRule.onNodeWithTag("nav_tab_Settings").assertIsNotSelected()
        assertEquals(0, navigationCalls.size)

        val capsuleAtSearch = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
        val centerAtSearch = (capsuleAtSearch.left.value + capsuleAtSearch.right.value) / 2f
        assertTrue(
            "Expected capsule to move right onto Search ($centerAtSearch > $centerAtSettings)",
            centerAtSearch > centerAtSettings + 20f
        )
        assertTrue(
            "Expected capsule to remain overlapping the dock (top=${capsuleAtSearch.top} < ${dockBounds.top}, bottom=${capsuleAtSearch.bottom} >= ${dockBounds.bottom})",
            capsuleAtSearch.top < dockBounds.top && capsuleAtSearch.bottom >= dockBounds.bottom
        )

        // Original small dock items remain visible at their exact original positions underneath
        val homeBoundsDuringDrag = composeTestRule.onNodeWithTag("nav_tab_Home").getUnclippedBoundsInRoot()
        val searchBoundsDuringDrag = composeTestRule.onNodeWithTag("nav_tab_Search").getUnclippedBoundsInRoot()
        assertEquals(homeBoundsBefore.left.value, homeBoundsDuringDrag.left.value, 0.5f)
        assertEquals(searchBoundsBefore.left.value, searchBoundsDuringDrag.left.value, 0.5f)

        // 5. Release finger -> returns to normal dock and navigates to Search exactly once
        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_Search").assertIsSelected()
        assertEquals(listOf("Search"), navigationCalls)
    }

    @Test
    fun holdAndDragCancelled_restoresNormalDockWithoutNavigating() {
        val navigationCalls = mutableListOf<String>()

        composeTestRule.setContent {
            MaterialTheme {
                var currentTab by remember { mutableStateOf("Home") }
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { newTab ->
                            navigationCalls += newTab
                            currentTab = newTab
                        },
                        primaryColor = Color(0xFFFF9500),
                        isVisible = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val dockBounds = composeTestRule.onNodeWithTag("floating_bottom_bar").getUnclippedBoundsInRoot()
        val homeBounds = composeTestRule.onNodeWithTag("nav_tab_Home").getUnclippedBoundsInRoot()
        val libraryBounds = composeTestRule.onNodeWithTag("nav_tab_Library").getUnclippedBoundsInRoot()
        val density = composeTestRule.density.density
        val homeX = ((homeBounds.left.value + homeBounds.right.value) / 2f - dockBounds.left.value) * density
        val libraryX = ((libraryBounds.left.value + libraryBounds.right.value) / 2f - dockBounds.left.value) * density
        val centerY = ((dockBounds.bottom.value - dockBounds.top.value) / 2f) * density

        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            down(Offset(homeX, centerY))
            advanceEventTime(200L)
            moveTo(Offset(libraryX, centerY))
            advanceEventTime(32L)
        }
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsSelected()
        composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()

        // Cancel gesture instead of releasing normally
        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            cancel()
        }
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        assertEquals("Expected no navigation on cancel, got $navigationCalls", emptyList<String>(), navigationCalls)
        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_Home").assertIsSelected()
    }

    @Test
    fun holdEachTabSeparately_andDragContinuouslyWithZeroLag() {
        val navigationCalls = mutableListOf<String>()

        composeTestRule.setContent {
            MaterialTheme {
                var currentTab by remember { mutableStateOf("Home") }
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { newTab ->
                            navigationCalls += newTab
                            currentTab = newTab
                        },
                        primaryColor = Color(0xFFFF9500),
                        isVisible = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val dockBounds = composeTestRule.onNodeWithTag("floating_bottom_bar").getUnclippedBoundsInRoot()
        val tabs = listOf("Home", "Library", "Settings", "Search")
        val tabBounds = tabs.associateWith {
            composeTestRule.onNodeWithTag("nav_tab_$it").getUnclippedBoundsInRoot()
        }
        val density = composeTestRule.density.density
        val centerY = ((dockBounds.bottom.value - dockBounds.top.value) / 2f) * density

        // 1. Hold Home, Library, Settings, and Search separately
        tabs.forEach { tab ->
            val bounds = tabBounds.getValue(tab)
            val tabExpectedCenterDp = (bounds.left.value + bounds.right.value) / 2f

            composeTestRule.onNodeWithTag("nav_tab_$tab").performTouchInput {
                down(center)
                advanceEventTime(140L)
            }
            composeTestRule.mainClock.advanceTimeBy(180L)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("liquid_glass_lens").assertIsDisplayed()
            composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_tab_$tab").assertIsSelected()

            val lensBounds = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
            val lensWidthDp = lensBounds.right.value - lensBounds.left.value
            val minClampedCenter = dockBounds.left.value + lensWidthDp * 0.46f
            val maxClampedCenter = dockBounds.right.value - lensWidthDp * 0.46f
            val expectedClampedCenterDp = tabExpectedCenterDp.coerceIn(minClampedCenter, maxClampedCenter)
            val lensCenterDp = (lensBounds.left.value + lensBounds.right.value) / 2f
            assertTrue(
                "Expected capsule anchored over $tab ($lensCenterDp vs $expectedClampedCenterDp)",
                abs(lensCenterDp - expectedClampedCenterDp) < 4f
            )

            composeTestRule.onNodeWithTag("nav_tab_$tab").performTouchInput {
                up()
            }
            composeTestRule.mainClock.advanceTimeBy(350L)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
            composeTestRule.onNodeWithTag("liquid_glass_capsule_rim").assertDoesNotExist()
        }

        assertEquals(emptyList<String>(), navigationCalls)

        // 2. Drag continuously forward Home -> Library -> Settings -> Search and back to Library
        // Verify ZERO async lag: capsule center and selected tab update synchronously right after moveTo
        fun dockLocalX(tab: String): Float {
            val b = tabBounds.getValue(tab)
            return ((b.left.value + b.right.value) / 2f - dockBounds.left.value) * density
        }

        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            down(Offset(dockLocalX("Home"), centerY))
            advanceEventTime(140L)
        }
        composeTestRule.mainClock.advanceTimeBy(180L)
        composeTestRule.waitForIdle()

        listOf("Library", "Settings", "Search", "Library").forEach { targetTab ->
            val targetX = dockLocalX(targetTab)
            val rawExpectedCenterDp = (tabBounds.getValue(targetTab).left.value + tabBounds.getValue(targetTab).right.value) / 2f

            composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
                moveTo(Offset(targetX, centerY))
                advanceEventTime(16L)
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("nav_tab_$targetTab").assertIsSelected()
            val lensBounds = composeTestRule.onNodeWithTag("liquid_glass_lens").getUnclippedBoundsInRoot()
            val lensWidthDp = lensBounds.right.value - lensBounds.left.value
            val minClampedCenter = dockBounds.left.value + lensWidthDp * 0.46f
            val maxClampedCenter = dockBounds.right.value - lensWidthDp * 0.46f
            val expectedCenterDp = rawExpectedCenterDp.coerceIn(minClampedCenter, maxClampedCenter)
            val lensCenterDp = (lensBounds.left.value + lensBounds.right.value) / 2f
            assertTrue(
                "Expected zero-lag capsule sync over $targetTab ($lensCenterDp vs $expectedCenterDp)",
                abs(lensCenterDp - expectedCenterDp) < 8f
            )
            assertEquals(0, navigationCalls.size)
        }

        // Release on final destination ("Library") -> navigates once
        composeTestRule.onNodeWithTag("floating_bottom_bar").performTouchInput {
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("liquid_glass_lens").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_Library").assertIsSelected()
        assertEquals(listOf("Library"), navigationCalls)
    }

    @Test
    fun playerLiquidGlassHudPills_renderVolumeBrightnessAnd2xSpeedWithLiquidGlassEffects() {
        com.example.ui.components.LiquidGlassDockEffects.prewarmHudPillEffects(
            blurRadiusPx = 16f,
            defaultWidthPx = 220f,
            heightPx = 76f,
            cornerRadiusPx = 38f
        )

        val effectFirst = com.example.ui.components.LiquidGlassDockEffects.createHudPillBackdropEffect(
            blurRadiusPx = 16f,
            widthPx = 220f,
            heightPx = 76f,
            cornerRadiusPx = 38f,
            lightPhase = 0.25f
        )
        val effectCached = com.example.ui.components.LiquidGlassDockEffects.createHudPillBackdropEffect(
            blurRadiusPx = 16f,
            widthPx = 220f,
            heightPx = 76f,
            cornerRadiusPx = 38f,
            lightPhase = 0.25f
        )
        org.junit.Assert.assertNotNull("Expected non-null HUD pill RenderEffect on API 36", effectFirst)
        org.junit.Assert.assertSame("Expected O(1) cached RenderEffect reuse without shader rebuild", effectFirst, effectCached)

        var hudModeState by mutableStateOf("volume")
        var volumeSeed by mutableStateOf(0.75f)
        val dummyBackdropBmp = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
        val dummyBackdropImg = dummyBackdropBmp.asImageBitmap()

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.screens.PlayerLiquidGlassHudPill(
                        testTag = "player_ios_hud_pill",
                        backdropImageProvider = { dummyBackdropImg },
                        backdropSize = { androidx.compose.ui.unit.IntSize(1080, 720) },
                        reflectionSeed = if (hudModeState == "volume") volumeSeed else 0.40f
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .align(Alignment.Center)
                        ) {
                            androidx.compose.material3.Text(
                                text = if (hudModeState == "volume") "${(volumeSeed * 100).toInt()}%" else "40%",
                                color = Color.White
                            )
                        }
                    }

                    com.example.ui.screens.PlayerLiquidGlassHudPill(
                        testTag = "player_2x_speed_hud_pill",
                        backdropImageProvider = { dummyBackdropImg },
                        backdropSize = { androidx.compose.ui.unit.IntSize(1080, 720) },
                        reflectionSeed = 0.65f
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .align(Alignment.Center)
                        ) {
                            androidx.compose.material3.Text(
                                text = "2×",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_ios_hud_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_2x_speed_hud_pill").assertIsDisplayed()

        val volBounds = composeTestRule.onNodeWithTag("player_ios_hud_pill").getUnclippedBoundsInRoot()
        val speedBounds = composeTestRule.onNodeWithTag("player_2x_speed_hud_pill").getUnclippedBoundsInRoot()
        val volWidth = volBounds.right.value - volBounds.left.value
        val speedWidth = speedBounds.right.value - speedBounds.left.value
        assertEquals(38f, volBounds.bottom.value - volBounds.top.value, 1f)
        assertEquals(38f, speedBounds.bottom.value - speedBounds.top.value, 1f)
        assertTrue("Expected compact Volume/Brightness pill width (< 120dp), got $volWidth", volWidth in 30f..120f)
        assertTrue("Expected compact 2x Speed pill width (< 120dp), got $speedWidth", speedWidth in 30f..120f)

        // Simulate rapid Volume drag and Brightness mode switch (preserves exact pill height & stability)
        volumeSeed = 0.90f
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.waitForIdle()
        hudModeState = "brightness"
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.waitForIdle()

        val brightnessBounds = composeTestRule.onNodeWithTag("player_ios_hud_pill").getUnclippedBoundsInRoot()
        assertEquals(38f, brightnessBounds.bottom.value - brightnessBounds.top.value, 1f)
        composeTestRule.onNodeWithTag("player_ios_hud_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_2x_speed_hud_pill").assertIsDisplayed()
        dummyBackdropBmp.recycle()
    }

    @Test
    fun youtubeSeekOverlay_sequentialForwardArrowAnimation_movesAndResetsCleanly() {
        composeTestRule.mainClock.autoAdvance = false

        var triggerKey by mutableStateOf(1)

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.screens.YouTubeSeekOverlay(
                        isForward = true,
                        seekSeconds = 10,
                        triggerKey = triggerKey
                    )
                }
            }
        }

        // t = 0: All 3 arrows visible together at initial resting positions, +10s text unchanged
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("seek_arrow_forward_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seek_arrow_forward_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seek_arrow_forward_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seek_arrow_forward_0").let { n ->
            assertEquals(28f, n.getUnclippedBoundsInRoot().right.value - n.getUnclippedBoundsInRoot().left.value, 0.5f)
        }

        val restLeft0 = composeTestRule.onNodeWithTag("seek_arrow_forward_0").getUnclippedBoundsInRoot().left.value
        val restLeft1 = composeTestRule.onNodeWithTag("seek_arrow_forward_1").getUnclippedBoundsInRoot().left.value
        val restLeft2 = composeTestRule.onNodeWithTag("seek_arrow_forward_2").getUnclippedBoundsInRoot().left.value
        assertTrue(restLeft1 > restLeft0 && restLeft2 > restLeft1)

        // t = ~180ms: Arrow 0 is moving right while Arrow 2 has NOT started moving yet
        composeTestRule.mainClock.advanceTimeBy(180L)
        val midLeft0 = composeTestRule.onNodeWithTag("seek_arrow_forward_0").getUnclippedBoundsInRoot().left.value
        val midLeft2 = composeTestRule.onNodeWithTag("seek_arrow_forward_2").getUnclippedBoundsInRoot().left.value
        assertTrue("Expected first arrow to move forward (right): $midLeft0 > $restLeft0", midLeft0 > restLeft0 + 2f)
        assertEquals("Expected third arrow to still be at rest while first moves", restLeft2, midLeft2, 0.5f)

        // t = ~360ms: Arrow 1 is now moving right while Arrow 2 is still at rest
        composeTestRule.mainClock.advanceTimeBy(180L)
        val step2Left1 = composeTestRule.onNodeWithTag("seek_arrow_forward_1").getUnclippedBoundsInRoot().left.value
        val step2Left2 = composeTestRule.onNodeWithTag("seek_arrow_forward_2").getUnclippedBoundsInRoot().left.value
        assertTrue("Expected second arrow to move forward after first: $step2Left1 > $restLeft1", step2Left1 > restLeft1 + 2f)
        assertEquals("Expected third arrow still at rest before stagger", restLeft2, step2Left2, 0.5f)

        // t = ~550ms: Arrow 2 is now moving forward
        composeTestRule.mainClock.advanceTimeBy(190L)
        val step3Left2 = composeTestRule.onNodeWithTag("seek_arrow_forward_2").getUnclippedBoundsInRoot().left.value
        assertTrue("Expected third arrow to move forward sequentially: $step3Left2 > $restLeft2", step3Left2 > restLeft2 + 2f)

        // t = ~900ms: Animation finished & smoothly reset back to initial resting positions
        composeTestRule.mainClock.advanceTimeBy(350L)
        val resetLeft0 = composeTestRule.onNodeWithTag("seek_arrow_forward_0").getUnclippedBoundsInRoot().left.value
        val resetLeft1 = composeTestRule.onNodeWithTag("seek_arrow_forward_1").getUnclippedBoundsInRoot().left.value
        val resetLeft2 = composeTestRule.onNodeWithTag("seek_arrow_forward_2").getUnclippedBoundsInRoot().left.value
        assertEquals(restLeft0, resetLeft0, 0.5f)
        assertEquals(restLeft1, resetLeft1, 0.5f)
        assertEquals(restLeft2, resetLeft2, 0.5f)
        composeTestRule.onNodeWithTag("seek_arrow_forward_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seek_arrow_forward_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seek_arrow_forward_2").assertIsDisplayed()

        // Subsequent double-tap increments triggerKey and runs sequentially again
        triggerKey = 2
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(180L)
        val repeatLeft0 = composeTestRule.onNodeWithTag("seek_arrow_forward_0").getUnclippedBoundsInRoot().left.value
        assertTrue("Expected arrow 0 to travel again on next triggerKey", repeatLeft0 > restLeft0 + 2f)
    }
}
