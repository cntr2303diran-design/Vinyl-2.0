package com.vinyl.launcher.ui.room

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.max

/**
 * Video milik pengguna di layar TV: tanpa suara (trek audio dimatikan, volume 0, tidak meminta audio focus sehingga
 * musik tidak terganggu), berulang, resolusi dibatasi 720p agar hemat baterai, dipotong memenuhi layar.
 * Muncul perlahan setelah frame pertama (sebelum itu cover tetap terlihat). Pemutar dilepas saat composable ini keluar
 * (pemanggil hanya memasangnya selama beranda tampil).
 */
@Composable
fun ScreenVideo(uri: Uri, playing: Boolean, onError: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val failed by rememberUpdatedState(onError)
    key(uri) {
        val player = remember {
            ExoPlayer.Builder(context.applicationContext).build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setMaxVideoSize(1280, 720)
                    .build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }
        var size by remember { mutableStateOf(VideoSize.UNKNOWN) }
        var firstFrame by remember { mutableStateOf(false) }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) { size = videoSize }
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onPlayerError(error: PlaybackException) = failed()
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
        LaunchedEffect(player, playing) { player.playWhenReady = playing }
        val alpha by animateFloatAsState(if (firstFrame) 1f else 0f, tween(600), label = "videoIn")
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    player.setVideoTextureView(tv)
                    tv.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> centerCrop(v as TextureView, v.tag as? VideoSize) }
                }
            },
            update = { tv ->
                tv.tag = size
                centerCrop(tv, size)
            },
            modifier = modifier.graphicsLayer { this.alpha = alpha }.clearAndSetSemantics { },
        )
    }
}

/** TextureView meregangkan video ke ukurannya; matriks ini memulihkan rasio lalu memotong agar memenuhi layar. */
private fun centerCrop(tv: TextureView, size: VideoSize?) {
    if (size == null) return
    val vw = size.width * (if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f)
    val vh = size.height.toFloat()
    val w = tv.width.toFloat()
    val h = tv.height.toFloat()
    if (vw <= 0f || vh <= 0f || w <= 0f || h <= 0f) return
    val scale = max(w / vw, h / vh)
    val m = Matrix()
    m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f)
    tv.setTransform(m)
}
