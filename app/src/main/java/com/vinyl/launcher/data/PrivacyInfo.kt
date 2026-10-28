package com.vinyl.launcher.data

/**
 * Pernyataan privasi dan alasan tiap izin, ditampilkan di Pengaturan → Cadangan & privasi.
 * Satu-satunya sumber teks ini; PrivacyInfoTest memastikan setiap izin di AndroidManifest.xml
 * punya alasan di sini dan sebaliknya, sehingga dokumentasi tidak bisa tertinggal dari kode.
 */
object PrivacyInfo {
    data class Item(
        /** Nama izin Android (android.permission.X), atau penanda untuk izin khusus tanpa entri manifest. */
        val id: String,
        val name: String,
        val reason: String,
        val optional: Boolean = false,
    )

    const val ID_NOTIFICATION_LISTENER = "special:notification-listener"
    const val ID_ACCESSIBILITY = "special:accessibility"
    const val ID_SHIZUKU = "moe.shizuku.manager.permission.API_V23"

    val summary: List<String> = listOf(
        "Semua pengaturan, riwayat lagu, dan kebiasaan memakai aplikasi disimpan hanya di perangkat ini. " +
            "Riwayat aplikasi yang sering dibuka (untuk baris Sering dipakai) bisa dihapus kapan saja di Pengaturan → App drawer, " +
            "dan tidak ikut dalam berkas cadangan.",
        "Tidak ada akun, iklan, analitik, atau pelacak. Tidak ada data yang dikirim ke server milik pengembang.",
        "Aplikasi hanya terhubung ke internet untuk empat hal: lirik (judul dan artis lagu dikirim ke lrclib.net; " +
            "bisa dimatikan di Pengaturan → Musik → Cari lirik online), " +
            "cuaca (koordinat kota yang Anda isi dikirim ke open-meteo.com), cover album bila aplikasi musik memberi tautan https, " +
            "dan situs film yang Anda pilih sendiri di laci kaset (lihat di bawah).",
        "Efek visual (distorsi kaca, cahaya, partikel) digambar di perangkat dan tidak mengirim apa pun. Parallax memakai sensor gerak " +
            "(tanpa izin), hanya selama layar utama terlihat, dan datanya tidak disimpan. Visualizer \"Audio asli\" hanya membaca spektrum " +
            "frekuensi keluaran audio perangkat — bukan rekaman suara dan bukan mikrofon — dan izin mikrofon yang disyaratkan Android baru " +
            "diminta saat Anda memilih mode itu.",
        "Video di layar TV (opsional) hanya membaca folder yang Anda pilih sendiri lewat pemilih folder Android, tanpa izin " +
            "penyimpanan. Video diputar di perangkat tanpa suara; tidak ada yang diunggah. Folder dan tautannya tidak ikut berkas cadangan.",
        "Laci kaset (opsional): kaset film membuka situs https pilihan Anda di layar TV, di dalam jendela web aplikasi ini. " +
            "Kaset bawaan hanya menunjuk sumber film legal (domain publik, film terbuka, Dewan Film Nasional Kanada); alamat lain " +
            "Anda tambahkan sendiri, dan isinya sepenuhnya milik situs itu. Halaman tidak punya jembatan ke aplikasi, tidak bisa " +
            "meminta lokasi, kamera, mikrofon, berkas, atau unduhan, dan hanya https yang dimuat. Cookie dan data situs film ada di " +
            "perangkat ini saja dan bisa dihapus di Pengaturan → Laci kaset. Agar film bisa dilanjutkan, aplikasi mengingat halaman " +
            "terakhir tiap kaset film (alamatnya, yang selalu di situs kaset itu sendiri), menit terakhir, dan waktu pencatatan; " +
            "ini hanya di perangkat ini, tidak ikut berkas cadangan, dan bisa dimatikan atau dilupakan di Pengaturan → Laci kaset. " +
            "Alamat kaset ikut berkas cadangan. Game Ular dan Bata di laci tidak memakai internet; rekor masing-masing (satu angka) " +
            "tersimpan lokal dan bisa disetel ulang di Pengaturan → Laci kaset.",
        "Ingat tombol daya TV (opsional, mati secara bawaan): bila Anda menyalakannya, aplikasi menyimpan satu nilai ya/tidak, yaitu " +
            "apakah TV terakhir dimatikan dengan tombol dayanya, di penyimpanan internal perangkat ini. Nilai itu tidak ikut berkas " +
            "cadangan atau preset, tidak dikirim ke mana pun, dan dihapus bersama data aplikasi.",
        "Pemutar musik bawaan (opsional) juga hanya membaca folder musik yang Anda pilih sendiri, tanpa izin penyimpanan. Daftar lagu " +
            "(judul, artis, album, format) disimpan di memori internal aplikasi agar pemindaian berikutnya cepat; berkas musik tidak " +
            "disalin, tidak diubah, dan tidak diunggah. Folder musik tidak ikut berkas cadangan.",
        "Sleep timer dan pergantian lembut hanya mengatur volume media dan menjeda sesi musik di perangkat; keduanya tidak butuh izin tambahan.",
        "Cadangan otomatis ke akun Google dimatikan. Gunakan Simpan cadangan di atas bila ingin memindahkan pengaturan.",
    )

