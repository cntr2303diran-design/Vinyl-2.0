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
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
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
import com.vinyl.launcher.audio.RememberedBattery
import com.vinyl.launcher.audio.SystemAudioShortcuts
import com.vinyl.launcher.audio.SystemAudioShortcuts.Kind
import com.vinyl.launcher.audio.ToneSynth
import com.vinyl.launcher.data.AudioConsoleRules
import com.vinyl.launcher.data.AudioConsoleSettings
import com.vinyl.launcher.data.PlayerRules
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.audio.EqMode
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.player.HiFi
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
    /** Izin baca baterai Bluetooth (Perangkat sekitar) sudah diberikan. */
    val btPermission: Boolean = false,
    /** Level baterai Bluetooth per alamat dari siaran sistem. */
    val btLevels: Map<String, Int> = emptyMap(),
    /** Level terakhir yang diingat (kunci sidik alamat) dan waktu sekarang untuk umurnya. */
    val btRemembered: Map<String, RememberedBattery> = emptyMap(),
    val nowMs: Long = 0L,
    /** Pengaturan pemutar bawaan (EQ parametrik, ReplayGain, crossfeed, profil AutoEQ tersimpan). */
    val player: PlayerSettings = PlayerSettings(),
    /** Jalur suara pemutar bawaan saat ini. */
    val path: HiFi.PathState = HiFi.PathState(),
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
    val onRequestBluetooth: () -> Unit = {},
    /** Ubah pengaturan pemutar bawaan (dipratinjau langsung ke rantai suara, disimpan sesaat kemudian). */
    val onPlayer: ((PlayerSettings) -> PlayerSettings) -> Unit = {},
)

/** Tab Audio Console, urut dari yang paling sering dipakai. */
internal enum class ConsoleTab(val label: String) {
    PLAYER("Pemutar"),
    APPS("Aplikasi lain"),
    DEVICE("Perangkat"),
    TOOLS("Alat"),
}

/**
 * Halaman kanan: Audio Console bergaya receiver stereo, dibagi empat tab supaya tiap layar pendek. Tablet landscape:
 * panel tab dibagi tiga kolom; layar sedang: dua kolom; ponsel: satu kolom yang digulir. Setiap nilai diberi label
 * sumbernya (lihat [ReadingRow]). [initialTab] = indeks [ConsoleTab] yang dibuka (dipakai tes tangkapan layar).
 */
