package com.example.util

import android.util.Log
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.InfuseApplication
import kotlinx.coroutines.delay

object PosterCardUIHelper {

    /**
     * Formats the subtitle text displayed directly below the Show Title.
     * Replaces generic "TV Series" with iOS Infuse-style Season info.
     */
    fun getShowSubtitleText(seasonCount: Int, currentSeasonNumber: Int? = 1): String {
        return if (seasonCount > 1) {
            "$seasonCount Seasons"
        } else {
            "Season ${currentSeasonNumber ?: 1}"
        }
    }

    /**
     * Ensures any TMDb path or URL is properly formatted with the official TMDB image CDN URL.
     */
    fun formatTmdbUrl(url: String?, size: String = "w780"): String {
        return TmdbImageUrlBuilder.buildPosterUrl(url, size)
    }

    /**
     * Ensures any TMDb backdrop or header path is properly formatted with HD w1280 resolution.
     */
    fun formatBackdropUrl(url: String?, size: String = "w1280"): String {
        return TmdbImageUrlBuilder.buildBackdropUrl(url, size)
    }
}

fun String?.toTmdbUrl(size: String = "w780"): String = PosterCardUIHelper.formatTmdbUrl(this, size)

/**
 * Hardware-friendly diagonal shimmer effect drawn in the draw phase without triggering layout recompositions.
 */
fun Modifier.infuseShimmer(
    enabled: Boolean = true,
    accentColor: Color = Color.White,
    baseColor: Color = Color(0xFF1A1B1E),
    highlightAlpha: Float = 0.11f
): Modifier = composed {
    if (!enabled) return@composed this

    val transition = rememberInfiniteTransition(label = "infuse_shimmer_transition")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "infuse_shimmer_progress"
    )

    this.drawWithCache {
        val width = size.width.coerceAtLeast(1f)
        val height = size.height.coerceAtLeast(1f)
        val bandWidth = (width + height) * 0.55f
        val shimmerHighlight = Color.White.copy(alpha = highlightAlpha)
        val subtleAccent = accentColor.copy(alpha = highlightAlpha * 0.45f)

        onDrawWithContent {
            drawRect(color = baseColor)
            val startX = progress * (width + bandWidth) - bandWidth
            val startY = progress * (height + bandWidth) - bandWidth
            val brush = Brush.linearGradient(
                colors = listOf(
                    baseColor,
                    subtleAccent,
                    shimmerHighlight,
                    subtleAccent,
                    baseColor
                ),
                start = Offset(startX, startY),
                end = Offset(startX + bandWidth, startY + bandWidth)
            )
            drawRect(brush = brush)
            drawContent()
        }
    }
}

/**
 * Reusable iOS Infuse-inspired dark liquid-glass shimmer skeleton surface.
 */
@Composable
fun InfuseShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    accentColor: Color = Color.White,
    showBorder: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(shape)
            .infuseShimmer(
                enabled = true,
                accentColor = accentColor,
                baseColor = Color(0xFF1B1C20),
                highlightAlpha = 0.12f
            )
            .then(
                if (showBorder) {
                    Modifier.border(0.75.dp, Color.White.copy(alpha = 0.07f), shape)
                } else {
                    Modifier
                }
            )
    )
}

/**
 * Full skeleton placeholder card matching MovieGridCard and ShowStackCard dimensions and typography rhythm.
 */
