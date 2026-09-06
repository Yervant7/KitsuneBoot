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

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color

data class ResolutionPreset(
    val label: String,
    val width: Int,
    val height: Int
)

enum class MultiPartSourceMode {
    DUAL_VIDEO,
    SINGLE_SPLIT
}

data class BootAnimationUiState(
    val videoUri: Uri? = null,
    val videoName: String = "No video selected",
    val videoDurationMs: Long = 0L,
    val detectedFps: Float? = null,

    // Trimming & Rotation (Video 1 / Intro / Single)
    val videoStartMs: Long = 0L,
    val videoEndMs: Long = 0L,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270

    // Video 2: Loop (part1 in Dual Video Mode)
    val loopVideoUri: Uri? = null,
    val loopVideoName: String = "No video selected",
    val loopVideoDurationMs: Long = 0L,
    val loopDetectedFps: Float? = null,
    val loopVideoStartMs: Long = 0L,
    val loopVideoEndMs: Long = 0L,
    val loopRotationDegrees: Int = 0,

    // Boot Animation Canvas Resolution (desc.txt)
    val canvasWidth: String = "1080",
    val canvasHeight: String = "2400",

    // Image Frame Resolution (extracted bitmap size)
    val frameScalePercent: Int = 100, // 10% to 100% of canvas resolution
    val customFrameResolution: Boolean = false, // false = percentage scale, true = manual px
    val syncFrameResolution: Boolean = true,
    val frameWidth: String = "1080",
    val frameHeight: String = "2400",

    val fps: String = "30",
    val loopCount: String = "0",
    val pauseCount: String = "0",
    val partType: Char = 'p', // 'p' or 'c'
    val scalingMode: ScalingMode = ScalingMode.FIT_CENTER,
    val imageFormat: ImageFormat = ImageFormat.PNG,
    val jpegQuality: String = "90",
    val backgroundColor: Color = Color.Black,
    val customHexColor: String = "",

    // Multi-Part Mode
    val multiPartMode: Boolean = false,
    val multiPartSourceMode: MultiPartSourceMode = MultiPartSourceMode.DUAL_VIDEO,
    val splitPointMs: Long = 0L,
    val part0LoopCount: String = "1",
    val part0PauseCount: String = "0",
    val part0PauseSeconds: Float = 0f,
    val part1LoopCount: String = "0",
    val part1PauseCount: String = "0",
    val isPingPongEnabled: Boolean = false,
    val isCrossfadeEnabled: Boolean = false,
    val crossfadeFrames: Int = 8,

    // Magisk / KernelSU Module Export
    val exportAsMagiskModule: Boolean = false,
    val magiskModuleName: String = "Custom Boot Animation",
    val magiskModuleAuthor: String = "KitsuneBoot",

    // Generation & Preview state
    val isGenerating: Boolean = false,
    val progressStep: String = "",
    val progressValue: Float = 0f,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Preview
    val isPreviewDialogOpen: Boolean = false,
    val isPreviewLoading: Boolean = false,
    val previewBitmaps: List<Bitmap> = emptyList(),
    val part0PreviewBitmaps: List<Bitmap> = emptyList(),
    val part1PreviewBitmaps: List<Bitmap> = emptyList(),

    // Inspector
    val isInspectDialogOpen: Boolean = false,
    val inspectionResult: BootAnimationInfo? = null,

    // OOM Warning (>90MB)
    val showOomWarningDialog: Boolean = false,
    val generatedFileSizeMb: Float = 0f
) {
    val isCanvasWidthValid: Boolean
        get() = canvasWidth.toIntOrNull()?.let { it in 100..8000 } ?: false

    val isCanvasHeightValid: Boolean
        get() = canvasHeight.toIntOrNull()?.let { it in 100..8000 } ?: false

    val isFrameWidthValid: Boolean
        get() = frameWidth.toIntOrNull()?.let { it in 100..8000 } ?: false

    val isFrameHeightValid: Boolean
        get() = frameHeight.toIntOrNull()?.let { it in 100..8000 } ?: false

    val isFpsValid: Boolean
        get() = fps.toIntOrNull()?.let { it in 1..120 } ?: false

    val isJpegQualityValid: Boolean
        get() = if (imageFormat == ImageFormat.JPEG) {
            jpegQuality.toIntOrNull()?.let { it in 1..100 } ?: false
        } else true

    val isLoopCountValid: Boolean
        get() = loopCount.toIntOrNull()?.let { it >= 0 } ?: false

    val isPauseCountValid: Boolean
        get() = pauseCount.toIntOrNull()?.let { it >= 0 } ?: false

    val isPart0LoopValid: Boolean
        get() = part0LoopCount.toIntOrNull()?.let { it >= 0 } ?: false

    val isPart0PauseValid: Boolean
        get() = part0PauseCount.toIntOrNull()?.let { it >= 0 } ?: false

    val isPart1LoopValid: Boolean
        get() = part1LoopCount.toIntOrNull()?.let { it >= 0 } ?: false

    val isPart1PauseValid: Boolean
        get() = part1PauseCount.toIntOrNull()?.let { it >= 0 } ?: false

    val isCustomHexValid: Boolean
        get() = customHexColor.isEmpty() || customHexColor.matches("^#[0-9A-Fa-f]{6}$".toRegex())

    val isTrimRangeValid: Boolean
        get() = videoDurationMs <= 0L || (videoStartMs >= 0L && videoEndMs <= videoDurationMs && (videoEndMs - videoStartMs) >= 100L)

    val isLoopTrimRangeValid: Boolean
        get() = loopVideoDurationMs <= 0L || (loopVideoStartMs >= 0L && loopVideoEndMs <= loopVideoDurationMs && (loopVideoEndMs - loopVideoStartMs) >= 100L)

    val isMagiskFormValid: Boolean
        get() = !exportAsMagiskModule || (magiskModuleName.isNotBlank() && magiskModuleAuthor.isNotBlank())

    val effectiveColor: Color
        get() {
            if (customHexColor.isNotBlank() && isCustomHexValid) {
                return try {
                    val cleanHex = customHexColor.removePrefix("#")
                    val colorInt = cleanHex.toLong(16).toInt()
                    Color(colorInt or -0x1000000)
                } catch (e: Exception) {
                    backgroundColor
                }
            }
            return backgroundColor
        }

    val isFormValid: Boolean
        get() {
            val baseValid = videoUri != null &&
                    isCanvasWidthValid &&
                    isCanvasHeightValid &&
                    isFrameWidthValid &&
                    isFrameHeightValid &&
                    isFpsValid &&
                    isJpegQualityValid &&
                    isCustomHexValid &&
                    isTrimRangeValid &&
                    isMagiskFormValid

            if (!baseValid) return false

            if (!multiPartMode) {
                return isLoopCountValid && isPauseCountValid
            }

            val partsValid = isPart0LoopValid && isPart0PauseValid && isPart1LoopValid && isPart1PauseValid
            if (!partsValid) return false

            return if (multiPartSourceMode == MultiPartSourceMode.DUAL_VIDEO) {
                loopVideoUri != null && isLoopTrimRangeValid
            } else {
                true
            }
        }

    val targetFpsInt: Int
        get() = fps.toIntOrNull()?.coerceIn(1, 120) ?: 30

    /**
     * Estimates frames for part0 (Intro).
     */
    val part0EstimatedFrames: Int
        get() {
            if (!multiPartMode) return estimatedTotalFrames
            return if (multiPartSourceMode == MultiPartSourceMode.DUAL_VIDEO) {
                val dMs = if (videoDurationMs > 0L) {
                    (videoEndMs - videoStartMs).coerceIn(100L, 60_000L)
                } else 0L
                ((dMs / 1000.0) * targetFpsInt).toInt().coerceAtLeast(1)
            } else {
                val totalMs = if (videoDurationMs > 0L) (videoEndMs - videoStartMs).coerceIn(100L, 60_000L) else 0L
                val totalFrames = ((totalMs / 1000.0) * targetFpsInt).toInt().coerceAtLeast(1)
                val splitMs = splitPointMs.coerceIn(videoStartMs, videoEndMs)
                val part0Duration = (splitMs - videoStartMs).coerceAtLeast(0L)
                val rawCount = ((part0Duration / 1000.0) * targetFpsInt).toInt()
                if (totalFrames >= 2) rawCount.coerceIn(1, totalFrames - 1) else rawCount.coerceAtLeast(1)
            }
        }

    val part0EstimatedDurationSec: Float
        get() = part0EstimatedFrames / targetFpsInt.toFloat()

    /**
     * Estimates frames for part1 (Loop), including ping-pong duplication if enabled.
     */
    val part1EstimatedFrames: Int
        get() {
            if (!multiPartMode) return 0
            val baseFrames = if (multiPartSourceMode == MultiPartSourceMode.DUAL_VIDEO) {
                val dMs = if (loopVideoDurationMs > 0L) {
                    (loopVideoEndMs - loopVideoStartMs).coerceIn(100L, 60_000L)
                } else 0L
                ((dMs / 1000.0) * targetFpsInt).toInt().coerceAtLeast(1)
            } else {
                val totalMs = if (videoDurationMs > 0L) (videoEndMs - videoStartMs).coerceIn(100L, 60_000L) else 0L
                val totalFrames = ((totalMs / 1000.0) * targetFpsInt).toInt().coerceAtLeast(1)
                (totalFrames - part0EstimatedFrames).coerceAtLeast(1)
            }
            return if (isPingPongEnabled && baseFrames >= 2) {
                (2 * baseFrames - 2)
            } else {
                baseFrames
            }
        }

    val part1EstimatedDurationSec: Float
        get() = part1EstimatedFrames / targetFpsInt.toFloat()

    /**
     * Warns if the loop part is too long (> 150 frames or > 5 seconds),
     * as heavy loops risk consuming too much RAM during Android startup.
     */
    val isLoopLengthRisky: Boolean
        get() = multiPartMode && (part1EstimatedFrames > 150 || part1EstimatedDurationSec > 5.0f)

    /**
     * Total frames across all parts.
     */
    val estimatedTotalFrames: Int
        get() {
            if (!multiPartMode) {
                val durationMs = if (videoDurationMs > 0L) {
                    (videoEndMs - videoStartMs).coerceIn(100L, 60_000L)
                } else 0L
                return ((durationMs / 1000.0) * targetFpsInt).toInt().coerceAtLeast(1)
            }
            return part0EstimatedFrames + part1EstimatedFrames
        }

    /**
     * Estimates the generated bootanimation.zip file size in bytes.
     */
    val estimatedSizeBytes: Long
        get() {
            if (videoDurationMs <= 0L) return 0L
            val fWidth = frameWidth.toIntOrNull()?.coerceIn(100, 8000) ?: 1080
            val fHeight = frameHeight.toIntOrNull()?.coerceIn(100, 8000) ?: 2400
            val pixelsPerFrame = fWidth.toLong() * fHeight.toLong()

            val bytesPerFrame = if (imageFormat == ImageFormat.PNG) {
                (pixelsPerFrame * 0.45).toLong()
            } else {
                val q = jpegQuality.toIntOrNull()?.coerceIn(1, 100) ?: 90
                (pixelsPerFrame * (q / 100.0) * 0.15).toLong().coerceAtLeast(50 * 1024L)
            }
            return estimatedTotalFrames * bytesPerFrame
        }

    /**
     * Flag indicating whether the estimated size exceeds 90 MB.
     */
    val isEstimatedOomRisk: Boolean
        get() = estimatedSizeBytes > OOM_THRESHOLD_BYTES

    companion object {
        const val OOM_THRESHOLD_BYTES = 90 * 1024 * 1024L // 90 MB

        val PRESETS = listOf(
            ResolutionPreset("FHD+ (1080x2400)", 1080, 2400),
            ResolutionPreset("2K+ (1440x3200)", 1440, 3200),
            ResolutionPreset("FHD 16:9 (1080x1920)", 1080, 1920),
            ResolutionPreset("HD 16:9 (720x1280)", 720, 1280),
            ResolutionPreset("Square (1080x1080)", 1080, 1080)
        )
    }
}