@Composable
fun ConsoleScreen(ui: ConsoleUi, actions: ConsoleActions, modifier: Modifier = Modifier, vu: @Composable () -> Unit = {}, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, ConsoleTab.entries.lastIndex)) }
    BoxWithConstraints(modifier.fillMaxSize().background(ConsoleColors.Page)) {
        val columns = when {
            maxWidth >= 900.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }
        // Panel tiap tab dan pembagiannya ke kolom (indeks ke daftar panel tab itu)
        val current = ConsoleTab.entries[tab.coerceIn(0, ConsoleTab.entries.lastIndex)]
        val panels: List<@Composable () -> Unit>
        val layout: List<List<Int>>
        when (current) {
            ConsoleTab.PLAYER -> {
                panels = listOf({ PathPanel(ui) }, { EqPanel(ui, actions) }, { OutputPanel(ui, actions) })
                layout = when (columns) { 1 -> listOf(listOf(0, 1, 2)); 2 -> listOf(listOf(0, 2), listOf(1)); else -> listOf(listOf(0), listOf(1), listOf(2)) }
            }
            ConsoleTab.APPS -> {
                panels = listOf({ SoundPanel(ui, actions) }, { ScopePanel(ui, actions) })
                layout = if (columns == 1) listOf(listOf(0, 1)) else listOf(listOf(0), listOf(1))
            }
            ConsoleTab.DEVICE -> {
                panels = listOf({ DevicesPanel(ui, actions) }, { QualityPanel(ui) }, { SpatialPanel(ui, actions) }, { DevicePanel(ui, actions) })
                layout = when (columns) { 1 -> listOf(listOf(0, 1, 2, 3)); 2 -> listOf(listOf(0, 3), listOf(1, 2)); else -> listOf(listOf(0), listOf(1, 2), listOf(3)) }
            }
            ConsoleTab.TOOLS -> {
                panels = listOf({ DiagnosticsPanel(ui, actions) }, { AutomationPanel(ui, actions) }, { AdvancedPanel(ui, actions) })
                layout = when (columns) { 1 -> listOf(listOf(0, 1, 2)); 2 -> listOf(listOf(0, 1), listOf(2)); else -> listOf(listOf(0), listOf(1), listOf(2)) }
            }
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
            ConsoleTabs(ConsoleTab.entries.map { it.label }, tab, { tab = it })
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
    var help by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali ke ruang TV", tint = ConsoleColors.Text)
            }
            Text(
                "AUDIO CONSOLE", color = ConsoleColors.Text, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            )
            Box(Modifier.widthIn(max = 180.dp)) { vu() }
            IconButton(onClick = { help = !help }) {
                Icon(
                    Icons.Rounded.Info, if (help) "Tutup penjelasan label sumber" else "Penjelasan label sumber",
                    tint = if (help) ConsoleColors.Amber else ConsoleColors.TextSecondary,
                )
            }
        }
        if (help) {
            ConsoleNote(
                "Setiap nilai berlabel sumbernya: Terukur, Dilaporkan sistem, Perkiraan, atau Tidak tersedia beserta alasannya. Tidak ada nilai yang ditebak.",
                Modifier.padding(start = 52.dp, top = 4.dp),
            )
        }
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
    ConsolePanel("Perangkat", help = "Daftar keluaran musik yang dilaporkan sistem. Baterai Bluetooth memakai laporan sistem lewat jalur tidak resmi, jadi bisa tidak tersedia di sebagian perangkat.") {
        ReadingRow("Keluaran aktif", ui.snapshot.active.reading)
        val rows = ConsoleRows.devices(ui.snapshot)
        if (rows.isEmpty()) ConsoleNote("Tidak ada perangkat keluaran musik yang dilaporkan sistem.")
        rows.forEach { DeviceLine(it) }
        ReadingRow("Baterai", ConsoleRows.battery(ui.snapshot, ui.advanced, ui.btPermission, ui.btLevels, ui.btRemembered, ui.nowMs))
        if (!ui.btPermission) {
            ConsoleButton("Izinkan baterai Bluetooth", actions.onRequestBluetooth, description = "Minta izin Perangkat sekitar untuk membaca baterai Bluetooth")
        }
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

    var importNote by rememberSaveable { mutableStateOf<String?>(null) }
    val importer = rememberEqImporter { result ->
        if (result == null) {
            importNote = "Bukan berkas ParametricEQ.txt yang dikenali."
        } else {
            actions.onPlayer { PlayerRules.saveEq(it, result.profile) }
            actions.onAudio { it.copy(eqMode = EqMode.PROFILE, profile = result.profile) }
            importNote = "\"${result.profile.name}\" dipakai untuk semua aplikasi."
        }
    }

    ConsolePanel(
        "Suara aplikasi lain", lit = a.eqEnabled,
        help = "Efek DynamicsProcessing 31 pita yang dipasang pada sesi audio aplikasi musik lain (Spotify, YouTube Music, …). " +
            "Bisa bentrok dengan efek suara bawaan perangkat (mis. Dolby) dan aplikasi equalizer lain; bila status menyebut " +
            "\"dikendalikan aplikasi efek lain\", matikan salah satunya.",
    ) {
        ConsoleSwitch("Equalizer", a.eqEnabled, { on -> actions.onAudio { it.copy(eqEnabled = on) } })
        ReadingRow("Diproses", EqSessions.summary(a.eqEnabled, ui.sessions))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((m, label) in listOf(EqMode.THREE to "3 pita", EqMode.GRAPHIC10 to "10 pita", EqMode.PROFILE to "Profil / AutoEQ")) {
                ConsoleButton(label, {
                    actions.onAudio { s ->
                        // Mode profil tanpa profil: pakai profil pemutar bawaan yang sedang dipilih (bila ada)
                        if (m == EqMode.PROFILE && s.profile == null) s.copy(eqMode = m, profile = ui.player.eq.takeIf { it.bands.isNotEmpty() } ?: ui.player.savedEqs.firstOrNull())
                        else s.copy(eqMode = m)
                    }
                }, selected = a.eqMode == m)
            }
        }
        when (a.eqMode) {
            EqMode.THREE -> {
                // Sumbu frekuensi EQ selalu rendah → tinggi dari kiri, juga pada bahasa RTL (seperti equalizer fisik)
                // Huruf besar: deretan fader digulir mendatar (SpaceEvenly tidak berlaku di wadah yang bisa digulir)
                val big = LocalDensity.current.fontScale > 1.15f
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(
                    if (big) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
                    horizontalArrangement = if (big) Arrangement.spacedBy(8.dp) else Arrangement.SpaceEvenly,
                ) {
                    val subs = listOf("≤ 250 Hz", "0,25–4 kHz", "≥ 4 kHz")
                    val values = Eq.bands(a.values)
                    Eq.BAND_NAMES.forEachIndexed { i, name ->
                        Fader(name, subs[i], values[i], Eq.MIN_DB..Eq.MAX_DB, Eq::dbText, { v ->
                            edit { when (i) { 0 -> it.copy(lowDb = v); 1 -> it.copy(midDb = v); else -> it.copy(highDb = v) } }
                        })
                    }
                    Fader("Preamp", "masukan", a.values.preampDb, Eq.PREAMP_MIN_DB..Eq.PREAMP_MAX_DB, Eq::dbText, { v -> edit { it.copy(preampDb = v) } })
                } }
            }
            EqMode.GRAPHIC10 -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        val g = Eq.cleanGraphic(a.graphic)
                        Eq.GRAPHIC_LABELS.forEachIndexed { i, label ->
                            Fader(label, "Hz", g[i], Eq.MIN_DB..Eq.MAX_DB, Eq::dbText, { v ->
                                actions.onAudio { s -> s.copy(graphic = Eq.cleanGraphic(s.graphic).mapIndexed { j, x -> if (j == i) v else x }) }
                            })
                        }
                        Fader("Preamp", "masukan", a.values.preampDb, Eq.PREAMP_MIN_DB..Eq.PREAMP_MAX_DB, Eq::dbText, { v -> edit { it.copy(preampDb = v) } })
                    }
                }
                ConsoleButton("Datarkan 10 pita", { actions.onAudio { it.copy(graphic = List(10) { 0f }) } })
            }
            EqMode.PROFILE -> {
                val prof = a.profile
                EqCurve(prof?.bands.orEmpty(), Modifier.fillMaxWidth().height(110.dp))
                Text(
                    prof?.let { "${it.name} · preamp ${Eq.dbText(it.preampDb)}" } ?: "Belum ada profil",
                    color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily,
                )
                ProfileChips(
                    saved = ui.player.savedEqs, current = prof?.name.orEmpty(),
                    onPick = { p -> actions.onAudio { it.copy(profile = p) } },
                    onImport = importer, onSave = null, onDelete = null, onFlat = null,
                )
                ConsoleNote("Didekati 31 pita; pemutar bawaan memakai biquad persis.")
            }
        }
        importNote?.let { ConsoleNote(it) }
        LineSlider(
            "Balance", a.values.balance, -1f..1f, balanceText(a.values.balance), "Kiri", "Kanan",
            { v -> edit { it.copy(balance = v) } },
        )
        if (a.eqMode == EqMode.THREE) {
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
        }
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

