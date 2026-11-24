package com.pckeyboard.ime.emoji.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixRecipesTest {

    /** A synthetic "face": yellow disc, two dark eyes and a pink mouth. */
    private fun face(n: Int = 128): Raster {
        val r = Raster(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x + 0.5f - n / 2f; val dy = y + 0.5f - n / 2f
            if (dx * dx + dy * dy > (n * 0.46f) * (n * 0.46f)) continue
            var c = Raster.pack(255, 250, 200, 40)
            if (Math.hypot((dx + n * 0.17).toDouble(), (dy + n * 0.1).toDouble()) < n * 0.06 ||
                Math.hypot((dx - n * 0.17).toDouble(), (dy + n * 0.1).toDouble()) < n * 0.06) {
                c = Raster.pack(255, 60, 40, 10)
            }
            if (dy > n * 0.15 && dy < n * 0.22 && Math.abs(dx) < n * 0.12) c = Raster.pack(255, 230, 60, 90)
            r.px[y * n + x] = c
        }
        return r
    }

    /** A synthetic "turtle": a big green body disc and a smaller head disc beside it. */
    private fun turtle(n: Int = 128): Raster {
        val r = Raster(n, n)
        fun disc(cx: Float, cy: Float, rad: Float, c: Int) {
            for (y in 0 until n) for (x in 0 until n) {
                if (Math.hypot((x + 0.5 - cx), (y + 0.5 - cy)) < rad) r.px[y * n + x] = c
            }
        }
        disc(n * 0.58f, n * 0.5f, n * 0.30f, Raster.pack(255, 60, 120, 40))
        disc(n * 0.18f, n * 0.52f, n * 0.14f, Raster.pack(255, 140, 200, 60))
        return r
    }

    @Test
    fun fitSquareCentresAndScales() {
        val small = Raster(100, 100)
        for (y in 10 until 30) for (x in 60 until 80) small.px[y * 100 + x] = Raster.pack(255, 255, 0, 0)
        val fit = small.fitSquare(64, 1f)
        val b = fit.bounds()!!
        assertTrue("fills the canvas", b[2] - b[0] >= 60 && b[3] - b[1] >= 60)
    }

    @Test
    fun skinColorAndFeatureExtraction() {
        val f = face()
        val skin = f.skinColor()
        assertTrue(Raster.red(skin) > 230 && Raster.blue(skin) < 80)
        val feats = f.featuresOnly(skin)
        val n = f.w
        // Skin is gone, eyes and mouth stay.
        assertEquals(0, Raster.alpha(feats.px[(n * 0.4).toInt() * n + (n * 0.5).toInt()]))
        assertTrue(Raster.alpha(feats.px[(n * 0.4).toInt() * n + (n * (0.5 - 0.17)).toInt()]) > 200)
        assertTrue(Raster.alpha(feats.px[(n * 0.68).toInt() * n + (n * 0.5).toInt()]) > 200)
    }

    @Test
    fun inscribedCirclesFindBodyAndHead() {
        val c = turtle().inscribedCircles(2)
        assertEquals(2, c.size)
        assertTrue("body first", c[0].r > c[1].r)
        assertTrue("head is beside (left of) the body", c[1].cx < c[0].cx - 0.2f)
    }

    @Test
    fun transplantPutsFeaturesOnTheHead() {
        val body = MixInput("🐢", turtle(), false)
        val donor = MixInput("😅", face(), true)
        val v = MixRecipes.variantsFor(body, donor).first()
        assertEquals("face", v.recipe)
        val img = v.render(256)
        // Dark feature pixels appear around the head (left), where the
        // plain head is light green with no dark pixels.
        var dark = 0
        for (y in 0 until 256) for (x in 0 until 110) {
            val p = img.px[y * 256 + x]
            if (Raster.alpha(p) > 200 && Raster.luminance(p) < 0.25f) dark++
        }
        assertTrue("features landed on the head ($dark)", dark > 20)
    }

    @Test
    fun recipeCompatibilityRules() {
        val face1 = MixInput("😀", face(), true)
        val face2 = MixInput("😍", face(), true)
        val obj = MixInput("🍕", turtle(), false)
        val obj2 = MixInput("🍎", turtle(), false)
        fun ids(a: MixInput, b: MixInput) = MixRecipes.variantsFor(a, b).map { it.recipe }.toSet()

        // face + object: transplant yes, swapface no
        val fo = ids(face1, obj)
        assertTrue("face" in fo && "swapface" !in fo)
        // two faces: swapface yes
        assertTrue("swapface" in ids(face1, face2))
        // two objects: no face-specific recipes at all
        val oo = ids(obj, obj2)
        assertFalse(oo.any { it in setOf("face", "face_mid", "swapface", "eyes", "mouth") })
        assertTrue("orbit" in oo && "texture" in oo)
    }

    @Test
    fun rendersAreNonEmptyAndDeterministic() {
        val a = MixInput("😀", face(), true)
        val b = MixInput("🍕", turtle(), false)
        for (v in MixRecipes.variantsFor(a, b)) {
            val img = v.render(128)
            assertEquals(128, img.w)
            assertTrue("${v.recipe} drew something", img.bounds() != null)
        }
        val v = MixRecipes.variantsFor(a, b).first()
        assertTrue(v.render(96).px.contentEquals(v.render(96).px))
        // Re-rolling changes the random details.
        val other = MixRecipes.variantsFor(a, b, salt = 5).first()
        assertEquals(v.recipe, other.recipe)
    }

    @Test
    fun stickerOutlineSurroundsTheSilhouette() {
        val img = face(96).stickerized()
        // A pixel just outside the disc edge is now opaque white-ish.
        val edge = img.px[48 * 96 + (96 * 0.5 + 96 * 0.46 + 1).toInt()]
        assertTrue(Raster.alpha(edge) > 150)
    }
}
