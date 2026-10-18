package com.vinyl.launcher.ui.room.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.room.TvMaterial
import com.vinyl.launcher.ui.room.woodGrain
import com.vinyl.launcher.ui.theme.Tokens

/*
 * Gambar statis panggung: dinding & lantai ruangan malam, meja kayu (atap & muka depan), lubang rak deck, serta atap &
 * sisi kabinet TV. Semua titik berasal dari [StageLayout.Frame] (dp) dan dikali [density] di sini.
 */

internal fun P2.px(d: Float) = Offset(x * d, y * d)

internal fun SRect.px(d: Float) = Rect(left * d, top * d, right * d, bottom * d)

private fun DrawScope.quadPath(points: List<V3>, f: StageLayout.Frame): Path {
    val d = density
    return Path().apply {
        points.forEachIndexed { i, v ->
            val p = f.camera.project(v).px(d)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
}

/** Warna ruangan untuk nada pilihan (gelap = malam VHS; terang = dinding putih hangat versi lama). */
internal data class StagePalette(val wallLit: Color, val wallDark: Color, val floor: Color, val woodLight: Color, val woodMid: Color, val woodDark: Color)

internal fun stagePalette(tone: RoomTone, wallColor: Color?): StagePalette {
    val base = if (tone == RoomTone.LIGHT) {
        StagePalette(Color(0xFFE9E5DD), Color(0xFFBDB7AC), Color(0xFFB4ADA1), Color(0xFF8A6446), Color(0xFF6C4C33), Color(0xFF3F2B1C))
    } else {
        StagePalette(
            Color(Tokens.Vhs.WALL_LIT), Color(Tokens.Vhs.WALL_DARK), Color(Tokens.Vhs.FLOOR),
            Color(Tokens.Vhs.WOOD_LIGHT), Color(Tokens.Vhs.WOOD_MID), Color(Tokens.Vhs.WOOD_DARK),
        )
    }
    return if (wallColor == null) base else base.copy(wallLit = wallColor, wallDark = lerp(wallColor, Color.Black, 0.7f))
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

/**
 * Dinding (diterangi proyeksi & layar TV) dan lantai. [projectionGlow] warna rata-rata proyeksi; alpha 0 = proyektor mati.
 * [wallGradient] bila pengguna memilih dinding bergradasi.
 */
internal fun DrawScope.drawStageBackdrop(
    f: StageLayout.Frame,
    pal: StagePalette,
    projectionGlow: Color,
    screenGlow: Color,
    glow: Float,
    wallGradient: Pair<Color, Color>?,
) {
    val d = density
    val w = size.width
    val h = size.height
    val floorY = f.floorLine * d
    val proj = f.projection.px(d)
    val tv = f.tvFront.px(d)

    // Dinding
    if (wallGradient != null) {
        drawRect(Brush.verticalGradient(listOf(wallGradient.first, wallGradient.second), endY = floorY))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = proj.center, radius = maxOf(w, h) * 0.8f))
    } else {
        drawRect(Brush.radialGradient(listOf(pal.wallLit, pal.wallDark), center = Offset(proj.center.x, proj.center.y), radius = maxOf(w, h) * 0.72f))
    }
    // Cahaya proyeksi memantul ke dinding di sekitar gambar (halo lembut)
    if (projectionGlow.alpha > 0f) {
        val r = maxOf(proj.width, proj.height) * 0.85f
        drawCircle(
            Brush.radialGradient(listOf(projectionGlow, Color.Transparent), center = proj.center, radius = r),
            r, proj.center, blendMode = BlendMode.Screen,
        )
    }
    // Cahaya layar TV menyebar di dinding belakang (halo sekitar kabinet)
    val g = glow.coerceIn(0f, 1f)
    if (g > 0f) {
        val r = tv.width * 0.95f
        drawCircle(
            Brush.radialGradient(listOf(screenGlow.copy(alpha = 0.20f * g), Color.Transparent), center = tv.center, radius = r),
            r, tv.center, blendMode = BlendMode.Screen,
        )
    }
    // Lantai: hampir hitam, sedikit lebih terang di dekat dinding
    drawRect(
        Brush.verticalGradient(listOf(lerp(pal.floor, pal.wallDark, 0.5f), pal.floor), startY = floorY, endY = h),
        topLeft = Offset(0f, floorY), size = Size(w, (h - floorY).coerceAtLeast(0f)),
    )
    drawLine(Color.Black.copy(alpha = 0.6f), Offset(0f, floorY), Offset(w, floorY), strokeWidth = 1.5f * d)
}

/**
 * Meja kayu (atap dengan serat & cahaya layar, muka depan dengan papan), lubang rak deck, bayangan kontak TV, lalu
 * atap & sisi kabinet TV. [screenGlow] warna cahaya layar (dari cover), [glow] intensitas 0..1.
 */
internal fun DrawScope.drawStageFurniture(
    f: StageLayout.Frame,
    pal: StagePalette,
    tv: TvMaterial,
    screenGlow: Color,
    glow: Float,
    screenOn: Boolean,
    /** Serat kayu muka depan meja (dibuat sekali per ukuran, lihat [standGrain]). */
    frontGrain: Path?,
) {
    val d = density
    val eye = f.camera.eye
    val g = glow.coerceIn(0f, 1f) * if (screenOn) 1f else 0.25f

    // ---------- Atap meja
    val top = f.stand.quad(Face.TOP)
    val topPath = quadPath(top, f)
    val frontEdgeY = f.camera.project(V3(0f, StageLayout.STAND_H, StageLayout.STAND_FRONT_Z)).y * d
    val backEdgeY = f.camera.project(V3(0f, StageLayout.STAND_H, StageLayout.STAND_BACK_Z)).y * d
    drawPath(topPath, Brush.verticalGradient(listOf(pal.woodDark, pal.woodMid, pal.woodLight), startY = backEdgeY, endY = frontEdgeY))
    clipPath(topPath) {
        // Serat kayu memanjang: garis di beberapa kedalaman (makin rapat ke belakang)
        var z = StageLayout.STAND_FRONT_Z + 1.2f
        var i = 0
        while (z < StageLayout.STAND_BACK_Z) {
            val a = f.camera.project(V3(-StageLayout.STAND_HALF_W, StageLayout.STAND_H, z)).px(d)
            val b = f.camera.project(V3(StageLayout.STAND_HALF_W, StageLayout.STAND_H, z)).px(d)
            drawLine(Color.Black.copy(alpha = if (i % 3 == 0) 0.22f else 0.10f), a, b, strokeWidth = (if (i % 3 == 0) 1.2f else 0.8f) * d)
            z += 2.1f + (i % 4) * 0.7f
            i++
        }
        // Pernis memantulkan layar TV tepat di depan kabinet
        if (g > 0f) {
            val spot = f.camera.project(V3(f.tv.origin.x, StageLayout.STAND_H, StageLayout.TV_FRONT_Z - 4f)).px(d)
            val r = f.tvFront.width * d * 0.75f
            drawOval(
                Brush.radialGradient(listOf(screenGlow.copy(alpha = 0.42f * g), Color.Transparent), center = spot, radius = r),
                topLeft = Offset(spot.x - r, spot.y - r * 0.35f), size = Size(r * 2f, r * 0.7f), blendMode = BlendMode.Screen,
            )
        }
        // Bayangan kontak di bawah TV
        val shadow = Solid.axisAligned(
            f.tv.origin.x - StageLayout.TV_W / 2f - 2.5f, f.tv.origin.x + StageLayout.TV_W / 2f + 2.5f,
            StageLayout.STAND_H, StageLayout.STAND_H, StageLayout.TV_FRONT_Z - 1.5f, StageLayout.TV_FRONT_Z + StageLayout.TV_D + 1f,
        ).quad(Face.TOP)
        drawPath(quadPath(shadow, f), Color.Black.copy(alpha = 0.55f))
    }
    // Tepi depan atap: sorot tipis (bevel)
    drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, frontEdgeY), Offset(size.width, frontEdgeY), strokeWidth = 1.2f * d)

    // ---------- Muka depan meja
    val front = f.standFront.px(d)
    drawRect(Brush.verticalGradient(listOf(pal.woodMid, pal.woodDark), startY = front.top, endY = front.bottom), topLeft = front.topLeft, size = front.size)
    clipRect(front.left, front.top, front.right, front.bottom) {
        // Serat mendatar bergelombang (dari pola yang sama dengan kabinet TV)
        if (frontGrain != null) translate(front.left, front.top) {
            drawPath(frontGrain, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 1.1f * d))
        }
        // Sambungan papan tegak & bayangan dari atap
        val seams = 6
        for (k in 1 until seams) {
            val x = front.left + front.width * k / seams
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, front.top), Offset(x, front.bottom), strokeWidth = 1.2f * d)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), startY = front.top, endY = front.top + 10f * d), topLeft = front.topLeft, size = Size(front.width, 10f * d))
        // Cahaya layar jatuh ke muka depan (lemah, dari atas)
        if (g > 0f) {
            val c = Offset(f.tvFront.centerX * d, front.top)
            val r = f.tvFront.width * d * 0.9f
            drawCircle(Brush.radialGradient(listOf(screenGlow.copy(alpha = 0.16f * g), Color.Transparent), center = c, radius = r), r, c, blendMode = BlendMode.Screen)
        }
    }

    // ---------- Lubang rak (deck digambar terpisah di dalamnya)
    val shelf = f.shelf.px(d)
    drawRect(Color(0xFF080706), topLeft = shelf.topLeft, size = shelf.size)
    drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = shelf.top, endY = shelf.top + shelf.height * 0.5f), topLeft = shelf.topLeft, size = Size(shelf.width, shelf.height * 0.5f))
    drawRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(shelf.left, shelf.bottom - 1.5f * d), size = Size(shelf.width, 1.5f * d))

    // ---------- Atap & sisi kabinet TV
    for (face in f.tv.visibleFaces(eye)) {
        if (face == Face.FRONT) continue    // muka depan = composable CrtTv
        val path = quadPath(f.tv.quad(face), f)
        when (face) {
            Face.TOP -> {
                drawPath(path, Brush.verticalGradient(listOf(tv.faceDark, tv.faceLight),
                    startY = f.camera.project(V3(0f, StageLayout.STAND_H + StageLayout.TV_H, StageLayout.TV_FRONT_Z + StageLayout.TV_D)).y * d,
                    endY = f.tvFront.top * d))
                if (tv.wood) clipPath(path) {
                    var z = StageLayout.TV_FRONT_Z + 1.5f
                    while (z < StageLayout.TV_FRONT_Z + StageLayout.TV_D) {
                        val a = f.camera.project(V3(f.tv.origin.x - StageLayout.TV_W / 2f, StageLayout.STAND_H + StageLayout.TV_H, z)).px(d)
                        val b = f.camera.project(V3(f.tv.origin.x + StageLayout.TV_W / 2f, StageLayout.STAND_H + StageLayout.TV_H, z)).px(d)
                        drawLine(Color.Black.copy(alpha = 0.16f), a, b, strokeWidth = 0.9f * d)
                        z += 2.7f
                    }
                }
                // Cahaya proyeksi dari belakang menyentuh tepi belakang atap
                drawPath(path, Color.White.copy(alpha = 0.05f))
            }
            else -> {
                drawPath(path, Brush.horizontalGradient(listOf(lerp(tv.faceDark, Color.Black, 0.35f), tv.faceDark)))
            }
        }
        drawPath(path, Color.Black.copy(alpha = 0.45f), style = Stroke(width = 1f * d))
    }
    // Tepi atas muka depan TV menangkap cahaya
    val tvFront = f.tvFront.px(d)
    drawLine(Color.White.copy(alpha = 0.22f), Offset(tvFront.left + 4f * d, tvFront.top), Offset(tvFront.right - 4f * d, tvFront.top), strokeWidth = 1.2f * d)
}

/** Serat kayu untuk muka depan meja pada bingkai [f] (px). */
internal fun standGrain(f: StageLayout.Frame, density: Float): Path {
    val r = f.standFront.px(density)
    return woodGrain(Size(r.width, r.height), seed = 3, lines = 18, vertical = false)
}
