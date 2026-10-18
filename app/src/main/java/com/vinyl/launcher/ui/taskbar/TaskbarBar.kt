package com.vinyl.launcher.ui.taskbar

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.service.TaskbarState
import com.vinyl.launcher.ui.components.rememberBattery
import com.vinyl.launcher.ui.components.rememberNow
import com.vinyl.launcher.ui.home.HomeActivity
import com.vinyl.launcher.ui.settings.SettingsActivity
import com.vinyl.launcher.util.SystemActions
import kotlinx.coroutines.flow.StateFlow

private val BarShape = RoundedCornerShape(26.dp)
private val BarColor = Color(0xF2151517)

/**
 * Isi Taskbar Vinyl: kapsul kaca gelap berisi Home, semua aplikasi, favorit,
 * aplikasi terakhir, dan kartu kecil lagu yang sedang diputar.
 *
 * Susunan dan ukuran diputuskan oleh [TaskbarPlanner] (lihat penjelasan di sana): jam dan tombol
 * ciutkan selalu terlihat; bila layar sempit, aplikasi terakhir/teks lagu/tanggal dibuang dan
 * favorit bisa digulir. Bisa diciutkan menjadi pegangan kecil agar tidak menutupi konten aplikasi.
 */
@Composable
fun TaskbarBar(modelFlow: StateFlow<TaskbarModel>) {
    val model by modelFlow.collectAsStateWithLifecycle()
    val plan = model.plan
    val vertical = plan.vertical

    // Jendela dibuat sedikit lebih besar dari kapsul (pembulatan piksel); kapsul dipusatkan di dalamnya.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (plan.collapsed) CollapsedHandle(vertical) else Bar(model)
    }
}

/** Pegangan kecil: ketuk untuk membuka kembali. Seluruh jendela (≥ 48dp di sisi pendek) adalah area sentuh. */
@Composable
private fun CollapsedHandle(vertical: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = "Buka taskbar", role = Role.Button) { TaskbarState.setCollapsed(false) }
            .semantics { contentDescription = "Taskbar diciutkan" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = if (vertical) 5.dp else 64.dp, height = if (vertical) 64.dp else 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun Bar(model: TaskbarModel) {
    val context = LocalContext.current
    val plan = model.plan
    val vertical = plan.vertical

    val content: @Composable () -> Unit = {
        BarIcon(Icons.Rounded.Home, "Layar utama") { TaskbarState.setAppPanel(false); goHome(context, openDrawer = false) }
        BarIcon(Icons.Rounded.Apps, "Semua aplikasi") { TaskbarState.toggleAppPanel() }
        if (plan.showMiddle) MiddleGroup(model)
        if (plan.chip != ChipMode.NONE) {
            Divider(vertical)
            NowPlayingChip(context, plan.chip, vertical, plan.fontScale)
        }
        if (plan.showTray) {
            Divider(vertical)
            SystemTray(plan)
        }
        Divider(vertical)
        BarIcon(Icons.Rounded.ExpandMore, "Ciutkan taskbar", iconSize = 22) { TaskbarState.setCollapsed(true) }
    }

    // Sumbu melintang dikunci ke rencana (tebal bar konsisten); sumbu searah bar mengikuti isi.
    val capsule = Modifier
        .then(if (vertical) Modifier.width(plan.barCrossDp.dp) else Modifier.height(plan.barCrossDp.dp))
        .clip(BarShape)
        .background(BarColor)
        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))), BarShape)
        .padding(horizontal = 10.dp, vertical = 8.dp)
    if (vertical) {
        Column(capsule, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TaskbarMetrics.GAP.dp)) { content() }
    } else {
        Row(capsule, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskbarMetrics.GAP.dp)) { content() }
    }
}

/**
 * Favorit + aplikasi terakhir. Bila layar sempit, grup ini diberi viewport tetap dan bisa digulir
 * sehingga elemen di kanannya (kartu lagu, jam, tombol ciutkan) tidak pernah terdorong keluar.
 */
@Composable
private fun MiddleGroup(model: TaskbarModel) {
    val context = LocalContext.current
    val plan = model.plan
    val vertical = plan.vertical
    val scroll = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    val sizeMod = when {
        !plan.middleScrolls -> Modifier
        vertical -> Modifier.height(plan.middleMainDp.dp).verticalScroll(scroll)
        else -> Modifier.width(plan.middleMainDp.dp).horizontalScroll(scroll)
    }
    val inner: @Composable () -> Unit = {
        if (model.favorites.isNotEmpty()) {
            Divider(vertical)
            model.favorites.forEach { app ->
                AppButton(app, dim = false, onHold = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    openTaskbarSettings(context)
                }) { AppsRepository.get(context).open(app, model.windowed) }
            }
        }
        if (model.recents.isNotEmpty()) {
            Divider(vertical)
            model.recents.forEach { app ->
                AppButton(app, dim = true, onHold = {}) { AppsRepository.get(context).open(app, model.windowed) }
            }
        }
        if (plan.showSettingsShortcut) {
            BarIcon(Icons.Rounded.Settings, "Tambah favorit di Pengaturan") { openTaskbarSettings(context) }
        }
    }
    if (vertical) {
        Column(sizeMod, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TaskbarMetrics.GAP.dp)) { inner() }
    } else {
        Row(sizeMod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskbarMetrics.GAP.dp)) { inner() }
    }
}

