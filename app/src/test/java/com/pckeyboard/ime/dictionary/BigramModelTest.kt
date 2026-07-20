package com.pckeyboard.ime.dictionary

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/** Loads the real shipped Hungarian bigram asset and sanity-checks the
 *  context signal the engine relies on. */
class BigramModelTest {

    companion object {
        private lateinit var dict: WordDictionary
        private lateinit var bigrams: BigramModel

        @BeforeClass
        @JvmStatic
        fun setUp() {
            dict = FileInputStream(File("src/main/assets/dict/hu_HU.dict")).use {
                WordDictionary.fromGzipStream("hu_HU", it)
            }!!
            bigrams = FileInputStream(File("src/main/assets/dict/hu_HU.bigrams")).use {
                BigramModel.fromGzipStream(it)
            }!!
        }
    }

    @Test
    fun commonPairsHaveCounts() {
        assertTrue(bigrams.size > 100_000)
        assertTrue(bigrams.count(dict.rankOf("azt"), dict.rankOf("mondta")) > 0)
        assertTrue(bigrams.count(dict.rankOf("nem"), dict.rankOf("tudom")) > 0)
        assertTrue(bigrams.count(dict.rankOf("arról"), dict.rankOf("hogy")) > 0)
    }

    @Test
    fun predictionsAreOrderedAndPlausible() {
        val next = bigrams.topNext(dict.rankOf("nem"), 5)
        assertTrue(next.size == 5)
        // Descending by count.
        assertTrue(next.zipWithNext().all { (a, b) -> a.second >= b.second })
        // Every predicted rank resolves to a real word.
        assertTrue(next.all { dict.wordAt(it.first).isNotEmpty() })
    }
}
