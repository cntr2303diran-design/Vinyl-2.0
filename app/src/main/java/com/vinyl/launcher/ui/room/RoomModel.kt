package com.vinyl.launcher.ui.room

import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.BackdropDim
import com.vinyl.launcher.ui.theme.BackdropLift
import com.vinyl.launcher.ui.theme.ColorMath
import com.vinyl.launcher.ui.theme.Tokens

/*
 * Keputusan ruang TV yang murni (tanpa Compose) sehingga bisa diuji: mode layar CRT, tata letak TV/kaset,
 * dan kontras teks di atas dinding yang menerima proyeksi.
 */

/** Tiga mode layar TV (spesifikasi: A Standby, B Album Display, C Album Motion). */
enum class TvScreenMode { STANDBY, DISPLAY, MOTION }

object TvScreen {
    /**
     * Mode layar. Tanpa lagu → standby. Ada lagu → cover tampil; bergerak (Mode C) hanya bila pengguna memilih gaya
     * gerak, lagu sedang diputar, dan animasi tidak dimatikan sistem/penghemat baterai. Saat jeda, gerak berhenti
     * (kembali ke Mode B) sesuai spesifikasi.
     */
    fun mode(hasTrack: Boolean, isPlaying: Boolean, motion: AlbumMotion, motionAllowed: Boolean): TvScreenMode = when {
        !hasTrack -> TvScreenMode.STANDBY
        motion != AlbumMotion.OFF && isPlaying && motionAllowed -> TvScreenMode.MOTION
        else -> TvScreenMode.DISPLAY
    }
}

/** Kotak dalam satuan pecahan lebar/tinggi panggung (0..1). */
data class FracRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    fun overlaps(o: FracRect, eps: Float = 1e-4f): Boolean =
        left < o.right - eps && o.left < right - eps && top < o.bottom - eps && o.top < bottom - eps
    fun inside(eps: Float = 1e-4f): Boolean = left >= -eps && top >= -eps && right <= 1f + eps && bottom <= 1f + eps
}

/**
 * Geometri bodi TV dalam pecahan kotak TV. Klasik: layar di kiri, panel kontrol di kanan (knob, VFD, kisi speaker).
 * Dipakai bersama oleh gambar TV (Canvas) dan area sentuh knob agar keduanya selalu sejajar.
 */
object TvGeometry {
    /** Rasio lebar : tinggi bodi TV. */
    const val ASPECT = 1.36f
    val SCREEN = FracRect(0.065f, 0.095f, 0.63f, 0.80f)
    /** Panel kontrol di kanan layar. */
    val PANEL = FracRect(0.735f, 0.095f, 0.20f, 0.80f)
    /** Knob seek (lingkaran di dalam panel); pusat & jari-jari dalam pecahan LEBAR TV. */
    const val KNOB_CX = 0.835f
    const val KNOB_CY = 0.30f
    const val KNOB_R = 0.062f
    /** VFD kecil di panel, di bawah knob. */
    val VFD = FracRect(0.748f, 0.50f, 0.174f, 0.11f)
    /** Lampu standby. */
    const val LED_CX = 0.90f
    const val LED_CY = 0.82f
    /** Jari-jari sudut bodi dan layar (pecahan lebar TV / lebar layar). */
    const val BODY_CORNER = 0.07f
    const val SCREEN_CORNER = 0.14f
}

/**
 * Tata letak panggung ruang TV: TV dan kaset berdiri di lantai. Landscape: berdampingan (TV kiri, kaset kanan).
 * Portrait: TV di atas, kaset di bawah (dekat jempol). Ukuran TV dari pengaturan (turntableSize, artinya kini
 * "ukuran objek utama"), posisi kiri/tengah/kanan dari turntablePosition.
 */
object RoomLayout {
    data class Stage(val tv: FracRect, val cassette: FracRect, val floorY: Float)

    /** Rasio lebar : tinggi kaset (cangkang kaset kompak ~ 10 x 6,4 cm). */
    const val CASSETTE_ASPECT = 1.56f

