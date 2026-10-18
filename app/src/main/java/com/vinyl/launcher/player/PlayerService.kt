package com.vinyl.launcher.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.ui.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pemutar bawaan: ExoPlayer + sesi media (Media3). Berkas dibaca langsung dari folder pilihan pengguna lewat URI SAF.
 * Sesi media platform membuat lagu tampil di beranda, layar kunci, notifikasi, dan bisa dikendalikan tombol headset
 * atau Bluetooth seperti aplikasi musik lain; layanan tidak diekspor, jadi aplikasi lain tidak bisa mengikatnya.
 *
 * Suara: dekoder diminta keluaran float (24-bit utuh), lalu [HiFiAudioSink] menjalankan ReplayGain, EQ parametrik,
 * crossfeed, dan pembatas sebelum AudioTrack float. Gapless bawaan ExoPlayer (FLAC/WAV, MP3 LAME, AAC iTunes).
 */
@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var usbRate: UsbRateMatch

    override fun onCreate() {
        super.onCreate()
        usbRate = UsbRateMatch(this)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                HiFiAudioSink(DefaultAudioSink.Builder(context).setEnableFloatOutput(true).build(), usbRate)
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PathListener(player))
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setCallback(Callback()).setSessionActivity(open).build()
        val repo = SettingsRepository.get(this)
        scope.launch {
            repo.settings.map { it.player }.distinctUntilChanged().collect { p ->
                HiFi.apply(p)
                usbRate.enabled = p.usbRateMatch
                if (!p.usbRateMatch) {
                    usbRate.clear()
                    HiFi.updatePath { it.copy(usbRate = "") }
                }
                LocalLibrary.load(this@PlayerService, p.folderUri)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        usbRate.clear()
        HiFi.updatePath { HiFi.PathState() }
        super.onDestroy()
    }

    /** ID lagu dari pengendali → MediaItem lengkap (URI berkas + tag dari indeks). */
    private inner class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val folder = LocalLibrary.state.value.folderUri
                ?: return Futures.immediateFailedFuture(IllegalStateException("folder musik belum dipilih"))
            val resolved = mediaItems.mapNotNull { item -> LocalLibrary.track(item.mediaId)?.let { item(folder, it) } }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }

    private fun item(folder: String, t: LocalTrack): MediaItem = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(LocalLibrary.uriOf(folder, t.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist.ifBlank { null })
                .setAlbumTitle(t.album.ifBlank { null })
                .setAlbumArtist(t.albumArtist.ifBlank { null })
                .setTrackNumber(t.trackNo.takeIf { it > 0 })
                .setDiscNumber(t.discNo.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()

    /** Info sumber lagu (format, laju sampel, kedalaman bit) untuk deck & Audio Console. */
    private inner class PathListener(private val player: Player) : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            val format = group?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let(g::getTrackFormat) }
            val id = player.currentMediaItem?.mediaId
            val indexed = id?.let(LocalLibrary::track)
            val codec = indexed?.let { LocalFormat.codec(format?.sampleMimeType ?: it.mime, it.fileName) }
                ?: format?.sampleMimeType?.substringAfter('/')?.uppercase().orEmpty()
            val pcmBits = format?.pcmEncoding?.let { HiFiAudioSink.pcmOf(it)?.bits } ?: 0
            val bits = pcmBits.takeIf { it > 0 } ?: indexed?.bitsPerSample ?: 0
            HiFi.updatePath {
                it.copy(
                    active = player.mediaItemCount > 0,
                    trackId = id,
                    codec = codec,
                    sourceRate = format?.sampleRate?.takeIf { r -> r > 0 } ?: indexed?.sampleRate ?: 0,
                    sourceBits = bits,
                    channels = format?.channelCount?.takeIf { c -> c > 0 } ?: 0,
                    lossless = LocalFormat.lossless(codec, bits),
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) {
                HiFi.updatePath { it.copy(active = player.mediaItemCount > 0) }
            }
        }
    }
}
