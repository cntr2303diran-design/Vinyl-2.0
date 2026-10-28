package com.vinyl.launcher.ui.settings

import android.widget.Toast
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
import com.vinyl.launcher.data.FilmResumeStore
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.FilmSettings
import com.vinyl.launcher.ui.film.FilmSiteData
import com.vinyl.launcher.ui.game.Games

/**
 * Pengaturan laci kaset di meja ruang TV. Kaset sendiri ditambah, diubah, dan dihapus dari laci (tahan sebuah kaset);
 * halaman ini memuat pilihan umum, ingatan "lanjut tonton" (halaman + menit terakhir), memulihkan kaset bawaan, rekor
 * game (Ular, Bata), dan menghapus data situs film (cookie, penyimpanan lokal, cache, posisi terakhir).
 */
@Composable
fun FilmControls(film: FilmSettings, onChange: (FilmSettings) -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<FilmConfirm?>(null) }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Laci di meja ruang TV berisi kaset film. Ketuk laci lalu ketuk sebuah kaset: kaset musik diganti kaset itu dan situsnya " +
                "dibuka di layar TV. Ketuk kaset yang sama lagi untuk mengembalikan kaset musik. Tahan kaset untuk mengubah atau menghapusnya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            "Teater mendatar", film.theaterLandscape, { on -> onChange(film.copy(theaterLandscape = on)) },
            subtitle = "Layar diputar mendatar saat film dibuka layar penuh (ketuk jendela kecil di deck)",
        )
        SwitchRow(
            "Lanjutkan musik", film.resumeMusic, { on -> onChange(film.copy(resumeMusic = on)) },
            subtitle = "Musik yang tadi diputar dilanjutkan saat kaset musik kembali ke TV",
        )
        SwitchRow(
            "Ingat tempat terakhir", film.rememberPlace,
            { on ->
                onChange(film.copy(rememberPlace = on))
                // Dimatikan = yang sudah diingat ikut dilupakan
                if (!on) FilmResumeStore.get(context).clear()
            },
            subtitle = "Kaset film dibuka di halaman dan menit terakhir yang Anda tonton. Hanya disimpan di perangkat ini",
        )
        if (film.rememberPlace) {
            OutlinedButton(onClick = { confirm = FilmConfirm.FORGET }, modifier = Modifier.fillMaxWidth()) { Text("Lupakan posisi terakhir") }
        }
        Text(
            "${film.cassettes.size} dari ${FilmRules.MAX_CASSETTES} kaset di laci.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(onClick = { confirm = FilmConfirm.RESTORE }, modifier = Modifier.fillMaxWidth()) { Text("Pulihkan kaset bawaan") }
        Text(
            "Kaset game (${Games.all.joinToString(" dan ") { Games.title(it) }}) selalu ada di laci. Rekor game tersimpan di perangkat ini saja: " +
                Games.all.joinToString("; ") { id ->
                    val best = Games.best(film, id)
                    "${Games.title(id)} ${if (best > 0) "$best" else "belum ada"}"
                } + ".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (Games.best(film, Games.SNAKE) > 0) {
            OutlinedButton(onClick = { confirm = FilmConfirm.RESET_SNAKE }, modifier = Modifier.fillMaxWidth()) { Text("Setel ulang rekor Ular") }
        }
        if (Games.best(film, Games.BREAKOUT) > 0) {
            OutlinedButton(onClick = { confirm = FilmConfirm.RESET_BREAKOUT }, modifier = Modifier.fillMaxWidth()) { Text("Setel ulang rekor Bata") }
        }
        Text(
            "Situs film menyimpan cookie dan data lokal di perangkat ini saja (misalnya agar tetap masuk akun). Anda bisa menghapusnya kapan saja.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(onClick = { confirm = FilmConfirm.CLEAR }, modifier = Modifier.fillMaxWidth()) { Text("Hapus cookie & data situs film") }
        Text(
            "Isi film sepenuhnya milik situs yang Anda buka. Kaset bawaan hanya menunjuk sumber film legal (domain publik, film terbuka, " +
                "Dewan Film Nasional Kanada). Layanan berlangganan dengan perlindungan DRM sering menolak diputar di dalam aplikasi lain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        FilmConfirm.RESTORE -> {
                            onChange(film.copy(cassettes = FilmRules.defaults()))
                            FilmResumeStore.get(context).prune(FilmRules.defaults())
                        }
                        FilmConfirm.RESET_SNAKE -> onChange(Games.reset(film, Games.SNAKE))
                        FilmConfirm.RESET_BREAKOUT -> onChange(Games.reset(film, Games.BREAKOUT))
                        FilmConfirm.FORGET -> {
                            FilmResumeStore.get(context).clear()
                            Toast.makeText(context, "Posisi terakhir semua kaset dilupakan.", Toast.LENGTH_SHORT).show()
                        }
                        FilmConfirm.CLEAR -> {
                            FilmSiteData.clear(context)
                            FilmResumeStore.get(context).clear()
                            Toast.makeText(context, "Data situs film dihapus.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(c.action) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

private enum class FilmConfirm(val title: String, val message: String, val action: String) {
    RESTORE(
        "Pulihkan kaset bawaan?",
        "Semua kaset di laci diganti dengan kaset bawaan. Kaset yang Anda tambahkan sendiri akan hilang.",
        "Pulihkan",
    ),
    RESET_SNAKE(
        "Setel ulang rekor?",
        "Rekor game Ular kembali ke nol. Ini tidak bisa dibatalkan.",
        "Setel ulang",
    ),
    RESET_BREAKOUT(
        "Setel ulang rekor?",
        "Rekor game Bata kembali ke nol. Ini tidak bisa dibatalkan.",
        "Setel ulang",
    ),
    FORGET(
        "Lupakan posisi terakhir?",
        "Halaman dan menit terakhir semua kaset film dilupakan. Kaset berikutnya dibuka dari alamat awalnya.",
        "Lupakan",
    ),
    CLEAR(
        "Hapus data situs film?",
        "Cookie, penyimpanan lokal, dan cache semua situs film dihapus, begitu pula posisi terakhir semua kaset. Anda mungkin harus masuk lagi ke situs yang memakai akun.",
        "Hapus",
    ),
}
