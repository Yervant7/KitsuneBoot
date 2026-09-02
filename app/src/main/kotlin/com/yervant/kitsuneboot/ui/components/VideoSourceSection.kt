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
    modifier: Modifier = Modifier
) {
    SectionCard(
        stepNumber = 1,
        title = stringResource(R.string.step_1_title),
        subtitle = stringResource(R.string.step_1_subtitle),
        modifier = modifier
    ) {
        if (uiState.videoUri == null) {
            // Empty State Dropzone
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !uiState.isGenerating, onClick = onChooseVideo),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.choose_video_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.drop_video_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Selected Video Header Card
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
                                text = uiState.videoName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (uiState.videoDurationMs > 0) {
                                val totalSec = uiState.videoDurationMs / 1000.0
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", totalSec)}s • ${uiState.canvasWidth}×${uiState.canvasHeight}" +
                                            (uiState.detectedFps?.let { " • ${it.toInt()} FPS" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onChooseVideo,
                            enabled = !uiState.isGenerating,
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

            // Trimming & Rotation (Visible when video has duration)
            AnimatedVisibility(
                visible = uiState.videoDurationMs > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    val startSec = uiState.videoStartMs / 1000.0
                    val endSec = uiState.videoEndMs / 1000.0
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

                    val maxDuration = maxOf(1f, uiState.videoDurationMs.toFloat())
                    val rangeStart = uiState.videoStartMs.toFloat().coerceIn(0f, maxDuration)
                    val rangeEnd = uiState.videoEndMs.toFloat().coerceIn(rangeStart, maxDuration)

                    RangeSlider(
                        value = rangeStart..rangeEnd,
                        onValueChange = { range ->
                            onTrimRangeChanged(range.start.toLong(), range.endInclusive.toLong())
                        },
                        valueRange = 0f..maxDuration,
                        enabled = !uiState.isGenerating
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.rotation_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val rotationOptions = listOf(
                        0 to R.string.rotation_0,
                        90 to R.string.rotation_90,
                        180 to R.string.rotation_180,
                        270 to R.string.rotation_270
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        rotationOptions.forEachIndexed { index, (deg, labelRes) ->
                            val isSelected = uiState.rotationDegrees == deg
                            SegmentedButton(
                                selected = isSelected,
                                onClick = { onRotationChanged(deg) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = rotationOptions.size),
                                enabled = !uiState.isGenerating
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    fontSize = 13.sp,
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
