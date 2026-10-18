package com.vinyl.launcher.ui.console

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vinyl.launcher.advanced.AdvancedAccess
import com.vinyl.launcher.audio.AdvancedReadings
import com.vinyl.launcher.audio.AudioConsoleRuntime
import com.vinyl.launcher.audio.BluetoothBattery
import com.vinyl.launcher.audio.AudioMonitor
import com.vinyl.launcher.audio.ChannelTest
import com.vinyl.launcher.audio.EqEngine
import com.vinyl.launcher.audio.SystemAudioShortcuts
import com.vinyl.launcher.data.AudioConsoleSettings
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.player.HiFi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Menghubungkan [ConsoleScreen] ke sumber datanya. Pemantau perangkat audio hanya aktif selama halaman ini tampil
 * (dan beranda dalam keadaan RESUMED); nada tes berhenti saat halaman ditinggalkan.
 *
 * Perubahan EQ langsung dipratinjau ke [EqEngine] lalu disimpan sesaat kemudian, agar menggeser fader tidak menulis
 * ke penyimpanan puluhan kali per detik.
 */
@Composable
fun ConsoleRoute(
    audio: AudioConsoleSettings,
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
    /** Paket aplikasi musik yang sesinya sedang tampil (untuk memilih pemutar yang benar di laporan sistem). */
    musicPackage: String?,
    onSave: (AudioConsoleSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vu: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snapshot by AudioMonitor.state.collectAsStateWithLifecycle()
    val sessions by EqEngine.sessions.collectAsStateWithLifecycle()
    val log by AudioConsoleRuntime.log.collectAsStateWithLifecycle()
    val tone by ChannelTest.playing.collectAsStateWithLifecycle()
    val access by AdvancedAccess.state.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(AdvancedReadings()) }
    var copied by remember { mutableStateOf(false) }
    val btLevels by BluetoothBattery.levels.collectAsStateWithLifecycle()
    var btPermission by remember { mutableStateOf(BluetoothBattery.granted(context)) }
    val btRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        btPermission = ok
        if (ok) { BluetoothBattery.refresh(context); AudioMonitor.refresh() }
    }

    var shortcuts by remember { mutableStateOf(emptyList<SystemAudioShortcuts.Shortcut>()) }
    LaunchedEffect(Unit) { shortcuts = withContext(Dispatchers.IO) { SystemAudioShortcuts.available(context) } }

    LifecycleResumeEffect(Unit) {
        AudioMonitor.acquire(context)
        AdvancedAccess.refresh() // pengguna mungkin baru saja menjalankan perintah ADB
        btPermission = BluetoothBattery.granted(context) // bisa dicabut lewat Pengaturan sistem
        onPauseOrDispose {
            AudioMonitor.release()
            ChannelTest.stop()
        }
    }
    // Status spasial & properti keluaran tidak punya callback perubahan: dibaca ulang berkala selama halaman tampil
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(3_000)
                AudioMonitor.refresh()
            }
        }
    }

    // Laporan diagnostik dibaca hanya selama halaman ini tampil dan izin DUMP ada (di thread latar, lihat DumpReader)
    val activeDevice = snapshot.active.device
    LaunchedEffect(lifecycle, access.dumpGranted, activeDevice?.profileKey, musicPackage) {
        if (!access.dumpGranted) { advanced = AdvancedReadings(); return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                advanced = AdvancedAccess.readings(context, activeDevice, musicPackage)
                delay(5_000)
            }
        }
    }
    LaunchedEffect(copied) { if (copied) { delay(2_000); copied = false } }

    var draft by remember { mutableStateOf<AudioConsoleSettings?>(null) }
    LaunchedEffect(draft) {
        val d = draft ?: return@LaunchedEffect
        EqEngine.update(d)
        delay(350)
        onSave(d)
        delay(1_000)
        if (draft == d) draft = null
    }
    LaunchedEffect(audio) { if (audio == draft) draft = null }

    // Pemutar bawaan: perubahan langsung terdengar (rantai suara dibangun ulang), disimpan sesaat kemudian
    var playerDraft by remember { mutableStateOf<PlayerSettings?>(null) }
    LaunchedEffect(playerDraft) {
        val d = playerDraft ?: return@LaunchedEffect
        HiFi.apply(d)
        delay(350)
        onSavePlayer(d)
        delay(1_000)
        if (playerDraft == d) playerDraft = null
    }
    LaunchedEffect(player) { if (player == playerDraft) playerDraft = null }
    val path by HiFi.path.collectAsStateWithLifecycle()

    val effectsPanel = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    ConsoleScreen(
        ui = ConsoleUi(
            snapshot = snapshot,
            advanced = advanced,
            audio = draft ?: audio,
            sessions = sessions,
            log = log,
            shortcuts = shortcuts,
            tone = tone,
            micGranted = mic,
            access = access,
            isSamsung = SystemAudioShortcuts.isSamsung,
            copied = copied,
            btPermission = btPermission,
            btLevels = btLevels,
            player = playerDraft ?: player,
            path = path,
        ),
        actions = ConsoleActions(
            onBack = onBack,
            onAudio = { f -> draft = f(draft ?: audio) },
            onTone = ChannelTest::play,
            onStopTone = ChannelTest::stop,
            onShortcut = { s ->
                runCatching { if (s.forResult) effectsPanel.launch(s.intent) else context.startActivity(s.intent) }
            },
            onCopyCommand = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Perintah ADB", AdvancedAccess.ADB_COMMAND))
                copied = true
            },
            onCheckAccess = AdvancedAccess::refresh,
            onGrantShizuku = AdvancedAccess::grantViaShizuku,
            onRequestBluetooth = { btRequest.launch(BluetoothBattery.permission) },
            onPlayer = { f -> playerDraft = f(playerDraft ?: player) },
        ),
        modifier = modifier,
        vu = vu,
    )
}
