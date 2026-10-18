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
        "Aplikasi hanya terhubung ke internet untuk tiga hal: lirik (judul dan artis lagu dikirim ke lrclib.net; " +
            "bisa dimatikan di Pengaturan → Musik → Cari lirik online), " +
            "cuaca (koordinat kota yang Anda isi dikirim ke open-meteo.com), dan cover album bila aplikasi musik memberi tautan https.",
        "Efek visual (distorsi kaca, cahaya, partikel) digambar di perangkat dan tidak mengirim apa pun. Parallax memakai sensor gerak " +
            "(tanpa izin), hanya selama layar utama terlihat, dan datanya tidak disimpan. Visualizer \"Audio asli\" hanya membaca spektrum " +
            "frekuensi keluaran audio perangkat — bukan rekaman suara dan bukan mikrofon — dan izin mikrofon yang disyaratkan Android baru " +
            "diminta saat Anda memilih mode itu.",
        "Video di layar TV (opsional) hanya membaca folder yang Anda pilih sendiri lewat pemilih folder Android, tanpa izin " +
            "penyimpanan. Video diputar di perangkat tanpa suara; tidak ada yang diunggah. Folder dan tautannya tidak ikut berkas cadangan.",
        "Sleep timer dan pergantian lembut hanya mengatur volume media dan menjeda sesi musik di perangkat; keduanya tidak butuh izin tambahan.",
        "Cadangan otomatis ke akun Google dimatikan. Gunakan Simpan cadangan di atas bila ingin memindahkan pengaturan.",
    )

    val permissions: List<Item> = listOf(
        Item("android.permission.INTERNET", "Internet", "Lirik, cuaca, dan cover album seperti dijelaskan di atas. Hanya HTTPS."),
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
            "Hanya dipakai bila Anda memilih Efek visual → Visualizer → Audio asli. Android mewajibkan izin ini untuk membaca spektrum " +
                "frekuensi keluaran audio perangkat. Vinyl tidak merekam suara, tidak menyimpan apa pun, dan tidak mengirim apa pun; " +
                "izin tidak diminta sebelum Anda memilih mode itu, dan tanpa izin visualizer tidak jalan (tidak diganti tiruan).",
            optional = true,
        ),
        Item(
            "android.permission.MODIFY_AUDIO_SETTINGS", "Pengaturan audio (visualizer & equalizer)",
            "Syarat teknis Android untuk membuka Visualizer pada keluaran audio sistem dan untuk memasang equalizer Audio Console " +
                "ke sesi audio yang dibuka aplikasi musik. Izin biasa yang diberikan saat pemasangan. Equalizer mati secara default; " +
                "launcher tidak mengubah volume atau setelan suara sistem Anda.",
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
            ID_SHIZUKU, "Shizuku (opsional)",
            "Izin API Shizuku (moe.shizuku.manager.permission.API_V23) ditambahkan library resmi Shizuku. Hanya dipakai bila Shizuku " +
                "terpasang dan Anda menekan \"Berikan izin lewat Shizuku\": launcher menjalankan satu perintah tetap " +
                "(pm grant com.vinyl.launcher android.permission.DUMP) lalu berhenti. Tidak ada perintah lain.",
            optional = true,
        ),
        Item(ID_ACCESSIBILITY, "Layanan aksesibilitas", "Hanya untuk aksi global kunci layar dan buka notifikasi dari gestur. Tidak membaca isi layar.", optional = true),
    )
}
