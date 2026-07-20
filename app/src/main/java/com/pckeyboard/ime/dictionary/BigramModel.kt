package com.pckeyboard.ime.dictionary

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Word-pair (bigram) frequency model for one language, built by
 * `scripts/generate_bigrams.py` from a news-sentence corpus.
 *
 * Each pair of unigram ranks is packed into a Long
 * (`rank1 << KEY_SHIFT | rank2`); [keys] is sorted, so lookups are a
 * binary search and "all continuations of rank1" is one contiguous
 * block. ~600k pairs ≈ 7 MB of primitive arrays — no boxing.
 *
 * This is what gives the suggestion engine *context*: which words tend
 * to follow the word before the cursor.
 */
class BigramModel private constructor(
    private val keys: LongArray,
    private val counts: IntArray
) {

    val size: Int get() = keys.size

    /** Corpus count of `prev -> next`, or 0. */
    fun count(prevRank: Int, nextRank: Int): Int {
        if (prevRank < 0 || nextRank < 0) return 0
        val idx = keys.binarySearch((prevRank.toLong() shl KEY_SHIFT) or nextRank.toLong())
        return if (idx >= 0) counts[idx] else 0
    }

    /**
     * The [n] most frequent continuations of [prevRank], as
     * (nextRank, count) pairs ordered by descending count.
     */
    fun topNext(prevRank: Int, n: Int): List<Pair<Int, Int>> {
        if (prevRank < 0 || n <= 0) return emptyList()
        val from = (prevRank.toLong() shl KEY_SHIFT)
        val to = ((prevRank + 1).toLong() shl KEY_SHIFT)
        var lo = keys.binarySearch(from).let { if (it < 0) -it - 1 else it }
        val best = ArrayList<Pair<Int, Int>>(n)
        while (lo < keys.size && keys[lo] < to) {
            val entry = (keys[lo] and KEY_MASK).toInt() to counts[lo]
            if (best.size < n) {
                best.add(entry)
                best.sortByDescending { it.second }
            } else if (entry.second > best.last().second) {
                best[best.size - 1] = entry
                best.sortByDescending { it.second }
            }
            lo++
        }
        return best
    }

    private fun LongArray.binarySearch(key: Long): Int {
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = this[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    companion object {
        /** Must match KEY_SHIFT in scripts/generate_bigrams.py. */
        const val KEY_SHIFT = 20
        private const val KEY_MASK = (1L shl KEY_SHIFT) - 1

        /** Loads `assets/dict/<langId>.bigrams`, or null when absent /
         *  unreadable. The file is sorted by key at generation time. */
        fun load(context: Context, langId: String): BigramModel? {
            return try {
                context.assets.open("dict/$langId.bigrams").use { raw ->
                    fromGzipStream(raw)
                }
            } catch (_: Throwable) {
                null
            }
        }

        fun fromGzipStream(raw: java.io.InputStream): BigramModel? {
            val keys = ArrayList<Long>(650_000)
            val counts = ArrayList<Int>(650_000)
            try {
                GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                    val a = line.indexOf(' ')
                    val b = line.indexOf(' ', a + 1)
                    if (a <= 0 || b <= a) return@forEachLine
                    val r1 = line.substring(0, a).toLong()
                    val r2 = line.substring(a + 1, b).toLong()
                    keys.add((r1 shl KEY_SHIFT) or r2)
                    counts.add(line.substring(b + 1).toInt())
                }
            } catch (_: Throwable) {
                return null
            }
            if (keys.isEmpty()) return null
            return BigramModel(keys.toLongArray(), counts.toIntArray())
        }
    }
}
