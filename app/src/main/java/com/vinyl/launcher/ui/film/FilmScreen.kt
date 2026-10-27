package com.vinyl.launcher.ui.film

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vinyl.launcher.ui.components.blockTouchesBehind
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens

/*
 * Layar film: isi layar TV untuk kaset film, dan mode Teater (layar penuh).
 *
 * Satu WebView milik [FilmWebHolder] dipasang bergantian di layar TV atau di Teater, jadi film tidak dimuat ulang ketika
 * berpindah. Di layar TV halaman dilukis pada lebar "monitor" 480 dp lalu dikecilkan (tata letak seluler tetap terbaca);
 * di Teater dilukis apa adanya.
 */

/** Lebar minimum (dp) tempat halaman dilukis di layar TV sebelum dikecilkan ke ukuran layar. */
private const val TV_VIRTUAL_DP = 480f

/**
 * Isi layar TV untuk kaset film. [theatre] true = WebView sedang dipakai di Teater, layar TV memperlihatkan penandanya.
 * Sentuhan di layar langsung diteruskan ke halaman (kecuali selimut "sinyal hilang").
 */
@Composable
internal fun FilmTvScreen(holder: FilmWebHolder, theatre: Boolean, motionAllowed: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Bagian dari gambar TV: ukurannya mengikuti TV, bukan skala huruf sistem
        val font = (maxWidth.value * 0.085f).coerceIn(11f, 34f) / LocalDensity.current.fontScale
        if (theatre) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "Film sedang diputar di mode Teater" }, contentAlignment = Alignment.Center) {
                Text("TEATER", color = Color.White.copy(alpha = 0.5f), fontFamily = OsdFamily, fontSize = (font * 1.4f).sp)
            }
        } else {
            FilmWebSlot(holder, TV_VIRTUAL_DP, Modifier.fillMaxSize())
            when (holder.load) {
                FilmLoad.LOADING -> LoadingTape(holder.progress, font, motionAllowed)
                FilmLoad.ERROR, FilmLoad.CRASHED -> LostSignal(holder.error, font, onRetry = holder::retry)
                FilmLoad.IDLE, FilmLoad.READY -> Unit
            }
        }
    }
}

/** Memuat: tulisan MEMUAT bergaya OSD kamera, garis kemajuan amber di bawah, dan pita tracking yang turun perlahan. */
@Composable
private fun LoadingTape(progress: Int, font: Float, motion: Boolean) {
    Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "Memuat film" }) {
        if (motion) {
            val transition = rememberInfiniteTransition(label = "tracking")
            val band = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "band")
            // Fase dibaca di fase gambar: hanya lapisan ini yang digambar ulang, bukan disusun ulang
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val h = size.height * 0.07f
                    drawRect(Color.White.copy(alpha = 0.10f), Offset(0f, (size.height + h) * band.value - h), Size(size.width, h))
                },
            )
        }
        Text(
            "MEMUAT",
            color = Color.White,
            fontFamily = OsdFamily,
            fontSize = font.sp,
            lineHeight = font.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = Tokens.Film.LOADING_SCRIM_ALPHA.toFloat()))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                .height(3.dp)
                .background(RetroColors.Amber),
        )
    }
}

/** Situs tidak terbuka / proses halaman mati: layar gelap "SINYAL HILANG"; ketuk di mana saja untuk mencoba lagi. */
@Composable
private fun LostSignal(message: String, font: Float, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = Tokens.Film.LOST_SCRIM_ALPHA.toFloat()))
            .semantics(mergeDescendants = true) { contentDescription = "Sinyal hilang. $message Ketuk untuk mencoba lagi." }
            .clickable(onClickLabel = "Coba lagi", role = Role.Button, onClick = onRetry)
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SINYAL HILANG", color = Color.White, fontFamily = OsdFamily, fontSize = (font * 1.5f).sp, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Text(
                message,
                color = Color.White.copy(alpha = Tokens.Film.LOST_MESSAGE_ALPHA.toFloat()),
                fontFamily = MonoFamily,
                fontSize = (font * 0.62f).coerceAtLeast(9f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "KETUK UNTUK COBA LAGI",
            color = RetroColors.Amber,
            fontFamily = OsdFamily,
            fontSize = font.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Layar penuh: mode Teater ([theatre]) atau tampilan layar penuh yang diminta pemutar di halaman (tombol layar penuh
 * miliknya sendiri, `holder.customView`). Bilah sistem disembunyikan; layar diputar mendatar bila [landscape]. Tombol
 * Kembali keluar dari layar penuh (lebih dulu), dan tombol tutup kecil (48 dp) ada di pojok.
 *
 * Dipasang hanya selama layar penuh aktif; semua perubahan jendela dikembalikan saat keluar dari komposisi.
 */
@Composable
internal fun FilmFullscreen(holder: FilmWebHolder, theatre: Boolean, landscape: Boolean, onExitTheatre: () -> Unit) {
    val custom = holder.customView
    BackHandler(enabled = true) {
        if (holder.customView != null) holder.exitCustomView() else onExitTheatre()
    }
    ImmersiveWindow(landscape)
    Box(Modifier.fillMaxSize().background(Color.Black).blockTouchesBehind()) {
        val inset = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
        if (custom != null) {
            CustomViewHost(custom, inset)
        } else if (theatre) {
            FilmWebSlot(holder, 0f, inset)
            if (holder.load == FilmLoad.ERROR || holder.load == FilmLoad.CRASHED) {
                LostSignal(holder.error, 20f, onRetry = holder::retry)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    onClickLabel = if (custom != null) "Keluar dari layar penuh" else "Tutup teater",
                    role = Role.Button,
                    onClick = { if (holder.customView != null) holder.exitCustomView() else onExitTheatre() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = if (custom != null) "Keluar dari layar penuh" else "Tutup teater", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

/** Menampung tampilan layar penuh dari halaman (video HTML5) di dalam hierarki Compose. */
@Composable
private fun CustomViewHost(view: View, modifier: Modifier) {
    AndroidView(
        factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
        update = { host ->
            if (view.parent !== host) {
                (view.parent as? ViewGroup)?.removeView(view)
                host.removeAllViews()
                host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        onRelease = { host -> host.removeAllViews() },
        modifier = modifier,
    )
}

/**
 * Sembunyikan bilah sistem (geser dari tepi memunculkannya sebentar) dan, bila [landscape], kunci layar mendatar selama
 * komposisi ini ada. Keadaan semula dikembalikan saat keluar; bilah status yang memang disembunyikan pengguna dipasang
 * ulang oleh `SystemStatusBar` (kuncinya ikut berubah saat layar penuh berakhir).
 */
@Composable
private fun ImmersiveWindow(landscape: Boolean) {
    val view = LocalView.current
    DisposableEffect(landscape) {
        val activity = view.context.hostActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val orientation = activity?.requestedOrientation
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        if (landscape && activity != null) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (activity != null && orientation != null) activity.requestedOrientation = orientation
        }
    }
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
