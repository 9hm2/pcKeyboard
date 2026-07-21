package com.pckeyboard.ime.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/** Pure-logic tests for the personal typo model's alignment, plus the
 *  engine behaviour with a faked set of personal signals. */
class PersonalErrorModelTest {

    @Test
    fun extractsSubstitutions() {
        assertEquals(listOf("s:fd"), PersonalErrorModel.extractEdits("kérfőjel", "kérdőjel"))
        assertEquals(
            listOf("s:re", "s:mn"),
            PersonalErrorModel.extractEdits("teljrsem", "teljesen")
        )
    }

    @Test
    fun extractsTranspositionDropAndOmission() {
        assertEquals(listOf("t:kn"), PersonalErrorModel.extractEdits("mikneth", "minketh"))
        assertEquals(listOf("d:z"), PersonalErrorModel.extractEdits("szzia", "szia"))
        assertEquals(listOf("i:l"), PersonalErrorModel.extractEdits("bilentyu", "billentyu"))
    }

    @Test
    fun messyPairsYieldNothing() {
        assertTrue(PersonalErrorModel.extractEdits("elsőte", "először").isEmpty())
    }

    companion object {
        private lateinit var dict: WordDictionary

        @BeforeClass
        @JvmStatic
        fun setUp() {
            dict = FileInputStream(File("src/main/assets/dict/hu_HU.dict")).use {
                WordDictionary.fromGzipStream("hu_HU", it)
            }!!
        }
    }

    private class FakeSignals(
        private val pair: Pair<String, Pair<String, Int>>? = null,
        private val factors: Map<String, Float> = emptyMap()
    ) : PersonalSignals {
        override fun correctionFor(typed: String): Pair<String, Int>? =
            pair?.takeIf { it.first == typed }?.second

        override fun editFactor(key: String): Float = factors[key] ?: 1f
    }

    @Test
    fun confirmedPairSuggestsAndAutoReplaces() {
        // "xqzsw" has no plausible engine candidates at all — only the
        // personally confirmed pair produces one, and with 2+ taps it
        // may auto-replace on the user's own authority.
        val engine = SuggestionEngine(
            dict, personal = FakeSignals(pair = "xqzsw" to ("szia" to 2))
        )
        val result = engine.suggest("xqzsw")
        assertTrue(result.suggestions.contains("szia"))
        assertEquals("szia", result.autoReplace)
    }

    @Test
    fun singleTapSuggestsButDoesNotAutoReplace() {
        val engine = SuggestionEngine(
            dict, personal = FakeSignals(pair = "xqzsw" to ("szia" to 1))
        )
        val result = engine.suggest("xqzsw")
        assertTrue(result.suggestions.contains("szia"))
        assertNull(result.autoReplace)
    }
}
