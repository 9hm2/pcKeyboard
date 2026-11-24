package com.pckeyboard.ime.emoji.mix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tiny pure-Kotlin ARGB raster (straight alpha, 0xAARRGGBB) with just
 * the operations the emoji mixer needs: bilinear sampling, transformed
 * compositing, masks, outline/shadow, colour work and warps. Written
 * without android.graphics on purpose so the whole mixer runs — and is
 * visually testable — on the plain JVM.
 */
class Raster(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {

    fun get(x: Int, y: Int): Int = px[y * w + x]

    fun copy() = Raster(w, h, px.copyOf())

    /** Bilinear sample at continuous pixel coordinates (pixel centres at
     *  +0.5), done in premultiplied space so transparent edges don't
     *  bleed dark fringes. Outside the raster is transparent. */
    fun sample(fx: Float, fy: Float): Int {
        val x = fx - 0.5f
        val y = fy - 0.5f
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = x - x0
        val ty = y - y0
        var aa = 0f; var rr = 0f; var gg = 0f; var bb = 0f
        for (j in 0..1) for (i in 0..1) {
            val xx = x0 + i
            val yy = y0 + j
            val wgt = (if (i == 0) 1 - tx else tx) * (if (j == 0) 1 - ty else ty)
            if (xx < 0 || yy < 0 || xx >= w || yy >= h || wgt <= 0f) continue
            val p = px[yy * w + xx]
            val a = (p ushr 24) / 255f
            aa += a * wgt
            rr += ((p shr 16) and 255) * a * wgt
            gg += ((p shr 8) and 255) * a * wgt
            bb += (p and 255) * a * wgt
        }
        if (aa <= 0.002f) return 0
        val ia = 1f / aa
        return pack((aa * 255f + 0.5f).toInt(), (rr * ia + 0.5f).toInt(),
            (gg * ia + 0.5f).toInt(), (bb * ia + 0.5f).toInt())
    }

    /** Bounding box of pixels with alpha above [threshold], or null. */
    fun bounds(threshold: Int = 12): IntArray? {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > threshold) {
                if (x < x0) x0 = x; if (x > x1) x1 = x
                if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        return if (x1 < 0) null else intArrayOf(x0, y0, x1 + 1, y1 + 1)
    }

    /**
     * The visible content, scaled to fit a square canvas of [size] with
     * [fill] of its side, centred. Every emoji goes through this so all
     * recipes can position things by fractions of the canvas.
     */
    fun fitSquare(size: Int, fill: Float = 1f): Raster {
        val b = bounds() ?: return Raster(size, size)
        val cw = (b[2] - b[0]).toFloat()
        val ch = (b[3] - b[1]).toFloat()
        val scale = size * fill / max(cw, ch)
        val out = Raster(size, size)
        val ccx = (b[0] + b[2]) / 2f
        val ccy = (b[1] + b[3]) / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val sx = ccx + (x + 0.5f - size / 2f) / scale
            val sy = ccy + (y + 0.5f - size / 2f) / scale
            out.px[y * size + x] = sample(sx, sy)
        }
        return out
    }

    companion object {
        fun pack(a: Int, r: Int, g: Int, b: Int): Int =
            (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        fun alpha(p: Int) = p ushr 24
        fun red(p: Int) = (p shr 16) and 255
        fun green(p: Int) = (p shr 8) and 255
        fun blue(p: Int) = p and 255
        fun luminance(p: Int): Float =
            (0.299f * red(p) + 0.587f * green(p) + 0.114f * blue(p)) / 255f
    }
}

/** Source-over: [src] (with extra [alphaMul]) onto [dst]. */
fun overPixel(dst: Int, src: Int, alphaMul: Float = 1f): Int {
    val sa = (Raster.alpha(src) / 255f) * alphaMul
    if (sa <= 0f) return dst
    val da = Raster.alpha(dst) / 255f
    val oa = sa + da * (1f - sa)
    if (oa <= 0f) return 0
    fun ch(s: Int, d: Int) = ((s * sa + d * da * (1f - sa)) / oa + 0.5f).toInt()
    return Raster.pack(
        (oa * 255f + 0.5f).toInt(),
        ch(Raster.red(src), Raster.red(dst)),
        ch(Raster.green(src), Raster.green(dst)),
        ch(Raster.blue(src), Raster.blue(dst))
    )
}

/**
 * Draws [src] onto [this], centred at ([cx],[cy]), scaled so its
 * (square) side becomes [size] pixels, rotated by [rotation] radians,
 * multiplied by [alpha]. Inverse-mapped bilinear, so rotation and
 * scaling stay smooth.
 */
fun Raster.drawTransformed(
    src: Raster, cx: Float, cy: Float, size: Float,
    rotation: Float = 0f, alpha: Float = 1f
) {
    val scale = size / max(src.w, src.h)
    val half = size / 2f * 1.5f
    val cosR = cos(-rotation)
    val sinR = sin(-rotation)
    val xMin = max(0, floor(cx - half).toInt())
    val xMax = min(w - 1, ceil(cx + half).toInt())
    val yMin = max(0, floor(cy - half).toInt())
    val yMax = min(h - 1, ceil(cy + half).toInt())
    for (y in yMin..yMax) for (x in xMin..xMax) {
        val dx = x + 0.5f - cx
        val dy = y + 0.5f - cy
        val rx = dx * cosR - dy * sinR
        val ry = dx * sinR + dy * cosR
        val s = src.sample(rx / scale + src.w / 2f, ry / scale + src.h / 2f)
        if (Raster.alpha(s) == 0) continue
        px[y * w + x] = overPixel(px[y * w + x], s, alpha)
    }
}

/** A soft-edged filled disc. */
fun Raster.drawDisc(cx: Float, cy: Float, radius: Float, color: Int, feather: Float = 0.25f) {
    val inner = radius * (1f - feather)
    val xMin = max(0, floor(cx - radius).toInt())
    val xMax = min(w - 1, ceil(cx + radius).toInt())
    val yMin = max(0, floor(cy - radius).toInt())
    val yMax = min(h - 1, ceil(cy + radius).toInt())
    for (y in yMin..yMax) for (x in xMin..xMax) {
        val d = sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy))
        if (d >= radius) continue
        val a = if (d <= inner) 1f else 1f - (d - inner) / (radius - inner)
        px[y * w + x] = overPixel(px[y * w + x], color, a)
    }
}

