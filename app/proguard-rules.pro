# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class cn.lyric.getter.api.data.*{*;}
-keep class cn.lyric.getter.api.API{*;}

# -keep class com.pryvn.audiophile.data.libraries.Music { *; }
# -keep class com.pryvn.audiophile.data.libraries.PlayList { *; }
# -keep class com.pryvn.audiophile.data.libraries.PlayStatus { *; }
# -keep class com.pryvn.audiophile.data.libraries.MusicLibrary { *; }
# -keep class com.pryvn.audiophile.data.libraries.PlayListBean { *; }
# -keep class com.pryvn.audiophile.data.libraries.Folder { *; }
-keepnames class com.pryvn.audiophile.data.libraries.** { *; }

-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** e(...);
    public static *** i(...);
    public static *** v(...);    public static *** println(...);
    public static *** w(...);
    public static *** wtf(...);
}

-assumenosideeffects class java.io.PrintStream {
    public *** println(...);
    public *** print(...);
}

-keep class com.cormor.overscroll.core.OverScrollKt

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pryvn.audiophile.**$$serializer { *; }
-keepclassmembers class com.pryvn.audiophile.** {
    *** Companion;
}
-keepclasseswithmembers class com.pryvn.audiophile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class org.slf4j.** { *; }

# YouTube API data classes
-keep class com.pryvn.audiophile.code.api.** { *; }

# InnerTube client
-keep class com.pryvn.audiophile.code.api.InnerTubeClient { *; }

# WebView JavaScript interface
-keepclassmembers class com.pryvn.audiophile.ui.pages.ytmusic.YTMusicLoginScreen$WebAppInterface {
    public *;
}

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }