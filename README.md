# Vinyl Launcher

Launcher Android untuk tablet dengan konsep **"tablet sebagai turntable"** — Kotlin + Jetpack Compose, minSdk 29, targetSdk 36. Dioptimalkan untuk landscape; portrait tetap didukung dengan tata letak yang menyesuaikan.

## Fitur (versi 1.0)

| Bagian | Isi |
|---|---|
| Layar utama | Latar ambient dari warna cover (dengan transisi warna sinematik saat cover berganti), turntable Canvas (sampul, vinyl beralur & berkilap, tonearm), jam & widget yang bisa diatur, info lagu + dua baris lirik tersinkron (LRCLIB) |
| Lirik layar penuh | Ketuk kapsul **Lirik** atau barisnya: baris aktif besar dan terang, baris lain mengecil, baris jauh sedikit mengabur; gulir otomatis mengikuti lagu (berhenti saat disentuh); ketuk baris untuk melompat. Ada status memuat / dimatikan / tidak ditemukan / gagal (*Coba lagi*) / instrumental / lirik tanpa waktu |
| Timer tidur | Kapsul di rak piringan dan lirik layar penuh: 5–90 menit atau sampai lagu selesai; volume dipudarkan pelan lalu musik dijeda dan **volume dikembalikan**. Tanpa izin tambahan |
| Pergantian lembut | Opsional (Pengaturan → Musik): volume turun sebentar lalu naik saat lagu berikutnya/sebelumnya. **Bukan crossfade** — Vinyl tidak memutar audio |
| Animasi | Play: vinyl meluncur keluar → tonearm turun → berputar 33/45 RPM. Pause: tonearm naik → melambat → masuk sebagian ke sampul |
| Interaksi turntable | Ketuk sampul = putar/jeda · geser sampul = lagu berikut/sebelumnya · seret tonearm = seek · angkat tonearm dari piringan = jeda · putar vinyl dengan jari = maju/mundur |
| Suara & getar | Bunyi jarum, klik saat vinyl diputar, kibasan ganti lagu, kresek vinyl opsional; volume & kekuatan getar bisa diatur |
| Rak piringan | Geser ke kiri di layar utama: tab **Rak** (sampul berjajar di atas papan kayu, atau **Tumpukan 3D**), tab **Antrean** (daftar putar milik aplikasi musik, ketuk untuk melompat), tab **Riwayat** per hari, tab **Koleksi** dengan statistik |
| Layar kunci vinyl | Tampil di atas keyguard, jam, tanggal, kalender, kartu lagu + lirik; geser untuk membuka, tekan lama untuk editor |
| App drawer | Dikelompokkan **A–Z** dengan bilah lompat, baris **Sering dipakai** (dari riwayat lokal), chip **kategori** (otomatis + bisa dipindah manual), **lencana notifikasi**, menu tekan-lama dengan **pintasan aplikasi**, dan **pencarian universal**: aplikasi · lagu di koleksi · kontak (opsional) · pengaturan sistem |
| Taskbar Vinyl | Bar gaya PC di atas semua aplikasi: Home, panel semua aplikasi, favorit, aplikasi terakhir, kartu lagu, jam/tanggal/baterai; posisi bawah/atas/kiri/kanan; opsi buka aplikasi sebagai jendela |
| Tombol navigasi Vinyl | Kembali/Home/Terbaru melayang: posisi bebas, jarak antar tombol, ukuran, transparansi, urutan, susun vertikal |
| Gestur | Swipe atas/bawah & ketuk dua kali, plus **gestur gambar sendiri** (satu goresan) dengan 16 pilihan aksi |
| Kustomisasi | Tekan lama area kosong: latar, turntable, jam (5 gaya), widget kalender/cuaca/baterai/foto, gestur, preset tema |
| Efek visual | Pengaturan → *Efek visual*: **distorsi kaca** (alur kaca bergelombang mengikuti musik + riak dari sentuhan, Android 13+), **cahaya reaktif**, **partikel**, **parallax** sensor gerak/giroskop (seperti jendela / melayang), dan **visualizer audio** (*Audio asli* membaca spektrum keluaran audio dan butuh izin mikrofon; *Animasi dekoratif* diberi label dan tanpa izin). Semua mati otomatis saat animasi sistem dimatikan atau Penghemat baterai aktif, dan teks tetap ≥ 4,5 : 1 di atas latar Album |
| Pengaturan | Dua panel ala tablet (kategori di kiri, isi di kanan) |

