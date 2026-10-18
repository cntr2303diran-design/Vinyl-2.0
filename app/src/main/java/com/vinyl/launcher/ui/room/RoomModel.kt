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
 * Geometri bodi TV dalam pecahan kotak TV, diukur dari gambar referensi (TV 547 x 407 px): layar cembung lebar di
 * kiri, panel kontrol di kanan berisi knob penala (atas), knob seek (tengah), panel kayu dengan VFD, dan tombol daya.
 * Dipakai bersama oleh gambar TV (Canvas) dan area sentuh knob agar keduanya selalu sejajar.
 */
object TvGeometry {
    /** Rasio lebar : tinggi bodi TV (referensi 547 : 407). */
    const val ASPECT = 547f / 407f
    /** Kaca layar. */
    val SCREEN = FracRect(0.077f, 0.108f, 0.707f, 0.749f)
    /** Ceruk gelap di sekeliling kaca. */
    val BEZEL = FracRect(0.058f, 0.083f, 0.745f, 0.80f)
    /** Panel kontrol di kanan layar. */
    val PANEL = FracRect(0.828f, 0.049f, 0.144f, 0.79f)
    /** Strip kayu di atas panel. */
    val WOOD_TOP = FracRect(0.828f, 0.049f, 0.144f, 0.049f)
    /** Knob penala (dekoratif, atas). Pusat dalam pecahan lebar (x) / tinggi (y); jari-jari dalam pecahan LEBAR. */
    const val TUNER_CX = 0.901f
    const val TUNER_CY = 0.204f
    const val TUNER_R = 0.051f
    /** Knob seek (tengah). */
    const val KNOB_CX = 0.901f
    const val KNOB_CY = 0.459f
    const val KNOB_R = 0.053f
    /** Panel kayu bawah yang menampung VFD. */
    val WOOD_VFD = FracRect(0.832f, 0.58f, 0.138f, 0.24f)
    /** VFD kecil (tiga baris). */
    val VFD = FracRect(0.842f, 0.663f, 0.117f, 0.127f)
    /** Tombol daya kecil / lampu. */
    const val LED_CX = 0.940f
    const val LED_CY = 0.876f
    /** Alas bodi (pita lebih gelap) dan kaki. */
    const val BASE_TOP = 0.915f
    /** Jari-jari sudut bodi dan layar (pecahan lebar TV / lebar layar). */
    const val BODY_CORNER = 0.055f
    const val SCREEN_CORNER = 0.16f
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
