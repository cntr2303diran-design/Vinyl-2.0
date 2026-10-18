# Bahasa desain Vinyl

Satu bahasa desain yang konsisten, bukan tiruan satu produk. Yang diambil dari tiap rujukan hanya **prinsipnya**
(tanpa logo, ikon, atau aset bermerek).

## Prinsip yang dipakai

| Prinsip | Dari | Mengapa dipilih untuk Vinyl | Di mana terlihat |
|---|---|---|---|
| Warna dinamis dari konten, bentuk bulat, tipografi jelas | Material 3 / Pixel Launcher | Cover album sudah menjadi sumber warna alami; bentuk bulat selaras dengan piringan hitam | Latar ambient dari cover, pil/chip, tombol bulat, Inter sebagai satu-satunya keluarga teks |
| Hierarki visual kuat, ruang lega, gerak pegas halus | Apple HIG | Tablet besar butuh satu fokus (turntable) dan jeda visual; gerak yang terasa fisik mendukung metafora tonearm | Judul besar, jarak 32dp (`Dimen.xxl`), drawer meluncur + memudar dengan easing `Tokens.Motion`; pegas pada sampul dan tonearm |
| Tata letak yang terjangkau jempol, kontras nyaman | Samsung One UI | Tablet dipegang dua tangan; aksi utama harus dekat tepi | Jump bar di tepi, kolom cari di atas tapi bisa ditarik turun untuk menutup, target sentuh ≥ 48dp |
| Material tembus pandang yang halus, kedalaman lewat lapisan | Microsoft Fluent | Memberi kedalaman tanpa bayangan berat; cocok dengan latar ambient | Permukaan "kaca" gelap (alpha 0,28) + garis tepi tipis, scrim di bawah panel |

**Aturan penengah bila prinsip bertabrakan: keterbacaan menang atas estetika.** Contohnya, "kaca" di atas latar cover memakai
lapisan **gelap** tembus pandang, bukan putih: lapisan putih mengangkat kecerahan latar dan merusak jaminan kontras.

## Token

Semua nilai ada di satu tempat: [`ui/theme/DesignTokens.kt`](app/src/main/java/com/vinyl/launcher/ui/theme/DesignTokens.kt)
(warna, spasi, radius, durasi & easing, batas aksesibilitas) — Kotlin murni agar bisa diuji.
[`TokenUnits.kt`](app/src/main/java/com/vinyl/launcher/ui/theme/TokenUnits.kt) hanya mengubahnya menjadi `Dimen`, `Corner`, dan `Ease` untuk Compose.

**Status pemakaian (jujur):**

| Token | Dipakai oleh |
|---|---|
| Warna (`Tokens.Argb`) | Seluruh aplikasi, lewat `Theme.kt` |
| Spasi, radius, easing, durasi (`Dimen`, `Corner`, `Ease`, `Tokens.Motion`) | App drawer dan animasi buka/tutupnya |
| Radius di layar lain | **Sebagian.** Semua `RoundedCornerShape` yang nilainya persis 12 / 16 / 26 dp kini memakai `Corner.s / m / l` (16 tempat, tampilan tidak berubah). Sisanya (6, 8, 9, 10, 13, 14, 20, 22, 24, 28 dp; 18 tempat) masih angka langsung: memetakannya ke token berarti mengubah tampilan, jadi menunggu keputusan desain |
| Durasi gerak di layar lain | **Sebagian.** `tween(500)` pada turntable memakai `Tokens.Motion.SLOW_MS`. Durasi lain (260–800 ms, mis. 420 / 650 / 800) adalah koreografi khusus turntable, jam, dan lirik; pegas (`spring(...)`) per interaksi juga sengaja khusus. `Tokens.Motion.SPRING_*` belum dipakai siapa pun |
| Spasi (padding) di layar lain | **Belum dimigrasikan** (±130 tempat dengan nilai campur) |

## Kontras (WCAG AA, ≥ 4,5 : 1) — dibuktikan oleh tes, bukan hanya dikira-kira

