package com.vinyl.launcher.ui.theme

/*
 * Design token Vinyl — SATU file untuk warna, spacing, radius, gerak, dan batas aksesibilitas.
 * Murni Kotlin (tanpa Compose) supaya nilainya bisa diuji unit; Theme.kt yang menerjemahkannya ke Compose.
 *
 * Bahasa desain (dipadukan, bukan menjiplak satu produk):
 *  - Material 3 / Pixel Launcher : bentuk bulat besar, tipografi jelas, skema warna ber-peran (surface/onSurface)
 *  - Apple HIG                   : hierarki visual kuat lewat 3 tingkat opasitas teks, ruang lega, gerak pegas halus
 *  - Samsung One UI              : kartu besar membulat, judul besar, kontrol di zona jempol, kontras nyaman
 *  - Microsoft Fluent            : material kaca tembus pandang yang subtil, kedalaman lewat lapisan (layering)
 */
object Tokens {

    /** Warna sebagai ARGB 32-bit (0xAARRGGBB) dalam Long. */
    object Argb {
        // Permukaan gelap bernada hangat (charcoal tema TV tabung, bukan hitam-biru). Tingkatan sama seperti sebelumnya.
        const val INK = 0xFF121210L
        const val INK_ELEVATED = 0xFF1A1A18L
        const val INK_CARD = 0xFF242522L
        const val INK_CARD_HIGH = 0xFF2D2F2BL
        const val INK_CARD_HIGHEST = 0xFF373935L
        const val HAIRLINE = 0x1FFFFFFFL
        const val OUTLINE = 0x33FFFFFFL

        // Teks: tiga tingkat opasitas. Semua >= 4.5:1 pada setiap permukaan di atas dan pada
        // latar ambient (lihat AmbientColor.MAX_LUMINANCE) — dijaga ContrastTest.
        const val TEXT_PRIMARY = 0xF5FFFFFFL
        const val TEXT_SECONDARY = 0xCCFFFFFFL
        const val TEXT_TERTIARY = 0x9EFFFFFFL

        // Aksen amber lampu indikator TV (sama dengan Retro.AMBER), menggantikan oranye label piringan
        const val ACCENT = 0xFFD6A64DL
        const val ACCENT_ON = 0xFF1E1D1BL
        const val PRIMARY_CONTAINER = 0xFF3A2F18L
        const val ON_PRIMARY_CONTAINER = 0xFFF2DDB0L
        const val SECONDARY = 0xFFD9C9B4L
        const val ERROR_CONTAINER = 0xFF3B1716L
        const val ON_ERROR_CONTAINER = 0xFFFFB4ABL
        const val SCRIM = 0x47000000L

        /**
         * Pasangan ekstrem untuk teks di atas latar sembarang warna (contentColorFor). Hitam/putih murni dipilih
         * karena hanya pasangan ini yang menjamin >= 4.5:1 di semua latar (terburuk ~4.58:1 di abu menengah).
         */
        const val TEXT_ON_LIGHT = 0xFF000000L
        const val TEXT_ON_DARK = 0xFFFFFFFFL

        /** Permukaan semua-aplikasi (panel taskbar, app drawer) — hampir buram agar teks tetap terbaca di atas aplikasi lain. */
        const val PANEL = 0xF2181816L
    }

    /**
     * Palet tema "Retro Projection" (TV tabung di ruang putih). Ruang utama TERANG (teks gelap di atas putih hangat);
     * Audio Console dan panel gelap memakai charcoal dengan teks krem. Cover album dan proyeksi adalah satu-satunya
     * sumber warna jenuh; warna di sini sengaja kalem. Pasangan teks/latar dijaga >= 4,5:1 oleh RetroPaletteTest.
     */
    object Retro {
        // Enam warna inti (dari spesifikasi konsep)
        const val WARM_WHITE = 0xFFF7F6F2L      // dinding & lantai White Void
        const val CREAM_BEIGE = 0xFFC5AD87L     // bodi TV, label kaset
        const val WALNUT = 0xFF684832L          // panel kayu TV & rak
        const val CHARCOAL = 0xFF343633L        // tombol, detail, latar Audio Console
        const val AMBER = 0xFFD6A64DL           // lampu indikator, VFD amber
        const val MUTED_GREEN = 0xFF82977AL     // aksen VFD hijau

        // Turunan ruang: gradasi putih hangat untuk memperlihatkan bentuk ruangan tanpa garis tegas
        const val VOID_WALL_TOP = 0xFFFBFAF7L
        const val VOID_FLOOR = 0xFFEFEDE7L
        const val VOID_CORNER = 0xFFE6E3DBL     // sudut dinding–lantai, samar
        const val CONTACT_SHADOW = 0x40332A20L  // bayangan kontak di lantai (hangat, tidak hitam pekat)

        // Teks di atas ruang terang (dinding/lantai/kartu putih)
        const val INK = 0xFF1E1D1BL
        const val INK_SECONDARY = 0xFF55524CL
        const val INK_TERTIARY = 0xFF67635CL   // 4,66:1 di sudut ruangan (tergelap)

        // Panel gelap (Audio Console, drawer, layar kunci)
        const val CHARCOAL_DEEP = 0xFF252724L
        const val CHARCOAL_RAISED = 0xFF3E403CL
        const val CREAM_TEXT = 0xFFEDE3D1L
        const val CREAM_TEXT_SECONDARY = 0xFFC9BEAAL

        // VFD: kaca gelap kebiruan-hijau, karakter amber atau hijau bercahaya
        const val VFD_GLASS = 0xFF141613L
        const val VFD_AMBER = AMBER
        const val VFD_GREEN = 0xFF9FC29AL
        /** Segmen VFD yang "mati" (tetap samar terlihat, seperti layar VFD sungguhan). Dekoratif saja. */
        const val VFD_GHOST = 0x1FFFFFFFL

