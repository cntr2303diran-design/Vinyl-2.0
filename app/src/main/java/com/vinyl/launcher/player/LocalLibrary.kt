package com.vinyl.launcher.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Indeks berkas musik di folder pilihan pengguna (Storage Access Framework, izin baca persisten; launcher tidak punya
 * izin penyimpanan). Subfolder ikut dipindai (paling dalam [MAX_DEPTH] tingkat). Tag dibaca dengan
 * MediaMetadataRetriever di thread latar, lalu indeks disimpan di penyimpanan internal aplikasi supaya pemindaian
 * berikutnya hanya membaca berkas yang baru atau berubah. Tidak ada yang dikirim ke luar perangkat.
 */
object LocalLibrary {
    private const val TAG = "VinylLocal"
    const val MAX_FILES = 10_000
    const val MAX_DEPTH = 8
    private const val INDEX_FILE = "local_library.json"

    data class State(
        val folderUri: String? = null,
        val tracks: List<LocalTrack> = emptyList(),
        val scanning: Boolean = false,
        /** Berkas yang sudah diperiksa / ditemukan selama pemindaian. */
        val checked: Int = 0,
        val found: Int = 0,
        val error: String? = null,
        val truncated: Boolean = false,
        val loaded: Boolean = false,
    )

    @Serializable
    private data class IndexFile(val folderUri: String, val tracks: List<LocalTrack>)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Muat indeks tersimpan (cepat) untuk [folderUri]; tidak memindai. */
    suspend fun load(context: Context, folderUri: String?) {
        if (folderUri == null) {
            _state.value = State(loaded = true)
            return
        }
        if (_state.value.folderUri == folderUri && _state.value.loaded) return
        val saved = withContext(Dispatchers.IO) { readIndex(context) }?.takeIf { it.folderUri == folderUri }
        _state.update { State(folderUri = folderUri, tracks = saved?.tracks.orEmpty(), loaded = true, scanning = it.scanning) }
    }

    /** Untuk tes tangkapan layar: isi indeks tanpa membaca perangkat (tidak dipakai kode aplikasi). */
    @VisibleForTesting
    internal fun preview(folderUri: String, tracks: List<LocalTrack>) {
        _state.value = State(folderUri = folderUri, tracks = tracks, loaded = true)
    }

    /** Peta id → lagu untuk daftar [state] saat ini; dibuat ulang hanya bila daftarnya berganti. */
    @Volatile private var byId: Pair<List<LocalTrack>, Map<String, LocalTrack>>? = null

    /**
     * Lagu dengan [id] di indeks sekarang. O(1): antrean "putar semua" bisa berisi ribuan id yang dicari satu per satu
     * di thread utama (sesi media), jadi pencarian linear per id tidak boleh dipakai.
     */
    fun track(id: String): LocalTrack? {
        val list = _state.value.tracks
        val cached = byId
        val map = if (cached != null && cached.first === list) cached.second else list.associateBy { it.id }.also { byId = list to it }
        return map[id]
    }

