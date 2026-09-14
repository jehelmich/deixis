# kotlinx.serialization: keep generated serializers for the Home Assistant DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.janhelmich.deixis.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.janhelmich.deixis.**$$serializer { *; }

# Filament / ARCore load native code through JNI; keep what they reflect on.
-keep class com.google.android.filament.** { *; }
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**
