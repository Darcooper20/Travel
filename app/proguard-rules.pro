# R8/ProGuard rules for the release build.
#
# The debug build is not minified, so every rule here is only exercised by
# `assembleRelease`. CI runs that on every push precisely so a missing keep
# rule shows up as a build failure rather than as a crash on someone's phone.
# Anything reached by reflection or by generated code needs a rule.

# ---- Attributes needed by Retrofit, kotlinx.serialization and Room --------
# Signature carries generic types (Retrofit reads Call<List<Foo>> at runtime);
# without it every parameterised return type collapses and Retrofit throws.
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# ---- kotlinx.serialization ----------------------------------------------
# Serializers are generated as nested $$serializer classes and looked up by name.
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
# @Serializable data classes are constructed reflectively from their fields.
-keepclassmembers @kotlinx.serialization.Serializable class com.travelbenefits.app.** {
    <fields>;
    <init>(...);
}

# ---- Enums ---------------------------------------------------------------
# Room TypeConverters and kotlinx.serialization both resolve enum constants by
# name. Renaming them turns a stored "MARRIOTT_BONVOY" into an unparseable value.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# ---- Room ----------------------------------------------------------------
# Generated _Impl classes are instantiated by name from the database builder.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.travelbenefits.app.data.local.**_Impl { *; }
-keep @androidx.room.Entity class com.travelbenefits.app.** { *; }
-dontwarn androidx.room.paging.**

# ---- Retrofit / OkHttp ---------------------------------------------------
# Service interfaces are implemented by a runtime proxy over their annotations.
-keep,allowobfuscation interface com.travelbenefits.app.data.remote.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- Compile-only annotations -------------------------------------------
# Tink (pulled in by androidx.security-crypto for EncryptedSharedPreferences)
# is compiled against Error Prone's annotations, which have CLASS retention and
# are deliberately not packaged at runtime. R8 reports them as missing classes
# and fails the build; they are genuinely unused at runtime, so silence them
# rather than shipping them. Found by the assembleRelease step in CI.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.concurrent.**

# ---- AppAuth (Gmail OAuth) ----------------------------------------------
# Parses its own JSON responses reflectively and is reached via an intent filter.
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# ---- Plaid Link ----------------------------------------------------------
-keep class com.plaid.** { *; }
-dontwarn com.plaid.**

# ---- WorkManager / Hilt --------------------------------------------------
# Workers are instantiated by class name by WorkManager.
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class com.travelbenefits.app.work.** { *; }

# ---- App widget ----------------------------------------------------------
# Named in AndroidManifest.xml and instantiated by the system.
-keep class com.travelbenefits.app.widget.** { *; }
