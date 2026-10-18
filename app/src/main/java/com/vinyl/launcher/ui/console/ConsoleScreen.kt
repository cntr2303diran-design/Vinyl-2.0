package com.vinyl.launcher.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.vinyl.launcher.advanced.AdvancedAccess
import com.vinyl.launcher.audio.AdvancedReadings
import com.vinyl.launcher.audio.AudioSnapshot
import com.vinyl.launcher.audio.Automation
import com.vinyl.launcher.audio.ConsoleRows
import com.vinyl.launcher.audio.DeviceRow
import com.vinyl.launcher.audio.Eq
import com.vinyl.launcher.audio.EqSession
import com.vinyl.launcher.audio.EqSessions
import com.vinyl.launcher.audio.Reading
import com.vinyl.launcher.audio.SystemAudioShortcuts
import com.vinyl.launcher.audio.SystemAudioShortcuts.Kind
import com.vinyl.launcher.audio.ToneSynth
import com.vinyl.launcher.data.AudioConsoleRules
import com.vinyl.launcher.data.AudioConsoleSettings
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Semua yang ditampilkan Audio Console (dikumpulkan oleh [ConsoleRoute]; diisi langsung oleh tes tangkapan layar). */
@Immutable
data class ConsoleUi(
    val snapshot: AudioSnapshot,
    val advanced: AdvancedReadings,
    val audio: AudioConsoleSettings,
    val sessions: List<EqSession>,
    val log: List<Automation.Result>,
    val shortcuts: List<SystemAudioShortcuts.Shortcut>,
    val tone: ToneSynth.Side?,
    val micGranted: Boolean,
    val access: AdvancedAccess.State,
    val isSamsung: Boolean,
    /** "Disalin" tampil sebentar setelah perintah ADB disalin. */
    val copied: Boolean = false,
)

class ConsoleActions(
    val onBack: () -> Unit,
    /** Ubah pengaturan Audio Console (dipratinjau langsung ke EQ, disimpan sesaat kemudian). */
    val onAudio: ((AudioConsoleSettings) -> AudioConsoleSettings) -> Unit,
    val onTone: (ToneSynth.Side) -> Unit,
    val onStopTone: () -> Unit,
    val onShortcut: (SystemAudioShortcuts.Shortcut) -> Unit,
    val onCopyCommand: () -> Unit = {},
    val onCheckAccess: () -> Unit = {},
    /** Dipanggil setelah pengguna menyetujui dialog yang menampilkan perintah Shizuku. */
    val onGrantShizuku: () -> Unit = {},
)

/**
 * Halaman kanan: Audio Console bergaya receiver stereo. Tablet landscape: tiga kolom panel; layar sedang: dua kolom;
 * ponsel: satu kolom yang digulir. Setiap nilai diberi label sumbernya (lihat [ReadingRow]).
 */
@Composable
fun ConsoleScreen(ui: ConsoleUi, actions: ConsoleActions, modifier: Modifier = Modifier, vu: @Composable () -> Unit = {}) {
    BoxWithConstraints(modifier.fillMaxSize().background(ConsoleColors.Page)) {
        val columns = when {
            maxWidth >= 900.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }
        val panels: List<@Composable () -> Unit> = listOf(
            { DevicesPanel(ui, actions) },          // 0
            { SoundPanel(ui, actions) },            // 1
            { QualityPanel(ui) },                   // 2
            { SpatialPanel(ui, actions) },          // 3
            { DiagnosticsPanel(ui, actions) },      // 4
            { AutomationPanel(ui, actions) },       // 5
            { DevicePanel(ui, actions) },           // 6
            { AdvancedPanel(ui, actions) },         // 7
        )
        val layout = when (columns) {
            3 -> listOf(listOf(0, 2, 3), listOf(1, 5), listOf(4, 7, 6))
            2 -> listOf(listOf(0, 1, 5), listOf(2, 3, 4, 7, 6))
            else -> listOf(listOf(0, 1, 2, 3, 4, 5, 7, 6))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(actions.onBack, vu)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                layout.forEach { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { col.forEach { panels[it]() } }
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, vu: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali ke ruang TV", tint = ConsoleColors.Text)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    "AUDIO CONSOLE", color = ConsoleColors.Text, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em, modifier = Modifier.semantics { heading() },
                )
                Text("STATUS SISTEM · KONTROL PERANGKAT", color = ConsoleColors.Amber, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.1.em)
            }
            Box(Modifier.widthIn(max = 220.dp)) { vu() }
        }
        ConsoleNote(
            "Setiap nilai berlabel sumbernya: Terukur, Dilaporkan sistem, Perkiraan, atau Tidak tersedia beserta alasannya. Tidak ada nilai yang ditebak.",
            Modifier.padding(start = 52.dp, top = 4.dp),
        )
    }
}

