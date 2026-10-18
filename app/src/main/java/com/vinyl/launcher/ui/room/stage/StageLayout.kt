package com.vinyl.launcher.ui.room.stage

import kotlin.math.PI

/*
 * Susunan panggung beranda bergaya rekaman VHS: TV tabung di atas konsol kayu rendah (papan atas tebal, rak terbuka
 * berisi deck kaset, kisi speaker kain di kiri-kanan), kaset berdiri miring di samping TV, dan proyeksi di dinding
 * berpanel kayu di belakang. Semua benda punya ukuran dunia tetap (cm); kamera dibingkai ulang untuk setiap ukuran
 * layar (ponsel tegak, tablet mendatar, lipat) sehingga tidak ada yang terpotong oleh OSD atas/bawah. TV tidak bisa
 * dipindah; yang diatur pengguna hanya ukurannya (sedikit).
 */
object StageLayout {

    // ---------- Dunia (cm)
    const val STAND_H = 46f
    const val STAND_HALF_W = 80f
    const val STAND_FRONT_Z = 40f
    const val STAND_BACK_Z = 88f
    const val TV_W = 62f
    const val TV_H = 47f
    const val TV_D = 44f
    const val TV_FRONT_Z = 50f
    const val WALL_Z = 100f
    /** Tebal papan atas konsol (tepi depannya terlihat), tinggi plint di kaki konsol, lis dinding, lebar panel dinding. */
    const val SLAB_T = 4.5f
    const val PLINTH_H = 6f
    const val BASEBOARD_H = 9f
    const val PANEL_W = 24f
    /** Deck kaset: ukuran dunia rancangan (dibesarkan bila di layar lebih kecil dari batas minimum). */
    private const val DECK_W = 46f
    private const val DECK_H = 11f
    /** Celah rak di sekeliling deck: samping, atas, dan bibir lantai rak. */
    private const val SHELF_SIDE = 3.5f
    private const val SHELF_TOP = 3f
    private const val SHELF_LIP = 1.5f
    /** Proyeksi: lebar terhadap lebar TV di layar, dan rasio gambar. */
    private const val PROJECTION_TV_RATIO = 1.8f
    private const val PROJECTION_ASPECT = 16f / 9f
    /** Kaset diperbesar 1,9× dari ukuran asli (10 × 6,35 × 1,2 cm) supaya labelnya terbaca. */
    const val CASSETTE_W = 19f
    const val CASSETTE_H = 12.06f
    const val CASSETTE_D = 2.3f
    private const val CASSETTE_YAW_DEG = 24f
    private const val CASSETTE_TILT_DEG = 11f
    /** Jarak tepi kanan TV ke pusat kaset. */
    private const val CASSETTE_GAP = 14.5f

    /** Mata kamera: sedikit di kiri dan di atas meja, seperti duduk di lantai ruangan. */
    val EYE = V3(-46f, 120f, -155f)

    // ---------- Layar (dp)
    /** Pita OSD atas/bawah (jam, baterai, tanggal, penghitung pita) yang tidak boleh ditutup benda. */
    const val OSD_TOP = 64f
    const val OSD_BOTTOM_LANDSCAPE = 68f
    const val OSD_BOTTOM_PORTRAIT = 112f
    const val SIDE = 14f
    /** Deck: tinggi minimum (tombol 48dp + bingkai) dan lebar minimum (VFD + empat tombol 48dp). */
    const val DECK_MIN_H = 64f
    const val DECK_MIN_W = 340f
    /** Ruang di atas TV yang disisakan untuk proyeksi dinding pada layar tegak (pecahan tinggi layar). */
    private const val PORTRAIT_PROJECTION_ROOM = 0.13f
    /**
     * Layar mendatar yang pendek (ponsel): teks OSD atas hanya ada di dua sudut, jadi TV boleh naik ke pita atas selama
     * ia tetap di antara kedua kolom sudut itu (lebar kolom diperkirakan untuk huruf OSD 1,5×).
     */
    private const val SHORT_LANDSCAPE_H = 560f
    private const val SHORT_LANDSCAPE_TOP = 18f
    const val OSD_CORNER_W = 210f

