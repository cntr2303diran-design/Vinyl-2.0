package com.vinyl.launcher.ui.room

/*
 * Komposisi beranda mengikuti gambar referensi (tablet landscape 1312 x 816): TV tabung krem di tengah sedikit ke
 * kanan, kaset tergeletak di lantai tepat di depannya, tombol putar mengapit kaset, garis progres di bawah kaset,
 * volume di kanan bawah, dan indikator TIME / Output di sudut atas. Semua posisi dinyatakan dalam pecahan layar dan
 * dihitung ulang untuk rasio layar apa pun; ponsel portrait memakai susunan vertikal dengan urutan yang sama.
 *
 * Angka landscape diukur langsung dari referensi (piksel / 1312 untuk x, / 816 untuk y).
 */
object SceneLayout {

    /** Rasio referensi. */
    const val REF_ASPECT = 1312f / 816f

    data class Scene(
        val tv: FracRect,
        val cassette: FracRect,
        /** Garis lantai (dasar TV). */
        val floorY: Float,
        /** Pusat vertikal baris tombol. */
        val controlsY: Float,
        /** Pusat horizontal tombol: sebelumnya, mundur 10 dtk, putar/jeda, berikutnya. */
        val controlsX: List<Float>,
        /** Garis progres (y = pusat garis). */
        val progress: FracRect,
        /** Penggeser volume (y = pusat). */
        val volume: FracRect,
        val landscape: Boolean,
    )

    /** Geser mendatar maksimum seluruh susunan untuk posisi kiri/kanan (pecahan lebar layar). */
    const val MAX_SHIFT = 0.16f

    /**
     * @param aspect lebar / tinggi area
     * @param size skala TV pilihan pengguna 0,3..1,2 (1 = seperti referensi pada ukuran bawaan 0,85)
     * @param position posisi pilihan pengguna −1 (kiri) .. 1 (kanan); hanya berlaku di layar mendatar (di ponsel tegak
     *   susunan sudah memenuhi lebar layar)
     */
    fun compute(aspect: Float, size: Float = 0.85f, position: Float = 0f): Scene {
        val a = aspect.coerceIn(0.4f, 3f)
        val k = (size.coerceIn(0.3f, 1.2f) / 0.85f)
        return if (a >= 1.05f) shifted(landscape(a, k), position) else portrait(a, k)
    }

    /** Geser TV, kaset, tombol, progres, dan volume bersama-sama, tanpa keluar dari layar. */
    private fun shifted(s: Scene, position: Float): Scene {
        val want = position.coerceIn(-1f, 1f) * MAX_SHIFT
        if (want == 0f) return s
        val minX = minOf(s.tv.left, s.cassette.left, s.controlsX.first() - 0.03f)
        val maxX = maxOf(s.tv.right, s.volume.right, s.controlsX.last() + 0.03f)
        val dx = want.coerceIn(0.02f - minX, 0.98f - maxX)
        fun FracRect.move() = copy(left = this.left + dx)
        return s.copy(
            tv = s.tv.move(), cassette = s.cassette.move(), controlsX = s.controlsX.map { it + dx },
            progress = s.progress.move(), volume = s.volume.move(),
        )
    }

    private fun landscape(a: Float, k: Float): Scene {
        // Ukuran TV di referensi: 0,417 lebar layar, tinggi 0,499. Dibatasi agar muat di rasio lain.
        var tvW = 0.417f * k
        var tvH = tvW * a / TvGeometry.ASPECT
        val maxH = 0.51f   // sisakan ruang untuk kaset, tombol, dan progres di bawah TV
        if (tvH > maxH) { tvH = maxH; tvW = tvH * TvGeometry.ASPECT / a }
        val tvCx = 0.5f + 0.023f          // referensi: sedikit ke kanan agar panel kontrol seimbang
        val floorY = 0.267f + 0.499f * (tvH / 0.499f).coerceAtMost(1.12f)
        val tvTop = (floorY - tvH).coerceAtLeast(0.10f)
        val tv = FracRect((tvCx - tvW / 2f).coerceAtLeast(0.02f), tvTop, tvW, tvH)

        val casW = (0.216f * (tvW / 0.417f)).coerceIn(0.14f, 0.30f)
        val casH = (casW * a / CASSETTE_FLAT_ASPECT).coerceAtMost(0.15f)
        val casTop = (tv.bottom + 0.016f).coerceAtMost(0.975f - 0.045f - casH)
        val cassette = FracRect(0.5f - casW / 2f, casTop, casW, casH)

        val controlsY = cassette.top + casH * 0.64f
        val gap = 0.047f
        val inner = cassette.width / 2f + 0.042f
        val controlsX = listOf(0.5f - inner - gap, 0.5f - inner, 0.5f + inner, 0.5f + inner + gap)
        val progY = (cassette.bottom + 0.045f).coerceAtMost(0.975f)
        val progress = FracRect(cassette.left, progY, cassette.width, 0f)
        val volume = FracRect(0.706f, progY, 0.094f, 0f)
        return Scene(tv, cassette, tv.bottom, controlsY, controlsX, progress, volume, landscape = true)
    }

    private fun portrait(a: Float, k: Float): Scene {
        // Seluruh tumpukan (TV, kaset, tombol, progres, volume) harus muat: batasi lebar TV menurut tinggi
        val perWidth = a * (1f / TvGeometry.ASPECT + 0.56f / CASSETTE_FLAT_ASPECT)
        val fit = (0.95f - 0.17f - 0.035f - 0.055f - 0.07f - 0.07f) / perWidth
        val tvW = minOf(0.86f * k, 0.94f, fit)
        val tvH = tvW * a / TvGeometry.ASPECT
        val top = 0.17f
        val tv = FracRect(0.5f - tvW / 2f, top, tvW, tvH)
        val casW = (tvW * 0.56f).coerceAtMost(0.62f)
        val casH = casW * a / CASSETTE_FLAT_ASPECT
        val cassette = FracRect(0.5f - casW / 2f, tv.bottom + 0.035f, casW, casH)
        val controlsY = cassette.bottom + 0.055f
        val controlsX = listOf(0.18f, 0.37f, 0.63f, 0.82f)
        val progY = controlsY + 0.07f
        val progress = FracRect(0.12f, progY, 0.76f, 0f)
        val volume = FracRect(0.30f, progY + 0.07f, 0.40f, 0f)
        return Scene(tv, cassette, tv.bottom, controlsY, controlsX, progress, volume, landscape = false)
    }

    /** Kaset tegak (cangkang kompak ~ 10 x 6,4 cm). */
    const val CASSETTE_UPRIGHT_ASPECT = 1.56f

    /** Kaset tergeletak di lantai dilihat miring: lebih pipih dari kaset tegak. Referensi: 284 x 104 px. */
    const val CASSETTE_FLAT_ASPECT = 284f / 104f
}
