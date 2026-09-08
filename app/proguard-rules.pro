# Add project specific ProGuard rules here.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.travelbenefits.app.**$$serializer { *; }
-keepclassmembers class com.travelbenefits.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.travelbenefits.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# AppAuth
-keep class net.openid.appauth.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