/** Area sistem ala PC: baterai, jam & tanggal. Ketuk untuk membuka panel notifikasi. Ukurannya tetap sesuai rencana. */
@Composable
private fun SystemTray(plan: TaskbarPlan) {
    val context = LocalContext.current
    val vertical = plan.vertical
    val now by rememberNow()
    val battery = rememberBattery()
    val time = remember(now.hour, now.minute) { "%02d:%02d".format(now.hour, now.minute) }
    val date = remember(now.dayOfYear) {
        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.forLanguageTag("id-ID")).format(now)
    }
    // Skala font di dalam bar dibatasi (TaskbarMetrics.MAX_FONT_SCALE) agar ukuran yang direncanakan selalu cukup
    val density = LocalDensity.current
    val capped = Density(density.density, fontScale = density.fontScale.coerceAtMost(TaskbarMetrics.MAX_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        Column(
            Modifier
                .then(if (vertical) Modifier.height(plan.trayMainDp.dp) else Modifier.width(plan.trayMainDp.dp).heightIn(min = TaskbarMetrics.TRAY_H.dp))
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = "Buka panel notifikasi", role = Role.Button) { SystemActions.expandNotifications(context) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$time, baterai ${battery.level} persen"
                },
            horizontalAlignment = if (vertical) Alignment.CenterHorizontally else Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(time, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Text(
                if (vertical || !plan.showDate) "${battery.level}%" else "$date  ·  ${battery.level}%",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun Divider(vertical: Boolean) {
    Box(
        Modifier
            .padding(if (vertical) PaddingValues(vertical = 4.dp) else PaddingValues(horizontal = 4.dp))
            .size(width = if (vertical) 28.dp else 1.dp, height = if (vertical) 1.dp else 28.dp)
            .background(Color.White.copy(alpha = 0.14f)),
    )
}

@Composable
private fun BarIcon(icon: ImageVector, desc: String, iconSize: Int = 26, onClick: () -> Unit) {
    Box(
        Modifier
            .size(TaskbarMetrics.ICON.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = desc, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(iconSize.dp)) }
}

@Composable
private fun AppButton(app: AppInfo, dim: Boolean, onHold: () -> Unit, onTap: () -> Unit) {
    // Ikon membesar saat disentuh (ala dock macOS)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 1.3f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "iconScale")
    Box(
        Modifier
            .size(TaskbarMetrics.ICON.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onTap() },
                    onLongPress = { onHold() },
                )
            }
            // Semantik eksplisit supaya TalkBack bisa mengaktifkan tombol (pointerInput saja tidak punya aksi klik)
            .semantics(mergeDescendants = true) {
                contentDescription = app.label
                role = Role.Button
                onClick(label = "Buka ${app.label}") { onTap(); true }
                onLongClick(label = "Pengaturan taskbar") { onHold(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(app.icon, null, Modifier.size(40.dp).alpha(if (dim) 0.78f else 1f))
    }
}

/** Kartu kecil lagu: cover, judul, tombol putar/jeda. Ukuran mengikuti [mode] dari rencana. */
@Composable
private fun NowPlayingChip(context: Context, mode: ChipMode, vertical: Boolean, fontScale: Float) {
    val np by MediaRepository.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val capped = Density(density.density, fontScale = density.fontScale.coerceAtMost(fontScale.coerceAtLeast(1f)))
    val label = "Sedang diputar ${np.title} oleh ${np.artist}. Buka aplikasi musik"
    val chipMod = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = 0.07f))
        .clickable(onClickLabel = "Buka aplikasi musik", role = Role.Button) { MediaRepository.openMusicApp(context) }
        .semantics { contentDescription = label }

    val cover: @Composable () -> Unit = {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            val art = np.art
            if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp))
            else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
    val playButton: @Composable () -> Unit = {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = if (np.isPlaying) "Jeda" else "Putar", role = Role.Button) { MediaRepository.togglePlayPause("Taskbar") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.isPlaying) "Jeda" else "Putar", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }

    CompositionLocalProvider(LocalDensity provides capped) {
        if (vertical) {
            Column(chipMod.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                cover()
                Spacer(Modifier.height(2.dp))
                playButton()
            }
        } else {
            Row(
                chipMod.padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cover()
                if (mode == ChipMode.FULL) {
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.width(TaskbarMetrics.NP_TEXT.dp)) {
                        Text(np.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(np.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                playButton()
            }
        }
    }
}

private fun goHome(context: Context, openDrawer: Boolean) {
    val intent = if (openDrawer) {
        Intent(context, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_OPEN_DRAWER, true)
    } else {
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

private fun openTaskbarSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