    fun uriOf(folderUri: String, id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(folderUri.toUri(), id)

    /**
     * Pindai folder: berkas yang ukuran & waktu ubahnya sama dengan indeks tidak dibaca ulang. Bisa dibatalkan (mis.
     * saat folder diganti); hasil sebagian tidak disimpan.
     */
    suspend fun scan(context: Context, folderUri: String) {
        mutex.withLock {
            val app = context.applicationContext
            load(app, folderUri)
            val known = _state.value.tracks.associateBy { it.id }
            _state.update { it.copy(folderUri = folderUri, scanning = true, checked = 0, found = 0, error = null) }
            try {
                val (tracks, truncated) = withContext(Dispatchers.IO) { walk(app, folderUri.toUri(), known) }
                withContext(Dispatchers.IO) { writeIndex(app, IndexFile(folderUri, tracks)) }
                _state.update { it.copy(tracks = tracks, scanning = false, truncated = truncated, error = null) }
            } catch (e: CancellationException) {
                _state.update { it.copy(scanning = false) }
                throw e
            } catch (e: SecurityException) {
                _state.update { it.copy(scanning = false, error = "Izin folder sudah tidak berlaku. Pilih folder lagi.") }
            } catch (e: Exception) {
                Log.w(TAG, "Pemindaian gagal: ${e.javaClass.simpleName}")
                _state.update { it.copy(scanning = false, error = "Folder tidak bisa dibaca.") }
            }
        }
    }

    /** Lupakan indeks (folder dilepas). */
    suspend fun clear(context: Context) {
        _state.value = State(loaded = true)
        withContext(Dispatchers.IO) { runCatching { File(context.filesDir, INDEX_FILE).delete() } }
    }

    private suspend fun walk(context: Context, tree: Uri, known: Map<String, LocalTrack>): Pair<List<LocalTrack>, Boolean> {
        if (context.contentResolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) {
            throw SecurityException("no persisted permission")
        }
        val out = ArrayList<LocalTrack>()
        var truncated = false
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(DocumentsContract.getTreeDocumentId(tree) to 0)
        val retriever = MediaMetadataRetriever()
        try {
            while (queue.isNotEmpty()) {
                val (dir, depth) = queue.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
                val cursor = context.contentResolver.query(children, projection, null, null, null) ?: continue
                cursor.use { c ->
                    while (c.moveToNext()) {
                        coroutineContext.ensureActive()
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            // Folder tersembunyi (".thumbnails" dll.) dilewati
                            if (depth < MAX_DEPTH && !name.startsWith(".")) queue.add(id to depth + 1)
                            continue
                        }
                        if (!LocalFormat.isAudio(mime, name)) continue
                        if (out.size >= MAX_FILES) { truncated = true; break }
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val modified = if (c.isNull(4)) 0L else c.getLong(4)
                        val prev = known[id]
                        val track = if (prev != null && prev.size == size && prev.modified == modified) prev
                        else read(context, retriever, tree, id, name, mime.orEmpty(), size, modified)
                        out += track
                        _state.update { it.copy(checked = it.checked + 1, found = out.size) }
                    }
                }
                if (truncated) break
            }
        } finally {
            runCatching { retriever.release() }
        }
        return out to truncated
    }

    /** Baca tag satu berkas. Berkas yang tagnya tidak terbaca tetap masuk (judul dari nama berkas). */
    private fun read(
        context: Context, r: MediaMetadataRetriever, tree: Uri, id: String, name: String, mime: String, size: Long, modified: Long,
    ): LocalTrack {
        val base = LocalTrack(
            id = id, title = LocalLibraryModel.titleFromFileName(name), mime = mime, fileName = name, size = size, modified = modified,
        )
        return try {
            r.setDataSource(context, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            fun meta(key: Int): String? = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val (rate, bits) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0) to
                    (meta(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0)
            } else 0 to 0
            base.copy(
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: base.title,
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty(),
                trackNo = LocalLibraryModel.parseTrackNo(meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                discNo = LocalLibraryModel.parseTrackNo(meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                mime = meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: mime,
                bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                sampleRate = rate.coerceIn(0, 1_536_000),
                bitsPerSample = bits.coerceIn(0, 64),
            )
        } catch (e: RuntimeException) {
            // Berkas rusak / format tidak dikenal dekoder sistem: tetap terdaftar, ExoPlayer mungkin masih bisa memutarnya
            base
        }
    }

    private fun readIndex(context: Context): IndexFile? = runCatching {
        val f = File(context.filesDir, INDEX_FILE)
        if (!f.isFile || f.length() > 64L * 1024 * 1024) return null
        json.decodeFromString(IndexFile.serializer(), f.readText())
    }.getOrNull()

    private fun writeIndex(context: Context, index: IndexFile) {
        val f = File(context.filesDir, INDEX_FILE)
        val tmp = File(context.filesDir, "$INDEX_FILE.tmp")
        tmp.writeText(json.encodeToString(IndexFile.serializer(), index))
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
