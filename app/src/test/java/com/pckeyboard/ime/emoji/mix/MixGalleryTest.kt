package com.pckeyboard.ime.emoji.mix

import org.junit.Test
import java.io.DataOutputStream
import java.io.File

/**
 * Visual QA harness (not part of the normal run). Reads emoji as raw
 * "uXXXX.rgba" files (int w, int h, then w*h*4 RGBA bytes), writes every
 * variant of a few pairs as raw files + a manifest; a small Python
 * script turns those into PNG contact sheets.
 *   PCK_MIX_EMOJI=<dir> PCK_MIX_OUT=<dir> gradle ... --tests '*MixGallery*'
 */
class MixGalleryTest {

    private fun load(dir: File, code: String): Raster {
        val b = File(dir, "u$code.rgba").readBytes()
        fun i32(o: Int) = ((b[o].toInt() and 255) shl 24) or ((b[o + 1].toInt() and 255) shl 16) or
            ((b[o + 2].toInt() and 255) shl 8) or (b[o + 3].toInt() and 255)
        val w = i32(0); val h = i32(4)
        val r = Raster(w, h)
        for (i in 0 until w * h) {
            val o = 8 + i * 4
            r.px[i] = Raster.pack(b[o + 3].toInt() and 255, b[o].toInt() and 255,
                b[o + 1].toInt() and 255, b[o + 2].toInt() and 255)
        }
        return r
    }

    @Test
    fun gallery() {
        val emojiDir = System.getenv("PCK_MIX_EMOJI") ?: return
        val outDir = File(System.getenv("PCK_MIX_OUT") ?: return).apply { mkdirs() }
        val dir = File(emojiDir)
        fun input(code: String, text: String) =
            MixInput(text, load(dir, code), MixRecipes.isFaceEmoji(text))
        val pairs = (System.getenv("PCK_MIX_PAIRS") ?: "1f600:😀+1f525:🔥").split(",")
        val size = (System.getenv("PCK_MIX_SIZE") ?: "200").toInt()
        val manifest = StringBuilder()
        for ((i, spec) in pairs.withIndex()) {
            val (l, r) = spec.split("+")
            // Text comes from the hex code (the JVM's default charset
            // would mangle emoji passed through the environment).
            fun text(code: String) = String(Character.toChars(code.substringBefore("-").toInt(16)))
            val a = input(l.substringBefore(":"), text(l.substringBefore(":")))
            val b = input(r.substringBefore(":"), text(r.substringBefore(":")))
            for ((nm, inp) in listOf("A" to a, "B" to b)) {
                val c = inp.art.fitSquare(200, 0.9f).inscribedCircles(2)
                manifest.appendLine("# pair $i $nm circles: " + c.joinToString { "(%.2f,%.2f r=%.2f)".format(it.cx, it.cy, it.r) })
            }
            val vs = MixRecipes.variantsFor(a, b, 0L, 24)
            for ((k, v) in vs.withIndex()) {
                val t0 = System.nanoTime()
                val img = v.render(size)
                val ms = (System.nanoTime() - t0) / 1_000_000
                DataOutputStream(File(outDir, "p${i}_v$k.rgba").outputStream().buffered()).use { o ->
                    o.writeInt(img.w); o.writeInt(img.h)
                    for (p in img.px) {
                        o.writeByte(Raster.red(p)); o.writeByte(Raster.green(p))
                        o.writeByte(Raster.blue(p)); o.writeByte(Raster.alpha(p))
                    }
                }
                manifest.appendLine("$i $k ${v.recipe} ${if (v.base === a) "A" else "B"} ${ms}ms")
            }
        }
        File(outDir, "manifest.txt").writeText(manifest.toString())
    }
}
