package com.pckeyboard.ime.emoji.mix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** One emoji taking part in a mix: its text and its rasterised artwork. */
class MixInput(val text: String, val art: Raster, val isFace: Boolean)

/** A finished recipe application: what to call it and how to render it. */
class MixVariant(
    val recipe: String,
    val base: MixInput,
    val other: MixInput,
    val seed: Long
) {
    /** Renders the sticker at [size] px (square, transparent). */
    fun render(size: Int): Raster {
        val rnd = Random(seed)
        val body = MixRecipes.byId.getValue(recipe).compose(base.art, other.art, size, rnd)
        return body.stickerized()
    }
}

/** A way of combining a base emoji with a second one. */
class Recipe(
    val id: String,
    /** Needs a face-like base (eyes / mouth sit at face positions). */
    val needsFace: Boolean,
    /** Extra compatibility rule for the (base, other) pair, if any. */
    val accepts: ((MixInput, MixInput) -> Boolean)? = null,
    val compose: (base: Raster, other: Raster, n: Int, rnd: Random) -> Raster
)

object MixRecipes {

    /** Face-like emoji: the ones whose eyes/mouth recipes make sense. */
    fun isFaceEmoji(text: String): Boolean {
        val cp = text.codePointAt(0)
        return cp in 0x1F600..0x1F64F || cp in 0x1F910..0x1F92F || cp in 0x1F970..0x1F97A ||
            cp in 0x1F9D0..0x1F9D0 || cp == 0x263A || cp == 0x2639 || cp == 0x1F921 ||
            cp == 0x1F479 || cp == 0x1F47A || cp == 0x1F47B || cp == 0x1F480 || cp == 0x1F47D ||
            cp == 0x1F916 || cp == 0x1F4A9 || cp in 0x1F431..0x1F43C || cp == 0x1F981 ||
            cp in 0x1F608..0x1F608
    }

    /** Round, human-style faces (donors for transplants / swaps) — not
     *  animal heads, whose "features" are ears and snouts. */
    fun isRoundFace(text: String): Boolean {
        val cp = text.codePointAt(0)
        return isFaceEmoji(text) && cp !in 0x1F431..0x1F43C && cp != 0x1F981
    }

    // ---- shared helpers ---------------------------------------------------

    private fun canvas(n: Int) = Raster(n, n)

    private fun fit(r: Raster, n: Int, fill: Float) = r.fitSquare(n, fill)

    private fun faceColor(face: Raster): Int = face.meanColor(0.18f, 0.45f, 0.34f, 0.70f)

    private fun jitter(rnd: Random, amount: Float) = (rnd.nextFloat() * 2f - 1f) * amount

    /** The other emoji, with a small drop shadow so it reads as an object
     *  placed ON the base rather than pasted flat. */
    private fun Raster.placeWithShadow(
        src: Raster, cx: Float, cy: Float, size: Float, rot: Float
    ) {
        val shadow = src.copy()
        for (i in shadow.px.indices) {
            val a = Raster.alpha(shadow.px[i])
            shadow.px[i] = Raster.pack((a * 0.35f).toInt(), 0, 0, 0)
        }
        val off = size * 0.035f
        drawTransformed(shadow, cx + off, cy + off * 1.4f, size, rot)
        drawTransformed(src, cx, cy, size, rot)
    }

    // ---- face transplant (the signature Kitchen move) -------------------------

