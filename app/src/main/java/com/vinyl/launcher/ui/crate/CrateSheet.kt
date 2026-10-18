package com.vinyl.launcher.ui.crate

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.data.ShelfStyle
import com.vinyl.launcher.ui.components.blockTouchesBehind
import com.vinyl.launcher.ui.music.SleepTimerChip
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.playFromSearch
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.ui.cassette.CassetteArt
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.library.CassetteRack
import com.vinyl.launcher.ui.library.LibraryCatalog
import com.vinyl.launcher.ui.library.LibraryState
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.components.glass
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import com.vinyl.launcher.ui.theme.TextTertiary
import com.vinyl.launcher.ui.theme.Corner
import com.vinyl.launcher.util.rememberImage
import java.util.concurrent.TimeUnit

/**
 * Perpustakaan kaset (halaman kiri beranda; dulu "rak piringan"). Empat tab:
 * - Rak: rak dinding kayu berisi kaset ([CassetteRack]) atau Tumpukan kaset 3D ([StackShelf])
 * - Deck: antrean milik aplikasi musik; ketuk untuk melompat ke lagunya
 * - Log: daftar lagu yang pernah diputar, dikelompokkan per hari
 * - Koleksi: statistik & semua lagu yang pernah didengarkan
 * - Lokal: berkas musik di folder pilihan pengguna, diputar pemutar bawaan ([LocalTab])
 * Di bawah tab ada tombol SLEEP (timer tidur) dan (di tab Rak) pilihan gaya rak.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrateSheet(
    np: NowPlaying,
    queue: List<MediaRepository.QueueEntry>,
    history: List<PlayRecord>,
    music: MusicSettings,
    onMusicChange: (MusicSettings) -> Unit,
    onClose: () -> Unit,
    onClearHistory: () -> Unit,
    state: LibraryState,
    onToggleFavorite: (String) -> Unit,
    player: PlayerSettings,
    videoFolder: String?,
    onPlayer: ((PlayerSettings) -> PlayerSettings) -> Unit,
) {
    // Tab, pencarian, saringan, dan posisi rak hidup di [state] (dibuat di beranda) sehingga bertahan saat halaman
    // ditinggalkan, layar diputar, atau proses dipulihkan
    val tab = state.tab
    val activeKey = if (np.hasTrack) LibraryCatalog.songKey(np.title, np.artist) else null
    val deckKeys = remember(queue) { queue.map { LibraryCatalog.songKey(it.title, it.subtitle) }.toSet() }
    Box(
        Modifier
            .fillMaxSize()
            .blockTouchesBehind()
            // Lemari kaset: charcoal hangat (teks krem/putih tetap >= 4,5:1)
            .background(Brush.verticalGradient(listOf(RetroColors.CharcoalDeep, Color(0xFF1A1916)))),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Perpustakaan kaset", style = MaterialTheme.typography.headlineMedium, color = RetroColors.CreamText)
                    Text(
                        "${history.size} kaset dalam koleksi",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).glass(CircleShape).clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Kembali ke ruang TV", tint = Color.White) }
            }
            Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Segmented(listOf("Rak", "Deck", "Log", "Koleksi", "Lokal"), tab) { state.tab = it }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SleepTimerChip(np, music.sleepMinutes, { m -> onMusicChange(music.copy(sleepMinutes = m)) })
                if (tab == 0) ShelfStyleToggle(music.shelfStyle) { onMusicChange(music.copy(shelfStyle = it)) }
            }
            when (tab) {
                0 -> if (music.shelfStyle == ShelfStyle.FLOW) {
                    CassetteRack(history, activeKey, deckKeys, music.favorites, onToggleFavorite, state)
                } else {
                    RecordShelf(np, queue, history)
                }
                1 -> QueueList(np, queue)
                2 -> HistoryList(history, onClearHistory)
                3 -> Collection(history, onClearHistory)
                else -> LocalTab(np, player, videoFolder, onPlayer)
            }
        }
    }
}

/** Kontrol segmen kapsul ala iOS: pil putih bergeser ke tab terpilih. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        // Bisa digeser bila tab tidak muat (layar sempit / font besar); tetap di tengah bila muat
        Modifier.glass(RoundedCornerShape(50), fillAlpha = 0.10f).horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, t ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Color.White.copy(alpha = 0.94f) else Color.Transparent, label = "seg")
            Text(
                t,
                style = MaterialTheme.typography.labelLarge,
                color = if (sel) Color(0xFF111113) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

/** Pilihan gaya rak: rak dinding kayu (dulu "berjajar") atau tumpukan kaset 3D. */
@Composable
private fun ShelfStyleToggle(style: ShelfStyle, onChange: (ShelfStyle) -> Unit) {
    Row(
        Modifier.glass(RoundedCornerShape(50), fillAlpha = 0.06f).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, label) in listOf(ShelfStyle.FLOW to "Rak dinding", ShelfStyle.STACK to "Tumpukan")) {
            val sel = value == style
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (sel) Color(0xFF111113) else TextSecondary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White.copy(alpha = 0.94f) else Color.Transparent)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { onChange(value) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// ======================= Rak (Sebelumnya / Berikutnya) =======================

/** Satu sampul di rak. */
internal data class ShelfItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val bitmap: android.graphics.Bitmap?,
    val source: String?,
    val art: ImageBitmap?,
    val relation: Int, // <0 sebelumnya, 0 sedang diputar, >0 berikutnya
    val play: (() -> Unit)?,
)

