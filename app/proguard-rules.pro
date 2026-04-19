# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.parc.fitnesstimer.**$$serializer { *; }
-keepclassmembers class com.parc.fitnesstimer.** {
    *** Companion;
}
-keepclasseswithmembers class com.parc.fitnesstimer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used in serialization
-keep class com.parc.fitnesstimer.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep enum names
-keepclassmembers enum * { *; }
