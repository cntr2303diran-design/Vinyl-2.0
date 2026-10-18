package com.vinyl.launcher.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.ContactHit
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.drawer.DrawerLayout
import com.vinyl.launcher.drawer.DrawerRow
import com.vinyl.launcher.drawer.SearchIndex
import com.vinyl.launcher.drawer.SystemSetting
import com.vinyl.launcher.drawer.UsageRanking
import com.vinyl.launcher.drawer.searchSystemSettings
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.components.AmbientBackdrop
import com.vinyl.launcher.ui.components.blockTouchesBehind
import com.vinyl.launcher.ui.theme.Corner
import com.vinyl.launcher.ui.theme.Dimen
import com.vinyl.launcher.ui.theme.PaperCream
import com.vinyl.launcher.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hasil pencarian untuk satu kueri. [query] menandai kueri mana yang sudah dihitung (untuk membedakan "belum" dari "kosong"). */
private data class SearchResults(
    val query: String = "",
    val apps: List<AppInfo> = emptyList(),
    val songs: List<PlayRecord> = emptyList(),
    val settings: List<SystemSetting> = emptyList(),
    val contacts: List<ContactHit> = emptyList(),
) {
    val isEmpty get() = apps.isEmpty() && songs.isEmpty() && settings.isEmpty() && contacts.isEmpty()
}

private const val SEARCH_DEBOUNCE_MS = 70L
private const val MAX_APP_RESULTS = 24
private const val MAX_SONG_RESULTS = 4
private const val MIN_LETTERS_FOR_JUMP_BAR = 4

/**
 * App drawer: aplikasi dikelompokkan per huruf, "Sering dipakai" di atas, chip kategori, bilah lompat A–Z,
 * lencana notifikasi, menu pintasan, dan pencarian ke aplikasi / lagu / kontak / pengaturan sistem.
 * Ditutup dengan tombol back, tombol home, atau menarik ke bawah saat daftar di posisi paling atas.
 */
