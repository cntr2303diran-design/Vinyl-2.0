package com.vinyl.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.Anchor
import com.vinyl.launcher.data.BackgroundSettings
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.ClockSettings
import com.vinyl.launcher.data.ClockType
import com.vinyl.launcher.data.CoverElement
import com.vinyl.launcher.data.CoverSettings
import com.vinyl.launcher.data.FontChoice
import com.vinyl.launcher.data.GestureAction
import com.vinyl.launcher.data.GestureBinding
import com.vinyl.launcher.data.GestureSettings
import com.vinyl.launcher.data.HomeSettings
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.data.TurntablePosition
import com.vinyl.launcher.data.WidgetConfig
import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.KnobStyle
import com.vinyl.launcher.data.ReelSpeed
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.data.VfdColor
import com.vinyl.launcher.data.WidgetType
import com.vinyl.launcher.ui.theme.label
import com.vinyl.launcher.ui.theme.toColor
import com.vinyl.launcher.ui.theme.toLongArgb
import com.vinyl.launcher.ui.theme.Corner
import kotlinx.coroutines.launch

/*
 * Kontrol pengaturan yang dipakai bersama oleh editor (pratinjau langsung)
 * dan halaman Pengaturan. Semua kontrol bersifat "stateless": menerima nilai
 * dan mengembalikan nilai baru lewat callback.
 */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 18.dp, top = 26.dp, bottom = 8.dp),
    )
}

/** Kartu pengelompokan ala One UI / daftar berkelompok iOS. */
@Composable
fun ControlCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corner.l)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun ControlLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(label: String?, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        if (label != null) ControlLabel(label)
        // Pil pilihan ala segmented control iOS: terpilih = putih solid, lainnya = kaca tipis
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            options.forEach { o ->
                val sel = o == selected
                Text(
                    text(o),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) Color(0xFF111113) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.07f))
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(o) })
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // klik ditangani baris (toggleable) agar TalkBack hanya melihat satu kontrol
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, valueText: (Float) -> String = { "%.0f%%".format(it * 100) }) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ControlLabel(label)
            Spacer(Modifier.weight(1f))
            Text(valueText(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText(value)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
        )
    }
}

private val SWATCHES = listOf(
    0xFFF2EFEA, 0xFFFFFFFF, 0xFF9B9893, 0xFF1B1B1F, 0xFF0E0E10, 0xFFE07A3F, 0xFFE5534B,
    0xFFF2C14E, 0xFF7BD389, 0xFF4FB3BF, 0xFF5B7FDE, 0xFF9C6ADE, 0xFFE38FB4, 0xFF2B1E3A,
)

