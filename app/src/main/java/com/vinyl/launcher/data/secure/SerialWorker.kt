package com.vinyl.launcher.data.secure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Antrean kerja berurutan di thread latar: pekerjaan berjalan satu per satu sesuai urutan masuk, jadi baca-ubah-simpan
 * data terenkripsi (operasi Keystore bisa memakan puluhan milidetik) tidak pernah memblokir layar dan dua perubahan
 * beruntun tidak saling menimpa. Satu pekerjaan yang gagal tidak mematikan antrean.
 */
class SerialWorker(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
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