    /**
     * @param aspect lebar / tinggi area panggung
     * @param size skala TV 0,3..1,2 (dibatasi)
     * @param align -1 kiri, 0 tengah, 1 kanan
     */
    fun stage(aspect: Float, landscape: Boolean, size: Float, align: Int): Stage {
        val s = size.coerceIn(0.3f, 1.2f)
        val a = aspect.coerceIn(0.3f, 4f)
        return if (landscape) {
            // Lebar total = TV + jarak + kaset; dibatasi agar muat tinggi
            val gap = 0.03f
            var tvW = 0.58f * s
            var tvH = tvW * a / TvGeometry.ASPECT
            if (tvH > 0.86f) { tvH = 0.86f; tvW = tvH * TvGeometry.ASPECT / a }
            var casW = (tvW * 0.48f).coerceAtMost(0.30f)
            if (tvW + gap + casW > 0.98f) casW = 0.98f - gap - tvW
            val casH = casW * a / CASSETTE_ASPECT
            val total = tvW + gap + casW
            val left = when { align < 0 -> 0.01f; align > 0 -> 0.99f - total; else -> (1f - total) / 2f }
            val floorY = ((1f + tvH) / 2f).coerceAtMost(0.97f)
            Stage(
                tv = FracRect(left, floorY - tvH, tvW, tvH),
                cassette = FracRect(left + tvW + gap, floorY - casH, casW, casH),
                floorY = floorY,
            )
        } else {
            var tvW = (0.88f * s).coerceAtMost(0.96f)
            var tvH = tvW * a / TvGeometry.ASPECT
            val gap = 0.04f
            var casW = (tvW * 0.72f).coerceAtMost(0.80f)
            var casH = casW * a / CASSETTE_ASPECT
            val maxH = 0.96f
            if (tvH + gap + casH > maxH) {
                val k = maxH / (tvH + gap + casH)
                tvW *= k; tvH *= k; casW *= k; casH *= k
            }
            val tvLeft = when { align < 0 -> 0.02f; align > 0 -> 0.98f - tvW; else -> (1f - tvW) / 2f }
            val top = (1f - (tvH + gap + casH)) / 2f
            val floorY = top + tvH
            Stage(
                tv = FracRect(tvLeft, top, tvW, tvH),
                cassette = FracRect((1f - casW) / 2f, floorY + gap, casW, casH),
                floorY = floorY,
            )
        }
    }
}

/** Warna teks di ruang TV dan perlindungan kontrasnya terhadap proyeksi. */
object RoomInk {
    /** Proyeksi cover di dinding: alpha di atas dinding putih. Cukup terlihat, tetap terasa "cahaya di dinding". */
    const val PROJECTION_ALPHA = 0.55

    fun wall(tone: RoomTone): Long = if (tone == RoomTone.LIGHT) Tokens.Retro.WARM_WHITE else Tokens.Retro.CHARCOAL_DEEP
    fun primary(tone: RoomTone): Long = if (tone == RoomTone.LIGHT) Tokens.Retro.INK else Tokens.Retro.CREAM_TEXT
    fun secondary(tone: RoomTone): Long = if (tone == RoomTone.LIGHT) Tokens.Retro.INK_SECONDARY else Tokens.Retro.CREAM_TEXT_SECONDARY

    /** [fg] (opak) ditumpuk di atas [bg] dengan alpha [a], dibulatkan 8-bit. */
    fun over(fg: Long, bg: Long, a: Double): Long {
        val k = a.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = Math.round(((fg shr shift) and 0xFF) * k + ((bg shr shift) and 0xFF) * (1 - k))
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Alpha "pelat" (warna dinding) di belakang teks agar teks ruang tetap >= 4,5:1 walau proyeksi jatuh di belakangnya.
     * [worstCoverPixel]: piksel cover yang paling merugikan teks (tergelap untuk ruang terang, terterang untuk ruang
     * redup); null bila tidak ada proyeksi. Hasil 0 = pelat tidak terlihat.
     */
    fun plateAlpha(worstCoverPixel: Long?, tone: RoomTone, projectionAlpha: Double = PROJECTION_ALPHA): Double {
        if (worstCoverPixel == null) return 0.0
        val bg = over(worstCoverPixel, wall(tone), projectionAlpha)
        return if (tone == RoomTone.LIGHT) BackdropLift.minVeilAlpha(bg)
        else BackdropDim.minOverlayAlpha(bg, listOf(Tokens.Retro.CREAM_TEXT, Tokens.Retro.CREAM_TEXT_SECONDARY), effects = false)
    }

    /**
     * Nada TEKS di ruang: mengikuti nada ruang, kecuali pengguna memilih dinding berwarna sendiri ([wallArgb], bawaan
     * tema vinyl biasanya gelap) — maka tinta mengikuti mana yang lebih terbaca di atas dinding itu.
     */
    fun textTone(room: RoomTone, wallArgb: Long?): RoomTone = when {
        wallArgb == null -> room
        ColorMath.darkTextIsBetter(wallArgb or (0xFFL shl 24), Tokens.Retro.INK, Tokens.Retro.CREAM_TEXT) -> RoomTone.LIGHT
        else -> RoomTone.DIM
    }

    /**
     * Warna jam/widget pilihan pengguna dipakai bila terbaca (>= 4,5:1) di atas dinding; kalau tidak — misalnya krem
     * terang bawaan tema vinyl di atas dinding putih — diganti tinta ruang. Pilihan tersimpan tidak diubah.
     */
    fun readable(userArgb: Long, tone: RoomTone): Long {
        val opaque = userArgb or (0xFFL shl 24)
        return if (ColorMath.contrast(opaque, wall(tone)) >= Tokens.A11y.MIN_CONTRAST) opaque else primary(tone)
    }
}
