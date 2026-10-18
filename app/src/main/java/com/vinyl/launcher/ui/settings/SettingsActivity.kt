package com.vinyl.launcher.ui.settings

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.MAX_DOCK_APPS
import com.vinyl.launcher.data.DockPosition
import com.vinyl.launcher.data.NavOrder
import com.vinyl.launcher.data.SettingsMigration
import com.vinyl.launcher.service.NavButtonsService
import com.vinyl.launcher.ui.cover.CoverActivity
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.service.TaskbarService
import com.vinyl.launcher.util.SystemActions
import android.provider.Settings
import com.vinyl.launcher.ui.onboarding.PermissionSteps
import com.vinyl.launcher.ui.theme.VinylTheme
import com.vinyl.launcher.ui.theme.Corner

/** Halaman Pengaturan lengkap. Semua opsi disimpan langsung (DataStore). */
class SettingsActivity : ComponentActivity() {
    private val vm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { VinylTheme { SettingsScreen(vm, onBack = ::finish) } }
    }
}

/** Kategori pengaturan (sidebar kiri di tablet, daftar di layar sempit). */
private enum class Page(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color) {
    PERMISSIONS("Izin & status", "Launcher default, notifikasi, aksesibilitas", Icons.Rounded.VerifiedUser, Color(0xFF30D158)),
    HOME("Layar utama", "Latar, turntable, jam, widget", Icons.Rounded.Home, Color(0xFF0A84FF)),
    DRAWER("App drawer", "Sering dipakai, kategori, lencana, pencarian", Icons.Rounded.GridView, Color(0xFF34C759)),
    LOCK("Layar kunci", "Layar kunci vinyl & tampilannya", Icons.Rounded.Lock, Color(0xFF5E5CE6)),
    MUSIC("Musik", "Aplikasi default, lirik, rak, timer tidur", Icons.Rounded.MusicNote, Color(0xFFFF375F)),
    TASKBAR("Taskbar", "Bar aplikasi gaya PC", Icons.Rounded.Dock, Color(0xFFFF9F43)),
    NAV("Tombol navigasi", "Kembali, Home, Terbaru", Icons.Rounded.TouchApp, Color(0xFF64D2FF)),
    VISUAL("Efek visual", "Distorsi kaca, cahaya, partikel, parallax, visualizer", Icons.Rounded.GraphicEq, Color(0xFF00C7BE)),
    FX("Suara & getar", "Jarum, kresek, getar", Icons.Rounded.Vibration, Color(0xFFBF5AF2)),
    GESTURES("Gestur", "Swipe & gestur gambar sendiri", Icons.Rounded.Gesture, Color(0xFFFFD60A)),
    WEATHER("Cuaca", "Lokasi untuk widget cuaca", Icons.Rounded.WbSunny, Color(0xFFFF9F0A)),
    APPS("Aplikasi & preset", "Aplikasi tersembunyi, preset tema", Icons.Rounded.Apps, Color(0xFF32D74B)),
    BACKUP("Cadangan & privasi", "Simpan/pulihkan pengaturan, izin, data", Icons.Rounded.Backup, Color(0xFF5AC8FA)),
    ABOUT("Tentang", "Versi, diagnostik, reset", Icons.Rounded.Info, Color(0xFF8E8E93)),
}