- `ContrastTest`: semua tingkat teks di semua permukaan; pasangan aksen; panel di atas latar putih; latar ambient dari **warna apa pun**.
- `BackdropDimTest`: latar dari cover (termasuk cover putih) digelapkan **secukupnya** oleh `BackdropDim` sehingga teks utama dan
  sekunder tetap ≥ 4,5 : 1. Cover gelap tidak digelapkan lebih dari yang diminta.
- Di atas latar cover hanya `TextPrimary` dan `TextSecondary` yang dijamin; `TextTertiary` hanya untuk permukaan buram (kartu).
- **Permukaan kaca ikut dihitung.** Isi kaca putih membuat latar di bawah teks sedikit lebih terang, dan pada cover putih itu menurunkan
  kontras ke ±3,5–4,1 : 1 bila peredam hanya memikirkan latar polos. Karena itu `BackdropDim.minOverlayAlpha` mengukur kontras
  di atas `withGlass(latar, GLASS_VEIL = 0,16)`, dan `Modifier.glass` membatasi isinya ke `MAX_GLASS_FILL = 0,12` (sisa 0,04 untuk kilau atas).
  Teks yang dijamin pada kartu/kapsul kaca di atas latar cover: `TextPrimary`, `TextSecondary`, dan teks lirik tak aktif.
- Lirik layar penuh memakai daftar teks sendiri (`LyricsFocus.BACKDROP_TEXTS`) karena baris tak aktif lebih redup daripada teks sekunder.
  Baris jauh yang dikaburkan adalah efek fokus yang bisa dimatikan (Pengaturan → Musik); teks aslinya tetap memenuhi kontras.
- **Efek visual ikut dihitung (Tahap 4).** Peredam mengukur kontras pada jalur render yang sebenarnya:
  `latar → withDist → peredam (darken) → withFx → withGlass`. `withDist` mengalikan warna latar paling banyak `1 + DIST_PEAK (0,03)`
  karena distorsi kaca terjadi di lapisan latar, SEBELUM peredam; ini perkalian, bukan putih (kanal terang naik lebih banyak dari kanal gelap),
  jadi dihitung terpisah. `withFx` menimpa putih `FX_VEIL = 0,12` untuk dua lapisan efek yang menumpuk (cahaya + partikel, masing-masing
  alpha ≤ `FX_PEAK = 0,06`; 1 − 0,94² = 0,116). Layar yang tidak pernah menggambar efek (lirik, app drawer, rak lagu) memanggil
  `minOverlayAlpha(..., effects = false)` agar tidak digelapkan lebih dari perlu; layar utama memakai bawaan yang aman (`effects = true`).
  **Cakupan jaminan:** hanya latar mode *Album*, yang peredamnya dihitung dari piksel terterang cover. Latar *Warna*, *Gradien*, dan *Gambar*
  dipilih pengguna dengan redaman tetap (seperti sebelum Tahap 4); efek menambah paling banyak ±12% putih di atasnya, tanpa jaminan kontras.
- Lencana di atas sampul album (yang warnanya bebas) memakai alas gelap, bukan kaca putih.
- Teks di atas warna aksen selalu memakai `AccentOn` (satu-satunya pasangan aksen yang dibuktikan).

## Efek visual (Tahap 4)

Satu keputusan, `FxPlan`, mengubah (pengaturan + lingkungan) menjadi apa yang boleh jalan; tidak ada penghitungan terpisah di tiap layar.

| Lapisan (bawah → atas) | Isi | Catatan |
|---|---|---|
| Latar jauh | Cover buram + gumpalan warna | Digeser parallax dan, Android 13+, dibiaskan shader AGSL (`GlassDistortion`) — hanya lapisan ini, tidak pernah teks atau kontrol, jadi area sentuh tidak bergeser |
| Penggelap sinematik + peredam | Hitam beralpha `BackdropDim` | Menjamin kontras (lihat di atas) |
| Efek depan | Cahaya reaktif (`ReactiveGlow`, AGSL; cadangan Canvas) dan partikel | Di atas peredam, di bawah konten; alpha ≤ `FX_PEAK` |
| Konten | Turntable, jam, lirik, kontrol | Tidak terkena efek |

