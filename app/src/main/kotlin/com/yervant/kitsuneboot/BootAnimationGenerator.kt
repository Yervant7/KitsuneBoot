/*
 * Kitsune Boot - Boot Animation Maker for Android
 * Copyright (C) 2026 Yervant
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.yervant.kitsuneboot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ScalingMode {
    FIT_CENTER,
    CENTER_CROP,
    STRETCH
}

enum class ImageFormat {
    PNG,
    JPEG
}

class BitmapPool(
    val width: Int,
    val height: Int,
    val config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    maxPoolSize: Int = 16
) {
    private val pool = ArrayBlockingQueue<Bitmap>(maxPoolSize)

    fun acquire(): Bitmap {
        return pool.poll() ?: createBitmap(width, height, config)
    }

    fun release(bitmap: Bitmap) {
        if (!bitmap.isRecycled && bitmap.width == width && bitmap.height == height && bitmap.config == config) {
            if (!pool.offer(bitmap)) {
                bitmap.recycle()
            }
        } else if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    fun clear() {
        while (true) {
            val bmp = pool.poll() ?: break
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
    }
}

class ByteArrayPool(
    val bufferSize: Int,
    maxPoolSize: Int = 16
) {
    private val pool = ArrayBlockingQueue<ByteArray>(maxPoolSize)

    fun acquire(): ByteArray {
        return pool.poll() ?: ByteArray(bufferSize)
    }

    fun release(buffer: ByteArray) {
        if (buffer.size >= bufferSize) {
            pool.offer(buffer)
        }
    }
}

class PooledByteArrayOutputStream(
    private val pool: ByteArrayPool
) : OutputStream() {
    var buffer: ByteArray = pool.acquire()
        private set
    var count: Int = 0
        private set

    override fun write(b: Int) {
        ensureCapacity(count + 1)
        buffer[count++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        ensureCapacity(count + len)
        System.arraycopy(b, off, buffer, count, len)
        count += len
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > buffer.size) {
            val newSize = maxOf(buffer.size * 2, minCapacity)
            val newBuffer = ByteArray(newSize)
            System.arraycopy(buffer, 0, newBuffer, 0, count)
            buffer = newBuffer
        }
    }

    fun reset() {
        count = 0
    }

    fun detachBufferAndReset(): Pair<ByteArray, Int> {
        val currentBuf = buffer
        val currentCount = count
        buffer = pool.acquire()
        count = 0
        return Pair(currentBuf, currentCount)
    }

    fun release() {
        pool.release(buffer)
    }
}

class CountingOutputStream(private val target: OutputStream) : OutputStream() {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        target.write(b)
        bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len > 0) {
            target.write(b, off, len)
            bytesWritten += len
        }
    }

    override fun flush() = target.flush()
    override fun close() = target.close()
}

object BootAnimationGenerator {
    const val MAX_DURATION_MS = 60_000L // 60 seconds limit
    const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB limit
    const val OOM_THRESHOLD_BYTES = 90 * 1024 * 1024L // 90 MB limit for OOM warning

    internal data class RawFrameTask(
        val frameIndex: Int,
        val isPart0: Boolean,
        val partFrameIndex: Int,
        val rawBitmap: Bitmap
    )

    private data class CompressedFrame(
        val frameIndex: Int,
        val relativePath: String,
        val bytes: ByteArray,
        val length: Int,
        val crc32: Long
    )

    /**
     * Generates a bootanimation.zip file directly from a video using a hardware GPU decoder
     * with multi-retriever fallback and zero-allocation streaming pipeline.
     */
    suspend fun generate(
        context: Context,
        videoUri: Uri,
        canvasWidth: Int,
        canvasHeight: Int,
        frameWidth: Int,
        frameHeight: Int,
        targetFps: Int,
        scalingMode: ScalingMode,
        backgroundColor: Int,
        loopCount: Int = 0,
        pauseCount: Int = 0,
        partType: Char = 'p', // 'p' or 'c'
        imageFormat: ImageFormat = ImageFormat.PNG,
        jpegQuality: Int = 90,
        startMs: Long = 0L,
        endMs: Long = 0L,
        rotationDegrees: Int = 0,
        multiPartMode: Boolean = false,
        multiPartSourceMode: MultiPartSourceMode = MultiPartSourceMode.SINGLE_SPLIT,
        loopVideoUri: Uri? = null,
        loopStartMs: Long = 0L,
        loopEndMs: Long = 0L,
        loopRotationDegrees: Int = 0,
        isPingPongEnabled: Boolean = false,
        isCrossfadeEnabled: Boolean = false,
        crossfadeFrames: Int = 8,
        splitPointMs: Long = 0L,
        part0LoopCount: Int = 1,
        part0PauseCount: Int = 0,
        part1LoopCount: Int = 0,
        part1PauseCount: Int = 0,
        outputStream: OutputStream,
        onProgress: (step: String, progress: Float) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): Long = withContext(Dispatchers.IO) {
        // Validation of input parameters
        require(targetFps in 1..120) { "Target FPS must be between 1 and 120 (got $targetFps)." }
        require(canvasWidth in 100..8000 && canvasHeight in 100..8000) { "Canvas dimensions must be between 100 and 8000 px." }
        require(frameWidth in 100..8000 && frameHeight in 100..8000) { "Frame dimensions must be between 100 and 8000 px." }
        require(jpegQuality in 1..100) { "JPEG quality must be between 1 and 100." }
        require(loopCount >= 0 && pauseCount >= 0) { "Loop and pause counts cannot be negative." }
        require(partType == 'p' || partType == 'c') { "Part type must be 'p' or 'c'." }

        // 1. Check video file size limits safely with .use
        val fileSize = context.contentResolver.openFileDescriptor(videoUri, "r")?.use {
            it.statSize
        } ?: 0L

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("Video file exceeds the 100MB limit (${fileSize / (1024 * 1024)}MB).")
        }

        val metaRetriever = MediaMetadataRetriever()
        var metaInitialized = false
        val totalDurationMs: Long
        try {
            metaRetriever.setDataSource(context, videoUri)
            metaInitialized = true
            val totalDurationStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            totalDurationMs = totalDurationStr?.toLongOrNull() ?: 0L
            if (totalDurationMs <= 0L) {
                throw IllegalArgumentException("Could not read video duration.")
            }
        } catch (e: Exception) {
            if (metaInitialized) {
                try { metaRetriever.release() } catch (ignored: Exception) {}
            }
            throw IllegalArgumentException("Could not load video. Invalid or corrupted format.")
        } finally {
            if (metaInitialized) {
                try { metaRetriever.release() } catch (ignored: Exception) {}
            }
        }

        val effectiveStartMs = startMs.coerceIn(0L, totalDurationMs)
        val minEndMs = (effectiveStartMs + 100L).coerceAtMost(totalDurationMs)
        val effectiveEndMs = if (endMs > 0L) endMs.coerceIn(minEndMs, totalDurationMs) else totalDurationMs
        val activeDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(100L)

        if (activeDurationMs > MAX_DURATION_MS) {
            throw IllegalArgumentException("Trimmed video exceeds 60s limit (${activeDurationMs / 1000}s).")
        }

        val isDualVideo = multiPartMode && multiPartSourceMode == MultiPartSourceMode.DUAL_VIDEO
        var loopEffectiveStartMs = 0L
        var loopEffectiveEndMs = 0L

        val part0TotalFrames: Int
        val basePart1Frames: Int
        val part1TotalFrames: Int
        val totalDecodedFrames: Int
        val totalTargetFrames: Int
        var effectiveSplitMs = effectiveEndMs

        if (isDualVideo) {
            require(loopVideoUri != null) { "Loop video URI cannot be null in dual video mode." }
            val loopFileSize = context.contentResolver.openFileDescriptor(loopVideoUri, "r")?.use {
                it.statSize
            } ?: 0L
            if (loopFileSize > MAX_FILE_SIZE_BYTES) {
                throw IllegalArgumentException("Loop video exceeds 100MB limit (${loopFileSize / (1024 * 1024)}MB).")
            }

            val loopRetriever = MediaMetadataRetriever()
            var loopMetaInit = false
            val loopTotalDurationMs: Long
            try {
                loopRetriever.setDataSource(context, loopVideoUri)
                loopMetaInit = true
                val loopDurStr = loopRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                loopTotalDurationMs = loopDurStr?.toLongOrNull() ?: 0L
                if (loopTotalDurationMs <= 0L) {
                    throw IllegalArgumentException("Could not read loop video duration.")
                }
            } catch (e: Exception) {
                if (loopMetaInit) {
                    try { loopRetriever.release() } catch (ignored: Exception) {}
                }
                throw IllegalArgumentException("Could not load loop video. Invalid or corrupted format.")
            } finally {
                if (loopMetaInit) {
                    try { loopRetriever.release() } catch (ignored: Exception) {}
                }
            }

            loopEffectiveStartMs = loopStartMs.coerceIn(0L, loopTotalDurationMs)
            val loopMinEnd = (loopEffectiveStartMs + 100L).coerceAtMost(loopTotalDurationMs)
            loopEffectiveEndMs = if (loopEndMs > 0L) loopEndMs.coerceIn(loopMinEnd, loopTotalDurationMs) else loopTotalDurationMs
            val loopActiveDurationMs = (loopEffectiveEndMs - loopEffectiveStartMs).coerceAtLeast(100L)
            if (loopActiveDurationMs > MAX_DURATION_MS) {
                throw IllegalArgumentException("Trimmed loop video exceeds 60s limit (${loopActiveDurationMs / 1000}s).")
            }

            part0TotalFrames = ((activeDurationMs / 1000.0) * targetFps).toInt().coerceAtLeast(1)
            basePart1Frames = ((loopActiveDurationMs / 1000.0) * targetFps).toInt().coerceAtLeast(1)
            part1TotalFrames = if (isPingPongEnabled && basePart1Frames >= 2) (2 * basePart1Frames - 2) else basePart1Frames
            totalDecodedFrames = part0TotalFrames + basePart1Frames
            totalTargetFrames = part0TotalFrames + part1TotalFrames
        } else {
            val durationSec = activeDurationMs / 1000.0
            val totalFramesInVideo = (durationSec * targetFps).toInt().coerceAtLeast(1)
            effectiveSplitMs = if (multiPartMode) {
                splitPointMs.coerceIn(effectiveStartMs, effectiveEndMs)
            } else effectiveEndMs

            val rawPart0Frames = if (multiPartMode) {
                (0 until totalFramesInVideo).count { i ->
                    val ts = effectiveStartMs + (i * 1000L) / targetFps
                    ts < effectiveSplitMs
                }
            } else {
                totalFramesInVideo
            }
            part0TotalFrames = if (multiPartMode && totalFramesInVideo >= 2) {
                rawPart0Frames.coerceIn(1, totalFramesInVideo - 1)
            } else {
                rawPart0Frames
            }
            basePart1Frames = if (multiPartMode) (totalFramesInVideo - part0TotalFrames).coerceAtLeast(1) else 0
            part1TotalFrames = if (multiPartMode && isPingPongEnabled && basePart1Frames >= 2) (2 * basePart1Frames - 2) else basePart1Frames
            totalDecodedFrames = if (multiPartMode) part0TotalFrames + basePart1Frames else part0TotalFrames
            totalTargetFrames = if (multiPartMode) part0TotalFrames + part1TotalFrames else part0TotalFrames
        }

        onProgress("Initializing frame extraction...", 0.0f)

        // 2. Format desc.txt according to standard AOSP bootanimation specification
        val bgHex = String.format(Locale.US, "#%06X", (0xFFFFFF and backgroundColor))
        val descContent = buildString {
            append("$canvasWidth $canvasHeight $targetFps\n")
            if (!multiPartMode) {
                append("$partType $loopCount $pauseCount part0 $bgHex\n")
            } else {
                append("$partType $part0LoopCount $part0PauseCount part0 $bgHex\n")
                append("$partType $part1LoopCount $part1PauseCount part1 $bgHex\n")
            }
        }
        val descBytes = descContent.toByteArray(Charsets.UTF_8)
        val descCrc = CRC32().apply { update(descBytes) }.value

        // 3. Concurrent streaming pipeline setup
        val availableCores = Runtime.getRuntime().availableProcessors()
        val workerCount = availableCores.coerceIn(2, 16)
        val decoderCount = if (totalDecodedFrames >= 12) availableCores.coerceIn(2, 4) else 1

        val rawChannel = Channel<RawFrameTask>(capacity = workerCount * 2)
        val compressedChannel = Channel<CompressedFrame>(capacity = workerCount * 2)

        val bitmapPool = BitmapPool(frameWidth, frameHeight, maxPoolSize = workerCount * 2)

        val initialBufferSize = if (imageFormat == ImageFormat.JPEG) {
            (frameWidth * frameHeight / 2).coerceIn(256 * 1024, 4 * 1024 * 1024)
        } else {
            (frameWidth * frameHeight * 4).coerceIn(1024 * 1024, 16 * 1024 * 1024)
        }
        val byteArrayPool = ByteArrayPool(initialBufferSize, maxPoolSize = workerCount * 2)
        val countingOut = CountingOutputStream(outputStream)

        ZipOutputStream(BufferedOutputStream(countingOut, 512 * 1024)).use { zipOut ->
            // Write desc.txt as the first entry in STORED mode
            addStoredEntryToZip(zipOut, "desc.txt", descBytes, descBytes.size, descCrc)

            try {
                coroutineScope {
                    // Producer: Hardware Video Decoder (GPU/MediaCodec) with automatic fallback to Multi-Retriever
                    launch(Dispatchers.IO) {
                        if (isDualVideo) {
                            // Decode Video 1 (Intro -> part0)
                            onProgress("Extracting Intro frames...", 0.05f)
                            val hwPart0Success = try {
                                HardwareVideoDecoder.decode(
                                    context = context,
                                    videoUri = videoUri,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                    targetFps = targetFps,
                                    scalingMode = scalingMode,
                                    backgroundColor = backgroundColor,
                                    rotationDegrees = rotationDegrees,
                                    startMs = effectiveStartMs,
                                    endMs = effectiveEndMs,
                                    multiPartMode = true,
                                    splitPointMs = 0L,
                                    totalTargetFrames = part0TotalFrames,
                                    part0TotalFrames = part0TotalFrames,
                                    bitmapPool = bitmapPool,
                                    rawChannel = rawChannel,
                                    frameIndexOffset = 0,
                                    overrideIsPart0 = true,
                                    isCancelled = isCancelled
                                )
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException || e is InterruptedException) throw e
                                false
                            }

                            if (!hwPart0Success) {
                                decodeWithMultiRetriever(
                                    context = context,
                                    videoUri = videoUri,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                    targetFps = targetFps,
                                    scalingMode = scalingMode,
                                    backgroundColor = backgroundColor,
                                    rotationDegrees = rotationDegrees,
                                    effectiveStartMs = effectiveStartMs,
                                    effectiveSplitMs = 0L,
                                    multiPartMode = true,
                                    totalTargetFrames = part0TotalFrames,
                                    part0TotalFrames = part0TotalFrames,
                                    decoderCount = decoderCount,
                                    bitmapPool = bitmapPool,
                                    rawChannel = rawChannel,
                                    frameIndexOffset = 0,
                                    overrideIsPart0 = true,
                                    isCancelled = isCancelled
                                )
                            }

                            // Decode Video 2 (Loop -> part1)
                            onProgress("Extracting Loop frames...", 0.35f)
                            val hwPart1Success = try {
                                HardwareVideoDecoder.decode(
                                    context = context,
                                    videoUri = loopVideoUri!!,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                    targetFps = targetFps,
                                    scalingMode = scalingMode,
                                    backgroundColor = backgroundColor,
                                    rotationDegrees = loopRotationDegrees,
                                    startMs = loopEffectiveStartMs,
                                    endMs = loopEffectiveEndMs,
                                    multiPartMode = true,
                                    splitPointMs = 0L,
                                    totalTargetFrames = basePart1Frames,
                                    part0TotalFrames = 0,
                                    bitmapPool = bitmapPool,
                                    rawChannel = rawChannel,
                                    frameIndexOffset = part0TotalFrames,
                                    overrideIsPart0 = false,
                                    isCancelled = isCancelled
                                )
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException || e is InterruptedException) throw e
                                false
                            }

                            if (!hwPart1Success) {
                                decodeWithMultiRetriever(
                                    context = context,
                                    videoUri = loopVideoUri!!,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                    targetFps = targetFps,
                                    scalingMode = scalingMode,
                                    backgroundColor = backgroundColor,
                                    rotationDegrees = loopRotationDegrees,
                                    effectiveStartMs = loopEffectiveStartMs,
                                    effectiveSplitMs = 0L,
                                    multiPartMode = true,
                                    totalTargetFrames = basePart1Frames,
                                    part0TotalFrames = 0,
                                    decoderCount = decoderCount,
                                    bitmapPool = bitmapPool,
                                    rawChannel = rawChannel,
                                    frameIndexOffset = part0TotalFrames,
                                    overrideIsPart0 = false,
                                    isCancelled = isCancelled
                                )
                            }
                        } else {
                            val hardwareSuccess = try {
                                HardwareVideoDecoder.decode(
                                    context = context,
                                    videoUri = videoUri,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                    targetFps = targetFps,
                                    scalingMode = scalingMode,
                                    backgroundColor = backgroundColor,
                                    rotationDegrees = rotationDegrees,
                                    startMs = effectiveStartMs,
                                    endMs = effectiveEndMs,
                                    multiPartMode = multiPartMode,
                                    splitPointMs = splitPointMs,
                                    totalTargetFrames = totalDecodedFrames,
                                    part0TotalFrames = part0TotalFrames,
                                    bitmapPool = bitmapPool,
                                    rawChannel = rawChannel,
                                    isCancelled = isCancelled
                                )
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: InterruptedException) {
                                throw e
                            } catch (e: Exception) {
                                false
                            }

                            if (!hardwareSuccess) {
                                decodeWithMultiRetriever(
                                    context = context,
                                    videoUri = videoUri,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight,
                                    targetFps = targetFps,
                                    scalingMode = scalingMode,
                                    backgroundColor = backgroundColor,
                                    rotationDegrees = rotationDegrees,
                                    effectiveStartMs = effectiveStartMs,
                                    effectiveSplitMs = effectiveSplitMs,
                                    multiPartMode = multiPartMode,
                                    totalTargetFrames = totalDecodedFrames,
                                    part0TotalFrames = part0TotalFrames,
                                    decoderCount = decoderCount,
                                    bitmapPool = bitmapPool,
                                    rawChannel = rawChannel,
                                    isCancelled = isCancelled
                                )
                            }
                        }
                        rawChannel.close()
                    }

                    // Worker Pool: Direct parallel frame compression & CRC32 (zero CPU re-rendering)
                    val workerJobs = (0 until workerCount).map {
                        launch(Dispatchers.Default) {
                            val pooledStream = PooledByteArrayOutputStream(byteArrayPool)
                            val crcCalculator = CRC32()
                            val fileExtension = if (imageFormat == ImageFormat.PNG) "png" else "jpg"

                            try {
                                for (task in rawChannel) {
                                    try {
                                        ensureActive()
                                        if (isCancelled()) throw InterruptedException("Operation cancelled by user.")

                                        pooledStream.reset()
                                        if (imageFormat == ImageFormat.PNG) {
                                            task.rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, pooledStream)
                                        } else {
                                            task.rawBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, pooledStream)
                                        }
                                    } finally {
                                        // Guarantee return to pool even upon cancellation or compression failure
                                        bitmapPool.release(task.rawBitmap)
                                    }

                                    val (frameBytes, frameLength) = pooledStream.detachBufferAndReset()
                                    crcCalculator.reset()
                                    crcCalculator.update(frameBytes, 0, frameLength)
                                    val crcValue = crcCalculator.value

                                    val dirName = if (task.isPart0) "part0" else "part1"
                                    val relativePath = String.format(Locale.US, "%s/%05d.%s", dirName, task.partFrameIndex, fileExtension)

                                    compressedChannel.send(
                                        CompressedFrame(task.frameIndex, relativePath, frameBytes, frameLength, crcValue)
                                    )
                                }
                            } finally {
                                pooledStream.release()
                            }
                        }
                    }

                    // Close compressedChannel once all workers finish
                    val coordinatorJob = launch(Dispatchers.Default) {
                        try {
                            workerJobs.joinAll()
                        } finally {
                            compressedChannel.close()
                        }
                    }

                    // Sequencer / Consumer: writes compressed frames strictly in ascending index order and returns buffers to pool
                    val pendingFrames = mutableMapOf<Int, CompressedFrame>()
                    val bounceDir = java.io.File(context.cacheDir, "bounce_${System.currentTimeMillis()}").apply { mkdirs() }
                    val fileExtension = if (imageFormat == ImageFormat.PNG) "png" else "jpg"
                    var maxPart1FrameIndex = -1

                    fun savePingPongFrameIfNeeded(frame: CompressedFrame) {
                        if (multiPartMode && isPingPongEnabled && frame.relativePath.startsWith("part1/")) {
                            val partFrameNum = frame.relativePath.substringAfter("part1/").substringBefore(".").toIntOrNull() ?: -1
                            if (partFrameNum >= 0) {
                                maxPart1FrameIndex = maxOf(maxPart1FrameIndex, partFrameNum)
                                // Stream intermediate frames to disk to prevent OOM
                                if (partFrameNum > 0) {
                                    val bounceFile = java.io.File(bounceDir, "frame_$partFrameNum")
                                    java.io.FileOutputStream(bounceFile).use { it.write(frame.bytes, 0, frame.length) }
                                }
                            }
                        }
                    }

                    try {
                        var nextExpectedIndex = 0
                        var writtenCount = 0

                        for (frame in compressedChannel) {
                            ensureActive()
                            if (isCancelled()) throw InterruptedException("Operation cancelled by user.")

                            if (frame.frameIndex == nextExpectedIndex) {
                                savePingPongFrameIfNeeded(frame)
                                addStoredEntryToZip(zipOut, frame.relativePath, frame.bytes, frame.length, frame.crc32)
                                byteArrayPool.release(frame.bytes)
                                writtenCount++
                                nextExpectedIndex++

                                val progress = 0.05f + (writtenCount.toFloat() / totalTargetFrames) * 0.90f
                                onProgress("Processing & packaging frame $writtenCount of $totalTargetFrames...", progress)

                                while (pendingFrames.containsKey(nextExpectedIndex)) {
                                    val nextFrame = pendingFrames.remove(nextExpectedIndex)!!
                                    savePingPongFrameIfNeeded(nextFrame)
                                    addStoredEntryToZip(zipOut, nextFrame.relativePath, nextFrame.bytes, nextFrame.length, nextFrame.crc32)
                                    byteArrayPool.release(nextFrame.bytes)
                                    writtenCount++
                                    nextExpectedIndex++

                                    val p = 0.05f + (writtenCount.toFloat() / totalTargetFrames) * 0.90f
                                    onProgress("Processing & packaging frame $writtenCount of $totalTargetFrames...", p)
                                }
                            } else {
                                pendingFrames[frame.frameIndex] = frame
                            }
                        }

                        // Write ping-pong reverse frames using actual decoded frame bounds
                        if (multiPartMode && isPingPongEnabled && maxPart1FrameIndex >= 2) {
                            val actualPart1Frames = maxPart1FrameIndex + 1
                            var bouncePartFrameIndex = actualPart1Frames
                            val crcCalc = CRC32()
                            for (reverseNum in (actualPart1Frames - 2) downTo 1) {
                                ensureActive()
                                if (isCancelled()) throw InterruptedException("Operation cancelled by user.")
                                val bounceFile = java.io.File(bounceDir, "frame_$reverseNum")
                                if (bounceFile.exists()) {
                                    val bBytes = bounceFile.readBytes()
                                    val bLen = bBytes.size
                                    crcCalc.reset()
                                    crcCalc.update(bBytes, 0, bLen)
                                    val bouncePath = String.format(Locale.US, "part1/%05d.%s", bouncePartFrameIndex, fileExtension)
                                    addStoredEntryToZip(zipOut, bouncePath, bBytes, bLen, crcCalc.value)
                                    bouncePartFrameIndex++
                                    writtenCount++
                                    val p = 0.05f + (writtenCount.toFloat() / totalTargetFrames) * 0.90f
                                    onProgress("Packaging Ping-Pong bounce frame $writtenCount of $totalTargetFrames...", p)
                                }
                            }
                        }
                    } finally {
                        bounceDir.deleteRecursively()
                        // Release any frames remaining in pendingFrames
                        for (frame in pendingFrames.values) {
                            byteArrayPool.release(frame.bytes)
                        }
                        pendingFrames.clear()

                        // Drain remaining tasks in case of cancellation/error to prevent bitmap/buffer leaks
                        while (true) {
                            val remaining = rawChannel.tryReceive().getOrNull() ?: break
                            bitmapPool.release(remaining.rawBitmap)
                        }
                        while (true) {
                            val remainingComp = compressedChannel.tryReceive().getOrNull() ?: break
                            byteArrayPool.release(remainingComp.bytes)
                        }
                    }

                    coordinatorJob.join()
                }
            } finally {
                bitmapPool.clear()
            }

            zipOut.flush()
        }

        onProgress("Done!", 1.0f)
        countingOut.bytesWritten
    }

    /**
     * Extracts a small sequence of lightweight sampled frames for live UI preview instantly in parallel.
     */
    suspend fun extractPreviewFrames(
        context: Context,
        videoUri: Uri,
        targetWidth: Int = 360,
        targetHeight: Int = 640,
        scalingMode: ScalingMode = ScalingMode.FIT_CENTER,
        backgroundColor: Int = 0xFF000000.toInt(),
        startMs: Long = 0L,
        endMs: Long = 0L,
        rotationDegrees: Int = 0,
        sampleCount: Int = 16
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val metaRetriever = MediaMetadataRetriever()
        var metaInit = false
        val durationMs: Long
        try {
            metaRetriever.setDataSource(context, videoUri)
            metaInit = true
            durationMs = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        } finally {
            if (metaInit) {
                try { metaRetriever.release() } catch (ignored: Exception) {}
            }
        }

        val effectiveStart = startMs.coerceIn(0L, durationMs)
        val minEnd = (effectiveStart + 100L).coerceAtMost(durationMs)
        val effectiveEnd = if (endMs > 0L) endMs.coerceIn(minEnd, durationMs) else durationMs
        val activeDuration = (effectiveEnd - effectiveStart).coerceAtLeast(100L)

        val stepMs = (activeDuration / sampleCount).coerceAtLeast(50L)
        val sampleTimesUs = (0 until sampleCount).map { i ->
            (effectiveStart + i * stepMs) * 1000L
        }

        val numThreads = minOf(4, sampleCount)
        val chunkSize = (sampleTimesUs.size + numThreads - 1) / numThreads

        coroutineScope {
            (0 until numThreads).map { threadIdx ->
                val chunkStart = threadIdx * chunkSize
                val chunkEnd = minOf(chunkStart + chunkSize, sampleTimesUs.size)
                if (chunkStart >= chunkEnd) return@map async { emptyList<Pair<Int, Bitmap>>() }

                async(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    var initialized = false
                    val threadResults = mutableListOf<Pair<Int, Bitmap>>()
                    try {
                        retriever.setDataSource(context, videoUri)
                        initialized = true
                        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                            isDither = true
                            isFilterBitmap = true
                        }

                        for (idx in chunkStart until chunkEnd) {
                            ensureActive()
                            val timeUs = sampleTimesUs[idx]
                            val raw = try {
                                retriever.getScaledFrameAtTime(
                                    timeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    targetWidth,
                                    targetHeight
                                ) ?: retriever.getScaledFrameAtTime(
                                    timeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST,
                                    targetWidth,
                                    targetHeight
                                ) ?: retriever.getFrameAtTime(timeUs)
                            } catch (e: Exception) {
                                null
                            } ?: continue

                            val output = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(output)
                            canvas.drawColor(backgroundColor)

                            val matrixToUse = computeTransformMatrix(
                                scalingMode, targetWidth, targetHeight, raw.width, raw.height, rotationDegrees
                            )
                            canvas.drawBitmap(raw, matrixToUse, paint)
                            raw.recycle()
                            threadResults.add(Pair(idx, output))
                        }
                    } catch (e: Exception) {
                        // Ignore extraction error for individual chunk
                    } finally {
                        if (initialized) {
                            try { retriever.release() } catch (ignored: Exception) {}
                        }
                    }
                    threadResults
                }
            }.awaitAll().flatten().sortedBy { it.first }.map { it.second }
        }
    }

    /**
     * Extracts distinct sample sequences for part0 and part1, with optional ping-pong expansion for part1.
     */
    suspend fun extractMultiPartPreviewFrames(
        context: Context,
        part0Uri: Uri,
        part0StartMs: Long,
        part0EndMs: Long,
        part0Rotation: Int,
        part1Uri: Uri,
        part1StartMs: Long,
        part1EndMs: Long,
        part1Rotation: Int,
        isPingPongEnabled: Boolean,
        targetWidth: Int = 360,
        targetHeight: Int = 640,
        scalingMode: ScalingMode = ScalingMode.FIT_CENTER,
        backgroundColor: Int = 0xFF000000.toInt(),
        sampleCountPerPart: Int = 12
    ): Pair<List<Bitmap>, List<Bitmap>> = withContext(Dispatchers.IO) {
        val part0Frames = extractPreviewFrames(
            context = context,
            videoUri = part0Uri,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scalingMode = scalingMode,
            backgroundColor = backgroundColor,
            startMs = part0StartMs,
            endMs = part0EndMs,
            rotationDegrees = part0Rotation,
            sampleCount = sampleCountPerPart
        )

        val basePart1Frames = extractPreviewFrames(
            context = context,
            videoUri = part1Uri,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scalingMode = scalingMode,
            backgroundColor = backgroundColor,
            startMs = part1StartMs,
            endMs = part1EndMs,
            rotationDegrees = part1Rotation,
            sampleCount = sampleCountPerPart
        )

        val part1Frames = if (isPingPongEnabled && basePart1Frames.size >= 3) {
            val bounce = basePart1Frames.drop(1).dropLast(1).reversed()
            basePart1Frames + bounce
        } else {
            basePart1Frames
        }

        Pair(part0Frames, part1Frames)
    }

    fun computeTransformMatrix(
        scalingMode: ScalingMode,
        frameWidth: Int,
        frameHeight: Int,
        srcW: Int,
        srcH: Int,
        rotationDegrees: Int
    ): Matrix {
        val matrix = Matrix()
        // 1. Center the raw frame at origin (0, 0)
        matrix.postTranslate(-srcW / 2f, -srcH / 2f)

        // 2. Apply rotation
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        // 3. Calculate effective dimensions after rotation
        val effW = if (rotationDegrees == 90 || rotationDegrees == 270) srcH.toFloat() else srcW.toFloat()
        val effH = if (rotationDegrees == 90 || rotationDegrees == 270) srcW.toFloat() else srcH.toFloat()

        // 4. Apply scale to fit destination frame dimensions
        when (scalingMode) {
            ScalingMode.STRETCH -> {
                matrix.postScale(frameWidth / effW, frameHeight / effH)
            }
            ScalingMode.FIT_CENTER -> {
                val scale = Math.min(frameWidth / effW, frameHeight / effH)
                matrix.postScale(scale, scale)
            }
            ScalingMode.CENTER_CROP -> {
                val scale = Math.max(frameWidth / effW, frameHeight / effH)
                matrix.postScale(scale, scale)
            }
        }

        // 5. Translate to canvas center
        matrix.postTranslate(frameWidth / 2f, frameHeight / 2f)
        return matrix
    }

    private fun decodeFrameWithFallback(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        dstWidth: Int,
        dstHeight: Int
    ): Bitmap? {
        var raw: Bitmap? = try {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, dstWidth, dstHeight)
        } catch (e: Throwable) {
            null
        }
        if (raw == null) {
            raw = try {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (e: Throwable) {
                null
            }
        }
        if (raw == null) {
            raw = try {
                retriever.getFrameAtTime(timeUs)
            } catch (e: Throwable) {
                null
            }
        }
        return raw
    }

    private suspend fun decodeWithMultiRetriever(
        context: Context,
        videoUri: Uri,
        frameWidth: Int,
        frameHeight: Int,
        targetFps: Int,
        scalingMode: ScalingMode,
        backgroundColor: Int,
        rotationDegrees: Int,
        effectiveStartMs: Long,
        effectiveSplitMs: Long,
        multiPartMode: Boolean,
        totalTargetFrames: Int,
        part0TotalFrames: Int,
        decoderCount: Int,
        bitmapPool: BitmapPool,
        rawChannel: Channel<RawFrameTask>,
        frameIndexOffset: Int = 0,
        overrideIsPart0: Boolean? = null,
        isCancelled: () -> Boolean
    ) = coroutineScope {
        val sliceSize = (totalTargetFrames + decoderCount - 1) / decoderCount
        val producerJobs = (0 until decoderCount).mapNotNull { d ->
            val sliceStart = d * sliceSize
            val sliceEnd = minOf(sliceStart + sliceSize, totalTargetFrames)
            if (sliceStart >= sliceEnd) return@mapNotNull null

            launch(Dispatchers.IO) {
                val sliceRetriever = MediaMetadataRetriever()
                var initialized = false
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    isDither = true
                    isFilterBitmap = true
                }
                try {
                    sliceRetriever.setDataSource(context, videoUri)
                    initialized = true
                } catch (e: Exception) {
                    // Fallback
                }

                try {
                    if (initialized) {
                        for (i in sliceStart until sliceEnd) {
                            ensureActive()
                            if (isCancelled()) throw InterruptedException("Operation cancelled by user.")

                            val currentTimestampMs = effectiveStartMs + (i * 1000L) / targetFps
                            val timeUs = currentTimestampMs * 1000L

                            val rawFrame = decodeFrameWithFallback(
                                retriever = sliceRetriever,
                                timeUs = timeUs,
                                dstWidth = frameWidth,
                                dstHeight = frameHeight
                            )

                            val outputBitmap = bitmapPool.acquire()
                            val canvas = Canvas(outputBitmap)
                            canvas.drawColor(backgroundColor)

                            if (rawFrame != null) {
                                val matrixToUse = computeTransformMatrix(
                                    scalingMode, frameWidth, frameHeight, rawFrame.width, rawFrame.height, rotationDegrees
                                )
                                canvas.drawBitmap(rawFrame, matrixToUse, paint)
                                rawFrame.recycle()
                            }

                            val isPart0 = overrideIsPart0 ?: (!multiPartMode || i < part0TotalFrames)
                            val partFrameIndex = if (overrideIsPart0 != null) i else (if (isPart0) i else i - part0TotalFrames)
                            val globalFrameIndex = frameIndexOffset + i

                            rawChannel.send(RawFrameTask(globalFrameIndex, isPart0, partFrameIndex, outputBitmap))
                        }
                    } else {
                        for (i in sliceStart until sliceEnd) {
                            ensureActive()
                            val isPart0 = overrideIsPart0 ?: (!multiPartMode || i < part0TotalFrames)
                            val partFrameIndex = if (overrideIsPart0 != null) i else (if (isPart0) i else i - part0TotalFrames)
                            val globalFrameIndex = frameIndexOffset + i
                            val blank = bitmapPool.acquire()
                            val canvas = Canvas(blank)
                            canvas.drawColor(backgroundColor)
                            rawChannel.send(RawFrameTask(globalFrameIndex, isPart0, partFrameIndex, blank))
                        }
                    }
                } finally {
                    if (initialized) {
                        try { sliceRetriever.release() } catch (ignored: Exception) {}
                    }
                }
            }
        }
        producerJobs.joinAll()
    }

    private fun addStoredEntryToZip(
        zipOut: ZipOutputStream,
        relativePath: String,
        bytes: ByteArray,
        length: Int,
        crc32: Long
    ) {
        val entry = ZipEntry(relativePath).apply {
            method = ZipEntry.STORED
            size = length.toLong()
            compressedSize = length.toLong()
            crc = crc32
        }
        zipOut.putNextEntry(entry)
        zipOut.write(bytes, 0, length)
        zipOut.closeEntry()
    }
}
