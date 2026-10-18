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
 * Geometri muka depan TV (kabinet 62 × 47 cm) dalam pecahan lebar/tinggi muka: layar cembung lebar di kiri, kolom kontrol
 * di kanan berisi kisi speaker, knob penala, knob SEEK, VFD kecil, lampu dan tombol daya. Dipakai bersama oleh gambar TV
 * dan area sentuh knob agar keduanya selalu sejajar.
 */
object TvGeometry {
    /** Rasio lebar : tinggi muka depan. */
    const val ASPECT = 62f / 47f
    /** Kaca layar. */
    val SCREEN = FracRect(0.058f, 0.090f, 0.700f, 0.775f)
    /** Bingkai tebal di sekeliling kaca. */
    val BEZEL = FracRect(0.030f, 0.052f, 0.756f, 0.851f)
    /** Kolom kontrol di kanan layar. */
    val PANEL = FracRect(0.810f, 0.058f, 0.158f, 0.884f)
    /** Kisi speaker di atas kolom kontrol. */
    val GRILLE = FracRect(0.832f, 0.096f, 0.114f, 0.250f)
    /** Knob penala (dekoratif). Pusat dalam pecahan lebar (x) / tinggi (y); jari-jari dalam pecahan LEBAR. */
    const val TUNER_CX = 0.889f
    const val TUNER_CY = 0.455f
    const val TUNER_R = 0.040f
    /** Knob SEEK (putar untuk menggeser posisi lagu). */
    const val KNOB_CX = 0.889f
    const val KNOB_CY = 0.640f
    const val KNOB_R = 0.046f
    /** VFD kecil satu baris. */
    val VFD = FracRect(0.828f, 0.770f, 0.122f, 0.062f)
    /** Lampu daya & tombol daya. */
    const val LED_CX = 0.852f
    const val LED_CY = 0.892f
    const val POWER_CX = 0.918f
    const val POWER_CY = 0.892f
    /** Jari-jari sudut kabinet dan layar (pecahan lebar TV / lebar layar). */
    const val BODY_CORNER = 0.028f
    const val SCREEN_CORNER = 0.13f
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
