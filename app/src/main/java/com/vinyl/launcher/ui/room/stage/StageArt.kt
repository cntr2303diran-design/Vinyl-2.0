package com.vinyl.launcher.ui.room.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
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
 * Dinding berpanel kayu (diterangi proyeksi & layar TV), lis dinding, dan lantai karpet. [projectionGlow] warna rata-rata
 * proyeksi; alpha 0 = proyektor mati. [wallGradient] bila pengguna memilih dinding bergradasi. [carpet] tekstur karpet
 * (lihat [carpetBrush]); null = lantai polos.
 */
internal fun DrawScope.drawStageBackdrop(
    f: StageLayout.Frame,
    pal: StagePalette,
    projectionGlow: Color,
    screenGlow: Color,
    glow: Float,
    wallGradient: Pair<Color, Color>?,
    paneling: Boolean,
    carpet: Brush?,
) {
    val d = density
    val w = size.width
    val h = size.height
    val floorY = f.floorLine * d
    val baseY = f.baseboardTop * d
    val proj = f.projection.px(d)
    val tv = f.tvFront.px(d)

    // Dinding
    if (wallGradient != null) {
        drawRect(Brush.verticalGradient(listOf(wallGradient.first, wallGradient.second), endY = floorY))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = proj.center, radius = maxOf(w, h) * 0.8f))
    } else {
        drawRect(Brush.radialGradient(listOf(pal.wallLit, pal.wallDark), center = Offset(tv.center.x, proj.center.y), radius = maxOf(w, h) * 0.75f))
    }
    // Panel kayu tegak: alur gelap + tepi terang tipis, papan selang-seling sedikit lebih gelap
    if (paneling) {
        val s = f.camera.scaleAt(StageLayout.WALL_Z) * d
        val step = StageLayout.PANEL_W * s
        if (step > 6f) {
            val x0 = f.camera.project(V3(0f, 0f, StageLayout.WALL_Z)).x * d
            var i = kotlin.math.floor((0f - x0) / step).toInt() - 1
            while (true) {
                val x = x0 + i * step
                if (x > w) break
                if (i % 2 == 0) drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(x, 0f), size = Size(step, baseY))
                // Alur antarpapan: celah gelap lebar dengan bibir terang di sisi kanannya (cahaya dari kiri)
                drawLine(Color.Black.copy(alpha = 0.62f), Offset(x, 0f), Offset(x, baseY), strokeWidth = 2.4f * d)
                drawLine(Color.White.copy(alpha = 0.08f), Offset(x + 2f * d, 0f), Offset(x + 2f * d, baseY), strokeWidth = 1f * d)
                // Alur tipis di tengah papan (papan lambrisering bermotif)
                drawLine(Color.Black.copy(alpha = 0.28f), Offset(x + step / 2f, 0f), Offset(x + step / 2f, baseY), strokeWidth = 1f * d)
                i++
            }
        }
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
    // Lis dinding: papan tipis dengan tepi atas tersorot
    if (floorY > baseY) {
        drawRect(Brush.verticalGradient(listOf(lerp(pal.woodMid, pal.wallDark, 0.55f), lerp(pal.woodDark, Color.Black, 0.4f)), startY = baseY, endY = floorY), topLeft = Offset(0f, baseY), size = Size(w, floorY - baseY))
        drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, baseY), Offset(w, baseY), strokeWidth = 1.2f * d)
    }
    // Lantai karpet: hampir hitam, sedikit lebih terang di dekat dinding, bertekstur
    val floorH = (h - floorY).coerceAtLeast(0f)
    drawRect(
        Brush.verticalGradient(listOf(lerp(pal.floor, pal.wallDark, 0.6f), pal.floor), startY = floorY, endY = h),
        topLeft = Offset(0f, floorY), size = Size(w, floorH),
    )
    if (carpet != null && floorH > 0f) drawRect(carpet, topLeft = Offset(0f, floorY), size = Size(w, floorH), alpha = 0.5f)
    drawLine(Color.Black.copy(alpha = 0.6f), Offset(0f, floorY), Offset(w, floorY), strokeWidth = 1.5f * d)
}