## Cara build di Android Studio

1. Pasang **Android Studio Narwhal (2025.1) atau lebih baru** dengan **Android SDK Platform 36** dan **JDK 17** (JBR bawaan Android Studio sudah cukup).
2. Ekstrak `VinylLauncher.zip`, lalu **File → Open** dan pilih folder `VinylLauncher`.
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

1. **Jadikan launcher default** — pilih Vinyl Launcher sebagai aplikasi Home.
2. **Akses notifikasi** — wajib agar launcher bisa membaca & mengontrol lagu (isi notifikasi tidak dibaca atau disimpan).
3. **Kalender** *(opsional)* — untuk titik acara di kalender layar kunci.
4. **Layar kunci vinyl** *(opsional)* — izinkan "Tampilkan di atas aplikasi lain" & notifikasi, lalu aktifkan.
5. **Aksi gestur** *(opsional)* — layanan aksesibilitas untuk gestur "kunci layar" dan "buka notifikasi".

Ikon *Vinyl Launcher* di app drawer membuka halaman **Pengaturan** (semua opsi + reset ke default).

## Tes, lint, dan keamanan

```bash
./gradlew testDebugUnitTest      # tes unit murni Kotlin (perencana taskbar, cadangan pengaturan, kontras warna & peredam latar, izin ↔ manifest, logika drawer: kategori, indeks abjad, peringkat, pencarian, lencana, susunan daftar; musik: parser LRC, klien LRCLIB, cache lirik, fokus lirik, rencana sleep timer & pergantian lembut, matematika tumpukan 3D; efek visual: parallax & penyaring sensor, partikel, analisis spektrum, perencana efek, anggaran kontras distorsi/cahaya/partikel)
./gradlew lintDebug              # laporan di app/build/reports/lint-results-debug.html
```

GitHub Actions menjalankan tes → lint → build pada setiap push dan mengunggah laporannya (artifact `VinylLauncher-reports`).
Hal-hal keamanan, privasi, dan **cara mengganti kunci tanda tangan** ada di [SECURITY.md](SECURITY.md).
Pengaturan bisa disimpan/dipulihkan dari Pengaturan → *Cadangan & privasi*.

## Unduh APK tanpa laptop

Setiap perubahan di branch `main` otomatis di-build oleh GitHub Actions. APK terbaru selalu tersedia di:
`https://github.com/cntr2303diran-design/vinyllauncher/releases/download/latest/VinylLauncher.apk`

## Catatan teknis

