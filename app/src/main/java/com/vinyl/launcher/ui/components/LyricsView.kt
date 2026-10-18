package com.vinyl.launcher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.LyricsRepository
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.music.Lyrics
import com.vinyl.launcher.music.LyricsOutcome
import com.vinyl.launcher.ui.theme.InterDisplay
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** Keadaan lirik untuk lagu yang sedang diputar. Setiap keadaan punya tampilan sendiri; tidak ada yang berupa layar kosong. */
sealed interface LyricsState {
    /** Fitur lirik mati di layar ini, atau tidak ada lagu. */
    object Idle : LyricsState
    object Loading : LyricsState
    /** Pengguna mematikan pencarian lirik online. */
    object Disabled : LyricsState
    /** Server menjawab: lagu ini tidak punya lirik. */
    object NotFound : LyricsState
    /** Jaringan/server bermasalah: belum diketahui ada atau tidak. Boleh dicoba lagi. */
    object Failed : LyricsState
    class Ready(val lyrics: Lyrics) : LyricsState
}

/** [state] plus [retry] untuk tombol "Coba lagi" (melupakan hasil gagal lalu bertanya ulang ke server). */
class LyricsHandle(val state: LyricsState, val retry: () -> Unit)

/**
 * Muat lirik untuk lagu yang sedang diputar.
 * @param enabled false = layar ini tidak menampilkan lirik (tidak ada permintaan sama sekali).
 * @param online false = pengguna mematikan pencarian online (Pengaturan → Musik); tidak ada permintaan jaringan.
 */
@Composable
fun rememberLyricsState(np: NowPlaying, enabled: Boolean, online: Boolean): LyricsHandle {
    var token by remember { mutableIntStateOf(0) }
    val state by produceState<LyricsState>(LyricsState.Idle, np.title, np.artist, enabled, online, token) {
        if (!enabled || !np.hasTrack) {
            value = LyricsState.Idle
            return@produceState
        }
        value = LyricsState.Loading
        value = when (val outcome = LyricsRepository.get(np.title, np.artist, np.album, np.durationMs, online)) {
            is LyricsOutcome.Found -> LyricsState.Ready(outcome.lyrics)
            LyricsOutcome.NotFound -> LyricsState.NotFound
            LyricsOutcome.Failed -> LyricsState.Failed
            LyricsOutcome.Disabled -> LyricsState.Disabled
        }
    }
    val title = np.title
    val artist = np.artist
    val retry: () -> Unit = {
        LyricsRepository.forget(title, artist)
        token += 1
    }
    return LyricsHandle(state, retry)
}

/** Lirik tersinkron saja (null bila tidak ada / belum dimuat / tidak tersinkron); untuk tampilan dua baris. */
@Composable
fun rememberLyrics(np: NowPlaying, enabled: Boolean, online: Boolean): Lyrics? =
    (rememberLyricsState(np, enabled, online).state as? LyricsState.Ready)?.lyrics

/**
 * Dua baris lirik ala Apple Music: baris aktif tebal & terang, baris berikutnya redup.
 * Pergantian baris dianimasikan naik ke atas (tanpa gerak bila animasi sistem dimatikan).
 */
@Composable
fun LyricsLines(
    np: NowPlaying,
    lyrics: Lyrics?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    align: TextAlign = TextAlign.Center,
    /** Warna baris aktif & berikutnya; bawaan untuk latar gelap, ruang TV terang memakai tinta gelap. */
    primary: Color = TextPrimary,
    secondary: Color = TextSecondary,
) {
    if (lyrics == null || !lyrics.hasSynced) return
    val reduceMotion = rememberReduceMotion()
    var index by remember(lyrics) { mutableIntStateOf(lyrics.indexAt(np.positionAt())) }
    LaunchedEffect(np, lyrics) {
        while (true) {
            // sedikit lebih awal (150 ms) agar terasa pas dengan suara
            index = lyrics.indexAt(np.positionAt() + 150)
            if (!np.isPlaying) break
            delay(120)
        }
    }
    val horizontal = when (align) {
        TextAlign.Start -> Alignment.Start
        TextAlign.End -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            if (reduceMotion) fadeIn(snap()) togetherWith fadeOut(snap())
            else (slideInVertically(tween(420)) { it / 2 } + fadeIn(tween(420))) togetherWith
                (slideOutVertically(tween(380)) { -it / 2 } + fadeOut(tween(260)))
        },
        modifier = modifier,
        label = "lyrics",
    ) { i ->
        Column(horizontalAlignment = horizontal) {
            val current = lyrics.synced.getOrNull(i)?.text.orEmpty().ifBlank { if (i >= 0) "♪" else "" }
            val next = lyrics.synced.getOrNull(i + 1)?.text.orEmpty()
            Text(
                current,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = fontSize, letterSpacing = (-0.015).em,
                ),
                color = primary,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (next.isNotBlank()) Text(
                next,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize * 0.68f, fontWeight = FontWeight.SemiBold),
                color = secondary,    // 38% putih dulu < 4,5:1; teks sekunder dijamin BackdropDim / BackdropLift
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
