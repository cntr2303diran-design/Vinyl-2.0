import java.net.URI

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
 * dari repositori google/fonts saat build pertama, lalu disimpan di res/font.
 */
val fetchFonts by tasks.registering {
    val fontDir = file("src/main/res/font")
    outputs.dir(fontDir)
    doLast {
        val base = "https://github.com/google/fonts/raw/main/ofl"
        val apache = "https://github.com/google/fonts/raw/main/apache"
        val fonts = mapOf(
            "inter.ttf" to "$base/inter/Inter%5Bopsz,wght%5D.ttf",
            "instrument_serif.ttf" to "$base/instrumentserif/InstrumentSerif-Regular.ttf",
            "jetbrains_mono.ttf" to "$base/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf",
            // Teks layar kamera VHS (OSD) — OFL
            "vt323.ttf" to "$base/vt323/VT323-Regular.ttf",
            // Tulisan spidol di label kaset — Apache 2.0
            "permanent_marker.ttf" to "$apache/permanentmarker/PermanentMarker-Regular.ttf",
        )
        fontDir.mkdirs()
        fonts.forEach { (name, url) ->
            val target = File(fontDir, name)
            if (!target.exists() || target.length() < 10_000) {
                println("Mengunduh font $name")
                URI(url).toURL().openStream().use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                // Pastikan yang terunduh benar-benar berkas font (bukan halaman galat HTML)
                val head = target.inputStream().use { it.readNBytes(4) }
                val ok = head.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                    String(head, Charsets.ISO_8859_1) in setOf("OTTO", "true", "ttcf")
                if (!ok) {
                    target.delete()
                    throw GradleException("Berkas $name bukan font yang valid (diunduh dari $url)")
                }
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
