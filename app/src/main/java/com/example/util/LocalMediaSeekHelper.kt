package com.example.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.File

/**
 * Robust extraction & seekability bridge for Media3 / ExoPlayer.
 *
 * For local Gallery content:// and file:// videos where native container extractors
 * emit an Unseekable SeekMap (e.g., unindexed fMP4, edit-list MP4, cue-less MKV),
 * this helper provides container-accurate timestamp SeekMaps anchored at valid container
 * boundaries (position = 0L) so that ordinary player.seekTo(targetMs) operations seek
 * smoothly across any distance (including far seeks) without getting reset to 0ms or
 * causing Matroska/EBML VarintReader desynchronization ("No valid varint length mask found").
 */
object LocalMediaSeekHelper {

    private const val TAG = "LocalMediaSeekHelper"

    /**
     * Creates an ExtractorsFactory equipped with native extractor flag tuning
     * and automatic fallback seekability wrapper.
     */
    fun createExtractorsFactory(context: Context): ExtractorsFactory {
        val baseFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMatroskaExtractorFlags(0)
            .setMp4ExtractorFlags(
                androidx.media3.extractor.mp4.Mp4Extractor.FLAG_READ_SEF_DATA
            )
            .setFragmentedMp4ExtractorFlags(
                androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK
            )
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setTsExtractorTimestampSearchBytes(1500 * 188)

        return object : ExtractorsFactory {
            override fun createExtractors(): Array<Extractor> {
                val extractors = baseFactory.createExtractors()
                return wrapExtractors(extractors, context, Uri.EMPTY)
            }

            override fun createExtractors(
                uri: Uri,
                responseHeaders: Map<String, List<String>>
            ): Array<Extractor> {
                val extractors = baseFactory.createExtractors(uri, responseHeaders)
                return wrapExtractors(extractors, context, uri)
            }
        }
    }

    private fun wrapExtractors(
        extractors: Array<Extractor>,
        context: Context,
        uri: Uri
    ): Array<Extractor> {
        return extractors.map { extractor ->
            SeekableFallbackExtractorWrapper(extractor, context, uri)
        }.toTypedArray()
    }

