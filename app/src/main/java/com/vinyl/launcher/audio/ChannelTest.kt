package com.vinyl.launcher.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tes kanal kiri/kanan di panel Diagnostik: memutar nada pendek dari [ToneSynth] ke keluaran media saat ini.
 * Tidak meminta audio focus (musik tidak dijeda), level dibatasi, berhenti sendiri, dan bisa dihentikan kapan saja.
 */
object ChannelTest {
    private const val DURATION_MS = 1200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var track: AudioTrack? = null
    private var job: Job? = null

    private val _playing = MutableStateFlow<ToneSynth.Side?>(null)
    val playing: StateFlow<ToneSynth.Side?> = _playing.asStateFlow()

    fun play(side: ToneSynth.Side) {
        stop()
        job = scope.launch {
            val pcm = ToneSynth.stereo(side, DURATION_MS)
            val t = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(ToneSynth.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                    .also { it.write(pcm, 0, pcm.size) }
            }.getOrNull() ?: return@launch
            synchronized(lock) {
                // stop() bisa terjadi saat nada sedang disiapkan: jangan sampai trek lepas tanpa dilepaskan
                if (!isActive) { runCatching { t.release() }; return@launch }
                track = t
                runCatching { t.play() }
                _playing.value = side
            }
            try {
                delay(DURATION_MS + 150L)
            } finally {
                synchronized(lock) { if (track === t) stopLocked() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
        _playing.value = null
    }
}