    data class Frame(
        val width: Float,
        val height: Float,
        val landscape: Boolean,
        val camera: StageCamera,
        val tv: Solid,
        val stand: Solid,
        val cassette: Solid,
        /** Muka depan TV (sejajar layar) dalam dp. */
        val tvFront: SRect,
        val standFront: SRect,
        /** Rak terbuka di muka depan konsol dan panel depan deck di dalamnya. */
        val shelf: SRect,
        val deck: SRect,
        /** Tepi depan papan atas konsol, plint di kakinya, dan kisi speaker kiri/kanan (null bila tidak muat). */
        val slabEdge: SRect,
        val plinth: SRect,
        val grilleLeft: SRect?,
        val grilleRight: SRect?,
        /** Batas layar kaset (area sentuh ≥ 48dp). */
        val cassetteBounds: SRect,
        /** Gambar proyeksi di dinding belakang. */
        val projection: SRect,
        /** Garis temu dinding–lantai dan tepi atas lis dinding (y layar). */
        val floorLine: Float,
        val baseboardTop: Float,
        val osdTop: Float,
        val osdBottom: Float,
    ) {
        /** Dp per cm di bidang muka depan TV / meja. */
        val tvScale: Float get() = camera.scaleAt(TV_FRONT_Z)
        val standScale: Float get() = camera.scaleAt(STAND_FRONT_Z)
    }

    /**
     * @param width lebar area dalam dp
     * @param height tinggi area dalam dp
     * @param size pengaturan ukuran TV (0,5..1; bawaan 0,85 = ukuran rancangan). Hanya mengecilkan, tidak pernah membuat
     *   benda keluar layar.
     * @param projectorSize pengaturan ukuran gambar proyeksi (0,6..1,2; 1 = rancangan)
     */
    fun compute(
        width: Float,
        height: Float,
        size: Float = 0.85f,
        topInset: Float = 0f,
        bottomInset: Float = 0f,
        projectorSize: Float = 1f,
    ): Frame {
        val w = width.coerceAtLeast(120f)
        val h = height.coerceAtLeast(160f)
        val landscape = w / h >= 1.05f
        val k = sizeFactor(size)
        val inset = topInset.coerceIn(0f, h * 0.25f)
        val osdBottom = (if (landscape) OSD_BOTTOM_LANDSCAPE else OSD_BOTTOM_PORTRAIT) + bottomInset.coerceIn(0f, h * 0.25f)
        // Ponsel mendatar: coba biarkan TV naik ke pita atas (di antara kolom sudut OSD); bila kelompok TV & kaset
        // menyentuh kolom sudut, kembali ke batas pita penuh
        if (landscape && h < SHORT_LANDSCAPE_H) {
            val relaxed = layout(w, h, landscape, k, SHORT_LANDSCAPE_TOP + inset, osdBottom, projectorSize)
            val group = SRect(
                minOf(relaxed.tvFront.left, SRect.around(relaxed.tv.corners().map(relaxed.camera::project)).left),
                0f, relaxed.cassetteBounds.right, 0f,
            )
            if (group.left >= OSD_CORNER_W && group.right <= w - OSD_CORNER_W) return relaxed
        }
        return layout(w, h, landscape, k, OSD_TOP + inset, osdBottom, projectorSize)
    }

