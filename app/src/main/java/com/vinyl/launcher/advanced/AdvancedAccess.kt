package com.vinyl.launcher.advanced

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.vinyl.launcher.audio.AdvancedReadings
import com.vinyl.launcher.audio.AdvancedReport
import com.vinyl.launcher.audio.DeviceKind
import com.vinyl.launcher.audio.DumpParsers
import com.vinyl.launcher.audio.DumpReader
import com.vinyl.launcher.audio.OutputDevice
import com.vinyl.launcher.audio.Reading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Akses lanjutan: izin DUMP (diberikan pengguna lewat ADB, atau lewat Shizuku dengan persetujuannya) untuk membaca
 * laporan diagnostik sistem. Tanpa izin ini semua fitur dasar tetap berjalan dan nilai terkait tampil "Tidak tersedia".
 *
 * Shizuku opsional: bila terpasang dan berjalan, launcher bisa memberi izin DUMP ke dirinya sendiri lewat [ShellService]
 * (satu perintah tetap). Launcher tidak memakai Shizuku untuk hal lain.
 */
object AdvancedAccess {
    private const val TAG = "VinylAdvanced"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val ADB_COMMAND = "adb shell pm grant ${ShellService.APP_ID} ${ShellService.DUMP}"
    const val ADB_REVOKE = "adb shell pm revoke ${ShellService.APP_ID} ${ShellService.DUMP}"
    private const val REQUEST_CODE = 0x5D11
    private const val BIND_TIMEOUT_MS = 15_000L

    enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, UNSUPPORTED, NEEDS_PERMISSION, READY }

    data class State(
        val dumpGranted: Boolean = false,
        val shizuku: ShizukuState = ShizukuState.NOT_INSTALLED,
        val working: Boolean = false,
        /** Hasil percobaan terakhir memberi izin lewat Shizuku (null = belum pernah dicoba sejak launcher dibuka). */
        val lastGrant: Reading? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var app: Context
    private var started = false
    private var pendingGrant = false
    private var timeoutJob: Job? = null

    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val deadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code != REQUEST_CODE) return@OnRequestPermissionResultListener
        if (result == PackageManager.PERMISSION_GRANTED && pendingGrant) bindAndGrant()
        else {
            pendingGrant = false
            finish(Reading.unavailable("Izin Shizuku tidak diberikan; tidak ada yang dijalankan"))
        }
    }

    /** Dipanggil sekali dari Application.onCreate. */
    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    /** Baca ulang status izin DUMP dan Shizuku (mis. setelah pengguna menjalankan perintah ADB). */
    fun refresh() {
        if (!started) return
        _state.update { it.copy(dumpGranted = DumpReader.granted(app), shizuku = shizukuState()) }
    }

    private fun shizukuState(): ShizukuState {
        val installed = runCatching { app.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        return when {
            !alive && !installed -> ShizukuState.NOT_INSTALLED
            !alive -> ShizukuState.NOT_RUNNING
            runCatching { Shizuku.isPreV11() }.getOrDefault(true) -> ShizukuState.UNSUPPORTED
            runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) != PackageManager.PERMISSION_GRANTED ->
                ShizukuState.NEEDS_PERMISSION
            else -> ShizukuState.READY
        }
    }

    /**
     * Beri izin DUMP lewat Shizuku. Dipanggil hanya setelah pengguna menyetujui dialog yang menampilkan perintahnya.
     * Bila aplikasi belum diizinkan Shizuku, dialog izin Shizuku muncul lebih dulu.
     */
    fun grantViaShizuku() {
        if (!started || _state.value.working) return
        refresh()
        when (_state.value.shizuku) {
            ShizukuState.NOT_INSTALLED -> finish(Reading.unavailable("Shizuku tidak terpasang"))
            ShizukuState.NOT_RUNNING -> finish(Reading.unavailable("Shizuku belum berjalan; nyalakan dulu di aplikasinya"))
            ShizukuState.UNSUPPORTED -> finish(Reading.unavailable("Versi Shizuku terlalu lama; perbarui Shizuku"))
            ShizukuState.NEEDS_PERMISSION -> {
                pendingGrant = true
                _state.update { it.copy(working = true) }
                runCatching { Shizuku.requestPermission(REQUEST_CODE) }
                    .onFailure { pendingGrant = false; finish(Reading.unavailable("Shizuku menolak permintaan izin")) }
            }
            ShizukuState.READY -> bindAndGrant()
        }
    }

    private fun args(): Shizuku.UserServiceArgs {
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt() }.getOrDefault(1)
        return Shizuku.UserServiceArgs(ComponentName(app.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .tag("vinyl-shell")
            .debuggable(debuggable)
            .version(version)
    }

    private fun bindAndGrant() {
        pendingGrant = false
        _state.update { it.copy(working = true) }
        val args = args()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                timeoutJob?.cancel()
                val conn = this
                scope.launch(Dispatchers.IO) {
                    val code = runCatching { IShellService.Stub.asInterface(binder).grantDump() }.getOrDefault(-1)
                    runCatching { Shizuku.unbindUserService(args, conn, true) }
                    launch(Dispatchers.Main) {
                        refresh()
                        finish(
                            when {
                                _state.value.dumpGranted -> Reading.reported("Izin DUMP diberikan", "Lewat Shizuku")
                                code == 0 -> Reading.unavailable("Perintah selesai tetapi sistem belum melaporkan izinnya; coba Periksa lagi")
                                else -> Reading.unavailable("Perintah gagal (kode $code)")
                            },
                        )
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        runCatching { Shizuku.bindUserService(args, connection) }.onFailure {
            Log.w(TAG, "Layanan Shizuku tidak bisa dimulai: ${it.javaClass.simpleName}")
            finish(Reading.unavailable("Layanan Shizuku tidak bisa dimulai"))
            return
        }
        timeoutJob = scope.launch {
            delay(BIND_TIMEOUT_MS)
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            finish(Reading.unavailable("Shizuku tidak merespons dalam ${BIND_TIMEOUT_MS / 1000} detik"))
        }
    }

    private fun finish(result: Reading) {
        _state.update { it.copy(working = false, lastGrant = result) }
    }

    /** Status Akses lanjutan sebagai satu nilai untuk panel console. */
    fun statusReading(s: State): Reading = if (s.dumpGranted) Reading.reported("Aktif (izin DUMP)", "Diberikan lewat ADB atau Shizuku")
    else Reading.unavailable("Belum diberikan; lihat panduan di panel Akses lanjutan")

    // ------------------------------------------------------------------ pembacaan

    /** Nilai Quality/Devices dari laporan diagnostik. Tanpa izin DUMP: semuanya "butuh Akses lanjutan". */
    suspend fun readings(context: Context, active: OutputDevice?, musicPackage: String?): AdvancedReadings {
        if (!DumpReader.granted(context)) return AdvancedReadings()
        val audio = DumpReader.read(context, "audio")
        val bt = if (active?.kind == DeviceKind.BLUETOOTH) DumpReader.read(context, "bluetooth_manager") else null
        val flinger = DumpReader.read(context, "media.audio_flinger")
        val uid = musicPackage?.let { p -> runCatching { context.packageManager.getPackageUid(p, 0) }.getOrNull() }
        return AdvancedReport.build(
            active = active,
            codec = bt?.let(DumpParsers::activeA2dpCodec),
            thread = flinger?.let { DumpParsers.activeThread(DumpParsers.outputThreads(it), active?.kind) },
            players = audio?.let(DumpParsers::players).orEmpty(),
            musicUid = uid,
        )
    }

    /** Sesi musik yang sedang ada (untuk EQ lapis kedua): pasangan nomor sesi → nama paket. */
    suspend fun detectedSessions(context: Context): List<Pair<Int, String>>? {
        val audio = DumpReader.read(context, "audio") ?: return null
        val pm = context.packageManager
        return DumpParsers.musicSessions(DumpParsers.players(audio))
            .filter { it.uid != android.os.Process.myUid() }
            .map { p -> p.sessionId to (runCatching { pm.getNameForUid(p.uid) }.getOrNull() ?: "uid ${p.uid}") }
    }
}
