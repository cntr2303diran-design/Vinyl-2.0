# Aturan R8 untuk build release (isMinifyEnabled + isShrinkResources). Prinsip: pertahankan hanya yang dicari lewat
# refleksi atau lewat nama; sisanya dikecilkan dan diacak. Pustaka (Compose, Media3, coroutines, kotlinx.serialization,
# DataStore) membawa aturan konsumennya sendiri; di sini hanya pengaman untuk kode kita. Dibuktikan oleh uji asap
# emulator di CI (.github/scripts/smoke_test.sh), bukan sekadar "build berhasil".

# Profil dasar (Baseline Profile): src/main/baseline-prof.txt berisi satu aturan wildcard untuk seluruh paket
# com.vinyl.launcher (berkasnya sengaja tanpa komentar agar semua pengurai profil aman). Profil pustaka digabung otomatis
# oleh Android Gradle Plugin. Profil statis ini BUKAN hasil rekaman perangkat nyata. APK di luar toko aplikasi menerima
# profil lewat ProfileInstaller saat dijalankan; kompilasinya dikerjakan Android ketika perangkat idle/mengisi daya.

# Nomor baris tetap ada di jejak tumpukan; dibaca ulang dengan mapping.txt yang diunggah CI sebagai artifact
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: pertahankan serializer model pengaturan
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.vinyl.launcher.data.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Serializer model Audio Console (EqValues, dsp) di paket audio
-keep,includedescriptorclasses class com.vinyl.launcher.audio.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.audio.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Indeks pustaka musik lokal (LocalTrack, urutan) disimpan sebagai JSON
-keep,includedescriptorclasses class com.vinyl.launcher.player.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Peringkat pemakaian aplikasi (UsageRanking) di paket drawer juga disimpan sebagai JSON
-keep,includedescriptorclasses class com.vinyl.launcher.drawer.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.drawer.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# DataStore (preferences) memakai protobuf-lite; kolom pesan dibaca lewat refleksi.
# Pustaka sudah membawa aturan ini; diulang di sini agar tidak bergantung pada versi pustaka.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Akses lanjutan (Shizuku): layanan dibuat oleh proses Shizuku lewat refleksi (nama kelas dikirim sebagai teks dan
# konstruktornya dicari), dan antarmuka AIDL dipanggil lewat Binder. Nama dan anggotanya dipertahankan apa adanya.
-keep class com.vinyl.launcher.advanced.ShellService { *; }
-keep class com.vinyl.launcher.advanced.IShellService { *; }
-keep class com.vinyl.launcher.advanced.IShellService$Stub { *; }
-keep class com.vinyl.launcher.advanced.IShellService$Stub$Proxy { *; }