/** Alpha channel as 0..1 floats. */
fun Raster.alphaMask(): FloatArray = FloatArray(w * h) { Raster.alpha(px[it]) / 255f }

/** Box blur of a float plane (used for soft shadows). */
fun blurPlane(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
    if (radius <= 0) return src.copyOf()
    fun pass(input: FloatArray, horizontal: Boolean): FloatArray {
        val out = FloatArray(w * h)
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val win = 2 * radius + 1
        for (l in 0 until lines) {
            var acc = 0f
            fun at(i: Int): Float {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) input[l * w + c] else input[c * w + l]
            }
            for (i in -radius..radius) acc += at(i)
            for (i in 0 until len) {
                val v = acc / win
                if (horizontal) out[l * w + i] = v else out[i * w + l] = v
                acc += at(i + radius + 1) - at(i - radius)
            }
        }
        return out
    }
    return pass(pass(src, true), false)
}

/** Grows a 0..1 mask by [radius] pixels (circular structuring element). */
fun dilatePlane(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
    if (radius <= 0) return src.copyOf()
    val out = FloatArray(w * h)
    val offsets = ArrayList<IntArray>()
    for (dy in -radius..radius) for (dx in -radius..radius) {
        if (dx * dx + dy * dy <= radius * radius) offsets.add(intArrayOf(dx, dy))
    }
    for (y in 0 until h) for (x in 0 until w) {
        var m = 0f
        for (o in offsets) {
            val xx = x + o[0]; val yy = y + o[1]
            if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
            val v = src[yy * w + xx]
            if (v > m) { m = v; if (m >= 1f) break }
        }
        out[y * w + x] = m
    }
    return out
}

