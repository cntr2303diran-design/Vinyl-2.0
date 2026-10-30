package com.vinyl.launcher.ui.room.stage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.PosterSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sisi terpanjang foto poster setelah di-decode: poster kecil di dinding, jadi 512 px cukup dan hemat memori. */
private const val PHOTO_MAX_PX = 512

/** Muat foto poster dari folder internal dengan downsampling; null bila path kosong atau berkas rusak/hilang. */
@Composable
internal fun rememberPosterPhoto(path: String?): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_PX) sample *= 2
                val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
                // Foto kamera sering disimpan miring dengan tag EXIF; putar supaya tegak
                val turn = PosterChoice.exifRotation(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
                val upright = if (turn == 0) raw else {
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
                        .also { if (it !== raw) raw.recycle() }
                }
                upright.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}

/**
 * Poster berbingkai di dinding. Isinya foto pilihan sendiri atau cover lagu yang sedang diputar ([PosterChoice]). Ketuk =
 * pilih foto (Photo Picker sistem), tahan = kembali ke cover album dan hapus foto yang tersimpan.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StageFrame(
    spot: PropSpot,
    look: PropLook,
    poster: PosterSettings,
    art: ImageBitmap?,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val photo = rememberPosterPhoto(poster.photoPath)
    val shown = PosterChoice.shown(poster, photoLoaded = photo != null, hasArt = art != null)
    val image = when (shown) { PosterShown.PHOTO -> photo; PosterShown.ALBUM -> art; PosterShown.EMPTY -> null }
    val b = spot.bounds
    Canvas(Modifier.offset(b.left.dp, b.top.dp).size(b.width.dp, b.height.dp)) { drawPoster(look, image) }
    val h = spot.hit
    val hasPhoto = poster.photoPath != null
    Box(
        Modifier
            .offset(h.left.dp, h.top.dp)
            .size(h.width.dp, h.height.dp)
            .semantics {
                contentDescription = "Poster di dinding"
                stateDescription = when (shown) {
                    PosterShown.PHOTO -> "Menampilkan foto pilihan Anda"
                    PosterShown.ALBUM -> "Menampilkan cover album"
                    PosterShown.EMPTY -> "Kosong"
                }
                customActions = buildList {
                    add(CustomAccessibilityAction("Pilih foto") { onPick(); true })
                    if (hasPhoto) add(CustomAccessibilityAction("Pakai cover album dan hapus foto") { onClear(); true })
                }
            }
            .combinedClickable(
                enabled = enabled, onClickLabel = "Pilih foto", role = Role.Button, onClick = onPick,
                onLongClickLabel = if (hasPhoto) "Pakai cover album dan hapus foto" else null,
                onLongClick = if (hasPhoto) onClear else null,
            ),
    )
}

private fun DrawScope.drawPoster(look: PropLook, image: ImageBitmap?) {
    val d = density
    val w = size.width
    val h = size.height
    val rim = 3.2f * d
    // Bayangan, lalu bingkai berkilau
    drawRect(Color.Black.copy(alpha = 0.28f), topLeft = Offset(0f, 1.5f * d), size = Size(w, h))
    drawRect(Brush.linearGradient(listOf(look.rim, look.rimDark), start = Offset.Zero, end = Offset(w, h)), size = Size(w, h))
    // Passe-partout krem, lalu isi
    val mat = Color(0xFFEFE6D2)
    val mx = rim + 2.2f * d
    drawRect(mat, topLeft = Offset(mx, mx), size = Size(w - 2 * mx, h - 2 * mx))
    val px = mx + 3f * d
    val pw = w - 2 * px
    val ph = h - 2 * px
    if (pw <= 0f || ph <= 0f) return
    if (image != null) {
        val (fx, fy) = PosterChoice.cropFraction(image.width, image.height, pw, ph)
        val sw = (image.width * fx).toInt().coerceAtLeast(1)
        val sh = (image.height * fy).toInt().coerceAtLeast(1)
        drawImage(
            image,
            srcOffset = IntOffset((image.width - sw) / 2, (image.height - sh) / 2), srcSize = IntSize(sw, sh),
            dstOffset = IntOffset(px.toInt(), px.toInt()), dstSize = IntSize(pw.toInt().coerceAtLeast(1), ph.toInt().coerceAtLeast(1)),
        )
        // Kilau kaca tipis
        drawRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = Offset(px, px), end = Offset(px + pw * 0.6f, px + ph * 0.6f)),
            topLeft = Offset(px, px), size = Size(pw, ph),
        )
    } else {
        // Kertas polos dengan tanda tambah: tampak kosong tapi jelas bisa diketuk
        drawRect(Color(0xFFD9CFB8), topLeft = Offset(px, px), size = Size(pw, ph))
        val c = Offset(px + pw / 2f, px + ph / 2f)
        val arm = minOf(pw, ph) * 0.18f
        val ink = Color(0xFF6B5E48)
        drawLine(ink, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 2f * d, cap = StrokeCap.Round)
        drawLine(ink, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 2f * d, cap = StrokeCap.Round)
    }
    drawRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(px, px), size = Size(pw, ph), style = Stroke(width = 0.8f * d))
}