- **Parallax** memakai rotation vector (giroskop) bila ada, lalu gravitasi/akselerometer. Rotasi dihitung relatif terhadap pose pegangan santai
  (baseline menyusul pelan, ±2,5 dtk) dan dipetakan ke sumbu LAYAR untuk keempat rotasi layar. Mode *Seperti jendela* memakai arah mata; *Seperti melayang* membalik.
  Pergeseran maksimum kecil agar tidak memusingkan.
  **Batasan:** tanpa giroskop, putaran terhadap sumbu tegak saat ponsel berdiri tidak terbaca (gravitasi buta terhadapnya); arah pada perangkat nyata
  belum diverifikasi — bila terasa terbalik, pilih mode satunya.
- **Visualizer**: *Audio asli* (`android.media.audiofx.Visualizer`, hanya FFT) atau *Animasi dekoratif* yang diberi label jelas dan tidak mengikuti suara.
  Bila Audio asli gagal (izin ditolak, perangkat menolak), layar utama menulis alasannya — **tidak pernah diganti animasi tiruan**.
- **Berhenti otomatis** bila: animasi sistem dimatikan (*Hapus animasi*), Penghemat baterai aktif, layar utama tertutup/tidak terlihat, atau (untuk
  partikel dan visualizer) tidak ada musik yang diputar. Pilihan pengguna tidak diubah; halaman Pengaturan menjelaskan alasannya.
- Batang visualizer dekoratif bagi TalkBack (`clearAndSetSemantics`); keterangan teksnya yang dibacakan.

## Aksesibilitas yang dibangun ke dalam komponen

- Target sentuh ≥ 48dp; pil kecil (chip) dibungkus area sentuh 48dp.
- Setiap kontrol punya label; gestur tekan-lama selalu punya padanan `customActions` untuk TalkBack.
- Jump bar A–Z dijadikan satu kontrol yang bisa disesuaikan (geser naik/turun) untuk pembaca layar, karena 27 huruf tidak mungkin
  masing-masing 48dp; kolom cari dan chip kategori adalah jalur utamanya.
- Skala font 200%: label ikon boleh dua baris; huruf jump bar sengaja tidak ikut skala (dekoratif, ada jalur lain).

---

## Tema "Retro Projection" (TV tabung) — branch `retro-tv`

Tema baru menggantikan identitas piringan hitam dengan TV tabung di ruang putih, dinding proyeksi, kaset, knob, dan VFD.
Semua fitur Vinyl tetap ada (lihat tabel pemetaan di `claude/PROMPT-RetroTV-Launcher.md` di Project).

### Prinsip yang dipadukan

| Prinsip | Sumber | Dipakai untuk |
|---|---|---|
| Warna dinamis dari konten, bentuk bulat, tipografi jelas | Material 3 / Pixel | Warna proyeksi & cahaya layar diambil dari cover; sudut bodi TV/kaset membulat |
| Hierarki kuat, ruang lega, gerak pegas halus | Apple HIG | Ruang putih yang lapang dengan satu fokus (TV); kaset & knob memakai pegas |
| Kontrol di jangkauan jempol, kontras nyaman | Samsung One UI | Kaset & knob di separuh bawah layar; console gelap dengan teks krem |
| Kedalaman lewat lapisan, material tembus pandang subtil | Microsoft Fluent | Lantai–TV–dinding sebagai lapisan; kaca CRT dengan pantulan tipis |
| Mekanis & taktil | Perangkat analog (umum, tanpa merek) | Tombol kaset tertekan, knob berdetak, VFD bercahaya |

### Token (`ui/theme/DesignTokens.kt` → `Tokens.Retro`, `Tokens.Motion`, `Tokens.A11y.LIGHT_SHADE_PEAK`)

| Token | Nilai | Pemakaian |
|---|---|---|
| `WARM_WHITE` | `#F7F6F2` | Dinding & lantai |
| `CREAM_BEIGE` | `#C5AD87` | Bodi TV, label kaset |
| `WALNUT` | `#684832` | Panel kayu TV & rak |
| `CHARCOAL` | `#343633` | Tombol, detail, latar Audio Console |
| `AMBER` | `#D6A64D` | Lampu indikator, VFD amber |
| `MUTED_GREEN` | `#82977A` | Aksen saja — **bukan teks** di atas charcoal (3,8:1, dibuktikan tes) |
| `INK` / `INK_SECONDARY` / `INK_TERTIARY` | `#1E1D1B` / `#55524C` / `#67635C` | Teks di ruang terang |
| `CREAM_TEXT` / `CREAM_TEXT_SECONDARY` | `#EDE3D1` / `#C9BEAA` | Teks di panel gelap |
| `VFD_GLASS` + `VFD_AMBER` / `VFD_GREEN` | `#141613` + `#D6A64D` / `#9FC29A` | Karakter VFD |

