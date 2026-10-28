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
| Situs film (laci kaset) | alamat `https` yang Anda isi sendiri di sebuah kaset film (bawaan: `archive.org`, `studio.blender.org`, `www.nfb.ca`) | Hanya terjadi setelah Anda menekan kaset film. Jendela web tertanam memuat halaman seperti peramban biasa: alamat IP, serta cookie, skrip, dan pelacak milik situs itu sendiri — di luar kendali aplikasi ini. Aplikasi tidak mengirim judul lagu, daftar aplikasi, atau pengaturan ke situs mana pun |

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

Beranda VHS, pemutar bawaan, dan Audio Console juga tidak mengirim apa pun ke internet:

| Data | Disimpan di | Catatan |
|---|---|---|
| Status perangkat untuk OSD (baterai, Wi-Fi tersambung & kekuatannya, sinyal seluler, Bluetooth, dering, Jangan Ganggu, alarm) | Hanya memori, hanya selama beranda tampil | API publik tanpa izin lokasi: nama Wi-Fi tidak dibaca, `READ_PHONE_STATE` tidak diminta. Penerima & callback dilepas saat beranda tertutup |
| Ikon notifikasi di OSD | Hanya memori | Ikon kecil + jumlah dari layanan akses notifikasi yang sama dengan kontrol musik; judul dan isi notifikasi tidak dibaca. Bisa dimatikan (editor beranda → *Status & jam*) |
| Indeks pustaka musik lokal (`local_library.json`) | Folder internal aplikasi | Judul, artis, album, nomor lagu, durasi, format, ukuran, waktu ubah, dan ID dokumen SAF per berkas — agar pemindaian ulang cepat. Berkas musik tidak disalin, diubah, atau diunggah. Dihapus saat folder dilepas (*Lepas*) |
| Level baterai earphone yang diingat (`bt_battery`) | `SharedPreferences` internal | Paling banyak 16 perangkat (yang terbaru); laporan lebih tua dari 3 hari tidak ditampilkan. Kuncinya 16 karakter awal SHA-256 alamat Bluetooth, **bukan** alamatnya; isinya hanya level & waktu laporan. Catatan jujur: ruang alamat Bluetooth kecil, jadi orang yang bisa membaca berkas ini (butuh akses root; cadangan Android dimatikan) secara teori bisa menebak alamatnya dari sidik itu |
| Profil EQ & pengaturan pemutar | DataStore (bersama pengaturan lain) | Ikut berkas cadangan **kecuali** folder musik (URI folder melekat pada perangkat dan izin SAF-nya) |

Laci kaset film di meja (Tahap 13) tidak menambah izin dan tidak mengirim data pengguna:

