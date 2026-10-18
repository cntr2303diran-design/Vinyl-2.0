# kotlinx.serialization: pertahankan serializer model pengaturan
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.vinyl.launcher.data.**$$serializer { *; }
-keepclassmembers class com.vinyl.launcher.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
