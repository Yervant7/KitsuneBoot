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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yervant.kitsuneboot.BootAnimationUiState
import com.yervant.kitsuneboot.ImageFormat
import com.yervant.kitsuneboot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSection(
    uiState: BootAnimationUiState,
    onFormatChanged: (ImageFormat) -> Unit,
    onQualityChanged: (String) -> Unit,
    onColorChanged: (Color) -> Unit,
    onHexChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        stepNumber = 4,
        title = stringResource(R.string.step_4_title),
        subtitle = stringResource(R.string.step_4_subtitle),
        modifier = modifier
    ) {
        // Image Format (Segmented Button Row)
        Text(
            text = stringResource(R.string.image_format_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val formats = listOf(
                ImageFormat.PNG to R.string.format_png,
                ImageFormat.JPEG to R.string.format_jpeg
            )
            formats.forEachIndexed { index, (format, labelRes) ->
                val isFormatSelected = uiState.imageFormat == format
                SegmentedButton(
                    selected = isFormatSelected,
                    onClick = { onFormatChanged(format) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                    enabled = !uiState.isGenerating
                ) {
                    Text(
                        stringResource(labelRes),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isFormatSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Estimated OOM Size Warning when high size estimated
        AnimatedVisibility(
            visible = uiState.isEstimatedOomRisk,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
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
                    Text(
                        text = stringResource(R.string.warning_oom_estimated_hint),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // JPEG Quality Setting (Visible only when JPEG is selected)
        AnimatedVisibility(
            visible = uiState.imageFormat == ImageFormat.JPEG,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val qualityInt = uiState.jpegQuality.toIntOrNull()?.coerceIn(1, 100) ?: 90

            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.jpeg_quality_label, qualityInt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Slider(
                    value = qualityInt.toFloat(),
                    onValueChange = { onQualityChanged(it.toInt().toString()) },
                    valueRange = 1f..100f,
                    enabled = !uiState.isGenerating
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Color Swatches & Hex
        ColorPickerRow(
            uiState = uiState,
            onColorSelected = onColorChanged,
            onHexChanged = onHexChanged
        )
    }
}
