package com.vinyl.launcher.data.secure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Antrean kerja berurutan di thread latar: pekerjaan berjalan satu per satu sesuai urutan masuk, jadi baca-ubah-simpan
 * data terenkripsi (operasi Keystore bisa memakan puluhan milidetik) tidak pernah memblokir layar dan dua perubahan
 * beruntun tidak saling menimpa. Satu pekerjaan yang gagal tidak mematikan antrean.
 *
 * Bawaannya berjalan di satu thread sendiri dengan prioritas terendah ([quietScope]): pekerjaan ini tidak mendesak, dan saat
 * aplikasi baru dibuka ia tidak boleh berebut CPU dengan thread utama yang sedang menggambar beranda pertama kali.
 */
class SerialWorker(scope: CoroutineScope = quietScope()) {
    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in queue) {
                try {
                    job()
                } catch (e: Exception) {
                    // Pekerjaan berikutnya tetap jalan; data yang gagal disimpan dilaporkan lewat status pemiliknya
                }
            }
        }
    }

    fun post(job: () -> Unit) {
        queue.trySend(job)
    }
}

/**
 * Lingkup coroutine di atas satu thread daemon bernama "vinyl-secure" berprioritas terendah. Pada Android prioritas Java
 * terendah dipetakan ke nilai nice terendah, jadi thread ini hanya memakai CPU yang tidak dipakai thread utama.
 */
private fun quietScope(): CoroutineScope {
    val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "vinyl-secure").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    return CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
}
