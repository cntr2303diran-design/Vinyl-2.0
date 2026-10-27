package com.vinyl.launcher.ui.room

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/*
 * Inset yang dipakai ruang TV (panggung, widget, OSD).
 *
 * Bilah status disembunyikan/ditampilkan setiap kali halaman berpindah, dan sistem menganimasikannya ±300 ms — Compose
 * ikut menerima nilai inset BARU TIAP FRAME. Panggung yang membaca inset itu saat komposisi akan menghitung ulang seluruh
 * tata letak 3D dan menggambar ulang meja/kayu/rak pada setiap frame, tepat ketika halaman sedang digeser. Maka, bila
 * pengguna memilih menyembunyikan bilah status (bawaan), bilah itu TIDAK dihitung sama sekali: tata letak ruang TV tidak
 * bergantung pada kapan bilahnya tampil. Bila tidak, dipakai inset aman biasa (yang tidak berubah-ubah).
 */

/** Disediakan HomeScreen untuk subpohon ruang TV; null = pakai inset aman biasa (mis. di tes tangkapan layar). */
val LocalRoomInsets: ProvidableCompositionLocal<WindowInsets?> = compositionLocalOf { null }

/** Inset ruang TV saat ini. */
@Composable
fun roomInsets(): WindowInsets = LocalRoomInsets.current ?: WindowInsets.safeDrawing

/**
 * Inset yang tidak ikut naik-turun bersama bilah status: tanpa bilah itu bila [statusBarHidden] (pilihan pengguna),
 * jika tidak inset aman biasa. Takik kamera, bilah navigasi, dan papan ketik tetap dihitung.
 */
@Composable
fun stableRoomInsets(statusBarHidden: Boolean): WindowInsets =
    if (statusBarHidden) WindowInsets.displayCutout.union(WindowInsets.navigationBars).union(WindowInsets.ime)
    else WindowInsets.safeDrawing
