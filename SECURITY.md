# Keamanan & privasi

Dokumen ini menjelaskan apa yang dilindungi Vinyl Launcher, apa yang belum, dan satu keputusan yang
**perlu Anda ambil sendiri**: mengganti kunci tanda tangan.

## Privasi

- Semua pengaturan, riwayat lagu, dan statistik pemakaian disimpan **hanya di perangkat** (DataStore + folder internal aplikasi).
- Tidak ada akun, iklan, analitik, atau pelacak. Tidak ada server milik pengembang.
- Cadangan otomatis Android (`allowBackup`) **dimatikan**, dan `res/xml/data_extraction_rules.xml` menuliskan hal yang sama untuk Android 12+ (tidak ada data yang keluar lewat backup cloud maupun transfer perangkat). Pindah perangkat: Pengaturan → *Cadangan & privasi* → *Simpan cadangan*.
- Koneksi internet hanya HTTPS (`usesCleartextTraffic=false`, dan `Http.kt` menolak skema selain https) dan hanya untuk:

| Tujuan | Host | Data yang dikirim |
|---|---|---|
| Lirik | `lrclib.net` | judul, artis, album, durasi lagu yang sedang diputar. Bisa dimatikan total di Pengaturan → Musik → *Cari lirik online*: bila mati, tidak ada permintaan jaringan sama sekali |
| Cuaca | `api.open-meteo.com` | koordinat kota yang Anda isi sendiri (4 desimal) |
| Cover album | tautan `https` yang diberikan aplikasi musik | alamat IP perangkat (seperti permintaan web biasa) |

Tidak ada yang dikirim ke internet dari fitur app drawer (kategori, Sering dipakai, lencana, pencarian). Datanya:

| Data | Disimpan di | Catatan |
|---|---|---|
| Riwayat "Sering dipakai" (kunci aplikasi + cap waktu, maks 40 per aplikasi) | DataStore lokal `vinyl_usage` | Tidak ikut berkas cadangan; hapus lewat Pengaturan → App drawer → *Data pribadi* |
| Hitungan notifikasi per aplikasi (lencana) | Hanya memori | Hanya nama paket + penanda berkelanjutan/ringkasan grup yang dibaca; judul dan isi notifikasi tidak pernah diakses. Mati total bila *Lencana notifikasi* dimatikan |
| Pencarian kontak | Tidak disimpan | Opsional, mati secara default, butuh izin `READ_CONTACTS`; hanya nama yang dibaca, tampil sebagai hasil lalu dilupakan |
| Hasil lirik | Hanya memori (maks 60 lagu, hilang saat proses berakhir) | Lagu tanpa lirik diingat 6 jam, kegagalan jaringan hanya 20 detik agar bisa dicoba lagi |
| Sleep timer & pergantian lembut | Tiga nilai kecil (penanda aktif, volume asli, volume terakhir yang diatur) di `SharedPreferences` (`vinyl_volume_guard`) selama volume sedang dipudarkan | Hanya untuk memulihkan volume bila proses mati di tengah pemudaran; dihapus setelah volume dipulihkan. Tidak butuh izin |

Efek visual (Tahap 4) tidak mengirim apa pun ke internet:

| Data | Disimpan di | Catatan |
|---|---|---|
| Sensor gerak (parallax) | Tidak disimpan | Tanpa izin; didengarkan 50 Hz hanya selama layar utama terlihat dan parallax menyala, dilepas saat layar tertutup |
| Spektrum audio (visualizer *Audio asli*) | Hanya memori, satu tangkapan terakhir | Hanya FFT (magnitudo per frekuensi) campuran keluaran audio; callback gelombang mentah tidak diminta. Android mewajibkan `RECORD_AUDIO` (dan `MODIFY_AUDIO_SETTINGS` untuk sesi 0), tetapi mikrofon tidak dibuka, tidak ada rekaman, tidak ada berkas. Izin hanya diminta saat pengguna memilih mode itu; cadangan pengaturan tidak bisa menyalakannya sendiri |

Setiap izin dan alasannya tampil di Pengaturan → *Cadangan & privasi*; daftarnya (`PrivacyInfo.kt`) dicocokkan dengan
`AndroidManifest.xml` oleh tes otomatis, jadi izin baru tanpa alasan akan menggagalkan build.

## Yang dikeraskan

