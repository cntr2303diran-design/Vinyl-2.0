package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.data.RoomEra

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

    /**
     * Beranda bergaya rekaman VHS ("found footage" malam hari): ruangan gelap yang hanya diterangi layar TV dan
     * proyeksi, meja kayu walnut, deck kaset hitam dengan tombol perak, dan teks layar kamera (OSD) putih bertepi hitam.
     * Pasangan teks/latar dijaga >= 4,5:1 oleh VhsPaletteTest.
     */
    object Vhs {
        // Ruangan malam
        const val WALL_LIT = 0xFF1B2029L        // dinding tepat di belakang TV (diterangi proyeksi)
        const val WALL_DARK = 0xFF07080BL       // tepi ruangan
        const val FLOOR = 0xFF050506L
        // Meja kayu
        const val WOOD_LIGHT = 0xFF6A4630L
        const val WOOD_MID = 0xFF46301FL
        const val WOOD_DARK = 0xFF231710L
        // Deck kaset
        const val DECK_FACE = 0xFF19191BL
        const val DECK_FACE_LIGHT = 0xFF2C2D30L
        const val KEY_FACE = 0xFFC9C6BDL        // tombol perak
        const val KEY_FACE_DOWN = 0xFFA9A69DL
        const val KEY_INK = 0xFF1A1918L         // simbol di tombol perak
        // Teks layar kamera (OSD)
        const val OSD_TEXT = 0xFFF4F3EEL
        const val OSD_SHADOW = 0xFF000000L
        /** Isian merah untuk baterai lemah; teks/garis OSD putih di atasnya (bukan teks merah di atas latar). */
        const val OSD_WARN_FILL = 0xFFB3261EL
        const val OSD_ACCENT = 0xFFFFD45CL      // "HI-FI", timer tidur
        /**
         * Peredam hitam di belakang pita OSD (atas & bawah). Dengan alpha ini teks OSD putih tetap >= 4,5:1 bahkan di atas
         * proyeksi putih penuh (dibuktikan VhsPaletteTest).
         */
        const val OSD_SCRIM_ALPHA = 0.66
        /**
         * Peredam sudut OSD dibuat dari banyak lapisan tipis yang makin kecil (tepi memudar halus, tanpa cincin terlihat);
         * di inti, di bawah teks, semua lapisan menumpuk: 1 − (1 − 0,066)^16 ≈ 0,665 ≥ [OSD_SCRIM_ALPHA]
         * (dibuktikan VhsPaletteTest).
         */
        const val SCRIM_LAYERS = 16
        const val SCRIM_LAYER_ALPHA = 0.066
        // Label kaset kertas
        const val LABEL_PAPER = 0xFFE9E0CCL
        const val LABEL_INK = 0xFF1C1A2EL       // spidol biru-hitam
    }

    /**
     * Laci kaset film (Tahap 13): kain baki dan lapisan di atas halaman web di layar TV. Halaman web bisa berwarna apa
     * saja (termasuk putih penuh), jadi tulisan di atasnya bertumpu pada peredam hitam ini. Pasangan teks/latar dijaga
     * >= 4,5:1 oleh FilmPaletteTest.
     */
    object Film {
        const val FELT_TOP = 0xFF2B1719L
        const val FELT_BOTTOM = 0xFF190D0FL
        /** Peredam hitam di belakang tulisan "MEMUAT" dan pesan OSD (LANJUT, TIDAK ADA VIDEO) yang melayang di atas halaman. */
        const val LOADING_SCRIM_ALPHA = 0.78
        /** Selimut "sinyal hilang" yang menutup halaman. */
        const val LOST_SCRIM_ALPHA = 0.94
        /** Alpha putih untuk keterangan galat di atas selimut itu. */
        const val LOST_MESSAGE_ALPHA = 0.82
    }

    /**
     * Game di layar TV (Ular, Bata): papan hijau-hitam seperti tabung fosfor yang sama untuk keduanya. Angka skor dan
     * rekor ditulis di pita sendiri di atas papan; tulisan di tengah papan (SIAP / JEDA / GAME OVER) berdiri di atas panel
     * gelap, jadi kontrasnya tidak bergantung pada posisi ular atau bata di belakangnya. Pasangan teks/latar dan kontras
     * bentuk (ular, makanan, bata, palet, bola) dijaga oleh GamePaletteTest.
     */
    object Game {
        const val BOARD_TOP = 0xFF07140BL
        const val BOARD_BOTTOM = 0xFF030A06L
        /** Titik petak samar di papan (hiasan). */
        const val GRID_DOT = 0xFF1B3B26L
        const val SNAKE_HEAD = 0xFFC9F7BFL
        const val SNAKE_TAIL = 0xFF2F9E58L
        /** Warna ular setelah menabrak tubuhnya sendiri. */
        const val SNAKE_DEAD = 0xFFE5533DL
        /** Panel di tengah papan: hitam dengan alpha ini. */
        const val PANEL_ALPHA = 0.86

        /** Game Bata: warna bata per baris, atas ke bawah (baris yang lebih mahal di atas); tujuh baris paling banyak. */
        val BRICK_ROWS: List<Long> = listOf(
            0xFFE5533DL, 0xFFF08A3CL, 0xFFF2C14EL, 0xFFB8D95AL, 0xFF5CC98AL, 0xFF4FB5C9L, 0xFF7D8CF0L,
        )
        /** Palet Bata (krem seperti tombol deck) dan bolanya. */
        const val PADDLE = 0xFFEDE6D3L
        const val BALL = 0xFFFFFFFFL
    }

    /**
     * Lampu dinding di ruang TV (ketuk = ruang terang/gelap). Kap dan bohlamnya bertepi ganda: isi krem terang di dalam garis
     * tepi gelap, jadi bentuknya terbaca >= 3:1 di atas dinding gelap (oleh isinya) maupun terang (oleh garis tepinya);
     * dibuktikan LampPaletteTest. Menyala/mati juga dibedakan oleh bentuk (kerucut cahaya di bawah kap), bukan warna saja.
     */
    object Lamp {
        const val SHADE_OFF = 0xFFC9BFA6L
        const val SHADE_ON = 0xFFFFE9B8L
        /** Garis tepi kap, lengan, dan bohlam. */
        const val OUTLINE = 0xFF1B1410L
        const val BRASS_LIGHT = 0xFFEBD28AL
        const val BRASS_DARK = 0xFF6E5420L
        const val BULB_ON = 0xFFFFF3C4L
        const val BULB_OFF = 0xFF9C9889L
        /** Cahaya hangat yang jatuh di dinding saat menyala. */
        const val GLOW = 0xFFFFC870L
    }

    /**
     * Palet ruangan per era (Tahap A "ruang hidup"). Satu [EraRoom] = warna dinding (terang di tengah, gelap di tepi),
     * lantai, tiga warna kayu/plastik meja, dan dua warna aksen hiasan dinding. Setiap era punya versi gelap (malam) dan
     * terang (siang) supaya lampu dinding tetap berfungsi. Teks tidak pernah diletakkan langsung di atas warna ini
     * (teks OSD selalu di atas peredam hitam, benda di atas pelatnya sendiri); yang dijaga EraPaletteTest: teks OSD tetap
     * >= 4,5:1 di atas dinding era, kap lampu terbaca >= 3:1, dan dinding berbeda dari lantai.
     * [room] bernilai null untuk DEFAULT: tampilan lama (Vhs) dipakai apa adanya.
     */
    object Era {
        // 70-an: cokelat dan oranye hangat, panel kayu gelap
        val SEVENTIES_DIM = EraRoom(0xFF4A2E1AL, 0xFF1F1209L, 0xFF2A1A0EL, 0xFF9A5B2AL, 0xFF6E3F1CL, 0xFF3A200FL, 0xFFD9822BL, 0xFFA4501AL)
        val SEVENTIES_LIGHT = EraRoom(0xFFE8C99AL, 0xFFB98A55L, 0xFF9C6B3EL, 0xFF8A4F22L, 0xFF6A3A18L, 0xFF3A200FL, 0xFFC8651BL, 0xFF8A4A1AL)
        // 80-an: ungu malam dengan neon magenta dan cyan, lak gelap
        val EIGHTIES_DIM = EraRoom(0xFF24104AL, 0xFF07030FL, 0xFF08040FL, 0xFF4B3F8AL, 0xFF332B66L, 0xFF160F2EL, 0xFFFF2BD6L, 0xFF22E5FFL)
        val EIGHTIES_LIGHT = EraRoom(0xFFCFC2F2L, 0xFF8E7DC9L, 0xFF6F5FB0L, 0xFF5A4C99L, 0xFF42376FL, 0xFF271F47L, 0xFFD614B0L, 0xFF0AA9C4L)
        // 90-an: abu-abu pastel, plastik matte dengan aksen mint dan merah muda
        val NINETIES_DIM = EraRoom(0xFF4A4D55L, 0xFF22242AL, 0xFF1D1E22L, 0xFF8E919AL, 0xFF6C6F78L, 0xFF40424AL, 0xFF9EC5C0L, 0xFFE2B6C8L)
        val NINETIES_LIGHT = EraRoom(0xFFDADCE2L, 0xFFA9ACB6L, 0xFF9396A0L, 0xFFA7AAB3L, 0xFF858892L, 0xFF555861L, 0xFF6FA8A0L, 0xFFC98BA6L)

        /** Palet era untuk [era] pada ruang gelap ([dim] true) atau terang; null untuk DEFAULT. */
        fun room(era: RoomEra, dim: Boolean): EraRoom? = when (era) {
            RoomEra.DEFAULT -> null
            RoomEra.SEVENTIES -> if (dim) SEVENTIES_DIM else SEVENTIES_LIGHT
            RoomEra.EIGHTIES -> if (dim) EIGHTIES_DIM else EIGHTIES_LIGHT
            RoomEra.NINETIES -> if (dim) NINETIES_DIM else NINETIES_LIGHT
        }
    }

    /**
     * Benda tambahan di ruangan (jam dinding, radio, telepon putar). Tulisan di benda selalu di atas pelatnya sendiri:
     * angka jam dan angka telepon berupa tinta gelap di atas muka krem; dibuktikan PropPaletteTest.
     */
    object Props {
        const val CLOCK_FACE = 0xFFF1E8D4L
        const val INK = 0xFF1B1410L
        const val BAKELITE = 0xFF231E1CL
        const val BAKELITE_DARK = 0xFF0F0C0BL
        const val SILVER = 0xFFC9C6BDL
        const val SILVER_DARK = 0xFF8C897FL
        /** Pelat angka telepon di balik cakram jari. */
        const val DIAL_PLATE = 0xFFE9E0CCL
        /** Jarum penala radio dan lampu timer tidur menyala. */
        const val NEEDLE = 0xFFB3261EL
        const val LED_ON = 0xFFFFB347L
        const val LED_OFF = 0xFF4A3A2AL
        /** Piala di rak: emas, perak, perunggu. */
        const val CUP_GOLD = 0xFFE0B341L
        const val CUP_SILVER = 0xFFCFD3D8L
        const val CUP_BRONZE = 0xFFC07A3EL
        /** Buku harian kaset di meja: sampul, halaman, dan titik merah saat merekam. */
        const val DIARY_COVER = 0xFF5B3A29L
        const val DIARY_PAGES = 0xFFEFE6D2L
        const val DIARY_REC = 0xFFD93025L
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
        const val PROJECTION_CROSSFADE_MS = 900
        const val PAGE_SLIDE_MS = 380
        /** Lama satu informasi tampil di VFD sebelum bergantian. */
        const val VFD_CYCLE_MS = 4000
        /** Lama nama output audio baru diprioritaskan di VFD. */
        const val VFD_PRIORITY_MS = 3000
        /** Lama tampilan maju/mundur di layar TV setelah knob dilepas. */
        const val SEEK_OSD_HOLD_MS = 900
        /** Butir film & garis tracking diperbarui ~12 kali per detik (seperti video lama; hemat baterai). */
        const val FILM_FRAME_MS = 83
        /** Jeda rata-rata antara dua gangguan tracking VHS. */
        const val TRACKING_EVERY_MS = 9000
        const val TRACKING_GLITCH_MS = 700
        /** Hujan statis singkat di layar TV saat kaset berganti ("ganti channel"). */
        const val STATIC_BURST_MS = 320
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

/** Palet satu ruangan era (lihat [Tokens.Era]); semua warna ARGB buram. */
class EraRoom(
    val wallLit: Long, val wallDark: Long, val floor: Long,
    val woodLight: Long, val woodMid: Long, val woodDark: Long,
    val accent: Long, val accent2: Long,
)
