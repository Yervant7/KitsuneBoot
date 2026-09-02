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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yervant.kitsuneboot.BootAnimationUiState
import com.yervant.kitsuneboot.R
import com.yervant.kitsuneboot.ResolutionPreset

@Composable
fun ResolutionSection(
    uiState: BootAnimationUiState,
    onDetectDevice: () -> Unit,
    onPresetSelected: (ResolutionPreset) -> Unit,
    onCanvasWidthChanged: (String) -> Unit,
    onCanvasHeightChanged: (String) -> Unit,
    onFrameScalePercentChanged: (Int) -> Unit,
    onCustomFrameResolutionChanged: (Boolean) -> Unit,
    onFrameWidthChanged: (String) -> Unit,
    onFrameHeightChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        stepNumber = 2,
        title = stringResource(R.string.step_2_title),
        subtitle = stringResource(R.string.step_2_subtitle),
        modifier = modifier
    ) {
        // Quick Presets Row Header with Device Detect Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.quick_presets),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${BootAnimationUiState.PRESETS.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            FilledTonalButton(
                onClick = onDetectDevice,
                enabled = !uiState.isGenerating,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.detect_device_res_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalScrollContainer(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BootAnimationUiState.PRESETS.forEach { preset ->
                val isSelected = uiState.canvasWidth == preset.width.toString() &&
                        uiState.canvasHeight == preset.height.toString()

                FilterChip(
                    selected = isSelected,
                    onClick = { onPresetSelected(preset) },
                    label = {
                        Text(
                            text = preset.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    enabled = !uiState.isGenerating,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Canvas Display Resolution
        Text(
            text = stringResource(R.string.canvas_resolution_title),
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
                value = uiState.canvasWidth,
                onValueChange = onCanvasWidthChanged,
                label = { Text(stringResource(R.string.canvas_width_label)) },
                suffix = {
                    Text(
                        stringResource(R.string.px_unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = !uiState.isCanvasWidthValid,
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isGenerating,
                shape = RoundedCornerShape(12.dp)
            )

            Box(
                modifier = Modifier.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = uiState.canvasHeight,
                onValueChange = onCanvasHeightChanged,
                label = { Text(stringResource(R.string.canvas_height_label)) },
                suffix = {
                    Text(
                        stringResource(R.string.px_unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = !uiState.isCanvasHeightValid,
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isGenerating,
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (!uiState.isCanvasWidthValid || !uiState.isCanvasHeightValid) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.resolution_range_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Frame Resolution & Scale Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.frame_scale_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.frame_scale_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Frame Resolution Result Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val frameW = uiState.frameWidth.toIntOrNull() ?: 1080
                    val frameH = uiState.frameHeight.toIntOrNull() ?: 2400
                    Text(
                        text = stringResource(R.string.frame_scale_result, uiState.frameScalePercent, frameW, frameH),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Percentage Chips
                val scalePresets = listOf(
                    100 to R.string.scale_100,
                    75 to R.string.scale_75,
                    50 to R.string.scale_50,
                    33 to R.string.scale_33,
                    25 to R.string.scale_25
                )

                HorizontalScrollContainer(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    scalePresets.forEach { (percent, labelRes) ->
                        val isSelected = !uiState.customFrameResolution && uiState.frameScalePercent == percent
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCustomFrameResolutionChanged(false)
                                onFrameScalePercentChanged(percent)
                            },
                            label = { Text(stringResource(labelRes), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            enabled = !uiState.isGenerating,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Percentage Slider
                Slider(
                    value = uiState.frameScalePercent.toFloat(),
                    onValueChange = {
                        onCustomFrameResolutionChanged(false)
                        onFrameScalePercentChanged(it.toInt())
                    },
                    valueRange = 10f..100f,
                    enabled = !uiState.isGenerating && !uiState.customFrameResolution,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Manual Dimension Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.custom_frame_resolution_switch),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = uiState.customFrameResolution,
                        onCheckedChange = onCustomFrameResolutionChanged,
                        enabled = !uiState.isGenerating
                    )
                }

                // Custom Manual Frame Inputs (Expandable when manual is checked)
                AnimatedVisibility(
                    visible = uiState.customFrameResolution,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.frameWidth,
                                onValueChange = onFrameWidthChanged,
                                label = { Text(stringResource(R.string.frame_width_label)) },
                                suffix = {
                                    Text(
                                        stringResource(R.string.px_unit),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                isError = !uiState.isFrameWidthValid,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !uiState.isGenerating,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "×",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = uiState.frameHeight,
                                onValueChange = onFrameHeightChanged,
                                label = { Text(stringResource(R.string.frame_height_label)) },
                                suffix = {
                                    Text(
                                        stringResource(R.string.px_unit),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                isError = !uiState.isFrameHeightValid,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !uiState.isGenerating,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (!uiState.isFrameWidthValid || !uiState.isFrameHeightValid) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.resolution_range_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
