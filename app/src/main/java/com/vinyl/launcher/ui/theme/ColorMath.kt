package com.vinyl.launcher.ui.theme

import kotlin.math.abs
import kotlin.math.pow

/** Satu warna HSV: [h] derajat 0..360, [s] dan [v] 0..1 (sama dengan android.graphics.Color.colorToHSV). */
data class Hsv(val h: Float, val s: Float, val v: Float)

/** Matematika warna murni (tanpa Android/Compose): luminans & kontras WCAG 2.x, serta konversi HSV. */
object ColorMath {

    /** sRGB 0..1 → linear. */
    private fun linear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** Luminans relatif (0..1) dari komponen sRGB 0..1. */
    fun luminance(r: Double, g: Double, b: Double): Double = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)

    private fun channel(argb: Long, shift: Int): Double = ((argb shr shift) and 0xFF) / 255.0

    /** Luminans warna ARGB (alpha diabaikan). */
    fun luminance(argb: Long): Double = luminance(channel(argb, 16), channel(argb, 8), channel(argb, 0))

    /**
     * Rasio kontras WCAG antara [fgArgb] (alpha-nya diperhitungkan: dikomposit di atas [bgArgb]) dan [bgArgb] yang dianggap opak.
     */
    fun contrast(fgArgb: Long, bgArgb: Long): Double {
        val a = channel(fgArgb, 24)
        fun mix(shift: Int) = a * channel(fgArgb, shift) + (1 - a) * channel(bgArgb, shift)
        val l1 = luminance(mix(16), mix(8), mix(0))
        val l2 = luminance(bgArgb)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** True bila [dark] lebih terbaca daripada [light] di atas latar [bgArgb]. */
    fun darkTextIsBetter(bgArgb: Long, dark: Long = Tokens.Argb.TEXT_ON_LIGHT, light: Long = Tokens.Argb.TEXT_ON_DARK): Boolean =
        contrast(dark, bgArgb) >= contrast(light, bgArgb)

    /** HSV (h derajat) → sRGB 0..1. */
    fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Double, Double, Double> {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = (v * s).toDouble()
        val x = c * (1 - abs(hh % 2 - 1))
        val m = (v - c)
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return Triple(r + m, g + m, b + m)
    }

    fun luminance(hsv: Hsv): Double = hsvToRgb(hsv.h, hsv.s, hsv.v).let { (r, g, b) -> luminance(r, g, b) }
}

/**
 * Warna latar ambient yang diturunkan dari cover. Cover bisa berwarna apa saja, jadi warna dibatasi agar
 * teks putih di atasnya selalu terbaca: saturasi dibatasi, kecerahan (V) dibatasi, DAN luminans dibatasi
 * ([MAX_LUMINANCE]). Membatasi luminans (bukan hanya V) menjaga biru/ungu tetap pekat sementara kuning/hijau
 * — yang luminansnya tinggi pada V sama — diredupkan lebih banyak.
 */
object AmbientColor {
    /** Luminans relatif maksimum latar ambient. Dengan nilai ini TextTertiary pun >= 4.5:1 (dibuktikan ContrastTest). */
    const val MAX_LUMINANCE = 0.05

    const val MIN_VALUE = 0.18f
    const val MAX_SATURATION = 0.85f

    fun limit(hsv: Hsv, maxValue: Float = 0.55f, minSat: Float = 0.25f): Hsv {
        val s = hsv.s.coerceIn(minSat.coerceIn(0f, MAX_SATURATION), MAX_SATURATION)
        val vCap = maxValue.coerceIn(MIN_VALUE, 1f)
        var v = hsv.v.coerceIn(MIN_VALUE, vCap)
        if (ColorMath.luminance(Hsv(hsv.h, s, v)) > MAX_LUMINANCE) {
            var lo = MIN_VALUE
            var hi = v
            repeat(24) {
                val mid = (lo + hi) / 2f
                if (ColorMath.luminance(Hsv(hsv.h, s, mid)) <= MAX_LUMINANCE) lo = mid else hi = mid
            }
            v = lo
        }
        return Hsv(hsv.h, s, v)
    }
}

/**
 * Peredam adaptif untuk latar yang berasal dari cover album. Cover bisa berwarna apa saja — termasuk putih —
 * sementara teks di atasnya putih. Alih-alih menggelapkan semuanya secara buta, alpha lapisan hitam dihitung dari
 * piksel TERTERANG cover agar teks utama dan sekunder tetap >= 4,5:1. Cover gelap tidak ikut digelapkan.
 *
 * Teks tersier sengaja tidak dijamin di atas latar cover (lihat [TEXTS]); pakai hanya untuk dekorasi.
 */
