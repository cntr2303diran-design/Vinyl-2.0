package com.vinyl.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.DiaryEntry
import com.vinyl.launcher.data.DiaryRules
import com.vinyl.launcher.data.DiaryStore
import com.vinyl.launcher.data.Trophy
import com.vinyl.launcher.data.TrophyTier
import com.vinyl.launcher.ui.theme.Tokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun TrophyTier.argb(): Long = when (this) {
    TrophyTier.GOLD -> Tokens.Props.CUP_GOLD
    TrophyTier.SILVER -> Tokens.Props.CUP_SILVER
    TrophyTier.BRONZE -> Tokens.Props.CUP_BRONZE
}

private fun TrophyTier.label() = when (this) {
    TrophyTier.GOLD -> "Emas"
    TrophyTier.SILVER -> "Perak"
    TrophyTier.BRONZE -> "Perunggu"
}

/** Daftar semua piala: yang sudah diraih diberi warna tingkatnya, yang belum diberi batang kemajuan. */
@Composable
fun TrophyList(trophies: List<Trophy>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (t in trophies) {
            val status = if (t.earned) "Diraih" else "${t.progress} dari ${t.goal} ${t.unit}"
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "${t.title}, ${t.tier.label()}"
                    stateDescription = status
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (t.earned) Color(t.tier.argb()) else MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyLarge)
                    Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!t.earned) {
                        LinearProgressIndicator(progress = { t.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

@Composable
fun TrophyDialog(trophies: List<Trophy>, onDismiss: () -> Unit) {
    val earned = trophies.count { it.earned }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rak piala · $earned dari ${trophies.size}") },
        text = {
            Column {
                Text(
                    "Piala dihitung dari rekor game, total waktu dengar, dan jumlah lagu di koleksi — semuanya data di perangkat ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) { item { TrophyList(trophies) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

/** Isi buku harian: per hari, terbaru dulu. [onDelete] menghapus satu catatan. */
@Composable
fun DiaryList(entries: List<DiaryEntry>, onDelete: (DiaryEntry) -> Unit, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val dayFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val days = remember(entries) { DiaryRules.groupByDay(entries, zone) }
    if (days.isEmpty()) {
        Text("Belum ada catatan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    LazyColumn(modifier.heightIn(max = 360.dp)) {
        for (day in days) {
            item(key = "d${day.date}") {
                Text(
                    day.date.format(dayFmt), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { contentDescription = "Tanggal ${day.date.format(dayFmt)}" },
                )
            }
            items(day.entries, key = { "${it.at}|${it.title}|${it.artist}" }) { e ->
                val time = Instant.ofEpochMilli(e.at).atZone(zone).format(timeFmt)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(time, e.artist).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = { onDelete(e) },
                        modifier = Modifier.semantics { contentDescription = "Hapus catatan ${e.title}" },
                    ) { Text("Hapus") }
                }
            }
        }
    }
}

/** Buku harian lengkap: saklar perekaman, isi, dan hapus semua (dengan konfirmasi). */
@Composable
fun DiaryDialog(store: DiaryStore, onDismiss: () -> Unit) {
    val entries by store.entries.collectAsState()
    val recording by store.recording.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buku harian kaset") },
        text = {
            Column {
                SwitchRow(
                    "Catat lagu yang diputar", recording, store::setRecording,
                    subtitle = "Mati secara bawaan. Disimpan di perangkat ini saja (maks ${DiaryRules.MAX_ENTRIES} catatan), tidak ikut cadangan.",
                )
                DiaryList(entries, onDelete = store::delete)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        dismissButton = { if (entries.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Hapus semua") } },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Hapus seluruh buku harian?") },
            text = { Text("${entries.size} catatan akan dihapus dari perangkat ini dan tidak bisa dikembalikan.") },
            confirmButton = { TextButton(onClick = { store.clearAll(); confirm = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } },
        )
    }
}