/**
 * Konsol kayu (atap dengan serat & cahaya layar, papan atas tebal, badan berserat, kisi speaker kain, rak terbuka,
 * plint), bayangan kontak TV, lalu atap & sisi kabinet TV. [screenGlow] warna cahaya layar (dari cover), [glow]
 * intensitas 0..1.
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
    /** Tenunan kain kisi speaker (lihat [weaveBrush]). */
    weave: Brush?,
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
            val a = f.camera.project(V3(-f.stand.w / 2f, StageLayout.STAND_H, z)).px(d)
            val b = f.camera.project(V3(f.stand.w / 2f, StageLayout.STAND_H, z)).px(d)
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
    // ---------- Muka depan konsol: plint gelap di kaki, badan berserat, papan atas tebal di atasnya
    val front = f.standFront.px(d)
    val slab = f.slabEdge.px(d)
    val plinth = f.plinth.px(d)
    drawRect(Color(0xFF070605), topLeft = plinth.topLeft, size = plinth.size)
    val body = Rect(front.left, slab.bottom, front.right, plinth.top)
    drawRect(Brush.verticalGradient(listOf(lerp(pal.woodMid, pal.woodDark, 0.35f), pal.woodDark), startY = body.top, endY = body.bottom), topLeft = body.topLeft, size = body.size)
    clipRect(body.left, body.top, body.right, body.bottom) {
        if (frontGrain != null) translate(front.left, front.top) {
            drawPath(frontGrain, Color.Black.copy(alpha = 0.16f), style = Stroke(width = 1.1f * d))
        }
        // Cahaya layar jatuh ke muka depan (lemah, dari atas)
        if (g > 0f) {
            val c = Offset(f.tvFront.centerX * d, body.top)
            val r = f.tvFront.width * d * 0.9f
            drawCircle(Brush.radialGradient(listOf(screenGlow.copy(alpha = 0.14f * g), Color.Transparent), center = c, radius = r), r, c, blendMode = BlendMode.Screen)
        }
        // Bayangan papan atas yang menjorok
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startY = body.top, endY = body.top + 9f * d), topLeft = body.topLeft, size = Size(body.width, 9f * d))
    }
    // Kisi speaker kain di kiri & kanan rak
    for (gr in listOfNotNull(f.grilleLeft, f.grilleRight)) drawGrille(gr.px(d), pal, weave)
    // Papan atas: tepi depan kayu lebih terang, sorot di sudut atas, garis gelap di bawah
    drawRect(Brush.verticalGradient(listOf(pal.woodLight, pal.woodMid, lerp(pal.woodMid, pal.woodDark, 0.5f)), startY = slab.top, endY = slab.bottom), topLeft = slab.topLeft, size = slab.size)
    clipRect(slab.left, slab.top, slab.right, slab.bottom) {
        var y = slab.top + slab.height * 0.3f
        var k = 0
        while (y < slab.bottom) {
            drawLine(Color.Black.copy(alpha = 0.12f), Offset(slab.left, y + (k % 2) * 0.6f * d), Offset(slab.right, y), strokeWidth = 0.8f * d)
            y += slab.height * 0.22f
            k++
        }
        if (g > 0f) {
            val c = Offset(f.tvFront.centerX * d, slab.top)
            val r = f.tvFront.width * d * 0.8f
            drawCircle(Brush.radialGradient(listOf(screenGlow.copy(alpha = 0.22f * g), Color.Transparent), center = c, radius = r), r, c, blendMode = BlendMode.Screen)
        }
    }
    drawLine(Color.White.copy(alpha = 0.22f), Offset(slab.left, slab.top + 0.6f * d), Offset(slab.right, slab.top + 0.6f * d), strokeWidth = 1.2f * d)
    drawLine(Color.Black.copy(alpha = 0.7f), Offset(slab.left, slab.bottom), Offset(slab.right, slab.bottom), strokeWidth = 1.4f * d)

    // ---------- Rak terbuka (deck digambar terpisah di dalamnya): ruang gelap, dinding dalam kanan, bibir lantai
    val shelf = f.shelf.px(d)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF030303), Color(0xFF0D0A08)), startY = shelf.top, endY = shelf.bottom), topLeft = shelf.topLeft, size = shelf.size)
    clipRect(shelf.left, shelf.top, shelf.right, shelf.bottom) {
        // Dinding dalam kanan tampak menyempit ke belakang (kamera di kiri)
        val sx = f.camera.unprojectX(f.shelf.right, StageLayout.STAND_FRONT_Z)
        val y0 = f.camera.unprojectY(f.shelf.bottom, StageLayout.STAND_FRONT_Z)
        val y1 = f.camera.unprojectY(f.shelf.top, StageLayout.STAND_FRONT_Z)
        val depth = StageLayout.STAND_FRONT_Z + 30f
        val wall = listOf(
            V3(sx, y1, StageLayout.STAND_FRONT_Z), V3(sx, y1, depth), V3(sx, y0, depth), V3(sx, y0, StageLayout.STAND_FRONT_Z),
        )
        drawPath(quadPath(wall, f), Brush.horizontalGradient(listOf(Color(0xFF0B0907), lerp(pal.woodDark, Color.Black, 0.45f)), startX = shelf.right - shelf.width * 0.1f, endX = shelf.right))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent), startY = shelf.top, endY = shelf.top + shelf.height * 0.3f), topLeft = shelf.topLeft, size = Size(shelf.width, shelf.height * 0.3f))
    }
    // Bibir lantai rak menangkap cahaya, bingkai rak sedikit gelap
    drawLine(lerp(pal.woodLight, Color.Black, 0.25f), Offset(shelf.left, shelf.bottom - 0.8f * d), Offset(shelf.right, shelf.bottom - 0.8f * d), strokeWidth = 1.6f * d)
    drawRect(Color.Black.copy(alpha = 0.55f), topLeft = shelf.topLeft, size = shelf.size, style = Stroke(width = 1.5f * d))

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

