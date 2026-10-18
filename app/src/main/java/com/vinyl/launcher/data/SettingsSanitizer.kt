package com.vinyl.launcher.data

import com.vinyl.launcher.drawer.AppCategorizer
import com.vinyl.launcher.music.SleepTimerPlan

/*
 * Pembersih pengaturan: memastikan nilai apa pun yang masuk (dibaca dari penyimpanan atau diimpor dari
 * berkas cadangan buatan pihak lain) aman dipakai UI — rentang angka dibatasi, daftar dipotong,
 * teks dipendekkan, path gambar hanya boleh menunjuk folder internal aplikasi, dan nama paket/komponen
 * harus berbentuk wajar. Batasnya sengaja lebih longgar dari rentang slider di UI supaya pengaturan
 * sah tidak pernah berubah; tujuannya mencegah crash dan nilai tak masuk akal, bukan menyeragamkan.
 *
 * Fungsi murni tanpa dependensi Android sehingga bisa diuji unit.
 */
object SettingsSanitizer {
    /** Jumlah titik per gestur gambar (harus sama dengan GestureRecognizer.N). */
    const val GESTURE_POINTS = 48

    const val MAX_WIDGETS = 12
    const val MAX_WIDGET_IMAGES = 24
    const val MAX_CUSTOM_GESTURES = 24
    const val MAX_PRESETS = 30
    const val MAX_HIDDEN_APPS = 1000
    const val MAX_NAME = 60
    const val MAX_CITY = 80
    /** Tombol navigasi tidak boleh lebih kecil dari target sentuh minimum (dp). */
    const val MIN_NAV_BUTTON = 48
    const val MIN_FREQUENT = 3
    const val MAX_FREQUENT = 10
    const val MAX_CATEGORY_OVERRIDES = 500
    const val MAX_FAVORITES = 1000
    const val MAX_SONG_KEY = 600
    /** Parallax terlalu lemah tidak terlihat; di bawah ini dianggap salah dan dinaikkan. */
    const val MIN_PARALLAX = 0.1f

    // ---------------------------------------------------------------- publik

    /**
     * @param isAllowedImage menjawab apakah sebuah path gambar boleh dipakai (di Android: file ada dan
     * berada di folder internal aplikasi). Default menolak semua gambar.
     */
    fun sanitize(s: AppSettings, isAllowedImage: (String) -> Boolean = { false }): AppSettings = s.copy(
        home = home(s.home, isAllowedImage),
        cover = cover(s.cover, isAllowedImage),
        gestures = s.gestures.copy(
            swipeUp = binding(s.gestures.swipeUp),
            swipeDown = binding(s.gestures.swipeDown),
            doubleTap = binding(s.gestures.doubleTap),
        ),
        dock = dock(s.dock),
        nav = nav(s.nav),
        drawer = drawer(s.drawer),
        music = s.music.copy(
            sleepMinutes = SleepTimerPlan.clampMinutes(s.music.sleepMinutes),
            favorites = s.music.favorites.map { it.text(MAX_SONG_KEY) }.filter { it.isNotEmpty() }.take(MAX_FAVORITES).toSet(),
        ),
        visual = s.visual.copy(parallaxStrength = s.visual.parallaxStrength.fix(MIN_PARALLAX, 1f, 0.6f)),
        retro = retro(s.retro),
        customGestures = s.customGestures
            .filter { g -> g.points.size == GESTURE_POINTS * 2 && g.points.all { it.isFinite() && it in -2f..2f } }
            .distinctBy { it.id }
            .take(MAX_CUSTOM_GESTURES)
            .map { g -> g.copy(id = g.id.text(64), name = g.name.text(MAX_NAME), binding = binding(g.binding)) },
        weather = s.weather.copy(
            cityName = s.weather.cityName.text(MAX_CITY),
            latitude = s.weather.latitude.fix(-90.0, 90.0, 0.0),
            longitude = s.weather.longitude.fix(-180.0, 180.0, 0.0),
        ),
        effects = s.effects.copy(
            volume = s.effects.volume.fix(0f, 1f, 0.7f),
            hapticStrength = s.effects.hapticStrength.fix(0.1f, 1f, 1f),
        ),
        defaultMusicPackage = s.defaultMusicPackage?.takeIf { isPackageName(it) },
        hiddenApps = s.hiddenApps.filter { isSafeKey(it) }.take(MAX_HIDDEN_APPS).toSet(),
        presets = s.presets
            .distinctBy { it.id }
            .take(MAX_PRESETS)
            .map {
                it.copy(
                    name = it.name.text(MAX_NAME),
                    home = home(it.home, isAllowedImage),
                    cover = cover(it.cover, isAllowedImage),
                    retro = it.retro?.let(::retro),
                )
            },
    )

