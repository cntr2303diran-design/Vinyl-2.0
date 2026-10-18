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
        // Permukaan (gelap saja; satu-satunya mode selain tampilan kaca)
        const val INK = 0xFF09090BL
        const val INK_ELEVATED = 0xFF141416L
        const val INK_CARD = 0xFF1C1C1FL
        const val INK_CARD_HIGH = 0xFF26262AL
        const val INK_CARD_HIGHEST = 0xFF303035L
        const val HAIRLINE = 0x1FFFFFFFL
        const val OUTLINE = 0x33FFFFFFL

        // Teks: tiga tingkat opasitas. Semua >= 4.5:1 pada setiap permukaan di atas dan pada
        // latar ambient (lihat AmbientColor.MAX_LUMINANCE) — dijaga ContrastTest.
        const val TEXT_PRIMARY = 0xF5FFFFFFL
        const val TEXT_SECONDARY = 0xCCFFFFFFL
        const val TEXT_TERTIARY = 0x9EFFFFFFL

        // Aksen hangat ala label piringan
        const val ACCENT = 0xFFFF9F43L
        const val ACCENT_ON = 0xFF1E1206L
        const val PRIMARY_CONTAINER = 0xFF3A2614L
        const val ON_PRIMARY_CONTAINER = 0xFFFFDCC2L
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
        const val PANEL = 0xF2151517L
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
        /** Skala font maksimum yang diuji tata letaknya. */
        const val MAX_FONT_SCALE = 2.0f
    }
}