/** Kisi speaker: kain gelap bertenun dalam bingkai kayu tipis, bayangan dalam di atas & kiri. */
private fun DrawScope.drawGrille(r: Rect, pal: StagePalette, weave: Brush?) {
    val d = density
    val frame = 2.2f * d
    drawRect(lerp(pal.woodDark, Color.Black, 0.25f), topLeft = r.topLeft, size = r.size)
    val inner = Rect(r.left + frame, r.top + frame, r.right - frame, r.bottom - frame)
    if (inner.width <= 0f || inner.height <= 0f) return
    // Kain speaker cokelat-abu tua (bukan lubang hitam), tenunannya terlihat samar
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3A322B), Color(0xFF2A241F)), startY = inner.top, endY = inner.bottom), topLeft = inner.topLeft, size = inner.size)
    if (weave != null) drawRect(weave, topLeft = inner.topLeft, size = inner.size)
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), startY = inner.top, endY = inner.top + 8f * d), topLeft = inner.topLeft, size = Size(inner.width, 8f * d))
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), startX = inner.left, endX = inner.left + 6f * d), topLeft = inner.topLeft, size = Size(6f * d, inner.height))
    drawRect(Color.White.copy(alpha = 0.07f), topLeft = Offset(r.left, r.bottom - 1f * d), size = Size(r.width, 1f * d))
}

/** Tenunan kain speaker: ubin kecil berulang (benang terang & gelap bergantian), dibuat sekali. */
internal fun weaveBrush(): Brush {
    val n = 6
    val px = IntArray(n * n) { i ->
        val x = i % n
        val y = i / n
        val warp = x % 3 == 0
        val weft = y % 3 == 0
        // Benang lusi & pakan gelap di atas dasar kain, simpulnya paling gelap
        val a = when {
            warp && weft -> 150
            warp || weft -> 90
            else -> 0
        }
        val v = 8
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/** Tekstur karpet: bintik acak halus, dibuat sekali. */
internal fun carpetBrush(): Brush {
    val n = 64
    var seed = 12345
    fun rnd(): Int { seed = seed * 1103515245 + 12345; return (seed ushr 16) and 0x7FFF }
    val px = IntArray(n * n) {
        val v = 40 + rnd() % 70
        val a = 30 + rnd() % 60
        (a shl 24) or (v shl 16) or ((v * 0.9f).toInt() shl 8) or (v * 0.8f).toInt()
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}