- **Arsitektur:** MVVM — `data/` (DataStore, LauncherApps, kalender, cuaca), `media/` (MediaSession, sleep timer, pengatur volume), `drawer/` dan `music/` (logika murni tanpa dependensi Android, diuji unit), `service/`, `ui/` (layar + ViewModel), `ui/components/` (komponen Canvas). Desain: lihat [DESIGN.md](DESIGN.md).
- **Penyimpanan:** seluruh pengaturan disimpan sebagai satu dokumen JSON (kotlinx.serialization) di DataStore, sehingga field baru otomatis memakai default dan reset/preset mudah.
- **Grafis:** vinyl, alur, kilap, label melingkar, tonearm, jam analog/melingkar, dan ikon dock digambar dengan Compose Canvas — tajam di semua ukuran. Loop putaran memakai `withFrameNanos` dan berhenti sendiri saat vinyl diam (hemat baterai).
- **Rotasi layar:** activity menangani `configChanges` sendiri; tata letak dihitung ulang dari `BoxWithConstraints`, posisi widget disimpan sebagai fraksi layar.
- **Layar kunci:** `CoverActivity` memakai `setShowWhenLocked` + `setTurnScreenOn`; keamanan tetap ditangani PIN/sandi/biometrik sistem via `KeyguardManager.requestDismissKeyguard`. Activity dibuka oleh `CoverService` (foreground service ringan) saat layar mati. Android 10+ membatasi pembukaan activity dari latar belakang, karena itu izin *Tampilkan di atas aplikasi lain* diperlukan.
- **Font:** Inter, Instrument Serif, dan JetBrains Mono (lisensi OFL) diunduh otomatis dari repositori google/fonts saat build pertama (task Gradle `fetchFonts`), jadi build pertama butuh internet.
- **Cuaca:** Open-Meteo (gratis, tanpa API key), lokasi diatur manual di Pengaturan → Cuaca; di-cache 30 menit.
- **Gestur notifikasi/kunci layar:** memakai layanan aksesibilitas bila aktif; notifikasi punya cadangan lewat `StatusBarManager` (API tersembunyi, berfungsi di sebagian besar perangkat).

## Keterbatasan yang perlu diketahui

- One UI menonaktifkan taskbar Samsung saat launcher pihak ketiga aktif — gunakan **Taskbar Vinyl** (Pengaturan → Taskbar).
- Tombol navigasi Vinyl memakai layanan aksesibilitas untuk Kembali & Terbaru. Agar tombol bawaan hilang, ubah navigasi sistem ke *Gestur geser*.
- "Buka sebagai jendela" hanya berfungsi bila sistem mengizinkan jendela bentuk bebas (Opsi pengembang).
- Pintasan aplikasi (menu tekan-lama) hanya tersedia bila Vinyl Launcher adalah launcher **default**; sistem menolaknya untuk launcher biasa.
- Lencana notifikasi dihitung per nama paket, jadi aplikasi yang sama di profil kerja dan pribadi berbagi hitungan.
- Seek (seret tonearm / putar vinyl) hanya aktif bila aplikasi musik mendukung `ACTION_SEEK_TO`.
- **Antrean** hanya tampil bila aplikasi musik membagikannya ke sesi media; Vinyl tidak bisa mengubah atau mengacak antrean itu.
- **Tidak ada crossfade sungguhan**: Vinyl tidak memutar audio, jadi tidak bisa menumpuk dua lagu. *Pergantian lembut* hanya mengatur volume media.
- **Timer tidur** hidup di proses Vinyl. Bila sistem menutup prosesnya, timer berhenti (volume tetap dipulihkan saat Vinyl hidup lagi).
- Pengaburan baris lirik jauh memakai `Modifier.blur`, yang hanya berefek di Android 12+ (di bawahnya baris tetap tajam).
- Lirik tersinkron mengikuti versi lagu di LRCLIB; versi dengan durasi berbeda dari lagu yang diputar (selisih > 8 dtk) dibuang agar tidak meleset.
- **Efek visual belum diuji di perangkat nyata**: shader AGSL (butuh Android 13+; di bawahnya distorsi tidak tampil, cahaya memakai cadangan Canvas), arah parallax, dan Visualizer pada sesi audio 0 bisa berbeda antarpabrikan. Bila Audio asli ditolak sistem, hasilnya ditulis apa adanya, bukan diganti animasi.
- Parallax tanpa giroskop terpadu lemah untuk gerak kiri-kanan saat ponsel berdiri tegak.
- Jaminan kontras efek hanya untuk latar mode *Album*; latar Warna/Gradien/Gambar dipilih pengguna dan tidak dihitung otomatis.
- Gestur gambar sendiri dikenali di area kosong layar utama (bukan di dalam aplikasi lain).
