package com.vinyl.launcher.ui.theme

import com.vinyl.launcher.ui.theme.Tokens.Film
import com.vinyl.launcher.ui.theme.Tokens.Retro
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teks di baki kaset dan di lapisan layar film harus >= 4,5:1, apa pun isi halaman web di belakangnya. */
class FilmPaletteTest {
    private fun assertReadable(name: String, fg: Long, bg: Long) {
        val c = ColorMath.contrast(fg, bg)
        assertTrue("$name: %.2f:1 < 4,5:1".format(c), c >= Tokens.A11y.MIN_CONTRAST)
    }

    /** Halaman tersulit: putih penuh, kuning/biru terang, dan abu-abu tengah. */
    private val pages = listOf(0xFFFFFFFFL, 0xFFFFF59DL, 0xFF9AD0FFL, 0xFFBDBDBDL, 0xFF808080L, 0xFF000000L)

    @Test
    fun trayTextIsReadableOnTheFelt() {
        for (felt in listOf(Film.FELT_TOP, Film.FELT_BOTTOM)) {
            assertReadable("CREAM_TEXT/%08X".format(felt), Retro.CREAM_TEXT, felt)
            assertReadable("CREAM_TEXT_SECONDARY/%08X".format(felt), Retro.CREAM_TEXT_SECONDARY, felt)
            assertReadable("AMBER/%08X".format(felt), Retro.AMBER, felt)
        }
    }

    @Test
    fun onTvBadgeIsReadable() {
        assertReadable("INK/AMBER", Retro.INK, Retro.AMBER)
    }

    @Test
    fun loadingLabelIsReadableOverAnyPage() {
        for (page in pages) {
            assertReadable("MEMUAT/%08X".format(page), 0xFFFFFFFFL, BackdropDim.darken(page, Film.LOADING_SCRIM_ALPHA))
        }
    }

    @Test
    fun osdNoticeIsReadableOverAnyPage() {
        // Pesan "LANJUT mm:ss" / "TIDAK ADA VIDEO": judul dan keterangan putih di atas peredam yang sama dengan "MEMUAT"
        for (page in pages) {
            val behind = BackdropDim.darken(page, Film.LOADING_SCRIM_ALPHA)
            assertReadable("pesan OSD judul/%08X".format(page), 0xFFFFFFFFL, behind)
            assertReadable("pesan OSD keterangan/%08X".format(page), 0xFFFFFFFFL, behind)
        }
    }

    @Test
    fun lostSignalTextIsReadableOverAnyPage() {
        val messageAlpha = Math.round(Film.LOST_MESSAGE_ALPHA * 255).toLong()
        val message = (messageAlpha shl 24) or 0xFFFFFFL
        for (page in pages) {
            val behind = BackdropDim.darken(page, Film.LOST_SCRIM_ALPHA)
            assertReadable("SINYAL HILANG/%08X".format(page), 0xFFFFFFFFL, behind)
            assertReadable("pesan/%08X".format(page), message, behind)
            assertReadable("KETUK UNTUK COBA LAGI/%08X".format(page), Retro.AMBER, behind)
        }
    }
}