// ------------------------------------------------------------------ panel

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutButtons(ui: ConsoleUi, actions: ConsoleActions, kinds: List<Kind>) {
    val list = ui.shortcuts.filter { it.kind in kinds }
    if (list.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { s ->
            ConsoleButton(
                s.label, { actions.onShortcut(s) },
                description = s.label + if (s.handler.isNotBlank()) ", dibuka oleh ${s.handler}" else "",
            )
        }
    }
}

@Composable
private fun DevicesPanel(ui: ConsoleUi, actions: ConsoleActions) {
    ConsolePanel("Perangkat") {
        ReadingRow("Keluaran aktif", ui.snapshot.active.reading)
        val rows = ConsoleRows.devices(ui.snapshot)
        if (rows.isEmpty()) ConsoleNote("Tidak ada perangkat keluaran musik yang dilaporkan sistem.")
        rows.forEach { DeviceLine(it) }
        ReadingRow("Baterai", ConsoleRows.battery(ui.snapshot, ui.advanced))
        ShortcutButtons(ui, actions, listOf(Kind.BLUETOOTH, Kind.VOLUME_PANEL))
    }
}

@Composable
private fun DeviceLine(d: DeviceRow) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${d.name}, ${d.kind}, ${d.status}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .then(if (d.active) Modifier.background(ConsoleColors.Green) else Modifier.border(1.dp, ConsoleColors.TextSecondary, CircleShape)),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(d.name, color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium)
            Text("${d.kind} · ${d.status}", color = if (d.active) ConsoleColors.Green else ConsoleColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPanel(ui: ConsoleUi, actions: ConsoleActions) {
    val a = ui.audio
    fun edit(f: (com.vinyl.launcher.audio.EqValues) -> com.vinyl.launcher.audio.EqValues) =
        actions.onAudio { AudioConsoleRules.edit(it, f(it.values)) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    ConsolePanel("Suara", lit = a.eqEnabled) {
        ConsoleSwitch(
            "Equalizer", a.eqEnabled, { on -> actions.onAudio { it.copy(eqEnabled = on) } },
            detail = "Dipasang ke sesi audio aplikasi musik; launcher sendiri tidak memutar musik",
        )
        ReadingRow("Diproses", EqSessions.summary(a.eqEnabled, ui.sessions))
        // Sumbu frekuensi EQ selalu rendah → tinggi dari kiri, juga pada bahasa RTL (seperti equalizer fisik)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val subs = listOf("≤ 250 Hz", "0,25–4 kHz", "≥ 4 kHz")
            val values = Eq.bands(a.values)
            Eq.BAND_NAMES.forEachIndexed { i, name ->
                Fader(name, subs[i], values[i], Eq.MIN_DB..Eq.MAX_DB, Eq::dbText, { v ->
                    edit { when (i) { 0 -> it.copy(lowDb = v); 1 -> it.copy(midDb = v); else -> it.copy(highDb = v) } }
                })
            }
            Fader("Preamp", "masukan", a.values.preampDb, Eq.PREAMP_MIN_DB..Eq.PREAMP_MAX_DB, Eq::dbText, { v -> edit { it.copy(preampDb = v) } })
        } }
        LineSlider(
            "Balance", a.values.balance, -1f..1f, balanceText(a.values.balance), "Kiri", "Kanan",
            { v -> edit { it.copy(balance = v) } },
        )
        ConsoleDivider()
        Text("Preset", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioConsoleRules.allPresets(a).forEach { (name, _) ->
                ConsoleButton(
                    name, { actions.onAudio { AudioConsoleRules.applyPreset(it, name) } }, selected = name == a.preset,
                    description = "Preset $name" + if (name == a.preset) ", dipakai" else "",
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleButton("Simpan sebagai preset", { saving = true })
            val p = a.preset
            if (p != null && !AudioConsoleRules.isBuiltIn(p)) ConsoleButton("Hapus \"$p\"", { deleting = p })
        }
        ProfileLine(ui, actions)
        ConsoleDivider()
        ConsoleSwitch(
            "Pengaman clipping", a.safeHeadroom, { on -> actions.onAudio { it.copy(safeHeadroom = on) } },
            detail = "Masukan diturunkan sebesar penguatan pita terbesar agar suara tidak pecah",
        )
        ConsoleSwitch(
            "Semua audio (eksperimental)", a.globalExperimental, { on -> actions.onAudio { it.copy(globalExperimental = on) } },
            enabled = a.eqEnabled,
            detail = "Mencoba EQ pada keluaran gabungan (sesi 0). Cara ini sudah usang di Android dan di banyak perangkat tidak berpengaruh.",
        )
        ConsoleSwitch(
            "Deteksi sesi lewat Akses lanjutan", a.detectSessions, { on -> actions.onAudio { it.copy(detectSessions = on) } },
            enabled = ui.access.dumpGranted,
            detail = if (ui.access.dumpGranted) "Juga memasang EQ ke sesi musik yang terlihat di laporan sistem, untuk aplikasi yang tidak membuka sesi efek"
            else "Butuh Akses lanjutan (izin DUMP)",
        )
        ConsoleNote(
            "Bisa bentrok dengan efek suara bawaan perangkat (mis. Dolby atau efek pabrikan) dan aplikasi equalizer lain. Bila status menyebut " +
                "\"dikendalikan aplikasi efek lain\", matikan salah satunya.",
            warn = true,
        )
    }

    if (saving) {
        var name by rememberSaveable { mutableStateOf("") }
        val ok = AudioConsoleRules.cleanName(name)?.let { !AudioConsoleRules.isBuiltIn(it) } == true
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Simpan preset") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(AudioConsoleRules.MAX_NAME) }, label = { Text("Nama preset") }, singleLine = true)
                    if (name.isNotBlank() && !ok) Text("Nama ini dipakai preset bawaan.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    actions.onAudio { AudioConsoleRules.savePreset(it, name) ?: it }
                    saving = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Batal") } },
        )
    }
    deleting?.let { target ->
        val used = a.profiles.count { it.preset == target }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus preset \"$target\"?") },
            text = { Text(if (used > 0) "Profil perangkat yang memakainya ($used) ikut dihapus." else "Preset ini akan dihapus dari perangkat.") },
            confirmButton = {
                TextButton(onClick = { actions.onAudio { AudioConsoleRules.deletePreset(it, target) }; deleting = null }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}

private fun balanceText(b: Float): String {
    val pct = (abs(b) * 100).roundToInt()
    return when {
        pct == 0 -> "Tengah"
        b < 0 -> "Kiri $pct%"
        else -> "Kanan $pct%"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileLine(ui: ConsoleUi, actions: ConsoleActions) {
    val device = ui.snapshot.active.device ?: return
    val profile = ui.audio.profiles.firstOrNull { it.key == device.profileKey }
    val preset = ui.audio.preset
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        when {
            profile != null -> {
                Text("Profil ${device.name}: ${profile.preset}", color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium)
                ConsoleButton("Lepas profil", { actions.onAudio { AudioConsoleRules.removeProfile(it, profile.key) } })
            }
            preset != null -> ConsoleButton(
                "Pakai \"$preset\" untuk ${device.name}", { actions.onAudio { AudioConsoleRules.setProfile(it, device, preset) } },
                description = "Jadikan $preset preset otomatis saat ${device.name} menjadi keluaran",
            )
            else -> ConsoleNote("Pilih atau simpan preset untuk membuat profil ${device.name}.")
        }
    }
}

@Composable
private fun QualityPanel(ui: ConsoleUi) {
    ConsolePanel("Kualitas") {
        ConsoleRows.quality(ui.snapshot, ui.advanced).forEach { ReadingRow(it.label, it.reading) }
        ConsoleNote("Format sumber (lagu) dan format keluaran (yang dikirim ke perangkat) dibedakan. Kemampuan perangkat bukan format yang sedang dipakai.")
    }
}

@Composable
private fun SpatialPanel(ui: ConsoleUi, actions: ConsoleActions) {
    ConsolePanel("Spasial & efek") {
        ConsoleRows.spatial(ui.snapshot).forEach { ReadingRow(it.label, it.reading) }
        ConsoleNote("Status hanya dibaca. Efek bawaan perangkat diatur di aplikasinya sendiri lewat tombol di bawah.")
        ShortcutButtons(ui, actions, listOf(Kind.EFFECTS_PANEL, Kind.SOUND_SETTINGS))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsPanel(ui: ConsoleUi, actions: ConsoleActions) {
    ConsolePanel("Diagnostik") {
        ConsoleRows.diagnostics(ui.snapshot).forEach { ReadingRow(it.label, it.reading) }
        ConsoleDivider()
        Text("Tes kanal", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleButton("Kiri", { actions.onTone(ToneSynth.Side.LEFT) }, selected = ui.tone == ToneSynth.Side.LEFT, description = "Putar nada tes di kanal kiri")
            ConsoleButton("Kanan", { actions.onTone(ToneSynth.Side.RIGHT) }, selected = ui.tone == ToneSynth.Side.RIGHT, description = "Putar nada tes di kanal kanan")
            ConsoleButton("Berhenti", actions.onStopTone, enabled = ui.tone != null, description = "Hentikan nada tes")
        }
        ConsoleNote("Nada pendek 1,2 detik (kiri 440 Hz, kanan 660 Hz) pada level aman, lewat keluaran media saat ini. Musik tidak dijeda.")
        ConsoleDivider()
        ReadingRow(
            "Visualizer audio asli",
            if (ui.micGranted) Reading.reported("Izin diberikan", "Izin mikrofon (hanya spektrum, bukan rekaman)")
            else Reading.unavailable("Izin mikrofon belum diberikan; opsional, atur di Pengaturan → Efek visual"),
        )
        ReadingRow("Akses lanjutan", accessReading(ui.access))
    }
}

@Composable
private fun AutomationPanel(ui: ConsoleUi, actions: ConsoleActions) {
    val a = ui.audio
    ConsolePanel("Otomasi", lit = a.profiles.isNotEmpty() || a.openConsoleOnUsb) {
        ConsoleSwitch(
            "Kembalikan preset saat terputus", a.restoreOnDisconnect, { on -> actions.onAudio { it.copy(restoreOnDisconnect = on) } },
            detail = "Perangkat berprofil terputus → preset sebelumnya dipakai lagi",
        )
        ConsoleSwitch(
            "USB DAC tersambung → buka console", a.openConsoleOnUsb, { on -> actions.onAudio { it.copy(openConsoleOnUsb = on) } },
            detail = "Halaman ini dibuka saat beranda tampil berikutnya",
        )
        if (a.profiles.isEmpty()) {
            ConsoleNote("Belum ada profil perangkat. Buat dari panel Suara saat perangkatnya menjadi keluaran.")
        } else {
            Text("Profil perangkat", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            a.profiles.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                        Text(p.name, color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium)
                        Text("→ ${p.preset}", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { actions.onAudio { AudioConsoleRules.removeProfile(it, p.key) } }) {
                        Icon(Icons.Rounded.Close, "Hapus profil ${p.name}", tint = ConsoleColors.TextSecondary)
                    }
                }
            }
        }
        ConsoleDivider()
        Text("Hasil terakhir", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        if (ui.log.isEmpty()) ConsoleNote("Belum ada aturan yang berjalan.")
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        ui.log.forEach { r ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                Row {
                    Text(r.rule, color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        outcomeLabel(r.outcome), style = MaterialTheme.typography.labelSmall,
                        color = if (r.outcome == Automation.Outcome.DONE) ConsoleColors.Green else ConsoleColors.Amber,
                    )
                }
                Text("${fmt.format(Date(r.atMs))} · ${r.detail}", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        ConsoleNote("Aturan berjalan selama launcher aktif; tidak ada layanan latar tambahan.")
    }
}

private fun outcomeLabel(o: Automation.Outcome) = when (o) {
    Automation.Outcome.DONE -> "Berhasil"
    Automation.Outcome.FAILED -> "Gagal"
    Automation.Outcome.NOT_ALLOWED -> "Tidak diizinkan"
    Automation.Outcome.SKIPPED -> "Dilewati"
}

/** Panel pabrikan: "Samsung" di perangkat Samsung, selain itu "Perangkat ini". Hanya pintasan resmi & status. */
@Composable
private fun DevicePanel(ui: ConsoleUi, actions: ConsoleActions) {
    ConsolePanel(if (ui.isSamsung) "Optimasi Samsung" else "Perangkat ini") {
        ConsoleNote(
            "Launcher tidak bisa mengubah Audio HAL atau efek suara pabrikan. Tombol di sini hanya membuka pengaturan resmi yang " +
                "terdeteksi ada di perangkat ini.",
        )
        if (ui.shortcuts.isEmpty()) ConsoleNote("Tidak ada pengaturan audio sistem yang bisa dibuka dari sini.")
        ui.shortcuts.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clearAndSetSemantics { }) {
                    Text(s.label, color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium)
                    if (s.handler.isNotBlank()) Text("Dibuka oleh ${s.handler}", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                ConsoleButton(
                    "Buka", { actions.onShortcut(s) },
                    description = "Buka ${s.label}" + if (s.handler.isNotBlank()) ", dibuka oleh ${s.handler}" else "",
                )
            }
        }
        ReadingRow("Akses lanjutan", accessReading(ui.access))
    }
}

private fun accessReading(s: AdvancedAccess.State): Reading =
    if (s.dumpGranted) Reading.reported("Aktif (izin DUMP)", "Diberikan lewat ADB atau Shizuku")
    else Reading.unavailable("Belum diberikan; lihat panduan di panel Akses lanjutan")

private fun shizukuReading(s: AdvancedAccess.ShizukuState): Reading = when (s) {
    AdvancedAccess.ShizukuState.NOT_INSTALLED -> Reading.unavailable("Tidak terpasang (opsional)")
    AdvancedAccess.ShizukuState.NOT_RUNNING -> Reading.unavailable("Terpasang tetapi belum berjalan; nyalakan di aplikasi Shizuku")
    AdvancedAccess.ShizukuState.UNSUPPORTED -> Reading.unavailable("Versi Shizuku terlalu lama; perbarui")
    AdvancedAccess.ShizukuState.NEEDS_PERMISSION -> Reading.reported("Berjalan · belum mengizinkan launcher", "Shizuku akan meminta persetujuan Anda")
    AdvancedAccess.ShizukuState.READY -> Reading.reported("Berjalan · launcher diizinkan", "Dilaporkan Shizuku")
}

/** Panduan & status Akses lanjutan (izin DUMP lewat ADB atau Shizuku). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedPanel(ui: ConsoleUi, actions: ConsoleActions) {
    val s = ui.access
    var confirming by rememberSaveable { mutableStateOf(false) }
    ConsolePanel("Akses lanjutan", lit = s.dumpGranted) {
        ReadingRow("Izin DUMP", accessReading(s))
        ConsoleNote(
            "Memberi console: codec Bluetooth aktif, format & sample rate keluaran, resampling, dan deteksi sesi musik untuk EQ. " +
                "Tidak memberi: baterai perangkat Bluetooth (tidak dimuat laporan sistem) atau akses ke Audio HAL.",
        )
        ConsoleDivider()
        Text("Lewat ADB · sekali, tetap ada setelah HP restart", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        ConsoleNote(
            "1. Aktifkan Opsi pengembang, lalu USB debugging atau Wireless debugging.\n" +
                "2. Sambungkan HP ke komputer berisi ADB (atau pasangkan Wireless debugging).\n" +
                "3. Jalankan perintah ini, lalu ketuk Periksa lagi:",
        )
        Text(
            AdvancedAccess.ADB_COMMAND,
            color = ConsoleColors.Green,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = com.vinyl.launcher.ui.theme.MonoFamily,
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(ConsoleColors.Page)
                .padding(12.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleButton(if (ui.copied) "Disalin" else "Salin perintah", actions.onCopyCommand, description = "Salin perintah ADB")
            ConsoleButton("Periksa lagi", actions.onCheckAccess, description = "Periksa lagi status izin DUMP")
        }
        ConsoleNote(
            "Di sebagian perangkat, sistem bisa mencabut lagi izin dari ADB. Bila status kembali \"Belum diberikan\", cari opsi " +
                "\"Disable permission monitoring\" di Opsi pengembang (bila ada), lalu ulangi perintahnya.",
        )
        ConsoleDivider()
        Text("Lewat Shizuku · opsional", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        ReadingRow("Shizuku", shizukuReading(s.shizuku))
        s.lastGrant?.let { ReadingRow("Hasil terakhir", it) }
        val canGrant = !s.dumpGranted && !s.working &&
            (s.shizuku == AdvancedAccess.ShizukuState.NEEDS_PERMISSION || s.shizuku == AdvancedAccess.ShizukuState.READY)
        ConsoleButton(
            if (s.working) "Menunggu Shizuku…" else "Berikan izin lewat Shizuku", { confirming = true }, enabled = canGrant,
        )
        ConsoleNote("Tanpa root, Shizuku perlu dinyalakan ulang lewat Wireless debugging setiap HP restart. Izin DUMP yang sudah diberikan tetap ada.")
        ConsoleDivider()
        ConsoleNote(
            "Privasi: laporan sistem dibaca di memori hanya saat console terbuka atau saat EQ mendeteksi sesi, dan hanya nilai yang tampil " +
                "yang diambil. Isinya tidak disimpan, tidak dicatat, dan tidak dikirim. Mencabut izin: ${AdvancedAccess.ADB_REVOKE}",
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Jalankan lewat Shizuku?") },
            text = {
                Column {
                    Text("Launcher akan meminta Shizuku menjalankan satu perintah dengan hak shell:")
                    Text(
                        "pm grant ${com.vinyl.launcher.advanced.ShellService.APP_ID} android.permission.DUMP",
                        fontFamily = com.vinyl.launcher.ui.theme.MonoFamily,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Text("Tidak ada perintah lain. Izin bisa dicabut kapan saja dengan perintah revoke.")
                }
            },
            confirmButton = { TextButton(onClick = { confirming = false; actions.onGrantShizuku() }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Batal") } },
        )
    }
}