| Area | Perlindungan |
|---|---|
| Pengaturan | Setiap nilai yang dibaca/diimpor dibersihkan (`SettingsSanitizer`): rentang angka, panjang teks, jumlah item, nama paket, dan path gambar yang hanya boleh menunjuk folder internal aplikasi |
| Impor cadangan | Maks 1 MB, format & versi diperiksa, isi dibersihkan, status yang melekat pada perangkat (layar kunci, taskbar, navigasi, onboarding) tidak ditimpa. Tidak ada path file di berkas ekspor |
| Cover dari aplikasi musik | URI dari aplikasi lain tidak dipercaya: hanya `https`, `content`, `android.resource`; `file://` ditolak; data maks 12 MB; gambar selalu diperkecil saat decode |
| Kunci aplikasi (dock, tersembunyi, kategori) | `SettingsSanitizer.isSafeKey` menolak karakter di luar nama komponen dan urutan `..`; kategori pilihan harus nama kategori yang dikenal |
| Respons jaringan | Dibaca berbatas (lirik 512 KB, cuaca 64 KB, gambar 12 MB), hanya status 200 |
| Lirik dari internet | Dianggap tidak tepercaya: parser LRC dibatasi (5000 baris, 300 karakter per baris), jumlah kandidat pencarian dibatasi 20, lirik biasa 20.000 karakter; URL dibangun dengan penyandian parameter sehingga judul lagu tidak bisa menyisipkan parameter lain |
| Volume | Sleep timer/pergantian lembut hanya menyentuh `STREAM_MUSIC`, berhenti bila pengguna menekan tombol volume, dan selalu memulihkan volume asli (termasuk setelah proses mati) |
| Audio Console: siaran sesi efek | Penerima `OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` harus diekspor (dikirim aplikasi musik), didaftarkan saat proses berjalan. Nomor sesi divalidasi (> 0), sesi 0 tidak bisa dibuka lewat siaran, jumlah sesi dibatasi 16, nama paket dipotong 200 karakter. EQ mati secara default |
| Audio Console: Akses lanjutan | Izin `DUMP` hanya bisa diberikan pengguna sendiri (ADB/Shizuku). Laporan `dumpsys` dibaca lewat pipa di memori (maks 4 MB, batas waktu 4 detik), tidak pernah ditulis ke berkas, tidak dicatat ke log, tidak dikirim; pengurai murni dan toleran (`DumpParsers`, diuji dengan contoh sintetis dari format AOSP) |
| Shizuku | `ShizukuProvider` dilindungi `INTERACT_ACROSS_USERS_FULL` (hanya sistem/Shizuku). `ShellService` (proses shell) hanya punya satu operasi tetap: `pm grant com.vinyl.launcher android.permission.DUMP`; AIDL-nya tidak menerima teks/perintah (dijaga `PrivacyInfoTest`). Dijalankan hanya setelah dialog yang menampilkan perintahnya disetujui |
| Video di layar TV | Hanya folder yang dipilih pengguna (Storage Access Framework, izin baca persisten untuk folder itu saja; dilepas saat ganti/lepas folder). Hanya nama & jenis berkas tingkat teratas yang dibaca (maks 2000). Diputar tanpa suara, tanpa audio focus, maks 720p, hanya selama ruang TV tampil. URI folder tidak ikut berkas cadangan. `ACCESS_NETWORK_STATE` bawaan Media3 dibuang dari manifest |
| Komponen | `BootReceiver` tidak diekspor (siaran boot/pembaruan dikirim sistem). Yang tetap diekspor memang harus: Home dan Onboarding (dipanggil sistem/peluncur), serta layanan notifikasi & aksesibilitas yang hanya bisa di-bind sistem lewat izin `BIND_*` |

## Kunci tanda tangan — perlu keputusan Anda

**Masalah.** `app/vinyl.keystore` dan kata sandinya (`vinyl-launcher`) ada di repositori, jadi siapa pun yang bisa melihat
repo dapat membuat APK yang Android anggap sebagai *pembaruan sah* dari aplikasi Anda. Karena sudah masuk riwayat Git, kunci ini
harus dianggap **bocor selamanya**; menghapus berkasnya tidak cukup.

**Kenapa belum saya ganti.** Mengganti kunci memutus jalur pembaruan: Android menolak pembaruan yang ditandatangani kunci berbeda,
jadi aplikasi yang terpasang harus di-*uninstall* dulu (pengaturan hilang kecuali dicadangkan). Itu keputusan Anda, bukan saya.

**Status sekarang.** Build memakai kunci dari GitHub Secrets bila ada; bila tidak, kunci lama dipakai dan CI menampilkan peringatan
(ringkasan run + log Gradle). Tidak ada yang berubah untuk pemasangan Anda yang sekarang.

**Di repo Vinyl-2.0 (publik).** Berkas `app/vinyl.keystore` sengaja **tidak** disertakan, jadi tidak ada kunci di repo ini. Selama Secrets di
atas belum diisi, CI menandatangani APK dengan kunci *debug* yang dibuat otomatis di mesin build, dan kunci itu **berbeda di setiap run**.
Akibatnya APK dari CI bisa dipasang, tetapi **tidak bisa menimpa** APK lain (build sebelumnya atau Vinyl lama): copot dulu, lalu pasang.
Mengisi Secrets (langkah di bawah) menyelesaikannya sekaligus: semua build berikutnya memakai satu kunci rahasia milik Anda.