    /**
     * The donor face's FEATURES (eyes, mouth, sweat drop, hand, blush —
     * everything but the skin) are lifted off and drawn onto the other
     * emoji's body: 🐢+😅 is a turtle with the sweating grin on its
     * head, 😘+🔥 a winking, kissing flame. [onHead] looks for a second
     * lobe of the body (a head) and falls back to the upper part.
     */
    private fun transplant(id: String, preferHead: Boolean) =
        Recipe(id, false, { b, o -> isRoundFace(o.text) && !b.isFace }) { body, donor, n, rnd ->
            val shape = fit(body, n, 0.90f)
            val out = shape.copy()
            val circles = shape.inscribedCircles(2)
            val main = circles.firstOrNull() ?: Circle(0.5f, 0.5f, 0.3f)
            // A second, clearly smaller lobe is a head (turtle, dog...);
            // blobs (flame, heart, fruit) have none and get the middle.
            val head = circles.getOrNull(1)?.takeIf {
                val lateral = abs(it.cx - main.cx) >= abs(it.cy - main.cy)
                // A head sits BESIDE the body (turtle, dog...); a lobe
                // straight above it (a flame's tip, a heart's lobe) is
                // only a head if it is nearly body-sized.
                it.r >= 0.11f && it.r <= main.r * 0.95f &&
                    (if (lateral) it.r >= main.r * 0.40f else it.r >= main.r * 0.75f)
            }
            val face = fit(donor, n, 1f)
            val feats = face.featuresOnly(face.skinColor())
            val useHead = if (head != null) preferHead else false
            val cx: Float; val cy: Float; val dia: Float
            if (useHead && head != null) {
                cx = head.cx; cy = head.cy
                dia = (2f * head.r * 1.28f).coerceIn(0.30f, 0.62f)
            } else if (!preferHead || head == null) {
                cx = main.cx; cy = main.cy
                dia = (2f * main.r * 1.10f).coerceIn(0.36f, 0.84f)
            } else {
                cx = main.cx; cy = main.cy
                dia = (2f * main.r * 1.10f).coerceIn(0.36f, 0.84f)
            }
            // The body may have a face of its own (a turtle's smile, a
            // ghost's eyes): wipe it so the new face isn't drawn on top
            // of the old one.
            out.wipeDarkDetails(cx * n, cy * n, dia * n * 0.62f)
            out.drawTransformed(feats, cx * n, cy * n, dia * n, jitter(rnd, 0.05f))
            out
        }

    /** Best placement first: head when the body has one, else the middle. */
    private val faceBest = transplant("face", true)
    /** The other placement — the middle of the body — as an alternative. */
    private val faceAlt = transplant("face_mid", false)

    /** Two faces: A's skin with B's features — a blue face with
     *  heart-eyes, a green one with a kiss. */
    private val swapFace = Recipe("swapface", true, { b, o -> isRoundFace(o.text) && isRoundFace(b.text) }) { base, donor, n, rnd ->
        val a = fit(base, n, 0.90f)
        val blank = a.skinFilled(a.skinColor())
        val d = fit(donor, n, 0.90f)
        val feats = d.featuresOnly(d.skinColor())
        val out = blank.copy()
        out.drawTransformed(feats, n / 2f, n / 2f, n.toFloat(), jitter(rnd, 0.04f))
        out
    }

    // ---- face recipes -----------------------------------------------------

    /** The other emoji REPLACES the eyes: 😀 + 🔥 = a face with flame eyes. */
    private val eyes = Recipe("eyes", true, { _, o -> !o.isFace }) { base, other, n, rnd ->
        val out = canvas(n)
        val face = fit(base, n, 0.90f)
        out.drawTransformed(face, n / 2f, n / 2f, n * 0.90f)
        val skin = faceColor(face)
        val w = n * 0.90f
        val ey = n * 0.5f - w * 0.5f + w * 0.375f
        val dx = w * 0.205f
        val size = w * 0.33f
        for (side in intArrayOf(-1, 1)) {
            val cx = n / 2f + side * dx
            out.drawDisc(cx, ey, size * 0.80f, skin, 0.40f)
            out.placeWithShadow(fit(other, n / 3, 1f), cx, ey, size,
                side * 0.12f + jitter(rnd, 0.05f))
        }
        out
    }