@Composable
fun AppDrawer(
    data: DrawerData,
    tint: Color,
    np: NowPlaying,
    actions: DrawerActions,
) {
    val settings = data.settings
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val close by rememberUpdatedState(actions.close)
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var pull by remember { mutableFloatStateOf(0f) }
    val searching = query.isNotBlank()

    // Back: batalkan pencarian / kategori dulu, baru tutup drawer
    BackHandler(enabled = query.isNotEmpty() || selectedCategory != null) {
        query = ""
        selectedCategory = null
    }

    // Tarik ke bawah di puncak daftar → tutup drawer
    val closeOnPull = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pull += available.y
                    if (pull > 220f) { pull = 0f; close() }
                } else if (available.y < 0f) pull = 0f
                return Offset.Zero
            }
        }
    }

    // ------------------------------------------------------------------ susunan daftar (tanpa pencarian)
    val categories = remember(data.apps, settings.categoryOverrides) {
        DrawerLayout.availableCategories(data.apps, { it.key }, { it.category }, settings.categoryOverrides)
    }
    val showChips = settings.showCategories && categories.isNotEmpty()
    // Kategori yang terpilih tetapi sudah tak punya aplikasi (mis. dipindahkan semua) dianggap "Semua"
    val category = selectedCategory?.takeIf { sel -> showChips && categories.any { it.first == sel } }

    val appKeys = remember(data.apps) { data.apps.mapTo(HashSet()) { it.key } }
    val frequentKeys = remember(data.usage, appKeys, settings.showFrequent, settings.frequentCount) {
        if (!settings.showFrequent) emptyList()
        else UsageRanking.top(data.usage, System.currentTimeMillis(), settings.frequentCount) { it in appKeys }
    }
    val layout = remember(data.apps, settings.categoryOverrides, category, frequentKeys, settings.frequentCount) {
        DrawerLayout.build(
            apps = data.apps, key = { it.key }, label = { it.label }, auto = { it.category },
            overrides = settings.categoryOverrides, selected = category,
            frequentKeys = frequentKeys, frequentCount = settings.frequentCount,
        )
    }
    val showJumpBar = !searching && layout.lettersPresent.size >= MIN_LETTERS_FOR_JUMP_BAR
    val currentLetter by remember(layout) { derivedStateOf { layout.letterAt(gridState.firstVisibleItemIndex) } }

    LaunchedEffect(searching, category) { gridState.scrollToItem(0) }

    // ------------------------------------------------------------------ pencarian (di luar main thread)
    val appIndex by produceState<SearchIndex<AppInfo>?>(null, data.apps) {
        value = withContext(Dispatchers.Default) { SearchIndex(data.apps) { it.label } }
    }
    val songIndex by produceState<SearchIndex<PlayRecord>?>(null, data.songs) {
        // distinctBy: key LazyGrid harus unik; berkas riwayat di disk tidak dijamin bebas duplikat
        value = withContext(Dispatchers.Default) { SearchIndex(data.songs.distinctBy { it.key }) { "${it.title} ${it.artist}" } }
    }
    var results by remember { mutableStateOf(SearchResults()) }
    LaunchedEffect(query, appIndex, songIndex, settings.searchSongs, settings.searchSettings, settings.searchContacts) {
        val q = query.trim()
        if (q.isEmpty()) { results = SearchResults(); return@LaunchedEffect }
        delay(SEARCH_DEBOUNCE_MS)   // ketikan beruntun membatalkan efek ini sebelum sempat menghitung
        val local = withContext(Dispatchers.Default) {
            SearchResults(
                query = q,
                apps = appIndex?.rank(q, MAX_APP_RESULTS).orEmpty(),
                songs = if (settings.searchSongs) songIndex?.rank(q, MAX_SONG_RESULTS).orEmpty() else emptyList(),
                settings = if (settings.searchSettings) searchSystemSettings(q) else emptyList(),
            )
        }
        results = local
        if (settings.searchContacts) {
            // Kontak datang belakangan (IO); daftar lokal sudah tampil lebih dulu
            val contacts = actions.searchContacts(q)
            if (contacts.isNotEmpty()) results = local.copy(contacts = contacts)
        }
    }

    /** Enter di papan ketik: buka hasil teratas. */
    fun openTopResult() {
        val r = results
        if (r.query != query.trim()) return
        focus.clearFocus()
        r.apps.firstOrNull()?.let { actions.launch(it); return }
        r.songs.firstOrNull()?.let { actions.playSong(it); return }
        r.contacts.firstOrNull()?.let { actions.openContact(it); return }
        r.settings.firstOrNull()?.let { actions.openSetting(it) }
    }

    // ------------------------------------------------------------------ tampilan
    Box(Modifier.fillMaxSize().blockTouchesBehind()) {
        // Latar ala App Library iOS: ambient cover yang lebih gelap (peredam adaptif menjaga kontras; lihat BackdropDim)
        AmbientBackdrop(np.art.takeIf { np.hasTrack }, np.swatches, tint, animate = false, dim = DRAWER_DIM)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Pegangan + judul + kolom cari (juga bisa ditarik ke bawah untuk menutup)
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { if (total > 120f) close(); total = 0f },
                        ) { _, dy -> total += dy }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.padding(top = 10.dp).size(width = 40.dp, height = 5.dp)
                        .background(PaperCream.copy(alpha = 0.3f), Corner.pill),
                )
                Text(
                    "Aplikasi",
                    style = MaterialTheme.typography.displaySmall,
                    color = PaperCream,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 22.dp),
                )
                SearchField(
                    value = query,
                    onChange = { query = it },
                    onSearch = ::openTopResult,
                    modifier = Modifier.padding(top = Dimen.l, bottom = 6.dp).widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = Dimen.xxl),
                )
            }
            if (showChips && !searching) {
                CategoryChips(categories, total = data.apps.size, selected = category, onSelect = { selectedCategory = it }, modifier = Modifier.fillMaxWidth())
            }

            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = Dimen.xxl, end = if (showJumpBar) 44.dp else Dimen.xxl, top = Dimen.s, bottom = Dimen.xl),
                    verticalArrangement = Arrangement.spacedBy(Dimen.m),
                    modifier = Modifier.fillMaxSize().nestedScroll(closeOnPull),
                ) {
                    if (searching) {
                        searchItems(query.trim(), results, data, actions) { focus.clearFocus() }
                    } else {
                        items(
                            layout.rows,
                            key = { it.id },
                            span = { row -> if (row is DrawerRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                            contentType = { row -> if (row is DrawerRow.Header) HEADER_TYPE else APP_TYPE },
                        ) { row ->
                            when (row) {
                                is DrawerRow.Header -> SectionHeader(row.title)
                                is DrawerRow.App -> AppTile(
                                    app = row.item,
                                    badge = badgeFor(data, row.item),
                                    canAddToDock = data.dockEnabled && row.item.key !in data.dockKeys,
                                    actions = actions,
                                    onClick = { focus.clearFocus(); actions.launch(row.item) },
                                )
                            }
                        }
                        if (layout.rows.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (data.apps.isEmpty()) "Memuat aplikasi…" else "Tidak ada aplikasi di kategori ini",
                                color = TextSecondary,
                                modifier = Modifier.padding(Dimen.xl),
                            )
                        }
                    }
                }
                if (showJumpBar) {
                    JumpBar(
                        present = layout.lettersPresent,
                        current = currentLetter,
                        onJump = { letter -> layout.jumpIndex(letter)?.let { index -> scope.launch { gridState.scrollToItem(index) } } },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = Dimen.xs, top = Dimen.s, bottom = Dimen.xl),
                    )
                }
            }
        }
    }
}