/**
 * Turns a finished composition into a "sticker": a soft drop shadow and
 * a clean white outline around the silhouette, on a transparent canvas.
 */
fun Raster.stickerized(outlinePx: Int = max(2, w / 48), shadowPx: Int = max(2, w / 64)): Raster {
    val mask = alphaMask()
    val grown = dilatePlane(mask, w, h, outlinePx)
    val out = Raster(w, h)
    // Shadow: blurred grown silhouette, nudged down-right.
    val shadow = blurPlane(grown, w, h, shadowPx)
    val off = shadowPx / 2 + 1
    for (y in 0 until h) for (x in 0 until w) {
        val sx = x - off; val sy = y - off
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue
        val a = shadow[sy * w + sx] * 0.30f
        if (a > 0.004f) out.px[y * w + x] = Raster.pack((a * 255).toInt(), 0, 0, 0)
    }
    // Outline, then the artwork on top.
    for (i in 0 until w * h) {
        val a = grown[i]
        if (a > 0f) out.px[i] = overPixel(out.px[i], Raster.pack(255, 255, 255, 255), a)
    }
    for (i in 0 until w * h) out.px[i] = overPixel(out.px[i], px[i])
    return out
}

// ---- colour helpers --------------------------------------------------------

/** HSV in floats: h 0..360, s 0..1, v 0..1. */
fun rgbToHsv(p: Int): FloatArray {
    val r = Raster.red(p) / 255f; val g = Raster.green(p) / 255f; val b = Raster.blue(p) / 255f
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
    var hh = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    if (hh < 0f) hh += 360f
    return floatArrayOf(hh, if (mx == 0f) 0f else d / mx, mx)
}

fun hsvToRgb(h: Float, s: Float, v: Float, a: Int = 255): Int {
    val c = v * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1 -> Triple(c, x, 0f)
        hp < 2 -> Triple(x, c, 0f)
        hp < 3 -> Triple(0f, c, x)
        hp < 4 -> Triple(0f, x, c)
        hp < 5 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Raster.pack(a, ((r1 + m) * 255 + 0.5f).toInt(), ((g1 + m) * 255 + 0.5f).toInt(),
        ((b1 + m) * 255 + 0.5f).toInt())
}

