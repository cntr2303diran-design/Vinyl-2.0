package com.vinyl.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LrcTest {

    private fun times(lrc: String) = Lrc.parse(lrc).map { it.timeMs }
    private fun texts(lrc: String) = Lrc.parse(lrc).map { it.text }

    @Test fun parsesBasicLine() {
        val l = Lrc.parse("[00:12.34] Hello world").single()
        assertEquals(12_340L, l.timeMs)
        assertEquals("Hello world", l.text)
    }

    @Test fun fractionLengthsAreScaledToMilliseconds() {
        assertEquals(listOf(5_000L), times("[00:05]x"))          // tanpa pecahan
        assertEquals(listOf(5_700L), times("[00:05.7]x"))         // 1 digit = persepuluh
        assertEquals(listOf(5_070L), times("[00:05.07]x"))        // 2 digit = perseratus
        assertEquals(listOf(5_007L), times("[00:05.007]x"))       // 3 digit = milidetik
        assertEquals(listOf(62_500L), times("[01:02:50]x"))       // pemisah ':' diterima
    }

    @Test fun minutesMayHaveUpToThreeDigits() {
        assertEquals(listOf(6_000_000L), times("[100:00.00]x"))
        assertEquals(listOf(65_000L), times("[1:05]x"))
    }

    @Test fun oneLineWithSeveralTagsBecomesSeveralLinesSortedByTime() {
        val lines = Lrc.parse("[00:50.00][00:10.00]Reff\n[00:30.00]Bait")
        assertEquals(listOf(10_000L, 30_000L, 50_000L), lines.map { it.timeMs })
        assertEquals(listOf("Reff", "Bait", "Reff"), lines.map { it.text })
    }

    @Test fun metadataTagsNeverBecomeLyrics() {
        val lrc = "[ar:Artis]\n[ti:Judul]\n[al:Album]\n[by:seseorang]\n[offset:+500]\n[length: 03:20]\n[00:01.00]Baris"
        assertEquals(listOf("Baris"), texts(lrc))
        assertEquals(listOf(1_000L), times(lrc))      // offset sengaja tidak diterapkan
    }

    @Test fun invalidSecondsAreSkippedButOtherTagsOnTheLineSurvive() {
        assertEquals(listOf(5_000L), times("[00:99.00]buruk\n[00:05.00]baik"))
        assertEquals(listOf(7_000L), times("[00:75.00][00:07.00]campur"))
    }

    @Test fun enhancedWordTagsAreRemovedFromText() {
        assertEquals(listOf("Hello"), texts("[00:12.00]<00:12.00>Hel<00:12.40>lo"))
        assertEquals(listOf("a b"), texts("[00:01.00]<00:01.00>a <00:01.50>b"))
    }

    @Test fun blankTextIsKeptAsInstrumentalBreak() {
        val l = Lrc.parse("[00:30.00]").single()
        assertEquals(30_000L, l.timeMs)
        assertEquals("", l.text)
    }

    @Test fun lineWithoutTagIsIgnored() {
        assertEquals(emptyList<LyricLine>(), Lrc.parse("hanya teks biasa\n\n  \nbaris lain"))
        assertEquals(emptyList<LyricLine>(), Lrc.parse(""))
    }

    @Test fun handlesCrLfAndSortsOutOfOrderInput() {
        assertEquals(listOf(1_000L, 2_000L, 3_000L), times("[00:03.00]c\r\n[00:01.00]a\r\n[00:02.00]b\r\n"))
    }

    @Test fun equalTimesKeepInputOrder() {
        assertEquals(listOf("pertama", "kedua", "ketiga"), texts("[00:05.00]pertama\n[00:05.00]kedua\n[00:05.00]ketiga"))
    }

    @Test fun controlCharactersAreStrippedAndLongTextIsCut() {
        assertEquals(listOf("ab"), texts("[00:01.00]a\u0000\u0007b"))
        val long = "x".repeat(Lrc.MAX_LINE_CHARS + 500)
        assertEquals(Lrc.MAX_LINE_CHARS, Lrc.parse("[00:01.00]$long").single().text.length)
    }

    @Test fun lineCountIsCapped() {
        val many = buildString { repeat(Lrc.MAX_LINES + 1000) { append("[00:%02d.00]x\n".format(it % 60)) } }
        assertEquals(Lrc.MAX_LINES, Lrc.parse(many).size)
        // Satu baris dengan ribuan tanda waktu pun dibatasi
        val tags = "[00:01.00]".repeat(Lrc.MAX_LINES + 50) + "x"
        assertEquals(Lrc.MAX_LINES, Lrc.parse(tags).size)
    }

    @Test fun garbageNeverThrows() {
        val rnd = Random(42)
        val alphabet = "[]<>:.0123456789 abc\n\r-+\u0000"
        repeat(2000) {
            val s = buildString { repeat(rnd.nextInt(200)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            Lrc.parse(s)      // tidak boleh melempar
        }
    }

    // ---- Lyrics ----

    private fun lyrics(vararg t: Long) = Lyrics(t.map { LyricLine(it, "l$it") }, null)

    @Test fun indexAtBeforeFirstIsMinusOne() {
        assertEquals(-1, lyrics(1000, 2000).indexAt(0))
        assertEquals(-1, lyrics(1000, 2000).indexAt(999))
        assertEquals(-1, Lyrics(emptyList(), "plain").indexAt(5000))
    }

    @Test fun indexAtFindsLastLineStartedAlready() {
        val l = lyrics(1000, 2000, 3000)
        assertEquals(0, l.indexAt(1000))
        assertEquals(0, l.indexAt(1999))
        assertEquals(1, l.indexAt(2000))
        assertEquals(2, l.indexAt(3000))
        assertEquals(2, l.indexAt(Long.MAX_VALUE))
    }

    @Test fun indexAtAgreesWithLinearScanOnRandomData() {
        val rnd = Random(7)
        repeat(300) {
            val ts = (0 until 1 + rnd.nextInt(60)).map { rnd.nextInt(200_000).toLong() }.sorted()
            val l = Lyrics(ts.map { LyricLine(it, "") }, null)
            repeat(30) {
                val ms = rnd.nextInt(210_000).toLong()
                val expected = ts.indexOfLast { it <= ms }
                assertEquals("ms=$ms ts=$ts", expected, l.indexAt(ms))
            }
        }
    }

    @Test fun plainLinesTrimOuterBlankLines() {
        assertEquals(listOf("a", "", "b"), Lyrics(emptyList(), "\n\n a\n\nb  \n\n").plainLines.map { it.trimStart() })
        assertTrue(Lyrics(emptyList(), null).plainLines.isEmpty())
        assertTrue(Lyrics(emptyList(), "  \n ").plainLines.isEmpty())
    }

    // ---- TitleCleaner ----

    @Test fun cleanerRemovesNoiseButKeepsRealTitles() {
        assertEquals("Song", TitleCleaner.clean("Song (feat. Someone)"))
        assertEquals("Song", TitleCleaner.clean("Song [Live]"))
        assertEquals("Song", TitleCleaner.clean("Song (Official Video)"))
        assertEquals("Song", TitleCleaner.clean("Song - Remastered 2011"))
        assertEquals("Song", TitleCleaner.clean("Song - Single Version"))
        assertEquals("Song - Artis", TitleCleaner.clean("Song - Artis - Remastered 2011"))
        assertEquals("Live and Let Die", TitleCleaner.clean("Live and Let Die"))
        assertEquals("Song (Acoustic)", TitleCleaner.clean("Song (Acoustic)"))
    }

    @Test fun cleanerNeverReturnsBlankForNonBlankInput() {
        assertEquals("(feat. X)", TitleCleaner.clean("(feat. X)"))
        assertEquals("", TitleCleaner.clean("   "))
    }

    @Test fun cleanerBoundsHugeAndAdversarialInput() {
        val evil = "a - ".repeat(5000) + "remaster"
        val out = TitleCleaner.clean(evil)
        assertTrue(out.length <= 200)
        val evil2 = " ".repeat(100_000) + "(feat"
        assertTrue(TitleCleaner.clean(evil2).length <= 200)
    }
}
