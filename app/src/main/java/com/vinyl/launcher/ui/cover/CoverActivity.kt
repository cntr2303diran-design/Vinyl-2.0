package com.vinyl.launcher.ui.cover

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.service.TaskbarState
import com.vinyl.launcher.ui.theme.VinylTheme

/**
 * Layar kunci vinyl ("cover screen"). Tampil DI ATAS keyguard sistem,
 * sehingga keamanan tetap ditangani PIN/sandi/biometrik bawaan Android:
 * - geser vinyl ke kanan → requestDismissKeyguard → PIN sistem → selesai
 * - tekan lama → verifikasi PIN sistem → editor tampilan
 */
class CoverActivity : ComponentActivity() {

    private val vm: CoverViewModel by viewModels()
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        // Sengaja TIDAK memakai setTurnScreenOn: activity ini dibuka saat layar dimatikan,
        // dan menyalakan layar di saat itu akan membuat layar langsung hidup lagi.
        hideSystemBars()

        // Back tidak menutup layar kunci; hanya membatalkan editor
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vm.draft.value != null) vm.cancelEdit()
            }
        })

        setContent {
            VinylTheme {
                CoverScreen(
                    vm = vm,
                    onUnlock = ::unlock,
                    onRequestEdit = ::verifyThenEdit,
                )
            }
        }
    }

    override fun onDestroy() {
        // Pastikan taskbar tidak tertahan tersembunyi bila activity ditutup sistem
        TaskbarState.setSuppressed(false)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        TaskbarState.setSuppressed(false)
    }

    override fun onResume() {
        super.onResume()
        TaskbarState.setSuppressed(true)
        MediaRepository.connect()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Dipanggil setelah animasi geser selesai. Jika perangkat terkunci, minta PIN sistem;
     * [onCancelled] dipanggil jika pengguna membatalkan (vinyl kembali ke tempatnya).
     */
    private fun unlock(onCancelled: () -> Unit) {
        if (!keyguard.isKeyguardLocked) {
            finishCover()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishCover()
            override fun onDismissCancelled() = onCancelled()
            override fun onDismissError() = onCancelled()
        })
    }

    /** Tekan lama: verifikasi PIN sistem lalu buka editor. */
    private fun verifyThenEdit() {
        if (!keyguard.isKeyguardLocked) {
            vm.startEdit()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = vm.startEdit()
        })
    }

    private fun finishCover() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        /** Tampilkan layar kunci vinyl (dipanggil CoverService saat layar mati). */
        fun show(context: Context) {
            val intent = Intent(context, CoverActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            runCatching { context.startActivity(intent) }
        }
    }
}
