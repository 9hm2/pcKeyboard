package com.pckeyboard.ime.emoji.mix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

/**
 * Android bridge for the pure-Kotlin mixer: draws an emoji with the
 * device's own colour-emoji font (so it matches what the user sees and
 * needs no bundled artwork), and converts finished rasters to Bitmaps /
 * PNG files.
 */
object EmojiRasterizer {

    private val cache = object : LruCache<String, Raster>(24) {}

    /** Renders [text] into a [size]×[size] raster (content roughly
     *  centred; the mixer re-fits it, so exact metrics don't matter). */
    @Synchronized
    fun render(text: String, size: Int = 256): Raster {
        val key = "$size|$text"
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.8f
            textAlign = Paint.Align.LEFT
            color = Color.BLACK
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        // Centre the glyph's actual ink box on the canvas.
        val x = size / 2f - bounds.exactCenterX()
        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(text, x, y, paint)
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        bmp.recycle()
        val r = Raster(size, size, px)
        cache.put(key, r)
        return r
    }

    fun toBitmap(r: Raster): Bitmap {
        val bmp = Bitmap.createBitmap(r.w, r.h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(r.px, 0, r.w, 0, 0, r.w, r.h)
        return bmp
    }

    /** Writes [r] as a PNG into the app cache under emoji_mix/ (pruning
     *  files older than an hour) and returns it. */
    fun savePng(cacheDir: File, r: Raster): File {
        val dir = File(cacheDir, "emoji_mix").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "mix_${System.currentTimeMillis()}.png")
        val bmp = toBitmap(r)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }
}