    private fun layout(w: Float, h: Float, landscape: Boolean, k: Float, top: Float, osdBottom: Float, projectorSize: Float): Frame {
        val tvX = if (landscape) 0f else -9f

        val tv = Solid.axisAligned(tvX - TV_W / 2f, tvX + TV_W / 2f, STAND_H, STAND_H + TV_H, TV_FRONT_Z, TV_FRONT_Z + TV_D)
        val stand = Solid.axisAligned(-STAND_HALF_W, STAND_HALF_W, 0f, STAND_H, STAND_FRONT_Z, STAND_BACK_Z)
        val cassette = Solid(
            CASSETTE_W, CASSETTE_H, CASSETTE_D,
            origin = V3(tvX + TV_W / 2f + CASSETTE_GAP, STAND_H, TV_FRONT_Z + 1.5f),
            yaw = (CASSETTE_YAW_DEG * PI / 180.0).toFloat(),
            tilt = (CASSETTE_TILT_DEG * PI / 180.0).toFloat(),
        )

        // Deck: ukuran dunia rancangan; dibesarkan bila di layar lebih kecil dari batas minimum (lihat di bawah)
        var deckW = DECK_W
        var deckH = DECK_H
        val deckTop = STAND_H - SLAB_T - SHELF_TOP - 1f

        var cam = frameCamera(w, h, landscape, k, tv, cassette, deckW, deckH, top, osdBottom)
        fun fitDeck() {
            val s = cam.scaleAt(STAND_FRONT_Z)
            val maxW = (w - 2 * SIDE) / s
            deckW = deckW.coerceAtLeast(DECK_MIN_W / s).coerceAtMost(maxOf(maxW, 1f))
            deckH = deckH.coerceAtLeast(DECK_MIN_H / s).coerceAtMost(deckTop - SHELF_LIP - PLINTH_H - 2f)
        }
        repeat(2) {
            fitDeck()
            cam = frameCamera(w, h, landscape, k, tv, cassette, deckW, deckH, top, osdBottom)
        }
        // Bingkai akhir sedikit berbeda dari putaran sebelumnya: pastikan deck tetap memenuhi batas minimum di layar
        fitDeck()

        // Deck di tengah konsol (bukan di bawah TV yang pada layar tegak digeser ke kiri demi kaset)
        val deck = cam.rectAt(-deckW / 2f, deckW / 2f, deckTop - deckH, deckTop, STAND_FRONT_Z)
        val shelfX0 = -deckW / 2f - SHELF_SIDE
        val shelfX1 = deckW / 2f + SHELF_SIDE
        val shelfY0 = deckTop - deckH - SHELF_LIP
        val shelf = cam.rectAt(shelfX0, shelfX1, shelfY0, deckTop + SHELF_TOP, STAND_FRONT_Z)
        val slabEdge = cam.rectAt(-STAND_HALF_W, STAND_HALF_W, STAND_H - SLAB_T, STAND_H, STAND_FRONT_Z)
        val plinth = cam.rectAt(-STAND_HALF_W + 3f, STAND_HALF_W - 3f, 0f, PLINTH_H, STAND_FRONT_Z + 2.5f)
        // Kisi speaker kain di kiri & kanan rak (konsol stereo); dilewati bila terlalu sempit
        val gy0 = PLINTH_H + 5f
        val gy1 = STAND_H - SLAB_T - 5f
        val grilleLeft = (-STAND_HALF_W + 6f to shelfX0 - 6f).takeIf { it.second - it.first >= 14f }
            ?.let { cam.rectAt(it.first, it.second, gy0, gy1, STAND_FRONT_Z) }
        val grilleRight = (shelfX1 + 6f to STAND_HALF_W - 6f).takeIf { it.second - it.first >= 14f }
            ?.let { cam.rectAt(it.first, it.second, gy0, gy1, STAND_FRONT_Z) }
        val tvFront = cam.rectAt(tv.origin.x - TV_W / 2f, tv.origin.x + TV_W / 2f, STAND_H, STAND_H + TV_H, TV_FRONT_Z)
        val standFront = cam.rectAt(-STAND_HALF_W, STAND_HALF_W, 0f, STAND_H, STAND_FRONT_Z)
        val casPts = cassette.corners().map(cam::project)
        val casRaw = SRect.around(casPts)
        // Area sentuh kaset minimal 48 × 48 dp walau kaset tergambar kecil
        val cassetteBounds = growTo(casRaw, 48f, 48f, leftLimit = tvFront.right, rightLimit = w)

        val projection = projectionRect(w, top, tvFront, projectorSize)
        val floorLine = cam.project(V3(0f, 0f, WALL_Z)).y
        val baseboardTop = cam.project(V3(0f, BASEBOARD_H, WALL_Z)).y

        return Frame(
            width = w, height = h, landscape = landscape, camera = cam, tv = tv, stand = stand, cassette = cassette,
            tvFront = tvFront, standFront = standFront, shelf = shelf, deck = deck, slabEdge = slabEdge, plinth = plinth,
            grilleLeft = grilleLeft, grilleRight = grilleRight, cassetteBounds = cassetteBounds,
            projection = projection, floorLine = floorLine, baseboardTop = baseboardTop, osdTop = top, osdBottom = h - osdBottom,
        )
    }

