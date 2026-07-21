package com.pckeyboard.ime.dictionary

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Word-triple (trigram) frequency model for one language, built by
 * `scripts/generate_trigrams.py`. Two words of context instead of one:
 * "meg tudod" → "csinálni" is a far stronger signal than "tudod" →
 * "csinálni" alone.
 *
 * Keys pack three unigram ranks into one Long
 * (`r1 << 40 | r2 << 20 | r3`), sorted — so an exact triple is a binary
 * search and "continuations of (r1, r2)" is one contiguous block.
 */
class TrigramModel private constructor(
    private val keys: LongArray,
    private val counts: IntArray
) {

    val size: Int get() = keys.size

    /** Corpus count of `prev2 prev1 -> next`, or 0. */
    fun count(prev2Rank: Int, prev1Rank: Int, nextRank: Int): Int {
        if (prev2Rank < 0 || prev1Rank < 0 || nextRank < 0) return 0
        val key = (prev2Rank.toLong() shl (2 * KEY_SHIFT)) or
            (prev1Rank.toLong() shl KEY_SHIFT) or nextRank.toLong()
        val idx = binarySearch(key)
        return if (idx >= 0) counts[idx] else 0
    }

    /** The [n] most frequent continuations of the (prev2, prev1) pair,
     *  as (nextRank, count) ordered by descending count. */
    fun topNext(prev2Rank: Int, prev1Rank: Int, n: Int): List<Pair<Int, Int>> {
        if (prev2Rank < 0 || prev1Rank < 0 || n <= 0) return emptyList()
        val prefix = (prev2Rank.toLong() shl (2 * KEY_SHIFT)) or
            (prev1Rank.toLong() shl KEY_SHIFT)
        val to = prefix + (1L shl KEY_SHIFT)
        var lo = binarySearch(prefix).let { if (it < 0) -it - 1 else it }
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

    private fun binarySearch(key: Long): Int {
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = keys[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    companion object {
        /** Must match KEY_SHIFT in scripts/generate_trigrams.py. */
        const val KEY_SHIFT = 20
        private const val KEY_MASK = (1L shl KEY_SHIFT) - 1

        fun load(context: Context, langId: String): TrigramModel? {
            return try {
                context.assets.open("dict/$langId.trigrams").use { raw ->
                    fromGzipStream(raw)
                }
            } catch (_: Throwable) {
                null
            }
        }

        fun fromGzipStream(raw: java.io.InputStream): TrigramModel? {
            val keys = ArrayList<Long>(420_000)
            val counts = ArrayList<Int>(420_000)
            try {
                GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                    val a = line.indexOf(' ')
                    val b = line.indexOf(' ', a + 1)
                    val c = line.indexOf(' ', b + 1)
                    if (a <= 0 || b <= a || c <= b) return@forEachLine
                    val r1 = line.substring(0, a).toLong()
                    val r2 = line.substring(a + 1, b).toLong()
                    val r3 = line.substring(b + 1, c).toLong()
                    keys.add((r1 shl (2 * KEY_SHIFT)) or (r2 shl KEY_SHIFT) or r3)
                    counts.add(line.substring(c + 1).toInt())
                }
            } catch (_: Throwable) {
                return null
            }
            if (keys.isEmpty()) return null
            return TrigramModel(keys.toLongArray(), counts.toIntArray())
        }
    }
}
