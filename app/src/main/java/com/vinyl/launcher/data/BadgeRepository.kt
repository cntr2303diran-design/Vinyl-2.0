package com.vinyl.launcher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Jumlah notifikasi belum dibaca per nama paket, hanya di memori (tidak pernah ditulis ke disk).
 * Diisi oleh MediaListenerService; kosong bila akses notifikasi tidak diberikan atau lencana dimatikan.
 * Kunci adalah nama paket, jadi aplikasi yang sama di profil kerja dan pribadi berbagi hitungan.
 */
object BadgeRepository {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun publish(counts: Map<String, Int>) { _counts.value = counts }
    fun clear() { _counts.value = emptyMap() }
}
