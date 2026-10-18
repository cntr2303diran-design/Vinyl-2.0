package com.vinyl.launcher.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategorizerTest {

    private fun cat(pkg: String, label: String, platform: PlatformCategory = PlatformCategory.UNDEFINED, override: AppCategory? = null) =
        AppCategorizer.categorize(platform, pkg, label, override)

    @Test fun overrideBeatsEverything() {
        assertEquals(AppCategory.TOOLS, cat("com.spotify.music", "Spotify", PlatformCategory.AUDIO, override = AppCategory.TOOLS))
    }

    @Test fun declaredCategoryBeatsGuess() {
        // Nama paket mengandung "music", tetapi pengembang menyatakan ini game
        assertEquals(AppCategory.GAME, cat("com.studio.musicdash", "Music Dash", PlatformCategory.GAME))
    }

    @Test fun everyPlatformCategoryIsHandled() {
        // Tidak ada kategori platform yang menjatuhkan kita ke pengecualian; yang "tidak diketahui" jatuh ke tebakan
        PlatformCategory.entries.forEach { p -> cat("com.example.zzz", "Zzz", p) }
        assertEquals(AppCategory.MUSIC, cat("x.y", "Y", PlatformCategory.AUDIO))
        assertEquals(AppCategory.MEDIA, cat("x.y", "Y", PlatformCategory.VIDEO))
        assertEquals(AppCategory.MEDIA, cat("x.y", "Y", PlatformCategory.IMAGE))
        assertEquals(AppCategory.SOCIAL, cat("x.y", "Y", PlatformCategory.SOCIAL))
        assertEquals(AppCategory.NEWS, cat("x.y", "Y", PlatformCategory.NEWS))
        assertEquals(AppCategory.TRAVEL, cat("x.y", "Y", PlatformCategory.MAPS))
        assertEquals(AppCategory.PRODUCTIVITY, cat("x.y", "Y", PlatformCategory.PRODUCTIVITY))
        assertEquals(AppCategory.OTHER, cat("x.y", "Y", PlatformCategory.UNDEFINED))
    }

    @Test fun guessesFromPackageAndLabel() {
        assertEquals(AppCategory.MUSIC, cat("com.spotify.music", "Spotify"))
        assertEquals(AppCategory.SOCIAL, cat("com.whatsapp", "WhatsApp"))
        assertEquals(AppCategory.TRAVEL, cat("com.google.android.apps.maps", "Maps"))
        assertEquals(AppCategory.MEDIA, cat("com.sec.android.app.camera", "Kamera"))
        assertEquals(AppCategory.TOOLS, cat("com.android.settings", "Settings"))
        assertEquals(AppCategory.SHOPPING, cat("com.tokopedia.tkpd", "Tokopedia"))
    }

    @Test fun earlierRuleWinsWhenSeveralMatch() {
        // "YouTube Music": Musik didahulukan dari Media
        assertEquals(AppCategory.MUSIC, cat("com.google.android.apps.youtube.music", "YouTube Music"))
        assertEquals(AppCategory.MEDIA, cat("com.google.android.youtube", "YouTube"))
    }

    @Test fun shortKeywordsDoNotMatchInsideLongerWords() {
        // Regresi: pencocokan substring membuat "bri" cocok dengan "bridge", "word" dengan "wordle", dst.
        assertNotEquals(AppCategory.SHOPPING, cat("com.clickgamer.bridge", "Bridge Constructor"))
        assertNotEquals(AppCategory.PRODUCTIVITY, cat("com.example.wordle", "Wordle"))
        assertNotEquals(AppCategory.MUSIC, cat("com.zhiliaoapp.musically", "Musically"))
        assertNotEquals(AppCategory.SOCIAL, cat("com.example.pipeline", "Pipeline"))
        assertEquals(AppCategory.OTHER, cat("com.clickgamer.bridge", "Bridge Constructor"))
    }

    @Test fun shortKeywordsMatchWholeTokens() {
        assertEquals(AppCategory.SHOPPING, cat("id.co.bri.brimo", "BRImo"))
        assertEquals(AppCategory.MUSIC, cat("com.apple.android.music", "Apple Music"))
    }

    @Test fun longKeywordsMayBePrefixOfToken() {
        assertEquals(AppCategory.MEDIA, cat("com.vendor.camera2", "Cam"))
    }

    @Test fun unknownFallsBackToOther() {
        assertEquals(AppCategory.OTHER, cat("com.example.qqqq", "Qqqq"))
        assertEquals(AppCategory.OTHER, cat("", ""))
        assertEquals(AppCategory.OTHER, cat("...", "---"))
    }

    @Test fun parseRoundTripsAndRejectsUnknown() {
        AppCategory.entries.forEach { assertEquals(it, AppCategorizer.parse(it.name)) }
        assertNull(AppCategorizer.parse("BOGUS"))
        assertNull(AppCategorizer.parse(null))
        assertNull(AppCategorizer.parse(""))
    }

    @Test fun titlesAreUniqueAndNonBlank() {
        val titles = AppCategory.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(titles.all { it.isNotBlank() })
    }
}
