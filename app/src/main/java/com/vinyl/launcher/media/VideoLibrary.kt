package com.vinyl.launcher.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Daftar video di folder yang dipilih pengguna lewat Storage Access Framework. Hanya folder itu (tingkat teratas) yang
 * dibaca, dengan izin baca persisten yang diberikan pengguna saat memilih folder; launcher tidak punya izin penyimpanan.
 * Isi berkas tidak dibaca di sini (hanya nama & jenis); video diputar langsung dari URI-nya.
 */
object VideoLibrary {
    const val MAX_FILES = 2000
    private const val RESCAN_MS = 60_000L

    data class State(
        val folderUri: String? = null,
        val files: List<VideoFile> = emptyList(),
        val scanning: Boolean = false,
        val error: String? = null,
        val scannedAt: Long = 0L,
        val truncated: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Pindai ulang folder. Tanpa [force], hasil yang berumur kurang dari satu menit dipakai ulang. */
    suspend fun scan(context: Context, folderUri: String?, force: Boolean = false) {
        if (folderUri == null) {
            _state.value = State()
            return
        }
        mutex.withLock {
            val s = _state.value
            if (!force && s.folderUri == folderUri && s.error == null && System.currentTimeMillis() - s.scannedAt < RESCAN_MS) return
            _state.update { it.copy(folderUri = folderUri, scanning = true) }
            val app = context.applicationContext
            val result = withContext(Dispatchers.IO) { runCatching { list(app, folderUri.toUri()) } }
            _state.value = result.fold(
                onSuccess = { (files, truncated) -> State(folderUri, files, false, null, System.currentTimeMillis(), truncated) },
                onFailure = { e ->
                    State(
                        folderUri, emptyList(), false,
                        if (e is SecurityException) "Izin folder sudah tidak berlaku. Pilih folder lagi." else "Folder tidak bisa dibaca.",
                        System.currentTimeMillis(),
                    )
                },
            )
        }
    }

    private fun list(context: Context, tree: Uri): Pair<List<VideoFile>, Boolean> {
        if (!hasPermission(context, tree)) throw SecurityException("no persisted permission")
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<VideoFile>()
        var truncated = false
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = context.contentResolver.query(children, projection, null, null, null) ?: throw IllegalStateException("no cursor")
        cursor.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2) ?: continue
                if (!mime.startsWith("video/")) continue
                if (out.size >= MAX_FILES) { truncated = true; break }
                out += VideoFile(c.getString(0) ?: continue, c.getString(1).orEmpty())
            }
        }
        return out.sortedBy { it.name.lowercase() } to truncated
    }

    fun hasPermission(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }

    fun uriOf(folderUri: String, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(folderUri.toUri(), documentId)

    /** Simpan izin baca folder agar tetap berlaku setelah HP restart. */
    fun takePermission(context: Context, tree: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    /**
     * Lepaskan izin folder lama kecuali folder yang sama masih dipakai fitur lain ([stillUsed]: folder video & folder
     * musik pemutar bawaan bisa sama).
     */
    fun releaseIfUnused(context: Context, folderUri: String, stillUsed: Collection<String?>) {
        if (folderUri !in stillUsed) releasePermission(context, folderUri)
    }

    /** Lepaskan izin folder lama (saat ganti/lepas folder) agar launcher tidak menyimpan akses yang tak dipakai. */
    fun releasePermission(context: Context, folderUri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(folderUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** Nama folder untuk ditampilkan. Panggil di thread latar. */
    fun folderName(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull().orEmpty()
}
