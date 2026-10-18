# TV Launcher 1.0

*(sebelumnya Vinyl Launcher; package id tetap `com.vinyl.launcher` agar pembaruan bisa menimpa versi lama)*

Launcher Android untuk tablet dengan tema **TV tabung analog** (sebelumnya "tablet sebagai turntable") — Kotlin + Jetpack Compose, minSdk 29, targetSdk 36. Dioptimalkan untuk landscape; portrait tetap didukung dengan tata letak yang menyesuaikan. Bahasa desain & token: [DESIGN.md](DESIGN.md).

## Fitur

| Bagian | Isi |
|---|---|
| Ruang TV (layar utama) | Ruang gelap (atau terang) dengan TV tabung: cover, judul & artis di layar CRT (scanline, tepi gelap, kilau kaca), VFD tiga baris (status, sample rate sistem, keluaran audio). Kaset tergeletak di depan TV dengan gulungan yang berputar saat musik berjalan; tombol sebelumnya · mundur 10 dtk · putar/jeda · berikutnya; garis progres yang bisa digeser; volume media; **TIME** dan **Output** di sudut atas |
| Knob seek | Ketuk knob kanan TV: kamera mendekat, putar knob untuk maju/mundur dengan gelembung waktu, klik & getar tiap gigi |
| Ketuk TV | Kamera mendekat ke layar; lirik tampil di layar CRT bila ada, tombol **LIRIK ›** membuka lirik layar penuh |
| Video di layar TV | Opsional: video milik Anda dari folder pilihan (`Artis - Album.mp4` dicocokkan otomatis, atau ditautkan manual) diputar tanpa suara & berulang di layar TV |
| Perpustakaan kaset | Geser dari tepi (halaman kiri): tab **Rak** (kaset per abjad, favorit), **Deck** (antrean aplikasi musik), **Log** (riwayat per hari), **Koleksi** (statistik) |
| Audio Console | Halaman kanan bergaya receiver stereo: perangkat keluaran, **EQ 3 pita** + preamp + balance + preset + profil per perangkat, kualitas (sample rate, kemampuan, bit-perfect USB), spasial & efek, diagnostik (tes kanal kiri/kanan), otomasi. Setiap nilai berlabel sumbernya: *Terukur / Dilaporkan sistem / Perkiraan / Tidak tersedia* |
| Akses lanjutan | Opsional: izin `DUMP` yang Anda berikan lewat ADB (atau Shizuku) membuka codec Bluetooth aktif, format keluaran, resampling, dan EQ untuk aplikasi musik yang tidak membuka sesi efek |
| Lirik layar penuh | Baris aktif besar dan terang, baris jauh mengabur; gulir otomatis; ketuk baris untuk melompat; status memuat / dimatikan / tidak ditemukan / gagal (*Coba lagi*) / instrumental / tanpa waktu |
| Timer tidur | Di perpustakaan kaset dan lirik layar penuh: 5–90 menit atau sampai lagu selesai; volume dipudarkan, musik dijeda, **volume dikembalikan** |
| Pergantian lembut | Opsional: volume turun sebentar lalu naik saat ganti lagu. **Bukan crossfade** — launcher tidak memutar audio |
| Suara & getar | Bunyi tombol kaset & motor, desing pita saat ganti lagu, klik knob, desis pita opsional; volume & kekuatan getar bisa diatur |
| Layar kunci TV | Tampil di atas keyguard: TV dengan cover & VFD (jam, artis), jam besar, tanggal, kalender, kartu lagu + lirik; geser untuk membuka, tekan lama untuk editor |
| App drawer (Panduan Channel) | Tiap aplikasi punya **nomor channel** tetap (urutan abjad); **A–Z** dengan bilah lompat, **Sering dipakai**, **kategori**, **lencana notifikasi**, pintasan aplikasi, **pencarian universal** (aplikasi · lagu · kontak opsional · pengaturan sistem) |
| Taskbar & tombol navigasi | Bar bergaya panel depan VCR di atas semua aplikasi (tombol mekanis, jam & lagu di jendela VFD); tombol Kembali/Home/Terbaru bergaya tombol remote yang bisa diatur |
| Gestur | Swipe atas/bawah & ketuk dua kali, plus gestur gambar sendiri (16 aksi) |
| Kustomisasi | Tekan lama area kosong membuka *service menu*: TV & kaset (bodi, ukuran, posisi, knob, VFD, layar CRT, cangkang & gulungan kaset, tukar halaman), dinding, jam sudut, widget, gestur, preset tema |
| Efek visual | Distorsi kaca, cahaya reaktif, partikel, parallax, visualizer audio (*Audio asli* butuh izin mikrofon; *Animasi dekoratif* diberi label). Mati otomatis saat animasi sistem dimatikan atau Penghemat baterai aktif |

## Cara build di Android Studio

