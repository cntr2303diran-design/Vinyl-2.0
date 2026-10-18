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
| Spasi, radius, gerak di layar lain | **Belum dimigrasikan**: masih angka langsung di kode (mis. pegas tonearm dan sampul). Nilai pegas bawaan `Tokens.Motion.SPRING_*` belum dipakai siapa pun |

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
