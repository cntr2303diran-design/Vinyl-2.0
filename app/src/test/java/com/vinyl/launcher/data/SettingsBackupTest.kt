package com.vinyl.launcher.data

import com.vinyl.launcher.data.SettingsBackup.ImportResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val defaults = AppSettings()
    private val allowedPrefix = "/data/user/0/com.vinyl.launcher/files/backgrounds/"
    private val allow: (String) -> Boolean = { it.startsWith(allowedPrefix) && !it.contains("..") }

    /** Berkas "buatan penyerang": ditulis langsung tanpa melewati ekspor resmi (yang membuang path gambar). */
    private val rawJson = Json { encodeDefaults = true }

    private fun craftedFile(s: AppSettings): String =
        rawJson.encodeToString(
            SettingsBackup.Envelope.serializer(),
            SettingsBackup.Envelope(SettingsBackup.FORMAT, SettingsBackup.VERSION, s),
        )

    private fun ok(r: ImportResult): AppSettings {
        assertTrue("diharapkan Ok tapi $r", r is ImportResult.Ok)
        return (r as ImportResult.Ok).settings
    }

    private fun err(r: ImportResult): String {
        assertTrue("diharapkan Error tapi $r", r is ImportResult.Error)
        return (r as ImportResult.Error).message
    }

    /** Pengaturan yang diubah di hampir semua bagian, dengan nilai yang sah. */
    private fun rich(): AppSettings {
        val pts = List(SettingsSanitizer.GESTURE_POINTS * 2) { (it % 7) / 10f - 0.3f }
        return AppSettings(
            home = HomeSettings(
                background = BackgroundSettings(type = BackgroundType.GRADIENT, solidColor = 0xFF123456, gradientStart = 0xFFAA0000, gradientEnd = 0xFF00AA00),
                turntableSize = 0.7f, turntablePosition = TurntablePosition.LEFT, vinylStyle = VinylStyle.COLORED,
                tonearmStyle = TonearmStyle.GOLD, rpm = 45,
                clock = ClockSettings(type = ClockType.FLIP, scale = 1.4f, anchor = Anchor.BOTTOM_END, use24h = false),
                widgets = listOf(
                    WidgetConfig(1, WidgetType.CALENDAR, 0.2f, 0.3f, 1.5f),
                    WidgetConfig(2, WidgetType.WEATHER, 0.6f, 0.1f, 0.9f),
                ),
            ),
            cover = CoverSettings(calendarEvents = true, lyricsVisible = false),
            gestures = GestureSettings(swipeUp = GestureBinding(GestureAction.OPEN_APP, "com.spotify.music")),
            dock = DockSettings(enabled = true, apps = listOf("com.a/com.a.Main", "com.b/.Main", "com.c/com.c.A\$Inner#12"), margin = 24, position = DockPosition.LEFT),
            nav = NavSettings(enabled = true, x = 0.1f, y = 0.9f, spacing = 40, size = 60, opacity = 0.5f),
            customGestures = listOf(CustomGesture("g1", "Lingkaran", pts, GestureBinding(GestureAction.CRATE))),
            weather = WeatherSettings("Bandung", -6.9175, 107.6191),
            effects = EffectSettings(needleSound = false, volume = 0.4f, hapticStrength = 0.6f),
            drawer = DrawerSettings(
                showFrequent = false, frequentCount = 8, showCategories = false, showBadges = false,
                searchSongs = false, searchSettings = false, searchContacts = false,
                categoryOverrides = mapOf("com.a/com.a.Main" to "GAME", "com.b/.Main" to "TOOLS"),
            ),
            defaultMusicPackage = "com.spotify.music",
            hiddenApps = setOf("com.x/com.x.Main", "com.y/.Main"),
            presets = listOf(ThemePreset(7, "Malam", HomeSettings(rpm = 45), CoverSettings())),
            coverEnabled = true, onboardingDone = true,
        )
    }

    @Test
    fun roundTrip_defaultsAreUnchanged() {
        val parsed = ok(SettingsBackup.parse(SettingsBackup.export(defaults), defaults, allow))
        assertEquals(defaults, parsed)
    }

    @Test
    fun roundTrip_richSettingsAreUnchanged() {
        val s = rich()
        val parsed = ok(SettingsBackup.parse(SettingsBackup.export(s), s, allow))
        assertEquals(s, parsed)
    }

    @Test
    fun export_stripsLocalImagePaths() {
        val s = defaults.copy(
            home = defaults.home.copy(
                background = BackgroundSettings(type = BackgroundType.IMAGE, imagePath = allowedPrefix + "bg.img"),
                widgets = listOf(WidgetConfig(1, WidgetType.PHOTO, images = listOf(allowedPrefix + "p1.img"))),
            ),
        )
        val text = SettingsBackup.export(s)
        assertFalse("path perangkat tidak boleh bocor ke berkas cadangan", text.contains("backgrounds"))
        assertFalse(text.contains("com.vinyl.launcher/files"))
    }

    @Test
    fun import_rejectsGarbageAndWrongFormats() {
        for (bad in listOf("", "   ", "{}", "[]", "null", "bukan json", "{\"format\":\"lain\",\"version\":1,\"settings\":{}}", "{\"format\":\"vinyl-settings\",\"version\":1}")) {
            err(SettingsBackup.parse(bad, defaults))
        }
    }

    @Test
    fun import_rejectsFutureOrInvalidVersion() {
        val good = SettingsBackup.export(defaults)
        val future = good.replace("\"version\": 1", "\"version\": 99")
        assertTrue(err(SettingsBackup.parse(future, defaults)).contains("lebih baru"))
        val zero = good.replace("\"version\": 1", "\"version\": 0")
        err(SettingsBackup.parse(zero, defaults))
    }

    @Test
    fun import_rejectsOversizedInput() {
        val huge = "x".repeat(SettingsBackup.MAX_BYTES + 1)
        assertTrue(err(SettingsBackup.parse(huge, defaults)).contains("terlalu besar"))
    }

    @Test
    fun import_ignoresUnknownKeys_forwardCompatibility() {
        val text = SettingsBackup.export(defaults).replaceFirst("{", "{\"fieldMasaDepan\": {\"a\": [1,2,3]},")
        ok(SettingsBackup.parse(text, defaults))
    }

    @Test
    fun import_keepsDeviceBoundState() {
        val device = defaults.copy(coverEnabled = false, onboardingDone = true, dock = DockSettings(enabled = false), nav = NavSettings(enabled = false))
        val fromOther = rich().let { it.copy(drawer = it.drawer.copy(searchContacts = true)) }   // coverEnabled=true, dock.enabled=true, nav.enabled=true, searchContacts=true
        val merged = ok(SettingsBackup.parse(SettingsBackup.export(fromOther), device, allow))
        assertFalse(merged.coverEnabled)
        assertTrue(merged.onboardingDone)
        assertFalse(merged.dock.enabled)
        assertFalse(merged.nav.enabled)
        assertFalse("izin kontak milik perangkat, bukan berkas", merged.drawer.searchContacts)
        assertEquals(fromOther.drawer.copy(searchContacts = false), merged.drawer)   // sisa pengaturan drawer ikut diimpor
        // tetapi gaya ikut diimpor
        assertEquals(fromOther.dock.apps, merged.dock.apps)
        assertEquals(fromOther.nav.spacing, merged.nav.spacing)
        assertEquals(45, merged.home.rpm)
    }

    @Test
    fun import_sanitizesHostileValues() {
        val hostile = defaults.copy(
            home = defaults.home.copy(
                turntableSize = 99f, rpm = 78,
                background = BackgroundSettings(imagePath = "/data/data/aplikasi.lain/files/rahasia.db"),
                clock = defaults.home.clock.copy(scale = -5f, color = 0x7FFFFFFFFFFFL),
                widgets = List(40) { WidgetConfig(it.toLong(), WidgetType.PHOTO, x = 50f, y = -3f, scale = 1000f,
                    images = listOf("/etc/passwd", allowedPrefix + "ok.img", allowedPrefix + "../../x")) },
            ),
            dock = DockSettings(apps = List(30) { "p$it/.A" } + listOf("bad key with spaces", "../../etc"), margin = 99999),
            nav = NavSettings(x = 9f, y = -9f, spacing = 99999, size = 1, opacity = 50f),
            drawer = DrawerSettings(
                frequentCount = 9999,
                categoryOverrides = mapOf("ok/.Main" to "GAME", "ok2/.Main" to "BUKAN_KATEGORI", "kunci buruk" to "GAME", "../../x" to "TOOLS") +
                    (1..2000).associate { "app$it/.M" to "MUSIC" },
            ),
            weather = WeatherSettings("Kota\u0000Aneh" + "x".repeat(500), 500.0, -500.0),
            effects = EffectSettings(volume = 9f, hapticStrength = -1f),
            defaultMusicPackage = "../../evil path",
            gestures = GestureSettings(swipeUp = GestureBinding(GestureAction.OPEN_APP, "a b;rm -rf")),
            hiddenApps = (1..3000).map { "app$it/.M" }.toSet() + "bad key",
            presets = List(100) { ThemePreset(it.toLong(), "P".repeat(500), HomeSettings(), CoverSettings()) },
            customGestures = listOf(
                CustomGesture("ok", "Valid", List(96) { 0.1f }),
                CustomGesture("pendek", "Terlalu pendek", listOf(0f, 1f)),
                CustomGesture("liar", "Nilai liar", List(96) { 500f }),
            ),
        )
        val s = ok(SettingsBackup.parse(craftedFile(hostile), defaults, allow))

        assertEquals(1.2f, s.home.turntableSize, 0f)
        assertEquals(33, s.home.rpm)
        assertEquals(null, s.home.background.imagePath)
        assertEquals(0.25f, s.home.clock.scale, 0f)
        assertTrue(s.home.clock.color in 0..0xFFFFFFFFL)
        assertEquals(SettingsSanitizer.MAX_WIDGETS, s.home.widgets.size)
        assertTrue(s.home.widgets.all { it.x == 1f && it.y == 0f && it.scale == 3f })
        // gambar widget: hanya yang berada di folder internal & tanpa ".."
        assertEquals(listOf(allowedPrefix + "ok.img"), s.home.widgets.first().images)
        assertEquals(MAX_DOCK_APPS, s.dock.apps.size)
        assertTrue(s.dock.apps.none { it.contains(' ') || it.contains("..") })
        assertEquals(120, s.dock.margin)
        assertEquals(1f, s.nav.x, 0f); assertEquals(0f, s.nav.y, 0f)
        assertEquals(200, s.nav.spacing); assertEquals(SettingsSanitizer.MIN_NAV_BUTTON, s.nav.size); assertEquals(1f, s.nav.opacity, 0f)
        assertEquals(SettingsSanitizer.MAX_FREQUENT, s.drawer.frequentCount)
        assertEquals(SettingsSanitizer.MAX_CATEGORY_OVERRIDES, s.drawer.categoryOverrides.size)
        assertEquals("GAME", s.drawer.categoryOverrides["ok/.Main"])
        assertFalse("kategori tak dikenal dibuang", "ok2/.Main" in s.drawer.categoryOverrides)
        assertTrue("kunci tak sah dibuang", s.drawer.categoryOverrides.keys.none { it.contains(' ') || it.contains("..") })
        assertEquals(80, s.weather.cityName.length)
        assertFalse(s.weather.cityName.contains('\u0000'))
        assertEquals(90.0, s.weather.latitude, 0.0); assertEquals(-180.0, s.weather.longitude, 0.0)
        assertEquals(1f, s.effects.volume, 0f); assertEquals(0.1f, s.effects.hapticStrength, 0f)
        assertEquals(null, s.defaultMusicPackage)
        assertEquals(null, s.gestures.swipeUp.packageName)
        assertEquals(SettingsSanitizer.MAX_HIDDEN_APPS, s.hiddenApps.size)
        assertFalse("bad key" in s.hiddenApps)
        assertEquals(SettingsSanitizer.MAX_PRESETS, s.presets.size)
        assertTrue(s.presets.all { it.name.length <= SettingsSanitizer.MAX_NAME })
        assertEquals(listOf("ok"), s.customGestures.map { it.id })
    }

    @Test
    fun sanitizer_handlesNonFiniteNumbers() {
        val s = defaults.copy(
            home = defaults.home.copy(turntableSize = Float.NaN, clock = defaults.home.clock.copy(scale = Float.POSITIVE_INFINITY)),
            weather = WeatherSettings("x", Double.NaN, Double.NEGATIVE_INFINITY),
            nav = NavSettings(opacity = Float.NaN),
        )
        val c = SettingsSanitizer.sanitize(s)
        assertEquals(0.85f, c.home.turntableSize, 0f)
        assertEquals(1f, c.home.clock.scale, 0f)
        assertEquals(0.0, c.weather.latitude, 0.0)
        assertEquals(0.0, c.weather.longitude, 0.0)
        assertEquals(0.85f, c.nav.opacity, 0f)
    }

    @Test
    fun sanitizer_isIdempotent() {
        val once = SettingsSanitizer.sanitize(rich(), allow)
        assertEquals(once, SettingsSanitizer.sanitize(once, allow))
    }

    @Test
    fun packageAndKeyValidation() {
        assertTrue(SettingsSanitizer.isPackageName("com.spotify.music"))
        assertTrue(SettingsSanitizer.isPackageName("a_b.C1"))
        assertFalse(SettingsSanitizer.isPackageName(""))
        assertFalse(SettingsSanitizer.isPackageName("a b"))
        assertFalse(SettingsSanitizer.isPackageName("a/b"))
        assertFalse(SettingsSanitizer.isPackageName("x".repeat(256)))
        assertTrue(SettingsSanitizer.isSafeKey("com.a/com.a.Main\$Inner#-123"))
        assertFalse(SettingsSanitizer.isSafeKey("com.a/ main"))
        assertFalse(SettingsSanitizer.isSafeKey("x".repeat(513)))
    }
}
