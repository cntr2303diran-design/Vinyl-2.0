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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.PhoneBook
import com.vinyl.launcher.data.PhoneBookRules
import com.vinyl.launcher.data.PhoneFavorite
import com.vinyl.launcher.data.PosterSettings
import com.vinyl.launcher.data.PosterSource
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomPropSettings
import com.vinyl.launcher.system.RoomShortcuts
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
    musicFolderUri: String?,
    onRetro: (RetroSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = remember { PhoneBook.get(context.applicationContext) }
    val favorites by book.favorites.collectAsState()
    val props = retro.props
    fun setProps(p: RoomPropSettings) = onRetro(retro.copy(props = p))

    val pickPoster = rememberImagePicker("poster") { path ->
        val old = retro.poster.photoPath
        onRetro(retro.copy(poster = PosterSettings(PosterSource.PHOTO, path)))
        if (old != null && old != path) scope.launch { SettingsRepository.get(context).deleteImage(old) }
    }

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
            "Radio kecil", props.radio, { on -> setProps(props.copy(radio = on)) },
            subtitle = "Ketuk memutar lagu acak dari musik lokal, tahan untuk menyalakan timer tidur",
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
            "Foto dipilih lewat pemilih foto sistem (tanpa izin penyimpanan), disalin ke penyimpanan aplikasi ini saja, dan tidak ikut cadangan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = pickPoster) { Text(if (poster.photoPath == null) "Pilih foto…" else "Ganti foto…") }
            if (poster.photoPath != null) {
                OutlinedButton(onClick = {
                    onRetro(retro.copy(poster = PosterSettings()))
                    poster.photoPath?.let { old -> scope.launch { SettingsRepository.get(context).deleteImage(old) } }
                }) { Text("Pakai cover album, hapus foto") }
            }
        }
    }

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
            OutlinedButton(onClick = { scope.launch { RoomShortcuts.playRandom(context, musicFolderUri, null) } }) { Text("Lagu acak") }
            OutlinedButton(onClick = { RoomShortcuts.toggleSleep(context, sleepMinutes) }) { Text("Timer tidur $sleepMinutes mnt") }
        }
    }

    SectionTitle("Kontak favorit telepon")
    ControlCard {
        Text(
            "Pilih lewat pemilih kontak sistem. Hanya nama dan alamat kontak disimpan di perangkat ini; tidak masuk cadangan dan tidak dikirim ke mana pun.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun contactName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
