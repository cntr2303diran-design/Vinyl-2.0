package com.vinyl.launcher.ui.settings

import androidx.compose.runtime.Composable
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.data.ShelfStyle
import com.vinyl.launcher.music.SleepTimerPlan
import androidx.compose.foundation.layout.Column

/** Lirik: satu-satunya fitur musik yang memakai internet. Penjelasannya sengaja eksplisit tentang apa yang dikirim. */
@Composable
fun MusicLyricsControls(m: MusicSettings, onChange: (MusicSettings) -> Unit) {
    Column {
        SwitchRow(
            "Cari lirik online", m.lyricsOnline, { onChange(m.copy(lyricsOnline = it)) },
            subtitle = "Mengirim judul, artis, album, dan durasi lagu ke lrclib.net, layanan lirik terbuka. Tidak ada akun, pelacak, atau data lain. Dimatikan = tidak ada permintaan jaringan sama sekali dan lirik tidak tampil.",
        )
        SwitchRow(
            "Kaburkan baris yang jauh", m.lyricsBlur, { onChange(m.copy(lyricsBlur = it)) },
            subtitle = "Efek fokus di lirik layar penuh: baris yang jauh dari baris aktif sedikit mengabur. Hanya berefek di Android 12 ke atas.",
        )
    }
}

/** Pergantian lembut dan durasi bawaan timer tidur. */
@Composable
fun MusicPlaybackControls(m: MusicSettings, onChange: (MusicSettings) -> Unit) {
    Column {
        SwitchRow(
            "Fade kaset (pergantian lembut)", m.softSkip, { onChange(m.copy(softSkip = it)) },
            subtitle = "Volume media turun sebentar lalu naik lagi saat Anda menekan lagu berikutnya atau sebelumnya. Ini bukan crossfade: TV Launcher tidak memutar audio, jadi tidak bisa menumpuk dua lagu. Volume selalu dikembalikan, dan bila Anda menekan tombol volume, TV Launcher berhenti menyentuhnya.",
        )
        // Pilihan sekarang ikut ditampilkan walau bukan salah satu preset (mis. dari berkas cadangan)
        val options = (SleepTimerPlan.PRESET_MINUTES + m.sleepMinutes).distinct().sorted()
        ChipRow(
            "Durasi bawaan timer tidur", options, m.sleepMinutes,
            text = { "$it mnt" },
            onSelect = { onChange(m.copy(sleepMinutes = SleepTimerPlan.clampMinutes(it))) },
        )
    }
}

/** Gaya rak piringan dan transisi warna. */
@Composable
fun MusicLookControls(m: MusicSettings, onChange: (MusicSettings) -> Unit) {
    Column {
        ChipRow(
            "Gaya rak kaset", ShelfStyle.entries.toList(), m.shelfStyle,
            text = { if (it == ShelfStyle.FLOW) "Berjajar" else "Tumpukan 3D" },
            onSelect = { onChange(m.copy(shelfStyle = it)) },
        )
        SwitchRow(
            "Transisi warna sinematik", m.cinematicColors, { onChange(m.copy(cinematicColors = it)) },
            subtitle = "Saat sampul album berganti, latar menggelap sekejap lalu menyala ke warna baru selama sekitar 1,4 detik. Otomatis dilewati bila animasi sistem dimatikan.",
        )
    }
}
