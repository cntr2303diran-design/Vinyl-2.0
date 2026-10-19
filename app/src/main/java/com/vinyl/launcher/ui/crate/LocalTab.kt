package com.vinyl.launcher.ui.crate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.media.VideoLibrary
import com.vinyl.launcher.player.HiFi
import com.vinyl.launcher.player.LocalFormat
import com.vinyl.launcher.player.LocalLibrary
import com.vinyl.launcher.player.LocalLibraryModel
import com.vinyl.launcher.player.LocalPlayer
import com.vinyl.launcher.player.LocalSort
import com.vinyl.launcher.player.LocalTrack
import com.vinyl.launcher.ui.components.glass
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import com.vinyl.launcher.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * Tab "Lokal": berkas musik di folder pilihan pengguna, diputar oleh pemutar bawaan (FLAC/WAV hi-res, MP3, AAC, OGG,
 * Opus; ALAC bila perangkat punya dekodernya). Pilih folder → dipindai → daftar per album/artis/judul dengan label
 * kualitas (mis. "FLAC 24/96"). Format yang belum didukung tetap tampil dengan keterangannya, tanpa aksi putar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalTab(
    np: NowPlaying,
    player: PlayerSettings,
    /** Folder video di layar TV: izinnya tidak dilepas bila sama dengan folder musik lama. */
    videoFolder: String?,
    onPlayer: ((PlayerSettings) -> PlayerSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib by LocalLibrary.state.collectAsStateWithLifecycle()
    val path by HiFi.path.collectAsStateWithLifecycle()
    val folder = player.folderUri

    LaunchedEffect(folder) {
        LocalLibrary.load(context, folder)
        // Indeks belum ada untuk folder ini: pindai sekali otomatis
        if (folder != null && LocalLibrary.state.value.tracks.isEmpty() && !LocalLibrary.state.value.scanning) LocalLibrary.scan(context, folder)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!VideoLibrary.takePermission(context, uri)) return@rememberLauncherForActivityResult
        val old = folder
        scope.launch {
            val name = withContext(Dispatchers.IO) { VideoLibrary.folderName(context, uri) }
            onPlayer { it.copy(folderUri = uri.toString(), folderName = name) }
            // Folder lama dilepas kecuali masih dipakai video
            if (old != null && old != uri.toString()) VideoLibrary.releaseIfUnused(context, old, listOf(videoFolder))
            LocalLibrary.scan(context, uri.toString())
        }
    }

    if (folder == null) {
        EmptyState(
            "Putar berkas musik di HP",
            "FLAC dan WAV (termasuk 24-bit hi-res), MP3, AAC, OGG, dan Opus diputar langsung oleh pemutar bawaan, dengan ReplayGain, " +
                "EQ parametrik & AutoEQ di Audio Console. ALAC bila perangkat punya dekodernya; AIFF, DSD, APE, dan WMA belum didukung. " +
                "Launcher hanya bisa membaca folder yang Anda pilih (tanpa izin penyimpanan).",
        ) {
            TextButton(onClick = { picker.launch(null) }, modifier = Modifier.padding(top = 16.dp)) { Text("Pilih folder musik", color = RetroColors.CreamText) }
        }
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    // Ribuan lagu: kunci urut & teks cari dihitung sekali di thread latar, bukan saat komposisi (null = sedang disiapkan)
    val prepared by produceState<LocalLibraryModel.Prepared?>(null, lib.tracks, player.sort) {
        value = withContext(Dispatchers.Default) { LocalLibraryModel.prepare(lib.tracks, player.sort) }
    }
    // Hasil cari beserta kata carinya; selama hasil baru dihitung, daftar lama tetap tampil
    val shown by produceState<Pair<String, List<LocalTrack>>?>(null, prepared, query) {
        val p = prepared ?: return@produceState
        value = query to (if (query.isBlank()) p.tracks else withContext(Dispatchers.Default) { LocalLibraryModel.search(p, query) })
    }
    val list = shown?.second.orEmpty()
    // Pesan "kosong" hanya bila hasil sudah sesuai daftar, urutan, dan kata cari terbaru (tidak berkedip saat dihitung)
    val upToDate = prepared.let { it != null && it.source == lib.tracks && it.sort == player.sort } && shown?.first == query
    val ownSession = np.packageName == context.packageName
    val playingId = if (ownSession && path.active) path.trackId else null

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 820.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        ) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                player.folderName.ifBlank { "Folder musik" },
                                style = MaterialTheme.typography.titleMedium, color = RetroColors.CreamText,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    lib.scanning -> "Memindai… ${lib.found} lagu"
                                    lib.truncated -> "${lib.tracks.size} lagu (dibatasi ${LocalLibrary.MAX_FILES})"
                                    else -> "${lib.tracks.size} lagu"
                                },
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { scope.launch { LocalLibrary.scan(context, folder) } }, enabled = !lib.scanning) {
                                Text("Pindai ulang", color = if (lib.scanning) TextTertiary else RetroColors.CreamText)
                            }
                            TextButton(onClick = { picker.launch(folder.toUri()) }) { Text("Ganti folder", color = RetroColors.CreamText) }
                            TextButton(onClick = {
                                scope.launch {
                                    onPlayer { it.copy(folderUri = null, folderName = "") }
                                    LocalLibrary.clear(context)
                                    VideoLibrary.releaseIfUnused(context, folder, listOf(videoFolder))
                                }
                            }) { Text("Lepas", color = TextSecondary) }
                        }
                    }
                    if (lib.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = RetroColors.CreamText)
                    lib.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFB4A9), modifier = Modifier.padding(vertical = 6.dp)) }
                    // Lagu yang dilewati pemutar karena gagal diputar (alasannya dari kode galat pemutar)
                    if (ownSession && path.skipped.isNotBlank()) {
                        Text(path.skipped, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFB4A9), modifier = Modifier.padding(vertical = 6.dp))
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(80) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("Cari judul, artis, album") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLeadingIconColor = TextSecondary, unfocusedLeadingIconColor = TextSecondary,
                            focusedPlaceholderColor = TextTertiary, unfocusedPlaceholderColor = TextTertiary,
                        ),
                    )
                    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((s, label) in listOf(LocalSort.ALBUM to "Album", LocalSort.ARTIST to "Artis", LocalSort.TITLE to "Judul", LocalSort.RECENT to "Terbaru")) {
                            FilterChip(
                                selected = player.sort == s, onClick = { onPlayer { it.copy(sort = s) } }, label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = TextSecondary, selectedLabelColor = Color(0xFF111113),
                                    selectedContainerColor = Color.White.copy(alpha = 0.94f),
                                ),
                            )
                        }
                    }
                }
            }
            if (upToDate && list.isEmpty() && !lib.scanning) {
                item(key = "empty") {
                    Text(
                        if (lib.tracks.isEmpty()) "Belum ada berkas musik di folder ini (subfolder ikut dipindai)." else "Tidak ada yang cocok dengan \"$query\".",
                        style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                val section = LocalLibraryModel.section(t, player.sort)
                val newSection = section.isNotEmpty() && (i == 0 || LocalLibraryModel.section(list[i - 1], player.sort) != section)
                Column {
                    if (newSection) {
                        Text(
                            section, style = MaterialTheme.typography.labelLarge, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 6.dp).semantics { heading() },
                        )
                    }
                    TrackRow(t, playing = t.id == playingId, showNumber = player.sort == LocalSort.ALBUM) {
                        // Antrean = lagu yang bisa diputar dari daftar yang tampil, mulai dari lagu ini
                        LocalLibraryModel.queue(list, t)?.let { (ids, start) -> LocalPlayer.play(context, ids, start) }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun TrackRow(t: LocalTrack, playing: Boolean, showNumber: Boolean, onClick: () -> Unit) {
    val quality = remember(t) { LocalFormat.quality(t) }
    val playable = remember(t) { LocalFormat.playable(t) }
    // Lencana emas hanya untuk hi-res yang memang bisa diputar
    val hiRes = remember(t) { playable && LocalFormat.hiRes(t) }
    // Format yang belum bisa dibaca pemutar bawaan (AIFF, DSD, APE, WMA): tetap tampil, dengan alasannya, tanpa aksi putar.
    // Nama format sudah ada di lencana, jadi keterangannya pendek agar muat di ponsel.
    val sub = if (!playable) "Belum didukung pemutar bawaan"
    else listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" — ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (playing) Modifier.glass(RoundedCornerShape(14.dp), fillAlpha = 0.12f) else Modifier)
            .clickable(enabled = playable, onClickLabel = "Putar") { onClick() }
            .semantics {
                contentDescription = listOf(t.title, sub, quality).filter { it.isNotBlank() }.joinToString(", ")
                if (playing) stateDescription = "Sedang diputar"
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (showNumber && t.trackNo > 0) "${t.trackNo}" else "",
            style = MaterialTheme.typography.labelMedium, color = TextTertiary, modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                t.title, style = MaterialTheme.typography.titleSmall,
                color = if (playing) RetroColors.CreamText else if (playable) TextPrimary else TextSecondary,
                fontWeight = if (playing) FontWeight.SemiBold else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = if (playable) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        if (quality.isNotBlank()) {
            Text(
                quality,
                style = MaterialTheme.typography.labelSmall, fontFamily = MonoFamily,
                color = if (hiRes) Color(0xFF111113) else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hiRes) Color(0xFFE8C46A) else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(clock(t.durationMs), style = MaterialTheme.typography.labelMedium, color = TextTertiary, fontFamily = MonoFamily)
    }
}

private fun clock(ms: Long): String {
    if (ms <= 0) return ""
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
}