| Data | Disimpan di | Catatan |
|---|---|---|
| Daftar kaset film (nama, alamat `https`, warna; maks 24) | DataStore (bersama pengaturan lain) | Ikut berkas cadangan agar bisa dipindah. Dibersihkan ulang saat dibaca atau diimpor: hanya `https`, tanpa alamat IP/`localhost`/info login di alamat, maks 500 karakter |
| Cookie, penyimpanan lokal, dan cache situs film | Folder internal WebView milik aplikasi ini | Tidak ikut cadangan (allowBackup mati). Cookie pihak ketiga dimatikan. Hapus lewat Pengaturan → *Laci kaset* → *Hapus cookie & data situs film* |
| Posisi dan durasi film yang sedang tampil | Memori (titik lanjut: baris berikutnya) | Dibaca tiap detik lewat skrip tetap milik aplikasi, hanya selama film tampil, untuk menampilkan waktu dan menggeser; tidak dikirim |
| Titik lanjut kaset film (halaman terakhir, posisi, durasi, cap waktu; satu per kaset) | DataStore terpisah (`vinyl_film_resume`) | Hanya alamat `https` halaman terakhir (selalu di situs kaset itu sendiri, diperiksa ulang tiap dipakai, maks. 2.000 karakter), angka posisi dan durasi, dan waktu pencatatan; tanpa isi halaman. Lokal, **tidak** ikut berkas cadangan, tidak dikirim ke mana pun. Ditulis tiap 15 detik selama film tampil, saat meninggalkan layar, dan sebelum ganti kaset; titik yang lebih tua dari 180 hari dibuang. Dimatikan lewat *Ingat tempat terakhir*, dihapus lewat *Lupakan posisi terakhir* di Pengaturan → *Laci kaset*, dan ikut terhapus oleh *Hapus cookie & data situs film* |
| Rekor game Ular dan Bata (kaset game di laci) | DataStore (bersama pengaturan lain) | Satu bilangan bulat per game; lokal, tidak dikirim ke mana pun. Ikut berkas cadangan; saat dibaca atau diimpor dipaksa ke 0–99.999 (`FilmRules.MAX_SNAKE`, `FilmRules.MAX_BREAKOUT`), jadi cadangan yang diubah tangan tidak bisa menyimpan nilai aneh. Cadangan lama tanpa rekor Bata tetap terbaca (bidangnya berbawaan 0). Hanya dinaikkan oleh game; Pengaturan → *Laci kaset* → *Setel ulang rekor Ular* / *Setel ulang rekor Bata* (dengan konfirmasi) mengembalikannya ke nol |
| Papan game yang sedang berjalan | Hanya memori | Tidak disimpan; hilang bila proses dimulai ulang |
| Keadaan daya TV | Memori; satu nilai ya/tidak di `SharedPreferences` privat `tv_power` hanya bila *Ingat tombol daya TV* menyala | Pilihan itu opsional dan **mati** secara bawaan (editor beranda → *TV & kaset*). Nilainya hanya "TV terakhir dimatikan dengan tombol dayanya"; tidak ikut berkas cadangan maupun preset (itu keadaan, bukan pengaturan) dan tidak dikirim ke mana pun. Berkasnya baru dibuat saat TV dimatikan dengan pilihan itu menyala; bila pilihannya dimatikan, yang tersimpan kembali "menyala". Pilihannya sendiri (`rememberTvPower`) adalah kebiasaan perangkat: ikut cadangan, tetapi menerapkan preset tidak mengubahnya |

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
| Baterai Bluetooth | Siaran `BATTERY_LEVEL_CHANGED` (jalur tidak resmi, @SystemApi) hanya didaftarkan setelah pengguna memberi izin Perangkat sekitar. Penerimanya harus diekspor (pengirimnya proses Bluetooth sistem), jadi isinya divalidasi: level 0–100, alamat harus berbentuk alamat Bluetooth, maks 16 perangkat; ditampilkan hanya untuk perangkat keluaran aktif dengan alamat yang sama. Level terakhir disimpan dengan sidik SHA-256 alamat (bukan alamatnya), maks 16 perangkat. Aplikasi lain yang mengirim siaran palsu paling jauh hanya bisa mengubah angka baterai yang tampil |
| Pemutar bawaan | `PlayerService` tidak diekspor: aplikasi lain tidak bisa mengikatnya. Kendali dari luar hanya lewat sesi media platform (notifikasi, layar kunci, headset/Bluetooth), seperti aplikasi musik lain. Permintaan "putar item" dari pengendali hanya menerima **ID lagu yang ada di indeks**; URI yang dikirim pengendali diabaikan, jadi pemutar tidak bisa disuruh membuka berkas/URL sembarang. Folder musik lewat SAF (izin baca persisten hanya untuk folder itu; dilepas saat ganti/lepas folder, kecuali masih dipakai video). Pemindaian dibatasi 10.000 berkas dan 8 tingkat subfolder; indeks rusak/terlalu besar (> 64 MB) diabaikan. Antrean yang diingat (`QueueStore`) hanya berisi id lagu, id lagu terakhir, dan posisi; terikat pada folder musik yang dipilih, tidak ikut cadangan (allowBackup mati), teks rusak diabaikan, id dibatasi 10.000 × 1.000 karakter, dan saat dilanjutkan tiap id diperiksa ulang terhadap indeks |
| Impor AutoEQ | Berkas dipilih pengguna (SAF), dibaca maks 32.000 karakter di thread latar; maks 20 pita, frekuensi/penguatan/Q dibatasi ke rentang aman, filter yang tidak dikenal dilewati dan dilaporkan |
| Laju sampel USB | Hanya `setPreferredMixerAttributes` untuk uid aplikasi ini, `USAGE_MEDIA`, dan DAC USB (batasan Android); perilaku mixer *default* (volume sistem tetap berlaku). Dikembalikan ke pengaturan sistem saat fitur dimatikan atau layanan pemutar berhenti |
| Shizuku | `ShizukuProvider` dilindungi `INTERACT_ACROSS_USERS_FULL` (hanya sistem/Shizuku). `ShellService` (proses shell) hanya punya satu operasi tetap: `pm grant com.vinyl.launcher android.permission.DUMP`; AIDL-nya tidak menerima teks/perintah (dijaga `PrivacyInfoTest`). Dijalankan hanya setelah dialog yang menampilkan perintahnya disetujui |
| Video di layar TV | Hanya folder yang dipilih pengguna (Storage Access Framework, izin baca persisten untuk folder itu saja; dilepas saat ganti/lepas folder). Hanya nama & jenis berkas tingkat teratas yang dibaca (maks 2000). Diputar tanpa suara, tanpa audio focus, maks 720p, hanya selama ruang TV tampil. URI folder tidak ikut berkas cadangan |
| Laci kaset: jendela web | Satu WebView, hanya dibuat saat kaset film dipakai dan dihancurkan saat kaset musik kembali ke deck atau beranda ditutup. JavaScript menyala (situs film memerlukannya) tetapi **tanpa jembatan** `addJavascriptInterface`: halaman tidak bisa memanggil kode aplikasi. Hanya `https` (`http` dinaikkan ke https; skema lain, alamat IP, `localhost`, dan alamat > 8192 karakter diblokir di setiap pindah halaman, termasuk pengalihan). Akses berkas & konten dimatikan, permintaan lokasi/kamera/mikrofon/izin lain ditolak, unduhan diabaikan, pemilih berkas tidak ada, jendela baru tidak dibuka, dialog JavaScript dibatalkan, cookie pihak ketiga dimatikan, konten campuran tidak pernah diizinkan, Safe Browsing menyala, dan galat sertifikat SSL selalu dibatalkan (tanpa tombol "lanjutkan"). Bila proses render halaman mati, WebView dibuang dan layar menampilkan "sinyal hilang" (ketuk untuk mencoba lagi) — aplikasi tidak ikut mati |
| Laci kaset: pembaca video | Aplikasi membaca dan mengendalikan `<video>` dengan skrip tetap (`FilmProbe`) lewat `evaluateJavascript`; hanya bilangan bulat yang disisipkan ke skrip (tidak ada teks dari halaman maupun pengguna). Jawaban halaman tidak dipercaya: harus tepat empat bilangan bulat yang masuk akal (maks ±100 jam, panjang ≤ 64), selain itu dibuang. Diuji unit (`FilmProbeTest`, `FilmRulesTest`) |
| APK yang dibagikan (release) | Build `release` memakai R8 (kode dipangkas dan nama diacak) dan penyusutan resource; `debuggable` mati, dan CI memeriksanya pada APK yang terpasang di emulator (bendera paket dari `dumpsys package`) sebelum rilis diterbitkan. Ditandatangani dengan kunci yang sama seperti build debug (lihat *Kunci tanda tangan* di bawah; tidak ada yang berubah untuk pemasangan Anda). Id paket, tag log, dan format cadangan tidak berubah (serializer model pengaturan dipertahankan oleh aturan R8 dan dibuktikan uji "proses dimatikan lalu dibuka lagi" di emulator); nama kelas di dalam APK diacak, dan jejak tumpukan dibaca ulang dengan `mapping.txt` yang diunggah CI sebagai artifact. Cadangan bila ada perangkat yang bermasalah: `TVLauncher-debug.apk` di rilis yang sama. Catatan jujur: R8 hanya mempersulit pembacaan, bukan perlindungan; seluruh kode aplikasi ini terbuka di repositori |
| Rantai pasok build | Font diunduh dari `google/fonts` pada **satu commit tetap** (`9862abc…`, bukan cabang `main` yang terus berubah) dan isi setiap berkas diperiksa SHA-256-nya; yang tidak cocok dibuang dan build gagal. Semua action GitHub (`checkout`, `setup-java`, `upload-artifact`, `android-emulator-runner`) dirujuk lewat hash commit penuh (nomor versinya ada di komentar), dan distribusi Gradle dikunci dengan `distributionSha256Sum` (nilai dari halaman rilis resmi Gradle untuk 8.13; wrapper memeriksanya saat mengunduh). Memperbarui salah satunya sengaja manual: ganti commit/hash bersama-sama dan lihat hasil CI |
| Komponen | `BootReceiver` tidak diekspor (siaran boot/pembaruan dikirim sistem). `PlayerButtonReceiver` (tombol media setelah proses dimatikan) diekspor karena sistem yang mengirim tombol media; ia menyalakan `PlayerService` HANYA bila ada antrean tersimpan, jadi siaran `MEDIA_BUTTON` dari aplikasi lain tanpa antrean diabaikan (dengan antrean, paling jauh memutar kembali lagu pengguna sendiri; di Android 12+ latar belakang tetap dibatasi sistem). Yang tetap diekspor memang harus: Home dan Onboarding (dipanggil sistem/peluncur), serta layanan notifikasi & aksesibilitas yang hanya bisa di-bind sistem lewat izin `BIND_*` |

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
   Ringkasan dan log run juga mencetak *Sertifikat APK (SHA-256)*; nilainya harus sama dengan sidik jari keystore Anda
   (`keytool -list -v`) dan tidak berubah antar-build.