    /** The other emoji becomes the mouth / what the face is eating. */
    private val mouth = Recipe("mouth", true, { _, o -> !o.isFace }) { base, other, n, rnd ->
        val out = canvas(n)
        val face = fit(base, n, 0.88f)
        out.drawTransformed(face, n / 2f, n * 0.47f, n * 0.88f)
        val skin = faceColor(face)
        val w = n * 0.88f
        val my = n * 0.47f - w / 2f + w * 0.74f
        out.drawDisc(n / 2f, my, w * 0.17f, skin, 0.4f)
        out.placeWithShadow(fit(other, n / 3, 1f), n / 2f, my + w * 0.02f, w * 0.36f,
            jitter(rnd, 0.25f))
        out
    }

    /** The other emoji is a hat / crown on top of the base. */
    private val hat = Recipe("hat", false) { base, other, n, rnd ->
        val out = canvas(n)
        out.drawTransformed(fit(base, n, 0.74f), n / 2f, n * 0.60f, n * 0.74f)
        out.placeWithShadow(fit(other, n / 2, 1f), n * 0.52f, n * 0.20f, n * 0.46f,
            -0.22f + jitter(rnd, 0.1f))
        out
    }

    // ---- object recipes ---------------------------------------------------

    /** Held / carried: the base with the other emoji in its "hand". */
    private val held = Recipe("held", false) { base, other, n, rnd ->
        val out = canvas(n)
        out.drawTransformed(fit(base, n, 0.70f), n * 0.40f, n * 0.44f, n * 0.70f)
        out.placeWithShadow(fit(other, n / 2, 1f), n * 0.72f, n * 0.70f, n * 0.46f,
            0.2f + jitter(rnd, 0.12f))
        out
    }

    /** Two-layer twin: the other emoji looming behind, tilted. */
    private val twin = Recipe("twin", false) { base, other, n, rnd ->
        val out = canvas(n)
        out.drawTransformed(fit(other, n, 0.82f), n * 0.60f, n * 0.46f, n * 0.82f,
            0.28f + jitter(rnd, 0.1f), 0.95f)
        out.placeWithShadow(fit(base, n, 0.66f), n * 0.40f, n * 0.56f, n * 0.66f,
            -0.08f + jitter(rnd, 0.05f))
        out
    }

    /** The base's silhouette MADE OF the other emoji, base shading kept. */
    private val texture = Recipe("texture", false) { base, other, n, rnd ->
        val shape = fit(base, n, 0.90f)
        val tile = fit(other, 64, 1f)
        val pattern = canvas(n)
        // Under the tiles: the other emoji's average colour, so gaps
        // between tiles read as part of the same material, not grey.
        val under = other.meanColor(0.15f, 0.15f, 0.85f, 0.85f)
        val underDark = Raster.pack(255, (Raster.red(under) * 0.8f).toInt(),
            (Raster.green(under) * 0.8f).toInt(), (Raster.blue(under) * 0.8f).toInt())
        for (i in pattern.px.indices) pattern.px[i] = underDark
        val cell = n * 0.19f
        var row = 0
        var y = -cell / 2f
        while (y < n + cell) {
            var x = -cell / 2f + if (row % 2 == 0) 0f else cell / 2f
            while (x < n + cell) {
                pattern.drawTransformed(tile, x, y, cell * 1.12f, jitter(rnd, 0.35f))
                x += cell
            }
            y += cell * 0.88f
            row++
        }
        val out = canvas(n)
        for (i in 0 until n * n) {
            val s = shape.px[i]
            val sa = Raster.alpha(s)
            if (sa == 0) continue
            val lum = Raster.luminance(s)
            var p = pattern.px[i]
            // Shade by the base's own luminance; its dark features
            // (eyes, outlines) show through so it stays recognisable.
            val k = 0.62f + 0.38f * lum
            p = Raster.pack(255, (Raster.red(p) * k).toInt(), (Raster.green(p) * k).toInt(),
                (Raster.blue(p) * k).toInt())
            if (lum < 0.34f) {
                val wgt = ((0.34f - lum) / 0.34f).coerceIn(0f, 1f) * 0.9f
                p = Raster.pack(255,
                    (Raster.red(p) * (1 - wgt) + Raster.red(s) * wgt).toInt(),
                    (Raster.green(p) * (1 - wgt) + Raster.green(s) * wgt).toInt(),
                    (Raster.blue(p) * (1 - wgt) + Raster.blue(s) * wgt).toInt())
            }
            out.px[i] = Raster.pack(sa, Raster.red(p), Raster.green(p), Raster.blue(p))
        }
        out
    }