    /**
     * Resolves the stream length in bytes safely for content:// and file:// URIs.
     */
    fun getStreamLength(context: Context, uri: Uri): Long? {
        if (uri == Uri.EMPTY) return null
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val len = afd.length
                    if (len > 0L) len else null
                }
            } else if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists() && file.length() > 0L) file.length() else null
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SEEK_RESOLVE_LENGTH_ERROR] uri=$uri, msg=${e.message}")
            null
        }
    }

    /**
     * Retrieves video duration in microseconds via MediaMetadataRetriever when container duration is unset.
     */
    fun retrieveDurationUs(context: Context, uri: Uri): Long {
        if (uri == Uri.EMPTY) return C.TIME_UNSET
        var retriever: android.media.MediaMetadataRetriever? = null
        return try {
            retriever = android.media.MediaMetadataRetriever()
            if (uri.scheme == "content") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uri.path)
            }
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull() ?: 0L
            if (durMs > 0L) durMs * 1000L else C.TIME_UNSET
        } catch (e: Exception) {
            SeekDiagnosticsLogger.log("LOCAL_SEEK_ROOT_CAUSE", "[SEEK_RETRIEVE_DURATION_ERROR] uri=$uri, msg=${e.message}")
            C.TIME_UNSET
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extractor wrapper that logs SeekMaps and diagnostic events while preserving
     * native Media3 extractor SeekMap and error behavior.
     */
    private class SeekableFallbackExtractorWrapper(
        private val delegate: Extractor,
        private val context: Context,
        private val uri: Uri
    ) : Extractor {

        private val extractorName = delegate.javaClass.simpleName

        override fun init(output: ExtractorOutput) {
            val wrappedOutput = object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput {
                    return output.track(id, type)
                }

                override fun endTracks() {
                    output.endTracks()
                }

                override fun seekMap(seekMap: SeekMap) {
                    SeekDiagnosticsLogger.log(
                        "LOCAL_SEEK_ROOT_CAUSE",
                        "[EXTRACTOR_SEEKMAP_REPORTED] extractor=$extractorName, uri=$uri, isSeekable=${seekMap.isSeekable}, durationUs=${seekMap.durationUs}"
                    )

                    // If the container is natively seekable, preserve the native SeekMap directly.
                    if (seekMap.isSeekable) {
                        output.seekMap(seekMap)
                        return
                    }

                    // Never block the ExoPlayer extractor thread during prepare()!
                    // Emit a non-blocking DeferredContainerSeekMap immediately so the first video frame
                    // renders right away, and build the exact container IndexSeekMap asynchronously.
                    if (uri != Uri.EMPTY) {
                        val deferredSeekMap = DeferredContainerSeekMap(
                            context = context.applicationContext,
                            uri = uri,
                            extractorName = extractorName,
                            initialSeekMap = seekMap
                        )
                        output.seekMap(deferredSeekMap)
                    } else {
                        output.seekMap(seekMap)
                    }
                }
            }
            delegate.init(wrappedOutput)
        }

        override fun sniff(input: ExtractorInput): Boolean {
            return delegate.sniff(input)
        }

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
            return try {
                delegate.read(input, seekPosition)
            } catch (e: IllegalStateException) {
                if (e.message?.contains("varint", ignoreCase = true) == true) {
                    SeekDiagnosticsLogger.log(
                        "LOCAL_SEEK_ROOT_CAUSE",
                        "[EXTRACTOR_VARINT_ERROR] Varint desync in $extractorName at position=${input.position}: ${e.message}"
                    )
                    // Let the original exception propagate rather than converting it into a fake successful seek to position 0.
                    throw e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                SeekDiagnosticsLogger.log(
                    "LOCAL_SEEK_ROOT_CAUSE",
                    "[EXTRACTOR_READ_EXCEPTION] in $extractorName: ${e.message}"
                )
                throw e
            }
        }

        override fun seek(position: Long, timeUs: Long) {
            SeekDiagnosticsLogger.log(
                "LOCAL_SEEK_ROOT_CAUSE",
                "[EXTRACTOR_SEEK_INVOKED] extractor=$extractorName, position=$position, timeUs=$timeUs"
            )
            delegate.seek(position, timeUs)
        }

        override fun release() {
            delegate.release()
        }
    }

    /**
     * Non-blocking SeekMap wrapper that allows ExoPlayer to start decoding and rendering the first
     * video frame immediately while building the container keyframe/cluster index on a background thread.
     */
    internal class DeferredContainerSeekMap(
        context: Context,
        private val uri: Uri,
        private val extractorName: String,
        private val initialSeekMap: SeekMap
    ) : SeekMap {

        private val indexReadyLatch = java.util.concurrent.CountDownLatch(1)

        @Volatile
        private var resolvedSeekMap: SeekMap? = null

        @Volatile
        private var resolvedDurationUs: Long = initialSeekMap.durationUs

        init {
            Thread({
                try {
                    val built = ContainerIndexHelper.buildFallbackSeekMap(
                        context = context,
                        uri = uri,
                        extractorName = extractorName,
                        containerDurationUs = initialSeekMap.durationUs
                    )
                    if (built != null && built.isSeekable) {
                        resolvedSeekMap = built
                        if (built.durationUs > 0L && built.durationUs != C.TIME_UNSET) {
                            resolvedDurationUs = built.durationUs
                        }
                        SeekDiagnosticsLogger.log(
                            "LOCAL_SEEK_ROOT_CAUSE",
                            "[FALLBACK_SEEKMAP_APPLIED] (async) extractor=$extractorName, uri=$uri, isSeekable=${built.isSeekable}, durationUs=${built.durationUs}"
                        )
                    }
                } catch (e: Exception) {
                    SeekDiagnosticsLogger.log(
                        "LOCAL_SEEK_ROOT_CAUSE",
                        "[FALLBACK_SEEKMAP_ASYNC_ERROR] extractor=$extractorName, uri=$uri, msg=${e.message}"
                    )
                } finally {
                    indexReadyLatch.countDown()
                }
            }, "ContainerIndexBuilder").apply {
                isDaemon = true
                start()
            }
        }

        private fun awaitResolvedForNonZeroSeek(): SeekMap? {
            resolvedSeekMap?.let { return it }
            if (indexReadyLatch.count > 0L) {
                try {
                    indexReadyLatch.await(120L, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            return resolvedSeekMap
        }

        override fun isSeekable(): Boolean {
            return resolvedSeekMap?.isSeekable ?: true
        }

        override fun getDurationUs(): Long {
            val resolved = resolvedSeekMap
            if (resolved != null && resolved.durationUs > 0L && resolved.durationUs != C.TIME_UNSET) {
                return resolved.durationUs
            }
            return resolvedDurationUs
        }

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            if (timeUs <= 0L) {
                val resolved = resolvedSeekMap
                if (resolved != null) {
                    return resolved.getSeekPoints(0L)
                }
                return SeekMap.SeekPoints(SeekPoint.START)
            }
            val resolved = resolvedSeekMap ?: awaitResolvedForNonZeroSeek()
            if (resolved != null) {
                return resolved.getSeekPoints(timeUs)
            }
            return initialSeekMap.getSeekPoints(timeUs)
        }
    }
}