7. Di tablet: uninstall Vinyl Launcher lama, pasang APK baru, lalu Pengaturan → *Cadangan & privasi* → **Pulihkan dari berkas**.
8. Hanya bila repo Anda masih menyimpan kunci lama (repo `vinyllauncher` yang lama): hapus dari pohon kerja dengan `git rm app/vinyl.keystore`
   (riwayat Git tetap berisi kunci lama — itu sebabnya langkah 2–7 perlu). Di Vinyl-2.0 langkah ini tidak diperlukan.

Alternatif tanpa uninstall adalah *rotasi kunci* APK Signature Scheme v3 lewat `apksigner rotate` (didukung Android 9+; minSdk proyek 29).
Itu butuh langkah penandatanganan manual di luar build ini dan tidak diotomatiskan di sini; lihat dokumentasi resmi Android
"Key rotation" bila ingin menempuhnya.

## Batasan yang diketahui

- Tidak ada pemindaian dependensi otomatis; perbarui versi di `gradle/libs.versions.toml` secara berkala.
- Kunci hash hanya mencakup font, action GitHub, dan distribusi Gradle. Dependensi Maven dan plugin Gradle masih dirujuk lewat nomor versi (`gradle/libs.versions.toml`) tanpa `verification-metadata.xml`, jadi isi artefaknya belum diperiksa hash (membuat berkasnya butuh build penuh). Karena kunci dibuat manual, pembaruan keamanan action/font/Gradle tidak datang otomatis (tanpa Dependabot): periksa berkala.
- Visualizer *Audio asli* memakai sesi audio 0 (keluaran sistem). Beberapa versi Android/pabrikan membatasi atau menolak efek pada sesi ini;
  bila ditolak, Pengaturan dan layar utama menyatakannya. Belum diuji di perangkat nyata mana pun.
