package com.vinyl.launcher.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.AchievementInput
import com.vinyl.launcher.data.Achievements
import com.vinyl.launcher.data.DiaryStore
import com.vinyl.launcher.data.FilmSettings
import com.vinyl.launcher.data.HistoryRepository
import com.vinyl.launcher.data.PhoneBook
import com.vinyl.launcher.data.PhoneBookRules
import com.vinyl.launcher.data.PhoneFavorite
import com.vinyl.launcher.data.PosterSettings
import com.vinyl.launcher.data.PosterSource
import com.vinyl.launcher.data.RadioRules
import com.vinyl.launcher.data.RadioStation
import com.vinyl.launcher.data.RadioStationBook
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomPropSettings
import com.vinyl.launcher.system.RoomShortcuts
import com.vinyl.launcher.ui.game.Games
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Halaman "Benda ruangan": benda di meja dan dinding (jam, radio, telepon) hanya tampil bila muat,
 * jadi halaman ini menjamin semua fungsinya tetap bisa dijangkau di layar apa pun.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomPropsControls(
    retro: RetroSettings,
    sleepMinutes: Int,
    film: FilmSettings,
    onRetro: (RetroSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = remember { PhoneBook.get(context.applicationContext) }
    val favorites by book.favorites.collectAsState()
    val phoneSaveFailed by book.saveFailed.collectAsState()
    val radioBook = remember { RadioStationBook.get(context.applicationContext) }
    val stations by radioBook.stations.collectAsState()
    val radioSaveFailed by radioBook.saveFailed.collectAsState()
    var addingStation by remember { mutableStateOf(false) }
    val props = retro.props
    fun setProps(p: RoomPropSettings) = onRetro(retro.copy(props = p))

    val pickPoster = rememberPosterPicker { path ->
        val old = retro.poster.photoPath
        onRetro(retro.copy(poster = PosterSettings(PosterSource.PHOTO, path)))
        if (old != null && old != path) scope.launch { SettingsRepository.get(context).deletePoster(old) }
    }

    val playRecords by HistoryRepository.records.collectAsState()
    val trophies = remember(film.snakeBest, film.breakoutBest, playRecords, retro.trophyGoals) {
        Achievements.compute(
            AchievementInput(
                snakeBest = Games.best(film, Games.SNAKE), breakoutBest = Games.best(film, Games.BREAKOUT),
                listenedMs = playRecords.sumOf { it.listenedMs }, songs = playRecords.size,
            ),
            retro.trophyGoals,
        )
    }
    val diary = remember { DiaryStore.get(context.applicationContext) }
    val diaryEntries by diary.entries.collectAsState()
    var showDiary by remember { mutableStateOf(false) }
    var confirmClearDiary by remember { mutableStateOf(false) }

    var pendingDigit by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        val digit = pendingDigit
        pendingDigit = 0
        if (uri == null || !PhoneBookRules.isDigit(digit)) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) { contactName(context, uri) }
            book.set(PhoneFavorite(digit, name ?: "Kontak $digit", uri.toString()))
        }
    }

    ControlCard {
        SwitchRow(
            "Jam dinding", props.clock, { on -> setProps(props.copy(clock = on)) },
            subtitle = "Ketuk untuk alarm, tahan untuk timer. Tampil di dinding bila muat",
        )
        SwitchRow(
            "Poster berbingkai", props.frame, { on -> setProps(props.copy(frame = on)) },
            subtitle = "Menampilkan cover lagu yang diputar atau foto pilihan Anda. Ketuk untuk mengganti foto, tahan untuk kembali ke cover",
        )
        SwitchRow(
            "Rak piala", props.trophy, { on -> setProps(props.copy(trophy = on)) },
            subtitle = "Piala dari rekor game, jam dengar, dan jumlah lagu. Ketuk rak untuk melihat semuanya",
        )
        SwitchRow(
            "Buku harian kaset", props.diary, { on -> setProps(props.copy(diary = on)) },
            subtitle = "Buku yang digantung di dinding; ketuk untuk membuka. Perekaman (musik, film, game) punya saklar sendiri di bawah",
        )
        SwitchRow(
            "Radio kecil", props.radio, { on -> setProps(props.copy(radio = on)) },
            subtitle = "Ketuk memutar stasiun radio internet berikutnya dari daftar Anda, tahan untuk timer tidur",
        )
        SwitchRow(
            "Telepon putar", props.phone, { on -> setProps(props.copy(phone = on)) },
            subtitle = "Putar angka 1–9 untuk menelepon kontak favorit, ketuk badan telepon untuk aplikasi telepon",
        )
    }

    SectionTitle("Isi poster")
    ControlCard {
        val poster = retro.poster
        Text(
            if (poster.photoPath != null) "Sekarang: foto pilihan Anda." else "Sekarang: cover album yang sedang diputar.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Foto dipilih lewat pemilih foto sistem (tanpa izin penyimpanan), dikecilkan, dibuang metadatanya (termasuk lokasi), lalu disimpan terenkripsi di perangkat ini saja; tidak ikut cadangan dan tidak dikirim ke mana pun.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = pickPoster) { Text(if (poster.photoPath == null) "Pilih foto…" else "Ganti foto…") }
            if (poster.photoPath != null) {
                OutlinedButton(onClick = {
                    onRetro(retro.copy(poster = PosterSettings()))
                    poster.photoPath?.let { old -> scope.launch { SettingsRepository.get(context).deletePoster(old) } }
                }) { Text("Pakai cover album, hapus foto") }
            }
        }
    }

    SectionTitle("Rak piala")
    ControlCard { TrophyList(trophies) }
    SectionTitle("Ambang piala")
    ControlCard { TrophyGoalsEditor(retro.trophyGoals) { onRetro(retro.copy(trophyGoals = it)) } }

    SectionTitle("Buku harian kaset")
    ControlCard {
        DiaryRecordingControls(diary)
        Text("${diaryEntries.size} catatan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = { showDiary = true }) { Text("Lihat isi") }
            if (diaryEntries.isNotEmpty()) OutlinedButton(onClick = { confirmClearDiary = true }) { Text("Hapus semua catatan") }
        }
    }
    if (showDiary) DiaryDialog(diary, onDismiss = { showDiary = false })
    if (confirmClearDiary) {
        AlertDialog(
            onDismissRequest = { confirmClearDiary = false },
            title = { Text("Hapus seluruh buku harian?") },
            text = { Text("${diaryEntries.size} catatan akan dihapus dari perangkat ini dan tidak bisa dikembalikan.") },
            confirmButton = { TextButton(onClick = { diary.clearAll(); confirmClearDiary = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmClearDiary = false }) { Text("Batal") } },
        )
    }

    SectionTitle("Stasiun radio")
    ControlCard {
        Text(
            "Radio memutar aliran internet milik Anda sendiri (alamat https dari stasiun yang Anda pilih; aliran mp3/aac/ogg langsung, bukan daftar putar .m3u atau HLS). Tidak ada stasiun bawaan. Alamat dihubungi hanya saat radio dinyalakan; daftar disimpan terenkripsi di perangkat ini, tidak ikut cadangan, dan tidak dikirim ke mana pun.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (radioSaveFailed) {
            Text(
                "Stasiun tidak disimpan: penyimpanan terenkripsi tidak tersedia di perangkat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (stations.isEmpty()) {
            Text("Belum ada stasiun.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        for (st in stations) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(st.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        st.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { radioBook.remove(st.url) }) { Text("Hapus") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = { addingStation = true }, enabled = stations.size < RadioRules.MAX_STATIONS) { Text("Tambah stasiun…") }
            if (stations.isNotEmpty()) OutlinedButton(onClick = { radioBook.clearAll() }) { Text("Hapus semua stasiun") }
        }
        if (stations.size >= RadioRules.MAX_STATIONS) {
            Text("Daftar penuh (${RadioRules.MAX_STATIONS} stasiun).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (addingStation) AddStationDialog(onDismiss = { addingStation = false }, onAdd = { s -> radioBook.add(s) })

    SectionTitle("Pintasan langsung")
    ControlCard {
        Text(
            "Dipakai bila benda tidak muat di layar ini (misalnya ponsel potret).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = { RoomShortcuts.openAlarms(context) }) { Text("Buka alarm") }
            OutlinedButton(onClick = { RoomShortcuts.openTimers(context) }) { Text("Buka timer") }
            OutlinedButton(onClick = { RoomShortcuts.openPhone(context) }) { Text("Aplikasi telepon") }
            OutlinedButton(onClick = { RoomShortcuts.playNextStation(context, stations, null) }) { Text("Putar radio") }
            OutlinedButton(onClick = { RoomShortcuts.toggleSleep(context, sleepMinutes) }) { Text("Timer tidur $sleepMinutes mnt") }
        }
    }

    SectionTitle("Kontak favorit telepon")
    ControlCard {
        Text(
            "Pilih lewat pemilih kontak sistem. Hanya nama dan alamat kontak yang disimpan, terenkripsi di perangkat ini; nomornya tidak disalin, tidak masuk cadangan, dan tidak dikirim ke mana pun.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (phoneSaveFailed) {
            Text(
                "Pintasan tidak disimpan: penyimpanan terenkripsi tidak tersedia di perangkat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        for (d in PhoneBookRules.MIN_DIGIT..PhoneBookRules.MAX_DIGIT) {
            val fav = favorites[d]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Angka $d", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        fav?.name ?: "Kosong (membuka aplikasi telepon)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { pendingDigit = d; picker.launch(null) }) { Text(if (fav == null) "Pilih" else "Ganti") }
                if (fav != null) TextButton(onClick = { book.clear(d) }) { Text("Hapus") }
            }
        }
        if (favorites.isNotEmpty()) {
            OutlinedButton(onClick = { book.clearAll() }, modifier = Modifier.padding(top = 8.dp)) { Text("Hapus semua pintasan") }
        }
    }
}

/** Dialog tambah stasiun: nama dan alamat https; tombol Simpan baru aktif bila keduanya sah. */
@Composable
private fun AddStationDialog(onDismiss: () -> Unit, onAdd: (RadioStation) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val candidate = RadioStation(name, url)
    val valid = RadioRules.clean(candidate) != null
    val urlBad = url.isNotBlank() && !RadioRules.isStreamUrl(url.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah stasiun") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(RadioRules.MAX_NAME) }, singleLine = true,
                    label = { Text("Nama stasiun") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it.take(RadioRules.MAX_URL) }, singleLine = true,
                    label = { Text("Alamat aliran (https://…)") }, isError = urlBad,
                    supportingText = { if (urlBad) Text("Harus alamat https yang lengkap, tanpa spasi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { if (onAdd(candidate)) onDismiss() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun contactName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
