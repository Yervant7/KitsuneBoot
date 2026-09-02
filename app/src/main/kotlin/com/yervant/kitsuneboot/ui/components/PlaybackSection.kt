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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yervant.kitsuneboot.BootAnimationUiState
import com.yervant.kitsuneboot.R
import com.yervant.kitsuneboot.ScalingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSection(
    uiState: BootAnimationUiState,
    onFpsChanged: (String) -> Unit,
    onPartTypeChanged: (Char) -> Unit,
    onScalingModeChanged: (ScalingMode) -> Unit,
    onMultiPartModeChanged: (Boolean) -> Unit,
    onSplitPointChanged: (Long) -> Unit,
    onPart0LoopChanged: (String) -> Unit,
    onPart1LoopChanged: (String) -> Unit,
    onLoopCountChanged: (String) -> Unit,
    onPauseCountChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        stepNumber = 3,
        title = stringResource(R.string.step_3_title),
        subtitle = stringResource(R.string.step_3_subtitle),
        modifier = modifier
    ) {
        // Target FPS
        Text(
            text = stringResource(R.string.target_fps_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.fps,
                onValueChange = onFpsChanged,
                label = { Text("FPS") },
                suffix = {
                    Text(
                        "FPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = !uiState.isFpsValid,
                singleLine = true,
                modifier = Modifier.weight(1.1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isGenerating,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                modifier = Modifier.weight(1.5f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "24" to R.string.fps_preset_24,
                    "30" to R.string.fps_preset_30,
                    "60" to R.string.fps_preset_60
                ).forEach { (presetFps, stringRes) ->
                    val isFpsSelected = uiState.fps == presetFps
                    FilterChip(
                        selected = isFpsSelected,
                        onClick = { onFpsChanged(presetFps) },
                        label = {
                            Text(
                                stringResource(stringRes),
                                fontSize = 11.sp,
                                fontWeight = if (isFpsSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        enabled = !uiState.isGenerating,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (!uiState.isFpsValid) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.fps_range_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scaling Mode
        Text(
            text = stringResource(R.string.scaling_mode_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                ScalingMode.FIT_CENTER to R.string.scaling_fit_center,
                ScalingMode.CENTER_CROP to R.string.scaling_center_crop,
                ScalingMode.STRETCH to R.string.scaling_stretch
            )
            modes.forEachIndexed { index, (mode, labelRes) ->
                val isModeSelected = uiState.scalingMode == mode
                SegmentedButton(
                    selected = isModeSelected,
                    onClick = { onScalingModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    enabled = !uiState.isGenerating
                ) {
                    Text(
                        stringResource(labelRes),
                        fontSize = 12.sp,
                        maxLines = 1,
                        fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Part Type Selector
        Text(
            text = stringResource(R.string.part_type_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Two Selectable Option Cards for Part Type
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Option 'p'
            val isPSelected = uiState.partType == 'p'
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp)),
                color = if (isPSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = if (isPSelected) 1.5.dp else 1.dp,
                    color = if (isPSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp),
                onClick = { if (!uiState.isGenerating) onPartTypeChanged('p') }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.part_type_p_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.part_type_p_desc),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = if (isPSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Option 'c'
            val isCSelected = uiState.partType == 'c'
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp)),
                color = if (isCSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = if (isCSelected) 1.5.dp else 1.dp,
                    color = if (isCSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp),
                onClick = { if (!uiState.isGenerating) onPartTypeChanged('c') }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.part_type_c_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.part_type_c_desc),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = if (isCSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-Part Switch Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.multi_part_switch_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.multi_part_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = uiState.multiPartMode,
                    onCheckedChange = onMultiPartModeChanged,
                    enabled = !uiState.isGenerating && uiState.videoDurationMs > 0
                )
            }
        }

        // Multi-Part Controls vs Single-Part Controls
        AnimatedVisibility(
            visible = uiState.multiPartMode && uiState.videoDurationMs > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                val startSec = uiState.videoStartMs / 1000.0
                val splitSec = uiState.splitPointMs / 1000.0
                val endSec = uiState.videoEndMs / 1000.0
                val part0Duration = maxOf(0.0, splitSec - startSec)
                val part1Duration = maxOf(0.0, endSec - splitSec)

                // Visual Timeline Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.part0_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", part0Duration)}s (${String.format(java.util.Locale.US, "%.1f", startSec)}s–${String.format(java.util.Locale.US, "%.1f", splitSec)}s)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.part1_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", part1Duration)}s (${String.format(java.util.Locale.US, "%.1f", splitSec)}s–${String.format(java.util.Locale.US, "%.1f", endSec)}s)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val sliderMin = uiState.videoStartMs.toFloat()
                val sliderMax = maxOf(sliderMin + 1f, uiState.videoEndMs.toFloat())
                val splitValue = uiState.splitPointMs.toFloat().coerceIn(sliderMin, sliderMax)

                Slider(
                    value = splitValue,
                    onValueChange = { onSplitPointChanged(it.toLong()) },
                    valueRange = sliderMin..sliderMax,
                    enabled = !uiState.isGenerating
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.part0LoopCount,
                        onValueChange = onPart0LoopChanged,
                        label = { Text(stringResource(R.string.part0_loop_label)) },
                        isError = !uiState.isPart0LoopValid,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !uiState.isGenerating,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = uiState.part1LoopCount,
                        onValueChange = onPart1LoopChanged,
                        label = { Text(stringResource(R.string.part1_loop_label)) },
                        isError = !uiState.isPart1LoopValid,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !uiState.isGenerating,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !uiState.multiPartMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.loopCount,
                    onValueChange = onLoopCountChanged,
                    label = { Text(stringResource(R.string.loop_count_label)) },
                    isError = !uiState.isLoopCountValid,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !uiState.isGenerating,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = uiState.pauseCount,
                    onValueChange = onPauseCountChanged,
                    label = { Text(stringResource(R.string.pause_count_label)) },
                    isError = !uiState.isPauseCountValid,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !uiState.isGenerating,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}
