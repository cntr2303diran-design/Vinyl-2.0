package com.vinyl.launcher.ui.lyrics

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.music.AutoFollow
import com.vinyl.launcher.music.LineLook
import com.vinyl.launcher.music.Lyrics
import com.vinyl.launcher.music.LyricsFocus
import com.vinyl.launcher.ui.components.AmbientBackdrop
import com.vinyl.launcher.ui.components.LyricsHandle
import com.vinyl.launcher.ui.components.LyricsState
import com.vinyl.launcher.ui.components.blockTouchesBehind
import com.vinyl.launcher.ui.components.glass
import com.vinyl.launcher.ui.components.rememberLyricsState
import com.vinyl.launcher.ui.music.SleepTimerChip
import com.vinyl.launcher.ui.theme.Corner
import com.vinyl.launcher.ui.theme.Dimen
import com.vinyl.launcher.ui.theme.Ease
import com.vinyl.launcher.ui.theme.InterDisplay
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import com.vinyl.launcher.ui.theme.Tokens
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** Aksi yang bisa dilakukan layar lirik; diisi oleh pemanggil agar layar ini tidak mengenal ViewModel. */
class LyricsActions(
    val close: () -> Unit,
    val seek: (Long) -> Unit,
    val togglePlay: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    /** Dipanggil saat pengguna menyalakan pencarian lirik online dari layar ini. */
    val enableOnline: () -> Unit,
    /** Durasi sleep timer yang baru dipilih, untuk diingat di Pengaturan → Musik. */
    val rememberSleepMinutes: (Int) -> Unit,
)

/**
 * Lirik layar penuh ala Apple Music: baris yang sedang dinyanyikan besar dan terang, baris lain mengecil dan
 * meredup, baris jauh sedikit mengabur. Gulir otomatis mengikuti lagu, berhenti saat jari menyentuh daftar dan
 * lanjut 3,5 detik setelah dilepas. Ketuk sebuah baris untuk melompat ke sana.
 *
 * Setiap keadaan punya tampilan sendiri: memuat, dimatikan, tidak ditemukan, gagal (dengan tombol coba lagi),
 * instrumental, lirik tanpa waktu, dan lirik tersinkron.
 */
@Composable
fun LyricsScreen(
    np: NowPlaying,
    fallback: Color,
    music: MusicSettings,
    actions: LyricsActions,
    modifier: Modifier = Modifier,
) {
    val handle = rememberLyricsState(np, enabled = true, online = music.lyricsOnline)
    Box(modifier.fillMaxSize().blockTouchesBehind()) {
        // Teks lirik tak aktif lebih redup dari teks sekunder standar, jadi peredam latar dihitung dari daftar khusus
        AmbientBackdrop(
            art = np.art.takeIf { np.hasTrack },
            swatches = np.swatches,
            fallback = fallback,
            animate = np.isPlaying,
            dim = 0.5f,
            cinematic = music.cinematicColors,
            texts = LyricsFocus.BACKDROP_TEXTS,
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Header(np, actions.close)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                    Body(np, handle, music, actions)
                }
            }
            Footer(np, music, actions)
        }
    }
}

@Composable
private fun Header(np: NowPlaying, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Dimen.xl, end = Dimen.l, top = Dimen.l, bottom = Dimen.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(Corner.m).background(Color(0xFF232327))) {
            val art = np.art
            if (np.hasTrack && art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = Dimen.m)) {
            Text(
                if (np.hasTrack) np.title else "Lirik",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (np.hasTrack) Text(
                np.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(Dimen.minTouch).clip(CircleShape).glass(CircleShape).clickable(onClickLabel = "Tutup lirik", role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Tutup lirik", tint = TextPrimary) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(np: NowPlaying, music: MusicSettings, actions: LyricsActions) {
    // FlowRow: pada layar sempit atau font 200% kapsul timer pindah ke baris sendiri, bukan menjepit tombol putar
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Dimen.xl, vertical = Dimen.m),
        verticalArrangement = Arrangement.spacedBy(Dimen.s),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SleepTimerChip(np, music.sleepMinutes, actions.rememberSleepMinutes)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimen.s), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Rounded.SkipPrevious, "Lagu sebelumnya", actions.previous)
            RoundButton(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.isPlaying) "Jeda" else "Putar", actions.togglePlay)
            RoundButton(Icons.Rounded.SkipNext, "Lagu berikutnya", actions.next)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Dimen.minTouch).clip(CircleShape).glass(CircleShape, fillAlpha = 0.12f).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = TextPrimary) }
}

// ------------------------------------------------------------------ isi

