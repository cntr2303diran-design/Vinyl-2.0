package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.ReelSpeed
import com.vinyl.launcher.ui.cassette.CassetteFaceUnits
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.cassette.CassetteSwap
import com.vinyl.launcher.ui.cassette.Reels
import com.vinyl.launcher.ui.cassette.drawCassetteFace
import com.vinyl.launcher.ui.cassette.rememberCassetteSwap
import com.vinyl.launcher.ui.cassette.rememberLabelText
import com.vinyl.launcher.ui.cassette.rememberReelAngles
import com.vinyl.launcher.ui.cassette.shellLook
import android.graphics.Matrix as AndroidMatrix

/**
 * Kaset 3D yang berdiri miring di atas meja, di samping TV: muka depan berlabel spidol dengan jendela pita (gulungan
 * berputar hanya saat musik berjalan), tebal cangkang di sisi & atap, dan bayangan di meja. Diterangi layar TV dari
 * kiri. Ketuk = putar/jeda. Saat lagu berganti, kaset lama terangkat keluar dan kaset baru turun masuk.
 */
@Composable
fun StageCassette(
    f: StageLayout.Frame,
    label: CassetteLabel,
    hasSession: Boolean,
    playing: Boolean,
    progress: Float,
    timeText: String,
    shell: CassetteShell,
    speed: ReelSpeed,
    accent: Color,
    screenGlow: Color,
    glow: Float,
    motionAllowed: Boolean,
    enabled: Boolean,
    onPlayPause: () -> Unit,
) {
    val swapState by rememberCassetteSwap(label, motionAllowed)
    val (shown, frame) = swapState
    val (rl, rr) = Reels.radii(progress)
    val angles = rememberReelAngles(playing && motionAllowed, speed, progress)
    val text = rememberLabelText(shown.title.ifBlank { "—" }, shown.artist, timeText)
    val look = shellLook(shell, accent)
    // Area gambar: batas kaset + ruang untuk bayangan & animasi angkat
    val area = remember(f) {
        val b = f.cassetteBounds
        SRect(b.left - 16f, b.top - b.height * 0.9f, b.right + 16f, b.bottom + 10f)
    }
    val lift = when (frame.phase) {
        CassetteSwap.Phase.EJECT -> frame.progress
        CassetteSwap.Phase.INSERT -> 1f - frame.progress
        CassetteSwap.Phase.IDLE -> 0f
    }
    val tapeName = if (shown.key == null) "Kaset kosong" else "Kaset: ${shown.title}" + if (shown.artist.isNotBlank()) ", ${shown.artist}" else ""
    Box(
        Modifier
            .offset(f.cassetteBounds.left.dp, f.cassetteBounds.top.dp)
            .size(f.cassetteBounds.width.dp, f.cassetteBounds.height.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = tapeName
                stateDescription = if (!hasSession) "Tidak ada lagu" else if (playing) "Diputar" else "Dijeda"
            }
            .clickable(
                enabled = enabled,
                onClickLabel = if (!hasSession) "Buka aplikasi musik" else if (playing) "Jeda" else "Putar",
                role = Role.Button,
                onClick = onPlayPause,
            ),
    )
    Canvas(
        Modifier
            .offset(area.left.dp, area.top.dp)
            .size(area.width.dp, area.height.dp)
            .graphicsLayer { alpha = 1f - lift },
    ) {
        val d = density
        val origin = Offset(area.left * d, area.top * d)
        val solid = f.cassette.copy(origin = f.cassette.origin + V3(0f, lift * StageLayout.CASSETTE_H * 0.8f, 0f))
        fun proj(v: V3) = f.camera.project(v).px(d) - origin
        fun path(vs: List<V3>) = Path().apply {
            vs.forEachIndexed { i, v -> val p = proj(v); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }

        // Bayangan di meja: tapak kaset (sedikit lebih besar), tidak ikut terangkat
        // Cahaya layar TV dari kiri: bayangan jatuh ke kanan-belakang kaset yang bersandar
        val cas = f.cassette
        val foot = Solid(cas.w * 1.08f, 0.01f, cas.d * 3.2f, cas.origin + V3(1.6f, 0f, cas.d * 1.4f), cas.yaw, 0f)
        drawPath(path(foot.quad(Face.TOP)), Color.Black.copy(alpha = 0.45f * (1f - lift)))

        val tvCenter = V3(f.tv.origin.x, StageLayout.STAND_H + StageLayout.TV_H * 0.55f, StageLayout.TV_FRONT_Z)
        val toLight = tvCenter - solid.toWorld(V3(0f, StageLayout.CASSETTE_H / 2f, 0f))
        val g = glow.coerceIn(0f, 1f)
        for (face in solid.visibleFaces(f.camera.eye)) {
            val q = solid.quad(face)
            when (face) {
                Face.FRONT -> {
                    val pts = q.map(::proj)
                    val m = AndroidMatrix()
                    m.setPolyToPoly(
                        floatArrayOf(0f, 0f, CassetteFaceUnits.W, 0f, CassetteFaceUnits.W, CassetteFaceUnits.H, 0f, CassetteFaceUnits.H), 0,
                        floatArrayOf(pts[0].x, pts[0].y, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y), 0, 4,
                    )
                    drawIntoCanvas { c ->
                        c.save()
                        c.nativeCanvas.concat(m)
                        drawCassetteFace(
                            look, text, accent, rl, rr, angles.left, angles.right, shown.art,
                            shell == CassetteShell.PICTURE, light = 0.35f + 0.65f * g, glow = screenGlow,
                        )
                        c.restore()
                    }
                }
                else -> {
                    // Tebal cangkang: warna badan diberi bayangan menurut arah cahaya layar TV
                    val shade = lambert(solid.normal(face), toLight, ambient = 0.30f)
                    val base = if (look.translucent) look.bodyDark.copy(alpha = 0.9f) else look.body
                    val c = lerp(Color.Black, base.copy(alpha = 1f), 0.35f + 0.65f * shade)
                    val p = path(q)
                    drawPath(p, c)
                    // Rusuk cetakan di sisi tebal
                    if (face == Face.LEFT || face == Face.RIGHT) {
                        for (i in 1 until 6) {
                            val t = i / 6f
                            val a = proj(q[0] + (q[3] - q[0]) * t)
                            val b = proj(q[1] + (q[2] - q[1]) * t)
                            drawLine(Color.Black.copy(alpha = 0.35f), a, b, strokeWidth = 0.8f * d)
                        }
                    }
                    drawPath(p, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = proj(q[0]), end = proj(q[2])))
                    drawPath(p, Color.Black.copy(alpha = 0.5f), style = Stroke(width = 0.8f * d))
                }
            }
        }
    }
}
