package com.vinyl.launcher.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.media.SleepTimer
import com.vinyl.launcher.media.VolumeController
import com.vinyl.launcher.music.SleepPhase
import com.vinyl.launcher.music.SleepTimerPlan
import com.vinyl.launcher.ui.components.glass
import com.vinyl.launcher.ui.theme.Accent
import com.vinyl.launcher.ui.theme.AccentOn
import com.vinyl.launcher.ui.theme.TextSecondary

/**
 * Tombol kapsul "Timer tidur". Saat timer berjalan, label berubah menjadi sisa waktu dan kapsul berwarna aksen.
 * Mengetuknya membuka [SleepTimerDialog].
 *
 * @param rememberedMinutes durasi yang terakhir dipakai (Pengaturan → Musik), jadi pilihan awal dialog.
 * @param onPick dipanggil saat pengguna memulai timer menit-an, agar pilihan diingat.
 */
@Composable
fun SleepTimerChip(
    np: NowPlaying,
    rememberedMinutes: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by SleepTimer.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val running = state as? SleepTimer.State.Running
    val label = when (val phase = running?.phase) {
        is SleepPhase.Counting -> "Berhenti ${SleepTimerPlan.format(phase.remainingMs)}"
        is SleepPhase.Fading -> "Memudar ${SleepTimerPlan.format(phase.remainingMs)}"
        else -> "Timer tidur"
    }
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (running != null) AccentOn else TextSecondary,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .then(if (running != null) Modifier.background(Accent, shape) else Modifier.glass(shape, fillAlpha = 0.06f))
            .clickable(onClickLabel = "Atur timer tidur", role = Role.Button) { open = true }
            // Label berubah tiap detik; pembaca layar cukup tahu statusnya, bukan angka yang terus berganti
            .semantics {
                contentDescription = "Timer tidur"
                stateDescription = if (running != null) "Berjalan" else "Mati"
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (open) SleepTimerDialog(
        np = np,
        rememberedMinutes = rememberedMinutes,
        running = running,
        onPick = onPick,
        onDismiss = { open = false },
    )
}

/** Dialog pilihan durasi. Tidak pernah memulai timer tanpa ketukan eksplisit pada tombol mulai. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerDialog(
    np: NowPlaying,
    rememberedMinutes: Int,
    running: SleepTimer.State.Running?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(rememberedMinutes) {
        (SleepTimerPlan.PRESET_MINUTES + SleepTimerPlan.clampMinutes(rememberedMinutes)).distinct().sorted()
    }
    var selected by remember { mutableIntStateOf(SleepTimerPlan.clampMinutes(rememberedMinutes)) }
    // Dihitung saat dialog dibuka; cukup, karena memilihnya langsung memakai posisi lagu pada saat itu
    val endOfTrackAvailable = remember(np.durationMs) { SleepTimerPlan.endOfTrackMs(np.durationMs, np.positionAt()) != null }
    val canFade = VolumeController.canControl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer tidur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (running != null) {
                    val remaining = when (val p = running.phase) {
                        is SleepPhase.Counting -> p.remainingMs
                        is SleepPhase.Fading -> p.remainingMs
                        else -> 0L
                    }
                    Text(
                        if (running.endOfTrack) "Musik dijeda saat lagu ini selesai, sekitar ${SleepTimerPlan.format(remaining)} lagi."
                        else "Musik dijeda dalam ${SleepTimerPlan.format(remaining)}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { SleepTimer.cancel(); onDismiss() }) { Text("Batalkan timer") }
                    Text("Atau mulai ulang dengan durasi lain:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { minutes ->
                        val isSelected = minutes == selected
                        Text(
                            "$minutes mnt",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { selected = minutes })
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .semantics { contentDescription = "$minutes menit" },
                        )
                    }
                }
                Text(
                    if (canFade) "Volume dipudarkan pelan di bagian akhir, lalu musik dijeda dan volume dikembalikan seperti semula."
                    else "Perangkat ini mengunci volume medianya, jadi musik langsung dijeda tanpa dipudarkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        if (SleepTimer.startAtEndOfTrack(np)) onDismiss()
                    },
                    enabled = endOfTrackAvailable,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Sampai lagu ini selesai") }
                if (!endOfTrackAvailable) {
                    Text(
                        "Tidak tersedia: lagu tidak sedang diputar, durasinya tidak diketahui, atau hampir habis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Timer berjalan selama Vinyl hidup di latar belakang. Bila sistem menutup Vinyl, timer ikut berhenti (volume tetap aman).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { SleepTimer.start(selected); onPick(selected); onDismiss() }) { Text("Mulai $selected menit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
