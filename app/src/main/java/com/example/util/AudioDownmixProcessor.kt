package com.example.util

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A custom Media3 AudioProcessor that intercepts multi-channel (5.1/7.1) audio
 * and downmixes it to stereo (2.0) while preserving the Center channel (dialogue).
 */
class AudioDownmixProcessor : AudioProcessor {

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var isActive = false

    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // We handle 16-bit PCM and Float PCM encodings
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        inputFormat = inputAudioFormat
        if (inputAudioFormat.channelCount > 2) {
            outputFormat = AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
            isActive = true
            Log.d("AudioDownmixProcessor", "Configured downmix: ${inputAudioFormat.channelCount} channels -> Stereo")
        } else {
            outputFormat = inputAudioFormat
            isActive = false
        }
        return outputFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position

        val inputChannelCount = inputFormat.channelCount
        val encoding = inputFormat.encoding

        if (encoding == C.ENCODING_PCM_16BIT) {
            // 2 bytes per sample per channel
            val frameCount = remaining / (inputChannelCount * 2)
            val outputFrameSize = 2 * 2 // 2 channels, 2 bytes per sample
            val outputSize = frameCount * outputFrameSize

            if (buffer.capacity() < outputSize) {
                buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
            } else {
                buffer.clear()
            }

            for (i in 0 until frameCount) {
                val channels = ShortArray(inputChannelCount)
                for (ch in 0 until inputChannelCount) {
                    val index = position + (i * inputChannelCount + ch) * 2
                    if (index + 1 < limit) {
                        channels[ch] = inputBuffer.getShort(index)
                    }
                }

                // Channel indices for standard 5.1/7.1 layouts:
                // 0: Front Left (FL)
                // 1: Front Right (FR)
                // 2: Center (C) -> Typically carries dialogue
                // 3: Low Frequency Effects (LFE)
                // 4: Surround Left (SL)
                // 5: Surround Right (SR)
                // 6: Back Left (BL)
                // 7: Back Right (BR)
                val fl = channels.getOrElse(0) { 0 }.toFloat()
                val fr = channels.getOrElse(1) { 0 }.toFloat()
                val center = channels.getOrElse(2) { 0 }.toFloat()
                val lfe = channels.getOrElse(3) { 0 }.toFloat()
                val sl = channels.getOrElse(4) { 0 }.toFloat()
                val sr = channels.getOrElse(5) { 0 }.toFloat()
                val bl = channels.getOrElse(6) { 0 }.toFloat()
                val br = channels.getOrElse(7) { 0 }.toFloat()

                // Mix coefficients (Standard downmixing equations)
                // Center dialogue mixed equally into both Left and Right at -3dB (0.707)
                // Surrounds and LFE mixed with standard downmix weights to preserve spatial information without overwhelming
                val centerWeight = 0.707f
                val surroundWeight = 0.5f
                val lfeWeight = 0.5f

                var leftMix = fl + (center * centerWeight) + (sl * surroundWeight) + (bl * surroundWeight) + (lfe * lfeWeight)
                var rightMix = fr + (center * centerWeight) + (sr * surroundWeight) + (br * surroundWeight) + (lfe * lfeWeight)

                // Scaling factor to prevent clipping and balance output volume
                val scale = 0.55f
                leftMix *= scale
                rightMix *= scale

                val minVal = Short.MIN_VALUE.toFloat()
                val maxVal = Short.MAX_VALUE.toFloat()

                val clampedLeft = if (leftMix < minVal) minVal else if (leftMix > maxVal) maxVal else leftMix
                val clampedRight = if (rightMix < minVal) minVal else if (rightMix > maxVal) maxVal else rightMix

                val finalLeft = clampedLeft.toInt().toShort()
                val finalRight = clampedRight.toInt().toShort()

                buffer.putShort(finalLeft)
                buffer.putShort(finalRight)
            }

            inputBuffer.position(limit)
            buffer.flip()
            outputBuffer = buffer

        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            // 4 bytes per sample per channel
            val frameCount = remaining / (inputChannelCount * 4)
            val outputFrameSize = 2 * 4 // 2 channels, 4 bytes per sample
            val outputSize = frameCount * outputFrameSize

            if (buffer.capacity() < outputSize) {
                buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
            } else {
                buffer.clear()
            }

            for (i in 0 until frameCount) {
                val channels = FloatArray(inputChannelCount)
                for (ch in 0 until inputChannelCount) {
                    val index = position + (i * inputChannelCount + ch) * 4
                    if (index + 3 < limit) {
                        channels[ch] = inputBuffer.getFloat(index)
                    }
                }

                val fl = channels.getOrElse(0) { 0f }
                val fr = channels.getOrElse(1) { 0f }
                val center = channels.getOrElse(2) { 0f }
                val lfe = channels.getOrElse(3) { 0f }
                val sl = channels.getOrElse(4) { 0f }
                val sr = channels.getOrElse(5) { 0f }
                val bl = channels.getOrElse(6) { 0f }
                val br = channels.getOrElse(7) { 0f }

                val centerWeight = 0.707f
                val surroundWeight = 0.5f
                val lfeWeight = 0.5f

                var leftMix = fl + (center * centerWeight) + (sl * surroundWeight) + (bl * surroundWeight) + (lfe * lfeWeight)
                var rightMix = fr + (center * centerWeight) + (sr * surroundWeight) + (br * surroundWeight) + (lfe * lfeWeight)

                val scale = 0.55f
                leftMix *= scale
                rightMix *= scale

                val clampedLeft = if (leftMix < -1.0f) -1.0f else if (leftMix > 1.0f) 1.0f else leftMix
                val clampedRight = if (rightMix < -1.0f) -1.0f else if (rightMix > 1.0f) 1.0f else rightMix

                buffer.putFloat(clampedLeft)
                buffer.putFloat(clampedRight)
            }

            inputBuffer.position(limit)
            buffer.flip()
            outputBuffer = buffer
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
        outputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        isActive = false
    }
}
