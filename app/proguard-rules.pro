# ==============================================================================
# SAIFI NATIONAL LUBRICANTS (SNL) — Production ProGuard / R8 Optimization Rules
# ==============================================================================

# Optimization & stack trace preservation
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# 1. Keep all SNL Lubricants Firestore Data Models & Repository Classes
# Required for Firebase Firestore DocumentSnapshot.toObject() reflection
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** {
    *;
    public <init>();
}
-keep class com.example.data.seed.** { *; }
-keep class com.example.util.** { *; }

# 2. Firebase Firestore, Auth, AI Logic & App Check Rules
-keep class com.google.firebase.firestore.** { *; }
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.auth.** { *; }

# 3. Jetpack Credential Manager & Google Identity Services (Google Sign-In)
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# 4. Kotlin Coroutines & Reflection Metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.flow.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 5. OkHttp3 & Okio (Google Sheets CSV Sync & Meta WhatsApp Cloud API)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
