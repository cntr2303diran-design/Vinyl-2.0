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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.DiaryEntry
import com.vinyl.launcher.data.DiaryFlags
import com.vinyl.launcher.data.DiaryKind
import com.vinyl.launcher.data.DiaryRules
import com.vinyl.launcher.data.DiaryStore
import com.vinyl.launcher.data.GoalParse
import com.vinyl.launcher.data.Trophy
import com.vinyl.launcher.data.TrophyGoal
import com.vinyl.launcher.data.TrophyGoalRules
import com.vinyl.launcher.data.TrophyGoals
import com.vinyl.launcher.data.TrophyLine
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
            items(day.entries, key = { "${it.at}|${it.kind}|${it.title}|${it.artist}" }) { e ->
                val time = Instant.ofEpochMilli(e.at).atZone(zone).format(timeFmt)
                val kind = if (e.kind == DiaryKind.MUSIC) "" else DiaryRules.label(e.kind)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(time, kind, e.artist).filter { it.isNotBlank() }.joinToString(" · "),
                            // 2 baris: di huruf 200% "waktu · Game · Skor 120" tetap terbaca, tidak terpotong jadi "waktu · Game ·…"
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
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

/**
 * Saklar perekaman buku harian: saklar utama (mati secara bawaan) dan, bila menyala, satu saklar per jenis (musik, film,
 * game). Dipakai di dialog buku harian dan di Pengaturan → Benda ruangan. Memberi tahu bila catatan tidak bisa disimpan
 * karena penyimpanan terenkripsi tidak tersedia di perangkat ini.
 */
@Composable
fun DiaryRecordingControls(store: DiaryStore) {
    val flags by store.flags.collectAsState()
    val saveFailed by store.saveFailed.collectAsState()
    DiaryRecordingPanel(flags, saveFailed, store::setRecording, store::setKind)
}

/** Isi [DiaryRecordingControls] tanpa penyimpanan: semua keadaannya dari parameter (dipakai juga oleh tangkapan layar uji). */
@Composable
fun DiaryRecordingPanel(flags: DiaryFlags, saveFailed: Boolean, onRecording: (Boolean) -> Unit, onKind: (DiaryKind, Boolean) -> Unit) {
    SwitchRow(
        "Catat buku harian", flags.on, onRecording,
        subtitle = "Mati secara bawaan. Disimpan terenkripsi di perangkat ini saja (maks ${DiaryRules.MAX_ENTRIES} catatan), tidak ikut cadangan dan tidak dikirim ke mana pun.",
    )
    if (flags.on) {
        Column(Modifier.padding(start = 16.dp)) {
            SwitchRow("Musik", flags.music, { onKind(DiaryKind.MUSIC, it) }, subtitle = "Judul, artis, dan waktu putar")
            SwitchRow("Film", flags.film, { onKind(DiaryKind.FILM, it) }, subtitle = "Nama kaset dan waktu; tanpa alamat situs")
            SwitchRow("Game", flags.games, { onKind(DiaryKind.GAME, it) }, subtitle = "Nama game, skor ronde, dan waktu")
        }
    }
    if (saveFailed) {
        Text(
            "Catatan tidak disimpan: penyimpanan terenkripsi tidak tersedia di perangkat ini.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

/** Buku harian lengkap: saklar perekaman, isi, dan hapus semua (dengan konfirmasi). */
@Composable
fun DiaryDialog(store: DiaryStore, onDismiss: () -> Unit) {
    val entries by store.entries.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buku harian kaset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DiaryRecordingControls(store)
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

/**
 * Ambang piala yang diatur sendiri: satu baris per jalur (Ular, Bata, jam dengar, lagu) dengan tiga angka perunggu < perak
 * < emas, tombol "Ubah" membuka isian, dan tombol mengembalikan ke bawaan. Disimpan lokal bersama pengaturan.
 */
@Composable
fun TrophyGoalsEditor(goals: TrophyGoals, onChange: (TrophyGoals) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Text(
        "Atur sendiri berapa banyak yang dibutuhkan untuk tiap piala. Angka harus makin besar dari perunggu ke emas.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    for (line in TrophyLine.entries) {
        val g = goals[line]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${g.bronze} · ${g.silver} · ${g.gold} ${line.unit}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { editing = line.name },
                modifier = Modifier.semantics { contentDescription = "Ubah ambang piala ${line.label}" },
            ) { Text("Ubah") }
        }
    }
    if (goals != TrophyGoals()) {
        OutlinedButton(onClick = { onChange(TrophyGoals()) }, modifier = Modifier.padding(top = 6.dp)) { Text("Kembalikan semua ke bawaan") }
    }
    val line = TrophyLine.entries.firstOrNull { it.name == editing }
    if (line != null) {
        TrophyGoalDialog(
            line = line, current = goals[line],
            onSave = { onChange(goals.replace(line, it)); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TrophyGoalDialog(line: TrophyLine, current: TrophyGoal, onSave: (TrophyGoal) -> Unit, onDismiss: () -> Unit) {
    var bronze by rememberSaveable(line) { mutableStateOf(current.bronze.toString()) }
    var silver by rememberSaveable(line) { mutableStateOf(current.silver.toString()) }
    var gold by rememberSaveable(line) { mutableStateOf(current.gold.toString()) }
    val parsed = TrophyGoalRules.parse(line, bronze, silver, gold)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ambang piala · ${line.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalField("Perunggu (${line.unit})", bronze) { bronze = it }
                GoalField("Perak (${line.unit})", silver) { silver = it }
                GoalField("Emas (${line.unit})", gold) { gold = it }
                if (parsed is GoalParse.Bad) {
                    Text(parsed.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                // Disusun menurun (bukan sejajar tombol Simpan/Batal) supaya tidak melebar di huruf 200%
                Text(
                    "Bawaan: ${line.default.bronze} · ${line.default.silver} · ${line.default.gold} ${line.unit}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    bronze = line.default.bronze.toString(); silver = line.default.silver.toString(); gold = line.default.gold.toString()
                }) { Text("Pakai bawaan") }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (parsed is GoalParse.Ok) onSave(parsed.goal) }, enabled = parsed is GoalParse.Ok) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun GoalField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it in '0'..'9' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
