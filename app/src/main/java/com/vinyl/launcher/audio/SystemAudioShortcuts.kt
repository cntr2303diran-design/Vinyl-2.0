package com.vinyl.launcher.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings

/**
 * Pintasan ke pengaturan audio resmi yang BENAR-BENAR ada di perangkat ini. Setiap intent diperiksa dengan
 * PackageManager sebelum ditampilkan; tidak ada nama aktivitas pabrikan yang ditebak. Launcher tidak bisa mengubah
 * efek pabrikan (mis. Dolby/efek Samsung); pintasan hanya membuka aplikasi yang mengaturnya.
 */
object SystemAudioShortcuts {
    enum class Kind { SOUND_SETTINGS, VOLUME_PANEL, BLUETOOTH, EFFECTS_PANEL }

    data class Shortcut(
        val kind: Kind,
        val label: String,
        /** Aplikasi yang akan membukanya (dari sistem), mis. nama panel efek pabrikan. */
        val handler: String,
        val intent: Intent,
        /** Panel efek audio wajib dibuka dengan startActivityForResult (dokumentasi AudioEffect). */
        val forResult: Boolean,
    )

    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun intentFor(context: Context, kind: Kind): Intent = when (kind) {
        Kind.SOUND_SETTINGS -> Intent(Settings.ACTION_SOUND_SETTINGS)
        Kind.VOLUME_PANEL -> Intent(Settings.Panel.ACTION_VOLUME)
        Kind.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        Kind.EFFECTS_PANEL -> Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }

    private fun label(kind: Kind): String = when (kind) {
        Kind.SOUND_SETTINGS -> "Pengaturan suara"
        Kind.VOLUME_PANEL -> "Panel volume & keluaran"
        Kind.BLUETOOTH -> "Bluetooth"
        Kind.EFFECTS_PANEL -> "Panel efek audio sistem"
    }

    /** Pintasan yang bisa dibuka di perangkat ini. Panggil di luar main thread bila memungkinkan (kueri PackageManager). */
    fun available(context: Context): List<Shortcut> {
        val pm = context.packageManager
        return Kind.entries.mapNotNull { kind ->
            val intent = intentFor(context, kind)
            val info = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull() ?: return@mapNotNull null
            // Jangan pernah mengarah kembali ke launcher ini
            if (info.activityInfo?.packageName == context.packageName) return@mapNotNull null
            val handler = runCatching { info.loadLabel(pm).toString() }.getOrDefault("")
            Shortcut(kind, label(kind), handler, intent, forResult = kind == Kind.EFFECTS_PANEL)
        }
    }
}
