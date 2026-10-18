package com.vinyl.launcher.data

import com.vinyl.launcher.media.VideoMatch
import kotlinx.serialization.Serializable

/** Tautan manual: lagu/album dengan [key] (lihat VideoMatch.linkKey) → video [documentId]. [label] hanya untuk tampilan. */
@Serializable
data class VideoLink(val key: String, val label: String, val documentId: String, val fileName: String)

/**
 * Video milik pengguna di layar TV. Folder dipilih lewat Storage Access Framework (izin baca persisten untuk folder itu
 * saja, tanpa izin penyimpanan). Mati secara default.
 */
@Serializable
data class VideoSettings(
    val enabled: Boolean = false,
    /** URI pohon folder (content://...). Bukan path berkas; tidak ikut berkas cadangan (lihat SettingsBackup). */
    val folderUri: String? = null,
    val folderName: String = "",
    val links: List<VideoLink> = emptyList(),
)

object VideoRules {
    const val MAX_LINKS = 300
    const val MAX_TEXT = 300
    const val MAX_ID = 1000

    fun linkMap(v: VideoSettings): Map<String, String> = v.links.associate { it.key to it.documentId }

    fun link(v: VideoSettings, artist: String, album: String, title: String, documentId: String, fileName: String): VideoSettings {
        val key = VideoMatch.linkKey(artist, album, title) ?: return v
        val label = listOf(artist, album.ifBlank { title }).filter { it.isNotBlank() }.joinToString(" – ").take(MAX_TEXT)
        val others = v.links.filterNot { it.key == key }
        if (others.size >= MAX_LINKS) return v
        return v.copy(links = others + VideoLink(key, label, documentId.take(MAX_ID), fileName.take(MAX_TEXT)))
    }

    fun unlink(v: VideoSettings, key: String): VideoSettings = v.copy(links = v.links.filterNot { it.key == key })

    /** Ganti folder: tautan lama menunjuk dokumen folder lama, jadi dihapus. */
    fun changeFolder(v: VideoSettings, uri: String?, name: String): VideoSettings =
        v.copy(folderUri = uri, folderName = name.take(MAX_TEXT), links = if (uri == v.folderUri) v.links else emptyList())

    fun sanitize(v: VideoSettings): VideoSettings {
        val uri = v.folderUri?.takeIf { it.startsWith("content://") && it.length <= MAX_ID }
        return v.copy(
            folderUri = uri,
            folderName = if (uri == null) "" else v.folderName.filterNot { it.isISOControl() }.take(MAX_TEXT),
            links = if (uri == null) emptyList() else v.links
                .filter { it.key.isNotBlank() && it.key.length <= MAX_TEXT && it.documentId.isNotBlank() && it.documentId.length <= MAX_ID }
                .map { it.copy(label = it.label.filterNot(Char::isISOControl).take(MAX_TEXT), fileName = it.fileName.filterNot(Char::isISOControl).take(MAX_TEXT)) }
                .distinctBy { it.key }
                .take(MAX_LINKS),
        )
    }
}