    /** Emoji-art: the base drawn from many tiny copies of the other. */
    private val mosaic = Recipe("mosaic", false) { base, other, n, rnd ->
        val shape = fit(base, n, 0.92f)
        val tile = fit(other, 48, 1f)
        val out = canvas(n)
        val cell = n * 0.075f
        var row = 0
        var y = cell / 2f
        while (y < n) {
            var x = cell / 2f + if (row % 2 == 0) 0f else cell / 2f
            while (x < n) {
                val s = shape.sample(x, y)
                val a = Raster.alpha(s)
                if (a > 150) {
                    val t = tile.copy()
                    // Tint each tile toward the base's colour at this spot,
                    // so the base's picture emerges from the little ones.
                    for (i in t.px.indices) {
                        val tp = t.px[i]
                        val ta = Raster.alpha(tp)
                        if (ta == 0) continue
                        val m = 0.5f
                        t.px[i] = Raster.pack(ta,
                            (Raster.red(tp) * (1 - m) + Raster.red(s) * m).toInt(),
                            (Raster.green(tp) * (1 - m) + Raster.green(s) * m).toInt(),
                            (Raster.blue(tp) * (1 - m) + Raster.blue(s) * m).toInt())
                    }
                    out.drawTransformed(t, x, y, cell * 1.18f, jitter(rnd, 0.4f))
                }
                x += cell
            }
            y += cell * 0.9f
            row++
        }
        out
    }

    /** The other emoji orbiting the base like a halo of moons. */
    private val orbit = Recipe("orbit", false) { base, other, n, rnd ->
        val out = canvas(n)
        val count = 8
        val radius = n * 0.385f
        val phase = rnd.nextFloat() * (2f * PI.toFloat() / count)
        val tile = fit(other, n / 3, 1f)
        for (i in 0 until count) {
            val ang = phase + i * (2f * PI.toFloat() / count)
            out.drawTransformed(tile, n / 2f + cos(ang) * radius, n / 2f + sin(ang) * radius,
                n * 0.2f, ang + (PI / 2).toFloat() + jitter(rnd, 0.2f))
        }
        out.placeWithShadow(fit(base, n, 0.58f), n / 2f, n / 2f, n * 0.58f, 0f)
        out
    }

    /** A confetti shower of the other emoji around the base. */
    private val shower = Recipe("shower", false) { base, other, n, rnd ->
        val out = canvas(n)
        val tile = fit(other, n / 3, 1f)
        val spots = ArrayList<FloatArray>()
        var guard = 0
        while (spots.size < 11 && guard++ < 400) {
            val x = n * (0.08f + rnd.nextFloat() * 0.84f)
            val y = n * (0.08f + rnd.nextFloat() * 0.84f)
            val dc = Math.hypot((x - n / 2f).toDouble(), (y - n / 2f).toDouble()).toFloat()
            if (dc < n * 0.30f) continue
            if (spots.any { Math.hypot((it[0] - x).toDouble(), (it[1] - y).toDouble()) < n * 0.17 }) continue
            spots.add(floatArrayOf(x, y))
        }
        for (s in spots) {
            val size = n * (0.13f + rnd.nextFloat() * 0.09f)
            out.drawTransformed(tile, s[0], s[1], size, jitter(rnd, 0.9f))
        }
        out.placeWithShadow(fit(base, n, 0.62f), n / 2f, n / 2f, n * 0.62f, jitter(rnd, 0.06f))
        out
    }

