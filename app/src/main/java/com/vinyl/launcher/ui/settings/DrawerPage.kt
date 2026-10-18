package com.vinyl.launcher.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vinyl.launcher.data.ContactsRepository
import com.vinyl.launcher.data.DrawerSettings
import com.vinyl.launcher.data.SettingsSanitizer
import kotlin.math.roundToInt

/** Tampilan app drawer: Sering dipakai, kategori, lencana. */
@Composable
fun DrawerDisplayControls(d: DrawerSettings, onChange: (DrawerSettings) -> Unit) {
    Column {
        SwitchRow(
            "Sering dipakai", d.showFrequent, { onChange(d.copy(showFrequent = it)) },
            subtitle = "Baris ikon yang paling sering Anda buka lewat launcher ini.",
        )
        if (d.showFrequent) {
            SliderRow(
                "Jumlah ikon sering dipakai", d.frequentCount.toFloat(),
                SettingsSanitizer.MIN_FREQUENT.toFloat()..SettingsSanitizer.MAX_FREQUENT.toFloat(),
                onChange = { onChange(d.copy(frequentCount = it.roundToInt())) },
                valueText = { "${it.roundToInt()}" },
            )
        }
        SwitchRow(
            "Chip kategori", d.showCategories, { onChange(d.copy(showCategories = it)) },
            subtitle = "Saring daftar per kategori (Musik, Game, …). Tekan lama ikon untuk memindahkan aplikasi ke kategori lain.",
        )
        SwitchRow(
            "Lencana notifikasi", d.showBadges, { onChange(d.copy(showBadges = it)) },
            subtitle = "Jumlah notifikasi di ikon. Memakai akses notifikasi (lihat Izin & status); hanya nama aplikasi dan jumlahnya yang dihitung, judul dan isi tidak dibaca.",
        )
    }
}

/** Apa saja yang dicari kolom cari. Aplikasi selalu dicari; sisanya bisa dimatikan, kontak butuh izin. */
@Composable
fun DrawerSearchControls(d: DrawerSettings, onChange: (DrawerSettings) -> Unit) {
    val context = LocalContext.current
    // Pengguna bisa mencabut izin lewat pengaturan sistem; cek ulang tiap layar ini kembali aktif
    var granted by remember { mutableStateOf(ContactsRepository.hasPermission(context)) }
    LifecycleResumeEffect(Unit) {
        granted = ContactsRepository.hasPermission(context)
        onPauseOrDispose { }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) onChange(d.copy(searchContacts = true))
        else Toast.makeText(context, "Izin Kontak ditolak, jadi kontak tidak dicari.", Toast.LENGTH_LONG).show()
    }

    Column {
        Text(
            "Kolom cari selalu mencari aplikasi. Yang lain bisa Anda atur:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        SwitchRow("Lagu dari koleksi", d.searchSongs, { onChange(d.copy(searchSongs = it)) }, subtitle = "Lagu yang pernah Anda putar. Ketuk untuk memutarnya di aplikasi musik.")
        SwitchRow("Pengaturan sistem", d.searchSettings, { onChange(d.copy(searchSettings = it)) }, subtitle = "Wi-Fi, Bluetooth, Layar, dan sebagainya.")
        SwitchRow(
            "Kontak", d.searchContacts && granted,
            { on ->
                if (!on) onChange(d.copy(searchContacts = false))
                else if (granted) onChange(d.copy(searchContacts = true))
                else request.launch(Manifest.permission.READ_CONTACTS)
            },
            subtitle = "Opsional; meminta izin Kontak. Pencarian terjadi di perangkat dan hasilnya hanya ditampilkan, tidak disimpan atau dikirim.",
        )
    }
}

/** Data pribadi di drawer: riwayat penggunaan dan kategori buatan sendiri. */
@Composable
fun DrawerDataControls(d: DrawerSettings, onClearUsage: () -> Unit, onResetCategories: () -> Unit) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Riwayat aplikasi yang sering dibuka hanya tersimpan di perangkat ini dan tidak ikut berkas cadangan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { confirm = Confirm.USAGE }, modifier = Modifier.fillMaxWidth()) { Text("Hapus riwayat “Sering dipakai”") }
        Text(
            if (d.categoryOverrides.isEmpty()) "Belum ada aplikasi yang Anda pindahkan ke kategori lain."
            else "${d.categoryOverrides.size} aplikasi Anda pindahkan ke kategori lain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (d.categoryOverrides.isNotEmpty()) {
            OutlinedButton(onClick = { confirm = Confirm.CATEGORIES }, modifier = Modifier.fillMaxWidth()) { Text("Kembalikan semua kategori ke otomatis") }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (c == Confirm.USAGE) onClearUsage() else onResetCategories()
                }) { Text(c.action) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

private enum class Confirm(val title: String, val message: String, val action: String) {
    USAGE("Hapus riwayat?", "Baris “Sering dipakai” akan kosong lalu terisi lagi seiring Anda membuka aplikasi. Tindakan ini tidak bisa dibatalkan.", "Hapus"),
    CATEGORIES("Kembalikan kategori?", "Semua aplikasi yang Anda pindahkan akan kembali ke kategori otomatis.", "Kembalikan"),
}
