package com.vinyl.launcher.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.PrivacyInfo
import com.vinyl.launcher.data.SettingsBackup
import kotlinx.coroutines.launch

/** Simpan/pulihkan semua pengaturan ke satu berkas JSON pilihan pengguna (Storage Access Framework, tanpa izin penyimpanan). */
@Composable
fun BackupActions(vm: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Uri bersifat Parcelable sehingga dialog konfirmasi bertahan saat proses diulang/diputar
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    fun say(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            say(vm.exportBackupTo(uri))
            busy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pending = uri
    }

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Simpan semua pengaturan ke satu berkas untuk dipindahkan ke perangkat lain atau disimpan sebelum reset. " +
                "Latar gambar dan foto widget tidak ikut karena tersimpan di perangkat ini; pilih ulang setelah memulihkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { exporter.launch(SettingsBackup.SUGGESTED_FILE_NAME) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Simpan cadangan…") }
        OutlinedButton(
            onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pulihkan dari berkas…") }
    }

    pending?.let { uri ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Ganti pengaturan saat ini?") },
            text = {
                Text(
                    "Semua pengaturan akan diganti dengan isi berkas. Status layar kunci, taskbar, dan tombol navigasi " +
                        "di perangkat ini tidak diubah. Tindakan ini tidak bisa dibatalkan; simpan cadangan dulu bila perlu.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = true
                        say(vm.importBackupFrom(uri))
                        busy = false
                    }
                }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } },
        )
    }
}

/** Ringkasan privasi: apa yang disimpan, ke mana aplikasi terhubung. */
@Composable
fun PrivacySummary() {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrivacyInfo.summary.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Daftar izin beserta alasannya (sumber: [PrivacyInfo], dijaga sinkron dengan manifest oleh tes). */
@Composable
fun PermissionReasons() {
    Column(Modifier.padding(vertical = 8.dp)) {
        PrivacyInfo.permissions.forEachIndexed { i, item ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Text(
                if (item.optional) "${item.name} (opsional)" else item.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
