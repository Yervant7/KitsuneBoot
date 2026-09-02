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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BootAnimationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BootAnimationUiState())
    val uiState: StateFlow<BootAnimationUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var previewJob: Job? = null

    fun onVideoSelected(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        val fileName = getFileName(appContext, uri)
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoName = fileName,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val captureRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
                val durationMs = durationStr?.toLongOrNull() ?: 0L

                val detectedFps = when {
                    captureRate != null && captureRate > 0f -> captureRate
                    frameCount != null && durationMs > 0L -> (frameCount / (durationMs / 1000f))
                    else -> null
                }
                val fpsString = if (detectedFps != null && detectedFps > 0f) {
                    detectedFps.toInt().coerceIn(1, 120).toString()
                } else null

                _uiState.update { state ->
                    state.copy(
                        videoDurationMs = durationMs,
                        videoStartMs = 0L,
                        videoEndMs = durationMs,
                        splitPointMs = durationMs / 2,
                        detectedFps = detectedFps,
                        canvasWidth = width ?: state.canvasWidth,
                        canvasHeight = height ?: state.canvasHeight,
                        frameWidth = width ?: state.frameWidth,
                        frameHeight = height ?: state.frameHeight,
                        fps = fpsString ?: state.fps
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Ignore metadata extraction errors and keep defaults
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }
    }

    fun onTrimRangeChanged(startMs: Long, endMs: Long) = _uiState.update {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart)
        it.copy(
            videoStartMs = safeStart,
            videoEndMs = safeEnd,
            splitPointMs = if (safeStart <= safeEnd) it.splitPointMs.coerceIn(safeStart, safeEnd) else safeStart
        )
    }

    fun onRotationChanged(degrees: Int) = _uiState.update { it.copy(rotationDegrees = degrees) }
    
    fun onFrameScalePercentChanged(percent: Int) = _uiState.update { state ->
        val safePercent = percent.coerceIn(10, 100)
        val cW = state.canvasWidth.toIntOrNull() ?: 1080
        val cH = state.canvasHeight.toIntOrNull() ?: 2400
        val fW = ((cW * safePercent) / 100).coerceAtLeast(100)
        val fH = ((cH * safePercent) / 100).coerceAtLeast(100)
        state.copy(
            frameScalePercent = safePercent,
            frameWidth = fW.toString(),
            frameHeight = fH.toString()
        )
    }

    fun onCustomFrameResolutionChanged(enabled: Boolean) = _uiState.update { state ->
        if (!enabled) {
            val cW = state.canvasWidth.toIntOrNull() ?: 1080
            val cH = state.canvasHeight.toIntOrNull() ?: 2400
            val fW = ((cW * state.frameScalePercent) / 100).coerceAtLeast(100)
            val fH = ((cH * state.frameScalePercent) / 100).coerceAtLeast(100)
            state.copy(
                customFrameResolution = false,
                frameWidth = fW.toString(),
                frameHeight = fH.toString()
            )
        } else {
            state.copy(customFrameResolution = true)
        }
    }

    fun onSyncFrameResolutionChanged(enabled: Boolean) = _uiState.update { state ->
        if (enabled) {
            val cW = state.canvasWidth.toIntOrNull() ?: 1080
            val cH = state.canvasHeight.toIntOrNull() ?: 2400
            val fW = ((cW * state.frameScalePercent) / 100).coerceAtLeast(100)
            val fH = ((cH * state.frameScalePercent) / 100).coerceAtLeast(100)
            state.copy(syncFrameResolution = true, customFrameResolution = false, frameWidth = fW.toString(), frameHeight = fH.toString())
        } else {
            state.copy(syncFrameResolution = false, customFrameResolution = true)
        }
    }

    fun onCanvasWidthChanged(value: String) = _uiState.update { state ->
        if (!state.customFrameResolution) {
            val cW = value.toIntOrNull() ?: (state.canvasWidth.toIntOrNull() ?: 1080)
            val fW = ((cW * state.frameScalePercent) / 100).coerceAtLeast(100)
            state.copy(canvasWidth = value, frameWidth = fW.toString())
        } else {
            state.copy(canvasWidth = value)
        }
    }

    fun onCanvasHeightChanged(value: String) = _uiState.update { state ->
        if (!state.customFrameResolution) {
            val cH = value.toIntOrNull() ?: (state.canvasHeight.toIntOrNull() ?: 2400)
            val fH = ((cH * state.frameScalePercent) / 100).coerceAtLeast(100)
            state.copy(canvasHeight = value, frameHeight = fH.toString())
        } else {
            state.copy(canvasHeight = value)
        }
    }

    fun onFrameWidthChanged(value: String) = _uiState.update { it.copy(frameWidth = value) }
    fun onFrameHeightChanged(value: String) = _uiState.update { it.copy(frameHeight = value) }
    fun onFpsChanged(value: String) = _uiState.update { it.copy(fps = value) }
    fun onLoopCountChanged(value: String) = _uiState.update { it.copy(loopCount = value) }
    fun onPauseCountChanged(value: String) = _uiState.update { it.copy(pauseCount = value) }
    fun onPartTypeChanged(value: Char) = _uiState.update { it.copy(partType = value) }
    fun onScalingModeChanged(value: ScalingMode) = _uiState.update { it.copy(scalingMode = value) }
    fun onImageFormatChanged(value: ImageFormat) = _uiState.update { it.copy(imageFormat = value) }
    fun onJpegQualityChanged(value: String) = _uiState.update { it.copy(jpegQuality = value) }
    fun onBackgroundColorChanged(value: Color) = _uiState.update { it.copy(backgroundColor = value, customHexColor = "") }
    fun onCustomHexColorChanged(value: String) = _uiState.update { it.copy(customHexColor = value) }

    fun onMultiPartModeChanged(enabled: Boolean) = _uiState.update { it.copy(multiPartMode = enabled) }
    fun onSplitPointChanged(value: Long) = _uiState.update { it.copy(splitPointMs = value) }
    fun onPart0LoopChanged(value: String) = _uiState.update { it.copy(part0LoopCount = value) }
    fun onPart0PauseChanged(value: String) = _uiState.update { it.copy(part0PauseCount = value) }
    fun onPart1LoopChanged(value: String) = _uiState.update { it.copy(part1LoopCount = value) }
    fun onPart1PauseChanged(value: String) = _uiState.update { it.copy(part1PauseCount = value) }

    fun onExportAsMagiskModuleChanged(enabled: Boolean) = _uiState.update { it.copy(exportAsMagiskModule = enabled) }
    fun onMagiskModuleNameChanged(value: String) = _uiState.update { it.copy(magiskModuleName = value) }
    fun onMagiskModuleAuthorChanged(value: String) = _uiState.update { it.copy(magiskModuleAuthor = value) }

    fun applyPreset(preset: ResolutionPreset) {
        _uiState.update { state ->
            val fW = if (!state.customFrameResolution) ((preset.width * state.frameScalePercent) / 100).coerceAtLeast(100) else preset.width
            val fH = if (!state.customFrameResolution) ((preset.height * state.frameScalePercent) / 100).coerceAtLeast(100) else preset.height
            state.copy(
                canvasWidth = preset.width.toString(),
                canvasHeight = preset.height.toString(),
                frameWidth = fW.toString(),
                frameHeight = fH.toString()
            )
        }
    }

    fun onDetectDeviceResolution(width: Int, height: Int) {
        _uiState.update { state ->
            val fW = if (!state.customFrameResolution) ((width * state.frameScalePercent) / 100).coerceAtLeast(100) else width
            val fH = if (!state.customFrameResolution) ((height * state.frameScalePercent) / 100).coerceAtLeast(100) else height
            state.copy(
                canvasWidth = width.toString(),
                canvasHeight = height.toString(),
                frameWidth = fW.toString(),
                frameHeight = fH.toString()
            )
        }
    }

    fun openPreview(context: Context) {
        val appContext = context.applicationContext
        val currentState = _uiState.value
        val uri = currentState.videoUri ?: return

        _uiState.update { it.copy(isPreviewDialogOpen = true, isPreviewLoading = true) }

        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val frames = BootAnimationGenerator.extractPreviewFrames(
                    context = appContext,
                    videoUri = uri,
                    targetWidth = 360,
                    targetHeight = 640,
                    scalingMode = currentState.scalingMode,
                    backgroundColor = currentState.effectiveColor.toArgb(),
                    startMs = currentState.videoStartMs,
                    endMs = currentState.videoEndMs,
                    rotationDegrees = currentState.rotationDegrees,
                    sampleCount = 16
                )
                _uiState.update {
                    it.copy(
                        isPreviewLoading = false,
                        previewBitmaps = frames
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isPreviewLoading = false,
                        previewBitmaps = emptyList(),
                        errorMessage = "Failed to load preview frames: ${e.message}"
                    )
                }
            }
        }
    }

    fun closePreview() {
        previewJob?.cancel()
        previewJob = null
        _uiState.update {
            it.copy(
                isPreviewDialogOpen = false,
                isPreviewLoading = false,
                previewBitmaps = emptyList()
            )
        }
    }

    fun inspectZip(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = BootAnimationParser.parseFromUri(appContext, uri)
                _uiState.update {
                    it.copy(
                        isInspectDialogOpen = true,
                        inspectionResult = info,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(errorMessage = "Could not parse bootanimation.zip: ${e.message}")
                }
            }
        }
    }

    fun closeInspectDialog() {
        _uiState.update { it.copy(isInspectDialogOpen = false, inspectionResult = null) }
    }

    fun applyInspectionToState(info: BootAnimationInfo) {
        _uiState.update {
            it.copy(
                canvasWidth = info.width.toString(),
                canvasHeight = info.height.toString(),
                frameWidth = info.width.toString(),
                frameHeight = info.height.toString(),
                fps = info.fps.toString(),
                isInspectDialogOpen = false
            )
        }
    }

    fun startGeneration(context: Context, outputUri: Uri) {
        if (_uiState.value.isGenerating || generationJob?.isActive == true) return

        val appContext = context.applicationContext
        val currentState = _uiState.value
        val videoUri = currentState.videoUri ?: return

        _uiState.update {
            it.copy(
                isGenerating = true,
                progressStep = "Starting...",
                progressValue = 0f,
                errorMessage = null,
                successMessage = null
            )
        }

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var generatedBootAnimBytes = 0L
            try {
                if (currentState.exportAsMagiskModule) {
                    // Generate temp bootanimation.zip then package as Magisk module
                    val tempBootAnim = File(appContext.cacheDir, "temp_bootanimation_${System.currentTimeMillis()}.zip")
                    try {
                        FileOutputStream(tempBootAnim).use { tempOut ->
                            generatedBootAnimBytes = generateAnimationToStream(appContext, currentState, videoUri, tempOut)
                        }

                        _uiState.update { it.copy(progressStep = "Packaging Magisk Module...", progressValue = 0.95f) }
                        appContext.contentResolver.openOutputStream(outputUri)?.use { magiskOut ->
                            MagiskModuleGenerator.createModule(
                                bootAnimFile = tempBootAnim,
                                moduleName = currentState.magiskModuleName,
                                moduleAuthor = currentState.magiskModuleAuthor,
                                outputStream = magiskOut
                            )
                        }
                        isSuccess = true
                    } finally {
                        tempBootAnim.delete()
                    }
                } else {
                    appContext.contentResolver.openOutputStream(outputUri)?.use { out ->
                        generatedBootAnimBytes = generateAnimationToStream(appContext, currentState, videoUri, out)
                    }
                    isSuccess = true
                }

                if (isSuccess) {
                    val sizeMb = generatedBootAnimBytes / (1024f * 1024f)
                    val isOom = generatedBootAnimBytes > BootAnimationUiState.OOM_THRESHOLD_BYTES
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            successMessage = if (currentState.exportAsMagiskModule) "Magisk Module created successfully!" else "bootanimation.zip created successfully!",
                            showOomWarningDialog = isOom,
                            generatedFileSizeMb = sizeMb
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                if (e !is InterruptedException) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = e.message ?: "An unexpected error occurred."
                        )
                    }
                } else {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            } finally {
                if (!isSuccess) {
                    withContext(NonCancellable) {
                        try {
                            DocumentsContract.deleteDocument(appContext.contentResolver, outputUri)
                        } catch (ignored: Exception) {
                            // Ignore cleanup errors
                        }
                    }
                }
            }
        }
    }

    private suspend fun generateAnimationToStream(
        appContext: Context,
        currentState: BootAnimationUiState,
        videoUri: Uri,
        out: java.io.OutputStream
    ): Long {
        return BootAnimationGenerator.generate(
            context = appContext,
            videoUri = videoUri,
            canvasWidth = currentState.canvasWidth.toIntOrNull() ?: 1080,
            canvasHeight = currentState.canvasHeight.toIntOrNull() ?: 2400,
            frameWidth = currentState.frameWidth.toIntOrNull() ?: 1080,
            frameHeight = currentState.frameHeight.toIntOrNull() ?: 2400,
            targetFps = currentState.fps.toIntOrNull() ?: 30,
            scalingMode = currentState.scalingMode,
            backgroundColor = currentState.effectiveColor.toArgb(),
            loopCount = currentState.loopCount.toIntOrNull() ?: 0,
            pauseCount = currentState.pauseCount.toIntOrNull() ?: 0,
            partType = currentState.partType,
            imageFormat = currentState.imageFormat,
            jpegQuality = currentState.jpegQuality.toIntOrNull() ?: 90,
            startMs = currentState.videoStartMs,
            endMs = currentState.videoEndMs,
            rotationDegrees = currentState.rotationDegrees,
            multiPartMode = currentState.multiPartMode,
            splitPointMs = currentState.splitPointMs,
            part0LoopCount = currentState.part0LoopCount.toIntOrNull() ?: 1,
            part0PauseCount = currentState.part0PauseCount.toIntOrNull() ?: 0,
            part1LoopCount = currentState.part1LoopCount.toIntOrNull() ?: 0,
            part1PauseCount = currentState.part1PauseCount.toIntOrNull() ?: 0,
            outputStream = out,
            onProgress = { step, progress ->
                _uiState.update {
                    it.copy(progressStep = step, progressValue = progress)
                }
            },
            isCancelled = { !_uiState.value.isGenerating }
        )
    }

    fun dismissOomWarningDialog() {
        _uiState.update { it.copy(showOomWarningDialog = false) }
    }

    fun cancelGeneration() {
        _uiState.update { it.copy(isGenerating = false) }
        generationJob?.cancel()
        generationJob = null
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore cursor query failure
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "video_file"
    }
}
