package com.vinyl.launcher.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pasang pada lapisan layar penuh yang menutupi beranda (drawer, rak piringan, lirik).
 * Compose hanya mengirim sentuhan ke lapisan di bawah bila lapisan atas tidak punya penerima sentuhan di titik itu;
 * tanpa ini, ketuk-ganda atau tekan-lama di area kosong lapisan merambat ke beranda dan membuka mode edit di belakangnya.
 */
fun Modifier.blockTouchesBehind(): Modifier = pointerInput(Unit) { }