@Composable
fun MovieGridSkeletonCard(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    isShowStack: Boolean = false,
    primaryColor: Color = Color(0xFFFF9500)
) {
    val cardShape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("movie_grid_skeleton_card"),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
    ) {
        if (isShowStack) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isCompact) 4.dp else 8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C1C).copy(alpha = 0.35f)),
                    shape = cardShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(2f / 3f)
                        .align(Alignment.TopCenter)
                        .offset(y = if (isCompact) (-4).dp else (-8).dp)
                ) {}
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C1C).copy(alpha = 0.5f)),
                    shape = cardShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(2f / 3f)
                        .align(Alignment.TopCenter)
                        .offset(y = if (isCompact) (-2).dp else (-4).dp)
                ) {}
                InfuseShimmerBox(
                    shape = cardShape,
                    accentColor = primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .align(Alignment.TopCenter)
                )
            }
        } else {
            InfuseShimmerBox(
                shape = cardShape,
                accentColor = primaryColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
        }

        // Title & metadata skeleton lines
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
        ) {
            InfuseShimmerBox(
                shape = RoundedCornerShape(4.dp),
                accentColor = primaryColor,
                showBorder = false,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(if (isCompact) 11.dp else 14.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfuseShimmerBox(
                    shape = RoundedCornerShape(4.dp),
                    accentColor = primaryColor,
                    showBorder = false,
                    modifier = Modifier
                        .width(if (isCompact) 36.dp else 48.dp)
                        .height(if (isCompact) 9.dp else 11.dp)
                )
                InfuseShimmerBox(
                    shape = RoundedCornerShape(4.dp),
                    accentColor = primaryColor,
                    showBorder = false,
                    modifier = Modifier
                        .width(if (isCompact) 26.dp else 32.dp)
                        .height(if (isCompact) 9.dp else 11.dp)
                )
            }
        }
    }
}

@Composable
fun rememberTmdbImageRequest(
    rawUrl: String?,
    size: String = "w780"
): ImageRequest? {
    val context = LocalContext.current
    val formattedUrl = remember(rawUrl, size) {
        if (size == "w1280") {
            PosterCardUIHelper.formatBackdropUrl(rawUrl, size)
        } else {
            PosterCardUIHelper.formatTmdbUrl(rawUrl, size)
        }
    }
    return remember(formattedUrl, context) {
        if (formattedUrl.isBlank()) null
        else {
            ImageRequest.Builder(context)
                .data(formattedUrl)
                .memoryCacheKey(formattedUrl)
                .diskCacheKey(formattedUrl)
                .crossfade(180)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        }
    }
}

/**
 * Shared poster rendering component ensuring complete poster visibility (ContentScale.Fit)
 * without cropping top, bottom, left, right, titles, or logos, and displaying a smooth
 * shimmer skeleton while remote TMDB artwork is loading.
 * Automatically recovers from temporary mobile data / Wi-Fi switchover failures without restarting.
 */
