package com.vinyl.launcher.system

import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId

/**
 * Mengumpulkan status perangkat untuk OSD beranda, hanya selama beranda tampil ([acquire]/[release] berpasangan,
 * dihitung referensinya). Semua lewat API publik:
 *  - baterai: siaran lengket ACTION_BATTERY_CHANGED, PowerManager (tanpa izin)
 *  - Wi-Fi/seluler: ConnectivityManager.registerDefaultNetworkCallback (izin biasa ACCESS_NETWORK_STATE); RSSI Wi-Fi dari
 *    NetworkCapabilities (tanpa lokasi, jadi nama jaringan tidak pernah dibaca)
 *  - sinyal seluler: TelephonyManager.getSignalStrength() (tanpa izin), dibaca ulang tiap 15 detik
 *  - Bluetooth: BluetoothAdapter.isEnabled (tanpa izin di Android 12+, izin biasa BLUETOOTH di Android 10–11) dan
 *    daftar keluaran audio Bluetooth dari AudioManager (tanpa izin)
 *  - VPN: transport VPN pada jaringan bawaan yang sama (tanpa izin tambahan)
 *  - mode dering, Jangan Ganggu, alarm berikutnya, mode pesawat: API publik tanpa izin
 *  - notifikasi: hitungan dari [NotificationIcons] (diisi layanan akses notifikasi yang sudah ada)
 * Tidak ada yang disimpan ke penyimpanan.
 */
object DeviceStatusMonitor {
    private const val TAG = "VinylStatus"
    private const val CELL_POLL_MS = 15_000L

    private val _state = MutableStateFlow(DeviceStatus())
    val state: StateFlow<DeviceStatus> = _state.asStateFlow()

    private var users = 0
    private val main = Handler(Looper.getMainLooper())
    /**
     * Langkah pembatalan untuk setiap pendaftaran (penerima siaran, callback jaringan & audio, polling sinyal). Hanya
     * konteks aplikasi yang ditangkap (hidup selama proses), bukan Activity.
     */
    private val undo = mutableListOf<() -> Unit>()

    fun acquire(context: Context) {
        users++
        if (users > 1) return
        val c = context.applicationContext
        runCatching { start(c) }.onFailure { Log.w(TAG, "Status perangkat tidak bisa dipantau: ${it.javaClass.simpleName}") }
    }

    fun release() {
        if (users == 0) return
        users--
        if (users > 0) return
        undo.forEach { runCatching { it() } }
        undo.clear()
    }

    private fun start(c: Context) {
        // ----- Baterai (siaran lengket: nilai pertama langsung tersedia)
        register(c, IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }) { i ->
            if (i.action == Intent.ACTION_BATTERY_CHANGED) readBattery(i)
            readPowerSave(c)
        }?.let { readBattery(it) }
        readPowerSave(c)

        // ----- Mode dering, Jangan Ganggu, alarm berikutnya, mode pesawat (semua dikirim system_server)
        register(
            c,
            IntentFilter().apply {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            },
        ) { readQuiet(c) }
        readQuiet(c)

