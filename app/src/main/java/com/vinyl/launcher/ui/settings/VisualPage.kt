package com.vinyl.launcher.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.ParallaxMode
import com.vinyl.launcher.data.VisualSettings
import com.vinyl.launcher.data.VisualizerMode
import com.vinyl.launcher.fx.FxPlan
import com.vinyl.launcher.fx.FxShaderStatus
import com.vinyl.launcher.fx.MotionSensors
import com.vinyl.launcher.fx.VisualizerStatus
import com.vinyl.launcher.fx.micPermissionGranted
import com.vinyl.launcher.fx.rememberPowerSave
import com.vinyl.launcher.ui.theme.rememberReduceMotion

/*
 * Pengaturan → Gambar & efek. Setiap kontrol menjelaskan apa yang terjadi, apa yang dibutuhkan, dan apa yang TIDAK
 * dilakukan; kegagalan (perangkat menolak, izin ditolak) ditulis apa adanya, tidak diganti tiruan.
 */

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
}

/** Alasan semua efek sedang dimatikan sistem (bila ada). */
@Composable
fun VisualBlockNote() {
    val reduce = rememberReduceMotion()
    val powerSave = rememberPowerSave()
    val msg = when {
        reduce -> "Semua efek di halaman ini dimatikan sementara karena animasi sistem dimatikan (Hapus animasi). Pilihan Anda tetap tersimpan."
        powerSave -> "Semua efek di halaman ini dimatikan sementara karena Penghemat baterai aktif. Pilihan Anda tetap tersimpan."
        else -> null
    }
    if (msg != null) {
        Text(
            msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp),
        )
    }
}

/** Distorsi kaca, cahaya reaktif, partikel. */
@Composable
fun VisualEffectControls(v: VisualSettings, onChange: (VisualSettings) -> Unit) {
    val agsl = Build.VERSION.SDK_INT >= FxPlan.AGSL_MIN_API
    val shaderFailed by FxShaderStatus.failed.collectAsStateWithLifecycle()
    Column {
        SwitchRow(
            "Distorsi kaca", v.glassDistortion, { onChange(v.copy(glassDistortion = it)) },
            subtitle = "Latar membias seperti kaca beralur, bergelombang mengikuti musik, dan beriak dari titik yang Anda sentuh. " +
                "Hanya latar yang berubah — teks dan tombol tidak bergeser. Butuh Android 13 ke atas.",
        )
        when {
            !agsl -> Note("Tidak tersedia di perangkat ini: Android ${Build.VERSION.RELEASE} (butuh 13). Pilihan tetap tersimpan.")
            shaderFailed -> Note("Perangkat ini menolak shader efek, jadi distorsi tidak tampil.")
        }
        SwitchRow(
            "Cahaya reaktif", v.reactiveGlow, { onChange(v.copy(reactiveGlow = it)) },
            subtitle = "Cahaya lembut di belakang TV. Mengikuti suara bila Visualizer di bawah menyala; bila tidak, hanya berdenyut pelan saat musik diputar.",
        )
        SwitchRow(
            "Partikel", v.particles, { onChange(v.copy(particles = it)) },
            subtitle = "Titik debu halus yang melayang pelan dan lebih cepat saat musik keras. Diam saat musik berhenti.",
        )
        Note("Efek dibatasi agar teks tetap terbaca (kontras minimal 4,5:1) dan otomatis berhenti saat layar utama tertutup.")
    }
}

private fun ParallaxMode.label() = when (this) {
    ParallaxMode.OFF -> "Mati"
    ParallaxMode.WINDOW -> "Seperti jendela"
    ParallaxMode.FLOAT -> "Seperti melayang"
}

