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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yervant.kitsuneboot.BootAnimationUiState
import com.yervant.kitsuneboot.R

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.luminance

private val PALETTE_COLORS = listOf(
    Color(0xFF000000), // OLED Black
    Color(0xFFFFFFFF), // Pure White
    Color(0xFF1E1E22), // Dark Charcoal
    Color(0xFF37474F), // Slate Grey
    Color(0xFFD84315), // Kitsune Flame
    Color(0xFFE53935), // Crimson Red
    Color(0xFFFB8C00), // Sunset Orange
    Color(0xFFFFB300), // Golden Amber
    Color(0xFF43A047), // Emerald Green
    Color(0xFF1E88E5), // Royal Blue
    Color(0xFF8E24AA)  // Deep Purple
)

@Composable
fun ColorPickerRow(
    uiState: BootAnimationUiState,
    onColorSelected: (Color) -> Unit,
    onHexChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.background_color_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Color Swatches
        HorizontalScrollContainer(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PALETTE_COLORS.forEach { color ->
                val isSelected = uiState.customHexColor.isBlank() && uiState.backgroundColor == color
                val contrastCheckColor = if (color.luminance() > 0.5f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            BorderStroke(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            shape = CircleShape
                        )
                        .clickable(enabled = !uiState.isGenerating) {
                            onColorSelected(color)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = contrastCheckColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Hex Input with Live Color Swatch
        OutlinedTextField(
            value = uiState.customHexColor,
            onValueChange = onHexChanged,
            label = { Text(stringResource(R.string.custom_hex_label)) },
            placeholder = { Text("#000000") },
            isError = !uiState.isCustomHexValid,
            supportingText = {
                if (!uiState.isCustomHexValid) {
                    Text(stringResource(R.string.hex_error_hint))
                }
            },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(uiState.effectiveColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isGenerating,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