/** Cakupan EQ aplikasi lain: kapan dan ke mana efek dipasang. */
@Composable
private fun ScopePanel(ui: ConsoleUi, actions: ConsoleActions) {
    val a = ui.audio
    ConsolePanel(
        "Cakupan", lit = a.eqEnabled,
        help = "Pengaman clipping menurunkan masukan sebesar penguatan pita terbesar. Cadangan: bila aplikasi musik tidak membuka sesi efek, " +
            "EQ dipasang ke keluaran gabungan (sesi 0) dan dilepas lagi saat sesi aplikasi muncul; cara lama Android, di sebagian perangkat " +
            "tidak terdengar. \"Selalu pasang\" memasang sesi 0 walau ada sesi aplikasi (EQ bisa terdengar dua kali; eksperimental). " +
            "Deteksi sesi memakai laporan sistem dan butuh Akses lanjutan (izin DUMP).",
    ) {
        ConsoleSwitch("Pengaman clipping", a.safeHeadroom, { on -> actions.onAudio { it.copy(safeHeadroom = on) } })
        ConsoleSwitch(
            "Cadangan: semua audio", a.globalFallback, { on -> actions.onAudio { it.copy(globalFallback = on) } },
            enabled = a.eqEnabled, detail = "Bila tak ada sesi efek",
        )
        ConsoleSwitch(
            "Selalu pasang ke semua audio", a.globalExperimental, { on -> actions.onAudio { it.copy(globalExperimental = on) } },
            enabled = a.eqEnabled, detail = "Eksperimental",
        )
        ConsoleSwitch(
            "Deteksi sesi lewat laporan sistem", a.detectSessions, { on -> actions.onAudio { it.copy(detectSessions = on) } },
            enabled = ui.access.dumpGranted, detail = if (ui.access.dumpGranted) null else "Butuh Akses lanjutan",
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
    ConsolePanel(
        "Kualitas",
        help = "Format sumber (lagu) dan format keluaran (yang dikirim ke perangkat) dibedakan. Kemampuan perangkat bukan format yang sedang dipakai.",
    ) {
        ConsoleRows.quality(ui.snapshot, ui.advanced).forEach { ReadingRow(it.label, it.reading) }
    }
}

@Composable
private fun SpatialPanel(ui: ConsoleUi, actions: ConsoleActions) {
    ConsolePanel("Spasial & efek", help = "Status hanya dibaca. Efek bawaan perangkat diatur di aplikasinya sendiri lewat tombol di panel ini.") {
        ConsoleRows.spatial(ui.snapshot).forEach { ReadingRow(it.label, it.reading) }
        ShortcutButtons(ui, actions, listOf(Kind.EFFECTS_PANEL, Kind.SOUND_SETTINGS))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsPanel(ui: ConsoleUi, actions: ConsoleActions) {
    ConsolePanel(
        "Diagnostik",
        help = "Tes kanal: nada pendek 1,2 detik (kiri 440 Hz, kanan 660 Hz) pada level aman lewat keluaran media saat ini; musik tidak dijeda. " +
            "Visualizer audio asli memakai izin mikrofon hanya untuk spektrum, bukan rekaman.",
    ) {
        ConsoleRows.diagnostics(ui.snapshot).forEach { ReadingRow(it.label, it.reading) }
        ConsoleDivider()
        Text("Tes kanal", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleButton("Kiri", { actions.onTone(ToneSynth.Side.LEFT) }, selected = ui.tone == ToneSynth.Side.LEFT, description = "Putar nada tes di kanal kiri")
            ConsoleButton("Kanan", { actions.onTone(ToneSynth.Side.RIGHT) }, selected = ui.tone == ToneSynth.Side.RIGHT, description = "Putar nada tes di kanal kanan")
            ConsoleButton("Berhenti", actions.onStopTone, enabled = ui.tone != null, description = "Hentikan nada tes")
        }
        ConsoleDivider()
        ReadingRow(
            "Visualizer audio asli",
            if (ui.micGranted) Reading.reported("Izin diberikan", "Izin mikrofon (hanya spektrum, bukan rekaman)")
            else Reading.unavailable("Izin mikrofon belum diberikan (opsional)"),
        )
    }
}

@Composable
private fun AutomationPanel(ui: ConsoleUi, actions: ConsoleActions) {
    val a = ui.audio
    ConsolePanel(
        "Otomasi", lit = a.profiles.isNotEmpty() || a.openConsoleOnUsb,
        help = "Aturan berjalan selama launcher aktif; tidak ada layanan latar tambahan. Profil perangkat dibuat dari panel Suara aplikasi lain saat perangkatnya menjadi keluaran.",
    ) {
        ConsoleSwitch(
            "Kembalikan preset saat terputus", a.restoreOnDisconnect, { on -> actions.onAudio { it.copy(restoreOnDisconnect = on) } },
            detail = "Saat perangkat berprofil terputus",
        )
        ConsoleSwitch(
            "USB DAC tersambung → buka console", a.openConsoleOnUsb, { on -> actions.onAudio { it.copy(openConsoleOnUsb = on) } },
            detail = "Dibuka di tampilan beranda berikutnya",
        )
        if (a.profiles.isEmpty()) {
            ConsoleNote("Belum ada profil perangkat.")
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
    ConsolePanel(
        if (ui.isSamsung) "Optimasi Samsung" else "Perangkat ini",
        help = "Launcher tidak bisa mengubah Audio HAL atau efek suara pabrikan. Tombol di sini hanya membuka pengaturan resmi yang terdeteksi ada di perangkat ini.",
    ) {
        if (ui.shortcuts.isEmpty()) ConsoleNote("Tidak ada pengaturan audio sistem yang bisa dibuka.")
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
    val context = androidx.compose.ui.platform.LocalContext.current
    ConsolePanel(
        "Akses lanjutan", lit = s.dumpGranted,
        help = "Izin DUMP memberi console: codec Bluetooth aktif, format & sample rate keluaran, resampling, dan deteksi sesi musik untuk EQ. " +
            "Tidak memberi baterai perangkat Bluetooth atau akses ke Audio HAL. Privasi: laporan sistem dibaca di memori hanya saat console " +
            "terbuka atau saat EQ mendeteksi sesi, hanya nilai yang tampil yang diambil, dan isinya tidak disimpan, dicatat, atau dikirim. " +
            "Mencabut izin: ${AdvancedAccess.ADB_REVOKE}",
    ) {
        ReadingRow("Izin DUMP", accessReading(s))
        ConsoleDivider()
        ConsoleExpander("Panduan tanpa komputer (Shizuku, Android 11+)") {
            ConsoleNote(
                "1. Pasang aplikasi Shizuku.\n" +
                    "2. Aktifkan Opsi pengembang: Pengaturan → Tentang ponsel → Informasi perangkat lunak → ketuk Nomor versi 7 kali.\n" +
                    "3. Sambungkan ke Wi-Fi, lalu nyalakan Wireless debugging di Opsi pengembang.\n" +
                    "4. Buka Shizuku → Pairing: masukkan kode pasangan dari Opsi pengembang ke notifikasi Shizuku, lalu Start.\n" +
                    "5. Kembali ke sini dan ketuk \"Berikan izin lewat Shizuku\". Cukup sekali: izin DUMP tetap ada walau Shizuku berhenti atau HP restart.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConsoleButton(
                    if (s.shizuku == AdvancedAccess.ShizukuState.NOT_INSTALLED) "Pasang Shizuku" else "Buka Shizuku",
                    { SystemSettingsIntents.openShizuku(context) },
                )
                ConsoleButton("Tentang ponsel", { SystemSettingsIntents.open(context, android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS) })
                ConsoleButton("Opsi pengembang", { SystemSettingsIntents.open(context, android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })
            }
            ReadingRow("Shizuku", shizukuReading(s.shizuku))
            s.lastGrant?.let { ReadingRow("Hasil terakhir", it) }
            val canGrant = !s.dumpGranted && !s.working &&
                (s.shizuku == AdvancedAccess.ShizukuState.NEEDS_PERMISSION || s.shizuku == AdvancedAccess.ShizukuState.READY)
            ConsoleButton(
                if (s.working) "Menunggu Shizuku…" else "Berikan izin lewat Shizuku", { confirming = true }, enabled = canGrant,
            )
            ConsoleNote("Tanpa root, Shizuku perlu dinyalakan ulang setiap HP restart, tetapi hanya bila ingin memberi izin lagi.")
        }
        ConsoleExpander("Panduan dengan komputer (ADB)") {
            ConsoleNote(
                "1. Aktifkan Opsi pengembang, lalu USB debugging atau Wireless debugging.\n" +
                    "2. Sambungkan HP ke komputer berisi ADB.\n" +
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
            }
            ConsoleNote(
                "Bila status kembali \"Belum diberikan\", cari opsi \"Disable permission monitoring\" di Opsi pengembang (bila ada), lalu ulangi perintahnya.",
            )
        }
        ConsoleButton("Periksa lagi", actions.onCheckAccess, description = "Periksa lagi status izin DUMP")
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