    /** Nama paket Android yang wajar (huruf, angka, titik, garis bawah). */
    fun isPackageName(s: String): Boolean = s.length in 1..255 && s.all { isAscii(it) || it == '.' || it == '_' }

    /**
     * Kunci komponen "paket/kelas" (boleh berisi $ untuk kelas dalam dan #id profil kerja). Urutan ".." ditolak:
     * segmen paket kosong tidak mungkin ada pada komponen asli, dan bentuk seperti "../../x" tidak boleh lolos
     * walau kunci hanya dipakai sebagai pencarian (bukan path file).
     */
    fun isSafeKey(s: String): Boolean =
        s.length in 1..512 && !s.contains("..") &&
            s.all { isAscii(it) || it == '.' || it == '_' || it == '$' || it == '/' || it == '#' || it == '-' }

    // ---------------------------------------------------------------- bagian-bagian

    private fun home(h: HomeSettings, ok: (String) -> Boolean) = h.copy(
        background = background(h.background, ok),
        turntableSize = h.turntableSize.fix(0.3f, 1.2f, 0.85f),
        rpm = if (h.rpm == 45) 45 else 33,
        clock = clock(h.clock),
        widgets = h.widgets
            .distinctBy { it.id }
            .take(MAX_WIDGETS)
            .map { w ->
                w.copy(
                    x = w.x.fix(0f, 1f, 0.75f),
                    y = w.y.fix(0f, 1f, 0.1f),
                    scale = w.scale.fix(0.4f, 3f, 1f),
                    images = w.images.filter { it.length <= 1024 && ok(it) }.take(MAX_WIDGET_IMAGES),
                )
            },
    )

    private fun cover(c: CoverSettings, ok: (String) -> Boolean) = c.copy(
        clock = clock(c.clock),
        background = background(c.background, ok),
    )

    private fun clock(c: ClockSettings) = c.copy(
        color = c.color.argb(),
        scale = c.scale.fix(0.25f, 3f, 1f),
    )

    private fun background(b: BackgroundSettings, ok: (String) -> Boolean) = b.copy(
        solidColor = b.solidColor.argb(),
        gradientStart = b.gradientStart.argb(),
        gradientEnd = b.gradientEnd.argb(),
        imagePath = b.imagePath?.takeIf { it.length <= 1024 && ok(it) },
    )

    private fun retro(r: RetroSettings) = r.copy(
        crtStrength = r.crtStrength.fix(0f, 1f, 0.6f),
        glowIntensity = r.glowIntensity.fix(0f, 1f, 0.7f),
    )

    private fun binding(b: GestureBinding) = b.copy(packageName = b.packageName?.takeIf { isPackageName(it) })

    private fun dock(d: DockSettings) = d.copy(
        apps = d.apps.filter { isSafeKey(it) }.distinct().take(MAX_DOCK_APPS),
        margin = d.margin.coerceIn(0, 120),
    )

    private fun drawer(d: DrawerSettings) = d.copy(
        frequentCount = d.frequentCount.coerceIn(MIN_FREQUENT, MAX_FREQUENT),
        // Kunci harus berbentuk kunci komponen dan nilainya kategori yang dikenal; sisanya dibuang
        categoryOverrides = d.categoryOverrides.entries
            .filter { (k, v) -> isSafeKey(k) && AppCategorizer.parse(v) != null }
            .take(MAX_CATEGORY_OVERRIDES)
            .associate { it.key to it.value },
    )

    private fun nav(n: NavSettings) = n.copy(
        x = n.x.fix(0f, 1f, 0.5f),
        y = n.y.fix(0f, 1f, 1f),
        spacing = n.spacing.coerceIn(0, 200),
        size = n.size.coerceIn(MIN_NAV_BUTTON, 120),
        opacity = n.opacity.fix(0.1f, 1f, 0.85f),
    )

    // ---------------------------------------------------------------- pembantu

    private fun isAscii(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private fun Float.fix(min: Float, max: Float, fallback: Float): Float =
        if (isNaN() || isInfinite()) fallback else coerceIn(min, max)

    private fun Double.fix(min: Double, max: Double, fallback: Double): Double =
        if (isNaN() || isInfinite()) fallback else coerceIn(min, max)

    /** Warna disimpan sebagai ARGB 32-bit; buang bit di atasnya. */
    private fun Long.argb(): Long = this and 0xFFFFFFFFL

    /** Teks satu baris: tanpa karakter kontrol, dipotong ke [max]. */
    private fun String.text(max: Int): String = filter { !it.isISOControl() }.trim().take(max)
}
