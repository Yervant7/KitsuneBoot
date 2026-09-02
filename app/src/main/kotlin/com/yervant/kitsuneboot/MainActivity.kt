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

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yervant.kitsuneboot.ui.components.*
import com.yervant.kitsuneboot.ui.theme.KitsuneBootTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 19f)
        horizontalLineToRelative(4f)
        lineTo(10f, 5f)
        lineTo(6f, 5f)
        verticalLineToRelative(14f)
        close()
        moveTo(14f, 5f)
        verticalLineToRelative(14f)
        horizontalLineToRelative(4f)
        lineTo(18f, 5f)
        horizontalLineToRelative(-4f)
        close()
    }.build()
}

private val SearchZipIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SearchZip",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(15.5f, 14f)
        horizontalLineToRelative(-0.79f)
        lineToRelative(-0.28f, -0.27f)
        curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
        curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
        reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
        reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
        curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
        lineToRelative(0.27f, 0.28f)
        verticalLineToRelative(0.79f)
        lineToRelative(5f, 4.99f)
        lineTo(20.49f, 19f)
        lineToRelative(-4.99f, -5f)
        close()
        moveTo(9.5f, 14f)
        curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
        reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
        reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
        reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
        close()
    }.build()
}

private val SkipPreviousIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SkipPrevious",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 6f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(12f)
        lineTo(6f, 18f)
        close()
        moveTo(9.5f, 12f)
        lineToRelative(8.5f, 6f)
        lineTo(18f, 6f)
        close()
    }.build()
}

private val SkipNextIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SkipNext",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 18f)
        lineToRelative(8.5f, -6f)
        lineTo(6f, 6f)
        verticalLineToRelative(12f)
        close()
        moveTo(16f, 6f)
        verticalLineToRelative(12f)
        horizontalLineToRelative(2f)
        lineTo(18f, 6f)
        horizontalLineToRelative(-2f)
        close()
    }.build()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KitsuneBootTheme {
                BootAnimationApp()
            }
        }
    }
}

