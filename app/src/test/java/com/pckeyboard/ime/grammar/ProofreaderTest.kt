package com.pckeyboard.ime.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofreaderTest {

    private val fixes = mapOf(
        "szeretlk" to "szeretlek",
        "teljrsem" to "teljesen",
        "kerdojel" to "kérdőjel"
    )

    private fun reader(log: MutableList<Triple<String, String?, String?>>? = null) =
        Proofreader("hu_HU") { w, p, p2 ->
            log?.add(Triple(w, p, p2))
            fixes[w.lowercase()]
        }

    @Test
    fun fixesWordsAndKeepsEverythingElse() {
        val r = reader().proofread("szeretlk teljrsem, látod? Nem kerdojel.")
        assertEquals("szeretlek teljesen, látod? Nem kérdőjel.", r.text)
        assertEquals(3, r.wordFixes.size)
    }

    @Test
    fun contextCarriesAcrossSpacesAndCommasButResetsAtSentenceEnd() {
        val log = mutableListOf<Triple<String, String?, String?>>()
        reader(log).proofread("alma, körte. barack szilva")
        val byWord = log.associateBy { it.first }
        assertEquals("alma", byWord["körte"]!!.second)          // comma keeps context
        assertEquals(null, byWord["barack"]!!.second)            // '.' resets it
        assertEquals("barack", byWord["szilva"]!!.second)
    }

    @Test
    fun insertsCommasPerAkH() {
        val r = reader().proofread("azt mondta hogy szép de drága")
        assertEquals("azt mondta, hogy szép, de drága", r.text)
        assertEquals(2, r.commasAdded)
        // Not at sentence start, not after a coordinator, not "több mint".
        assertEquals("De jó. És hogy ne", reader().proofread("De jó. És hogy ne").text)
        assertEquals("több mint száz", reader().proofread("több mint száz").text)
        assertEquals("vagy ezt, vagy azt", reader().proofread("vagy ezt vagy azt").text)
    }

    @Test
    fun existingCommaIsNotDoubled() {
        assertEquals("azt mondta, hogy jó",
            reader().proofread("azt mondta, hogy jó").text)
    }

    @Test
    fun identifiersAndNumbersAreNeverTouched() {
        val r = reader().proofread("teljrsem1 www.teljrsem.hu a-teljrsem user@teljrsem")
        assertEquals("teljrsem1 www.teljrsem.hu a-teljrsem user@teljrsem", r.text)
        assertTrue(r.wordFixes.isEmpty())
    }

    @Test
    fun sentenceFinalWordIsStillCorrected() {
        // The dot followed by a space/end is punctuation, not a domain.
        assertEquals("ez teljesen. jó", reader().proofread("ez teljrsem. jó").text)
        assertEquals("ez teljesen.", reader().proofread("ez teljrsem.").text)
    }

    @Test
    fun budgetTruncatesGracefully() {
        val big = (1..2000).joinToString(" ") { "teljrsem" }
        val r = reader().proofread(big, budgetMs = 0)
        assertTrue(r.truncated)
        // Nothing lost: same number of words come out.
        assertEquals(2000, r.text.split(" ").size)
        assertFalse(r.text.isEmpty())
    }

    @Test
    fun unsupportedLanguageSkipsCommas() {
        val r = Proofreader("en_US") { _, _, _ -> null }.proofread("I said that but no")
        assertEquals("I said that but no", r.text)
        assertEquals(0, r.commasAdded)
    }
}
