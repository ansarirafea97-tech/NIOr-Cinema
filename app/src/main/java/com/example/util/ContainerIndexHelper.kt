package com.example.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.IndexSeekMap
import androidx.media3.extractor.SeekMap
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Parses local media container structures (Matroska/MKV/WebM, Fragmented MP4)
 * to build real keyframe / cluster index SeekMaps when the media container lacks
 * built-in Cues/sidx tables.
 */
object ContainerIndexHelper {

    private const val TAG = "LOCAL_SEEK_ROOT_CAUSE"

    // Matroska / EBML Element IDs
    private const val ID_EBML_HEADER = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_SEEK_HEAD = 0x114D9B74L
    private const val ID_INFO = 0x1549A966L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_CUES = 0x1C53BB6BL

    // Child element IDs
    private const val ID_TIMECODE_SCALE = 0x2AD7B1L
    private const val ID_DURATION = 0x4489L
    private const val ID_TIMECODE = 0xE7L
    private const val ID_CUE_POINT = 0xBBL
    private const val ID_CUE_TIME = 0xB3L
    private const val ID_CUE_TRACK_POSITIONS = 0xB7L
    private const val ID_CUE_CLUSTER_POSITION = 0xF1L

    /**
     * Attempts to build a real SeekMap for an unseekable file using actual container indices.
     */
    fun buildFallbackSeekMap(
        context: Context,
        uri: Uri,
        extractorName: String,
        containerDurationUs: Long
    ): SeekMap? {
        if (uri == Uri.EMPTY) return null
        val isMatroska = extractorName.contains("Matroska", ignoreCase = true) || extractorName.isBlank()
        val durationUs = if (containerDurationUs > 0L && containerDurationUs != C.TIME_UNSET) {
            containerDurationUs
        } else if (isMatroska) {
            // Matroska header (ID_INFO) provides duration directly in <1ms without opening MediaMetadataRetriever
            C.TIME_UNSET
        } else {
            LocalMediaSeekHelper.retrieveDurationUs(context, uri)
        }

        SeekDiagnosticsLogger.log(
            TAG,
            "[CONTAINER_INDEX_ATTEMPT] extractor=$extractorName, uri=$uri, durationUs=$durationUs"
        )

        return try {
            when {
                extractorName.contains("Matroska", ignoreCase = true) -> {
                    buildMatroskaSeekMap(context, uri, durationUs)
                }
                extractorName.contains("FragmentedMp4", ignoreCase = true) -> {
                    buildFragmentedMp4SeekMap(context, uri, durationUs)
                }
                extractorName.contains("Mp4", ignoreCase = true) -> {
                    buildFragmentedMp4SeekMap(context, uri, durationUs)
                }
                else -> {
                    // Try Matroska first, then Fragmented MP4
                    buildMatroskaSeekMap(context, uri, durationUs)
                        ?: buildFragmentedMp4SeekMap(
                            context,
                            uri,
                            if (durationUs > 0L && durationUs != C.TIME_UNSET) durationUs else LocalMediaSeekHelper.retrieveDurationUs(context, uri)
                        )
                }
            }
        } catch (e: Exception) {
            SeekDiagnosticsLogger.log(
                TAG,
                "[CONTAINER_INDEX_ERROR] Failed building index for uri=$uri: ${e.message}"
            )
            null
        }
    }

