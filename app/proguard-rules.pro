# kotlinx.serialization: pertahankan serializer model pengaturan
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.vinyl.launcher.data.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Serializer model Audio Console (EqValues) di paket audio
-keep,includedescriptorclasses class com.vinyl.launcher.audio.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.audio.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Layanan Shizuku dibuat lewat refleksi di proses shell: konstruktornya harus tetap ada
-keep class com.vinyl.launcher.advanced.ShellService { <init>(...); }

# Indeks pustaka musik lokal (LocalTrack, urutan) disimpan sebagai JSON
-keep,includedescriptorclasses class com.vinyl.launcher.player.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