1. Pasang **Android Studio Narwhal (2025.1) atau lebih baru** dengan **Android SDK Platform 36** dan **JDK 17** (JBR bawaan Android Studio sudah cukup).
2. Clone repositori ini, lalu **File → Open** dan pilih foldernya.
3. Tunggu *Gradle Sync* selesai (Gradle 8.13 dan semua dependensi diunduh otomatis). Jika Android Studio menawarkan upgrade AGP/Kotlin, aman untuk diterima.
4. Sambungkan tablet (aktifkan **Opsi pengembang → USB debugging**) atau jalankan emulator tablet (mis. *Pixel Tablet*, API 34+).
5. Klik **Run ▶** (konfigurasi `app`).

Build lewat terminal:

```bash
./gradlew assembleDebug          # APK di app/build/outputs/apk/debug/
./gradlew installDebug           # langsung pasang ke perangkat
```

## Setelah terpasang

Aplikasi pertama kali membuka **onboarding**:

1. **Jadikan launcher default** — pilih TV Launcher sebagai aplikasi Home.
2. **Akses notifikasi** — wajib agar launcher bisa membaca & mengontrol lagu (isi notifikasi tidak dibaca atau disimpan).
3. **Kalender** *(opsional)* — untuk titik acara di kalender layar kunci.
4. **Layar kunci TV** *(opsional)* — izinkan "Tampilkan di atas aplikasi lain" & notifikasi, lalu aktifkan.
5. **Aksi gestur** *(opsional)* — layanan aksesibilitas untuk gestur "kunci layar" dan "buka notifikasi".

Ikon *TV Launcher* di app drawer membuka halaman **Pengaturan** (semua opsi + reset ke default).

## Tes, lint, dan keamanan

```bash
./gradlew testDebugUnitTest      # tes unit murni Kotlin (perencana taskbar, cadangan pengaturan, kontras warna & peredam latar, izin ↔ manifest, logika drawer: kategori, indeks abjad, peringkat, pencarian, lencana, susunan daftar; musik: parser LRC, klien LRCLIB, cache lirik, fokus lirik, rencana sleep timer & pergantian lembut, matematika tumpukan 3D; efek visual: parallax & penyaring sensor, partikel, analisis spektrum, perencana efek, anggaran kontras distorsi/cahaya/partikel)
./gradlew lintDebug              # laporan di app/build/reports/lint-results-debug.html
```

GitHub Actions menjalankan tes → lint → build pada setiap push dan mengunggah laporannya (artifact `VinylLauncher-reports`). Lint memblokir build bila ada *error*; peringatan (versi pustaka yang lebih baru, saran KTX) dilaporkan di `build.log` tetapi tidak memblokir.
Hal-hal keamanan, privasi, dan **cara mengganti kunci tanda tangan** ada di [SECURITY.md](SECURITY.md).
Pengaturan bisa disimpan/dipulihkan dari Pengaturan → *Cadangan & privasi*.

## Unduh APK tanpa laptop

Setiap perubahan di branch `main` otomatis di-build oleh GitHub Actions. APK terbaru selalu tersedia di:
`https://github.com/cntr2303diran-design/Vinyl-2.0/releases/download/latest/TVLauncher.apk`
(tautan lama `.../latest/VinylLauncher.apk` tetap berisi APK yang sama)

> Selama Secrets `VINYL_KEYSTORE_*` belum diisi, tiap build memakai kunci debug yang berbeda, sehingga APK baru tidak bisa menimpa APK sebelumnya (copot dulu). Lihat [SECURITY.md](SECURITY.md) → *Kunci tanda tangan*.

## Catatan teknis

- **Arsitektur:** MVVM — `data/` (DataStore, LauncherApps, kalender, cuaca), `media/` (MediaSession, sleep timer, pengatur volume, video), `audio/` (Audio Console: model murni, pemantau perangkat, EQ, pengurai laporan sistem), `advanced/` (Akses lanjutan & Shizuku), `drawer/` dan `music/` (logika murni, diuji unit), `service/`, `ui/room` (ruang TV), `ui/cassette`, `ui/library`, `ui/console`, `ui/` lainnya (layar + ViewModel). Desain: lihat [DESIGN.md](DESIGN.md).
- **Penyimpanan:** seluruh pengaturan disimpan sebagai satu dokumen JSON (kotlinx.serialization) di DataStore, sehingga field baru otomatis memakai default dan reset/preset mudah.
- **Grafis:** TV tabung, kaset, gulungan, knob, ruangan, dan jam digambar dengan Compose Canvas — tajam di semua ukuran, tanpa aset bermerek. Animasi gulungan berhenti saat musik berhenti (hemat baterai). Komposisi ruang TV dihitung dari pecahan layar (`SceneLayout`, diuji).
- **Video di layar TV:** Media3 ExoPlayer 1.8.0, tanpa audio & tanpa audio focus, maks 720p, dilepas saat ruang TV tidak tampil. Folder lewat Storage Access Framework (tanpa izin penyimpanan).
- **Rotasi layar:** activity menangani `configChanges` sendiri; tata letak dihitung ulang dari `BoxWithConstraints`, posisi widget disimpan sebagai fraksi layar.
- **Layar kunci:** `CoverActivity` memakai `setShowWhenLocked` + `setTurnScreenOn`; keamanan tetap ditangani PIN/sandi/biometrik sistem via `KeyguardManager.requestDismissKeyguard`. Activity dibuka oleh `CoverService` (foreground service ringan) saat layar mati. Android 10+ membatasi pembukaan activity dari latar belakang, karena itu izin *Tampilkan di atas aplikasi lain* diperlukan.
- **Font:** Inter, Instrument Serif, dan JetBrains Mono (lisensi OFL) diunduh otomatis dari repositori google/fonts saat build pertama (task Gradle `fetchFonts`), jadi build pertama butuh internet.
- **Cuaca:** Open-Meteo (gratis, tanpa API key), lokasi diatur manual di Pengaturan → Cuaca; di-cache 30 menit.
- **Gestur notifikasi/kunci layar:** memakai layanan aksesibilitas bila aktif; notifikasi punya cadangan lewat `StatusBarManager` (API tersembunyi, berfungsi di sebagian besar perangkat).

