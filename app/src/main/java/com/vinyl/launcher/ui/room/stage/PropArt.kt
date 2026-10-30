package com.vinyl.launcher.ui.room.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * Warna benda tambahan menurut ruangan: tanpa era = kuningan, kayu meja, dan bakelit hitam; di era tertentu bingkai dan
 * pesawat telepon mengambil aksen era (oranye 70-an, magenta 80-an, mint 90-an).
 */
internal class PropLook(
    val rim: Color, val rimDark: Color,
    val wood: Color, val woodDark: Color,
    val phoneBody: Color, val phoneBodyDark: Color,
    val trim: Color,
)

internal fun propLook(pal: StagePalette): PropLook {
    val eraOn = pal.accent.alpha > 0f
    return if (eraOn) {
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
