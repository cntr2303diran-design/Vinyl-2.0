package com.vinyl.launcher.data

import kotlinx.serialization.Serializable
import java.net.IDN
import kotlin.random.Random

/**
 * Kaset film di laci meja: nama di label + alamat situs (HTTPS) yang dibuka di layar TV. Isi situs sepenuhnya milik
 * situs itu; aplikasi hanya membukanya (lihat PrivacyInfo). Kaset bawaan hanya menunjuk sumber film legal.
 */
@Serializable
data class FilmCassette(
    val id: String,
    val name: String,
    val url: String,
    /** Warna cangkang kaset (ARGB). */
    val color: Int = FilmRules.COLORS[0],
)

@Serializable
data class FilmSettings(
    val cassettes: List<FilmCassette> = FilmRules.defaults(),
    /** Mode Teater (layar penuh) memutar layar ke mendatar. */
    val theaterLandscape: Boolean = true,
    /** Musik yang tadinya sedang main dilanjutkan saat kaset musik dikembalikan ke TV. */
    val resumeMusic: Boolean = true,
    /** Skor tertinggi game Snake; tersimpan lokal bersama pengaturan. */
    val snakeBest: Int = 0,
    /** Kaset film mengingat halaman dan menit terakhir (disimpan di perangkat ini saja, lihat [FilmResume]). */
    val rememberPlace: Boolean = true,
)

/** Mengapa sebuah alamat ditolak; [message] ditampilkan apa adanya di editor kaset. */
enum class UrlProblem(val message: String) {
    EMPTY("Isi alamat situsnya dulu."),
    TOO_LONG("Alamat terlalu panjang."),
    SPACES("Alamat tidak boleh berisi spasi."),
    SCHEME("Hanya alamat https yang boleh, mis. archive.org."),
    CREDENTIALS("Alamat dengan nama pengguna/kata sandi di dalamnya tidak diizinkan."),
    HOST("Nama situs tidak sah. Contoh yang benar: archive.org."),
}

/** Keputusan untuk tautan di dalam halaman film. */
sealed interface NavDecision {
    data object Allow : NavDecision
    data class Redirect(val httpsUrl: String) : NavDecision
    data object Block : NavDecision
}

/** Hasil membaca alamat ketikan pengguna. */
sealed interface UrlResult {
    data class Ok(val url: String) : UrlResult
    data class Bad(val problem: UrlProblem) : UrlResult
}

/**
 * Aturan kaset film, murni (tanpa Android) agar bisa diuji. Prinsip keamanan: hanya HTTPS (http dinaikkan menjadi https,
 * skema lain ditolak), tanpa nama pengguna/kata sandi di alamat, nama situs harus nama publik yang sah (bukan alamat IP,
 * localhost, atau akhiran jaringan pribadi). Dipakai editor, SettingsSanitizer, dan impor cadangan.
 */
object FilmRules {
    const val MAX_CASSETTES = 24
    const val MAX_NAME = 20
    const val MAX_URL = 500
    /** Alamat tujuan tautan di dalam halaman boleh lebih panjang daripada yang diketik pengguna (parameter pelacak, dll.). */
    const val MAX_NAV_URL = 8_192
    const val MAX_ID = 40
    const val MAX_SNAKE = 99_999

    /** Warna cangkang pilihan: bata, jingga, mustard, lumut, toska, biru tinta, anggur, abu hangat. */
    val COLORS: List<Int> = listOf(
        0xFFB5453A.toInt(), 0xFFD2793A.toInt(), 0xFFCDAA3E.toInt(), 0xFF6E8F4E.toInt(),
        0xFF3F8F8A.toInt(), 0xFF3F5F9A.toInt(), 0xFF7A4F8F.toInt(), 0xFF8A857C.toInt(),
    )

    /** Nama warna di [COLORS] (urutan sama) untuk pembaca layar. */
    val COLOR_NAMES: List<String> = listOf("Bata", "Jingga", "Mustard", "Lumut", "Toska", "Biru tinta", "Anggur", "Abu hangat")

