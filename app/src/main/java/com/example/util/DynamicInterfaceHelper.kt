package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Ambient palette holding subtle, dark-mode tuned colors derived from media artwork.
 */
data class AmbientPalette(
    val primary: Color = Color.Transparent,
    val secondary: Color = Color.Transparent,
    val isDynamic: Boolean = false
) {
    companion object {
        val StaticDark = AmbientPalette(
            primary = Color.Transparent,
            secondary = Color.Transparent,
            isDynamic = false
        )
    }
}

object DynamicInterfaceHelper {
    private const val TAG = "DynamicInterface"
    private val paletteCache = LruCache<String, Pair<Color, Color>>(60)

    /**
     * Synchronously checks cache or returns null if not yet extracted.
     */
    fun getCachedColors(artworkKey: String?): Pair<Color, Color>? {
        if (artworkKey.isNullOrBlank()) return null
        return paletteCache.get(artworkKey)
    }

    /**
     * Extracts dominant and secondary ambient colors from an artwork URL or local file path.
     * Operates off the main thread and downsamples image to 32x32 for instantaneous processing.
     */
    suspend fun extractArtworkColors(context: Context, artworkUrl: String?): Pair<Color, Color> = withContext(Dispatchers.IO) {
        if (artworkUrl.isNullOrBlank()) {
            return@withContext Pair(Color(0xFF2C3440), Color(0xFF1E242B))
        }

        // Check memory cache
        paletteCache.get(artworkUrl)?.let { return@withContext it }

        var bitmap: Bitmap? = null
        try {
            if (artworkUrl.startsWith("data:image")) {
                val base64Data = artworkUrl.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else if (artworkUrl.startsWith("/") || artworkUrl.startsWith("file://")) {
                val path = artworkUrl.removePrefix("file://")
                val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                bitmap = BitmapFactory.decodeFile(path, options)
            } else {
                val imageLoader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(32, 32)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                }
            }

            if (bitmap != null) {
                val extracted = sampleCinematicColors(bitmap)
                paletteCache.put(artworkUrl, extracted)
                return@withContext extracted
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting colors from artwork: ${e.message}")
        }

        // Fallback subtle cinematic tint based on string hash
        val hash = abs(artworkUrl.hashCode())
        val hue = (hash % 360).toFloat()
        val hsv1 = floatArrayOf(hue, 0.65f, 0.75f)
        val hsv2 = floatArrayOf((hue + 40f) % 360f, 0.55f, 0.65f)
        val fallback = Pair(
            Color(android.graphics.Color.HSVToColor(hsv1)),
            Color(android.graphics.Color.HSVToColor(hsv2))
        )
        paletteCache.put(artworkUrl, fallback)
        fallback
    }

    /**
     * Efficiently samples pixels from a small bitmap to find vibrant, cinematic dark-mode colors.
     */
    private fun sampleCinematicColors(bitmap: Bitmap): Pair<Color, Color> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return Pair(Color(0xFF3B4856), Color(0xFF232B34))
        }

        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 16).coerceAtLeast(1)

        val hueBins = FloatArray(12) // 12 bins for 360 degrees (30 deg each)
        val binColors = IntArray(12)
        val binSaturation = FloatArray(12)
        val binCount = IntArray(12)

        val hsv = FloatArray(3)
        var totalNonDarkPixels = 0

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) continue

                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]

                // Filter out near-black, washed-out whites, and low-saturation grays
                if (value < 0.12f || (value > 0.92f && saturation < 0.15f) || saturation < 0.12f) {
                    continue
                }

                totalNonDarkPixels++
                val binIdx = ((hue / 30f).toInt()).coerceIn(0, 11)
                binCount[binIdx]++
                binSaturation[binIdx] += saturation
                // Weight score by vibrancy and mid-level brightness
                val weight = saturation * (1.0f - abs(value - 0.55f))
                hueBins[binIdx] += weight
                binColors[binIdx] = pixel
            }
        }

        // Find primary and secondary color bins
        var bestIdx = -1
        var bestScore = -1f
        var secondIdx = -1
        var secondScore = -1f

        for (i in 0 until 12) {
            val score = hueBins[i]
            if (score > bestScore) {
                secondScore = bestScore
                secondIdx = bestIdx
                bestScore = score
                bestIdx = i
            } else if (score > secondScore) {
                secondScore = score
                secondIdx = i
            }
        }

        if (bestIdx != -1 && bestScore > 0f) {
            val primaryHue = bestIdx * 30f + 15f
            val primaryColor = Color(
                android.graphics.Color.HSVToColor(
                    floatArrayOf(primaryHue, 0.72f, 0.78f)
                )
            )

            val secondaryHue = if (secondIdx != -1 && secondScore > 0f) {
                secondIdx * 30f + 15f
            } else {
                (primaryHue + 45f) % 360f
            }
            val secondaryColor = Color(
                android.graphics.Color.HSVToColor(
                    floatArrayOf(secondaryHue, 0.60f, 0.68f)
                )
            )

            return Pair(primaryColor, secondaryColor)
        }

        // Default cinematic cool slate palette
        return Pair(Color(0xFF384B60), Color(0xFF223040))
    }
}

