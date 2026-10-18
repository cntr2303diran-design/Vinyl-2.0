package com.vinyl.launcher.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Satu hasil pencarian kontak. Hanya nama dan penunjuk ke kontak; nomor/surel tidak dibaca. */
data class ContactHit(val id: Long, val lookupKey: String, val name: String)

/**
 * Pencarian kontak untuk app drawer. Hanya berjalan bila pengguna menyalakannya DAN izin READ_CONTACTS diberikan.
 * Hasil hanya ditampilkan; tidak disimpan dan tidak dikirim.
 */
object ContactsRepository {
    const val MIN_QUERY = 2

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun search(context: Context, query: String, limit: Int = 5): List<ContactHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < MIN_QUERY || limit <= 0 || !hasPermission(context)) return@withContext emptyList()
        try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            )
            val hits = ArrayList<ContactHit>(limit)
            context.contentResolver.query(uri, projection, null, null, ContactsContract.Contacts.SORT_KEY_PRIMARY)?.use { c ->
                val iId = c.getColumnIndex(ContactsContract.Contacts._ID)
                val iKey = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val iName = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                if (iId < 0 || iKey < 0 || iName < 0) return@use
                while (hits.size < limit && c.moveToNext()) {
                    val name = c.getString(iName)?.takeIf { it.isNotBlank() } ?: continue
                    val key = c.getString(iKey) ?: continue
                    hits += ContactHit(c.getLong(iId), key, name)
                }
            }
            hits
        } catch (e: SecurityException) {
            emptyList()      // izin dicabut di tengah jalan
        } catch (e: RuntimeException) {
            emptyList()      // penyedia kontak tidak tersedia / kursor rusak
        }
    }

    fun open(context: Context, hit: ContactHit) {
        val uri = ContactsContract.Contacts.getLookupUri(hit.id, hit.lookupKey)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
