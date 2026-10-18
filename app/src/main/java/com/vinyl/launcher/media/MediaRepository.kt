package com.vinyl.launcher.media

import androidx.core.graphics.get
import androidx.core.net.toUri
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.vinyl.launcher.service.MediaListenerService
import com.vinyl.launcher.util.Http
import com.vinyl.launcher.util.Images
import com.vinyl.launcher.util.readAtMost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Status lagu yang sedang diputar (atau terakhir diputar).
 * Posisi dihitung dari snapshot PlaybackState + waktu berlalu, sehingga UI
 * cukup memanggil [positionAt] tiap frame tanpa polling ke aplikasi musik.
 */
data class NowPlaying(
    val hasSession: Boolean = false,
    val packageName: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: ImageBitmap? = null,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    /** Warna dominan cover (ARGB) dari Palette API, null jika tidak ada cover. */
    val dominantColor: Int? = null,
    /** Warna aksen cerah dari cover, untuk label vinyl. */
    val accentColor: Int? = null,
    /** Beberapa warna dari cover untuk latar ambient bergerak (ala Apple Music). */
    val swatches: List<Int> = emptyList(),
    val positionMs: Long = 0L,
    val positionUpdateTime: Long = 0L,
    val speed: Float = 1f,
    val canSeek: Boolean = false,
) {
    /** True jika ada metadata yang layak ditampilkan. */
    val hasTrack: Boolean get() = hasSession && title.isNotBlank()

    /** Posisi pemutaran terkini (ms), diekstrapolasi dari snapshot terakhir. */
    fun positionAt(now: Long = SystemClock.elapsedRealtime()): Long {
        val elapsed = if (isPlaying) ((now - positionUpdateTime) * speed).toLong() else 0L
        val pos = positionMs + elapsed
        return if (durationMs > 0) pos.coerceIn(0, durationMs) else pos.coerceAtLeast(0)
    }

    val progress: Float get() = if (durationMs > 0) positionAt().toFloat() / durationMs else 0f
}

/**
 * Integrasi MediaSessionManager.
 * Membaca semua sesi media aktif (izin akses notifikasi diperlukan),
 * memilih sesi yang sedang memutar, lalu mengekspos metadata & kontrol.
 */
object MediaRepository {
    private const val TAG = "MediaRepository"
    /** Sisi terpanjang minimal cover hasil decode (nanti diperkecil lagi ke 900 px untuk Palette & tampilan). */
    private const val ART_TARGET_PX = 900

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private lateinit var appContext: Context
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var sessionManager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    // @Volatile: dibaca juga dari coroutine sleep timer (thread lain) selain thread utama yang menulisnya
    @Volatile private var active: MediaController? = null
    private var connected = false

    /** Paket aplikasi musik default (diisi dari pengaturan). */
    @Volatile var defaultMusicPackage: String? = null
        set(value) { field = value; if (connected) chooseActive() }

    /** Pergantian lembut untuk tombol berikutnya/sebelumnya (diisi dari pengaturan Musik). */
    @Volatile var softSkipEnabled: Boolean = false