@Suppress("LocalContextResourcesRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootAnimationApp(viewModel: BootAnimationViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var lastExportedUri by remember { mutableStateOf<Uri?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val shareActionLabel = stringResource(R.string.action_share)
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    val couldNotShareMsg = stringResource(R.string.error_could_not_share)

    // Handle toast/snackbar messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            viewModel.clearErrorMessage()
            scope.launch {
                snackbarHostState.showSnackbar("Error: $error")
            }
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            viewModel.clearSuccessMessage()
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = shareActionLabel,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    lastExportedUri?.let { uri ->
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
                        } catch (e: Exception) {
                            Toast.makeText(context, couldNotShareMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Video file picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onVideoSelected(context, it) }
    }

    // ZIP Inspector file picker
    val zipInspectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.inspectZip(context, it) }
    }

    // Export document picker
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { outputUri ->
            lastExportedUri = outputUri
            viewModel.startGeneration(context, outputUri)
        }
    }

    val selectVideoErrorMsg = stringResource(R.string.error_select_video_first)
    val checkFormErrorMsg = stringResource(R.string.error_check_form)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { zipInspectorLauncher.launch("application/zip") },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).padding(end = 4.dp)
                    ) {
                        Icon(SearchZipIcon, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.action_inspect_zip), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = { showAboutDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.action_about),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            BottomActionBar(
                uiState = uiState,
                onPreview = { viewModel.openPreview(context) },
                onCreateZip = {
                    if (uiState.videoUri == null) {
                        Toast.makeText(context, selectVideoErrorMsg, Toast.LENGTH_SHORT).show()
                    } else if (!uiState.isFormValid) {
                        Toast.makeText(context, checkFormErrorMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        val defaultName = if (uiState.exportAsMagiskModule) "KitsuneBootAnim_module.zip" else "bootanimation.zip"
                        exportLauncher.launch(defaultName)
                    }
                },
                onCancel = { viewModel.cancelGeneration() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header Hero Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.app_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step 1: Video Selection & Trimming
                VideoSourceSection(
                    uiState = uiState,
                    onChooseVideo = { videoPickerLauncher.launch("video/*") },
                    onTrimRangeChanged = { start, end -> viewModel.onTrimRangeChanged(start, end) },
                    onRotationChanged = { deg -> viewModel.onRotationChanged(deg) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Resolution & Presets
                ResolutionSection(
                    uiState = uiState,
                    onDetectDevice = {
                        val metrics = context.resources.displayMetrics
                        viewModel.onDetectDeviceResolution(metrics.widthPixels, metrics.heightPixels)
                    },
                    onPresetSelected = { preset -> viewModel.applyPreset(preset) },
                    onCanvasWidthChanged = { viewModel.onCanvasWidthChanged(it) },
                    onCanvasHeightChanged = { viewModel.onCanvasHeightChanged(it) },
                    onFrameScalePercentChanged = { viewModel.onFrameScalePercentChanged(it) },
                    onCustomFrameResolutionChanged = { viewModel.onCustomFrameResolutionChanged(it) },
                    onFrameWidthChanged = { viewModel.onFrameWidthChanged(it) },
                    onFrameHeightChanged = { viewModel.onFrameHeightChanged(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 3: Playback & Animation Loop
                PlaybackSection(
                    uiState = uiState,
                    onFpsChanged = { viewModel.onFpsChanged(it) },
                    onPartTypeChanged = { viewModel.onPartTypeChanged(it) },
                    onScalingModeChanged = { viewModel.onScalingModeChanged(it) },
                    onMultiPartModeChanged = { viewModel.onMultiPartModeChanged(it) },
                    onSplitPointChanged = { viewModel.onSplitPointChanged(it) },
                    onPart0LoopChanged = { viewModel.onPart0LoopChanged(it) },
                    onPart1LoopChanged = { viewModel.onPart1LoopChanged(it) },
                    onLoopCountChanged = { viewModel.onLoopCountChanged(it) },
                    onPauseCountChanged = { viewModel.onPauseCountChanged(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 4: Formatting & Quality
                FormatSection(
                    uiState = uiState,
                    onFormatChanged = { viewModel.onImageFormatChanged(it) },
                    onQualityChanged = { viewModel.onJpegQualityChanged(it) },
                    onColorChanged = { viewModel.onBackgroundColorChanged(it) },
                    onHexChanged = { viewModel.onCustomHexColorChanged(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 5: Export Type & Actions
                ExportSection(
                    uiState = uiState,
                    onExportMagiskChanged = { viewModel.onExportAsMagiskModuleChanged(it) },
                    onModuleNameChanged = { viewModel.onMagiskModuleNameChanged(it) },
                    onModuleAuthorChanged = { viewModel.onMagiskModuleAuthorChanged(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Preview Dialog
    if (uiState.isPreviewDialogOpen) {
        AnimationPreviewDialog(
            uiState = uiState,
            onDismiss = { viewModel.closePreview() }
        )
    }

    // Inspector Dialog
    if (uiState.isInspectDialogOpen && uiState.inspectionResult != null) {
        ZipInspectorDialog(
            info = uiState.inspectionResult!!,
            onApply = { viewModel.applyInspectionToState(it) },
            onDismiss = { viewModel.closeInspectDialog() }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    // OOM Risk Warning Dialog (>90MB)
    if (uiState.showOomWarningDialog) {
        OomWarningDialog(
            fileSizeMb = uiState.generatedFileSizeMb,
            onDismiss = { viewModel.dismissOomWarningDialog() }
        )
    }
}

@Composable
private fun BottomActionBar(
    uiState: BootAnimationUiState,
    onPreview: () -> Unit,
    onCreateZip: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isGenerating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.progressStep.ifEmpty { stringResource(R.string.generation_starting) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.cancel_generation_button), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                Row(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPreview,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = uiState.videoUri != null,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.preview_button), fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onCreateZip,
                        modifier = Modifier
                            .weight(1.6f)
                            .height(50.dp),
                        enabled = uiState.isFormValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (uiState.exportAsMagiskModule) R.string.create_magisk_button
                                else R.string.create_bootanimation_button
                            ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimationPreviewDialog(
    uiState: BootAnimationUiState,
    onDismiss: () -> Unit
) {
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    val frames = uiState.previewBitmaps

    LaunchedEffect(frames, isPlaying) {
        if (frames.isNotEmpty() && isPlaying) {
            val fps = uiState.fps.toIntOrNull()?.coerceIn(1, 60) ?: 30
            val delayMs = (1000L / fps).coerceAtLeast(16L)
            while (isPlaying) {
                delay(delayMs)
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.preview_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.preview_fps_label, uiState.fps.toIntOrNull() ?: 30),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val canvasW = uiState.canvasWidth.toFloatOrNull() ?: 1080f
                val canvasH = uiState.canvasHeight.toFloatOrNull() ?: 2400f
                val aspect = if (canvasH > 0f) (canvasW / canvasH).coerceIn(0.45f, 2.2f) else 0.5625f

                if (uiState.isPreviewLoading) {
                    Box(
                        modifier = Modifier
                            .height(280.dp)
                            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.preview_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (frames.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 200.dp, max = 300.dp)
                            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(16.dp))
                            .background(uiState.effectiveColor)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = frames[currentFrameIndex.coerceIn(0, frames.size - 1)].asImageBitmap(),
                            contentDescription = "Preview Frame",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.preview_frame_indicator, currentFrameIndex + 1, frames.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Scrubber Slider
                    Slider(
                        value = currentFrameIndex.toFloat().coerceIn(0f, maxOf(0f, (frames.size - 1).toFloat())),
                        onValueChange = {
                            isPlaying = false
                            currentFrameIndex = it.toInt().coerceIn(0, frames.size - 1)
                        },
                        valueRange = 0f..maxOf(1f, (frames.size - 1).toFloat()),
                        enabled = frames.size > 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isPlaying = false
                                currentFrameIndex = if (currentFrameIndex > 0) currentFrameIndex - 1 else frames.size - 1
                            },
                            enabled = frames.size > 1
                        ) {
                            Icon(
                                imageVector = SkipPreviousIcon,
                                contentDescription = "Previous Frame",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = { isPlaying = !isPlaying },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isPlaying) stringResource(R.string.pause_preview) else stringResource(R.string.play_preview),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = {
                                isPlaying = false
                                currentFrameIndex = (currentFrameIndex + 1) % frames.size
                            },
                            enabled = frames.size > 1
                        ) {
                            Icon(
                                imageVector = SkipNextIcon,
                                contentDescription = "Next Frame",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.no_preview_frames),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.close_button), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ZipInspectorDialog(
    info: BootAnimationInfo,
    onApply: (BootAnimationInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with icon and subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = SearchZipIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.inspector_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val sizeMb = info.fileSizeBytes / (1024f * 1024f)
                        Text(
                            text = if (info.fileSizeBytes > 0L) {
                                stringResource(R.string.inspector_file_size, sizeMb)
                            } else {
                                stringResource(R.string.inspector_subtitle)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // First frame preview thumbnail if available
                val previewBitmap = remember(info.previewImageBytes) {
                    info.previewImageBytes?.let { bytes ->
                        try {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }

                if (previewBitmap != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = stringResource(R.string.inspector_preview_label),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.inspector_preview_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (info.isModule) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.inspector_module_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (!info.moduleVersion.isNullOrBlank()) {
                                    Text(
                                        text = "v${info.moduleVersion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val name = info.moduleName ?: "Module"
                            val author = info.moduleAuthor ?: "Unknown"
                            Text(
                                text = stringResource(R.string.inspector_module_info, name, author),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (info.isOomRisk) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val sizeMb = info.fileSizeBytes / (1024f * 1024f)
                            Text(
                                text = stringResource(R.string.inspector_oom_warning, sizeMb),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.inspector_desc_resolution, info.width, info.height, info.fps),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val totalFrames = info.parts.sumOf { it.frameCount }
                        if (totalFrames > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.inspector_total_frames, totalFrames, info.parts.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.inspector_parts_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    info.parts.forEach { part ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val bgText = if (part.bgColor != null) " (bg=${part.bgColor})" else ""
                            Text(
                                text = stringResource(R.string.inspector_part_item, part.path, part.type, part.frameCount, part.count, part.pause, bgText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.close_button))
                    }
                    Button(
                        onClick = { onApply(info) },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.inspector_apply_button), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 480.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.about_dialog_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.about_license_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.about_license_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close_button), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun OomWarningDialog(
    fileSizeMb: Float,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 480.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.warning_oom_dialog_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = stringResource(R.string.warning_oom_dialog_message, fileSizeMb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.dismiss_warning_button), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun BootAnimationAppPreview() {
    KitsuneBootTheme {
        BootAnimationApp()
    }
}
