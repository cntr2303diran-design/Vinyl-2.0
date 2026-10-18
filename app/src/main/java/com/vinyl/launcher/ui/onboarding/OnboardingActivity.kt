package com.vinyl.launcher.ui.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import com.vinyl.launcher.data.CalendarRepository
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.ui.components.VinylDisc
import com.vinyl.launcher.ui.components.VinylLabel
import com.vinyl.launcher.ui.home.HomeActivity
import com.vinyl.launcher.ui.settings.SettingsActivity
import com.vinyl.launcher.ui.theme.VinylTheme
import com.vinyl.launcher.ui.theme.WarmAccent
import com.vinyl.launcher.util.SystemActions
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.vinyl.launcher.data.VinylStyle

/**
 * Onboarding: launcher default, akses notifikasi, kalender (opsional), layar kunci vinyl.
 * Jika onboarding sudah selesai, activity ini langsung meneruskan ke Pengaturan
 * (karena ia juga menjadi ikon "Vinyl Launcher" di daftar aplikasi).
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val repo = SettingsRepository.get(this@OnboardingActivity)
            if (repo.current().onboardingDone && savedInstanceState == null) {
                startActivity(Intent(this@OnboardingActivity, SettingsActivity::class.java))
                finish()
                return@launch
            }
            setContent {
                VinylTheme {
                    OnboardingScreen(onFinish = {
                        lifecycleScope.launch {
                            repo.update { it.copy(onboardingDone = true) }
                            startActivity(
                                Intent(this@OnboardingActivity, HomeActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            )
                            finish()
                        }
                    })
                }
            }
        }
    }
}

// ===== Status izin (dipakai juga di halaman Pengaturan) =====

fun isDefaultLauncher(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Kartu satu langkah izin dengan status & tombol aksi. */
@Composable
fun PermissionStep(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    actionText: String,
    optional: Boolean = false,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(if (done) WarmAccent else Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color.Black, modifier = Modifier.size(22.dp))
            else Text("$number", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title + if (optional) "  (opsional)" else "", style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (done) Text("Selesai", color = WarmAccent)
        else FilledTonalButton(onClick = onAction) { Text(actionText) }
    }
}

/** Daftar langkah izin; status diperbarui setiap kali layar kembali aktif. */
@Composable
fun PermissionSteps(coverEnabled: Boolean, onCoverEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Membaca `refresh` memaksa status dihitung ulang
    val isHome = remember(refresh) { isDefaultLauncher(context) }
    val hasNotifAccess = remember(refresh) { MediaRepository.hasNotificationAccess(context) }
    val hasCalendar = remember(refresh) { CalendarRepository.hasPermission(context) }
    val overlay = remember(refresh) { canDrawOverlays(context) }
    val postNotif = remember(refresh) { canPostNotifications(context) }
    val a11y = remember(refresh) { SystemActions.isAccessibilityEnabled() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionStep(1, "Jadikan launcher default", "Vinyl Launcher tampil saat tombol Home ditekan.", isHome, "Atur") {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else SystemActions.openHomeSettings(context)
        }
        PermissionStep(2, "Akses notifikasi", "Diperlukan untuk membaca & mengontrol lagu dari Spotify, Apple Music, YouTube Music, dll.", hasNotifAccess, "Izinkan") {
            SystemActions.openNotificationAccessSettings(context)
        }
        PermissionStep(3, "Kalender", "Menampilkan titik acara pada kalender layar kunci.", hasCalendar, "Izinkan", optional = true) {
            calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
        PermissionStep(
            4, "Layar kunci vinyl",
            "Tampilkan di atas aplikasi lain agar layar kunci vinyl muncul setiap layar dinyalakan.",
            coverEnabled && overlay && postNotif,
            if (!overlay) "Izinkan tampilan" else if (!postNotif) "Izinkan notifikasi" else "Aktifkan",
            optional = true,
        ) {
            when {
                !overlay -> SystemActions.openOverlaySettings(context)
                !postNotif && Build.VERSION.SDK_INT >= 33 -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> onCoverEnabled(true)
            }
        }
        PermissionStep(5, "Aksi gestur", "Layanan aksesibilitas untuk gestur kunci layar & buka notifikasi.", a11y, "Buka", optional = true) {
            SystemActions.openAccessibilitySettings(context)
        }
    }
}

@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository.get(context) }
    var coverEnabled by remember { mutableIntStateOf(0) } // 0 = belum, 1 = aktif

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VinylDisc(
                modifier = Modifier.size(160.dp),
                rotation = 0f,
                style = VinylStyle.CLASSIC,
                labelColor = WarmAccent,
                discTint = WarmAccent,
                label = VinylLabel.Text("Vinyl Launcher", "tablet sebagai turntable"),
            )
            Spacer(Modifier.height(20.dp))
            Text("Selamat datang di Vinyl Launcher", fontSize = 28.sp, fontWeight = FontWeight.Light)
            Text(
                "Beberapa langkah singkat agar turntable Anda siap diputar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
            )
            PermissionSteps(coverEnabled == 1) { on ->
                coverEnabled = if (on) 1 else 0
                scope.launch { repo.update { it.copy(coverEnabled = on) } }
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Mulai") }
        }
    }
}
