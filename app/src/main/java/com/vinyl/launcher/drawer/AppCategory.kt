package com.vinyl.launcher.drawer

/** Kategori aplikasi di app drawer. [title] tampil di chip dan menu. */
enum class AppCategory(val title: String) {
    MUSIC("Musik"),
    MEDIA("Foto & Video"),
    SOCIAL("Sosial & Komunikasi"),
    GAME("Game"),
    PRODUCTIVITY("Produktivitas"),
    SHOPPING("Belanja & Keuangan"),
    TRAVEL("Peta & Perjalanan"),
    NEWS("Berita"),
    TOOLS("Alat & Sistem"),
    OTHER("Lainnya"),
}

/**
 * Kategori bawaan Android (`ApplicationInfo.category`) tanpa bergantung pada konstanta Android, supaya pengelompokan
 * bisa diuji unit. Pemetaan dari konstanta Android ke enum ini ada di AppsRepository (when yang eksplisit).
 */
enum class PlatformCategory { UNDEFINED, GAME, AUDIO, VIDEO, IMAGE, SOCIAL, NEWS, MAPS, PRODUCTIVITY }

/**
 * Menentukan kategori sebuah aplikasi. Urutan prioritas:
 *  1. pilihan manual pengguna (override);
 *  2. kategori yang dideklarasikan pengembang aplikasi (`ApplicationInfo.category`);
 *  3. tebakan dari nama paket/label (daftar kata kunci di bawah; sengaja sederhana dan bisa meleset);
 *  4. Lainnya.
 * Karena tebakan bisa salah, pengguna selalu bisa memindahkan aplikasi ke kategori lain.
 */
object AppCategorizer {

    fun categorize(
        platform: PlatformCategory,
        packageName: String,
        label: String,
        override: AppCategory? = null,
    ): AppCategory {
        if (override != null) return override
        fromPlatform(platform)?.let { return it }
        return fromKeywords(packageName, label) ?: AppCategory.OTHER
    }

    /** Nama enum yang tersimpan di pengaturan → kategori; null bila tidak dikenal (mis. dari versi lain). */
    fun parse(name: String?): AppCategory? = AppCategory.entries.firstOrNull { it.name == name }

    private fun fromPlatform(p: PlatformCategory): AppCategory? = when (p) {
        PlatformCategory.GAME -> AppCategory.GAME
        PlatformCategory.AUDIO -> AppCategory.MUSIC
        PlatformCategory.VIDEO, PlatformCategory.IMAGE -> AppCategory.MEDIA
        PlatformCategory.SOCIAL -> AppCategory.SOCIAL
        PlatformCategory.NEWS -> AppCategory.NEWS
        PlatformCategory.MAPS -> AppCategory.TRAVEL
        PlatformCategory.PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        // Tidak diketahui: biarkan tebakan kata kunci yang menentukan
        PlatformCategory.UNDEFINED -> null
    }

    /**
     * Kata kunci dicocokkan per TOKEN (nama paket dan label dipecah di setiap karakter bukan huruf/angka), bukan per
     * potongan teks: "bri" tidak boleh cocok dengan "Bridge Constructor". Kata ≤ 5 huruf harus sama persis dengan
     * token ("music" cocok "Apple Music", bukan "Musically"); kata ≥ 6 huruf boleh menjadi awalan token
     * ("camera" cocok "camera2"). Urutan penting: aturan lebih atas menang (mis. "YouTube Music" → Musik sebelum Media).
     */
    private val RULES: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.MUSIC to listOf(
            "spotify", "music", "musik", "deezer", "soundcloud", "joox", "podcast", "podcasts", "radio", "tidal", "shazam", "poweramp", "audio",
        ),
        AppCategory.SOCIAL to listOf(
            "whatsapp", "telegram", "instagram", "facebook", "twitter", "tiktok", "line", "discord", "signal", "snapchat",
            "messenger", "messaging", "dialer", "contacts", "kontak", "mms", "sms", "pesan", "telepon", "phone", "mail", "gmail", "email",
        ),
        AppCategory.SHOPPING to listOf(
            "tokopedia", "shopee", "lazada", "bukalapak", "blibli", "olx", "wallet", "dompet", "bank", "bca", "mandiri", "bni", "bri",
            "dana", "ovo", "gopay", "linkaja", "jenius", "paypal", "pay", "finance", "keuangan", "shop", "shopping",
        ),
        AppCategory.TRAVEL to listOf(
            "maps", "peta", "waze", "gojek", "grab", "traveloka", "uber", "navigation", "navigasi", "transit", "kereta",
        ),
        AppCategory.MEDIA to listOf(
            "camera", "kamera", "gallery", "galeri", "photo", "photos", "foto", "video", "youtube", "netflix", "vlc", "player", "disney", "vidio", "twitch",
        ),
        AppCategory.NEWS to listOf("news", "berita", "kompas", "detik", "cnn", "bbc"),
        AppCategory.PRODUCTIVITY to listOf(
            "docs", "sheets", "slides", "office", "word", "excel", "powerpoint", "notes", "catatan", "keep", "calendar", "kalender",
            "dropbox", "pdf", "notion", "todo", "tasks", "zoom", "meet", "teams", "slack",
        ),
        AppCategory.GAME to listOf("game", "games", "supercell", "mojang", "roblox", "mobilelegends", "pubg", "freefire"),
        AppCategory.TOOLS to listOf(
            "settings", "pengaturan", "calculator", "kalkulator", "clock", "deskclock", "jam", "files", "filemanager", "documentsui",
            "weather", "cuaca", "flashlight", "senter", "recorder", "perekam", "chrome", "browser", "firefox", "vending", "packageinstaller",
            "systemui", "launcher", "keyboard", "inputmethod", "translate", "terjemah", "vpn", "security", "cleaner",
        ),
    )

    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")
    private const val PREFIX_MIN_LENGTH = 6

    private fun fromKeywords(packageName: String, label: String): AppCategory? {
        val tokens = (packageName + " " + label).lowercase().split(SPLIT).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return RULES.firstOrNull { (_, words) -> words.any { w -> tokens.any { t -> matches(w, t) } } }?.first
    }

    private fun matches(word: String, token: String): Boolean =
        token == word || (word.length >= PREFIX_MIN_LENGTH && token.startsWith(word))
}