@Composable
private fun Body(np: NowPlaying, handle: LyricsHandle, music: MusicSettings, actions: LyricsActions) {
    if (!np.hasTrack) {
        Message("Tidak ada lagu yang diputar", "Putar lagu di aplikasi musik; liriknya akan muncul di sini.")
        return
    }
    when (val state = handle.state) {
        LyricsState.Idle, LyricsState.Loading -> Message("Mencari lirik…", "Hanya judul, artis, album, dan durasi lagu yang dikirim ke lrclib.net.") {
            CircularProgressIndicator(color = TextPrimary, modifier = Modifier.padding(top = Dimen.l))
        }
        LyricsState.Disabled -> Message(
            "Pencarian lirik online mati",
            "Menyalakannya mengirim judul, artis, album, dan durasi lagu ke lrclib.net. Tidak ada data lain yang dikirim. Bisa dimatikan lagi di Pengaturan → Musik.",
        ) { PillButton("Nyalakan pencarian lirik", actions.enableOnline) }
        LyricsState.NotFound -> Message("Lirik tidak ditemukan", "lrclib.net belum punya lirik untuk lagu ini.")
        LyricsState.Failed -> Message("Tidak bisa mengambil lirik", "Periksa koneksi internet Anda, lalu coba lagi.") {
            PillButton("Coba lagi", handle.retry)
        }
        is LyricsState.Ready -> {
            val lyrics = state.lyrics
            when {
                lyrics.instrumental -> Message("♪ Instrumental", "Lagu ini tidak memiliki lirik.")
                lyrics.hasSynced -> SyncedLyrics(np, lyrics, music.lyricsBlur, actions.seek)
                lyrics.plainLines.isNotEmpty() -> PlainLyrics(lyrics.plainLines)
                else -> Message("Lirik tidak ditemukan", "lrclib.net belum punya lirik untuk lagu ini.")
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(Dimen.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Dimen.s))
        content()
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
        modifier = Modifier
            .padding(top = Dimen.l)
            .minimumInteractiveComponentSize()
            .clip(Corner.pill)
            .glass(Corner.pill, fillAlpha = Tokens.A11y.MAX_GLASS_FILL)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimen.l, vertical = Dimen.s),
    )
}

/** Pudar tepi atas dan bawah daftar agar baris tidak terpotong kasar. */
private fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.07f to Color.Black,
                0.90f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun SyncedLyrics(np: NowPlaying, lyrics: Lyrics, blurEnabled: Boolean, onSeek: (Long) -> Unit) {
    val lines = lyrics.synced
    val reduceMotion = rememberReduceMotion()
    val listState = rememberLazyListState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var active by remember(lyrics) { mutableIntStateOf(lyrics.indexAt(np.positionAt())) }
    LaunchedEffect(np, lyrics) {
        while (true) {
            active = lyrics.indexAt(np.positionAt() + 150)   // sedikit lebih awal agar terasa pas dengan suara
            if (!np.isPlaying) break
            delay(120)
        }
    }

    // Sentuhan pengguna pada daftar: berhenti mengikuti selama disentuh dan sebentar sesudahnya
    var touching by remember { mutableStateOf(false) }
    var lastTouchMs by remember { mutableStateOf<Long?>(null) }

    var placed by remember(lyrics) { mutableStateOf(false) }
    LaunchedEffect(active, lyrics) {
        while (!AutoFollow.shouldFollow(lastTouchMs, SystemClock.elapsedRealtime(), touching)) delay(250)
        val target = active.coerceIn(0, lines.lastIndex)
        if (reduceMotion || !placed) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        placed = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // Baris aktif berhenti di 30% dari atas; ruang bawah besar agar baris terakhir pun bisa naik ke sana
            contentPadding = PaddingValues(start = Dimen.xl, end = Dimen.xl, top = maxHeight * 0.30f, bottom = maxHeight * 0.55f),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        touching = true
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        touching = false
                        lastTouchMs = SystemClock.elapsedRealtime()
                    }
                }
                .edgeFade(),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                SyncedLine(
                    text = line.text,
                    look = LyricsFocus.look(LyricsFocus.distance(index, active), blurEnabled),
                    active = index == active,
                    rtl = rtl,
                    reduceMotion = reduceMotion,
                    onClick = { onSeek(line.timeMs) },
                )
            }
        }
    }
}

@Composable
private fun SyncedLine(text: String, look: LineLook, active: Boolean, rtl: Boolean, reduceMotion: Boolean, onClick: () -> Unit) {
    val ms = if (reduceMotion) 0 else Tokens.Motion.MEDIUM_MS
    val lineAlpha by animateFloatAsState(look.alpha, tween(ms, easing = Ease.standard), label = "lineAlpha")
    val lineScale by animateFloatAsState(look.scale, tween(ms, easing = Ease.standard), label = "lineScale")
    val blurDp by animateFloatAsState(look.blurDp, tween(ms, easing = Ease.standard), label = "lineBlur")
    // Kaburnya hanya nyata di Android 12+; di bawah itu Modifier.blur tidak berefek, jadi dilewati saja
    val blurModifier = if (blurDp > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(blurDp.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else Modifier
    Text(
        text.ifBlank { "♪" },
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp,
        ),
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = "Lompat ke baris ini", role = Role.Button, onClick = onClick)
            .graphicsLayer {
                alpha = lineAlpha
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
            }
            .then(blurModifier)
            .padding(vertical = Dimen.s)
            .semantics { selected = active },
    )
}

@Composable
private fun PlainLyrics(lines: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().edgeFade(),
        contentPadding = PaddingValues(start = Dimen.xl, end = Dimen.xl, top = Dimen.l, bottom = Dimen.xxl),
    ) {
        item(key = "note") {
            Text(
                "Lirik ini tidak bertanda waktu, jadi tidak ikut bergulir otomatis.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = Dimen.m),
            )
        }
        itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
            if (line.isBlank()) Spacer(Modifier.height(Dimen.l))
            else Text(
                line,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            )
        }
    }
}
