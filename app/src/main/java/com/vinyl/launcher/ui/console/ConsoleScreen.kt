package com.vinyl.launcher.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.ui.theme.RetroColors

/**
 * Halaman kanan: Audio Console.
 *
 * TAHAP 2 (branch kerja retro-tv): hanya kerangka halaman agar navigasi tiga halaman bisa diuji. Panel Devices,
 * Sound, Quality, Spatial, Diagnostics, Automation, dan Samsung dibangun di Tahap 6; teks di bawah menyatakannya apa
 * adanya. Berkas ini wajib terisi penuh sebelum branch digabung ke main.
 */
@Composable
fun ConsoleScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(RetroColors.CharcoalDeep)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "AUDIO CONSOLE",
            color = RetroColors.CreamText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text("SYSTEM STATUS · DEVICE CONTROL", color = RetroColors.Amber, fontSize = 13.sp)
        Text(
            "Panel perangkat, suara, kualitas, efek, diagnostik, otomasi, dan Samsung sedang dibangun (Tahap 6).",
            color = RetroColors.CreamTextSecondary,
        )
        TextButton(onClick = onBack) { Text("Kembali ke ruang TV", color = RetroColors.CreamText) }
    }
}
