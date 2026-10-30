import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.vinyl.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vinyl.launcher"
        minSdk = 29
        targetSdk = 36
        // Nomor build CI dipakai sebagai versionCode agar setiap rilis bisa dipasang sebagai pembaruan
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + run
        versionName = "1.0.$run"
    }

    // Tanda tangan TETAP agar setiap build bisa dipasang sebagai pembaruan (kunci acak per build ditolak Android).
    //
    // Urutan pilihan kunci:
    //  1. Kunci rahasia dari variabel lingkungan (di CI: GitHub Secrets) — DIREKOMENDASIKAN:
    //       VINYL_KEYSTORE_FILE, VINYL_KEYSTORE_PASSWORD, VINYL_KEY_ALIAS (opsional, default "vinyl"),
    //       VINYL_KEY_PASSWORD (opsional, default sama dengan password keystore)
    //  2. Kunci lama di repo (app/vinyl.keystore) — HANYA agar pemasangan yang ada tetap bisa diperbarui.
    //     Kunci ini sudah publik (ada di riwayat repo), jadi siapa pun bisa membuat APK yang dianggap
    //     "pembaruan sah". Ganti ke opsi 1 begitu siap; langkahnya ada di SECURITY.md.
    //  3. Tidak ada keduanya: build debug memakai kunci debug bawaan Android; build release tidak ditandatangani.
    val envKeystore = System.getenv("VINYL_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }
    val legacyKeystore = file("vinyl.keystore").takeIf { it.isFile }
    val signingMode = when {
        envKeystore != null -> "env"
        legacyKeystore != null -> "legacy"
        else -> "none"
    }
    if (signingMode == "legacy") {
        logger.warn(
            "PERINGATAN KEAMANAN: build ditandatangani dengan kunci lama yang ada di repo (publik). " +
                "Setel VINYL_KEYSTORE_* untuk memakai kunci rahasia; lihat SECURITY.md."
        )
    }
    if (signingMode == "none") {
        logger.warn("PERINGATAN: tidak ada keystore; APK debug memakai kunci debug bawaan, release tidak ditandatangani.")
    }

    signingConfigs {
        if (signingMode != "none") {
            create("vinyl") {
                if (signingMode == "env") {
                    storeFile = envKeystore
                    storePassword = System.getenv("VINYL_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("VINYL_KEY_ALIAS") ?: "vinyl"
                    keyPassword = System.getenv("VINYL_KEY_PASSWORD") ?: System.getenv("VINYL_KEYSTORE_PASSWORD")
                } else {
                    storeFile = legacyKeystore
                    storePassword = "vinyl-launcher"
                    keyAlias = "vinyl"
                    keyPassword = "vinyl-launcher"
                }
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("vinyl")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("vinyl")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // IShellService.aidl: layanan Shizuku satu-perintah untuk Akses lanjutan
        aidl = true
    }

    // Robolectric (tangkapan layar Compose di JVM) butuh resource Android saat tes
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Pesan kegagalan tes tampil di log CI (tanpa ini hanya "AssertionError at baris N")
        unitTests.all { test ->
            test.testLogging {
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                events("failed")
            }
        }
    }

    lint {
        // Laporan HTML + teks selalu dibuat (CI mengunggahnya). Temuan berseverity error menggagalkan
        // tugas lint; peringatan hanya dilaporkan.
        abortOnError = true
        checkDependencies = false
        htmlReport = true
        textReport = true
    }
}

/*
 * Font (Inter, Instrument Serif, JetBrains Mono, VT323 — lisensi OFL; Permanent Marker — Apache 2.0) diunduh otomatis
 * dari repositori google/fonts saat build pertama, lalu disimpan di res/font (tidak ikut di-commit).
 *
 * Rantai pasok: alamat unduhan dikunci ke SATU commit (bukan cabang `main` yang terus berubah) dan isi setiap berkas
 * diperiksa dengan SHA-256 sebelum dipakai. Berkas yang sudah ada tetapi tidak cocok diunduh ulang; unduhan yang tidak
 * cocok dibuang dan build GAGAL (bukan memakai font yang tidak dikenal). Untuk memperbarui font: ganti `fontsCommit` dan
 * semua hash sekaligus (hash dihitung dari berkas pada commit itu, mis. `git show <commit>:<jalur> | sha256sum`).
 */
val fetchFonts by tasks.registering {
    val fontDir = file("src/main/res/font")
    val fontsCommit = "9862abc066c9b83bf84c3e8ed1fc2d8d48f9bbf0"
    // Nama berkas di res/font -> (jalur di repositori google/fonts, SHA-256 isi berkas)
    val fonts = mapOf(
        "inter.ttf" to ("ofl/inter/Inter%5Bopsz,wght%5D.ttf" to
            "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031"),
        "instrument_serif.ttf" to ("ofl/instrumentserif/InstrumentSerif-Regular.ttf" to
            "498efd461f6ddfcb7a111bf9a565709d2085d48201d501ead960d93e84ffbb88"),
        "jetbrains_mono.ttf" to ("ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf" to
            "48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda"),
        // Teks layar kamera VHS (OSD) — OFL
        "vt323.ttf" to ("ofl/vt323/VT323-Regular.ttf" to
            "cf4de751ada78ceac033dbe16a687742939995b77bc2a052ae17a4957958594d"),
        // Tulisan spidol di label kaset — Apache 2.0
        "permanent_marker.ttf" to ("apache/permanentmarker/PermanentMarker-Regular.ttf" to
            "28f82c8a7943cb8e9d599f8554da1d4fc75dbcf69b9885ad6c0611d20c6946c5"),
    )
    // Tugas dijalankan ulang bila commit atau hash berubah (berkas lama di res/font tidak dianggap terbaru)
    inputs.property("fontsPin", fontsCommit + fonts.entries.joinToString(";") { (name, spec) -> "$name=${spec.second}" })
    outputs.dir(fontDir)
    doLast {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fontDir.mkdirs()
        fonts.forEach { (name, spec) ->
            val (path, expected) = spec
            val target = File(fontDir, name)
            if (target.exists() && sha256(target) == expected) return@forEach
            val url = "https://github.com/google/fonts/raw/$fontsCommit/$path"
            println("Mengunduh font $name")
            target.delete()
            URI(url).toURL().openStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            val actual = sha256(target)
            if (actual != expected) {
                target.delete()
                throw GradleException("Berkas $name dari $url tidak cocok: SHA-256 $actual, seharusnya $expected")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchFonts) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // Tangkapan layar tanpa HP: Robolectric menggambar Compose di JVM, Roborazzi menyimpannya sebagai PNG
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