    // Cache cover & palette per lagu agar tidak dihitung ulang
    private var artKey: String? = null
    private var artJob: Job? = null
    private var cachedArt: ImageBitmap? = null
    private var cachedDominant: Int? = null
    private var cachedAccent: Int? = null
    private var cachedSwatches: List<Int> = emptyList()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessionsChanged(list.orEmpty())
    }

    /**
     * Mulai mendengarkan sesi media. Aman dipanggil berkali-kali; dipanggil dari
     * MediaListenerService.onListenerConnected dan saat activity kembali aktif.
     */
    fun connect() {
        if (!::appContext.isInitialized || connected) return
        if (!hasNotificationAccess(appContext)) return
        val msm = appContext.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(appContext, MediaListenerService::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            sessionManager = msm
            connected = true
            onSessionsChanged(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            Log.w(TAG, "Akses notifikasi belum diberikan", e)
        }
    }

    fun disconnect() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        controllers = emptyList()
        active = null
        connected = false
        _state.value = NowPlaying()
    }

    private fun onSessionsChanged(list: List<MediaController>) {
        // Lepas callback dari sesi yang sudah hilang
        val tokens = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it.sessionToken !in tokens }.forEach { c ->
            callbacks.remove(c)?.let { c.unregisterCallback(it) }
        }
        controllers = list
        // Daftarkan callback ke semua sesi agar tahu ketika sesi lain mulai memutar
        list.forEach { c ->
            if (callbacks.keys.none { it.sessionToken == c.sessionToken }) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        logState(c.packageName, state)
                        chooseActive()
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) = chooseActive()
                    override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = chooseActive()
                    override fun onSessionDestroyed() {
                        log("${appName(c.packageName)}: sesi ditutup aplikasi")
                        chooseActive()
                    }
                }
                c.registerCallback(cb, handler)
                callbacks[c] = cb
            }
        }
        chooseActive()
    }

    /** Prioritas: sesi yang sedang memutar → aplikasi default → sesi pertama dengan metadata. */
    private fun chooseActive() {
        val list = callbacks.keys.toList()
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName == defaultMusicPackage && it.metadata != null }
            ?: list.firstOrNull { it.metadata != null }
            ?: list.firstOrNull()
        active = chosen
        publish(chosen)
    }

    private fun publish(c: MediaController?) {
        if (c == null) {
            _state.value = NowPlaying()
            _queue.value = emptyList()
            HistoryRecorder.onState(_state.value)
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        // Kunci cover = lagu + "sidik jari" gambarnya. Banyak aplikasi (mis. Spotify) mengirim
        // metadata dulu TANPA gambar, lalu menyusulkan gambar untuk lagu yang sama.
        // Tanpa sidik jari, gambar susulan itu dulu terlewat → cover tidak pernah berganti.
        val key = "${c.packageName}|$title|$artist|$album|${md?.let { artSignature(it) } ?: "none"}"
        val playing = ps?.state == PlaybackState.STATE_PLAYING ||
            ps?.state == PlaybackState.STATE_BUFFERING
        val actions = ps?.actions ?: 0L

        _state.update { old ->
            NowPlaying(
                hasSession = true,
                packageName = c.packageName,
                title = title,
                artist = artist,
                album = album,
                // Pertahankan cover lama sampai cover baru selesai dimuat (transisi crossfade, tanpa kedip)
                art = if (key == artKey) cachedArt else old.art,
                durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L,
                isPlaying = playing,
                dominantColor = if (key == artKey) cachedDominant else old.dominantColor,
                accentColor = if (key == artKey) cachedAccent else old.accentColor,
                swatches = if (key == artKey) cachedSwatches else old.swatches,
                positionMs = ps?.position ?: 0L,
                positionUpdateTime = ps?.lastPositionUpdateTime?.takeIf { t -> t > 0 }
                    ?: SystemClock.elapsedRealtime(),
                speed = ps?.playbackSpeed?.takeIf { s -> s > 0f } ?: 1f,
                canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            )
        }
        if (key != artKey) loadArt(key, md)
        publishQueue(c)
        HistoryRecorder.onState(_state.value)
    }

    // ===================== Antrean (Up Next) =====================

    /** Satu lagu dalam antrean aplikasi musik. */
    data class QueueEntry(val id: Long, val title: String, val subtitle: String, val art: Bitmap?, val artUri: String?, val isCurrent: Boolean)

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    /** Antrean dari aplikasi musik aktif (kosong jika aplikasi tidak membagikannya). */
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private fun publishQueue(c: MediaController) {
        val activeId = c.playbackState?.activeQueueItemId ?: -1L
        _queue.value = runCatching {
            c.queue.orEmpty().map { item ->
                val d = item.description
                QueueEntry(
                    id = item.queueId,
                    title = d.title?.toString().orEmpty(),
                    subtitle = d.subtitle?.toString().orEmpty(),
                    art = d.iconBitmap,
                    artUri = d.iconUri?.toString(),
                    isCurrent = item.queueId == activeId,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun playQueueItem(id: Long) { active?.transportControls?.skipToQueueItem(id) }

    /**
     * Sidik jari murah untuk gambar cover: ukuran + 5 sampel piksel, atau string URI.
     * Bitmap dari metadata selalu objek baru tiap dibaca, jadi tidak bisa dibandingkan referensinya.
     */
    private fun artSignature(md: MediaMetadata): String {
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            return runCatching {
                val w = bmp.width; val h = bmp.height
                val pts = listOf(w / 2 to h / 2, w / 4 to h / 4, 3 * w / 4 to h / 4, w / 4 to 3 * h / 4, 3 * w / 4 to 3 * h / 4)
                val px = if (bmp.config == Bitmap.Config.HARDWARE) "hw" else pts.joinToString(",") { (x, y) -> bmp[x, y].toString() }
                "${w}x$h:$px"
            }.getOrDefault("bmp")
        }
        return md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: "none"
    }

    private data class ArtResult(val art: ImageBitmap, val dominant: Int, val accent: Int, val swatches: List<Int>)

    /** Ambil cover dari metadata (bitmap, content URI, atau URL http), lalu hitung warna dengan Palette. */
    private fun loadArt(key: String, md: MediaMetadata?) {
        artKey = key
        artJob?.cancel()
        artJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = md?.let { extractBitmap(it) } ?: return@runCatching null
                    val soft = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
                    val scaled = if (soft.width > 900 || soft.height > 900) {
                        val f = 900f / maxOf(soft.width, soft.height)
                        soft.scale((soft.width * f).toInt().coerceAtLeast(1), (soft.height * f).toInt().coerceAtLeast(1))
                    } else soft
                    val palette = Palette.from(scaled).maximumColorCount(24).generate()
                    val dominant = palette.getDominantColor(0xFF1B1B1F.toInt())
                    val accent = palette.getVibrantColor(palette.getLightVibrantColor(palette.getMutedColor(dominant)))
                    // Warna untuk latar ambient: urut dari yang paling "hidup"
                    val swatches = listOfNotNull(
                        palette.vibrantSwatch?.rgb, palette.darkVibrantSwatch?.rgb, palette.mutedSwatch?.rgb,
                        palette.darkMutedSwatch?.rgb, palette.lightVibrantSwatch?.rgb, palette.dominantSwatch?.rgb,
                    ).distinct().ifEmpty { listOf(dominant) }
                    ArtResult(scaled.asImageBitmap(), dominant, accent, swatches)
                }.getOrNull()
            }
            if (artKey != key) return@launch
            cachedArt = result?.art
            cachedDominant = result?.dominant
            cachedAccent = result?.accent
            cachedSwatches = result?.swatches.orEmpty()
            _state.update { s ->
                s.copy(art = cachedArt, dominantColor = cachedDominant, accentColor = cachedAccent, swatches = cachedSwatches)
            }
            HistoryRecorder.onState(_state.value)
        }
    }

    private fun extractBitmap(md: MediaMetadata): Bitmap? {
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        val uris = listOfNotNull(
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
        )
        for (uri in uris) {
            // URI berasal dari aplikasi lain, jadi tidak dipercaya: hanya https (internet) dan content/android.resource
            // (penyedia konten); skema file:// ditolak. Ukuran data dibatasi dan gambar selalu diperkecil saat decode.
            val bmp = runCatching {
                val u = uri.toUri()
                val bytes = when (u.scheme?.lowercase()) {
                    "content", "android.resource" ->
                        appContext.contentResolver.openInputStream(u)?.use { it.readAtMost(Images.MAX_BYTES) }
                    "https" -> Http.get(uri, Images.MAX_BYTES, connectMs = 6_000, readMs = 8_000)
                    else -> null
                }
                bytes?.let { Images.decodeSampled(it, ART_TARGET_PX) }
            }.getOrNull()
            if (bmp != null) return bmp
        }
        return null
    }

    // ===================== Diagnostik =====================
    // Catatan kejadian pemutaran untuk melacak "musik berhenti sendiri":
    // membedakan perintah yang dikirim Vinyl dengan perubahan dari aplikasi musik itu sendiri.

    data class LogEntry(val time: Long, val text: String)

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()
    private val lastStates = mutableMapOf<String, Int>()

    fun log(text: String) {
        _log.update { (it + LogEntry(System.currentTimeMillis(), text)).takeLast(120) }
    }

    private fun logState(pkg: String, ps: PlaybackState?) {
        val st = ps?.state ?: PlaybackState.STATE_NONE
        if (lastStates[pkg] == st) return
        lastStates[pkg] = st
        val name = when (st) {
            PlaybackState.STATE_PLAYING -> "memutar"
            PlaybackState.STATE_PAUSED -> "dijeda"
            PlaybackState.STATE_STOPPED -> "berhenti"
            PlaybackState.STATE_BUFFERING -> "buffering"
            PlaybackState.STATE_ERROR -> "ERROR"
            PlaybackState.STATE_CONNECTING -> "menyambung"
            PlaybackState.STATE_NONE -> "tidak aktif"
            else -> "status $st"
        }
        val err = ps?.errorMessage?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
        log("${appName(pkg)}: $name$err")
    }

    private fun appName(pkg: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ===================== Kontrol =====================

    fun togglePlayPause(source: String = "TV Launcher") {
        val c = active ?: return
        log("$source → ${if (c.playbackState?.state == PlaybackState.STATE_PLAYING) "JEDA" else "PUTAR"} dikirim ke ${appName(c.packageName)}")
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
        // Respons instan di UI; status asli menyusul lewat callback
        _state.update {
            it.copy(
                isPlaying = !it.isPlaying,
                positionMs = it.positionAt(),
                positionUpdateTime = SystemClock.elapsedRealtime(),
            )
        }
    }

    /**
     * Jeda secara eksplisit (berbeda dari [togglePlayPause]: tidak pernah memutar). Dipakai sleep timer.
     * Tidak melakukan apa pun bila tidak ada sesi atau sesi sudah berhenti.
     */
    fun pause(source: String = "TV Launcher") {
        val c = active ?: return
        if (c.playbackState?.state != PlaybackState.STATE_PLAYING && !_state.value.isPlaying) return
        log("$source → JEDA dikirim ke ${appName(c.packageName)}")
        c.transportControls.pause()
        _state.update { it.copy(isPlaying = false, positionMs = it.positionAt(), positionUpdateTime = SystemClock.elapsedRealtime()) }
    }

    fun next() { log("TV Launcher → lagu berikutnya"); skip { it.skipToNext() } }
    fun previous() { log("TV Launcher → lagu sebelumnya"); skip { it.skipToPrevious() } }

    /** Kirim perintah ganti lagu, dengan volume memudar di sekelilingnya bila pergantian lembut menyala. */
    private fun skip(command: (MediaController.TransportControls) -> Unit) {
        val send = { active?.transportControls?.let(command); Unit }
        if (softSkipEnabled) SoftSkip.run(send) else send()
    }

    fun seekTo(ms: Long) {
        val c = active ?: return
        log("TV Launcher → lompat ke ${ms / 1000} dtk")
        val target = if (_state.value.durationMs > 0) ms.coerceIn(0, _state.value.durationMs) else ms.coerceAtLeast(0)
        c.transportControls.seekTo(target)
        _state.update { it.copy(positionMs = target, positionUpdateTime = SystemClock.elapsedRealtime()) }
    }

    /** Buka aplikasi musik: default → sesi aktif → pemilih aplikasi musik sistem. */
    fun openMusicApp(context: Context) {
        val pm = context.packageManager
        val pkg = defaultMusicPackage ?: _state.value.packageName
        val intent = pkg?.let { pm.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Tidak ada aplikasi musik", it) }
    }
}