/**
 * Composable hook to retrieve and animate the dynamic ambient palette derived from media artwork.
 * Returns a static empty palette when Dynamic Interface is turned OFF.
 */
@Composable
fun rememberDynamicAmbientPalette(
    artworkUrl: String?,
    isDynamicEnabled: Boolean,
    fallbackColor: Color = Color.Transparent
): AmbientPalette {
    val context = LocalContext.current

    var rawPalette by remember(artworkUrl, isDynamicEnabled) {
        val cached = if (isDynamicEnabled) DynamicInterfaceHelper.getCachedColors(artworkUrl) else null
        mutableStateOf(cached)
    }

    LaunchedEffect(artworkUrl, isDynamicEnabled) {
        if (isDynamicEnabled && !artworkUrl.isNullOrBlank()) {
            val extracted = DynamicInterfaceHelper.extractArtworkColors(context, artworkUrl)
            rawPalette = extracted
        } else {
            rawPalette = null
        }
    }

    val targetPrimary = if (isDynamicEnabled && rawPalette != null) {
        rawPalette!!.first
    } else if (isDynamicEnabled && fallbackColor != Color.Transparent) {
        fallbackColor
    } else {
        Color.Transparent
    }

    val targetSecondary = if (isDynamicEnabled && rawPalette != null) {
        rawPalette!!.second
    } else {
        Color.Transparent
    }

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "dynamic_primary_anim"
    )

    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "dynamic_secondary_anim"
    )

    return remember(animatedPrimary, animatedSecondary, isDynamicEnabled) {
        AmbientPalette(
            primary = animatedPrimary,
            secondary = animatedSecondary,
            isDynamic = isDynamicEnabled && (animatedPrimary != Color.Transparent)
        )
    }
}

/**
 * Composable background overlay layer that paints subtle, dark, cinematic ambient gradients
 * derived from the dynamic artwork palette.
 */
@Composable
fun DynamicAmbientBackgroundLayer(
    palette: AmbientPalette,
    modifier: Modifier = Modifier
) {
    if (!palette.isDynamic || palette.primary == Color.Transparent) {
        // Dynamic Interface is OFF: Completely static solid dark canvas
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top-Left / Top-Center prominent ambient glow
        Box(
            modifier = Modifier
                .size(520.dp)
                .offset(x = (-80).dp, y = (-120).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = 0.30f),
                            palette.primary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Top-Right secondary ambient glow
        Box(
            modifier = Modifier
                .size(440.dp)
                .offset(x = 160.dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.secondary.copy(alpha = 0.22f),
                            palette.secondary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Smooth vertical ambient wash from top header down to mid screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = 0.18f),
                            palette.secondary.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 1600f
                    )
                )
        )
    }
}