    /**
     * Gambar proyeksi 16:9 di dinding, ±1,8× lebar TV, berpusat di belakang TV. Tepi atasnya selalu di dalam layar
     * (gambar proyeksi harus terbaca sebagai bingkai cahaya, bukan potongan), bagian bawahnya tertutup TV & meja.
     * [k] = pengaturan ukuran (0,6..1,2), diukur dari pusat bingkai; lebarnya tidak melebihi layar.
     */
    private fun projectionRect(w: Float, osdTop: Float, tv: SRect, k: Float): SRect {
        val kk = if (k.isNaN()) 1f else k.coerceIn(0.6f, 1.2f)
        val pw = (tv.width * PROJECTION_TV_RATIO * kk).coerceAtMost(w - 2 * SIDE)
        val ph = pw / PROJECTION_ASPECT
        val minTop = (osdTop * 0.3f).coerceAtLeast(8f)
        val top = (tv.top - ph * 0.45f).coerceAtLeast(minTop)
        val cx = tv.centerX.coerceIn(SIDE + pw / 2f, w - SIDE - pw / 2f)
        return SRect(cx - pw / 2f, top, cx + pw / 2f, top + ph)
    }

    /** Pengaturan ukuran 0,5..1 → pengali 0,7..1 (0,85 dan lebih = ukuran rancangan penuh). */
    fun sizeFactor(size: Float): Float {
        val s = if (size.isNaN()) 0.85f else size
        return (0.7f + 0.3f * (s - 0.5f) / 0.35f).coerceIn(0.7f, 1f)
    }

    /**
     * Pilih panjang fokus & titik utama agar TV (dengan atapnya), kaset, dan deck muat di antara pita OSD dan tepi layar,
     * lalu letakkan kelompok itu di tengah. Proyeksi dinding boleh terpotong tepi atas (ia latar belakang).
     */
    private fun frameCamera(
        w: Float, h: Float, landscape: Boolean, k: Float, tv: Solid, cassette: Solid, deckW: Float, deckH: Float,
        osdTop: Float, osdBottom: Float,
    ): StageCamera {
        val e = EYE
        val deckTop = STAND_H - SLAB_T - SHELF_TOP - 1f
        val bottom = deckTop - deckH - SHELF_LIP - 1f
        val pts = tv.corners() + cassette.corners() + listOf(
            V3(-deckW / 2f - SHELF_SIDE, bottom, STAND_FRONT_Z), V3(deckW / 2f + SHELF_SIDE, bottom, STAND_FRONT_Z),
        )
        val us = pts.map { (it.x - e.x) / (it.z - e.z) }
        val vs = pts.map { -(it.y - e.y) / (it.z - e.z) }
        val uMin = us.min(); val uMax = us.max(); val vMin = vs.min(); val vMax = vs.max()
        val top = osdTop + if (landscape) 0f else h * PORTRAIT_PROJECTION_ROOM
        val availW = w - 2 * SIDE
        val availH = (h - top - osdBottom - 6f).coerceAtLeast(40f)
        val f = minOf(availW / (uMax - uMin), availH / (vMax - vMin)) * k
        val cx = SIDE + (availW - f * (uMax - uMin)) / 2f - f * uMin
        val cy = top + (availH - f * (vMax - vMin)) / 2f - f * vMin
        return StageCamera(e, f, cx, cy)
    }

    /**
     * Besarkan area sentuh ke ATAS saja (kaset berdiri di atas meja; di bawahnya ada deck) dan ke samping tanpa melewati
     * tepi kanan TV ([leftLimit]) maupun tepi layar ([rightLimit]).
     */
    private fun growTo(r: SRect, minW: Float, minH: Float, leftLimit: Float, rightLimit: Float): SRect {
        val gw = ((minW - r.width) / 2f).coerceAtLeast(0f)
        val gh = (minH - r.height).coerceAtLeast(0f)
        var left = r.left - gw
        var right = r.right + gw
        if (left < leftLimit) { right += leftLimit - left; left = leftLimit }
        if (right > rightLimit) { left -= right - rightLimit; right = rightLimit }
        return SRect(left, r.top - gh, right, r.bottom)
    }
}