**Langkah rotasi (sekitar 10 menit):**

1. Di Vinyl Launcher lama: Pengaturan → *Cadangan & privasi* → **Simpan cadangan** (simpan berkasnya di luar ponsel).
2. Buat keystore baru di komputer Anda (jangan di dalam repo):
   ```bash
   keytool -genkeypair -v -keystore vinyl-release.jks -alias vinyl -keyalg RSA -keysize 4096 -validity 10000
   ```
3. Ubah menjadi teks base64: `base64 -w0 vinyl-release.jks` (Linux) atau `base64 -i vinyl-release.jks` (macOS).
4. Di GitHub: Settings → Secrets and variables → Actions → New repository secret:
   `VINYL_KEYSTORE_BASE64` (hasil langkah 3), `VINYL_KEYSTORE_PASSWORD`, serta bila alias/kata sandi kunci berbeda:
   `VINYL_KEY_ALIAS`, `VINYL_KEY_PASSWORD`.
5. **Simpan salinan `vinyl-release.jks` dan kata sandinya di tempat aman** (pengelola kata sandi). Bila hilang, aplikasi tidak bisa diperbarui lagi.
6. Jalankan ulang workflow. Ringkasan run harus berbunyi *"Kunci tanda tangan rahasia dari Secrets dipakai"*.
7. Di tablet: uninstall Vinyl Launcher lama, pasang APK baru, lalu Pengaturan → *Cadangan & privasi* → **Pulihkan dari berkas**.
8. Hanya bila repo Anda masih menyimpan kunci lama (repo `vinyllauncher` yang lama): hapus dari pohon kerja dengan `git rm app/vinyl.keystore`
   (riwayat Git tetap berisi kunci lama — itu sebabnya langkah 2–7 perlu). Di Vinyl-2.0 langkah ini tidak diperlukan.

Alternatif tanpa uninstall adalah *rotasi kunci* APK Signature Scheme v3 lewat `apksigner rotate` (didukung Android 9+; minSdk proyek 29).
Itu butuh langkah penandatanganan manual di luar build ini dan tidak diotomatiskan di sini; lihat dokumentasi resmi Android
"Key rotation" bila ingin menempuhnya.

## Batasan yang diketahui

- Tidak ada pemindaian dependensi otomatis; perbarui versi di `gradle/libs.versions.toml` secara berkala.
- Action GitHub (`actions/*`) dirujuk lewat tag versi, bukan hash commit.
- Font diunduh saat build dari repositori `google/fonts` cabang `main` (isi dicek berupa berkas font, tetapi belum dikunci ke hash tertentu).
- Visualizer *Audio asli* memakai sesi audio 0 (keluaran sistem). Beberapa versi Android/pabrikan membatasi atau menolak efek pada sesi ini;
  bila ditolak, Pengaturan dan layar utama menyatakannya. Belum diuji di perangkat nyata mana pun.
- Equalizer Audio Console bekerja per sesi audio aplikasi lain (DynamicsProcessing). Aplikasi musik yang tidak membuka sesi efek
  hanya terjangkau lewat Akses lanjutan; efek "semua audio" (sesi 0) sudah usang di Android dan diberi label eksperimental.
  Bisa bentrok dengan efek pabrikan (Dolby/Samsung) atau aplikasi EQ lain; status sesi ditampilkan apa adanya. Belum diuji di perangkat nyata.
- Format `dumpsys` milik OEM (mis. Samsung One UI) bisa berbeda dari AOSP; field yang tidak cocok tampil "Tidak tersedia". Belum diverifikasi
  di perangkat Samsung nyata. Laporan sistem AOSP tidak memuat baterai perangkat Bluetooth, jadi baterai tidak ditampilkan.
- Video YouTube sengaja tidak dibangun: Kebijakan Pengembang YouTube API Services III.I butir 7 melarang memisahkan atau
  mengisolasi komponen audio/video konten YouTube (https://developers.google.com/youtube/terms/developer-policies), padahal
  video di layar TV diputar tanpa suara sementara musik berasal dari aplikasi lain.
- Overlay (layar kunci, taskbar, tombol navigasi) memerlukan izin "tampil di atas aplikasi lain"; ini inheren pada fiturnya.

## Melapor kerentanan

Buka *Security advisory* privat di tab Security repositori GitHub ini, atau issue tanpa detail eksploit bila fiturnya tidak tersedia.