    /** "Infection": the base dyed in the other's colours and swirled,
     *  with the other as a badge. */
    private val infect = Recipe("infect", false) { base, other, n, rnd ->
        val out = canvas(n)
        val hue = other.dominantHue()
        var body = fit(base, n, 0.84f)
        if (hue != null) body = body.hueShifted(hue, 0.85f)
        body = body.swirled(0.7f + jitter(rnd, 0.15f))
        out.placeWithShadow(body, n / 2f, n * 0.48f, n * 0.84f, 0f)
        out.placeWithShadow(fit(other, n / 3, 1f), n * 0.76f, n * 0.74f, n * 0.34f,
            0.25f + jitter(rnd, 0.1f))
        out
    }

    /** A wavy-seam fusion of the two, crossfaded. */
    private val fusion = Recipe("fusion", false) { base, other, n, rnd ->
        val a = fit(base, n, 0.90f)
        val b = fit(other, n, 0.90f)
        val out = canvas(n)
        val freq = 2f * PI.toFloat() * (1.2f + rnd.nextFloat() * 0.8f) / n
        val phase = rnd.nextFloat() * 6f
        val band = n * 0.14f
        for (y in 0 until n) for (x in 0 until n) {
            val seam = n * 0.5f + n * 0.07f * sin(y * freq + phase)
            val t = ((x - seam) / band + 0.5f).coerceIn(0f, 1f)   // 0 = base, 1 = other
            val pa = a.px[y * n + x]
            val pb = b.px[y * n + x]
            val wa = Raster.alpha(pa) / 255f * (1f - t)
            val wb = Raster.alpha(pb) / 255f * t
            val tot = wa + wb
            if (tot <= 0.002f) continue
            out.px[y * n + x] = Raster.pack(
                (min(1f, tot) * 255).toInt(),
                ((Raster.red(pa) * wa + Raster.red(pb) * wb) / tot).toInt(),
                ((Raster.green(pa) * wa + Raster.green(pb) * wb) / tot).toInt(),
                ((Raster.blue(pa) * wa + Raster.blue(pb) * wb) / tot).toInt())
        }
        out
    }

    val all: List<Recipe> = listOf(
        faceBest, faceAlt, swapFace, eyes, texture, hat, infect, mosaic, shower, mouth, orbit, held, twin, fusion
    )
    val byId: Map<String, Recipe> = all.associateBy { it.id }

    /**
     * The variants to offer for a pair: both orderings (either emoji can
     * be the base), face-only recipes only on face bases, ordered so the
     * most striking/readable come first. Deterministic for a given
     * [salt]; bumping it re-rolls the random details.
     */
    fun variantsFor(a: MixInput, b: MixInput, salt: Long = 0L, max: Int = 24): List<MixVariant> {
        val out = ArrayList<MixVariant>()
        val pairSeed = (a.text.hashCode() * 31L + b.text.hashCode()) * 1_000_003L + salt
        fun add(r: Recipe, base: MixInput, other: MixInput) {
            if (r.needsFace && !base.isFace) return
            if (r.accepts?.invoke(base, other) == false) return
            out.add(MixVariant(r.id, base, other, pairSeed + r.id.hashCode() + if (base === a) 0 else 7))
        }
        for (r in all) {
            add(r, a, b)
            add(r, b, a)
        }
        // Symmetric-looking recipes in both orders are near-duplicates:
        // keep the second only when the pair is mixed-kind.
        val seen = HashSet<String>()
        val unique = out.filter { v ->
            val key = if (v.recipe == "fusion" || v.recipe == "twin")
                v.recipe + setOf(v.base.text, v.other.text).sorted().joinToString() + abs(v.seed % 2)
            else v.recipe + v.base.text + v.other.text
            seen.add(key)
        }
        return unique.take(max)
    }
}
