package com.vinyl.launcher.audio

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Level baterai perangkat Bluetooth dari siaran sistem `android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`.
 *
 * JALUR TIDAK RESMI: konstanta siaran ini bertanda @SystemApi (bukan bagian SDK publik), jadi nilainya ditulis sebagai teks
 * (diambil dari kode sumber AOSP, BluetoothDevice.java) dan bisa berubah/hilang di versi Android atau pabrikan tertentu.
 * Butuh izin BLUETOOTH_CONNECT (Android 12+) yang diminta saat pengguna menekan tombolnya di Audio Console; Android 10–11
 * memakai izin BLUETOOTH biasa. Siaran dikirim saat level pertama kali terbaca (biasanya saat perangkat tersambung) atau
 * berubah; launcher harus sedang berjalan untuk menerimanya. Nilai divalidasi ([BatteryReports.accept]) dan hanya
 * dicocokkan ke perangkat dengan alamat yang sama.
 *
 * Level terakhir tiap perangkat diingat di penyimpanan internal (kunci = sidik SHA-256 alamat, bukan alamatnya) agar
 * console bisa menampilkan perkiraan "≈ 70%, 25 menit lalu" setelah launcher dimulai ulang, sebelum laporan baru datang.
 */
object BluetoothBattery {
    const val ACTION = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val PREFS = "bt_battery"
    private const val KEY = "last"

    private val _levels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val levels: StateFlow<Map<String, Int>> = _levels.asStateFlow()

    private val _remembered = MutableStateFlow<Map<String, RememberedBattery>>(emptyMap())
    val remembered: StateFlow<Map<String, RememberedBattery>> = _remembered.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val level = intent.getIntExtra(EXTRA_LEVEL, -1)
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            val address = runCatching { device?.address }.getOrNull()
            val before = _levels.value
            _levels.update { BatteryReports.accept(it, address, level) }
            if (address != null && _levels.value != before) remember(context, address, level)
        }
    }

    private fun remember(context: Context, address: String, level: Int) {
        val next = _remembered.value + (BatteryReports.storeKey(address) to RememberedBattery(level, System.currentTimeMillis()))
        _remembered.value = BatteryReports.decode(BatteryReports.encode(next))
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, BatteryReports.encode(next)) }
    }

    /** Muat level yang diingat (dipanggil saat aplikasi mulai; cepat, berkas kecil). */
    fun load(context: Context) {
        _remembered.value = BatteryReports.decode(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
        )
    }

    /** Izin yang dibutuhkan versi Android ini. */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Daftarkan penerima bila izin sudah ada (dipanggil saat aplikasi mulai dan setelah pengguna memberi izin). */
    fun refresh(context: Context) {
        if (registered || !granted(context)) return
        // Pengirimnya proses Bluetooth sistem (bukan aplikasi ini), jadi penerima harus diekspor; isinya divalidasi.
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }
}
