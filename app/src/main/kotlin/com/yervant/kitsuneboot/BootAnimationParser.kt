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
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream

data class BootAnimationPartInfo(
    val type: Char,
    val count: Int,
    val pause: Int,
    val path: String,
    val bgColor: String?,
    val frameCount: Int
)

data class BootAnimationInfo(
    val width: Int,
    val height: Int,
    val fps: Int,
    val parts: List<BootAnimationPartInfo>,
    val fileSizeBytes: Long = 0L,
    val isModule: Boolean = false,
    val moduleName: String? = null,
    val moduleAuthor: String? = null,
    val moduleVersion: String? = null,
    val previewImageBytes: ByteArray? = null
) {
    val isOomRisk: Boolean
        get() = fileSizeBytes > BootAnimationUiState.OOM_THRESHOLD_BYTES

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BootAnimationInfo) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fps != other.fps) return false
        if (parts != other.parts) return false
        if (fileSizeBytes != other.fileSizeBytes) return false
        if (isModule != other.isModule) return false
        if (moduleName != other.moduleName) return false
        if (moduleAuthor != other.moduleAuthor) return false
        if (moduleVersion != other.moduleVersion) return false
        if (previewImageBytes != null) {
            if (other.previewImageBytes == null) return false
            if (!previewImageBytes.contentEquals(other.previewImageBytes)) return false
        } else if (other.previewImageBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + fps
        result = 31 * result + parts.hashCode()
        result = 31 * result + fileSizeBytes.hashCode()
        result = 31 * result + isModule.hashCode()
        result = 31 * result + (moduleName?.hashCode() ?: 0)
        result = 31 * result + (moduleAuthor?.hashCode() ?: 0)
        result = 31 * result + (moduleVersion?.hashCode() ?: 0)
        result = 31 * result + (previewImageBytes?.contentHashCode() ?: 0)
        return result
    }
}

object BootAnimationParser {

    /**
     * Parses a bootanimation.zip or a Magisk/KernelSU root module ZIP input stream.
     */
    fun parse(inputStream: InputStream, fileSizeBytes: Long = 0L): BootAnimationInfo {
        var descContent: String? = null
        val frameCounts = mutableMapOf<String, Int>()
        var nestedBootAnimBytes: ByteArray? = null
        var modulePropContent: String? = null
        var firstImageBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val name = entry.name
                val nameLower = name.lowercase()
                if (name.equals("desc.txt", ignoreCase = true)) {
                    descContent = zipIn.readBytes().toString(Charsets.UTF_8)
                } else if (name.equals("module.prop", ignoreCase = true)) {
                    modulePropContent = zipIn.readBytes().toString(Charsets.UTF_8)
                } else if (nameLower.endsWith("bootanimation.zip")) {
                    nestedBootAnimBytes = zipIn.readBytes()
                } else if (!entry.isDirectory) {
                    val slashIndex = name.indexOf('/')
                    if (slashIndex != -1) {
                        val partName = name.substring(0, slashIndex)
                        frameCounts[partName] = (frameCounts[partName] ?: 0) + 1
                    }
                    if (firstImageBytes == null && (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg"))) {
                        firstImageBytes = zipIn.readBytes()
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        var modName: String? = null
        var modAuthor: String? = null
        var modVersion: String? = null
        if (modulePropContent != null) {
            for (line in modulePropContent.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("name=") -> modName = trimmed.substringAfter("name=").trim()
                    trimmed.startsWith("author=") -> modAuthor = trimmed.substringAfter("author=").trim()
                    trimmed.startsWith("version=") -> modVersion = trimmed.substringAfter("version=").trim()
                }
            }
        }

        if (nestedBootAnimBytes != null) {
            // Nested bootanimation.zip found in Magisk module
            val innerInfo = parse(java.io.ByteArrayInputStream(nestedBootAnimBytes), fileSizeBytes)
            return innerInfo.copy(
                isModule = true,
                moduleName = modName,
                moduleAuthor = modAuthor,
                moduleVersion = modVersion,
                fileSizeBytes = fileSizeBytes,
                previewImageBytes = innerInfo.previewImageBytes
            )
        }

        val content = descContent
        if (content.isNullOrBlank()) {
            throw IllegalArgumentException("Invalid archive: Missing desc.txt or bootanimation.zip.")
        }

        return parseDescContent(
            content = content,
            frameCounts = frameCounts,
            fileSizeBytes = fileSizeBytes,
            isModule = modulePropContent != null,
            moduleName = modName,
            moduleAuthor = modAuthor,
            moduleVersion = modVersion,
            previewImageBytes = firstImageBytes
        )
    }

    fun parseFromUri(context: Context, uri: Uri): BootAnimationInfo {
        val fileSizeBytes = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open bootanimation archive.")
        return stream.use { parse(it, fileSizeBytes) }
    }

    fun parseDescContent(
        content: String,
        frameCounts: Map<String, Int> = emptyMap(),
        fileSizeBytes: Long = 0L,
        isModule: Boolean = false,
        moduleName: String? = null,
        moduleAuthor: String? = null,
        moduleVersion: String? = null,
        previewImageBytes: ByteArray? = null
    ): BootAnimationInfo {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Empty desc.txt content.")
        }

        val headerTokens = lines[0].split("\\s+".toRegex())
        if (headerTokens.size < 3) {
            throw IllegalArgumentException("Invalid desc.txt header: '${lines[0]}'")
        }

        val width = headerTokens[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid width in desc.txt")
        val height = headerTokens[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid height in desc.txt")
        val fps = headerTokens[2].toIntOrNull() ?: throw IllegalArgumentException("Invalid FPS in desc.txt")

        val parts = mutableListOf<BootAnimationPartInfo>()
        for (i in 1 until lines.size) {
            val tokens = lines[i].split("\\s+".toRegex())
            if (tokens.size >= 4) {
                val type = tokens[0].firstOrNull() ?: 'p'
                val count = tokens[1].toIntOrNull() ?: 0
                val pause = tokens[2].toIntOrNull() ?: 0
                val path = tokens[3]
                val bgColor = if (tokens.size >= 5) tokens[4] else null
                val frameCount = frameCounts[path] ?: 0

                parts.add(
                    BootAnimationPartInfo(
                        type = type,
                        count = count,
                        pause = pause,
                        path = path,
                        bgColor = bgColor,
                        frameCount = frameCount
                    )
                )
            }
        }

        return BootAnimationInfo(
            width = width,
            height = height,
            fps = fps,
            parts = parts,
            fileSizeBytes = fileSizeBytes,
            isModule = isModule,
            moduleName = moduleName,
            moduleAuthor = moduleAuthor,
            moduleVersion = moduleVersion,
            previewImageBytes = previewImageBytes
        )
    }
}
