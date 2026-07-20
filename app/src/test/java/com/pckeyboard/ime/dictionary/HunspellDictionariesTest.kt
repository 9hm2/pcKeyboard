package com.pckeyboard.ime.dictionary

import org.apache.lucene.analysis.hunspell.Dictionary as HunspellDictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Loads the exact .aff/.dic assets shipped in the APK with the exact
 * Lucene Hunspell code path the IME uses, and checks the verdicts the
 * suggestion engine depends on. Runs on the host JVM.
 */
class HunspellDictionariesTest {

    private fun load(langId: String): Hunspell {
        val dir = File("src/main/assets/hunspell")
        FileInputStream(File(dir, "$langId.aff")).use { aff ->
            FileInputStream(File(dir, "$langId.dic")).use { dic ->
                return Hunspell(
                    HunspellDictionary(ByteBuffersDirectory(), langId, aff, dic)
                )
            }
        }
    }

    @Test
    fun hungarianValidatesInflectionsAndCompounds() {
        val h = load("hu_HU")
        // Inflected forms and compounds — the corpus list's blind spot.
        assertTrue(h.spell("szeretlek"))
        assertTrue(h.spell("billentyűzetkiosztás"))
        assertTrue(h.spell("megkérdezhetném"))
        assertTrue(h.spell("gondolkodhattam"))
        assertTrue(h.spell("vágyok"))
        // Typos and accentless spellings must NOT validate.
        assertFalse(h.spell("hpgy"))
        assertFalse(h.spell("szeretlk"))
        assertFalse(h.spell("talalkozunk"))
        assertFalse(h.spell("kerdojel"))
    }

    @Test
    fun otherLanguagesLoadAndSpell() {
        val en = load("en_US")
        assertTrue(en.spell("keyboard"))
        assertFalse(en.spell("keybaord"))
        val de = load("de_DE")
        assertTrue(de.spell("Tastatur"))
        assertFalse(de.spell("Tastatoor"))
        val es = load("es_ES")
        assertTrue(es.spell("teclado"))
        assertFalse(es.spell("tecladdo"))
    }
}