        // ----- Bluetooth: siaran dari tumpukan Bluetooth (bukan system_server), jadi penerimanya diekspor. Isinya tidak
        // dipercaya: siaran hanya memicu pembacaan ulang dari BluetoothAdapter.
        register(c, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), exported = true) { readBluetooth(c) }
        val am = c.getSystemService(AudioManager::class.java)
        if (am != null) {
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = readBluetooth(c)
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = readBluetooth(c)
            }
            am.registerAudioDeviceCallback(cb, main)
            undo += { am.unregisterAudioDeviceCallback(cb) }
        }
        readBluetooth(c)

        // ----- Jaringan
        val cm = c.getSystemService(ConnectivityManager::class.java)
        if (cm != null) {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = readNetwork(caps)
                override fun onLost(network: Network) = readNetwork(null)
            }
            cm.registerDefaultNetworkCallback(cb, main)
            undo += { cm.unregisterNetworkCallback(cb) }
            readNetwork(cm.getNetworkCapabilities(cm.activeNetwork))
        }
        val hasCell = c.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        _state.update { it.copy(hasCellular = hasCell) }
        val tm = c.getSystemService(TelephonyManager::class.java)
        if (hasCell && tm != null) {
            val poll = object : Runnable {
                override fun run() {
                    readCellular(tm)
                    main.postDelayed(this, CELL_POLL_MS)
                }
            }
            main.post(poll)
            undo += { main.removeCallbacks(poll) }
        }
    }

    private fun register(c: Context, filter: IntentFilter, exported: Boolean = false, onReceive: (Intent) -> Unit): Intent? {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onReceive(intent)
        }
        val sticky = ContextCompat.registerReceiver(
            c, r, filter, if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        undo += { c.unregisterReceiver(r) }
        return sticky
    }

    private fun readBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        _state.update { it.copy(batteryLevel = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else -1, charging = plugged) }
    }

    private fun readPowerSave(c: Context) {
        val ps = c.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        _state.update { it.copy(powerSave = ps) }
    }

    private fun readQuiet(c: Context) {
        val am = c.getSystemService(AudioManager::class.java)
        val ringer = when (am?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> Ringer.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> Ringer.VIBRATE
            else -> Ringer.NORMAL
        }
        val nm = c.getSystemService(NotificationManager::class.java)
        val filter = runCatching { nm?.currentInterruptionFilter }.getOrNull()
        val dnd = filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        val alarm = runCatching { c.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
        val airplane = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        _state.update { it.copy(ringer = ringer, doNotDisturb = dnd, nextAlarm = alarm, airplane = airplane) }
    }

    private fun readBluetooth(c: Context) {
        val adapter = runCatching { c.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        val enabled: Boolean? = try {
            adapter?.isEnabled
        } catch (e: SecurityException) {
            null
        }
        val am = c.getSystemService(AudioManager::class.java)
        val btOut = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
        val bt = when {
            btOut != null -> Bluetooth.CONNECTED
            enabled == true -> Bluetooth.ON
            enabled == false -> Bluetooth.OFF
            else -> Bluetooth.UNKNOWN
        }
        _state.update {
            it.copy(
                bluetooth = bt,
                bluetoothDevice = btOut?.productName?.toString()?.takeIf { n -> n.isNotBlank() },
                bluetoothAddress = btOut?.address?.takeIf { a -> a.isNotBlank() && a != "00:00:00:00:00:00" },
            )
        }
    }

    private fun readNetwork(caps: NetworkCapabilities?) {
        val wifiTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        // RSSI dari info transport (Android 12+) atau kekuatan sinyal jaringan; keduanya tanpa izin lokasi
        val rssi: Int? = if (!wifiTransport) null else {
            val fromInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (caps?.transportInfo as? WifiInfo)?.rssi else null
            fromInfo ?: caps?.signalStrength?.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        }
        // Menyala-tapi-tidak-tersambung butuh izin tambahan (ACCESS_WIFI_STATE); OSD cukup menampilkan Wi-Fi yang
        // tersambung, seperti status bar
        val wifi = if (wifiTransport) Wifi.CONNECTED else Wifi.UNKNOWN
        // VPN: jaringan bawaan bertransport VPN (ikon kunci di status bar); tanpa izin tambahan
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        _state.update { it.copy(wifi = wifi, wifiLevel = StatusText.wifiLevel(rssi), mobileData = cellTransport, vpn = vpn) }
    }

    private fun readCellular(tm: TelephonyManager) {
        val absent = runCatching { tm.simState == TelephonyManager.SIM_STATE_ABSENT }.getOrDefault(false)
        val level = try {
            tm.signalStrength?.level ?: -1
        } catch (e: UnsupportedOperationException) {
            -1
        } catch (e: SecurityException) {
            -1
        }
        _state.update { it.copy(simAbsent = absent, cellLevel = if (absent) -1 else level.coerceIn(-1, 4)) }
    }

    /** Dipanggil [NotificationIcons] saat hitungan berubah. */
    internal fun onNotificationCount(count: Int) {
        _state.update { it.copy(notifications = count) }
    }
}