@Composable
private fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var pickMusic by remember { mutableStateOf(false) }
    var pickDock by remember { mutableIntStateOf(-2) } // -2 = tidak aktif, -1 = tambah, >=0 = ganti index
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { n -> Page.entries.firstOrNull { it.name == n } }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.update { it.copy(cover = it.cover.copy(calendarEvents = false)) }
    }

    // Isi tiap kategori
    val pageBody: @Composable (Page) -> Unit = { p ->
        when (p) {
            Page.PERMISSIONS -> PermissionSteps(s.coverEnabled) { on -> vm.update { it.copy(coverEnabled = on) } }
            Page.HOME -> HomeStyleControls(s.home) { h -> vm.update { it.copy(home = h) } }
            Page.DRAWER -> {
                Group("Tampilan") { DrawerDisplayControls(s.drawer) { d -> vm.update { it.copy(drawer = d) } } }
                Group("Pencarian") { DrawerSearchControls(s.drawer) { d -> vm.update { it.copy(drawer = d) } } }
                Group("Data pribadi") {
                    DrawerDataControls(
                        s.drawer,
                        onClearUsage = vm::clearAppUsage,
                        onResetCategories = { vm.update { it.copy(drawer = it.drawer.copy(categoryOverrides = emptyMap())) } },
                    )
                }
            }
            Page.LOCK -> {

        ControlCard {
        SwitchRow(
            "Aktifkan layar kunci vinyl", s.coverEnabled, { on -> vm.update { it.copy(coverEnabled = on) } },
            subtitle = "Muncul di atas layar kunci sistem setiap layar dinyalakan",
        )
        TextButton(onClick = { CoverActivity.show(context) }) { Text("Pratinjau layar kunci") }
        }
        CoverStyleControls(
            s.cover,
            onChange = { c -> vm.update { it.copy(cover = c) } },
            onRequestCalendarPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
        )

            }
            Page.MUSIC -> {
                Group("Aplikasi musik") {

        val current = apps.firstOrNull { it.packageName == s.defaultMusicPackage }
        Row(
            Modifier.fillMaxWidth().clickable { pickMusic = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Aplikasi musik default", style = MaterialTheme.typography.bodyLarge)
                Text(
                    current?.label ?: "Otomatis (aplikasi yang sedang memutar)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            current?.let { Image(it.icon, null, Modifier.size(36.dp)) }
        }
        if (s.defaultMusicPackage != null) TextButton(onClick = { vm.update { it.copy(defaultMusicPackage = null) } }) { Text("Kembali ke otomatis") }
                }
                Group("Lirik") { MusicLyricsControls(s.music) { m -> vm.update { it.copy(music = m) } } }
                Group("Pemutaran") { MusicPlaybackControls(s.music) { m -> vm.update { it.copy(music = m) } } }
                Group("Tampilan") { MusicLookControls(s.music) { m -> vm.update { it.copy(music = m) } } }
            }
            Page.TASKBAR -> ControlCard {

        SwitchRow(
            "Aktifkan Taskbar Vinyl", s.dock.enabled,
            { on ->
                if (on && !Settings.canDrawOverlays(context)) SystemActions.openOverlaySettings(context)
                vm.update { it.copy(dock = it.dock.copy(enabled = on)) }
                if (on) TaskbarService.start(context)
            },
            subtitle = "Bar melayang di atas semua aplikasi, pengganti taskbar Samsung",
        )
        if (s.dock.enabled) {
            if (!Settings.canDrawOverlays(context)) TextButton(onClick = { SystemActions.openOverlaySettings(context) }) {
                Text("Izinkan \"Tampilkan di atas aplikasi lain\"")
            }
            Text("Aplikasi favorit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            s.dock.apps.forEachIndexed { i, key ->
                val app = apps.firstOrNull { it.key == key }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    app?.let { Image(it.icon, null, Modifier.size(32.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(app?.label ?: key, Modifier.weight(1f))
                    TextButton(onClick = { pickDock = i }) { Text("Ganti") }
                    TextButton(onClick = { vm.update { st -> st.copy(dock = st.dock.copy(apps = st.dock.apps.filterIndexed { j, _ -> j != i })) } }) { Text("Hapus") }
                }
            }
            if (s.dock.apps.size < MAX_DOCK_APPS) TextButton(onClick = { pickDock = -1 }) { Text("+ Tambah favorit (${s.dock.apps.size}/$MAX_DOCK_APPS)") }
            SwitchRow("Gaya PC", s.dock.pcStyle, { on -> vm.update { it.copy(dock = it.dock.copy(pcStyle = on)) } },
                subtitle = "Jam, tanggal & baterai di taskbar; ikon membesar saat disentuh")
            SwitchRow("Buka aplikasi sebagai jendela", s.dock.windowed, { on -> vm.update { it.copy(dock = it.dock.copy(windowed = on)) } },
                subtitle = "Jendela melayang di tengah layar. Aktifkan dulu \"Jendela bentuk bebas\" di Opsi pengembang; kalau tidak, aplikasi terbuka biasa")
            ChipRow("Posisi taskbar", DockPosition.entries, s.dock.position, {
                when (it) { DockPosition.BOTTOM -> "Bawah"; DockPosition.TOP -> "Atas"; DockPosition.LEFT -> "Kiri"; DockPosition.RIGHT -> "Kanan" }
            }) { pos -> vm.update { st -> st.copy(dock = st.dock.copy(position = pos)) } }
            SliderRow("Jarak dari tepi layar", s.dock.margin.toFloat(), 0f..80f, { v -> vm.update { it.copy(dock = it.dock.copy(margin = v.toInt())) } }, { "${it.toInt()} dp" })
            SwitchRow("Aplikasi terakhir dibuka", s.dock.showRecents, { on ->
                if (on && !TaskbarService.hasUsageAccess(context)) SystemActions.openUsageAccessSettings(context)
                vm.update { it.copy(dock = it.dock.copy(showRecents = on)) }
            }, subtitle = if (TaskbarService.hasUsageAccess(context)) null else "Perlu izin \"Akses penggunaan\" — ketuk untuk membuka")
            SwitchRow("Kartu lagu yang diputar", s.dock.showNowPlaying, { on -> vm.update { it.copy(dock = it.dock.copy(showNowPlaying = on)) } })
            SwitchRow("Sembunyikan saat layar penuh", s.dock.hideInFullscreen, { on -> vm.update { it.copy(dock = it.dock.copy(hideInFullscreen = on)) } },
                subtitle = "Misalnya saat menonton video atau bermain game")
        }

            }
            Page.NAV -> ControlCard {

        SwitchRow(
            "Aktifkan tombol navigasi", s.nav.enabled,
            { on ->
                if (on && !Settings.canDrawOverlays(context)) SystemActions.openOverlaySettings(context)
                if (on && !SystemActions.isAccessibilityEnabled()) SystemActions.openAccessibilitySettings(context)
                vm.update { it.copy(nav = it.nav.copy(enabled = on)) }
                if (on) NavButtonsService.start(context)
            },
            subtitle = "Tombol Kembali, Home & Terbaru yang bisa dipindah dan diatur jaraknya",
        )
        if (s.nav.enabled) {
            if (!SystemActions.isAccessibilityEnabled()) TextButton(onClick = { SystemActions.openAccessibilitySettings(context) }) {
                Text("Nyalakan layanan aksesibilitas Vinyl (agar Kembali & Terbaru berfungsi)")
            }
            Text(
                "Agar tombol bawaan sistem hilang: Pengaturan tablet → Tampilan → Bilah navigasi → Gestur geser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Kiri bawah" to (0f to 1f), "Tengah bawah" to (0.5f to 1f), "Kanan bawah" to (1f to 1f),
                    "Kiri tengah" to (0f to 0.5f), "Kanan tengah" to (1f to 0.5f),
                ).forEach { (label, xy) ->
                    TextButton(onClick = { vm.update { it.copy(nav = it.nav.copy(x = xy.first, y = xy.second, vertical = xy.second == 0.5f)) } }) { Text(label) }
                }
            }
            SliderRow("Posisi mendatar", s.nav.x, 0f..1f, { v -> vm.update { it.copy(nav = it.nav.copy(x = v)) } })
            SliderRow("Posisi tegak", s.nav.y, 0f..1f, { v -> vm.update { it.copy(nav = it.nav.copy(y = v)) } })
            SliderRow("Jarak antar tombol", s.nav.spacing.toFloat(), 0f..160f, { v -> vm.update { it.copy(nav = it.nav.copy(spacing = v.toInt())) } }, { "${it.toInt()} dp" })
            SliderRow("Ukuran tombol", s.nav.size.toFloat(), 48f..88f, { v -> vm.update { it.copy(nav = it.nav.copy(size = v.toInt())) } }, { "${it.toInt()} dp" })
            SliderRow("Transparansi", s.nav.opacity, 0.2f..1f, { v -> vm.update { it.copy(nav = it.nav.copy(opacity = v)) } })
            ChipRow("Urutan", NavOrder.entries, s.nav.order, {
                when (it) { NavOrder.BACK_HOME_RECENTS -> "◁  ○  □"; NavOrder.RECENTS_HOME_BACK -> "□  ○  ◁" }
            }) { o -> vm.update { st -> st.copy(nav = st.nav.copy(order = o)) } }
            SwitchRow("Susun ke bawah (vertikal)", s.nav.vertical, { on -> vm.update { it.copy(nav = it.nav.copy(vertical = on)) } })
        }

            }
            Page.VISUAL -> {
                VisualBlockNote()
                Group("Latar") {
                    VisualEffectControls(s.visual) { v -> vm.update { it.copy(visual = v) } }
                }
                Group("Parallax") {
                    VisualParallaxControls(s.visual) { v -> vm.update { it.copy(visual = v) } }
                }
                Group("Visualizer audio") {
                    VisualizerControls(s.visual) { v -> vm.update { it.copy(visual = v) } }
                }
            }
            Page.FX -> ControlCard {

        SwitchRow("Suara jarum", s.effects.needleSound, { on -> vm.update { it.copy(effects = it.effects.copy(needleSound = on)) } },
            subtitle = "Bunyi \"tuk\" dan desis vinyl saat lagu mulai")
        SwitchRow("Getar", s.effects.haptics, { on -> vm.update { it.copy(effects = it.effects.copy(haptics = on)) } })
        SwitchRow("Efek saat vinyl disentuh", s.effects.interactionFx, { on -> vm.update { it.copy(effects = it.effects.copy(interactionFx = on)) } },
            subtitle = "Klik & getar saat vinyl diputar dengan jari, tonearm digeser, dan ganti lagu")
        SwitchRow("Kresek vinyl terus-menerus", s.effects.crackle, { on -> vm.update { it.copy(effects = it.effects.copy(crackle = on)) } },
            subtitle = "Desis & letupan pelan selama lagu diputar di layar utama")
        if (s.effects.needleSound || s.effects.crackle) SliderRow("Volume efek", s.effects.volume, 0.05f..1f, { v -> vm.update { it.copy(effects = it.effects.copy(volume = v)) } })
        if (s.effects.haptics) SliderRow("Kekuatan getar", s.effects.hapticStrength, 0.3f..1f, { v -> vm.update { it.copy(effects = it.effects.copy(hapticStrength = v)) } })

            }
            Page.GESTURES -> {
                Group("Swipe & ketuk") {

            GestureControls(s.gestures, apps) { g -> vm.update { it.copy(gestures = g) } }

                }
                Group("Gestur buatan sendiri") {

            CustomGesturesSection(s.customGestures, apps) { list -> vm.update { it.copy(customGestures = list) } }

                }
            }
            Page.WEATHER -> ControlCard {

        var city by remember(s.weather) { mutableStateOf(s.weather.cityName) }
        var lat by remember(s.weather) { mutableStateOf(s.weather.latitude.toString()) }
        var lon by remember(s.weather) { mutableStateOf(s.weather.longitude.toString()) }
        OutlinedTextField(city, { city = it }, label = { Text("Nama kota") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(lat, { lat = it }, label = { Text("Lintang") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(lon, { lon = it }, label = { Text("Bujur") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Button(onClick = {
            val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull()
            if (la != null && lo != null) vm.update { it.copy(weather = it.weather.copy(cityName = city, latitude = la, longitude = lo)) }
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Simpan lokasi cuaca") }

            }
            Page.APPS -> {
                Group("Aplikasi tersembunyi") {

            if (s.hiddenApps.isEmpty()) Text("Tidak ada. Sembunyikan aplikasi dengan menekan lama ikonnya di app drawer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.hiddenApps.forEach { key ->
                val app = apps.firstOrNull { it.key == key }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    app?.let { Image(it.icon, null, Modifier.size(32.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(app?.label ?: key, Modifier.weight(1f))
                    TextButton(onClick = { vm.update { it.copy(hiddenApps = it.hiddenApps - key) } }) { Text("Tampilkan") }
                }
            }

                }
                Group("Preset tema") {

            if (s.presets.isEmpty()) Text("Belum ada preset. Simpan gaya dari mode edit home screen (tekan lama area kosong).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.presets.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f))
                    TextButton(onClick = { vm.update { it.copy(home = p.home, cover = p.cover, retro = SettingsMigration.retroFor(p)) } }) { Text("Terapkan") }
                    TextButton(onClick = { vm.update { st -> st.copy(presets = st.presets.filterNot { it.id == p.id }) } }) { Text("Hapus") }
                }
            }

                }
            }
            Page.BACKUP -> {
                Group("Cadangan pengaturan") { BackupActions(vm) }
                Group("Privasi") { PrivacySummary() }
                Group("Izin yang dipakai dan alasannya") { PermissionReasons() }
            }
            Page.ABOUT -> {
                AboutCard()
                Group("Diagnostik pemutaran") {

            val log by MediaRepository.log.collectAsStateWithLifecycle()
            Text(
                "Catatan ini membantu melacak musik yang berhenti sendiri: baris \"Vinyl → …\" berarti perintah dari launcher, baris lain berasal dari aplikasi musik.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
            if (log.isEmpty()) Text("Belum ada kejadian.", style = MaterialTheme.typography.bodyMedium)
            log.takeLast(30).asReversed().forEach { e ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(fmt.format(java.util.Date(e.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    Text(e.text, style = MaterialTheme.typography.bodySmall)
                }
            }

                }
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { confirmReset = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset ke default") }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            // Dua panel ala Pengaturan Samsung (tablet) / System Settings macOS
            Row(Modifier.fillMaxSize()) {
                Sidebar(page ?: Page.PERMISSIONS, onSelect = { pageName = it.name }, onBack = onBack, modifier = Modifier.width(320.dp).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)))
                PagePane(page ?: Page.PERMISSIONS, showBack = false, onBack = {}, modifier = Modifier.weight(1f)) { pageBody(it) }
            }
        } else {
            if (page == null) {
                Sidebar(null, onSelect = { pageName = it.name }, onBack = onBack, modifier = Modifier.fillMaxSize())
            } else {
                BackHandler { pageName = null }
                PagePane(page, showBack = true, onBack = { pageName = null }, modifier = Modifier.fillMaxSize()) { pageBody(it) }
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset semua pengaturan?") },
        text = { Text("Gaya home, layar kunci, gestur, dock, preset, dan aplikasi tersembunyi akan dikembalikan ke default.") },
        confirmButton = { TextButton(onClick = { vm.reset(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } },
    )
    if (pickMusic) AppPickerDialog(apps, "Aplikasi musik default", onDismiss = { pickMusic = false }) { app ->
        vm.update { it.copy(defaultMusicPackage = app.packageName) }; pickMusic = false
    }
    if (pickDock != -2) AppPickerDialog(apps, "Pilih aplikasi favorit", onDismiss = { pickDock = -2 }) { app ->
        val idx = pickDock
        vm.update { st ->
            val list = st.dock.apps.toMutableList()
            if (idx >= 0 && idx < list.size) list[idx] = app.key else if (app.key !in list) list += app.key
            st.copy(dock = st.dock.copy(apps = list.distinct().take(MAX_DOCK_APPS)))
        }
        pickDock = -2
    }
}


/** Kolom kiri: judul besar + daftar kategori dengan ikon berwarna (ala iOS/One UI). */
@Composable
private fun Sidebar(selected: Page?, onSelect: (Page) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Tutup") }
        }
        Text("Pengaturan", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 10.dp, top = 12.dp))
        Text(
            "Vinyl Launcher", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 18.dp),
        )
        Page.entries.forEach { p ->
            val sel = p == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(Corner.m)
                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(p.tint),
                    contentAlignment = Alignment.Center,
                ) { Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (selected == null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Panel kanan: judul kategori besar + isinya, lebar dibatasi agar nyaman dibaca di layar besar. */
@Composable
private fun PagePane(page: Page, showBack: Boolean, onBack: () -> Unit, modifier: Modifier, body: @Composable (Page) -> Unit) {
    // key: tiap kategori punya posisi gulir sendiri
    Box(modifier) { androidx.compose.runtime.key(page) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState(), enabled = true)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            if (showBack) TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pengaturan")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 16.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(page.tint), contentAlignment = Alignment.Center) {
                    Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(page.title, style = MaterialTheme.typography.headlineLarge)
            }
            body(page)
            Spacer(Modifier.height(40.dp))
        }
    } }
}

/** Kartu "Tentang": nama, versi, ringkasan fitur. */
@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }
    ControlCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(Corner.m).background(Color(0xFF111113)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Album, null, tint = Color(0xFFFF9F43), modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Vinyl Launcher", style = MaterialTheme.typography.titleLarge)
                Text("Versi $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Tablet sebagai turntable: layar utama & layar kunci bertema piringan hitam, lirik tersinkron, rak piringan, " +
                "taskbar gaya PC, tombol navigasi & gestur yang bisa diatur sendiri.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Kelompok pengaturan: judul besar + (opsional) kartu membulat ala One UI. */
@Composable
private fun Group(title: String, carded: Boolean = true, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 8.dp, top = 28.dp, bottom = 4.dp),
    )
    if (carded) {
        Spacer(Modifier.height(10.dp))
        ControlCard { content() }
    } else {
        Column { content() }
    }
}