Gerak: `CRT_POWER_ON_MS` 420, `CRT_POWER_OFF_MS` 360, `CASSETTE_EJECT_MS` 280, `CASSETTE_INSERT_MS` 340,
`CASSETTE_RAPID_MS` 120 (skip beruntun), `KNOB_ZOOM_IN_MS` 320 / `OUT` 260, `PROJECTION_CROSSFADE_MS` 900,
`PAGE_SLIDE_MS` 380, `VFD_CYCLE_MS` 4000, `VFD_PRIORITY_MS` 3000, gulungan 40/60 rpm.

### Kontras pada tema terang

Ruang utama terang, jadi teks gelap. Ancaman kontras bukan lagi latar yang terlalu terang, melainkan **proyeksi
gelap/berwarna dan efek yang menggelapkan** (vignette CRT, bayangan kontak, sudut ruangan). `BackdropLift`
(`ColorMath.kt`) menghitung alpha lapisan putih terkecil agar `INK` dan `INK_SECONDARY` tetap >= 4,5:1 (+0,15
cadangan) setelah penggelapan terburuk `LIGHT_SHADE_PEAK` = 0,10. `BackdropLiftTest` membuktikannya di 224 warna latar
(grid seluruh ruang warna + warna sulit) dan membuktikan pencarian biner aman. Saat diuji, `INK_TERTIARY` lama
(`#6E6A63`) hanya 4,19:1 di sudut ruangan, jadi diganti `#67635C` (4,66:1).

### Migrasi pengaturan

`AppSettings.retro` (`data/RetroSettings.kt`) menyimpan gaya TV tabung. Field vinyl lama tidak dihapus atau diganti
nama. Dokumen tanpa kunci `retro` (pengaturan & cadangan dari versi vinyl) dimigrasi oleh `SettingsMigration`:
piringan klasik/berwarna/transparan/bergambar → kaset hitam/berwarna (TV space-age)/transparan/bergambar, tonearm
perak/hitam/emas → knob krom/bakelite/kuningan, 45 RPM → gulungan cepat. Preset lama mendapat padanannya saat diterapkan.
Dibuktikan `SettingsMigrationTest`.

### Audio Console (halaman kanan)

- Gaya receiver stereo tanpa merek: halaman `CHARCOAL_DEEP`, panel `CHARCOAL` sudut 14dp, lampu indikator amber per panel
  (menyala bila fitur panel aktif), fader EQ dengan tutup krem bergaris amber.
- Kontras teks di atas panel `#343633`: krem `#EDE3D1` 9,6:1, krem sekunder `#C9BEAA` 6,6:1, amber `#D6A64D` 5,5:1,
  hijau VFD `#9FC29A` 6,2:1.
- Setiap nilai memakai `ReadingRow`: label, nilai (huruf mono bila ada), label sumber (Terukur / Dilaporkan sistem hijau,
  Perkiraan amber), dan alasan/asal. Nilai "Tidak tersedia" tidak diberi label sumber ganda.
- Tata letak: ≥ 900dp tiga kolom, ≥ 600dp dua kolom, selain itu satu kolom bergulir. Semua baris bertumpuk agar tetap
  terbaca pada ukuran huruf 200%.
- RTL: panel dicerminkan, tetapi sumbu frekuensi EQ dan balance kiri/kanan tetap kiri→kanan (kanal fisik). Teks memakai
  `TextDirection.Content` di tipografi aplikasi, jadi teks Latin seperti "-4 dB" tetap terbaca benar.
- Target sentuh ≥ 48dp (tombol, sakelar baris penuh, fader 48×150dp). Fader dan balance adalah penggeser TalkBack
  (`setProgress`) dengan aksi "Naik/Turun 1 dB" dan "Atur ke 0 dB"/"Tengah".
