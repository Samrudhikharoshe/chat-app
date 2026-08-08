# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Gson
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Socket.IO
-keep class io.socket.** { *; }
-keep class com.chatapp.data.** { *; }
-dontwarn okio.**
-dontwarn org.conscrypt.**
