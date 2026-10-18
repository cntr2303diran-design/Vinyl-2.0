package com.vinyl.launcher.ui.library

import androidx.compose.ui.unit.IntOffset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.media.playFromSearch
import com.vinyl.launcher.ui.cassette.CassetteArt
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.util.rememberImage

/**
 * Keadaan perpustakaan kaset yang harus bertahan saat halaman ditinggalkan (posisi gulir rak, pencarian, saringan,
 * tab). Dibuat di tingkat beranda, bukan di dalam halaman, karena halaman yang tidak terlihat tidak digambar.
 */
@Stable
class LibraryState(
    val grid: LazyGridState,
    tabState: MutableState<Int>,
    queryState: MutableState<String>,
    filterState: MutableState<RackFilter>,
) {
    var tab by tabState
    var query by queryState
    var filter by filterState
    var selected by mutableStateOf<String?>(null)
}

/** Tab, pencarian, dan saringan disimpan dengan rememberSaveable (bertahan rotasi); posisi gulir lewat LazyGridState. */
@Composable
fun rememberLibraryState(): LibraryState {
    val grid = rememberLazyGridState()
    val tab = rememberSaveable { mutableStateOf(0) }
    val query = rememberSaveable { mutableStateOf("") }
    val filter = rememberSaveable { mutableStateOf(RackFilter.ALL) }
    return remember(grid, tab, query, filter) { LibraryState(grid, tab, query, filter) }
}

private val spinePalette = listOf(
    Color(0xFFB5523B), Color(0xFF3E6E8E), Color(0xFF6F8F4E), Color(0xFFC48A2C),
    Color(0xFF7A4E8C), Color(0xFF2F7F73), Color(0xFF9A3F5F), Color(0xFF5B6770),
)

private fun spineColor(key: String): Color = spinePalette[Math.floorMod(key.hashCode(), spinePalette.size)]

/**
 * Rak dinding kayu: kompartemen walnut berisi punggung kaset. Ketuk kaset → kaset tertarik keluar dan detailnya
 * tampil (putar, favorit, lagu lain dari album yang sama di koleksi).
 */
@Composable
fun CassetteRack(
    history: List<PlayRecord>,
    activeKey: String?,
    deckKeys: Set<String>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    state: LibraryState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = remember(history) { System.currentTimeMillis() }
    val all = remember(history, favorites, activeKey, deckKeys) { LibraryCatalog.entries(history, favorites, activeKey, deckKeys) }
    val arranged = remember(all, state.filter, state.query) { LibraryCatalog.arrange(all, state.filter, state.query) }
    val compartments = remember(arranged, state.filter, now) { LibraryCatalog.compartments(arranged, state.filter, now) }

    // Box: lapisan detail digambar DI ATAS rak (di dalam Column ia akan tersusun di bawah daftar, di luar layar)
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Pencarian katalog & saringan
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it.take(80) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = RetroColors.CreamTextSecondary) },
            placeholder = { Text("Cari label: judul, artis, album", color = RetroColors.CreamTextSecondary) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RetroColors.CreamText, unfocusedTextColor = RetroColors.CreamText,
                focusedBorderColor = RetroColors.Amber, unfocusedBorderColor = RetroColors.CreamTextSecondary.copy(alpha = 0.6f),
                cursorColor = RetroColors.Amber,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).semantics { contentDescription = "Cari kaset" },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((f, name) in listOf(
                RackFilter.ALL to "Semua", RackFilter.ARTIST to "Artis", RackFilter.ALBUM to "Album",
                RackFilter.RECENT to "Terakhir diputar", RackFilter.FAVORITE to "Favorit",
            )) {
                val sel = state.filter == f
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) RetroColors.Ink else RetroColors.CreamText,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) RetroColors.CreamText else Color.Transparent)
                        .border(1.dp, RetroColors.CreamTextSecondary.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .selectable(selected = sel, role = Role.RadioButton) { state.filter = f }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        if (compartments.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        history.isEmpty() -> "Rak masih kosong. Lagu yang Anda dengarkan lebih dari 30 detik otomatis menjadi kaset di sini."
                        state.filter == RackFilter.FAVORITE && state.query.isBlank() -> "Belum ada kaset favorit. Ketuk sebuah kaset lalu tandai bintang."
                        else -> "Tidak ada kaset yang cocok."
                    },
                    color = RetroColors.CreamTextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                state = state.grid,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                var lastSection: String? = null
                compartments.forEachIndexed { i, comp ->
                    if (comp.section.isNotEmpty() && comp.section != lastSection) {
                        val title = comp.section
                        item(key = "s:$i:$title", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = RetroColors.Amber,
                                fontFamily = MonoFamily,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                            )
                        }
                        lastSection = comp.section
                    }
                    item(key = "c:$i:${comp.entries.first().key}") {
                        CompartmentBox(comp, state.selected) { state.selected = it }
                    }
                }
            }
        }
    }

    // Detail kaset terpilih
    val chosen = all.firstOrNull { it.key == state.selected }
    if (chosen != null) {
        CassetteDetail(
            entry = chosen,
            sameAlbum = remember(all, chosen) { LibraryCatalog.sameAlbum(all, chosen) },
            onPlay = { r -> playFromSearch(context, r) },
            onToggleFavorite = { onToggleFavorite(chosen.key) },
            onPick = { state.selected = it },
            onClose = { state.selected = null },
        )
    }
    }
}