    /**
     * Builds an IndexSeekMap or ChunkIndex for Matroska / WebM files by scanning for
     * existing Cues or by collecting Level-1 Cluster positions and timecodes.
     */
    private fun buildMatroskaSeekMap(
        context: Context,
        uri: Uri,
        knownDurationUs: Long
    ): SeekMap? {
        val afd = try {
            if (uri.scheme == "content") {
                context.contentResolver.openAssetFileDescriptor(uri, "r")
            } else if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: return null)
                if (file.exists() && file.length() > 0) {
                    android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)?.let {
                        android.content.res.AssetFileDescriptor(it, 0, file.length())
                    }
                } else null
            } else null
        } catch (e: Exception) {
            SeekDiagnosticsLogger.log(TAG, "[MATROSKA_OPEN_ERROR] ${e.message}")
            null
        } ?: return null

        return afd.use { assetFd ->
            val pfd = assetFd.parcelFileDescriptor
            val fileLength = if (assetFd.length > 0) assetFd.length else pfd.statSize
            if (fileLength <= 0) return null

            java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                // First: Check if Cues exists in the file (e.g. at the end or after SeekHead)
                val cuesSeekMap = tryReadCues(channel, fileLength, knownDurationUs)
                if (cuesSeekMap != null) {
                    SeekDiagnosticsLogger.log(
                        TAG,
                        "[MATROSKA_CUES_FOUND] Successfully parsed Cues table: durationUs=${cuesSeekMap.durationUs}"
                    )
                    return cuesSeekMap
                }

                // Second: Scan clusters sequentially
                val clusterSeekMap = scanMatroskaClusters(channel, fileLength, knownDurationUs)
                if (clusterSeekMap != null) {
                    SeekDiagnosticsLogger.log(
                        TAG,
                        "[MATROSKA_CLUSTERS_INDEXED] Successfully indexed clusters: durationUs=${clusterSeekMap.durationUs}"
                    )
                    return clusterSeekMap
                }
                null
            }
        }
    }

    /**
     * Reads Matroska Cues if present in the file.
     */
    private fun tryReadCues(
        channel: FileChannel,
        fileLength: Long,
        knownDurationUs: Long
    ): SeekMap? {
        // Cues are commonly stored in the last 2MB of an MKV file if placed at the end.
        val scanWindow = minOf(fileLength, 2 * 1024 * 1024L)
        val startOffset = fileLength - scanWindow
        val buffer = ByteBuffer.allocate(scanWindow.toInt())
        channel.position(startOffset)
        var totalRead = 0
        while (buffer.hasRemaining()) {
            val r = channel.read(buffer)
            if (r <= 0) break
            totalRead += r
        }
        buffer.flip()

        // Search backwards from the end of the file for Cues ID (0x1C, 0x53, 0xBB, 0x6B)
        // and verify each candidate so a random 4-byte match in video payload doesn't abort Cues parsing.
        val bytes = buffer.array()
        val limit = totalRead - 4
        for (i in limit downTo 0) {
            if (bytes[i] == 0x1C.toByte() &&
                bytes[i + 1] == 0x53.toByte() &&
                bytes[i + 2] == 0xBB.toByte() &&
                bytes[i + 3] == 0x6B.toByte()
            ) {
                val cuesOffset = startOffset + i
                val parsed = parseCuesAtOffset(channel, cuesOffset, fileLength, knownDurationUs)
                if (parsed != null) {
                    return parsed
                }
            }
        }

        return null
    }

    private fun findSegmentContentPosition(channel: FileChannel): Long {
        return try {
            val originalPos = channel.position()
            channel.position(0)
            val buf = ByteBuffer.allocate(64)
            val readBytes = channel.read(buf)
            if (readBytes < 8) {
                channel.position(originalPos)
                return 0L
            }
            buf.flip()

            val ebmlIdRes = readEbmlIdFromBuffer(buf) ?: run { channel.position(originalPos); return 0L }
            if (ebmlIdRes.first != ID_EBML_HEADER) { channel.position(originalPos); return 0L }
            val ebmlSzRes = readEbmlVintFromBuffer(buf) ?: run { channel.position(originalPos); return 0L }

            val segmentElemStart = (ebmlIdRes.second + ebmlSzRes.second).toLong() + ebmlSzRes.first
            channel.position(segmentElemStart)
            buf.clear()
            val segReadBytes = channel.read(buf)
            if (segReadBytes < 5) {
                channel.position(originalPos)
                return 0L
            }
            buf.flip()
            val segIdRes = readEbmlIdFromBuffer(buf) ?: run { channel.position(originalPos); return 0L }
            if (segIdRes.first != ID_SEGMENT) { channel.position(originalPos); return 0L }
            val segSzRes = readEbmlVintFromBuffer(buf) ?: run { channel.position(originalPos); return 0L }

            val segmentContentPos = segmentElemStart + segIdRes.second + segSzRes.second
            channel.position(originalPos)
            segmentContentPos
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseCuesAtOffset(
        channel: FileChannel,
        cuesOffset: Long,
        fileLength: Long,
        knownDurationUs: Long
    ): SeekMap? {
        val segmentContentPos = findSegmentContentPosition(channel)
        val maxCuesBytes = minOf(fileLength - cuesOffset, 2 * 1024 * 1024L).toInt()
        if (maxCuesBytes < 12) return null

        val buf = ByteBuffer.allocate(maxCuesBytes)
        channel.position(cuesOffset)
        var totalRead = 0
        while (buf.hasRemaining()) {
            val r = channel.read(buf)
            if (r <= 0) break
            totalRead += r
        }
        buf.flip()

        val idRes = readEbmlIdFromBuffer(buf) ?: return null
        if (idRes.first != ID_CUES) return null
        val szRes = readEbmlVintFromBuffer(buf) ?: return null
        val cuesSize = szRes.first
        if (cuesSize <= 0L || cuesSize > fileLength - cuesOffset) return null

        val cuesEndInBuf = minOf(buf.position() + cuesSize.toInt(), totalRead)
        val positions = androidx.media3.common.util.LongArray()
        val timesUs = androidx.media3.common.util.LongArray()

        while (buf.position() < cuesEndInBuf) {
            val elemIdRes = readEbmlIdFromBuffer(buf) ?: break
            val elemSzRes = readEbmlVintFromBuffer(buf) ?: break
            val elemId = elemIdRes.first
            val elemSize = elemSzRes.first.toInt()
            if (elemSize < 0 || buf.position() + elemSize > cuesEndInBuf) break
            val childEnd = buf.position() + elemSize

            if (elemId == ID_CUE_POINT) {
                var cueTime = -1L
                var clusterPos = -1L
                while (buf.position() < childEnd) {
                    val cpIdRes = readEbmlIdFromBuffer(buf) ?: break
                    val cpSzRes = readEbmlVintFromBuffer(buf) ?: break
                    val cpElemId = cpIdRes.first
                    val cpElemSize = cpSzRes.first.toInt()
                    if (cpElemSize < 0 || buf.position() + cpElemSize > childEnd) break
                    val cpChildEnd = buf.position() + cpElemSize

                    when (cpElemId) {
                        ID_CUE_TIME -> {
                            cueTime = readUintFromBuffer(buf, cpElemSize)
                        }
                        ID_CUE_TRACK_POSITIONS -> {
                            while (buf.position() < cpChildEnd) {
                                val ctpIdRes = readEbmlIdFromBuffer(buf) ?: break
                                val ctpSzRes = readEbmlVintFromBuffer(buf) ?: break
                                val ctpElemId = ctpIdRes.first
                                val ctpElemSize = ctpSzRes.first.toInt()
                                if (ctpElemSize < 0 || buf.position() + ctpElemSize > cpChildEnd) break
                                if (ctpElemId == ID_CUE_CLUSTER_POSITION) {
                                    clusterPos = readUintFromBuffer(buf, ctpElemSize)
                                } else {
                                    buf.position(buf.position() + ctpElemSize)
                                }
                            }
                        }
                        else -> {
                            buf.position(cpChildEnd)
                        }
                    }
                    buf.position(cpChildEnd)
                }

                if (cueTime >= 0L && clusterPos >= 0L) {
                    timesUs.add(cueTime * 1000L)
                    positions.add(segmentContentPos + clusterPos)
                }
            }
            buf.position(childEnd)
        }

        if (positions.size() > 0) {
            val durationUs = if (knownDurationUs > 0 && knownDurationUs != C.TIME_UNSET) {
                knownDurationUs
            } else {
                timesUs.get(timesUs.size() - 1)
            }
            val posArr = positions.toArray()
            val timeArr = timesUs.toArray()
            if (timeArr.isNotEmpty() && timeArr[0] > 0L) {
                timeArr[0] = 0L
            }
            return IndexSeekMap(posArr, timeArr, durationUs)
        }
        return null
    }

    /**
     * Scans Level-1 Clusters in Matroska/WebM to extract accurate keyframe/cluster offsets and timecodes.
     */
    private fun scanMatroskaClusters(
        channel: FileChannel,
        fileLength: Long,
        knownDurationUs: Long
    ): SeekMap? {
        val positions = androidx.media3.common.util.LongArray()
        val timesUs = androidx.media3.common.util.LongArray()

        var pos = 0L
        var timecodeScaleNs = 1_000_000L // default 1ms
        var detectedDurationUs = knownDurationUs

        val buf = ByteBuffer.allocate(32)
        val scanDeadlineMs = android.os.SystemClock.elapsedRealtime() + 1200L

        while (pos < fileLength) {
            if (android.os.SystemClock.elapsedRealtime() > scanDeadlineMs) {
                SeekDiagnosticsLogger.log(
                    TAG,
                    "[MATROSKA_CLUSTER_SCAN_TIMEOUT_BUDGET] Reached 1200ms time budget with ${positions.size()} clusters indexed"
                )
                break
            }
            channel.position(pos)
            buf.clear()
            val bytesRead = channel.read(buf)
            if (bytesRead < 4) break
            buf.flip()

            val idResult = readEbmlIdFromBuffer(buf) ?: break
            val elemId = idResult.first
            val idLen = idResult.second

            val sizeResult = readEbmlVintFromBuffer(buf) ?: break
            val elemSize = sizeResult.first
            val sizeLen = sizeResult.second
            val isUnknownSize = sizeResult.third

            val headerLen = (idLen + sizeLen).toLong()

            when (elemId) {
                ID_SEGMENT -> {
                    // Enter Segment: Level 1 elements are inside
                    pos += headerLen
                }
                ID_INFO -> {
                    // Parse TimecodeScale and Duration if available
                    if (!isUnknownSize && elemSize > 0 && elemSize < 4096) {
                        val infoBytes = ByteBuffer.allocate(elemSize.toInt())
                        channel.position(pos + headerLen)
                        channel.read(infoBytes)
                        infoBytes.flip()
                        parseInfoElement(infoBytes, timecodeScaleNs, detectedDurationUs) { scale, dur ->
                            timecodeScaleNs = scale
                            if (dur > 0) detectedDurationUs = dur
                        }
                    }
                    pos += headerLen + if (isUnknownSize) 0 else elemSize
                }
                ID_CLUSTER -> {
                    val clusterOffset = pos
                    // Read Timecode child element (ID 0xE7)
                    val tcBytes = ByteBuffer.allocate(16)
                    channel.position(pos + headerLen)
                    val tcRead = channel.read(tcBytes)
                    var clusterTimeUs = 0L
                    if (tcRead >= 3) {
                        tcBytes.flip()
                        val tcIdRes = readEbmlIdFromBuffer(tcBytes)
                        if (tcIdRes != null && tcIdRes.first == ID_TIMECODE) {
                            val tcSzRes = readEbmlVintFromBuffer(tcBytes)
                            if (tcSzRes != null) {
                                val tcVal = readUintFromBuffer(tcBytes, tcSzRes.first.toInt())
                                clusterTimeUs = (tcVal * timecodeScaleNs) / 1000L
                            }
                        }
                    }

                    positions.add(clusterOffset)
                    timesUs.add(clusterTimeUs)

                    if (isUnknownSize || elemSize <= 0) {
                        // Cannot skip cluster of unknown size without scanning every byte; stop here.
                        break
                    } else {
                        pos += headerLen + elemSize
                    }
                }
                else -> {
                    if (isUnknownSize || elemSize <= 0) {
                        break
                    } else {
                        pos += headerLen + elemSize
                    }
                }
            }
        }

        if (positions.size() > 0) {
            val finalDurationUs = if (detectedDurationUs > 0 && detectedDurationUs != C.TIME_UNSET) {
                detectedDurationUs
            } else {
                timesUs.get(timesUs.size() - 1)
            }
            SeekDiagnosticsLogger.log(
                TAG,
                "[MATROSKA_INDEX_BUILT] Discovered ${positions.size()} clusters, durationUs=$finalDurationUs"
            )
            val posArr = positions.toArray()
            val timeArr = timesUs.toArray()
            if (timeArr.isNotEmpty() && timeArr[0] > 0L) {
                timeArr[0] = 0L
            }
            return IndexSeekMap(posArr, timeArr, finalDurationUs)
        }
        return null
    }

    /**
     * Builds an IndexSeekMap for Fragmented MP4 files by locating all 'moof' boxes
     * and reading their baseMediaDecodeTime from the tfdt box.
     */
    private fun buildFragmentedMp4SeekMap(
        context: Context,
        uri: Uri,
        knownDurationUs: Long
    ): SeekMap? {
        val afd = try {
            if (uri.scheme == "content") {
                context.contentResolver.openAssetFileDescriptor(uri, "r")
            } else if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: return null)
                if (file.exists() && file.length() > 0) {
                    android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)?.let {
                        android.content.res.AssetFileDescriptor(it, 0, file.length())
                    }
                } else null
            } else null
        } catch (e: Exception) {
            SeekDiagnosticsLogger.log(TAG, "[FMP4_OPEN_ERROR] ${e.message}")
            null
        } ?: return null

        return afd.use { assetFd ->
            val pfd = assetFd.parcelFileDescriptor
            val fileLength = if (assetFd.length > 0) assetFd.length else pfd.statSize
            if (fileLength <= 0) return null

            java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val positions = androidx.media3.common.util.LongArray()
                val timesUs = androidx.media3.common.util.LongArray()

                var pos = 0L
                var timescale = 1000L // Default timescale
                val headerBuf = ByteBuffer.allocate(16)
                val scanDeadlineMs = android.os.SystemClock.elapsedRealtime() + 1200L

                while (pos + 8 <= fileLength) {
                    if (android.os.SystemClock.elapsedRealtime() > scanDeadlineMs) {
                        break
                    }
                    channel.position(pos)
                    headerBuf.clear()
                    val r = channel.read(headerBuf)
                    if (r < 8) break
                    headerBuf.flip()

                    var boxSize = headerBuf.getInt().toLong() and 0xFFFFFFFFL
                    val typeBytes = ByteArray(4)
                    headerBuf.get(typeBytes)
                    val boxType = String(typeBytes, Charsets.ISO_8859_1)

                    val headerLen = if (boxSize == 1L) {
                        if (r < 16) break
                        boxSize = headerBuf.getLong()
                        16L
                    } else if (boxSize == 0L) {
                        boxSize = fileLength - pos
                        8L
                    } else {
                        8L
                    }

                    if (boxSize < headerLen) break

                    if (boxType == "moof") {
                        val moofOffset = pos
                        // Search inside moof payload for tfdt
                        val payloadLen = minOf(boxSize - headerLen, 8192L).toInt()
                        val payloadBuf = ByteBuffer.allocate(payloadLen)
                        channel.position(pos + headerLen)
                        channel.read(payloadBuf)
                        val payloadBytes = payloadBuf.array()

                        var tfdtOffset = -1
                        for (i in 0 until payloadLen - 4) {
                            if (payloadBytes[i] == 't'.code.toByte() &&
                                payloadBytes[i + 1] == 'f'.code.toByte() &&
                                payloadBytes[i + 2] == 'd'.code.toByte() &&
                                payloadBytes[i + 3] == 't'.code.toByte()
                            ) {
                                tfdtOffset = i + 4
                                break
                            }
                        }

                        var timeUs = 0L
                        if (tfdtOffset >= 0 && tfdtOffset + 8 <= payloadLen) {
                            val version = payloadBytes[tfdtOffset].toInt() and 0xFF
                            // Version 1 has 8-byte baseMediaDecodeTime at tfdtOffset + 4
                            // Version 0 has 4-byte baseMediaDecodeTime at tfdtOffset + 4
                            val timeDataOffset = tfdtOffset + 4
                            val baseDecodeTime = if (version == 1 && timeDataOffset + 8 <= payloadLen) {
                                ByteBuffer.wrap(payloadBytes, timeDataOffset, 8).getLong()
                            } else if (timeDataOffset + 4 <= payloadLen) {
                                ByteBuffer.wrap(payloadBytes, timeDataOffset, 4).getInt().toLong() and 0xFFFFFFFFL
                            } else 0L

                            timeUs = (baseDecodeTime * 1_000_000L) / timescale
                        }

                        positions.add(moofOffset)
                        timesUs.add(timeUs)
                    }

                    pos += boxSize
                }

                if (positions.size() > 0) {
                    val finalDurationUs = if (knownDurationUs > 0 && knownDurationUs != C.TIME_UNSET) {
                        knownDurationUs
                    } else {
                        timesUs.get(timesUs.size() - 1)
                    }
                    SeekDiagnosticsLogger.log(
                        TAG,
                        "[FMP4_INDEX_BUILT] Discovered ${positions.size()} moof fragments, durationUs=$finalDurationUs"
                    )
                    val posArr = positions.toArray()
                    val timeArr = timesUs.toArray()
                    if (timeArr.isNotEmpty() && timeArr[0] > 0L) {
                        timeArr[0] = 0L
                    }
                    IndexSeekMap(posArr, timeArr, finalDurationUs)
                } else null
            }
        }
    }

    // --- EBML Parsing Helpers ---

    private fun readEbmlId(stream: InputStream): Long? {
        val b0 = stream.read()
        if (b0 == -1) return null
        var mask = 0x80
        var idLen = 1
        while (mask > 0 && (b0 and mask) == 0) {
            mask = mask ushr 1
            idLen++
        }
        if (mask == 0 || idLen > 4) return null
        var id = b0.toLong()
        for (i in 1 until idLen) {
            val b = stream.read()
            if (b == -1) return null
            id = (id shl 8) or (b.toLong() and 0xFFL)
        }
        return id
    }

    private fun readEbmlVint(stream: InputStream): Long? {
        val b0 = stream.read()
        if (b0 == -1) return null
        var mask = 0x80
        var len = 1
        while (mask > 0 && (b0 and mask) == 0) {
            mask = mask ushr 1
            len++
        }
        if (mask == 0 || len > 8) return null
        var value = (b0 and (mask - 1)).toLong()
        for (i in 1 until len) {
            val b = stream.read()
            if (b == -1) return null
            value = (value shl 8) or (b.toLong() and 0xFFL)
        }
        return value
    }

    private fun readEbmlUint(stream: InputStream, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            val b = stream.read()
            if (b == -1) break
            value = (value shl 8) or (b.toLong() and 0xFFL)
        }
        return value
    }

    private fun skipBytes(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readEbmlIdFromBuffer(buf: ByteBuffer): Pair<Long, Int>? {
        if (!buf.hasRemaining()) return null
        val b0 = buf.get().toInt() and 0xFF
        var mask = 0x80
        var idLen = 1
        while (mask > 0 && (b0 and mask) == 0) {
            mask = mask ushr 1
            idLen++
        }
        if (mask == 0 || idLen > 4 || buf.remaining() < idLen - 1) return null
        var id = b0.toLong()
        for (i in 1 until idLen) {
            val b = buf.get().toInt() and 0xFF
            id = (id shl 8) or b.toLong()
        }
        return Pair(id, idLen)
    }

    private fun readEbmlVintFromBuffer(buf: ByteBuffer): Triple<Long, Int, Boolean>? {
        if (!buf.hasRemaining()) return null
        val b0 = buf.get().toInt() and 0xFF
        var mask = 0x80
        var len = 1
        while (mask > 0 && (b0 and mask) == 0) {
            mask = mask ushr 1
            len++
        }
        if (mask == 0 || len > 8 || buf.remaining() < len - 1) return null
        var value = (b0 and (mask - 1)).toLong()
        for (i in 1 until len) {
            val b = buf.get().toInt() and 0xFF
            value = (value shl 8) or b.toLong()
        }
        val isUnknown = value == ((1L shl (7 * len)) - 1L)
        return Triple(value, len, isUnknown)
    }

    private fun readUintFromBuffer(buf: ByteBuffer, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            if (!buf.hasRemaining()) break
            val b = buf.get().toInt() and 0xFF
            value = (value shl 8) or b.toLong()
        }
        return value
    }

    private fun parseInfoElement(
        buf: ByteBuffer,
        defaultScale: Long,
        defaultDur: Long,
        onParsed: (Long, Long) -> Unit
    ) {
        var scale = defaultScale
        var dur = defaultDur
        while (buf.hasRemaining()) {
            val idRes = readEbmlIdFromBuffer(buf) ?: break
            val szRes = readEbmlVintFromBuffer(buf) ?: break
            val elemId = idRes.first
            val elemSize = szRes.first.toInt()
            if (buf.remaining() < elemSize) break
            when (elemId) {
                ID_TIMECODE_SCALE -> {
                    scale = readUintFromBuffer(buf, elemSize)
                }
                ID_DURATION -> {
                    dur = if (elemSize == 4) {
                        (buf.getFloat() * 1000f).toLong()
                    } else if (elemSize == 8) {
                        (buf.getDouble() * 1000.0).toLong()
                    } else {
                        readUintFromBuffer(buf, elemSize) * 1000L
                    }
                }
                else -> {
                    buf.position(buf.position() + elemSize)
                }
            }
        }
        onParsed(scale, dur)
    }
}
