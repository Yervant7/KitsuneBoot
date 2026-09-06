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

package com.yervant.kitsuneboot.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yervant.kitsuneboot.BootAnimationUiState
import com.yervant.kitsuneboot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSourceSection(
    uiState: BootAnimationUiState,
    onChooseVideo: () -> Unit,
    onTrimRangeChanged: (Long, Long) -> Unit,
    onRotationChanged: (Int) -> Unit,
    onChooseLoopVideo: () -> Unit = {},
    onLoopTrimRangeChanged: (Long, Long) -> Unit = { _, _ -> },
    onLoopRotationChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SectionCard(
        stepNumber = 1,
        title = stringResource(R.string.step_1_title),
        subtitle = stringResource(R.string.step_1_subtitle),
        modifier = modifier
    ) {
        // Main Video Slot (always visible)
        VideoCardSlot(
            slotTitle = null,
            badgeText = null,
            videoUri = uiState.videoUri,
            videoName = uiState.videoName,
            videoDurationMs = uiState.videoDurationMs,
            videoStartMs = uiState.videoStartMs,
            videoEndMs = uiState.videoEndMs,
            rotationDegrees = uiState.rotationDegrees,
            detectedFps = uiState.detectedFps,
            canvasWidth = uiState.canvasWidth,
            canvasHeight = uiState.canvasHeight,
            isGenerating = uiState.isGenerating,
            onChooseVideo = onChooseVideo,
            onTrimRangeChanged = onTrimRangeChanged,
            onRotationChanged = onRotationChanged,
            chooseButtonText = stringResource(R.string.choose_video_button),
            dropHintText = stringResource(R.string.drop_video_hint)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Loop Video Section Divider — always visible, clearly marked Optional
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.loop_video_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.loop_video_section_optional),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loop_video_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Loop Video Card Slot
        VideoCardSlot(
            slotTitle = null,
            badgeText = stringResource(R.string.part1_badge),
            videoUri = uiState.loopVideoUri,
            videoName = uiState.loopVideoName,
            videoDurationMs = uiState.loopVideoDurationMs,
            videoStartMs = uiState.loopVideoStartMs,
            videoEndMs = uiState.loopVideoEndMs,
            rotationDegrees = uiState.loopRotationDegrees,
            detectedFps = uiState.loopDetectedFps,
            canvasWidth = uiState.canvasWidth,
            canvasHeight = uiState.canvasHeight,
            isGenerating = uiState.isGenerating,
            onChooseVideo = onChooseLoopVideo,
            onTrimRangeChanged = onLoopTrimRangeChanged,
            onRotationChanged = onLoopRotationChanged,
            chooseButtonText = stringResource(R.string.choose_loop_video_button),
            dropHintText = stringResource(R.string.drop_loop_video_hint),
            isOptional = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoCardSlot(
    slotTitle: String?,
    badgeText: String?,
    videoUri: Uri?,
    videoName: String,
    videoDurationMs: Long,
    videoStartMs: Long,
    videoEndMs: Long,
    rotationDegrees: Int,
    detectedFps: Float?,
    canvasWidth: String,
    canvasHeight: String,
    isGenerating: Boolean,
    onChooseVideo: () -> Unit,
    onTrimRangeChanged: (Long, Long) -> Unit,
    onRotationChanged: (Int) -> Unit,
    chooseButtonText: String,
    dropHintText: String,
    isOptional: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (slotTitle != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = slotTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (badgeText != null) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (videoUri == null) {
            // Empty State Dropzone
            val borderColor = if (isOptional) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !isGenerating, onClick = onChooseVideo),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.5.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isOptional) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isOptional) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = chooseButtonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOptional) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dropHintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Selected Video Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = videoName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (videoDurationMs > 0) {
                                val totalSec = videoDurationMs / 1000.0
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", totalSec)}s • $canvasWidth×$canvasHeight" +
                                            (detectedFps?.let { " • ${it.toInt()} FPS" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onChooseVideo,
                            enabled = !isGenerating,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.change_video_button), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Trimming & Rotation
            AnimatedVisibility(
                visible = videoDurationMs > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val startSec = videoStartMs / 1000.0
                    val endSec = videoEndMs / 1000.0
                    val activeSec = maxOf(0.0, endSec - startSec)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.trim_start_label, startSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.trim_duration_badge, activeSec),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        Text(
                            text = stringResource(R.string.trim_end_label, endSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val maxDuration = maxOf(1f, videoDurationMs.toFloat())
                    val rangeStart = videoStartMs.toFloat().coerceIn(0f, maxDuration)
                    val rangeEnd = videoEndMs.toFloat().coerceIn(rangeStart, maxDuration)

                    RangeSlider(
                        value = rangeStart..rangeEnd,
                        onValueChange = { range ->
                            onTrimRangeChanged(range.start.toLong(), range.endInclusive.toLong())
                        },
                        valueRange = 0f..maxDuration,
                        enabled = !isGenerating
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.rotation_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val rotationOptions = listOf(
                        0 to R.string.rotation_0,
                        90 to R.string.rotation_90,
                        180 to R.string.rotation_180,
                        270 to R.string.rotation_270
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        rotationOptions.forEachIndexed { index, (deg, labelRes) ->
                            val isSelected = rotationDegrees == deg
                            SegmentedButton(
                                selected = isSelected,
                                onClick = { onRotationChanged(deg) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = rotationOptions.size),
                                enabled = !isGenerating
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
