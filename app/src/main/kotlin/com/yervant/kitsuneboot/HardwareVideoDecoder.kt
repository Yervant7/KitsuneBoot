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
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal object HardwareVideoDecoder {

    /**
     * Decodes video frames using hardware MediaCodec + SurfaceTexture with OpenGL ES offscreen rendering.
     * Renders frames transformed directly on the GPU and writes them into pooled Bitmaps in [rawChannel].
     * Returns true if decoding completed successfully, or false if hardware decoding is unsupported.
     */
    internal suspend fun decode(
        context: Context,
        videoUri: Uri,
        frameWidth: Int,
        frameHeight: Int,
        targetFps: Int,
        scalingMode: ScalingMode,
        backgroundColor: Int,
        rotationDegrees: Int,
        startMs: Long,
        endMs: Long,
        multiPartMode: Boolean,
        splitPointMs: Long,
        totalTargetFrames: Int,
        part0TotalFrames: Int,
        bitmapPool: BitmapPool,
        rawChannel: SendChannel<BootAnimationGenerator.RawFrameTask>,
        isCancelled: () -> Boolean
    ): Boolean {
        val singleThreadExecutor = Executors.newSingleThreadExecutor()
        val singleThreadDispatcher = singleThreadExecutor.asCoroutineDispatcher()
        return try {
            withContext(singleThreadDispatcher) {
                decodeOnGlThread(
                    context = context,
                    videoUri = videoUri,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    targetFps = targetFps,
                    scalingMode = scalingMode,
                    backgroundColor = backgroundColor,
                    rotationDegrees = rotationDegrees,
                    startMs = startMs,
                    endMs = endMs,
                    multiPartMode = multiPartMode,
                    splitPointMs = splitPointMs,
                    totalTargetFrames = totalTargetFrames,
                    part0TotalFrames = part0TotalFrames,
                    bitmapPool = bitmapPool,
                    rawChannel = rawChannel,
                    isCancelled = isCancelled
                )
            }
        } finally {
            singleThreadDispatcher.close()
            singleThreadExecutor.shutdown()
        }
    }

    private suspend fun decodeOnGlThread(
        context: Context,
        videoUri: Uri,
        frameWidth: Int,
        frameHeight: Int,
        targetFps: Int,
        scalingMode: ScalingMode,
        backgroundColor: Int,
        rotationDegrees: Int,
        startMs: Long,
        endMs: Long,
        multiPartMode: Boolean,
        splitPointMs: Long,
        totalTargetFrames: Int,
        part0TotalFrames: Int,
        bitmapPool: BitmapPool,
        rawChannel: SendChannel<BootAnimationGenerator.RawFrameTask>,
        isCancelled: () -> Boolean
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var eglHelper: EglHelper? = null
        var surface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        var handlerThread: HandlerThread? = null
        var renderer: GlRenderer? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                return false
            }

            extractor.selectTrack(videoTrackIndex)
            val mimeType = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false

            val videoWidth = if (videoFormat.containsKey(MediaFormat.KEY_WIDTH)) videoFormat.getInteger(MediaFormat.KEY_WIDTH) else frameWidth
            val videoHeight = if (videoFormat.containsKey(MediaFormat.KEY_HEIGHT)) videoFormat.getInteger(MediaFormat.KEY_HEIGHT) else frameHeight

            eglHelper = EglHelper()
            if (!eglHelper.init()) {
                return false
            }

            val textureId = eglHelper.createOESTexture()
            surfaceTexture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(frameWidth, frameHeight)
            }
            surface = Surface(surfaceTexture)

            handlerThread = HandlerThread("HardwareDecoderSurfaceCallback").apply { start() }
            val callbackHandler = Handler(handlerThread.looper)

            val frameAvailableSemaphore = Semaphore(0)
            surfaceTexture.setOnFrameAvailableListener({
                frameAvailableSemaphore.release()
            }, callbackHandler)

            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(videoFormat, surface, null, 0)
            decoder.start()

            renderer = GlRenderer(
                textureId = textureId,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                scalingMode = scalingMode,
                backgroundColor = backgroundColor,
                rotationDegrees = rotationDegrees
            ).apply { init() }

            val frameIntervalUs = 1_000_000L / targetFps
            var currentTargetPtsUs = startMs * 1000L
            val endPtsUs = endMs * 1000L

            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false
            var capturedFrames = 0
            val timeoutUs = 10_000L

            val pixelBuffer = ByteBuffer.allocateDirect(frameWidth * frameHeight * 4).order(ByteOrder.nativeOrder())

            while (capturedFrames < totalTargetFrames && !isDecoderEOS) {
                coroutineContext.ensureActive()
                if (isCancelled()) throw InterruptedException("Cancelled by user.")

                // Feed input buffers
                if (!isExtractorEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Dequeue output buffers
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }

                    val presentationTimeUs = bufferInfo.presentationTimeUs
                    val shouldRender = presentationTimeUs >= (currentTargetPtsUs - (frameIntervalUs / 2)) && presentationTimeUs <= (endPtsUs + frameIntervalUs)

                    if (shouldRender && capturedFrames < totalTargetFrames) {
                        decoder.releaseOutputBuffer(outputBufferIndex, true)

                        if (frameAvailableSemaphore.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                            surfaceTexture.updateTexImage()

                            val pooledBitmap = bitmapPool.acquire()
                            renderer.drawAndRead(surfaceTexture, pixelBuffer, pooledBitmap)

                            val isPart0 = !multiPartMode || capturedFrames < part0TotalFrames
                            val partFrameIndex = if (isPart0) capturedFrames else capturedFrames - part0TotalFrames

                            rawChannel.send(
                                BootAnimationGenerator.RawFrameTask(capturedFrames, isPart0, partFrameIndex, pooledBitmap)
                            )
                            capturedFrames++
                            currentTargetPtsUs += frameIntervalUs
                        }
                    } else {
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        if (presentationTimeUs > currentTargetPtsUs + frameIntervalUs && capturedFrames < totalTargetFrames) {
                            currentTargetPtsUs += frameIntervalUs
                        }
                    }
                }
            }

            return capturedFrames >= (totalTargetFrames * 0.7) // Success if at least 70% frames captured
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            return false // Trigger fallback to multi-retriever
        } finally {
            try { renderer?.release() } catch (ignored: Exception) {}
            try { decoder?.stop() } catch (ignored: Exception) {}
            try { decoder?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { surface?.release() } catch (ignored: Exception) {}
            try { surfaceTexture?.release() } catch (ignored: Exception) {}
            try { handlerThread?.quitSafely() } catch (ignored: Exception) {}
            try { eglHelper?.release() } catch (ignored: Exception) {}
        }
    }

    private class EglHelper {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun init(): Boolean {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay === EGL14.EGL_NO_DISPLAY) return false

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] <= 0) {
                return false
            }

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext === EGL14.EGL_NO_CONTEXT) return false

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0)
            if (eglSurface === EGL14.EGL_NO_SURFACE) return false

            return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun createOESTexture(): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return textures[0]
        }

        fun release() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface !== EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext !== EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private class GlRenderer(
        private val textureId: Int,
        private val videoWidth: Int,
        private val videoHeight: Int,
        private val frameWidth: Int,
        private val frameHeight: Int,
        private val scalingMode: ScalingMode,
        private val backgroundColor: Int,
        private val rotationDegrees: Int
    ) {
        private var program = 0
        private var fboId = 0
        private var fboTextureId = 0
        private var vertexBuffer: FloatBuffer? = null
        private var texCoordBuffer: FloatBuffer? = null

        private val mvpMatrix = FloatArray(16)
        private val texMatrix = FloatArray(16)

        private val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        fun init() {
            program = createProgram(vertexShaderCode, fragmentShaderCode)

            // Setup FBO
            val fbos = IntArray(1)
            val textures = IntArray(1)
            GLES20.glGenFramebuffers(1, fbos, 0)
            GLES20.glGenTextures(1, textures, 0)

            fboId = fbos[0]
            fboTextureId = textures[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, frameWidth, frameHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextureId, 0)

            // Standard quad coordinates: [-1, -1] to [1, 1]
            val quadCoords = floatArrayOf(
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
            )
            vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(quadCoords)
                position(0)
            }

            // Standard texture coordinates: [0, 0] to [1, 1]
            val texCoords = floatArrayOf(
                0.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 0.0f, 1.0f
            )
            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(texCoords)
                position(0)
            }

            computeMvpMatrix()
        }

        private fun computeMvpMatrix() {
            val effSrcW = if (rotationDegrees == 90 || rotationDegrees == 270) videoHeight.toFloat() else videoWidth.toFloat()
            val effSrcH = if (rotationDegrees == 90 || rotationDegrees == 270) videoWidth.toFloat() else videoHeight.toFloat()

            val srcAspect = if (effSrcH > 0f) effSrcW / effSrcH else 1.0f
            val dstAspect = if (frameHeight > 0) frameWidth.toFloat() / frameHeight.toFloat() else 1.0f

            val (scaleX, scaleY) = when (scalingMode) {
                ScalingMode.STRETCH -> {
                    Pair(1.0f, 1.0f)
                }
                ScalingMode.FIT_CENTER -> {
                    if (srcAspect > dstAspect) {
                        Pair(1.0f, dstAspect / srcAspect)
                    } else {
                        Pair(srcAspect / dstAspect, 1.0f)
                    }
                }
                ScalingMode.CENTER_CROP -> {
                    if (srcAspect > dstAspect) {
                        Pair(srcAspect / dstAspect, 1.0f)
                    } else {
                        Pair(1.0f, dstAspect / srcAspect)
                    }
                }
            }

            android.opengl.Matrix.setIdentityM(mvpMatrix, 0)
            // Invert Y in MVP matrix so OpenGL NDC (-1 at bottom, +1 at top) maps directly to Bitmap row 0 (top of image)
            android.opengl.Matrix.scaleM(mvpMatrix, 0, scaleX, -scaleY, 1.0f)
            if (rotationDegrees != 0) {
                android.opengl.Matrix.rotateM(mvpMatrix, 0, -rotationDegrees.toFloat(), 0f, 0f, 1f)
            }
        }

        fun drawAndRead(
            surfaceTexture: SurfaceTexture,
            pixelBuffer: ByteBuffer,
            destinationBitmap: Bitmap
        ) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glViewport(0, 0, frameWidth, frameHeight)

            val r = ((backgroundColor shr 16) and 0xFF) / 255.0f
            val g = ((backgroundColor shr 8) and 0xFF) / 255.0f
            val b = (backgroundColor and 0xFF) / 255.0f
            val a = ((backgroundColor shr 24) and 0xFF) / 255.0f

            GLES20.glClearColor(r, g, b, if (a == 0f) 1f else a)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)

            surfaceTexture.getTransformMatrix(texMatrix)

            val aPositionLocation = GLES20.glGetAttribLocation(program, "aPosition")
            val aTexCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
            val uTextureLocation = GLES20.glGetUniformLocation(program, "uTexture")
            val uMVPMatrixLocation = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            val uTexMatrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(uTextureLocation, 0)

            GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uTexMatrixLocation, 1, false, texMatrix, 0)

            GLES20.glEnableVertexAttribArray(aPositionLocation)
            GLES20.glVertexAttribPointer(aPositionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordLocation)
            GLES20.glVertexAttribPointer(aTexCoordLocation, 4, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionLocation)
            GLES20.glDisableVertexAttribArray(aTexCoordLocation)

            pixelBuffer.rewind()
            GLES20.glReadPixels(0, 0, frameWidth, frameHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
            pixelBuffer.rewind()

            destinationBitmap.copyPixelsFromBuffer(pixelBuffer)
        }

        fun release() {
            if (fboId != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
                fboId = 0
            }
            if (fboTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
                fboTextureId = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
            return prog
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
