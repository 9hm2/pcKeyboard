package com.pckeyboard.ime.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashGuardTest {

    private class MemStorage : GuardStorage {
        val m = HashMap<String, Int>()
        override fun getInt(key: String, default: Int) = m[key] ?: default
        override fun putInt(key: String, value: Int) { m[key] = value }
    }

    private val f = NativeCrashGuard.FEATURE_RERANKER

    @Test
    fun cleanRunsNeverSkip() {
        val s = MemStorage()
        repeat(5) {
            val g = NativeCrashGuard(s, appVersion = 1)   // fresh "process" each time
            assertFalse(g.shouldSkip(f))
            g.begin(f); g.end(f)
        }
    }

    @Test
    fun oneCrashIsForgivenTwoConsecutiveDisable() {
        val s = MemStorage()
        // Process 1: dies inside the risky call (begin without end).
        NativeCrashGuard(s, 1).apply { assertFalse(shouldSkip(f)); begin(f) }
        // Process 2: sees the dangling marker -> strike 1 -> still allowed.
        NativeCrashGuard(s, 1).apply { assertFalse(shouldSkip(f)); begin(f) }
        // ...and dies again.
        // Process 3: strike 2 -> skipped.
        assertTrue(NativeCrashGuard(s, 1).shouldSkip(f))
        // Stays skipped on later starts (no begin => no new strikes).
        assertTrue(NativeCrashGuard(s, 1).shouldSkip(f))
    }

    @Test
    fun successResetsStrikes() {
        val s = MemStorage()
        NativeCrashGuard(s, 1).apply { shouldSkip(f); begin(f) }          // crash
        NativeCrashGuard(s, 1).apply { assertFalse(shouldSkip(f)); begin(f); end(f) } // ok
        NativeCrashGuard(s, 1).apply { shouldSkip(f); begin(f) }          // crash again
        // Only ONE strike since the success -> still allowed.
        assertFalse(NativeCrashGuard(s, 1).shouldSkip(f))
    }

    @Test
    fun newAppVersionGivesAFreshChance() {
        val s = MemStorage()
        NativeCrashGuard(s, 1).apply { shouldSkip(f); begin(f) }
        NativeCrashGuard(s, 1).apply { shouldSkip(f); begin(f) }
        assertTrue(NativeCrashGuard(s, 1).shouldSkip(f))
        assertFalse(NativeCrashGuard(s, 2).shouldSkip(f))
    }
}
