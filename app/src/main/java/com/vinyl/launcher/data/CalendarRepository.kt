package com.vinyl.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** Membaca hari-hari yang memiliki acara dari kalender perangkat (izin READ_CALENDAR). */
object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Tanggal-tanggal yang memiliki acara dari awal [from] hingga akhir [to]. */
    suspend fun eventDays(context: Context, from: YearMonth, to: YearMonth): Set<LocalDate> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptySet()
            val zone = ZoneId.systemDefault()
            val begin = from.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = to.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val projection = arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            val days = mutableSetOf<LocalDate>()
            runCatching {
                CalendarContract.Instances.query(context.contentResolver, projection, begin, end)?.use { c ->
                    while (c.moveToNext()) {
                        val allDay = c.getInt(2) == 1
                        // Acara sepanjang hari disimpan dalam UTC
                        val z = if (allDay) ZoneOffset.UTC else zone
                        var d = Instant.ofEpochMilli(c.getLong(0)).atZone(z).toLocalDate()
                        val last = Instant.ofEpochMilli((c.getLong(1) - 1).coerceAtLeast(c.getLong(0)))
                            .atZone(z).toLocalDate()
                        // Tandai setiap hari dari acara multi-hari (dibatasi 62 hari)
                        var guard = 0
                        while (!d.isAfter(last) && guard++ < 62) { days += d; d = d.plusDays(1) }
                    }
                }
            }
            days
        }
}