## Keterbatasan yang perlu diketahui

- One UI menonaktifkan taskbar Samsung saat launcher pihak ketiga aktif — gunakan **Taskbar TV** (Pengaturan → Taskbar).
- Tombol navigasi TV memakai layanan aksesibilitas untuk Kembali & Terbaru. Agar tombol bawaan hilang, ubah navigasi sistem ke *Gestur geser*.
- "Buka sebagai jendela" hanya berfungsi bila sistem mengizinkan jendela bentuk bebas (Opsi pengembang).
- Pintasan aplikasi (menu tekan-lama) hanya tersedia bila TV Launcher adalah launcher **default**; sistem menolaknya untuk launcher biasa.
- Lencana notifikasi dihitung per nama paket, jadi aplikasi yang sama di profil kerja dan pribadi berbagi hitungan.
- Seek (knob, garis progres, mundur 10 dtk) hanya aktif bila aplikasi musik mendukung `ACTION_SEEK_TO`.
- **Equalizer** hanya bekerja pada sesi audio yang dibuka aplikasi musik (atau yang terdeteksi lewat Akses lanjutan); mode "semua audio" (sesi 0) eksperimental. Bisa bentrok dengan efek pabrikan (Dolby/Samsung) atau aplikasi EQ lain — statusnya ditampilkan apa adanya.
- **Codec, format keluaran, dan resampling** hanya terbaca dengan Akses lanjutan; format laporan sistem OEM bisa berbeda dari AOSP (yang tidak cocok tampil *Tidak tersedia*). **Baterai earphone Bluetooth** tidak ditampilkan karena laporan sistem AOSP tidak memuatnya.
- **Video YouTube tidak didukung**: kebijakan YouTube API melarang memisahkan audio dan video kontennya.
- **Audio Console, EQ, Akses lanjutan, video, layar kunci TV, dan bunyi kaset belum diuji di perangkat nyata** (hanya tes unit & tangkapan layar otomatis).
- **Antrean** hanya tampil bila aplikasi musik membagikannya ke sesi media; TV Launcher tidak bisa mengubah atau mengacak antrean itu.
- **Tidak ada crossfade sungguhan**: TV Launcher tidak memutar audio, jadi tidak bisa menumpuk dua lagu. *Pergantian lembut* hanya mengatur volume media.
- **Timer tidur** hidup di proses TV Launcher. Bila sistem menutup prosesnya, timer berhenti (volume tetap dipulihkan saat TV Launcher hidup lagi).
- Pengaburan baris lirik jauh memakai `Modifier.blur`, yang hanya berefek di Android 12+ (di bawahnya baris tetap tajam).
- Lirik tersinkron mengikuti versi lagu di LRCLIB; versi dengan durasi berbeda dari lagu yang diputar (selisih > 8 dtk) dibuang agar tidak meleset.
- **Efek visual belum diuji di perangkat nyata**: shader AGSL (butuh Android 13+; di bawahnya distorsi tidak tampil, cahaya memakai cadangan Canvas), arah parallax, dan Visualizer pada sesi audio 0 bisa berbeda antarpabrikan. Bila Audio asli ditolak sistem, hasilnya ditulis apa adanya, bukan diganti animasi.
- Parallax tanpa giroskop terpadu lemah untuk gerak kiri-kanan saat ponsel berdiri tegak.
- Jaminan kontras efek hanya untuk latar mode *Album*; latar Warna/Gradien/Gambar dipilih pengguna dan tidak dihitung otomatis.
- Gestur gambar sendiri dikenali di area kosong layar utama (bukan di dalam aplikasi lain).
