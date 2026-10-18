package com.vinyl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Menjaga dokumentasi izin tetap sinkron dengan AndroidManifest.xml. */
class PrivacyInfoTest {

    private val manifest: String by lazy {
        // Gradle menjalankan tes dengan folder kerja = modul app/; cari juga dari root repo.
        val f = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml tidak ditemukan dari ${File(".").absolutePath}")
        f.readText()
    }

    private val manifestPermissions: Set<String> by lazy {
        Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
    }

    @Test fun `setiap izin di manifest punya alasan tertulis`() {
        val documented = PrivacyInfo.permissions.map { it.id }.toSet()
        val missing = manifestPermissions - documented
        assertTrue("Izin tanpa alasan di PrivacyInfo: $missing", missing.isEmpty())
    }

    @Test fun `tidak ada alasan untuk izin yang sudah tidak ada di manifest`() {
        val stale = PrivacyInfo.permissions.map { it.id }.filter { it.startsWith("android.permission.") } - manifestPermissions
        assertTrue("Entri usang di PrivacyInfo: $stale", stale.isEmpty())
    }

    @Test fun `alasan tidak kosong dan id unik`() {
        PrivacyInfo.permissions.forEach {
            assertTrue("alasan kosong: ${it.id}", it.reason.length >= 20)
            assertTrue("nama kosong: ${it.id}", it.name.isNotBlank())
        }
        assertEquals(PrivacyInfo.permissions.size, PrivacyInfo.permissions.map { it.id }.toSet().size)
    }

    @Test fun `layanan khusus di manifest terdokumentasi`() {
        val ids = PrivacyInfo.permissions.map { it.id }
        assertTrue(manifest.contains("BIND_NOTIFICATION_LISTENER_SERVICE") == ids.contains(PrivacyInfo.ID_NOTIFICATION_LISTENER))
        assertTrue(manifest.contains("BIND_ACCESSIBILITY_SERVICE") == ids.contains(PrivacyInfo.ID_ACCESSIBILITY))
    }

    @Test fun `cadangan otomatis dimatikan dan receiver boot tidak diekspor`() {
        assertTrue("allowBackup harus false", manifest.contains("""android:allowBackup="false""""))
        val boot = Regex("""<receiver[^>]*BootReceiver[^>]*>""", RegexOption.DOT_MATCHES_ALL).find(manifest)?.value.orEmpty()
        assertTrue("BootReceiver harus exported=false", boot.contains("""android:exported="false""""))
        assertFalse("BootReceiver tidak boleh exported=true", boot.contains("""android:exported="true""""))
    }

    @Test fun `lalu lintas jaringan hanya https tanpa cleartext`() {
        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
    }
}
