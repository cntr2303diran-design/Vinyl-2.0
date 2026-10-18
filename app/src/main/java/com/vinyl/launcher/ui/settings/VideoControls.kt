package com.vinyl.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.VideoRules
import com.vinyl.launcher.data.VideoSettings
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.media.VideoLibrary
import com.vinyl.launcher.media.VideoMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pengaturan "Video di layar TV": pilih folder video milik Anda (Storage Access Framework, tanpa izin penyimpanan),
 * lihat video yang cocok dengan lagu sekarang, dan tautkan video secara manual.
 */
@Composable
fun VideoControls(v: VideoSettings, onChange: ((VideoSettings) -> VideoSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib by VideoLibrary.state.collectAsStateWithLifecycle()
    val np by MediaRepository.state.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(v.folderUri) { VideoLibrary.scan(context, v.folderUri, force = true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!VideoLibrary.takePermission(context, uri)) return@rememberLauncherForActivityResult
        val old = v.folderUri
        scope.launch {
            val name = withContext(Dispatchers.IO) { VideoLibrary.folderName(context, uri) }
            onChange { VideoRules.changeFolder(it, uri.toString(), name).copy(enabled = true) }
            if (old != null && old != uri.toString()) VideoLibrary.releasePermission(context, old)
        }
    }

    SwitchRow(
        "Video di layar TV", v.enabled, { on -> onChange { it.copy(enabled = on) } },
        subtitle = "Video milik Anda diputar tanpa suara dan berulang di layar TV saat lagu yang cocok diputar. Tanpa video yang cocok, cover yang tampil.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picker.launch(null) }) { Text(if (v.folderUri == null) "Pilih folder video" else "Ganti folder") }
        if (v.folderUri != null) {
            TextButton(onClick = {
                v.folderUri?.let { VideoLibrary.releasePermission(context, it) }
                onChange { VideoRules.changeFolder(it, null, "") }
            }) { Text("Lepas folder") }
        }
    }
    val status = when {
        v.folderUri == null -> "Belum ada folder. Hanya folder yang Anda pilih yang bisa dibaca launcher."
        lib.scanning -> "Membaca folder ${v.folderName}…"
        lib.error != null -> lib.error!!
        else -> "Folder ${v.folderName.ifBlank { "pilihan" }}: ${lib.files.size} video" +
            if (lib.truncated) " (hanya ${VideoLibrary.MAX_FILES} pertama yang dibaca)" else ""
    }
    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))

    // Lagu sekarang: video yang cocok & tautan manual
    if (v.folderUri != null && lib.error == null && np.hasTrack) {
        val match = VideoMatch.find(lib.files, VideoRules.linkMap(v), np.artist, np.album, np.title)
        val key = VideoMatch.linkKey(np.artist, np.album, np.title)
        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text("Lagu sekarang: ${listOf(np.artist, np.album.ifBlank { np.title }).filter { it.isNotBlank() }.joinToString(" – ")}",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                match?.let { "Video: ${it.file.name} (${VideoMatch.reasonLabel(it.reason)})" } ?: "Tidak ada video yang cocok",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = key != null && lib.files.isNotEmpty(), onClick = { choosing = true }) { Text("Pilih video untuk lagu ini") }
                if (match?.reason == VideoMatch.Reason.LINKED && key != null) {
                    TextButton(onClick = { onChange { VideoRules.unlink(it, key) } }) { Text("Lepas tautan") }
                }
            }
        }
    }

    if (v.links.isNotEmpty()) {
        ControlLabel("Tautan manual")
        v.links.forEach { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(l.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (lib.files.any { it.documentId == l.documentId }) l.fileName else "${l.fileName} (berkas tidak ditemukan)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onChange { VideoRules.unlink(it, l.key) } }) { Icon(Icons.Rounded.Close, "Lepas tautan ${l.label}") }
            }
        }
    }

    Text(
        "Nama berkas \"Artis - Album.mp4\" atau \"Artis - Judul.mp4\" dicocokkan otomatis. Video tidak diputar saat Hapus animasi atau " +
            "hemat daya aktif, dan pemutarnya dilepas saat beranda tidak tampil. Berkas hanya dibaca di perangkat, tidak dikirim ke mana pun. " +
            "Video YouTube tidak didukung: kebijakan YouTube melarang memutar videonya tanpa suara terpisah dari audionya.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Pilih video") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(lib.files, key = { it.documentId }) { f ->
                        Text(
                            f.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClickLabel = "Tautkan ${f.name}") {
                                    onChange { VideoRules.link(it, np.artist, np.album, np.title, f.documentId, f.name) }
                                    choosing = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Batal") } },
        )
    }
}
