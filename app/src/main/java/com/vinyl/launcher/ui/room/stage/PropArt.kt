package com.vinyl.launcher.ui.room.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.vinyl.launcher.data.RoomEra
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * Warna benda tambahan menurut ruangan: tanpa era = kuningan, kayu meja, dan bakelit hitam; di era tertentu bingkai dan
 * pesawat telepon mengambil aksen era (oranye 70-an, magenta 80-an, mint 90-an). Era Menara pengawas memakai benda usang:
 * bingkai kayu kasar, piala kusam, buku bersampul retak, pesawat telepon kelabu-zaitun, dan lapisan debu/goresan ([aged]).
 */
internal class PropLook(
    val rim: Color, val rimDark: Color,
    val wood: Color, val woodDark: Color,
    val phoneBody: Color, val phoneBodyDark: Color,
    val trim: Color,
    /** Bingkai poster (bawaan sama dengan [rim]/[rimDark]), faktor tebalnya, dan warna passe-partout. */
    val frame: Color = rim, val frameDark: Color = rimDark, val frameScale: Float = 1f,
    val mat: Color = Color(0xFFEFE6D2),
    /** Sampul buku harian; null = cokelat bawaan. */
    val diaryCover: Color? = null,
    /** Seberapa kusam piala (0 = mengilap, 1 = hampir abu-abu). */
    val tarnish: Float = 0f,
    /** Benda usang: goresan, debu, noda, dan retakan tipis digambar di atasnya. */
    val aged: Boolean = false,
)

internal fun propLook(pal: StagePalette): PropLook {
    val eraOn = pal.accent.alpha > 0f
    return if (pal.propEra == RoomEra.LOOKOUT) {
        PropLook(
            rim = Color(0xFF8B8F78), rimDark = Color(0xFF3F4435), wood = pal.woodMid, woodDark = pal.woodDark,
            phoneBody = Color(0xFF8C8872), phoneBodyDark = Color(0xFF4B4A3E), trim = Color(0xFFB09A5E),
            frame = Color(0xFF8A7B63), frameDark = Color(0xFF3F362A), frameScale = 1.8f, mat = Color(0xFFB9B4A2),
            diaryCover = Color(0xFF6B705E), tarnish = 0.4f, aged = true,
        )
    } else if (eraOn) {
        PropLook(
            rim = pal.accent, rimDark = pal.accent2, wood = pal.woodMid, woodDark = pal.woodDark,
            phoneBody = pal.accent, phoneBodyDark = lerp(pal.accent, Color.Black, 0.5f), trim = pal.accent2,
        )
    } else {
        PropLook(
            rim = Color(Tokens.Lamp.BRASS_LIGHT), rimDark = Color(Tokens.Lamp.BRASS_DARK), wood = pal.woodMid, woodDark = pal.woodDark,
            phoneBody = Color(Tokens.Props.BAKELITE), phoneBodyDark = Color(Tokens.Props.BAKELITE_DARK), trim = Color(Tokens.Props.SILVER),
        )
    }
}

/** Titik pada jarak [len] dari [c] menurut sudut [deg] (searah jarum jam dari atas). */
internal fun polar(c: Offset, deg: Float, len: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + (sin(a) * len).toFloat(), c.y - (cos(a) * len).toFloat())
}

/**
 * Lapisan usang untuk benda era Menara pengawas, digambar paling akhir di atas benda dalam kotak (atau lingkaran bila
 * [round]) dan terpotong olehnya: bayangan debu di bagian bawah, beberapa goresan terang-gelap, dan bintik. Acaknya tetap
 * menurut [seed] (tiap benda berbeda), jadi tidak berkedip dan murah: satu gradasi, lima goresan, tujuh bintik.
 */
internal fun DrawScope.drawWear(seed: Int, round: Boolean = false) {
    val d = density
    val w = size.width
    val h = size.height
    var s = seed * 7919 + 17
    fun next(): Float {
        s = s * 1103515245 + 12345
        return ((s ushr 8) and 0xFFFF) / 65535f
    }
    val bounds = if (round) Path().apply { addOval(Rect(0f, 0f, w, h)) } else Path().apply { addRect(Rect(0f, 0f, w, h)) }
    clipPath(bounds) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f))), Offset.Zero, size)
        repeat(5) {
            val x = next() * w
            val y = next() * h
            val len = (0.18f + next() * 0.30f) * minOf(w, h)
            val end = Offset((x + len * 0.8f).coerceIn(0f, w), (y + len * 0.4f).coerceIn(0f, h))
            drawLine(Color.White.copy(alpha = 0.17f), Offset(x, y), end, strokeWidth = 0.7f * d)
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(x, y + 0.8f * d), Offset(end.x, end.y + 0.8f * d), strokeWidth = 0.7f * d)
        }
        repeat(7) {
            drawCircle(Color.Black.copy(alpha = 0.14f), (0.5f + next() * 0.9f) * d, Offset(next() * w, next() * h))
        }
    }
}
