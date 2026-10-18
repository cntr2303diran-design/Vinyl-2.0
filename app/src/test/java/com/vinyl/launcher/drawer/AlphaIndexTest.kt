package com.vinyl.launcher.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlphaIndexTest {

    @Test fun lettersAreCaseAndAccentInsensitive() {
        assertEquals('A', AlphaIndex.letterOf("alarm"))
        assertEquals('A', AlphaIndex.letterOf("Ärger"))
        assertEquals('E', AlphaIndex.letterOf("élan"))
        assertEquals('N', AlphaIndex.letterOf("ñandú"))
    }

    @Test fun digitsSymbolsAndOtherScriptsGoToHash() {
        assertEquals('#', AlphaIndex.letterOf("1Password"))
        assertEquals('#', AlphaIndex.letterOf("@#$%"))          // hanya simbol → tidak ada huruf untuk dipakai
        assertEquals('H', AlphaIndex.letterOf("@home"))         // simbol di depan dilewati (konsisten dengan "(Kamera)")
        assertEquals('#', AlphaIndex.letterOf(""))
        assertEquals('#', AlphaIndex.letterOf("   "))
        assertEquals('#', AlphaIndex.letterOf("日本語"))
        assertEquals('#', AlphaIndex.letterOf("عربي"))
        assertEquals('#', AlphaIndex.letterOf("😀"))
    }

    @Test fun leadingPunctuationIsSkipped() {
        assertEquals('K', AlphaIndex.letterOf("(Kamera)"))
        assertEquals('K', AlphaIndex.letterOf("  - Kalkulator"))
        assertEquals('#', AlphaIndex.letterOf("(1) Satu"))
    }

    @Test fun groupOrdersSectionsAndKeepsItemOrder() {
        val sections = AlphaIndex.group(listOf("Zoom", "1Pass", "Bank", "Alarm", "Asisten", "Zebra")) { it }
        assertEquals(listOf('A', 'B', 'Z', '#'), sections.map { it.letter })
        assertEquals(listOf("Alarm", "Asisten"), sections[0].items)
        assertEquals(listOf("Zoom", "Zebra"), sections[2].items)   // urutan masukan dipertahankan (stabil)
        assertEquals(listOf("1Pass"), sections[3].items)
    }

    @Test fun groupOfEmptyIsEmpty() {
        assertEquals(emptyList<AlphaIndex.Section<String>>(), AlphaIndex.group(emptyList<String>()) { it })
    }

    @Test fun flatIndexCountsHeaders() {
        // A: header(0) Alarm(1) Asisten(2) | B: header(3) Bank(4) | Z: header(5) Zoom(6)
        val s = AlphaIndex.group(listOf("Alarm", "Asisten", "Bank", "Zoom")) { it }
        assertEquals(0, AlphaIndex.flatIndexOf(s, 'A'))
        assertEquals(3, AlphaIndex.flatIndexOf(s, 'B'))
        assertEquals(5, AlphaIndex.flatIndexOf(s, 'Z'))
        assertEquals(3, AlphaIndex.flatIndexOf(s, 'b'))       // huruf kecil diterima
    }

    @Test fun missingLetterJumpsToNextExistingSection() {
        val s = AlphaIndex.group(listOf("Alarm", "Bank", "Zoom")) { it }   // A(0,1) B(2,3) Z(4,5)
        assertEquals(4, AlphaIndex.flatIndexOf(s, 'C'))
        assertEquals(4, AlphaIndex.flatIndexOf(s, 'Y'))
    }

    @Test fun letterBeyondLastSectionJumpsToLastSection() {
        val s = AlphaIndex.group(listOf("Alarm", "Bank")) { it }           // A(0,1) B(2,3)
        assertEquals(2, AlphaIndex.flatIndexOf(s, 'Z'))
        assertEquals(2, AlphaIndex.flatIndexOf(s, '#'))
    }

    @Test fun unknownCharacterIsTreatedAsHash() {
        val s = AlphaIndex.group(listOf("Alarm", "1Pass")) { it }          // A(0,1) #(2,3)
        assertEquals(2, AlphaIndex.flatIndexOf(s, '?'))
    }

    @Test fun flatIndexOfEmptyIsNull() {
        assertNull(AlphaIndex.flatIndexOf(emptyList<AlphaIndex.Section<String>>(), 'A'))
    }

    @Test fun letterAtIsInverseOfFlatIndex() {
        val s = AlphaIndex.group(listOf("Alarm", "Asisten", "Bank", "Zoom")) { it }
        val expected = listOf('A', 'A', 'A', 'B', 'B', 'Z', 'Z')
        expected.forEachIndexed { i, c -> assertEquals("indeks $i", c, AlphaIndex.letterAt(s, i)) }
        assertEquals('Z', AlphaIndex.letterAt(s, 999))          // di luar daftar → bagian terakhir
        assertNull(AlphaIndex.letterAt(emptyList<AlphaIndex.Section<String>>(), 0))
        for (l in s.map { it.letter }) {
            assertEquals(l, AlphaIndex.letterAt(s, AlphaIndex.flatIndexOf(s, l)!!))
        }
    }

    @Test fun hundredsOfAppsStayConsistent() {
        val labels = (0 until 500).map { i -> ('A' + i % 26).toString() + "pp$i" }
        val s = AlphaIndex.group(labels) { it }
        assertEquals(500, s.sumOf { it.items.size })
        assertEquals(26, s.size)
        // Total panjang datar = header + item
        assertEquals(500 + 26, s.sumOf { 1 + it.items.size })
    }
}
