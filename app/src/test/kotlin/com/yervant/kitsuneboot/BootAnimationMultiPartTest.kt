package com.yervant.kitsuneboot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootAnimationMultiPartTest {

    @Test
    fun testPingPongFrameCalculationDoublesIntermediateFrames() {
        // In dual video mode, duration = 1000ms at 30 fps -> 30 frames
        // Ping-pong formula: (2 * baseFrames - 2) = 2 * 30 - 2 = 58
        val state = BootAnimationUiState(
            multiPartMode = true,
            multiPartSourceMode = MultiPartSourceMode.DUAL_VIDEO,
            loopVideoDurationMs = 1000L,
            loopVideoStartMs = 0L,
            loopVideoEndMs = 1000L,
            fps = "30",
            isPingPongEnabled = true
        )
        assertEquals(58, state.part1EstimatedFrames)

        val stateNormal = state.copy(isPingPongEnabled = false)
        assertEquals(30, stateNormal.part1EstimatedFrames)
    }

    @Test
    fun testLoopLengthRiskWarningTriggersWhenExceeding150Frames() {
        // 6000ms at 30 fps -> 180 frames > 150 frames -> risky
        val riskyState = BootAnimationUiState(
            multiPartMode = true,
            multiPartSourceMode = MultiPartSourceMode.DUAL_VIDEO,
            loopVideoDurationMs = 6000L,
            loopVideoStartMs = 0L,
            loopVideoEndMs = 6000L,
            fps = "30"
        )
        assertTrue(riskyState.isLoopLengthRisky)

        // 2000ms at 30 fps -> 60 frames <= 150 frames -> not risky
        val safeState = riskyState.copy(
            loopVideoDurationMs = 2000L,
            loopVideoEndMs = 2000L
        )
        assertFalse(safeState.isLoopLengthRisky)
    }

    @Test
    fun testSingleVideoSplitEstimatesIntroAndLoopFrames() {
        // Video 10000ms split at 4000ms at 30 fps -> part0: 120 frames, part1: 180 frames
        val state = BootAnimationUiState(
            multiPartMode = true,
            multiPartSourceMode = MultiPartSourceMode.SINGLE_SPLIT,
            videoDurationMs = 10_000L,
            videoStartMs = 0L,
            videoEndMs = 10_000L,
            splitPointMs = 4_000L,
            fps = "30"
        )
        assertEquals(120, state.part0EstimatedFrames)
        assertEquals(180, state.part1EstimatedFrames)
    }
}
