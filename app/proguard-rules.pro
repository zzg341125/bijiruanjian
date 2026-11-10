-keepattributes *Annotation*
-keep class io.github.latticenotes.data.** { *; }
# JNI and reflection entry points must retain their original names.
-keep class io.github.frozencaviar.weaveoffice.** { *; }
-keep class com.google.androidgamesdk.** { *; }
-keepclassmembers class * {
    native <methods>;
}