/** Peredam dasar latar drawer; pada cover terang, [AmbientBackdrop] menebalkannya sesuai kebutuhan kontras. */
internal const val DRAWER_DIM = 0.62f
private const val HEADER_TYPE = 0
private const val APP_TYPE = 1

private fun badgeFor(data: DrawerData, app: AppInfo): Int =
    if (data.settings.showBadges) data.badges[app.packageName] ?: 0 else 0

/** Isi grid saat mencari: Aplikasi (ikon) lalu Lagu, Kontak, Pengaturan (baris penuh). */
private fun LazyGridScope.searchItems(
    query: String,
    results: SearchResults,
    data: DrawerData,
    actions: DrawerActions,
    onBeforeOpen: () -> Unit,
) {
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    if (results.apps.isNotEmpty()) {
        item(key = "h:apps", span = full) { SectionHeader("Aplikasi") }
        items(results.apps, key = { "r:app:${it.key}" }) { app ->
            AppTile(
                app = app,
                badge = badgeFor(data, app),
                canAddToDock = data.dockEnabled && app.key !in data.dockKeys,
                actions = actions,
                onClick = { onBeforeOpen(); actions.launch(app) },
            )
        }
    }
    if (results.songs.isNotEmpty()) {
        item(key = "h:songs", span = full) { SectionHeader("Lagu") }
        items(results.songs, key = { "r:song:${it.key}" }, span = { full() }) { song ->
            SongRow(song) { onBeforeOpen(); actions.playSong(song) }
        }
    }
    if (results.contacts.isNotEmpty()) {
        item(key = "h:contacts", span = full) { SectionHeader("Kontak") }
        items(results.contacts, key = { "r:contact:${it.id}" }, span = { full() }) { hit ->
            ContactRow(hit) { onBeforeOpen(); actions.openContact(hit) }
        }
    }
    if (results.settings.isNotEmpty()) {
        item(key = "h:settings", span = full) { SectionHeader("Pengaturan") }
        items(results.settings, key = { "r:setting:${it.name}" }, span = { full() }) { setting ->
            SettingRow(setting) { onBeforeOpen(); actions.openSetting(setting) }
        }
    }
    // Hanya tampilkan "tidak ada hasil" bila hitungan untuk kueri INI sudah selesai (bukan saat masih mengetik)
    if (results.isEmpty && results.query == query) {
        item(key = "none", span = full) {
            Text("Tidak ada hasil untuk “$query”", color = TextSecondary, modifier = Modifier.padding(Dimen.xl))
        }
    }
}
