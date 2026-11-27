package com.example.util

import android.content.Context
import android.media.MediaCodecInfo as AndroidMediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

data class PlaybackDiagnosticInfo(
    val title: String,
    val errorMessage: String,
    val errorCodeName: String,
    val codecName: String,
    val mimeType: String,
    val profile: String,
    val level: String,
    val bitDepth: String,
    val resolution: String,
    val colorSpace: String,
    val hdrType: String,
    val deviceDecoders: List<String>,
    val rawException: String,
    val fallbackTriggered: Boolean = false,
    val fallbackReason: String = "None",
    val decoderType: String = "Hardware",
    val media3Version: String = MediaCodec10BitHelper.MEDIA3_VERSION
)

object MediaCodec10BitHelper {

    private const val TAG = "MediaCodec10BitHelper"
    const val MEDIA3_VERSION = "1.5.1"

    private val failedHardwareDecoders = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val softwareFallbackStreams = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    var currentStreamKey: String? = null

    @Volatile
    var currentActiveFormat: Format? = null

    @Volatile
    var lastSelectedDecoderName: String? = null

    @Volatile
    var lastSelectedDecoderIsSoftware: Boolean = false

    @Volatile
    var lastFallbackReason: String? = null

    fun isSoftwareDecoder(info: MediaCodecInfo): Boolean {
        if (info.softwareOnly) return true
        if (!info.hardwareAccelerated) return true
        val name = info.name.lowercase()
        return name.startsWith("c2.android.") ||
                name.startsWith("omx.google.") ||
                name.contains(".sw.") ||
                name.contains("software") ||
                name.contains("ffmpeg")
    }

    fun is10BitHevcFormat(format: Format?): Boolean {
        if (format == null) return false
        val mime = format.sampleMimeType ?: format.containerMimeType ?: ""
        if (!mime.contains("hevc", ignoreCase = true)) return false

        val color = format.colorInfo
        val bitDepth = color?.lumaBitdepth
        if (bitDepth != null && bitDepth >= 10) return true

        val codecs = format.codecs?.lowercase() ?: ""
        if (codecs.contains("hev1.2") || codecs.contains("hvc1.2") || codecs.contains("main10") || codecs.contains("10bit") || codecs.contains("profile=2")) return true

        if (color?.colorTransfer == C.COLOR_TRANSFER_ST2084 || color?.colorTransfer == C.COLOR_TRANSFER_HLG) return true

        return false
    }

    fun isStreamUsingSoftwareFallback(streamKey: String?): Boolean {
        if (streamKey == null) return false
        return softwareFallbackStreams.contains(streamKey)
    }

    fun markHardwareDecoderFailed(decoderName: String, streamKey: String? = null) {
        if (decoderName.isNotBlank() && !decoderName.equals("Unknown", ignoreCase = true)) {
            failedHardwareDecoders.add(decoderName)
            Log.w(TAG, "Blacklisted failing hardware decoder: $decoderName")
        }
        if (!streamKey.isNullOrBlank()) {
            softwareFallbackStreams.add(streamKey)
            lastFallbackReason = "Decoder failed for stream: $decoderName"
            Log.w(TAG, "Activated software HEVC fallback for stream: $streamKey")
        }
    }

    fun shouldFallbackToSoftwareForHevc(streamKey: String?): Boolean {
        if (streamKey != null && softwareFallbackStreams.contains(streamKey)) return true
        val activeFmt = currentActiveFormat
        if (activeFmt != null && is10BitHevcFormat(activeFmt) && failedHardwareDecoders.isNotEmpty()) {
            return true
        }
        return false
    }

    fun extractCodecNameFromError(error: PlaybackException): String? {
        val cause = error.cause
        if (cause is androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException) {
            val name = cause.codecInfo?.name
            if (!name.isNullOrBlank()) return name
        }
        val msg = error.message ?: ""
        val regex = Regex("""(?i)(c2\.[a-z0-9._-]+|OMX\.[a-z0-9._-]+)""")
        val match = regex.find(msg)
        if (match != null) return match.value

        val causeMsg = cause?.message ?: ""
        val matchCause = regex.find(causeMsg)
        if (matchCause != null) return matchCause.value

        return null
    }

