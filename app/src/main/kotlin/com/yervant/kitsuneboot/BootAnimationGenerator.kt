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

        val durationSec = activeDurationMs / 1000.0
        val totalTargetFrames = (durationSec * targetFps).toInt().coerceAtLeast(1)
        val effectiveSplitMs = if (multiPartMode) {
            splitPointMs.coerceIn(effectiveStartMs, effectiveEndMs)
        } else effectiveEndMs

        val rawPart0Frames = if (multiPartMode) {
            (0 until totalTargetFrames).count { i ->
                val ts = effectiveStartMs + (i * 1000L) / targetFps
                ts < effectiveSplitMs
            }
        } else {
            totalTargetFrames
        }
        val part0TotalFrames = if (multiPartMode && totalTargetFrames >= 2) {
            rawPart0Frames.coerceIn(1, totalTargetFrames - 1)
        } else {
            rawPart0Frames
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
        val decoderCount = if (totalTargetFrames >= 12) availableCores.coerceIn(2, 4) else 1

        val rawChannel = Channel<RawFrameTask>(capacity = workerCount * 2)
        val compressedChannel = Channel<CompressedFrame>(capacity = workerCount * 2)

        val bitmapPool = BitmapPool(frameWidth, frameHeight, maxPoolSize = workerCount * 3)

        val initialBufferSize = if (imageFormat == ImageFormat.JPEG) {
            (frameWidth * frameHeight / 2).coerceIn(256 * 1024, 4 * 1024 * 1024)
        } else {
            (frameWidth * frameHeight * 4).coerceIn(1024 * 1024, 16 * 1024 * 1024)
        }
        val byteArrayPool = ByteArrayPool(initialBufferSize, maxPoolSize = workerCount * 4)
        val countingOut = CountingOutputStream(outputStream)

        ZipOutputStream(BufferedOutputStream(countingOut, 512 * 1024)).use { zipOut ->
            // Write desc.txt as the first entry in STORED mode
            addStoredEntryToZip(zipOut, "desc.txt", descBytes, descBytes.size, descCrc)

            try {
                coroutineScope {
                    // Producer: Hardware Video Decoder (GPU/MediaCodec) with automatic fallback to Multi-Retriever
                    launch(Dispatchers.IO) {
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
                                totalTargetFrames = totalTargetFrames,
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
                            // Multi-Retriever Parallel Chunked Fallback
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
                                totalTargetFrames = totalTargetFrames,
                                part0TotalFrames = part0TotalFrames,
                                decoderCount = decoderCount,
                                bitmapPool = bitmapPool,
                                rawChannel = rawChannel,
                                isCancelled = isCancelled
                            )
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
                                    ensureActive()
                                    if (isCancelled()) throw InterruptedException("Operation cancelled by user.")

                                    pooledStream.reset()
                                    if (imageFormat == ImageFormat.PNG) {
                                        task.rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, pooledStream)
                                    } else {
                                        task.rawBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, pooledStream)
                                    }

                                    // Immediately return bitmap to pool
                                    bitmapPool.release(task.rawBitmap)

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
                    try {
                        var nextExpectedIndex = 0
                        var writtenCount = 0

                        for (frame in compressedChannel) {
                            ensureActive()
                            if (isCancelled()) throw InterruptedException("Operation cancelled by user.")

                            if (frame.frameIndex == nextExpectedIndex) {
                                addStoredEntryToZip(zipOut, frame.relativePath, frame.bytes, frame.length, frame.crc32)
                                byteArrayPool.release(frame.bytes)
                                writtenCount++
                                nextExpectedIndex++

                                val progress = 0.05f + (writtenCount.toFloat() / totalTargetFrames) * 0.90f
                                onProgress("Processing & packaging frame $writtenCount of $totalTargetFrames...", progress)

                                while (pendingFrames.containsKey(nextExpectedIndex)) {
                                    val nextFrame = pendingFrames.remove(nextExpectedIndex)!!
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
                    } finally {
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

                            val isPart0 = !multiPartMode || i < part0TotalFrames
                            val partFrameIndex = if (isPart0) i else i - part0TotalFrames

                            rawChannel.send(RawFrameTask(i, isPart0, partFrameIndex, outputBitmap))
                        }
                    } else {
                        for (i in sliceStart until sliceEnd) {
                            ensureActive()
                            val isPart0 = !multiPartMode || i < part0TotalFrames
                            val partFrameIndex = if (isPart0) i else i - part0TotalFrames
                            val blank = bitmapPool.acquire()
                            val canvas = Canvas(blank)
                            canvas.drawColor(backgroundColor)
                            rawChannel.send(RawFrameTask(i, isPart0, partFrameIndex, blank))
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