        // Layar CRT: kaca gelap saat standby, lampu standby merah redup
        const val CRT_GLASS = 0xFF101211L
        const val STANDBY_LED = 0xFFB4442EL
    }

    /** Jarak dalam dp. Kelipatan 4 supaya ritme tata letak konsisten. */
    object Space {
        const val XS = 4
        const val S = 8
        const val M = 12
        const val L = 16
        const val XL = 24
        const val XXL = 32
    }

    /** Radius sudut dalam dp. */
    object Radius {
        const val S = 12
        const val M = 16
        const val L = 26
        const val XL = 32
        const val PILL = 50
    }

    /** Gerak: durasi (ms), pegas, dan kurva easing (titik kontrol cubic-bezier ala Material 3). */
    object Motion {
        const val FAST_MS = 150
        const val MEDIUM_MS = 300
        const val SLOW_MS = 500
        /** Transisi warna sinematik (latar mengikuti cover). */
        const val CINEMATIC_MS = 1400

        /** Pegas halus: sedikit memantul, tidak "kenyal". */
        const val SPRING_DAMPING = 0.78f
        const val SPRING_STIFFNESS = 380f

        // --- Tema TV tabung. Semua menghormati "Hapus animasi" (durasi 0) dan mode hemat daya.
        /** CRT menyala: garis horizontal mengembang menjadi gambar. */
        const val CRT_POWER_ON_MS = 420
        /** CRT mati (sleep timer selesai): gambar menyusut menjadi titik. */
        const val CRT_POWER_OFF_MS = 360
        const val CASSETTE_EJECT_MS = 280
        const val CASSETTE_INSERT_MS = 340
        /** Batas bawah satu langkah animasi kaset saat skip beruntun (animasi digabung & dipercepat). */
        const val CASSETTE_RAPID_MS = 120
        const val KNOB_ZOOM_IN_MS = 320
        const val KNOB_ZOOM_OUT_MS = 260
        const val PROJECTION_CROSSFADE_MS = 900
        const val PAGE_SLIDE_MS = 380
        /** Lama satu informasi tampil di VFD sebelum bergantian. */
        const val VFD_CYCLE_MS = 4000
        /** Lama nama output audio baru diprioritaskan di VFD. */
        const val VFD_PRIORITY_MS = 3000
        /** Putaran gulungan kaset per menit pada kecepatan normal / cepat. */
        const val REEL_RPM_NORMAL = 40f
        const val REEL_RPM_FAST = 60f

        /** Easing standar M3 (0.2, 0, 0, 1). */
        val EASE_STANDARD = floatArrayOf(0.2f, 0f, 0f, 1f)
        /** Masuk: melambat di akhir (0.05, 0.7, 0.1, 1). */
        val EASE_DECELERATE = floatArrayOf(0.05f, 0.7f, 0.1f, 1f)
        /** Keluar: mempercepat (0.3, 0, 0.8, 0.15). */
        val EASE_ACCELERATE = floatArrayOf(0.3f, 0f, 0.8f, 0.15f)
    }

    /** Batas aksesibilitas yang dipakai tes dan UI. */
    object A11y {
        const val MIN_TOUCH_DP = 48
        const val MIN_CONTRAST = 4.5
        /** Isi putih terbanyak yang boleh dipakai permukaan kaca di atas latar cover; `Modifier.glass` membatasinya. */
        const val MAX_GLASS_FILL = 0.12f
        /**
         * Lapisan putih total yang diasumsikan berada di bawah teks pada permukaan kaca: isi ([MAX_GLASS_FILL]) ditambah
         * kilau di bagian atas. Peredam latar ([BackdropDim]) menghitung kontras DENGAN lapisan ini.
         */
        const val GLASS_VEIL = 0.16
        /**
         * Putih terbanyak yang boleh ditambahkan SATU lapisan efek (partikel, cahaya reaktif). Lapisan efek digambar di
         * atas peredam latar dan di bawah konten, jadi peredam menghitungnya ([FX_VEIL]) dan kontras teks tetap terjamin.
         */
        const val FX_PEAK = 0.06f
        /** Putih total dua lapisan efek yang menumpuk: 1 - (1 - FX_PEAK)^2 = 0,1164, dibulatkan ke atas. */
        const val FX_VEIL = 0.12
        /**
         * Pencerahan maksimum distorsi kaca. Berbeda dari lapisan efek di atas, distorsi bukan lapisan putih: shader
         * MENGALIKAN warna latar paling banyak (1 + DIST_PEAK) kali (dipotong ke 255). Perkalian ini terjadi SEBELUM
         * peredam menggelapkan latar, jadi [com.vinyl.launcher.ui.theme.BackdropDim.withDist] menaikkan warna latar
         * dengan faktor yang sama sebelum menghitung alpha peredam; tidak dihitung sebagai putih tambahan.
         */
        const val DIST_PEAK = 0.03f
        /**
         * Penggelapan terbanyak (lapisan hitam, alpha) yang boleh dijatuhkan efek ruang terang di bawah teks: vignette
         * CRT, bayangan kontak, sudut ruangan. Pada tema terang teks GELAP, jadi efek yang menggelapkan latar-lah yang
         * mengancam kontras; [com.vinyl.launcher.ui.theme.BackdropLift] menghitungnya.
         */
        const val LIGHT_SHADE_PEAK = 0.10
        /** Skala font maksimum yang diuji tata letaknya. */
        const val MAX_FONT_SCALE = 2.0f
    }
}
