package com.vinyl.launcher.advanced

import android.content.Context
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * User Service Shizuku: berjalan di proses terpisah dengan identitas shell, dimulai hanya saat pengguna menekan
 * "Berikan izin lewat Shizuku" dan menyetujuinya. Satu-satunya operasi: `pm grant <paket ini> android.permission.DUMP`.
 * Nama paket tetap ([APP_ID]), bukan masukan, jadi layanan ini tidak bisa dipakai menjalankan perintah lain.
 */
class ShellService : IShellService.Stub {

    constructor() : super()

    /** Shizuku v13 memakai konstruktor dengan Context bila ada (lihat dokumentasi Shizuku-API). */
    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun grantDump(): Int {
        val process = runCatching {
            ProcessBuilder("pm", "grant", APP_ID, DUMP).redirectErrorStream(true).start()
        }.getOrNull() ?: return -1
        return try {
            // Keluaran pm sangat pendek (muat di buffer pipa), jadi menunggu dulu lalu membuangnya tidak bisa macet
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy()
                return -2
            }
            process.inputStream.close()
            process.exitValue()
        } catch (_: Exception) {
            process.destroy()
            -3
        }
    }

    companion object {
        /** Sama dengan applicationId di app/build.gradle.kts (dijaga tes AdvancedAccessContractTest). */
        const val APP_ID = "com.vinyl.launcher"
        const val DUMP = "android.permission.DUMP"
    }
}
