package com.vinyl.launcher.ui.crate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.music.QueuePlan
import com.vinyl.launcher.ui.components.glass
import com.vinyl.launcher.ui.theme.Accent
import com.vinyl.launcher.ui.theme.Corner
import com.vinyl.launcher.ui.theme.Dimen
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import com.vinyl.launcher.ui.theme.TextTertiary
import com.vinyl.launcher.ui.theme.Tokens
import com.vinyl.launcher.util.rememberImage

/**
 * Antrean "Berikutnya" milik aplikasi musik yang sedang aktif, persis seperti yang dibagikan aplikasi itu.
 * Ketuk sebuah lagu untuk melompat ke sana. Launcher tidak menyimpan atau mengubah antrean (tidak bisa mengacak atau
 * menghapus item): itu wewenang aplikasi musik.
 *
 * Tidak semua aplikasi musik membagikan antreannya; saat kosong, layar ini menjelaskannya dan menawarkan
 * membuka aplikasi musik, bukan menampilkan daftar kosong tanpa penjelasan.
 */
@Composable
internal fun QueueList(np: NowPlaying, queue: List<MediaRepository.QueueEntry>) {
    val context = LocalContext.current
    if (queue.isEmpty()) {
        EmptyState(
            if (np.hasTrack) "Antrean tidak dibagikan" else "Antrean kosong",
            if (np.hasTrack) "Aplikasi musik ini tidak membagikan daftar antreannya ke launcher. Lihat antrean di dalam aplikasi musiknya."
            else "Putar lagu di aplikasi musik; antreannya akan muncul di sini bila aplikasi itu membagikannya.",
        ) {
            Text(
                "Buka aplikasi musik",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                modifier = Modifier
                    .padding(top = Dimen.l)
                    .minimumInteractiveComponentSize()
                    .clip(Corner.pill)
                    .glass(Corner.pill, fillAlpha = Tokens.A11y.MAX_GLASS_FILL)
                    .clickable(role = Role.Button) { MediaRepository.openMusicApp(context) }
                    .padding(horizontal = Dimen.l, vertical = Dimen.s),
            )
        }
        return
    }

    val currentIndex = queue.indexOfFirst { it.isCurrent }
    val listState = rememberLazyListState()
    // Mulai dari satu lagu sebelum yang sedang diputar, dan ikuti saat lagu berganti
    LaunchedEffect(currentIndex, queue.size) {
        QueuePlan.scrollTarget(currentIndex, queue.size)?.let { listState.scrollToItem(it) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Kunci memakai indeks: aplikasi musik boleh memberi semua item id yang sama (UNKNOWN_ID), dan kunci ganda membuat daftar crash
            itemsIndexed(queue, key = { index, _ -> index }) { index, entry ->
                QueueRow(index, entry, playable = QueuePlan.isPlayable(entry.id))
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QueueRow(index: Int, entry: MediaRepository.QueueEntry, playable: Boolean) {
    val img = rememberImage(entry.art, entry.artUri)
    val shape = Corner.l
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (entry.isCurrent) Accent.copy(alpha = 0.16f) else Color.Transparent, shape)
            .then(
                if (playable && !entry.isCurrent) Modifier.clickable(onClickLabel = "Putar lagu ini", role = Role.Button) { MediaRepository.playQueueItem(entry.id) }
                else Modifier
            )
            .semantics { selected = entry.isCurrent }
            .padding(horizontal = Dimen.m, vertical = Dimen.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = TextTertiary,
            modifier = Modifier.width(28.dp),
        )
        Box(Modifier.size(52.dp)) { Sleeve(img, 52.dp, highlight = entry.isCurrent) }
        Column(Modifier.weight(1f).padding(horizontal = Dimen.m)) {
            Text(
                entry.title.ifBlank { "Tanpa judul" },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle.isNotBlank()) Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.isCurrent) {
            Icon(Icons.Rounded.GraphicEq, "Sedang diputar", tint = Accent, modifier = Modifier.size(22.dp))
        }
    }
}