    val permissions: List<Item> = listOf(
        Item("android.permission.INTERNET", "Internet", "Lirik, cuaca, cover album, dan situs film yang Anda pilih di laci kaset, seperti dijelaskan di atas. Hanya HTTPS."),
        Item("android.permission.SYSTEM_ALERT_WINDOW", "Tampil di atas aplikasi lain", "Menampilkan layar kunci TV, taskbar, dan tombol navigasi."),
        Item("android.permission.FOREGROUND_SERVICE", "Layanan latar depan", "Menjaga layar kunci, taskbar, dan tombol navigasi tetap aktif."),
        Item("android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "Layanan latar (khusus)", "Jenis layanan untuk tampilan di atas aplikasi lain; tidak ada kategori sistem lain yang cocok."),
        Item("android.permission.POST_NOTIFICATIONS", "Menampilkan notifikasi", "Notifikasi wajib dari layanan latar depan di atas."),
        Item("android.permission.RECEIVE_BOOT_COMPLETED", "Mulai setelah restart", "Menyalakan lagi layar kunci TV dan taskbar setelah ponsel menyala atau aplikasi diperbarui."),
        Item("android.permission.REQUEST_DELETE_PACKAGES", "Meminta penghapusan aplikasi", "Tombol Copot di app drawer; sistem tetap meminta konfirmasi Anda."),
        Item("android.permission.EXPAND_STATUS_BAR", "Membuka panel notifikasi", "Gestur geser untuk membuka panel notifikasi."),
        Item("android.permission.VIBRATE", "Getar", "Getar halus saat tombol kaset ditekan dan knob diputar. Bisa dimatikan di Suara & getar."),
        Item("android.permission.READ_CALENDAR", "Kalender", "Menampilkan titik acara hari ini di layar kunci. Dibaca di perangkat, tidak dikirim ke mana pun.", optional = true),
        Item("android.permission.PACKAGE_USAGE_STATS", "Akses penggunaan aplikasi", "Mengetahui aplikasi yang terakhir dibuka untuk taskbar. Diproses di perangkat saja.", optional = true),
        Item(
            ID_NOTIFICATION_LISTENER, "Akses notifikasi",
            "Membaca sesi media (judul, cover, kontrol putar) dari aplikasi musik apa pun. Bila Lencana notifikasi di app drawer menyala, " +
                "juga menghitung jumlah notifikasi per aplikasi (hanya nama aplikasi dan jumlahnya, di memori). Judul dan isi notifikasi tidak pernah dibaca atau disimpan.",
        ),
        Item(
            "android.permission.READ_CONTACTS", "Kontak",
            "Mencari nama kontak di kolom cari app drawer. Opsional dan mati secara default. Pencarian terjadi di perangkat; hasilnya hanya ditampilkan, tidak disimpan atau dikirim.",
            optional = true,
        ),
        Item(
            "android.permission.RECORD_AUDIO", "Mikrofon (visualizer)",
            "Hanya dipakai bila Anda memilih Gambar & efek → Visualizer → Audio asli. Android mewajibkan izin ini untuk membaca spektrum " +
                "frekuensi keluaran audio perangkat. TV Launcher tidak merekam suara, tidak menyimpan apa pun, dan tidak mengirim apa pun; " +
                "izin tidak diminta sebelum Anda memilih mode itu, dan tanpa izin visualizer tidak jalan (tidak diganti tiruan).",
            optional = true,
        ),
        Item(
            "android.permission.MODIFY_AUDIO_SETTINGS", "Pengaturan audio (visualizer, equalizer, DAC USB)",
            "Syarat teknis Android untuk membuka Visualizer pada keluaran audio sistem, untuk memasang equalizer Audio Console " +
                "ke sesi audio yang dibuka aplikasi musik, dan (Android 14+, opsional) untuk meminta mixer DAC USB berjalan di laju " +
                "sampel lagu pemutar bawaan. Izin biasa yang diberikan saat pemasangan. Equalizer dan pencocokan laju sampel mati " +
                "secara default; launcher tidak mengubah volume atau setelan suara sistem Anda.",
        ),
        Item(
            "android.permission.DUMP", "Laporan diagnostik sistem (Akses lanjutan)",
            "Opsional dan tidak pernah diberikan otomatis: hanya aktif bila Anda sendiri memberikannya lewat ADB atau Shizuku. " +
                "Dipakai Audio Console untuk membaca codec Bluetooth aktif, format & sample rate keluaran, resampling, dan sesi audio " +
                "aplikasi musik (untuk equalizer). Laporan dibaca di memori saat console terbuka atau EQ aktif; hanya nilai yang " +
                "ditampilkan yang diambil. Isi laporan tidak disimpan, tidak dicatat ke log, dan tidak dikirim. Cabut kapan saja: " +
                "adb shell pm revoke com.vinyl.launcher android.permission.DUMP.",
            optional = true,
        ),
        Item(
            "android.permission.BLUETOOTH_CONNECT", "Perangkat sekitar (Bluetooth)",
            "Opsional, diminta saat Anda menekan \"Izinkan baca baterai Bluetooth\" di Audio Console. Hanya untuk menerima laporan " +
                "level baterai earphone/speaker Bluetooth dari sistem (jalur tidak resmi), yang tampil di Audio Console dan di samping " +
                "ikon Bluetooth OSD beranda. Tidak memindai, tidak menyambung, dan tidak " +
                "mengubah perangkat. Level terakhir tiap perangkat diingat di penyimpanan internal (dengan sidik alamat, bukan alamat " +
                "aslinya) agar bisa ditampilkan sebagai perkiraan setelah launcher dimulai ulang; tidak pernah dikirim.",
            optional = true,
        ),
        Item(
            "android.permission.BLUETOOTH", "Bluetooth (Android 10–11)",
            "Padanan lama izin di atas untuk Android 10 dan 11 (diberikan saat pemasangan). Dipakai untuk hal yang sama, dan untuk " +
                "membaca apakah Bluetooth menyala (ikon di OSD beranda).",
            optional = true,
        ),
        Item(
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "Layanan latar (pemutaran media)",
            "Agar lagu dari pemutar bawaan tetap berjalan saat layar mati atau Anda membuka aplikasi lain, dengan notifikasi " +
                "pemutaran seperti aplikasi musik biasa. Hanya aktif selama pemutar bawaan memutar lagu.",
        ),
        Item(
            "android.permission.WAKE_LOCK", "Mencegah ponsel tidur",
            "Menjaga prosesor tetap bangun selama pemutar bawaan memutar lagu, supaya musik tidak terputus saat layar mati. " +
                "Dilepas begitu lagu dijeda atau berhenti.",
        ),
        Item(
            "android.permission.ACCESS_NETWORK_STATE", "Melihat status jaringan",
            "OSD beranda (pengganti status bar): apakah Wi-Fi tersambung dan kekuatan sinyalnya, serta apakah internet lewat data " +
                "seluler. Nama jaringan tidak dibaca. Izin biasa tanpa dialog.",
        ),
        Item(
            ID_SHIZUKU, "Shizuku (opsional)",
            "Izin API Shizuku (moe.shizuku.manager.permission.API_V23) ditambahkan library resmi Shizuku. Hanya dipakai bila Shizuku " +
                "terpasang dan Anda menekan \"Berikan izin lewat Shizuku\": launcher menjalankan satu perintah tetap " +
                "(pm grant com.vinyl.launcher android.permission.DUMP) lalu berhenti. Tidak ada perintah lain.",
            optional = true,
        ),
        Item(ID_ACCESSIBILITY, "Layanan aksesibilitas", "Hanya untuk aksi global: kunci layar, buka notifikasi, dan buka pengaturan cepat (dari gestur dan OSD beranda). Tidak membaca isi layar.", optional = true),
    )
}