object BackdropDim {
    /** Cadangan di atas 4,5:1 untuk pembulatan warna 8-bit saat dirender. */
    const val SAFETY_MARGIN = 0.15

    /** Tingkat teks yang boleh diletakkan langsung di atas latar cover. */
    val TEXTS: List<Long> = listOf(Tokens.Argb.TEXT_PRIMARY, Tokens.Argb.TEXT_SECONDARY)

    /** [bgArgb] ditimpa lapisan hitam beralpha [alpha] (0..1), hasil dibulatkan ke 8-bit seperti saat dirender. */
    fun darken(bgArgb: Long, alpha: Double): Long {
        val k = 1.0 - alpha.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = Math.round(((bgArgb shr shift) and 0xFF) * k)
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * [bgArgb] ditimpa lapisan putih beralpha [veil] (0..1): permukaan kaca di atas latar. Teks pada kartu atau kapsul
     * kaca melihat latar yang SEDIKIT LEBIH TERANG daripada latar polos, jadi kontras harus diukur di sini.
     */
    fun withGlass(bgArgb: Long, veil: Double = Tokens.A11y.GLASS_VEIL): Long {
        val a = veil.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = Math.round(((bgArgb shr shift) and 0xFF) * (1.0 - a) + 255.0 * a)
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * [bgArgb] setelah lapisan efek (partikel, cahaya) menumpuk di atasnya: efek hanya bisa memutihkan sampai [veil].
     * Urutan render: latar → peredam → efek → konten, jadi pengukuran kontras memutihkan latar yang SUDAH diredam.
     * Hitungannya sama dengan [withGlass] (menimpa putih secara proporsional).
     */
    fun withFx(bgArgb: Long, veil: Double = Tokens.A11y.FX_VEIL): Long = withGlass(bgArgb, veil)

    /**
     * [bgArgb] setelah distorsi kaca di titik paling terang: tiap kanal dikali paling banyak (1 + [gain]) lalu dipotong
     * ke 255. Distorsi diterapkan pada lapisan latar (di bawah peredam), jadi pencerahannya perkalian pada warna latar,
     * bukan penimpaan putih seperti [withFx]/[withGlass]; menghitungnya sebagai putih akan meremehkan kanal terang
     * (kanal 0,9 naik 0,027, padahal 0,03 putih hanya menambah 0,003).
     */
    fun withDist(bgArgb: Long, gain: Double = Tokens.A11y.DIST_PEAK.toDouble()): Long {
        val k = 1.0 + gain.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = minOf(255L, Math.round(((bgArgb shr shift) and 0xFF) * k))
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Isi kaca yang benar-benar dipakai untuk permintaan [requested]: dibatasi 0..[Tokens.A11y.MAX_GLASS_FILL]. */
    fun glassFill(requested: Float): Float =
        if (requested.isNaN()) 0f else requested.coerceIn(0f, Tokens.A11y.MAX_GLASS_FILL)

    private fun worstContrast(bg: Long, texts: List<Long>): Double = texts.minOf { ColorMath.contrast(it, bg) }

    /**
     * Alpha lapisan hitam terkecil (0..1) agar semua [texts] mencapai kontras >= [target] di atas [bgArgb] yang telah
     * digelapkan, baik langsung di atas latar maupun di atas permukaan kaca ([withGlass]) di atasnya. Latar lebih dulu
     * dicerahkan distorsi kaca ([withDist], terjadi sebelum peredam); setelah diredam, latar diputihkan sebesar anggaran
     * efek ([withFx]) karena partikel/cahaya digambar di atas peredam.
     * Kontras makin tinggi seiring latar makin gelap, jadi pencarian biner cukup. 0 bila sudah lolos.
     *
     * @param effects true (bawaan, aman) bila layar ini bisa menampilkan efek visual — distorsi, cahaya, partikel. Layar yang
     * TIDAK pernah menggambar efek (lirik, app drawer, rak lagu) mengisi false agar peredamnya tidak lebih pekat dari perlu.
     */
    fun minOverlayAlpha(
        bgArgb: Long,
        texts: List<Long> = TEXTS,
        target: Double = Tokens.A11y.MIN_CONTRAST + SAFETY_MARGIN,
        effects: Boolean = true,
    ): Double {
        val lit = if (effects) withDist(bgArgb) else bgArgb
        fun seen(c: Long) = withGlass(if (effects) withFx(c) else c)
        if (worstContrast(seen(lit), texts) >= target) return 0.0
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val mid = (lo + hi) / 2
            if (worstContrast(seen(darken(lit, mid)), texts) >= target) hi = mid else lo = mid
        }
        return hi
    }

    /** Piksel (ARGB, dibuat opak) dengan luminans tertinggi; null bila [pixels] kosong. */
    fun brightest(pixels: IntArray): Long? {
        var best: Long? = null
        var bestLum = -1.0
        for (p in pixels) {
            val argb = (p.toLong() and 0xFFFFFFFFL) or (0xFFL shl 24)
            val l = ColorMath.luminance(argb)
            if (l > bestLum) { bestLum = l; best = argb }
        }
        return best
    }

    /**
     * Tiga titik gradien peredam (atas, tengah, bawah) dari [dim] dasar — atas lebih tipis, bawah lebih tebal (vignette) —
     * dengan setiap titik minimal [needed], dan dibatasi 0..1 (alpha > 1 membuat Compose melempar exception).
     */
    fun stops(dim: Float, needed: Double): Triple<Float, Float, Float> {
        val n = needed.coerceIn(0.0, 1.0).toFloat()
        fun s(a: Float) = maxOf(a, n).coerceIn(0f, 1f)
        return Triple(s(dim * 0.7f), s(dim), s(dim * 1.35f))
    }
}

/**
 * Kebalikan [BackdropDim] untuk tema TERANG (ruang putih "Retro Projection"): teks GELAP di atas dinding yang bisa
 * menerima proyeksi berwarna apa saja (cover gelap, gambar pengguna). Alih-alih menggelapkan, latar DIPUTIHKAN dengan
 * lapisan putih beralpha terkecil yang membuat teks gelap tetap >= 4,5:1.
 *
 * Urutan render yang diasumsikan: latar/proyeksi → lapisan putih ini → efek ruang yang MENGGELAPKAN (vignette CRT,
 * bayangan kontak, sudut ruangan; paling banyak [Tokens.A11y.LIGHT_SHADE_PEAK]) → teks. Efek terang (glow) hanya menambah
 * kontras teks gelap, jadi tidak dihitung.
 *
 * Pencarian biner aman karena predikatnya monoton: selama latar masih lebih gelap daripada teks, kontras paling banyak
 * (L_teks + 0,05) / 0,05 — untuk teks di [TEXTS] itu < 2,7, jauh di bawah target — sehingga predikat "lolos" baru bisa
 * benar setelah latar lebih terang daripada teks, dan dari situ kontras naik seiring alpha (dibuktikan BackdropLiftTest).
 */
object BackdropLift {
    /** Tingkat teks yang boleh diletakkan langsung di atas dinding/lantai ruang terang. */
    val TEXTS: List<Long> = listOf(Tokens.Retro.INK, Tokens.Retro.INK_SECONDARY)

    /** [bgArgb] ditimpa lapisan putih beralpha [alpha] (0..1). */
    fun lighten(bgArgb: Long, alpha: Double): Long = BackdropDim.withGlass(bgArgb, alpha)

    /** [bgArgb] setelah efek ruang yang menggelapkan, paling banyak [shade]. */
    fun withShade(bgArgb: Long, shade: Double = Tokens.A11y.LIGHT_SHADE_PEAK): Long = BackdropDim.darken(bgArgb, shade)

    private fun worstContrast(bg: Long, texts: List<Long>): Double = texts.minOf { ColorMath.contrast(it, bg) }

    /**
     * Alpha lapisan putih terkecil (0..1) agar semua [texts] mencapai >= [target] di atas [bgArgb] yang telah diputihkan,
     * termasuk setelah efek penggelap ruang bila [effects] = true. 0 bila latar sudah cukup terang.
     */
    fun minVeilAlpha(
        bgArgb: Long,
        texts: List<Long> = TEXTS,
        target: Double = Tokens.A11y.MIN_CONTRAST + BackdropDim.SAFETY_MARGIN,
        effects: Boolean = true,
    ): Double {
        fun seen(c: Long) = if (effects) withShade(c) else c
        if (worstContrast(seen(bgArgb), texts) >= target) return 0.0
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val mid = (lo + hi) / 2
            if (worstContrast(seen(lighten(bgArgb, mid)), texts) >= target) hi = mid else lo = mid
        }
        return hi
    }

    /** Piksel (ARGB, dibuat opak) dengan luminans TERENDAH — titik terburuk untuk teks gelap; null bila kosong. */
    fun darkest(pixels: IntArray): Long? {
        var best: Long? = null
        var bestLum = 2.0
        for (p in pixels) {
            val argb = (p.toLong() and 0xFFFFFFFFL) or (0xFFL shl 24)
            val l = ColorMath.luminance(argb)
            if (l < bestLum) { bestLum = l; best = argb }
        }
        return best
    }
}