@Composable
fun SharedPosterImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    showShimmerWhileLoading: Boolean = true
) {
    val context = LocalContext.current
    val networkGeneration by InfuseApplication.networkGeneration.collectAsState()

    if (model == null) return

    Box(
        modifier = modifier.background(Color.Transparent),
        contentAlignment = alignment
    ) {
        when (model) {
            is ImageRequest -> {
                val baseData = model.data
                var isImageLoading by remember(baseData) { mutableStateOf(true) }
                var hasLoadError by remember(baseData) { mutableStateOf(false) }
                var retryAttempt by remember(baseData) { mutableIntStateOf(0) }

                // Automatically retry when network changes (e.g. Wi-Fi <-> Mobile Data) or once after transient error
                LaunchedEffect(networkGeneration) {
                    if (hasLoadError && networkGeneration > 0) {
                        hasLoadError = false
                        isImageLoading = true
                        retryAttempt++
                    }
                }
                LaunchedEffect(hasLoadError, retryAttempt) {
                    if (hasLoadError && retryAttempt < 2) {
                        delay(1500L * (retryAttempt + 1))
                        if (hasLoadError) {
                            hasLoadError = false
                            isImageLoading = true
                            retryAttempt++
                        }
                    }
                }

                val effectiveRequest = remember(model, retryAttempt) {
                    if (retryAttempt == 0) {
                        model
                    } else {
                        model.newBuilder()
                            .setParameter("retry_attempt", retryAttempt, memoryCacheKey = null)
                            .build()
                    }
                }

                if (showShimmerWhileLoading && isImageLoading) {
                    InfuseShimmerBox(
                        shape = RoundedCornerShape(0.dp),
                        showBorder = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AsyncImage(
                    model = effectiveRequest,
                    contentDescription = contentDescription,
                    imageLoader = coil.Coil.imageLoader(context),
                    onLoading = { isImageLoading = true },
                    onSuccess = {
                        isImageLoading = false
                        hasLoadError = false
                    },
                    onError = { state ->
                        isImageLoading = false
                        hasLoadError = true
                        if (com.example.BuildConfig.DEBUG) {
                            Log.e(
                                "TMDB_POSTER_LOAD",
                                "Poster ImageRequest failed for data='${model.data}' (retry=$retryAttempt): ${state.result.throwable.javaClass.simpleName}: ${state.result.throwable.message}",
                                state.result.throwable
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    alignment = alignment
                )
            }
            is android.graphics.Bitmap -> {
                Image(
                    bitmap = model.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    alignment = alignment
                )
            }
            else -> {
                val rawString = model.toString().trim()
                if (rawString.isBlank()) return@Box

                val base64Bitmap = remember(rawString) {
                    if (rawString.startsWith("data:image") || (rawString.length > 200 && !rawString.startsWith("http"))) {
                        try {
                            val cleanData = if (rawString.contains(",")) rawString.substringAfter(",") else rawString
                            val decodedBytes = Base64.decode(cleanData, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                if (base64Bitmap != null) {
                    Image(
                        bitmap = base64Bitmap.asImageBitmap(),
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                        alignment = alignment
                    )
                } else {
                    val finalUrl = remember(rawString) {
                        PosterCardUIHelper.formatTmdbUrl(rawString, "w780")
                    }
                    if (finalUrl.isNotBlank()) {
                        var isImageLoading by remember(finalUrl) { mutableStateOf(true) }
                        var hasLoadError by remember(finalUrl) { mutableStateOf(false) }
                        var retryAttempt by remember(finalUrl) { mutableIntStateOf(0) }

                        LaunchedEffect(networkGeneration) {
                            if (hasLoadError && networkGeneration > 0) {
                                hasLoadError = false
                                isImageLoading = true
                                retryAttempt++
                            }
                        }
                        LaunchedEffect(hasLoadError, retryAttempt) {
                            if (hasLoadError && retryAttempt < 2) {
                                delay(1500L * (retryAttempt + 1))
                                if (hasLoadError) {
                                    hasLoadError = false
                                    isImageLoading = true
                                    retryAttempt++
                                }
                            }
                        }

                        if (showShimmerWhileLoading && isImageLoading) {
                            InfuseShimmerBox(
                                shape = RoundedCornerShape(0.dp),
                                showBorder = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AsyncImage(
                            model = remember(finalUrl, context, retryAttempt) {
                                ImageRequest.Builder(context)
                                    .data(finalUrl)
                                    .memoryCacheKey(finalUrl)
                                    .diskCacheKey(finalUrl)
                                    .setParameter("retry_attempt", retryAttempt, memoryCacheKey = null)
                                    .crossfade(180)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build()
                            },
                            contentDescription = contentDescription,
                            imageLoader = coil.Coil.imageLoader(context),
                            onLoading = { isImageLoading = true },
                            onSuccess = {
                                isImageLoading = false
                                hasLoadError = false
                            },
                            onError = { state ->
                                isImageLoading = false
                                hasLoadError = true
                                if (com.example.BuildConfig.DEBUG) {
                                    Log.e(
                                        "TMDB_POSTER_LOAD",
                                        "Poster load failed for url='$finalUrl' (retry=$retryAttempt): ${state.result.throwable.javaClass.simpleName}: ${state.result.throwable.message}",
                                        state.result.throwable
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale,
                            alignment = alignment
                        )
                    }
                }
            }
        }
    }
}