/**
 * Rak "Tumpukan kaset": kaset bersandar bertumpuk ([StackShelf]). Sumber: antrean aplikasi musik bila tersedia, jika
 * tidak → riwayat (sebelumnya) + lagu saat ini. Di bawahnya lajur "Baru diputar".
 */
@Composable
private fun RecordShelf(np: NowPlaying, queue: List<MediaRepository.QueueEntry>, history: List<PlayRecord>) {
    val context = LocalContext.current
    val items = remember(np.title, np.artist, np.art, queue, history) {
        if (queue.isNotEmpty()) {
            val cur = queue.indexOfFirst { it.isCurrent }.let { if (it < 0) 0 else it }
            queue.mapIndexed { i, e ->
                ShelfItem("q${e.id}", e.title, e.subtitle, e.art, e.artUri, null, i - cur, { MediaRepository.playQueueItem(e.id) })
            }
        } else {
            val currentKey = "${np.title.trim().lowercase()}|${np.artist.trim().lowercase()}"
            val past = history.filter { it.key != currentKey }.take(14).reversed().mapIndexed { i, r ->
                ShelfItem("h${r.key}", r.title, r.artist, null, r.artFile, null, -(14 - i), { playFromSearch(context, r) })
            }
            val now = if (np.hasTrack) listOf(ShelfItem("now", np.title, np.artist, null, null, np.art, 0, null)) else emptyList()
            past + now
        }
    }
    if (items.isEmpty()) {
        EmptyState("Tumpukan masih kosong", "Putar beberapa lagu dulu — kaset sebelumnya dan berikutnya akan bertumpuk di sini.")
        return
    }
    val startPage = items.indexOfFirst { it.relation == 0 }.let { if (it < 0) items.lastIndex else it }
    var stackFocus by remember(items) { mutableIntStateOf(startPage) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showStrip = maxHeight > 560.dp && history.isNotEmpty()
        // Dihitung di sini, bukan di dalam Column: maxHeight/maxWidth milik BoxWithConstraintsScope tidak bisa dipakai
        // sebagai penerima implisit di dalam lambda ber-scope layout lain (DslMarker), dan kompiler menolaknya
        val stackCover = minOf(maxHeight * (if (showStrip) 0.34f else 0.42f), maxWidth * 0.5f, 300.dp)
        val focused = items.getOrNull(stackFocus)

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.15f))
            StackShelf(
                items = items,
                startPage = startPage,
                cover = stackCover,
                onFocusChange = { stackFocus = it },
                modifier = Modifier.fillMaxWidth().height(stackCover * 1.3f + 8.dp),
            )
            // Info kaset yang sedang di depan
            Spacer(Modifier.height(16.dp))
            if (focused != null) {
                val (label, color) = when {
                    focused.relation == 0 -> "SEDANG DIPUTAR" to RetroColors.Amber
                    focused.relation < 0 -> "SEBELUMNYA" to RetroColors.CreamTextSecondary
                    else -> "BERIKUTNYA" to RetroColors.CreamTextSecondary
                }
                Row(
                    Modifier
                        .widthIn(max = 560.dp)
                        .clip(Corner.l)
                        .background(RetroColors.Charcoal)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (focused.relation == 0) {
                                Icon(Icons.Rounded.GraphicEq, null, tint = RetroColors.Amber, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
                            Text("   ${stackFocus + 1} / ${items.size}", color = RetroColors.CreamTextSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(focused.title, style = MaterialTheme.typography.titleLarge, color = RetroColors.CreamText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(focused.subtitle, style = MaterialTheme.typography.bodyMedium, color = RetroColors.CreamTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (focused.relation != 0 && focused.play != null) {
                        Spacer(Modifier.width(14.dp))
                        Row(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .background(RetroColors.CreamText)
                                .clickable(role = Role.Button) { focused.play.invoke() }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = RetroColors.Ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Putar", color = RetroColors.Ink, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            // Lajur riwayat: kaset yang baru diputar
            if (showStrip) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "BARU DIPUTAR",
                    style = MaterialTheme.typography.labelMedium, color = RetroColors.CreamTextSecondary,
                    modifier = Modifier.align(Alignment.Start).padding(start = 40.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(history.take(14), key = { it.key }) { r ->
                        val img = rememberImage(null, r.artFile)
                        Column(
                            Modifier.width(120.dp).clickable(onClickLabel = "Putar ${r.title}", role = Role.Button) { playFromSearch(context, r) },
                        ) {
                            CassetteArt(
                                CassetteLabel(r.key, r.title, r.artist, img),
                                shell = if (img != null) CassetteShell.PICTURE else CassetteShell.BLACK,
                                accent = RetroColors.Amber,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.56f),
                            )
                            Text(r.title, style = MaterialTheme.typography.labelMedium, color = RetroColors.CreamText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(0.25f))
        }
    }
}

// ======================= Koleksi =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Collection(history: List<PlayRecord>, onClear: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    if (history.isEmpty()) {
        EmptyState("Koleksi masih kosong", "Lagu yang Anda dengarkan lebih dari 30 detik akan otomatis masuk ke rak ini.")
        return
    }
    val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    val playsThisWeek = history.sumOf { r -> r.playTimes.count { it >= weekAgo } }
    val hours = history.sumOf { it.listenedMs } / 3_600_000.0
    val topArtists = history.groupBy { it.artist.ifBlank { "Tidak diketahui" } }
        .mapValues { (_, v) -> v.sumOf { it.plays } }
        .entries.sortedByDescending { it.value }.take(6)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ---- Statistik ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard("$playsThisWeek", "diputar minggu ini", Modifier.weight(1f))
                StatCard("%.1f".format(hours), "jam mendengarkan", Modifier.weight(1f))
                StatCard("${history.size}", "lagu di koleksi", Modifier.weight(1f))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("ARTIS TERATAS", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topArtists.forEachIndexed { i, (artist, plays) ->
                        Text(
                            "${i + 1}  $artist  ·  $plays×",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (i == 0) Color(0xFF111113) else Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (i == 0) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TERAKHIR DIPUTAR", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1f).padding(start = 4.dp))
                TextButton(onClick = { confirmClear = true }) { Text("Hapus riwayat", color = TextSecondary) }
            }
        }
        // ---- Sampul-sampul ----
        items(history, key = { it.key }) { r ->
            val img = rememberImage(null, r.artFile)
            Column(Modifier.clip(Corner.s).clickable { playFromSearch(context, r) }) {
                Box {
                    Sleeve(img, null, highlight = false)
                    if (r.plays > 1) Text(
                        "${r.plays}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        // Di atas sampul apa pun (bukan latar yang diredam), jadi alasnya gelap, bukan kaca putih
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
                Text(r.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                Text(r.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Hapus semua riwayat?") },
        text = { Text("Koleksi dan statistik akan dikosongkan.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
    )
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/** Sampul persegi ala lengan piringan, dengan bayangan lembut. */
@Composable
internal fun Sleeve(img: ImageBitmap?, size: Dp?, highlight: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        (if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().aspectRatio(1f))
            .shadow(if (highlight) 30.dp else 14.dp, shape)
            .clip(shape)
            .background(Color(0xFF232327))
            .then(if (highlight) Modifier.border(2.dp, RetroColors.Amber, shape) else Modifier.border(0.6.dp, Color.White.copy(alpha = 0.1f), shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
    }
}

@Composable
internal fun EmptyState(title: String, body: String, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(420.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            action()
        }
    }
}

// ======================= Riwayat =======================

private data class HistoryEntry(val record: PlayRecord, val at: Long)

private fun dayLabel(at: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return when (d) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.forLanguageTag("id-ID")).format(d)
    }
}

private fun clockLabel(at: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(
        java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()),
    )

/** Riwayat lagu yang diputar, terbaru di atas, dikelompokkan per hari (gaya daftar iOS/One UI). */
@Composable
private fun HistoryList(history: List<PlayRecord>, onClear: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val entries = remember(history) {
        history.flatMap { r ->
            val times = r.playTimes.ifEmpty { listOf(r.lastPlayed) }
            times.map { HistoryEntry(r, it) }
        }.sortedByDescending { it.at }.take(300)
    }
    if (entries.isEmpty()) {
        EmptyState("Belum ada riwayat", "Lagu yang Anda dengarkan lebih dari 30 detik akan tercatat di sini.")
        return
    }
    val groups = remember(entries) { entries.groupBy { dayLabel(it.at) } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        ) {
            groups.forEach { (label, list) ->
                item(key = "h_$label") {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                        if (label == groups.keys.first()) TextButton(onClick = { confirmClear = true }) { Text("Hapus riwayat", color = TextSecondary) }
                    }
                }
                item(key = "g_$label") {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
                        list.forEachIndexed { i, e ->
                            val img = rememberImage(null, e.record.artFile)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { playFromSearch(context, e.record) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Sleeve(img, 58.dp, highlight = false)
                                Spacer(Modifier.width(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.record.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(e.record.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(clockLabel(e.at), style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Rounded.PlayArrow, "Putar ulang", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                            }
                            if (i < list.lastIndex) Box(Modifier.padding(start = 90.dp).fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.08f)))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Hapus semua riwayat?") },
        text = { Text("Riwayat, koleksi dan statistik akan dikosongkan.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
    )
}