/** Parallax sensor gerak. */
@Composable
fun VisualParallaxControls(v: VisualSettings, onChange: (VisualSettings) -> Unit) {
    val context = LocalContext.current
    val available = remember(context) { MotionSensors.available(context) }
    val gyro = remember(context) { MotionSensors.hasGyro(context) }
    Column {
        ChipRow(
            "Arah", ParallaxMode.entries.toList(), v.parallax, text = { it.label() },
            onSelect = { onChange(v.copy(parallax = it)) },
        )
        Note(
            "Latar bergeser sedikit saat ponsel dimiringkan. Seperti jendela: latar terasa di belakang kaca. " +
                "Seperti melayang: arahnya dibalik. Tidak butuh izin; sensor hanya aktif saat layar utama terlihat.",
        )
        if (v.parallax != ParallaxMode.OFF) {
            SliderRow(
                "Kekuatan", v.parallaxStrength, 0.1f..1f,
                { onChange(v.copy(parallaxStrength = it)) },
                { "%.0f%%".format(it * 100) },
            )
        }
        when {
            !available -> Note("Perangkat ini tidak punya sensor gerak, jadi parallax tidak berfungsi.")
            !gyro -> Note("Tanpa giroskop terpadu, gerak kiri-kanan melemah saat ponsel berdiri tegak.")
        }
    }
}

private fun VisualizerMode.label() = when (this) {
    VisualizerMode.OFF -> "Mati"
    VisualizerMode.REAL -> "Audio asli"
    VisualizerMode.DECORATIVE -> "Animasi dekoratif"
}

/** Visualizer audio, dengan alur izin mikrofon yang hanya dimulai saat pengguna memilih "Audio asli". */
@Composable
fun VisualizerControls(v: VisualSettings, onChange: (VisualSettings) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(micPermissionGranted(context)) }
    var denied by remember { mutableStateOf(false) }
    // Pengguna bisa memberi/mencabut izin lewat pengaturan sistem lalu kembali ke sini
    LifecycleResumeEffect(Unit) {
        granted = micPermissionGranted(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) onChange(v.copy(visualizer = VisualizerMode.REAL))
    }
    val status by VisualizerStatus.state.collectAsStateWithLifecycle()

    Column {
        ChipRow(
            "Sumber", VisualizerMode.entries.toList(), v.visualizer, text = { it.label() },
            onSelect = { mode ->
                if (mode == VisualizerMode.REAL && !granted) {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    denied = false
                    onChange(v.copy(visualizer = mode))
                }
            },
        )
        when (v.visualizer) {
            VisualizerMode.OFF -> Note("Batang spektrum tidak ditampilkan di layar utama.")
            VisualizerMode.DECORATIVE -> Note(
                "Animasi berirama yang menyala saat musik diputar. Animasi ini TIDAK mengikuti suara asli, dan diberi label " +
                    "\"Animasi dekoratif\" di layar utama. Tidak butuh izin.",
            )
            VisualizerMode.REAL -> when {
                !granted -> {
                    Note("Butuh izin mikrofon. Tanpa izin, tidak ada batang yang ditampilkan.")
                    TextButton(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Beri izin mikrofon") }
                }
                status == VisualizerStatus.State.UNAVAILABLE ->
                    Note("Perangkat ini menolak membuka Visualizer audio, jadi tidak ada batang yang ditampilkan. Mode Animasi dekoratif bisa dipakai sebagai gantinya.")
                status == VisualizerStatus.State.LIVE -> Note("Aktif: spektrum keluaran audio perangkat sedang dibaca.")
                else -> Note("Siap. Batang tampil di layar utama saat musik diputar.")
            }
        }
        if (denied) {
            Note("Izin mikrofon ditolak, jadi Audio asli tidak dinyalakan.")
            TextButton(onClick = { context.startActivity(appDetailsIntent(context.packageName)) }) { Text("Buka pengaturan aplikasi") }
        }
        Note(
            "Audio asli membaca spektrum frekuensi keluaran audio perangkat lewat Visualizer Android. Android mewajibkan izin " +
                "mikrofon untuk ini, tetapi Vinyl tidak merekam suara, tidak menyimpan apa pun, dan tidak mengirim apa pun ke internet. " +
                "Izin tidak pernah diminta kecuali Anda memilih mode ini.",
        )
    }
}

private fun appDetailsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
