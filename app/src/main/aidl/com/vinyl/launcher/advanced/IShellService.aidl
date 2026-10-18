// Layanan yang dijalankan Shizuku dengan identitas shell (ADB). Sengaja hanya punya satu operasi tetap: memberi izin
// DUMP ke launcher ini sendiri. Tidak ada metode yang menerima perintah atau argumen bebas.
package com.vinyl.launcher.advanced;

interface IShellService {
    // Kode transaksi "destroy" yang ditetapkan server Shizuku (dokumentasi Shizuku-API)
    void destroy() = 16777114;

    // Menjalankan: pm grant com.vinyl.launcher android.permission.DUMP. Mengembalikan kode keluar (0 = berhasil).
    int grantDump() = 1;
}