/** Pemilih warna: palet cepat + dialog HSV untuk warna kustom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRow(label: String, color: Long, onChange: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    Column {
        ControlLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { sw ->
                Box(
                    Modifier.minimumInteractiveComponentSize().size(30.dp).clip(CircleShape).background(sw.toColor())
                        .border(if (sw == color) 2.dp else 1.dp, if (sw == color) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), CircleShape)
                        .selectable(selected = sw == color, role = Role.RadioButton, onClick = { onChange(sw) })
                        .semantics { contentDescription = "$label: warna #%06X".format(sw and 0xFFFFFF) },
                    contentAlignment = Alignment.Center,
                ) { if (sw == color) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = if (sw > 0xFF808080) Color.Black else Color.White) }
            }
            Box(
                Modifier.minimumInteractiveComponentSize().size(30.dp).clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .clickable(role = Role.Button) { custom = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Warna kustom", Modifier.size(16.dp), tint = Color.White) }
        }
    }
    if (custom) HsvDialog(color, onDismiss = { custom = false }, onPick = { onChange(it); custom = false })
}

@Composable
private fun HsvDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val current = Color.hsv(h, s, v)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(current.toLongArgb()) }) { Text("Pakai") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        title = { Text("Warna kustom") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(Corner.s).background(current))
                SliderRow("Rona", h / 360f, 0f..1f, { h = it * 360f }) { "${(it * 360).toInt()}°" }
                SliderRow("Saturasi", s, 0f..1f, { s = it })
                SliderRow("Kecerahan", v, 0f..1f, { v = it })
            }
        },
    )
}

/** Pemilih posisi 3x3. */
@Composable
fun AnchorPicker(label: String, selected: Anchor, onSelect: (Anchor) -> Unit) {
    Column {
        ControlLabel(label)
        Column(
            Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Anchor.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { a ->
                        // Sel 48dp (batas target sentuh); kotak yang terlihat tetap kecil di tengahnya
                        Box(
                            Modifier.size(48.dp)
                                .selectable(selected = a == selected, role = Role.RadioButton, onClick = { onSelect(a) })
                                .semantics { contentDescription = "$label: ${a.description()}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(width = 34.dp, height = 24.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (a == selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f))
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Nama posisi untuk pembaca layar. */
private fun Anchor.description() = when (this) {
    Anchor.TOP_START -> "kiri atas"
    Anchor.TOP_CENTER -> "tengah atas"
    Anchor.TOP_END -> "kanan atas"
    Anchor.CENTER_START -> "kiri tengah"
    Anchor.CENTER -> "tengah"
    Anchor.CENTER_END -> "kanan tengah"
    Anchor.BOTTOM_START -> "kiri bawah"
    Anchor.BOTTOM_CENTER -> "tengah bawah"
    Anchor.BOTTOM_END -> "kanan bawah"
}

fun ClockType.label() = when (this) {
    ClockType.THIN -> "Digital tipis"
    ClockType.BOLD -> "Digital tebal"
    ClockType.FLIP -> "Flip clock"
    ClockType.ANALOG -> "Analog"
    ClockType.CIRCULAR -> "Melingkar"
    ClockType.VFD -> "VFD (layar TV)"
}

/** Kontrol gaya jam (dipakai di home & layar kunci). */
@Composable
fun ClockControls(
    clock: ClockSettings,
    onChange: (ClockSettings) -> Unit,
    allowHide: Boolean = true,
    showAnchor: Boolean = true,
    showDateToggle: Boolean = true,
) {
    if (allowHide) SwitchRow("Tampilkan jam", clock.visible, { onChange(clock.copy(visible = it)) })
    ChipRow("Gaya jam", ClockType.entries, clock.type, { it.label() }) { onChange(clock.copy(type = it)) }
    ChipRow("Font", FontChoice.entries, clock.font, { it.label() }) { onChange(clock.copy(font = it)) }
    ColorRow("Warna", clock.color) { onChange(clock.copy(color = it)) }
    SliderRow("Ukuran", clock.scale, 0.3f..2f, { onChange(clock.copy(scale = it)) })
    SwitchRow("Format 24 jam", clock.use24h, { onChange(clock.copy(use24h = it)) })
    if (showDateToggle) SwitchRow("Tampilkan tanggal di bawah jam", clock.showDate, { onChange(clock.copy(showDate = it)) })
    if (showAnchor) AnchorPicker("Posisi", clock.anchor) { onChange(clock.copy(anchor = it)) }
}

/** Mengembalikan fungsi untuk membuka Photo Picker; gambar disalin ke penyimpanan internal. */
@Composable
fun rememberImagePicker(name: String, onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            SettingsRepository.get(context).importImage(uri, name)?.let(onPicked)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

fun BackgroundType.label() = when (this) {
    BackgroundType.ALBUM -> "Warna cover album"
    BackgroundType.SOLID -> "Warna solid"
    BackgroundType.GRADIENT -> "Gradasi"
    BackgroundType.IMAGE -> "Gambar galeri"
}

@Composable
fun BackgroundControls(bg: BackgroundSettings, allowGradient: Boolean, pickerName: String, onChange: (BackgroundSettings) -> Unit) {
    val pick = rememberImagePicker(pickerName) { onChange(bg.copy(type = BackgroundType.IMAGE, imagePath = it)) }
    val types = BackgroundType.entries.filter { allowGradient || it != BackgroundType.GRADIENT }
    ChipRow("Latar", types, bg.type, { it.label() }) {
        if (it == BackgroundType.IMAGE && bg.imagePath == null) pick() else onChange(bg.copy(type = it))
    }
    when (bg.type) {
        BackgroundType.SOLID -> ColorRow("Warna latar", bg.solidColor) { onChange(bg.copy(solidColor = it)) }
        BackgroundType.GRADIENT -> {
            ColorRow("Warna awal", bg.gradientStart) { onChange(bg.copy(gradientStart = it)) }
            ColorRow("Warna akhir", bg.gradientEnd) { onChange(bg.copy(gradientEnd = it)) }
        }
        BackgroundType.IMAGE -> OutlinedButton(onClick = pick, modifier = Modifier.padding(top = 6.dp)) { Text("Pilih gambar lain…") }
        BackgroundType.ALBUM -> Unit
    }
}

fun TurntablePosition.label() = when (this) {
    TurntablePosition.LEFT -> "Kiri"
    TurntablePosition.CENTER -> "Tengah"
    TurntablePosition.RIGHT -> "Kanan"
}

fun WidgetType.label() = when (this) {
    WidgetType.CALENDAR -> "Kalender mini"
    WidgetType.WEATHER -> "Cuaca"
    WidgetType.BATTERY -> "Baterai"
    WidgetType.PHOTO -> "Foto"
}

/** Pemilih beberapa foto sekaligus; foto disalin ke penyimpanan internal. */
@Composable
fun rememberMultiImagePicker(onPicked: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val repo = SettingsRepository.get(context)
            val paths = uris.mapIndexedNotNull { i, u -> repo.importImage(u, "photo$i") }
            if (paths.isNotEmpty()) onPicked(paths)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Bagian editor home screen (dipakai sebagai tab di panel edit). */
enum class HomeSection(val title: String) {
    TV("TV & kaset"), BACKGROUND("Dinding"), CLOCK("Jam"), WIDGETS("Widget")
}

/**
 * Kontrol untuk satu bagian gaya layar utama, dibungkus kartu. Pengaturan gaya TV ([RetroSettings]) disimpan langsung
 * lewat [onRetro]; pengaturan [HomeSettings] lewat [onChange] (di panel edit beranda: draf yang disimpan dengan tombol Simpan).
 */
@Composable
fun HomeSectionControls(
    section: HomeSection,
    home: HomeSettings,
    retro: RetroSettings,
    onChange: (HomeSettings) -> Unit,
    onRetro: (RetroSettings) -> Unit,
) {
    when (section) {
        HomeSection.TV -> TvSectionControls(home, retro, onChange, onRetro)
        HomeSection.BACKGROUND -> {
            SectionTitle("Dinding ruang")
            ControlCard {
                ChipRow("Ruang", RoomTone.entries, retro.room, { it.label() }) { onRetro(retro.copy(room = it)) }
                BackgroundControls(home.background, allowGradient = true, pickerName = "home") { onChange(home.copy(background = it)) }
                Text(
                    "Album: cahaya layar mengikuti warna cover. Warna/gradien: dinding diwarnai. Gambar: gambar Anda diproyeksikan samar di dinding.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        HomeSection.CLOCK -> {
            SectionTitle("Jam di sudut")
            ControlCard {
                SwitchRow("Tampilkan TIME di kiri atas", home.clock.visible, { onChange(home.copy(clock = home.clock.copy(visible = it))) })
                SwitchRow("Format 24 jam", home.clock.use24h, { onChange(home.copy(clock = home.clock.copy(use24h = it))) })
                Text(
                    "Gaya jam besar (tipis, flip, analog, dan lainnya) tetap ada di Layar kunci.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        HomeSection.WIDGETS -> {
            SectionTitle("Widget tambahan")
            ControlCard {
                WidgetControls(home.widgets) { onChange(home.copy(widgets = it)) }
                ColorRow("Warna teks widget", home.clock.color) { onChange(home.copy(clock = home.clock.copy(color = it))) }
            }
        }
    }
}

/** TV, kaset, knob, layar CRT, dan VFD (pengganti bagian Turntable, Piringan & lengan, Sampul album). */
@Composable
private fun TvSectionControls(home: HomeSettings, retro: RetroSettings, onChange: (HomeSettings) -> Unit, onRetro: (RetroSettings) -> Unit) {
    SectionTitle("TV")
    ControlCard {
        ChipRow("Bodi TV", TvBody.entries, retro.tvBody, { it.label() }) { onRetro(retro.copy(tvBody = it)) }
        SliderRow("Ukuran", home.turntableSize, 0.5f..1f, { onChange(home.copy(turntableSize = it)) })
        ChipRow("Posisi (layar mendatar)", TurntablePosition.entries, home.turntablePosition, { it.label() }) { onChange(home.copy(turntablePosition = it)) }
        ChipRow("Knob", KnobStyle.entries, retro.knobStyle, { it.label() }) { onRetro(retro.copy(knobStyle = it)) }
        ChipRow("Warna VFD", VfdColor.entries, retro.vfdColor, { it.label() }) { onRetro(retro.copy(vfdColor = it)) }
    }
    SectionTitle("Layar CRT")
    ControlCard {
        SwitchRow("Tampilkan cover", home.coverVisible, { onChange(home.copy(coverVisible = it)) }, subtitle = "Mati: layar hanya berwarna aksen lagu")
        SwitchRow("Judul & artis di layar", home.trackInfoVisible, { onChange(home.copy(trackInfoVisible = it)) })
        SwitchRow(
            "Lirik saat TV didekatkan", home.lyricsVisible, { onChange(home.copy(lyricsVisible = it)) },
            subtitle = "Ketuk TV untuk mendekat. Lirik dari LRCLIB, butuh internet",
        )
        ChipRow("Gerak cover", AlbumMotion.entries, retro.albumMotion, { it.label() }) { onRetro(retro.copy(albumMotion = it)) }
        SliderRow("Efek tabung (scanline, tepi gelap)", retro.crtStrength, 0f..1f, { onRetro(retro.copy(crtStrength = it)) })
        SliderRow("Cahaya layar di dinding", retro.glowIntensity, 0f..1f, { onRetro(retro.copy(glowIntensity = it)) })
    }
    SectionTitle("Kaset")
    ControlCard {
        ChipRow("Cangkang", CassetteShell.entries, retro.cassetteShell, { it.label() }) { onRetro(retro.copy(cassetteShell = it)) }
        ChipRow("Gulungan", ReelSpeed.entries, retro.reelSpeed, { it.label() }) { onRetro(retro.copy(reelSpeed = it)) }
    }
    SectionTitle("Halaman")
    ControlCard {
        SwitchRow(
            "Tukar halaman kiri & kanan", retro.swapPages, { onRetro(retro.copy(swapPages = it)) },
            subtitle = if (retro.swapPages) "Audio Console di kiri, perpustakaan kaset di kanan" else "Perpustakaan kaset di kiri, Audio Console di kanan",
        )
    }
}

fun TvBody.label() = when (this) {
    TvBody.CREAM -> "Krem"
    TvBody.WALNUT -> "Kayu walnut"
    TvBody.CHARCOAL -> "Arang"
    TvBody.SPACE_AGE -> "Oranye space-age"
}

fun KnobStyle.label() = when (this) {
    KnobStyle.BAKELITE -> "Bakelit hitam"
    KnobStyle.CHROME -> "Krom"
    KnobStyle.BRASS -> "Kuningan"
}

fun CassetteShell.label() = when (this) {
    CassetteShell.BLACK -> "Hitam"
    CassetteShell.TRANSPARENT -> "Bening berasap"
    CassetteShell.COLORED -> "Warna aksen"
    CassetteShell.PICTURE -> "Bergambar cover"
}

fun ReelSpeed.label() = when (this) {
    ReelSpeed.NORMAL -> "Normal"
    ReelSpeed.FAST -> "Cepat"
}

fun AlbumMotion.label() = when (this) {
    AlbumMotion.OFF -> "Diam"
    AlbumMotion.ROTATE -> "Berputar"
    AlbumMotion.ZOOM -> "Zoom pelan"
    AlbumMotion.PERSPECTIVE -> "Perspektif"
    AlbumMotion.ANALOG -> "Getar analog"
}

fun RoomTone.label() = when (this) {
    RoomTone.DIM -> "Gelap"
    RoomTone.LIGHT -> "Terang"
}

fun VfdColor.label() = when (this) {
    VfdColor.GREEN -> "Hijau"
    VfdColor.AMBER -> "Amber"
}

/** Semua kontrol gaya layar utama (halaman Pengaturan). */
@Composable
fun HomeStyleControls(home: HomeSettings, retro: RetroSettings, onChange: (HomeSettings) -> Unit, onRetro: (RetroSettings) -> Unit) {
    HomeSection.entries.forEach { HomeSectionControls(it, home, retro, onChange, onRetro) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetControls(widgets: List<WidgetConfig>, onChange: (List<WidgetConfig>) -> Unit) {
    var replacingId by remember { mutableStateOf<Long?>(null) }
    val pickNew = rememberMultiImagePicker { paths ->
        onChange(widgets + WidgetConfig(System.currentTimeMillis(), WidgetType.PHOTO, x = 0.68f, y = 0.12f, images = paths))
    }
    val pickReplace = rememberMultiImagePicker { paths ->
        val id = replacingId
        onChange(widgets.map { if (it.id == id) it.copy(images = paths) else it })
        replacingId = null
    }
    Text(
        "Di mode edit layar utama: seret widget untuk memindahkan, seret titik putih di sudut untuk mengubah ukuran.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
        WidgetType.entries.forEach { t ->
            Text(
                "+ " + t.label(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable {
                        if (t == WidgetType.PHOTO) pickNew()
                        else onChange(widgets + WidgetConfig(System.currentTimeMillis(), t, x = 0.7f, y = (0.12f + 0.18f * widgets.size) % 0.8f))
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
    widgets.forEach { w ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                w.type.label() + if (w.type == WidgetType.PHOTO && w.images.size > 1) " (${w.images.size} foto)" else "",
                Modifier.weight(1f),
            )
            if (w.type == WidgetType.PHOTO) TextButton(onClick = { replacingId = w.id; pickReplace() }) { Text("Ganti foto") }
            TextButton(onClick = { onChange(widgets.filterNot { it.id == w.id }) }) { Text("Hapus") }
        }
    }
}

/** Kontrol layar kunci (cover screen). */
@Composable
fun CoverStyleControls(cover: CoverSettings, onChange: (CoverSettings) -> Unit, onRequestCalendarPermission: () -> Unit) {
    SectionTitle("Jam")
    ControlCard { ClockControls(cover.clock, { onChange(cover.copy(clock = it)) }, showDateToggle = false) }

    SectionTitle("Elemen layar kunci")
    ControlCard {
        ElementControls("Tanggal", cover.date) { onChange(cover.copy(date = it)) }
        ElementControls("Kalender dua bulan", cover.calendar) { onChange(cover.copy(calendar = it)) }
        if (cover.calendar.visible) SwitchRow(
            "Titik acara dari kalender perangkat",
            cover.calendarEvents,
            { on -> if (on) onRequestCalendarPermission(); onChange(cover.copy(calendarEvents = on)) },
            subtitle = "Memerlukan izin kalender",
        )
        ElementControls("Kartu musik", cover.songInfo) { onChange(cover.copy(songInfo = it)) }
        if (cover.songInfo.visible) SwitchRow("Lirik di bawah kartu musik", cover.lyricsVisible, { onChange(cover.copy(lyricsVisible = it)) })
        ElementControls("Baterai", cover.battery) { onChange(cover.copy(battery = it)) }
    }

    SectionTitle("Latar")
    ControlCard { BackgroundControls(cover.background, allowGradient = false, pickerName = "cover") { onChange(cover.copy(background = it)) } }
}

@Composable
private fun ElementControls(label: String, el: CoverElement, onChange: (CoverElement) -> Unit) {
    SwitchRow(label, el.visible, { onChange(el.copy(visible = it)) })
    if (el.visible) AnchorPicker("Posisi $label".lowercase().replaceFirstChar { it.uppercase() }, el.anchor) { onChange(el.copy(anchor = it)) }
}

fun GestureAction.label() = when (this) {
    GestureAction.NONE -> "Tidak ada"
    GestureAction.APP_DRAWER -> "App drawer"
    GestureAction.NOTIFICATIONS -> "Notifikasi"
    GestureAction.LOCK_SCREEN -> "Kunci layar"
    GestureAction.OPEN_APP -> "Buka aplikasi"
    GestureAction.SETTINGS -> "Pengaturan"
    GestureAction.CRATE -> "Perpustakaan kaset"
    GestureAction.PLAY_PAUSE -> "Putar/jeda"
    GestureAction.NEXT_TRACK -> "Lagu berikutnya"
    GestureAction.PREV_TRACK -> "Lagu sebelumnya"
    GestureAction.BACK -> "Kembali"
    GestureAction.RECENTS -> "Aplikasi terbaru"
    GestureAction.QUICK_SETTINGS -> "Panel pengaturan cepat"
    GestureAction.SCREENSHOT -> "Tangkapan layar"
    GestureAction.EDIT_HOME -> "Edit layar utama"
    GestureAction.TASKBAR_TOGGLE -> "Nyala/mati taskbar"
}

@Composable
fun GestureControls(gestures: GestureSettings, apps: List<AppInfo>, onChange: (GestureSettings) -> Unit) {
    GestureRow("Swipe ke atas", gestures.swipeUp, apps) { onChange(gestures.copy(swipeUp = it)) }
    GestureRow("Swipe ke bawah", gestures.swipeDown, apps) { onChange(gestures.copy(swipeDown = it)) }
    GestureRow("Ketuk dua kali", gestures.doubleTap, apps) { onChange(gestures.copy(doubleTap = it)) }
}

@Composable
fun GestureRow(label: String, binding: GestureBinding, apps: List<AppInfo>, onChange: (GestureBinding) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    ChipRow(label, GestureAction.entries, binding.action, { a ->
        if (a == GestureAction.OPEN_APP && binding.action == a)
            "Buka: " + (apps.firstOrNull { it.packageName == binding.packageName }?.label ?: "?")
        else a.label()
    }) { a ->
        if (a == GestureAction.OPEN_APP) picking = true else onChange(GestureBinding(a))
    }
    if (picking) AppPickerDialog(apps, title = "Pilih aplikasi", onDismiss = { picking = false }) {
        onChange(GestureBinding(GestureAction.OPEN_APP, it.packageName)); picking = false
    }
}

/** Dialog daftar aplikasi dengan pencarian. */
@Composable
fun AppPickerDialog(apps: List<AppInfo>, title: String, onDismiss: () -> Unit, onPick: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Cari") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.key }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(app) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(app.icon, null, Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
    )
}