/** Mean colour of the opaque pixels in a rectangle given in 0..1 fractions. */
fun Raster.meanColor(fx0: Float, fy0: Float, fx1: Float, fy1: Float): Int {
    var r = 0.0; var g = 0.0; var b = 0.0; var n = 0.0
    for (y in (fy0 * h).toInt() until (fy1 * h).toInt().coerceAtMost(h)) {
        for (x in (fx0 * w).toInt() until (fx1 * w).toInt().coerceAtMost(w)) {
            val p = px[y * w + x]
            val a = Raster.alpha(p) / 255.0
            if (a < 0.6) continue
            r += Raster.red(p) * a; g += Raster.green(p) * a; b += Raster.blue(p) * a; n += a
        }
    }
    if (n == 0.0) return Raster.pack(255, 128, 128, 128)
    return Raster.pack(255, (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** Dominant hue (degrees) of the vivid pixels, or null for grey art. */
fun Raster.dominantHue(): Float? {
    val bins = FloatArray(36)
    var weight = 0f
    for (p in px) {
        val a = Raster.alpha(p) / 255f
        if (a < 0.6f) continue
        val hsv = rgbToHsv(p)
        val wgt = hsv[1] * hsv[2] * a
        if (hsv[1] < 0.25f || hsv[2] < 0.25f) continue
        bins[(hsv[0] / 10f).toInt().coerceIn(0, 35)] += wgt
        weight += wgt
    }
    if (weight < 5f) return null
    val best = bins.indices.maxByOrNull { bins[it] } ?: return null
    return best * 10f + 5f
}

/** Rotates every pixel's hue toward [targetHue] by [strength] (0..1),
 *  keeping saturation and value — "A, dyed in B's colours". */
fun Raster.hueShifted(targetHue: Float, strength: Float): Raster {
    val out = Raster(w, h)
    for (i in px.indices) {
        val p = px[i]
        if (Raster.alpha(p) == 0) continue
        val hsv = rgbToHsv(p)
        // Grey/dark/white pixels (outlines, eyes) keep their colour.
        val vivid = ((hsv[1] - 0.12f) / 0.3f).coerceIn(0f, 1f)
        var delta = ((targetHue - hsv[0] + 540f) % 360f) - 180f
        val nh = hsv[0] + delta * strength * vivid
        out.px[i] = hsvToRgb(nh, hsv[1], hsv[2], Raster.alpha(p))
    }
    return out
}

/** A swirl warp around the centre: strong in the middle, none at the edge. */
fun Raster.swirled(strength: Float): Raster {
    val out = Raster(w, h)
    val cx = w / 2f; val cy = h / 2f
    val rad = min(w, h) / 2f
    for (y in 0 until h) for (x in 0 until w) {
        val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
        val d = sqrt(dx * dx + dy * dy)
        val t = (1f - d / rad).coerceIn(0f, 1f)
        val ang = atan2(dy, dx) - strength * t * t * PI.toFloat()
        out.px[y * w + x] = sample(cx + cos(ang) * d, cy + sin(ang) * d)
    }
    return out
}

// ---- face transplant helpers -------------------------------------------------

/** A circle in 0..1 canvas fractions. */
class Circle(val cx: Float, val cy: Float, val r: Float)

/**
 * The most common colour among the opaque pixels (quantised mode,
 * then averaged) — for a face emoji that's its skin, for most other
 * emoji whatever their dominant fill is.
 */
fun Raster.skinColor(): Int {
    // Skin lives in a band a little inside the silhouette (outside the
    // darker rim, outside the features) — robust even when the features
    // are the biggest single colour, as with large sunglasses.
    val edge = edgeDistance()
    val lo = w * 0.07f
    val hi = w * 0.16f
    val counts = HashMap<Int, Int>()
    for (i in px.indices) {
        val p = px[i]
        if (Raster.alpha(p) < 230 || edge[i] < lo || edge[i] > hi) continue
        val key = ((Raster.red(p) shr 4) shl 8) or ((Raster.green(p) shr 4) shl 4) or (Raster.blue(p) shr 4)
        counts[key] = (counts[key] ?: 0) + 1
    }
    if (counts.isEmpty()) {
        for (p in px) {
            if (Raster.alpha(p) < 230) continue
            val key = ((Raster.red(p) shr 4) shl 8) or ((Raster.green(p) shr 4) shl 4) or (Raster.blue(p) shr 4)
            counts[key] = (counts[key] ?: 0) + 1
        }
    }
    val best = counts.maxByOrNull { it.value }?.key ?: return Raster.pack(255, 250, 200, 40)
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (i in px.indices) {
        val p = px[i]
        if (Raster.alpha(p) < 230) continue
        val key = ((Raster.red(p) shr 4) shl 8) or ((Raster.green(p) shr 4) shl 4) or (Raster.blue(p) shr 4)
        if (key != best) continue
        r += Raster.red(p); g += Raster.green(p); b += Raster.blue(p); n++
    }
    return Raster.pack(255, (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/**
 * The face emoji WITHOUT its skin: everything that differs enough from
 * the skin colour (eyes, mouth, tears, sweat, hands, hearts, blush) is
 * kept; skin and the anti-aliased rim around it become transparent.
 * This is what gets transplanted onto another emoji's body.
 */
fun Raster.featuresOnly(skin: Int, lo: Float = 18f, hi: Float = 52f): Raster {
    val out = Raster(w, h)
    val skinHsv = rgbToHsv(skin)
    val edge = edgeDistance()
    // The face's rim (a darker, same-hue band hugging the silhouette) is
    // skin; but the same hue INSIDE the face is a real detail — the
    // outline of a hand, say — so the rim rule only applies near the edge.
    val rimBand = w * 0.075f
    for (i in px.indices) {
        val p = px[i]
        val a = Raster.alpha(p)
        if (a == 0) continue
        val dr = (Raster.red(p) - Raster.red(skin)).toFloat()
        val dg = (Raster.green(p) - Raster.green(skin)).toFloat()
        val db = (Raster.blue(p) - Raster.blue(skin)).toFloat()
        val d = sqrt(dr * dr + dg * dg + db * db)
        val t = ((d - lo) / (hi - lo)).coerceIn(0f, 1f)
        var keep = t * t * (3f - 2f * t)
        if (edge[i] < rimBand) {
            val hsv = rgbToHsv(p)
            var dh = abs(hsv[0] - skinHsv[0]); if (dh > 180f) dh = 360f - dh
            if (dh < 20f && hsv[1] > 0.25f) {
                val rim = ((hsv[2] - 0.40f) / 0.22f).coerceIn(0f, 1f)
                keep *= 1f - rim
            }
            // Fade the outermost pixels so no ring survives at the border.
            if (Raster.luminance(p) >= 0.3f) keep *= (edge[i] / (rimBand * 0.5f)).coerceIn(0f, 1f)
        }
        if (keep <= 0.01f) continue
        out.px[i] = (p and 0x00FFFFFF) or (((a * keep).toInt().coerceIn(0, 255)) shl 24)
    }
    return out
}

/** Every non-skin pixel inside the face replaced by the skin colour —
 *  the face with its features wiped (a blank canvas for new ones). */
fun Raster.skinFilled(skin: Int): Raster {
    val out = copy()
    for (i in px.indices) {
        val a = Raster.alpha(px[i])
        if (a == 0) continue
        out.px[i] = (skin and 0x00FFFFFF) or (a shl 24)
    }
    return out
}

/**
 * Up to [count] well-separated maximal inscribed circles of the shape —
 * first the main body, then (typically) a head or other big lobe.
 * Chamfer distance transform on a coarse grid; coordinates and radii
 * are fractions of the canvas.
 */
fun Raster.inscribedCircles(count: Int = 2, res: Int = 64): List<Circle> {
    val inf = 1_000_000
    val dt = IntArray(res * res)
    for (y in 0 until res) for (x in 0 until res) {
        val p = sample((x + 0.5f) / res * w, (y + 0.5f) / res * h)
        dt[y * res + x] = if (Raster.alpha(p) > 128) inf else 0
    }
    for (y in 0 until res) for (x in 0 until res) {
        val i = y * res + x
        if (dt[i] == 0) continue
        var v = dt[i]
        if (x > 0) v = min(v, dt[i - 1] + 3)
        if (y > 0) v = min(v, dt[i - res] + 3)
        if (x > 0 && y > 0) v = min(v, dt[i - res - 1] + 4)
        if (x < res - 1 && y > 0) v = min(v, dt[i - res + 1] + 4)
        // The image border counts as outside.
        v = min(v, 3 * (min(min(x, y), min(res - 1 - x, res - 1 - y)) + 1))
        dt[i] = v
    }
    for (y in res - 1 downTo 0) for (x in res - 1 downTo 0) {
        val i = y * res + x
        if (dt[i] == 0) continue
        var v = dt[i]
        if (x < res - 1) v = min(v, dt[i + 1] + 3)
        if (y < res - 1) v = min(v, dt[i + res] + 3)
        if (x < res - 1 && y < res - 1) v = min(v, dt[i + res + 1] + 4)
        if (x > 0 && y < res - 1) v = min(v, dt[i + res - 1] + 4)
        dt[i] = v
    }
    val circles = ArrayList<Circle>()
    repeat(count) {
        var bi = -1; var bv = 0
        for (i in dt.indices) if (dt[i] > bv) { bv = dt[i]; bi = i }
        if (bi < 0 || bv < 4) return circles
        val rPx = bv / 3f
        val cx = bi % res; val cy = bi / res
        circles.add(Circle((cx + 0.5f) / res, (cy + 0.5f) / res, rPx / res))
        // Exclude this lobe (and a margin) before looking for the next.
        val ex = rPx * 1.25f + 1f
        for (y in 0 until res) for (x in 0 until res) {
            val ddx = x - cx; val ddy = y - cy
            if (ddx * ddx + ddy * ddy <= ex * ex) dt[y * res + x] = 0
        }
    }
    return circles
}

// ---- removing the body's own face / better feature extraction -----------------

/** Distance in pixels from each opaque pixel to the nearest transparent
 *  one (chamfer 3-4), 0 for transparent pixels. */
fun Raster.edgeDistance(): FloatArray {
    val inf = 1_000_000
    val d = IntArray(w * h) { if (Raster.alpha(px[it]) > 128) inf else 0 }
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        if (d[i] == 0) continue
        var v = min(d[i], 3 * (min(min(x, y), min(w - 1 - x, h - 1 - y)) + 1))
        if (x > 0) v = min(v, d[i - 1] + 3)
        if (y > 0) v = min(v, d[i - w] + 3)
        if (x > 0 && y > 0) v = min(v, d[i - w - 1] + 4)
        if (x < w - 1 && y > 0) v = min(v, d[i - w + 1] + 4)
        d[i] = v
    }
    for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
        val i = y * w + x
        if (d[i] == 0) continue
        var v = d[i]
        if (x < w - 1) v = min(v, d[i + 1] + 3)
        if (y < h - 1) v = min(v, d[i + w] + 3)
        if (x < w - 1 && y < h - 1) v = min(v, d[i + w + 1] + 4)
        if (x > 0 && y < h - 1) v = min(v, d[i + w - 1] + 4)
        d[i] = v
    }
    return FloatArray(w * h) { d[it] / 3f }
}

/**
 * Erases the body's OWN face-like details (dark eyes, mouth, nostrils)
 * inside the disc ([cx],[cy],[radius] in pixels) so a transplanted face
 * doesn't end up drawn on top of another face. The dark pixels (and
 * their anti-aliased fringe) are filled by diffusing the surrounding
 * colour inward. Does nothing when the disc is mostly dark (a black
 * object, not a face) — returns true when it changed anything.
 */
fun Raster.wipeDarkDetails(cx: Float, cy: Float, radius: Float): Boolean {
    val x0 = max(0, (cx - radius).toInt()); val x1 = min(w - 1, (cx + radius).toInt())
    val y0 = max(0, (cy - radius).toInt()); val y1 = min(h - 1, (cy + radius).toInt())
    val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
    if (bw < 4 || bh < 4) return false
    val inDisc = BooleanArray(bw * bh)
    val dark = BooleanArray(bw * bh)
    var discPx = 0; var darkPx = 0
    for (y in 0 until bh) for (x in 0 until bw) {
        val gx = x0 + x; val gy = y0 + y
        val dx = gx + 0.5f - cx; val dy = gy + 0.5f - cy
        if (dx * dx + dy * dy > radius * radius) continue
        val p = px[gy * w + gx]
        if (Raster.alpha(p) < 128) continue
        inDisc[y * bw + x] = true; discPx++
        // "Dark" = the brightest channel is low (eyes/mouth: near black),
        // NOT low luminance — a deep red or green shadow is still colourful.
        val vmax = max(Raster.red(p), max(Raster.green(p), Raster.blue(p))) / 255f
        if (vmax < 0.32f) { dark[y * bw + x] = true; darkPx++ }
    }
    if (darkPx == 0 || darkPx > discPx * 0.30f) return false
    // Keep only SMALL dark blobs (eyes, mouth, nostrils). A big connected
    // dark structure — a turtle's shell seams, outlines — is part of the
    // body, not a face, and must survive.
    val label = IntArray(bw * bh)
    val maxBlob = (discPx * 0.15f).toInt().coerceAtLeast(12)
    var next = 0
    val stack = IntArray(bw * bh)
    for (start in 0 until bw * bh) {
        if (!dark[start] || label[start] != 0) continue
        next++
        var sp = 0; stack[sp++] = start; label[start] = next
        val members = ArrayList<Int>()
        while (sp > 0) {
            val cur = stack[--sp]; members.add(cur)
            val cx2 = cur % bw; val cy2 = cur / bw
            if (cx2 > 0 && dark[cur - 1] && label[cur - 1] == 0) { label[cur - 1] = next; stack[sp++] = cur - 1 }
            if (cx2 < bw - 1 && dark[cur + 1] && label[cur + 1] == 0) { label[cur + 1] = next; stack[sp++] = cur + 1 }
            if (cy2 > 0 && dark[cur - bw] && label[cur - bw] == 0) { label[cur - bw] = next; stack[sp++] = cur - bw }
            if (cy2 < bh - 1 && dark[cur + bw] && label[cur + bw] == 0) { label[cur + bw] = next; stack[sp++] = cur + bw }
        }
        if (members.size > maxBlob) for (m in members) dark[m] = false
    }
    if (dark.none { it }) return false
    // Grow the dark mask to swallow the soft fringe around eyes / lines.
    val grow = max(2, (radius / 13f).toInt())
    val mask = BooleanArray(bw * bh)
    for (y in 0 until bh) for (x in 0 until bw) {
        if (!inDisc[y * bw + x]) continue
        var hit = false
        loop@ for (dy in -grow..grow) for (dx in -grow..grow) {
            val xx = x + dx; val yy = y + dy
            if (xx < 0 || yy < 0 || xx >= bw || yy >= bh) continue
            if (dark[yy * bw + xx]) { hit = true; break@loop }
        }
        mask[y * bw + x] = hit
    }
    // Fill: every masked pixel takes the colour of its NEAREST known
    // pixel (multi-source BFS from the unmasked, opaque neighbourhood —
    // including pixels just outside the disc), then a few smoothing
    // passes blend the seams. Starting from the nearest colour (not a
    // disc-wide mean) keeps a green head green next to an olive shell.
    val rr = FloatArray(bw * bh); val gg = FloatArray(bw * bh); val bb = FloatArray(bw * bh)
    val known = BooleanArray(bw * bh)
    val queue = IntArray(bw * bh)
    var qh = 0; var qt = 0
    for (i in 0 until bw * bh) {
        val gx = x0 + i % bw; val gy = y0 + i / bw
        val p = px[gy * w + gx]
        if (mask[i] || Raster.alpha(p) < 128) continue
        rr[i] = Raster.red(p).toFloat(); gg[i] = Raster.green(p).toFloat(); bb[i] = Raster.blue(p).toFloat()
        known[i] = true; queue[qt++] = i
    }
    if (qt == 0) return false
    while (qh < qt) {
        val cur = queue[qh++]
        val cx2 = cur % bw; val cy2 = cur / bw
        fun visit(nb: Int) {
            if (known[nb] || !mask[nb]) return
            rr[nb] = rr[cur]; gg[nb] = gg[cur]; bb[nb] = bb[cur]
            known[nb] = true; queue[qt++] = nb
        }
        if (cx2 > 0) visit(cur - 1)
        if (cx2 < bw - 1) visit(cur + 1)
        if (cy2 > 0) visit(cur - bw)
        if (cy2 < bh - 1) visit(cur + bw)
    }
    repeat(8) {
        for (y in 0 until bh) for (x in 0 until bw) {
            val i = y * bw + x
            if (!mask[i]) continue
            var sr = 0f; var sg = 0f; var sb = 0f; var c = 0
            if (x > 0) { sr += rr[i - 1]; sg += gg[i - 1]; sb += bb[i - 1]; c++ }
            if (x < bw - 1) { sr += rr[i + 1]; sg += gg[i + 1]; sb += bb[i + 1]; c++ }
            if (y > 0) { sr += rr[i - bw]; sg += gg[i - bw]; sb += bb[i - bw]; c++ }
            if (y < bh - 1) { sr += rr[i + bw]; sg += gg[i + bw]; sb += bb[i + bw]; c++ }
            rr[i] = sr / c; gg[i] = sg / c; bb[i] = sb / c
        }
    }
    for (i in 0 until bw * bh) {
        if (!mask[i]) continue
        val gx = x0 + i % bw; val gy = y0 + i / bw
        val a = Raster.alpha(px[gy * w + gx])
        px[gy * w + gx] = Raster.pack(a, rr[i].toInt(), gg[i].toInt(), bb[i].toInt())
    }
    return true
}
