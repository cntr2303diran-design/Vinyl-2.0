# TV Launcher 1.0

*(sebelumnya Vinyl Launcher; package id tetap `com.vinyl.launcher` agar pembaruan bisa menimpa versi lama)*

Launcher Android untuk tablet dengan tema **TV tabung analog** (sebelumnya "tablet sebagai turntable") — Kotlin + Jetpack Compose, minSdk 29, targetSdk 36. Dioptimalkan untuk landscape; portrait tetap didukung dengan tata letak yang menyesuaikan. Bahasa desain & token: [DESIGN.md](DESIGN.md).

## Fitur

| Bagian | Isi |
|---|---|
| Beranda VHS (layar utama) | Ruangan malam bergaya rekaman kamera VHS: dinding berpanel kayu, **proyektor di belakang TV** yang menyorotkan cover (atau visualizer) ke dinding, meja stereo dengan deck kaset di raknya, **TV tabung yang diam** (tidak membesar, tidak bisa dipindah), dan **kaset 3D** di sampingnya dengan gulungan yang berputar saat musik berjalan. Ketuk layar TV = lirik layar penuh; ketuk kaset = putar/jeda; tombol deck = sebelumnya · mundur 10 dtk · putar/jeda · berikutnya. Jendela VFD deck: mode pita (PLAY/PAUSE + *HI-FI*/*SP*) dan keluaran audio, atau format sumber pemutar bawaan (mis. `FLAC 24/96`). Butir film & garis tracking opsional |
| Knob SEEK | Putar knob di panel TV untuk menggeser posisi lagu; waktu tujuan tampil di layar TV, klik & getar tiap gigi. Knob dan TV tidak bergerak |
| OSD (pengganti status bar) | Status bar sistem disembunyikan selama beranda tampil (usap dari atas memunculkannya sebentar). Teks kamera VHS menampilkan jam (detik opsional), tanggal, baterai & pengisian, Wi-Fi, sinyal seluler, Bluetooth **+ baterai earphone**, mode dering, Jangan Ganggu, alarm, mode pesawat, ikon & jumlah notifikasi, timer tidur, dan **penghitung pita** yang bisa digeser. Ketuk jam/tanggal = alarm, ketuk status = Pengaturan cepat, ketuk notifikasi = panel notifikasi. Semua bisa dimatikan (editor beranda → *Status & jam*) |
| Proyektor | Editor beranda → tab **Proyektor** (juga di Pengaturan): sumber *Cover / Visualizer / Mati*, kecerahan, ukuran, ketajaman (buram butuh Android 12+), dan gangguan pita VHS |
| Video di layar TV | Opsional: video milik Anda dari folder pilihan (`Artis - Album.mp4` dicocokkan otomatis, atau ditautkan manual) diputar tanpa suara & berulang di layar TV |
| Perpustakaan kaset | Geser dari tepi (halaman kiri): tab **Rak** (kaset per abjad, favorit), **Deck** (antrean aplikasi musik), **Log** (riwayat per hari), **Koleksi** (statistik), **Lokal** (pemutar bawaan) |
| Pemutar bawaan (hi-fi) | Tab **Lokal**: pilih satu folder musik (tanpa izin penyimpanan), subfolder ikut dipindai, urut per album/artis/judul/terbaru, cari, label kualitas (`FLAC 24/96`). FLAC & WAV hi-res, MP3, AAC, OGG, Opus; ALAC bila perangkat punya dekodernya. Dekoder mengeluarkan **float 32-bit** (24-bit utuh) ke AudioTrack float; **gapless**. Rantai suara: **ReplayGain** (lagu/album, R128, cegah clipping), **EQ parametrik** sampai 20 pita (peaking/shelf/pass) dengan impor/ekspor **AutoEQ** `ParametricEQ.txt`, headroom otomatis, **crossfeed** headphone, pembatas puncak hanya bila perlu; bila tidak ada olahan, sampel diteruskan apa adanya. Android 14+ dengan DAC USB: *laju sampel cocok* (mixer mengikuti laju lagu, tanpa resampling Android). Lagu tampil di beranda, layar kunci, dan notifikasi media seperti aplikasi musik lain |
| Audio Console | Halaman kanan bergaya receiver stereo: perangkat keluaran, **Pemutar bawaan** (jalur suara: sumber → dekoder → rantai → ke Android, apa adanya), **Suara aplikasi lain** (EQ 3 pita, 10 pita grafis, atau profil parametrik/AutoEQ yang dipetakan ke 31 pita DynamicsProcessing; preamp, balance, preset, profil per perangkat; cadangan keluaran gabungan sesi 0), kualitas, spasial & efek, diagnostik (tes kanal kiri/kanan), otomasi, **baterai earphone** (level terakhir diingat, ditandai ≈). Setiap nilai berlabel sumbernya: *Pasti / Terukur / Dilaporkan sistem / Perkiraan / Tidak tersedia* |
| Akses lanjutan | Opsional: izin `DUMP` membuka codec Bluetooth aktif (termasuk mode LDAC 990/660/330 kbps/adaptif), format keluaran, resampling, dan EQ untuk aplikasi musik yang tidak membuka sesi efek. Diberikan **tanpa komputer** lewat Shizuku (Android 11+, *Wireless debugging*; langkahnya ada di console) atau lewat ADB |
| Lirik layar penuh | Baris aktif besar dan terang, baris jauh mengabur; gulir otomatis; ketuk baris untuk melompat; status memuat / dimatikan / tidak ditemukan / gagal (*Coba lagi*) / instrumental / tanpa waktu |
| Timer tidur | Di perpustakaan kaset dan lirik layar penuh: 5–90 menit atau sampai lagu selesai; volume dipudarkan, musik dijeda, **volume dikembalikan** |
| Pergantian lembut | Opsional, untuk aplikasi musik lain: volume turun sebentar lalu naik saat ganti lagu. **Bukan crossfade** (launcher tidak memegang audio aplikasi lain) |
| Suara & getar | Bunyi tombol kaset & motor, desing pita saat ganti lagu, klik knob, desis pita opsional; volume & kekuatan getar bisa diatur |
| Layar kunci TV | Tampil di atas keyguard: TV dengan cover & VFD (jam, artis), jam besar, tanggal, kalender, kartu lagu + lirik; geser untuk membuka, tekan lama untuk editor |
| App drawer (Panduan Channel) | Tiap aplikasi punya **nomor channel** tetap (urutan abjad); **A–Z** dengan bilah lompat, **Sering dipakai**, **kategori**, **lencana notifikasi**, pintasan aplikasi, **pencarian universal** (aplikasi · lagu · kontak opsional · pengaturan sistem) |
| Taskbar & tombol navigasi | Bar bergaya panel depan VCR di atas semua aplikasi (tombol mekanis, jam & lagu di jendela VFD); tombol Kembali/Home/Terbaru bergaya tombol remote yang bisa diatur |
| Gestur | Swipe atas/bawah & ketuk dua kali, plus gestur gambar sendiri (16 aksi) |
| Kustomisasi | Tekan lama area kosong membuka *service menu*: TV & kaset (bodi, ukuran, knob, VFD, layar CRT, cangkang & gulungan kaset, tukar halaman), proyektor, dinding, status & jam (OSD), widget, gestur, preset tema |
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

Pemutar bawaan: buka perpustakaan kaset (geser dari tepi) → tab **Lokal** → *Pilih folder musik*. Pengaturan suaranya
(ReplayGain, EQ parametrik/AutoEQ, crossfeed, keluaran float, laju sampel USB) ada di Audio Console → *Pemutar bawaan*.

## Tes, lint, dan keamanan

```bash
./gradlew testDebugUnitTest      # tes unit murni Kotlin + tangkapan layar Compose (Robolectric/Roborazzi)
./gradlew lintDebug              # laporan di app/build/reports/lint-results-debug.html
```

Yang diuji tes unit, antara lain: kontras warna & peredam latar (termasuk palet VHS/OSD), izin ↔ manifest ↔ dokumen privasi,
cadangan & migrasi pengaturan, logika drawer, musik (parser LRC, cache lirik, sleep timer), efek visual, tata letak panggung
VHS (kamera satu titik hilang, proyeksi, deck, layar pendek/RTL), teks OSD (jam, tanggal, baterai earphone), DSP pemutar
bawaan (respons & kestabilan filter biquad, EQ parametrik & headroom, crossfeed, pembatas & ekor latensinya, ReplayGain/R128,
dither, konversi PCM 8–32-bit & float, impor/ekspor AutoEQ, rantai gapless), model pustaka lokal (urut, cari, format yang
didukung, antrean, pesan galat), model Audio Console, dan pengurai laporan sistem (codec Bluetooth, LDAC, AudioFlinger).

GitHub Actions menjalankan tes → lint → build pada setiap push dan mengunggah laporannya (artifact `VinylLauncher-reports`). Lint memblokir build bila ada *error*; peringatan (versi pustaka yang lebih baru, saran KTX) dilaporkan di `build.log` tetapi tidak memblokir.
Hal-hal keamanan, privasi, dan **cara mengganti kunci tanda tangan** ada di [SECURITY.md](SECURITY.md).
Pengaturan bisa disimpan/dipulihkan dari Pengaturan → *Cadangan & privasi*.

## Unduh APK tanpa laptop

Setiap perubahan di branch `main` otomatis di-build oleh GitHub Actions. APK terbaru selalu tersedia di:
`https://github.com/cntr2303diran-design/Vinyl-2.0/releases/download/latest/TVLauncher.apk`
(tautan lama `.../latest/VinylLauncher.apk` tetap berisi APK yang sama)

> Selama Secrets `VINYL_KEYSTORE_*` belum diisi, tiap build memakai kunci debug yang berbeda, sehingga APK baru tidak bisa menimpa APK sebelumnya (copot dulu). Lihat [SECURITY.md](SECURITY.md) → *Kunci tanda tangan*.

## Catatan teknis

- **Arsitektur:** MVVM — `data/` (DataStore, LauncherApps, kalender, cuaca), `media/` (MediaSession aplikasi lain, sleep timer, pengatur volume, video), `player/` (pemutar bawaan: Media3 ExoPlayer + `MediaSessionService`, `HiFiAudioSink`, pustaka lokal SAF), `audio/` (Audio Console: model murni, pemantau perangkat, EQ aplikasi lain, pengurai laporan sistem) dan `audio/dsp/` (DSP murni, diuji unit), `system/` (status perangkat untuk OSD), `advanced/` (Akses lanjutan & Shizuku), `drawer/` dan `music/` (logika murni, diuji unit), `service/`, `ui/room` (TV, panggung VHS `stage/`, OSD `vhs/`), `ui/crate` & `ui/library` (perpustakaan kaset, tab Lokal), `ui/console`, `ui/` lainnya (layar + ViewModel). Desain: lihat [DESIGN.md](DESIGN.md).
- **Penyimpanan:** seluruh pengaturan disimpan sebagai satu dokumen JSON (kotlinx.serialization) di DataStore, sehingga field baru otomatis memakai default dan reset/preset mudah.
- **Grafis:** TV tabung, kaset, gulungan, knob, deck, meja, dinding, dan OSD digambar dengan Compose Canvas — tajam di semua ukuran, tanpa aset bermerek. Panggung VHS memakai kamera satu titik hilang (`StageCamera`): bidang yang menghadap kamera adalah composable biasa (target sentuh & TalkBack tetap normal), sisi atas/samping digambar Canvas. Susunannya dihitung `StageLayout` (diuji unit: berbagai ukuran layar, layar pendek mendatar, bilah sistem & taskbar, TV tidak bisa dipindah; huruf 200% dan RTL lewat tangkapan layar otomatis). Animasi gulungan, butir film, dan proyeksi berhenti saat musik berhenti, beranda tidak tampil, *Hapus animasi*, atau Penghemat baterai.
- **Video di layar TV:** Media3 ExoPlayer 1.8.0, tanpa audio & tanpa audio focus, maks 720p, dilepas saat ruang TV tidak tampil. Folder lewat Storage Access Framework (tanpa izin penyimpanan).
- **Pemutar bawaan:** `PlayerService` (`MediaSessionService`, tidak diekspor, foreground `mediaPlayback` hanya selama memutar). `HiFiAudioSink` membungkus `DefaultAudioSink` (float output): dekoder diminta keluaran float, sink mengubah PCM 8/16/24/32-bit/float ke float 32-bit, menjalankan rantai `DspChain` (ReplayGain → EQ biquad presisi ganda → crossfeed → pembatas), lalu mengirim float (atau 16-bit ber-dither TPDF) ke AudioTrack. Rantai yang tidak mengubah apa pun diteruskan apa adanya. Kontrak `AudioSink` (buffer yang sama sampai habis, ekor pembatas di akhir aliran) dibuat mengikuti kode sumber Media3 1.8.0. Pustaka: indeks JSON di penyimpanan internal, pemindaian ulang hanya membaca berkas yang berubah.
- **Rotasi layar:** activity menangani `configChanges` sendiri; tata letak dihitung ulang dari `BoxWithConstraints`, posisi widget disimpan sebagai fraksi layar.
- **Layar kunci:** `CoverActivity` memakai `setShowWhenLocked` + `setTurnScreenOn`; keamanan tetap ditangani PIN/sandi/biometrik sistem via `KeyguardManager.requestDismissKeyguard`. Activity dibuka oleh `CoverService` (foreground service ringan) saat layar mati. Android 10+ membatasi pembukaan activity dari latar belakang, karena itu izin *Tampilkan di atas aplikasi lain* diperlukan.
- **Font:** Inter, Instrument Serif, JetBrains Mono, VT323 (teks OSD) — lisensi OFL — dan Permanent Marker (tulisan label kaset, Apache 2.0) diunduh otomatis dari repositori google/fonts saat build pertama (task Gradle `fetchFonts`), jadi build pertama butuh internet.
- **Cuaca:** Open-Meteo (gratis, tanpa API key), lokasi diatur manual di Pengaturan → Cuaca; di-cache 30 menit.
- **Gestur notifikasi/kunci layar:** memakai layanan aksesibilitas bila aktif; notifikasi punya cadangan lewat `StatusBarManager` (API tersembunyi, berfungsi di sebagian besar perangkat).

## Keterbatasan yang perlu diketahui

- One UI menonaktifkan taskbar Samsung saat launcher pihak ketiga aktif — gunakan **Taskbar TV** (Pengaturan → Taskbar).
- Tombol navigasi TV memakai layanan aksesibilitas untuk Kembali & Terbaru. Agar tombol bawaan hilang, ubah navigasi sistem ke *Gestur geser*.
- "Buka sebagai jendela" hanya berfungsi bila sistem mengizinkan jendela bentuk bebas (Opsi pengembang).
- Pintasan aplikasi (menu tekan-lama) hanya tersedia bila TV Launcher adalah launcher **default**; sistem menolaknya untuk launcher biasa.
- Lencana notifikasi dihitung per nama paket, jadi aplikasi yang sama di profil kerja dan pribadi berbagi hitungan.
- Seek (knob, penghitung pita OSD, mundur 10 dtk) hanya aktif bila aplikasi musik mendukung `ACTION_SEEK_TO` (pemutar bawaan selalu mendukung).
- **EQ untuk aplikasi lain** bekerja pada sesi audio yang dibuka aplikasi musik (atau terdeteksi lewat Akses lanjutan). Cadangan "keluaran gabungan" (sesi 0) memakai cara yang sudah usang di Android: di sebagian perangkat ditolak atau tidak berefek, dan bisa bentrok dengan efek pabrikan (Dolby/Samsung) atau aplikasi EQ lain — statusnya ditampilkan apa adanya. EQ pemutar bawaan tidak terpengaruh (berjalan di dalam pemutar).
- **Codec Bluetooth (termasuk mode LDAC), format keluaran, dan resampling** hanya terbaca dengan Akses lanjutan; format laporan sistem OEM bisa berbeda dari AOSP (yang tidak cocok tampil *Tidak tersedia*).
- **Baterai earphone** memakai siaran sistem yang tidak resmi (`BATTERY_LEVEL_CHANGED`, butuh izin Perangkat sekitar): tidak semua earphone/ponsel mengirimnya, dan launcher harus sedang berjalan saat laporan datang. Level terakhir diingat dan ditandai ≈ beserta umurnya.
- **Video YouTube tidak didukung**: kebijakan YouTube API melarang memisahkan audio dan video kontennya.
- **Belum diuji di perangkat nyata** (hanya tes unit, tangkapan layar otomatis, dan build CI): pemutar bawaan & rantai suaranya, laju sampel USB, Audio Console, EQ, Akses lanjutan/Shizuku, OSD & penyembunyian status bar, video, layar kunci TV, dan bunyi kaset.
- **Bukan bit-perfect.** Pemutar bawaan mengirim float 32-bit ke mixer Android; volume sistem dan suara lain tetap dicampur, dan di luar USB Android me-resample ke laju mixer (umumnya 48 kHz). *Laju sampel cocok* (Android 14+, DAC USB) hanya mencegah resampling bila DAC menyediakan laju lagu itu. Mode bit-perfect Android (`MIXER_BEHAVIOR_BIT_PERFECT`) belum dibuat: mode itu menuntut format AudioTrack sama persis dengan format DAC (umumnya 24/32-bit integer, sedangkan keluaran sink Media3 hanya 16-bit atau float), data dikirim apa adanya tanpa pencampuran sehingga volume sistem tidak diterapkan mixer, dan perlu diuji dengan DAC nyata sebelum aman dipakai.
- **Format pemutar bawaan:** AIFF, DSD (DSF/DFF), APE, dan WMA belum didukung (tampil di daftar dengan keterangan, tidak masuk antrean). ALAC tergantung dekoder perangkat; lagu yang gagal diputar dilewati dan alasannya ditulis di tab Lokal.
- **OSD** hanya menampilkan status yang bisa dibaca lewat API publik tanpa izin tambahan (lihat tabel Fitur). Indikator lain dari status bar Samsung (VPN, hotspot, NFC, lokasi, nama Wi-Fi, kecepatan jaringan) tidak ada di OSD — usap dari atas untuk melihat status bar asli.
- **Antrean** hanya tampil bila aplikasi musik membagikannya ke sesi media; TV Launcher tidak bisa mengubah atau mengacak antrean itu.
- **Tidak ada crossfade.** Pemutar bawaan memutar tanpa jeda (gapless), tanpa menumpuk dua lagu. Untuk aplikasi musik lain, *Pergantian lembut* hanya mengatur volume media.
- **Timer tidur** hidup di proses TV Launcher. Bila sistem menutup prosesnya, timer berhenti (volume tetap dipulihkan saat TV Launcher hidup lagi).
- Pengaburan baris lirik jauh memakai `Modifier.blur`, yang hanya berefek di Android 12+ (di bawahnya baris tetap tajam).
- Lirik tersinkron mengikuti versi lagu di LRCLIB; versi dengan durasi berbeda dari lagu yang diputar (selisih > 8 dtk) dibuang agar tidak meleset.
- **Efek visual belum diuji di perangkat nyata**: shader AGSL (butuh Android 13+; di bawahnya distorsi tidak tampil, cahaya memakai cadangan Canvas), arah parallax, dan Visualizer pada sesi audio 0 bisa berbeda antarpabrikan. Bila Audio asli ditolak sistem, hasilnya ditulis apa adanya, bukan diganti animasi.
- Parallax tanpa giroskop terpadu lemah untuk gerak kiri-kanan saat ponsel berdiri tegak.
- Jaminan kontras efek hanya untuk latar mode *Album*; latar Warna/Gradien/Gambar dipilih pengguna dan tidak dihitung otomatis.
- Gestur gambar sendiri dikenali di area kosong layar utama (bukan di dalam aplikasi lain).
