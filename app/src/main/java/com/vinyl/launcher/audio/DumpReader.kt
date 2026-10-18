package com.vinyl.launcher.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * Membaca laporan diagnostik satu layanan sistem (setara `dumpsys <layanan>`) lewat [Debug.dumpService], hanya bila
 * izin DUMP sudah diberikan pengguna (ADB atau Shizuku). Isi laporan:
 *  - dibaca lewat pipa di memori (tidak pernah ditulis ke berkas), dibatasi [MAX_BYTES];
 *  - tidak dicatat ke log dan tidak dikirim ke mana pun; pemanggil hanya mengambil field yang ditampilkan;
 *  - dibaca di thread latar dengan batas waktu agar layanan yang macet tidak menggantung launcher.
 */
object DumpReader {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val TIMEOUT_MS = 4_000L

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** Isi laporan, atau null bila izin tidak ada, layanan menolak, atau melewati batas waktu. */
    suspend fun read(context: Context, service: String, args: Array<String> = emptyArray()): String? {
        if (!granted(context)) return null
        return withContext(Dispatchers.IO) { readBlocking(service, args) }
    }

    private fun readBlocking(service: String, args: Array<String>): String? {
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return null
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val bytes = ByteArrayOutputStream()
        // Pembaca jalan bersamaan: tanpa itu layanan bisa tertahan saat pipa penuh
        val reader = thread(isDaemon = true, name = "vinyl-dump-read") {
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(readSide).use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        val room = MAX_BYTES - bytes.size()
                        if (room > 0) bytes.write(buf, 0, min(n, room)) // sisanya dibuang agar penulis tidak tertahan
                    }
                }
            }
        }
        var ok = false
        val writer = thread(isDaemon = true, name = "vinyl-dump") {
            ok = runCatching { Debug.dumpService(service, writeSide.fileDescriptor, args) }.getOrDefault(false)
            runCatching { writeSide.close() }
        }
        writer.join(TIMEOUT_MS)
        if (writer.isAlive) {
            runCatching { writeSide.close() }
            runCatching { readSide.close() }
            return null
        }
        reader.join(TIMEOUT_MS)
        if (reader.isAlive) {
            runCatching { readSide.close() }
            return null
        }
        if (!ok) return null
        val text = bytes.toString(Charsets.UTF_8.name())
        return text.takeUnless { it.startsWith("Permission Denial") }
    }
}
