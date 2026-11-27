package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * High-performance video frame thumbnail extractor for seekbar scrubbing previews.
 * Reuses a single [MediaMetadataRetriever] per media [Uri] on a dedicated background thread
 * and caches scaled 16:9 thumbnails in an in-memory [LruCache].
 */
class VideoFramePreviewExtractor(
    private val context: Context,
    private val videoUri: Uri
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val extractorDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var retriever: MediaMetadataRetriever? = null
    private var isInitialized = false
    private var initFailed = false

    // Cache up to 48 scaled preview frames keyed by 1-second timestamp buckets
    private val frameCache = object : LruCache<Long, Bitmap>(48) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    @Volatile
    private var lastExtractedBitmap: Bitmap? = null

    private fun ensureInitialized(): MediaMetadataRetriever? {
        if (initFailed) return null
        retriever?.let { return it }

        if (videoUri == Uri.EMPTY) {
            initFailed = true
            return null
        }

        val newRetriever = MediaMetadataRetriever()
        return try {
            val scheme = videoUri.scheme?.lowercase()
            when (scheme) {
                "content", "android.resource" -> {
                    newRetriever.setDataSource(context, videoUri)
                }
                "file" -> {
                    val path = videoUri.path
                    if (!path.isNullOrBlank()) {
                        newRetriever.setDataSource(path)
                    } else {
                        newRetriever.setDataSource(context, videoUri)
                    }
                }
                "http", "https" -> {
                    newRetriever.setDataSource(videoUri.toString(), HashMap<String, String>())
                }
                else -> {
                    newRetriever.setDataSource(context, videoUri)
                }
            }
            retriever = newRetriever
            isInitialized = true
            newRetriever
        } catch (e: Exception) {
            try {
                newRetriever.release()
            } catch (_: Exception) {
            }
            initFailed = true
            null
        }
    }

    /**
     * Extracts a scaled preview [Bitmap] at [positionMs].
     * Uses 1-second bucketing and [MediaMetadataRetriever.OPTION_CLOSEST_SYNC] for rapid scrubbing,
     * with an optional [precise] pass using [MediaMetadataRetriever.OPTION_CLOSEST].
     */
    suspend fun getFrameAtPosition(
        positionMs: Long,
        targetWidthPx: Int = 320,
        targetHeightPx: Int = 180,
        precise: Boolean = false
    ): Bitmap? = withContext(extractorDispatcher) {
        val clampedMs = positionMs.coerceAtLeast(0L)
        val bucketKey = if (precise) {
            (clampedMs / 500L) * 500L + 1L // Odd key distinguishes precise frames
        } else {
            (clampedMs / 1000L) * 1000L
        }

        frameCache.get(bucketKey)?.let { cached ->
            if (!cached.isRecycled) {
                lastExtractedBitmap = cached
                return@withContext cached
            }
        }

        val activeRetriever = ensureInitialized() ?: return@withContext lastExtractedBitmap
        val timeUs = clampedMs * 1000L
        val option = if (precise) {
            MediaMetadataRetriever.OPTION_CLOSEST
        } else {
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        }

        val extracted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activeRetriever.getScaledFrameAtTime(
                    timeUs,
                    option,
                    targetWidthPx,
                    targetHeightPx
                )
            } else {
                activeRetriever.getFrameAtTime(timeUs, option)?.let { raw ->
                    val scaled = Bitmap.createScaledBitmap(raw, targetWidthPx, targetHeightPx, true)
                    if (scaled !== raw) {
                        raw.recycle()
                    }
                    scaled
                }
            }
        } catch (_: Exception) {
            null
        } ?: try {
            // Fallback to sync frame if precise extraction failed
            if (precise && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activeRetriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidthPx,
                    targetHeightPx
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        if (extracted != null) {
            frameCache.put(bucketKey, extracted)
            lastExtractedBitmap = extracted
            extracted
        } else {
            lastExtractedBitmap
        }
    }

    fun getLastCachedFrame(): Bitmap? = lastExtractedBitmap

    fun release() {
        val currentRetriever = retriever
        retriever = null
        isInitialized = false
        try {
            currentRetriever?.release()
        } catch (_: Exception) {
        }
        frameCache.evictAll()
    }
}
