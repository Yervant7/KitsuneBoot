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

package com.yervant.kitsuneboot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KitsunePrimaryDark,
    onPrimary = KitsuneOnPrimaryDark,
    primaryContainer = KitsunePrimaryContainerDark,
    onPrimaryContainer = KitsuneOnPrimaryContainerDark,
    secondary = KitsuneSecondaryDark,
    onSecondary = KitsuneOnSecondaryDark,
    secondaryContainer = KitsuneSecondaryContainerDark,
    onSecondaryContainer = KitsuneOnSecondaryContainerDark,
    tertiary = KitsuneTertiaryDark,
    onTertiary = KitsuneOnTertiaryDark,
    tertiaryContainer = KitsuneTertiaryContainerDark,
    onTertiaryContainer = KitsuneOnTertiaryContainerDark,
    error = KitsuneErrorDark,
    onError = KitsuneOnErrorDark,
    errorContainer = KitsuneErrorContainerDark,
    onErrorContainer = KitsuneOnErrorContainerDark,
    background = KitsuneBackgroundDark,
    onBackground = KitsuneOnBackgroundDark,
    surface = KitsuneSurfaceDark,
    onSurface = KitsuneOnSurfaceDark,
    surfaceVariant = KitsuneSurfaceVariantDark,
    onSurfaceVariant = KitsuneOnSurfaceVariantDark,
    outline = KitsuneOutlineDark,
    outlineVariant = KitsuneOutlineVariantDark,
    surfaceContainerLowest = KitsuneSurfaceContainerLowestDark,
    surfaceContainerLow = KitsuneSurfaceContainerLowDark,
    surfaceContainer = KitsuneSurfaceContainerDark,
    surfaceContainerHigh = KitsuneSurfaceContainerHighDark,
    surfaceContainerHighest = KitsuneSurfaceContainerHighestDark
)

private val LightColorScheme = lightColorScheme(
    primary = KitsunePrimaryLight,
    onPrimary = KitsuneOnPrimaryLight,
    primaryContainer = KitsunePrimaryContainerLight,
    onPrimaryContainer = KitsuneOnPrimaryContainerLight,
    secondary = KitsuneSecondaryLight,
    onSecondary = KitsuneOnSecondaryLight,
    secondaryContainer = KitsuneSecondaryContainerLight,
    onSecondaryContainer = KitsuneOnSecondaryContainerLight,
    tertiary = KitsuneTertiaryLight,
    onTertiary = KitsuneOnTertiaryLight,
    tertiaryContainer = KitsuneTertiaryContainerLight,
    onTertiaryContainer = KitsuneOnTertiaryContainerLight,
    error = KitsuneErrorLight,
    onError = KitsuneOnErrorLight,
    errorContainer = KitsuneErrorContainerLight,
    onErrorContainer = KitsuneOnErrorContainerLight,
    background = KitsuneBackgroundLight,
    onBackground = KitsuneOnBackgroundLight,
    surface = KitsuneSurfaceLight,
    onSurface = KitsuneOnSurfaceLight,
    surfaceVariant = KitsuneSurfaceVariantLight,
    onSurfaceVariant = KitsuneOnSurfaceVariantLight,
    outline = KitsuneOutlineLight,
    outlineVariant = KitsuneOutlineVariantLight,
    surfaceContainerLowest = KitsuneSurfaceContainerLowestLight,
    surfaceContainerLow = KitsuneSurfaceContainerLowLight,
    surfaceContainer = KitsuneSurfaceContainerLight,
    surfaceContainerHigh = KitsuneSurfaceContainerHighLight,
    surfaceContainerHighest = KitsuneSurfaceContainerHighestLight
)

@Composable
fun KitsuneBootTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}