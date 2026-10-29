# TraceDay R8 rules

# kotlinx.serialization: keep generated serializers of app DTOs
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.traceday.**$$serializer { *; }
-keepclasseswithmembers class app.traceday.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: keep generic signatures and suspended API methods
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Google Play services (legacy GoogleSignIn uses reflection)
-keep class com.google.android.gms.common.api.GoogleSignInOptions { *; }
-keep class com.google.android.gms.auth.api.signin.GoogleSignInAccount { *; }
