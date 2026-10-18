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

    @Test fun `akses lanjutan opsional dan shizuku hanya untuk paket ini`() {
        val dump = PrivacyInfo.permissions.first { it.id == "android.permission.DUMP" }
        assertTrue("DUMP harus opsional", dump.optional)
        // Penyedia Shizuku hanya boleh dipanggil sistem/Shizuku
        val provider = Regex("""<provider[^>]*ShizukuProvider[^>]*/>""", RegexOption.DOT_MATCHES_ALL).find(manifest)?.value.orEmpty()
        assertTrue(provider.contains("""android:permission="android.permission.INTERACT_ACROSS_USERS_FULL""""))
        assertTrue(PrivacyInfo.permissions.any { it.id == PrivacyInfo.ID_SHIZUKU })
        // Perintah tetap di layanan Shizuku menunjuk applicationId aplikasi ini
        fun read(vararg paths: String) = paths.map(::File).first { it.isFile }.readText()
        val gradle = read("build.gradle.kts", "app/build.gradle.kts")
        val appId = Regex("""applicationId\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]
        val shell = read(
            "src/main/java/com/vinyl/launcher/advanced/ShellService.kt",
            "app/src/main/java/com/vinyl/launcher/advanced/ShellService.kt",
        )
        assertTrue("APP_ID harus $appId", shell.contains("""const val APP_ID = "$appId""""))
        val aidl = read(
            "src/main/aidl/com/vinyl/launcher/advanced/IShellService.aidl",
            "app/src/main/aidl/com/vinyl/launcher/advanced/IShellService.aidl",
        )
        // Tidak ada metode yang menerima perintah/argumen bebas
        assertFalse("AIDL tidak boleh menerima String", aidl.contains("String"))
    }
}