    /** Sumber film legal: film domain publik, film terbuka (CC), dan film Dewan Film Nasional Kanada. */
    fun defaults(): List<FilmCassette> = listOf(
        FilmCassette("domain-publik", "Domain Publik", "https://archive.org/details/feature_films", COLORS[0]),
        FilmCassette("blender", "Blender Studio", "https://studio.blender.org/films/", COLORS[4]),
        FilmCassette("nfb", "NFB Kanada", "https://www.nfb.ca/", COLORS[2]),
    )

    private val SCHEME = Regex("[a-zA-Z][a-zA-Z0-9+.\\-]*")
    private val ID = Regex("[A-Za-z0-9_-]{1,$MAX_ID}")
    private val LABEL = Regex("[a-z0-9-]{1,63}")
    private val PRIVATE_TLDS = setOf("local", "localhost", "internal", "lan", "home", "corp", "intranet", "localdomain", "invalid", "test", "onion")
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    /** Alamat ketikan pengguna → alamat HTTPS yang aman dibuka, atau alasan penolakannya. */
    fun parseUrl(input: String, maxLength: Int = MAX_URL): UrlResult {
        val raw = input.trim()
        if (raw.isEmpty()) return UrlResult.Bad(UrlProblem.EMPTY)
        if (raw.length > maxLength) return UrlResult.Bad(UrlProblem.TOO_LONG)
        if (raw.any { it.isWhitespace() || it.isISOControl() }) return UrlResult.Bad(UrlProblem.SPACES)
        if ('\\' in raw) return UrlResult.Bad(UrlProblem.HOST)

        val rest: String
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd >= 0) {
            val scheme = raw.substring(0, schemeEnd)
            if (!SCHEME.matches(scheme)) return UrlResult.Bad(UrlProblem.SCHEME)
            if (!scheme.equals("https", ignoreCase = true) && !scheme.equals("http", ignoreCase = true)) return UrlResult.Bad(UrlProblem.SCHEME)
            rest = raw.substring(schemeEnd + 3)
        } else {
            // "situs.com:8080/x" adalah alamat tanpa skema; "javascript:…", "mailto:…", "data:…" adalah skema
            val colon = raw.indexOf(':')
            if (colon > 0 && SCHEME.matches(raw.substring(0, colon))) {
                val after = raw.substring(colon + 1)
                val digits = after.takeWhile { it.isDigit() }
                val portForm = digits.isNotEmpty() && (digits.length == after.length || after[digits.length] in "/?#")
                if (!portForm) return UrlResult.Bad(UrlProblem.SCHEME)
            }
            rest = raw
        }

        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, end)
        val tail = rest.substring(end)
        if (authority.isEmpty()) return UrlResult.Bad(UrlProblem.HOST)
        if ('@' in authority) return UrlResult.Bad(UrlProblem.CREDENTIALS)
        if ('[' in authority || ']' in authority) return UrlResult.Bad(UrlProblem.HOST)
        val parts = authority.split(':')
        if (parts.size > 2) return UrlResult.Bad(UrlProblem.HOST)
        val port = parts.getOrNull(1)
        if (port != null) {
            val p = port.toIntOrNull()
            if (p == null || p !in 1..65535 || port.length > 5) return UrlResult.Bad(UrlProblem.HOST)
        }
        val host = try {
            IDN.toASCII(parts[0].trimEnd('.'), IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (e: IllegalArgumentException) {
            return UrlResult.Bad(UrlProblem.HOST)
        }
        if (!validHost(host)) return UrlResult.Bad(UrlProblem.HOST)
        val portPart = if (port != null && port.toInt() != 443) ":${port.toInt()}" else ""
        return UrlResult.Ok("https://$host$portPart$tail")
    }

    fun normalizeUrl(input: String): String? = (parseUrl(input) as? UrlResult.Ok)?.url

    /**
     * Tautan di dalam halaman film mengarah ke [url]: https ke situs publik dibiarkan, http dinaikkan ke https, selain itu
     * (intent:, market:, tel:, file:, content:, javascript:, alamat IP, localhost, nama pengguna di alamat…) diblokir.
     */
    fun checkNavigation(url: String): NavDecision {
        val ok = parseUrl(url, MAX_NAV_URL) as? UrlResult.Ok ?: return NavDecision.Block
        return if (url.trim().startsWith("https://", ignoreCase = true)) NavDecision.Allow else NavDecision.Redirect(ok.url)
    }

    private fun validHost(h: String): Boolean {
        if (h.isEmpty() || h.length > 253) return false
        val labels = h.split('.')
        if (labels.size < 2) return false
        if (labels.any { !LABEL.matches(it) || it.startsWith('-') || it.endsWith('-') }) return false
        val tld = labels.last()
        if (tld in PRIVATE_TLDS) return false
        // Akhiran harus huruf (menolak alamat IP seperti 192.168.1.1) atau berbentuk punycode
        return tld.startsWith("xn--") || (tld.length >= 2 && tld.all { it in 'a'..'z' })
    }

    /** Nama situs untuk tampilan (tanpa "www."), mis. "archive.org". Alamat yang sudah dinormalkan saja. */
    fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }
        return host.removePrefix("www.")
    }

    /** Nama kaset yang disarankan dari alamatnya, mis. "https://studio.blender.org/" → "Blender". */
    fun suggestName(url: String): String {
        val labels = hostOf(url).split('.').filter { it.isNotEmpty() }
        if (labels.isEmpty()) return ""
        var body = if (labels.size > 1) labels.dropLast(1) else labels
        if (body.size > 1 && body.last() in SECOND_LEVEL) body = body.dropLast(1)
        return body.last().replaceFirstChar { it.uppercase() }.take(MAX_NAME)
    }

    /** Nama untuk label kaset: tanpa karakter kontrol, spasi berlebih dirapikan, dipotong [MAX_NAME]. */
    fun cleanName(name: String): String =
        name.filterNot { it.isISOControl() }.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()

    fun cleanColor(color: Int): Int = color or 0xFF000000.toInt()

    fun newId(existing: Set<String>, random: Random = Random.Default): String {
        while (true) {
            val id = "f" + (0 until 8).joinToString("") { "0123456789abcdef"[random.nextInt(16)].toString() }
            if (id !in existing) return id
        }
    }

    /** Kaset baru; null bila alamat tidak sah atau laci penuh. Nama kosong → disarankan dari alamat. */
    fun add(f: FilmSettings, name: String, url: String, color: Int, random: Random = Random.Default): FilmSettings? {
        if (f.cassettes.size >= MAX_CASSETTES) return null
        val clean = normalizeUrl(url) ?: return null
        val label = cleanName(name).ifEmpty { suggestName(clean) }
        val cassette = FilmCassette(newId(f.cassettes.map { it.id }.toSet(), random), label, clean, cleanColor(color))
        return f.copy(cassettes = f.cassettes + cassette)
    }

    /** Ubah kaset [id]; null bila alamat tidak sah atau [id] tidak ada. */
    fun update(f: FilmSettings, id: String, name: String, url: String, color: Int): FilmSettings? {
        if (f.cassettes.none { it.id == id }) return null
        val clean = normalizeUrl(url) ?: return null
        val label = cleanName(name).ifEmpty { suggestName(clean) }
        return f.copy(cassettes = f.cassettes.map { if (it.id == id) it.copy(name = label, url = clean, color = cleanColor(color)) else it })
    }

    fun remove(f: FilmSettings, id: String): FilmSettings = f.copy(cassettes = f.cassettes.filterNot { it.id == id })

    /** Skor tertinggi baru bila [score] lebih baik. */
    fun withSnakeScore(f: FilmSettings, score: Int): FilmSettings =
        if (score > f.snakeBest) f.copy(snakeBest = score.coerceAtMost(MAX_SNAKE)) else f

    fun sanitize(f: FilmSettings): FilmSettings {
        val seen = mutableSetOf<String>()
        val cleaned = ArrayList<FilmCassette>()
        for (c in f.cassettes) {
            if (cleaned.size >= MAX_CASSETTES) break
            val url = normalizeUrl(c.url) ?: continue
            val id = if (ID.matches(c.id) && c.id !in seen) c.id else newId(seen)
            seen += id
            cleaned += FilmCassette(id, cleanName(c.name).ifEmpty { suggestName(url) }, url, cleanColor(c.color))
        }
        return f.copy(cassettes = cleaned, snakeBest = f.snakeBest.coerceIn(0, MAX_SNAKE))
    }
}