- Equalizer Audio Console bekerja per sesi audio aplikasi lain (DynamicsProcessing). Aplikasi musik yang tidak membuka sesi efek
  hanya terjangkau lewat Akses lanjutan; efek "semua audio" (sesi 0) sudah usang di Android dan diberi label eksperimental.
  Bisa bentrok dengan efek pabrikan (Dolby/Samsung) atau aplikasi EQ lain; status sesi ditampilkan apa adanya. Belum diuji di perangkat nyata.
- Format `dumpsys` milik OEM (mis. Samsung One UI) bisa berbeda dari AOSP; field yang tidak cocok tampil "Tidak tersedia". Belum diverifikasi
  di perangkat Samsung nyata. Baterai earphone tidak berasal dari laporan ini, melainkan dari siaran sistem tidak resmi (lihat tabel di atas).
- Pemutar bawaan, laju sampel USB, OSD, dan penyembunyian status bar belum diuji di perangkat nyata (hanya tes unit, tangkapan layar, build CI).
- Video YouTube sengaja tidak dibangun: Kebijakan Pengembang YouTube API Services III.I butir 7 melarang memisahkan atau
  mengisolasi komponen audio/video konten YouTube (https://developers.google.com/youtube/terms/developer-policies), padahal
  video di layar TV diputar tanpa suara sementara musik berasal dari aplikasi lain.
- Game Ular dan Bata tidak memakai jaringan, izin, atau pustaka tambahan: seluruh permainan, gambar, dan bunyinya dihitung dan disintesis di dalam aplikasi. Belum diuji di perangkat nyata
  (hanya tes unit mesin game, tangkapan layar, build CI, dan uji asap di emulator); kenyamanan usapan dan geser palet serta kecepatannya perlu dirasakan langsung.
  Hal yang sama berlaku bagi lampu dinding (tanpa izin, tanpa data baru selain pilihan *Ruang* yang sudah ada), *Ingat tombol daya TV*, dan melanjutkan film.
- Laci kaset film belum diuji di perangkat nyata (hanya tes unit untuk aturan alamat, pembaca video, dan alur kaset; tangkapan layar dengan
  halaman rekaan; build CI). Layanan streaming berlangganan dengan perlindungan DRM (Widevine L1, dsb.) sering menolak diputar di dalam
  WebView aplikasi lain; kaset bawaan sengaja hanya menunjuk sumber legal. Isi, iklan, dan pelacak halaman film sepenuhnya milik situs
  yang dibuka; aplikasi ini tidak menyaring isi halaman, hanya membatasi apa yang boleh dilakukan halaman terhadap aplikasi dan perangkat.
- Overlay (layar kunci, taskbar, tombol navigasi) memerlukan izin "tampil di atas aplikasi lain"; ini inheren pada fiturnya.

## Melapor kerentanan

Buka *Security advisory* privat di tab Security repositori GitHub ini, atau issue tanpa detail eksploit bila fiturnya tidak tersedia.