/** Satu kompartemen walnut: kaset bertumpuk dengan punggung menghadap keluar; slot kosong tetap terlihat. */
@Composable
private fun CompartmentBox(comp: Compartment, selected: String?, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroColors.Walnut)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A1C12))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Pelat kuningan berhuruf di atas kompartemen
        Text(
            LibraryCatalog.plate(comp),
            color = RetroColors.Ink,
            fontFamily = MonoFamily,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFC9A55A))
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )
        comp.entries.forEach { e -> Spine(e, pulled = e.key == selected) { onSelect(e.key) } }
        repeat(LibraryCatalog.PER_COMPARTMENT - comp.entries.size) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.25f)))
        }
    }
}

/** Punggung kaset di rak: garis warna, cover kecil, judul & artis, penanda diputar / deck / favorit. */
@Composable
private fun Spine(e: RackEntry, pulled: Boolean, onClick: () -> Unit) {
    val r = e.record
    val shift by animateDpAsState(if (pulled) 14.dp else 0.dp, label = "pull")
    val img = rememberImage(null, r.artFile)
    val markers = buildList {
        if (e.active) add("sedang diputar")
        if (e.inDeck) add("ada di deck")
        if (e.favorite) add("favorit")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .offset { IntOffset(shift.roundToPx(), 0) }
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClickLabel = "Tarik kaset ini", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${r.title}, ${r.artist}. ${LibraryCatalog.catalogNumber(e.number)}"
                if (markers.isNotEmpty()) stateDescription = markers.joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().heightIn(min = 48.dp).background(spineColor(e.key)))
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(RetroColors.CreamText)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (img != null) {
                Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(r.title, color = RetroColors.Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.artist, color = RetroColors.InkSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (e.active) Text("▶", color = Color(0xFF8A5A10), style = MaterialTheme.typography.labelLarge)
            if (e.inDeck) Text(" DECK", color = RetroColors.InkSecondary, style = MaterialTheme.typography.labelSmall, fontFamily = MonoFamily)
            if (e.favorite) Icon(Icons.Rounded.Star, null, tint = Color(0xFF8A5A10), modifier = Modifier.size(16.dp))
        }
    }
}

/** Kaset yang ditarik keluar, diperbesar, dengan aksi. */
@Composable
private fun CassetteDetail(
    entry: RackEntry,
    sameAlbum: List<RackEntry>,
    onPlay: (PlayRecord) -> Unit,
    onToggleFavorite: () -> Unit,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val r = entry.record
    val img = rememberImage(null, r.artFile)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClickLabel = "Kembalikan kaset ke rak", role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RetroColors.Charcoal)
                // Ketukan di dalam kartu tidak menembus ke lapisan gelap (yang menutup detail)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CassetteArt(
                CassetteLabel(entry.key, r.title, r.artist, img),
                shell = if (img != null) CassetteShell.PICTURE else CassetteShell.BLACK,
                accent = spineColor(entry.key),
                modifier = Modifier.fillMaxWidth().aspectRatio(1.56f),
            )
            Text(r.title, color = RetroColors.CreamText, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(r.artist, r.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = RetroColors.CreamTextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${LibraryCatalog.catalogNumber(entry.number)} · diputar ${r.plays}×",
                color = RetroColors.Amber, fontFamily = MonoFamily, style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(RetroColors.CreamText)
                        .clickable(onClickLabel = "Putar ${r.title}", role = Role.Button) { onPlay(r) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = RetroColors.Ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Putar", color = RetroColors.Ink, style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, RetroColors.CreamTextSecondary, RoundedCornerShape(50))
                        .clickable(onClickLabel = if (entry.favorite) "Buang dari favorit" else "Tandai favorit", role = Role.Button, onClick = onToggleFavorite)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (entry.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = RetroColors.Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (entry.favorite) "Favorit" else "Tandai favorit", color = RetroColors.CreamText, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(50)).clickable(onClickLabel = "Tutup", role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Tutup", tint = RetroColors.CreamText) }
            }
            if (sameAlbum.isNotEmpty()) {
                Text(
                    "Lagu lain dari album ini di koleksi Anda",
                    color = RetroColors.CreamTextSecondary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
                sameAlbum.take(12).forEach { o ->
                    Text(
                        o.record.title,
                        color = RetroColors.CreamText,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = "Tampilkan kaset ${o.record.title}", role = Role.Button) { onPick(o.key) }
                            .padding(vertical = 12.dp),
                    )
                }
            } else if (r.album.isNotBlank()) {
                Text(
                    "Daftar lagu lengkap album tidak bisa dibaca launcher; buka aplikasi musik untuk melihatnya.",
                    color = RetroColors.CreamTextSecondary, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
