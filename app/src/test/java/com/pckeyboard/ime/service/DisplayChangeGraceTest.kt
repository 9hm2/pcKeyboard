package com.pckeyboard.ime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayChangeGraceTest {

    @Test
    fun firstCallOnlyRecordsBaseline() {
        val g = DisplayChangeGrace()
        assertFalse(g.onConfiguration(1, 400, 800, nowMs = 0))
        assertFalse(g.isRecent(1))
    }

    @Test
    fun rotationOpensTheWindowThenItCloses() {
        val g = DisplayChangeGrace(windowMs = 2000)
        g.onConfiguration(1, 400, 800, 0)
        assertTrue(g.onConfiguration(2, 800, 400, nowMs = 10_000))
        assertTrue(g.isRecent(10_500))
        assertTrue(g.isRecent(12_000))
        assertFalse(g.isRecent(12_001))
    }

    @Test
    fun foldChangesSizeWithoutChangingOrientation() {
        val g = DisplayChangeGrace()
        g.onConfiguration(1, 360, 780, 0)
        assertTrue(g.onConfiguration(1, 720, 780, nowMs = 5_000))   // unfolded
        assertTrue(g.isRecent(5_100))
    }

    @Test
    fun unrelatedReconfigurationsDoNotCount() {
        val g = DisplayChangeGrace()
        g.onConfiguration(1, 400, 800, 0)
        // Same size/orientation (e.g. dark-mode or locale change).
        assertFalse(g.onConfiguration(1, 400, 800, nowMs = 5_000))
        assertFalse(g.isRecent(5_100))
    }
}