    /**
     * Capability-based MediaCodecSelector that preserves hardware acceleration for all formats
     * and filters decoders based on device hardware/software capabilities.
     */
    val tenBitMediaCodecSelector: MediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        try {
            val allDecoders = MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            val streamKey = currentStreamKey
            val isHevc = mimeType.contains("hevc", ignoreCase = true)
            val shouldFallback = isHevc && shouldFallbackToSoftwareForHevc(streamKey)

            val filtered = if (shouldFallback && failedHardwareDecoders.isNotEmpty()) {
                allDecoders.filter { !failedHardwareDecoders.contains(it.name) }
            } else {
                allDecoders
            }

            // Log diagnostic info
            if (filtered.isNotEmpty()) {
                val primary = filtered[0]
                lastSelectedDecoderName = primary.name
                lastSelectedDecoderIsSoftware = isSoftwareDecoder(primary)
                Log.d(
                    TAG,
                    "MediaCodecSelector for $mimeType: Selected primary=${primary.name} [${if (lastSelectedDecoderIsSoftware) "SW" else "HW"}], fallbackActive=$shouldFallback"
                )
            } else if (shouldFallback) {
                lastSelectedDecoderName = "hevc-software-10bit-decoder"
                lastSelectedDecoderIsSoftware = true
                Log.i(TAG, "All hardware decoders bypassed for stream $streamKey -> Routed to Hevc10BitSoftwareVideoRenderer")
            }

            filtered
        } catch (e: Exception) {
            Log.e(TAG, "Error querying decoders for $mimeType", e)
            emptyList()
        }
    }

    /**
     * Creates and configures a DefaultRenderersFactory for optimal 10-bit hardware playback
     * with automated fallback and stereo downmix.
     */
    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(
                        androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(
                            AudioDownmixProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // 1. Primary Hardware MediaCodecVideoRenderer (for normal videos, 8-bit HEVC, and working hardware 10-bit)
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    allowedVideoJoiningTimeMs,
                    out
                )

                // 2. Load ExperimentalFfmpegVideoRenderer if present in classpath with native binaries
                try {
                    val clazz = Class.forName("androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer")
                    val ctor = clazz.getConstructor(
                        Long::class.javaPrimitiveType,
                        android.os.Handler::class.java,
                        androidx.media3.exoplayer.video.VideoRendererEventListener::class.java,
                        Int::class.javaPrimitiveType
                    )
                    val ffmpegRenderer = ctor.newInstance(
                        allowedVideoJoiningTimeMs,
                        eventHandler,
                        eventListener,
                        50
                    ) as androidx.media3.exoplayer.Renderer
                    out.add(ffmpegRenderer)
                    Log.i(TAG, "[10BIT] FFmpeg video renderer available: YES (ExperimentalFfmpegVideoRenderer registered)")
                } catch (e: Exception) {
                    Log.i(TAG, "[10BIT] FFmpeg video renderer available: NO (native FFmpeg video decoder library not bundled in APK)")
                }
            }
        }

        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        renderersFactory.setEnableDecoderFallback(true)
        renderersFactory.setAllowedVideoJoiningTimeMs(5000L)
        renderersFactory.setMediaCodecSelector(tenBitMediaCodecSelector)

        return renderersFactory
    }

    /**
     * Inspects device MediaCodecList to query all supported decoders and 10-bit capabilities for a given MIME.
     */
    fun queryAvailableDecodersForMime(mimeType: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                val types = codecInfo.supportedTypes
                if (types.any { it.equals(mimeType, ignoreCase = true) }) {
                    val caps = try {
                        codecInfo.getCapabilitiesForType(mimeType)
                    } catch (_: Exception) {
                        null
                    }

                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        codecInfo.isHardwareAccelerated
                    } else {
                        !codecInfo.name.startsWith("OMX.google.", ignoreCase = true) &&
                                !codecInfo.name.startsWith("c2.android.", ignoreCase = true)
                    }

                    val profilesStr = caps?.profileLevels?.map { pl ->
                        getProfileName(mimeType, pl.profile)
                    }?.distinct()?.joinToString(", ") ?: "Default"

                    val is10BitSupported = caps?.profileLevels?.any { is10BitProfile(mimeType, it.profile) } == true

                    results.add(
                        "${codecInfo.name} [${if (isHw) "HW" else "SW"}] 10-Bit Capable: ${if (is10BitSupported) "YES" else "NO"} (Profiles: $profilesStr)"
                    )
                }
            }
        } catch (e: Exception) {
            results.add("Error querying codecs: ${e.message}")
        }
        return results
    }

    /**
     * Extracts deep diagnostic information when a playback error occurs.
     */
    fun buildDiagnosticReport(
        error: PlaybackException,
        activeFormat: Format?
    ): PlaybackDiagnosticInfo {
        val rootCause = error.cause
        var codecName = "Unknown"
        var decoderInitError = error.message ?: "Playback error"

        if (rootCause is MediaCodecRenderer.DecoderInitializationException) {
            codecName = rootCause.codecInfo?.name ?: "Hardware/Software Decoder"
            decoderInitError = "Decoder Initialization Failed: ${rootCause.diagnosticInfo ?: rootCause.message}"
        } else if (rootCause != null) {
            decoderInitError = rootCause.message ?: rootCause.javaClass.simpleName
        }

        val mimeType = activeFormat?.sampleMimeType ?: activeFormat?.containerMimeType ?: "video/hevc"
        val width = activeFormat?.width ?: -1
        val height = activeFormat?.height ?: -1
        val fps = if (activeFormat?.frameRate != null && activeFormat.frameRate > 0) "${activeFormat.frameRate.toInt()} fps" else ""
        val resolution = if (width > 0 && height > 0) "${width}x${height} $fps".trim() else "Auto/Unknown"

        val colorInfo = activeFormat?.colorInfo
        val bitDepth = when {
            colorInfo?.lumaBitdepth != null && colorInfo.lumaBitdepth != Format.NO_VALUE -> "${colorInfo.lumaBitdepth}-bit"
            activeFormat?.codecs?.contains("10", ignoreCase = true) == true -> "10-bit"
            activeFormat?.codecs?.contains("main10", ignoreCase = true) == true -> "10-bit Main 10"
            else -> "10-bit (Stream default / HEVC Main 10)"
        }

        val profile = formatProfileName(mimeType, activeFormat)
        val level = if (activeFormat?.codecs != null) activeFormat.codecs.toString() else "Main Level"

        val colorSpace = when (colorInfo?.colorSpace) {
            C.COLOR_SPACE_BT2020 -> "BT.2020 Wide Color Gamut"
            C.COLOR_SPACE_BT709 -> "BT.709 Standard"
            C.COLOR_SPACE_BT601 -> "BT.601"
            else -> "BT.2020 / Device Default"
        }

        val hdrType = when (colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10 (SMPTE ST 2084 PQ)"
            C.COLOR_TRANSFER_HLG -> "HDR HLG (Hybrid Log-Gamma)"
            C.COLOR_TRANSFER_SDR -> "SDR"
            else -> if (colorInfo?.hdrStaticInfo != null) "HDR10 / Wide Color" else "10-Bit Dynamic Range"
        }

        val deviceDecoders = queryAvailableDecodersForMime(mimeType).ifEmpty {
            queryAvailableDecodersForMime("video/hevc")
        }

        val streamKey = currentStreamKey
        val fallbackActive = isStreamUsingSoftwareFallback(streamKey)
        val fallbackReason = lastFallbackReason ?: "None"
        val activeDecoderName = lastSelectedDecoderName ?: codecName
        val activeDecoderType = if (lastSelectedDecoderIsSoftware) "Native Software" else "Hardware"

        return PlaybackDiagnosticInfo(
            title = "10-Bit Playback Diagnostics",
            errorMessage = decoderInitError,
            errorCodeName = error.errorCodeName,
            codecName = activeDecoderName,
            mimeType = mimeType,
            profile = profile,
            level = level,
            bitDepth = bitDepth,
            resolution = resolution,
            colorSpace = colorSpace,
            hdrType = hdrType,
            deviceDecoders = deviceDecoders,
            rawException = rootCause?.stackTraceToString() ?: error.stackTraceToString(),
            fallbackTriggered = fallbackActive,
            fallbackReason = fallbackReason,
            decoderType = activeDecoderType,
            media3Version = MEDIA3_VERSION
        )
    }

    fun is10BitProfile(mimeType: String, profile: Int): Boolean {
        return when {
            mimeType.contains("hevc", ignoreCase = true) -> {
                profile == CodecProfileLevel.HEVCProfileMain10 ||
                        profile == 4096 /* HEVCProfileMain10HDR10 */ ||
                        profile == 8192 /* HEVCProfileMain10HDR10Plus */
            }
            mimeType.contains("av01", ignoreCase = true) || mimeType.contains("av1", ignoreCase = true) -> {
                profile == 2 /* AV1ProfileMain10 */ ||
                        profile == 4096 /* AV1ProfileMain10HDR10 */ ||
                        profile == 8192 /* AV1ProfileMain10HDR10Plus */
            }
            mimeType.contains("vp9", ignoreCase = true) -> {
                profile == CodecProfileLevel.VP9Profile2 ||
                        profile == CodecProfileLevel.VP9Profile3 ||
                        profile == 4096 /* VP9Profile2HDR */ ||
                        profile == 8192 /* VP9Profile3HDR */ ||
                        profile == 16384 /* VP9Profile2HDR10Plus */
            }
            else -> false
        }
    }

    fun getProfileName(mimeType: String, profile: Int): String {
        return when {
            mimeType.contains("hevc", ignoreCase = true) -> {
                when (profile) {
                    CodecProfileLevel.HEVCProfileMain -> "Main"
                    CodecProfileLevel.HEVCProfileMain10 -> "Main 10 (10-bit)"
                    CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
                    4096 -> "Main 10 HDR10"
                    8192 -> "Main 10 HDR10+"
                    else -> "Profile $profile"
                }
            }
            mimeType.contains("av01", ignoreCase = true) || mimeType.contains("av1", ignoreCase = true) -> {
                when (profile) {
                    1 -> "Main 8-bit"
                    2 -> "Main 10-bit"
                    4096 -> "Main 10 HDR10"
                    8192 -> "Main 10 HDR10+"
                    else -> "Profile $profile"
                }
            }
            mimeType.contains("vp9", ignoreCase = true) -> {
                when (profile) {
                    CodecProfileLevel.VP9Profile0 -> "Profile 0 (8-bit)"
                    CodecProfileLevel.VP9Profile1 -> "Profile 1 (8-bit 4:2:2/4:4:4)"
                    CodecProfileLevel.VP9Profile2 -> "Profile 2 (10-bit)"
                    CodecProfileLevel.VP9Profile3 -> "Profile 3 (10-bit 4:2:2/4:4:4)"
                    4096 -> "Profile 2 HDR"
                    8192 -> "Profile 3 HDR"
                    16384 -> "Profile 2 HDR10+"
                    else -> "Profile $profile"
                }
            }
            else -> "Profile $profile"
        }
    }

    fun formatProfileName(mimeType: String, format: Format?): String {
        if (format == null) return "Main 10 (10-bit)"
        val codecs = format.codecs ?: ""
        return when {
            codecs.contains("hev1.2", ignoreCase = true) || codecs.contains("hvc1.2", ignoreCase = true) -> "HEVC Main 10 Profile"
            codecs.contains("hev1.1", ignoreCase = true) || codecs.contains("hvc1.1", ignoreCase = true) -> "HEVC Main Profile"
            mimeType.contains("hevc", ignoreCase = true) -> "HEVC Main 10 Profile"
            mimeType.contains("av01", ignoreCase = true) -> "AV1 Main 10 Profile"
            mimeType.contains("vp9", ignoreCase = true) -> "VP9 Profile 2 (10-bit)"
            else -> codecs.ifBlank { "Main 10 Profile" }
        }
    }
}
