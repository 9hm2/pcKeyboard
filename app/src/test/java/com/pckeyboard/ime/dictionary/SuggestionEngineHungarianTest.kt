package com.pckeyboard.ime.dictionary

import org.apache.lucene.analysis.hunspell.Dictionary as HunspellDictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * End-to-end engine test on the REAL shipped Hungarian assets: the
 * frequency dictionary and the Hunspell checker are loaded exactly the
 * way the IME loads them, and the auto-correct verdicts are pinned for
 * the typo classes users actually produce (all cases below come from
 * live typing sessions).
 */
class SuggestionEngineHungarianTest {

    companion object {
        private lateinit var engine: SuggestionEngine

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val dict = FileInputStream(File("src/main/assets/dict/hu_HU.dict")).use {
                WordDictionary.fromGzipStream("hu_HU", it)
            }!!
            val hunspellDir = File("src/main/assets/hunspell")
            val checker = FileInputStream(File(hunspellDir, "hu_HU.aff")).use { aff ->
                FileInputStream(File(hunspellDir, "hu_HU.dic")).use { dic ->
                    Hunspell(HunspellDictionary(ByteBuffersDirectory(), "hu_HU", aff, dic))
                }
            }
            engine = SuggestionEngine(dict, null) { w ->
                try { checker.spell(w) } catch (_: Throwable) { true }
            }
        }
    }

    private fun auto(typed: String): String? = engine.suggest(typed).autoReplace

    @Test
    fun singleTypoCorrections() {
        assertEquals("szeretlek", auto("szeretlk"))
        assertEquals("kérdőjel", auto("kérfőjel"))
        assertEquals("hogy", auto("hpgy"))
        assertEquals("szia", auto("szzia"))
    }

    @Test
    fun accentRestoration() {
        assertEquals("kérdőjel", auto("kerdojel"))
        assertEquals("találkozunk", auto("talalkozunk"))
        assertEquals("mégis", auto("megis"))
        assertEquals("úgy", auto("ugy"))
        assertEquals("holnapután", auto("holnaputan"))
    }

    @Test
    fun typoPlusMissingAccents() {
        assertEquals("billentyűzet", auto("bilentyuzet"))
        assertEquals("hazamegyek", auto("hazamrgyek"))
    }

    @Test
    fun twoTypoCorrections() {
        assertEquals("teljesen", auto("teljrsem"))
        assertEquals("kellene", auto("krllrne"))
        assertEquals("adjon", auto("afjom"))
        assertEquals("értelmezte", auto("értelmrztr"))
    }

    @Test
    fun reverseAccentFix() {
        assertEquals("mindig", auto("mindíg"))
    }

    @Test
    fun validWordsAreNeverTouched() {
        assertNull(auto("vagyok"))
        assertNull(auto("vágyok"))
        assertNull(auto("szia"))
        assertNull(auto("tesó"))
        assertNull(auto("mondtam"))
        assertNull(auto("hazamegyek"))
        // Compound the corpus never saw — Hunspell validates it.
        assertNull(auto("billentyűzetkiosztás"))
        assertNull(auto("megbeszélhetnénk"))
    }
}
