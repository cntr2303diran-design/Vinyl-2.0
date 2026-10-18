package com.vinyl.launcher.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Layanan aksesibilitas opsional. Hanya dipakai untuk aksi global
 * (kunci layar & buka notifikasi) dari gestur home screen.
 * Tidak membaca isi jendela (canRetrieveWindowContent = false).
 */
class VinylAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: VinylAccessibilityService? = null
            private set
    }
}
