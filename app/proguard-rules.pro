# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.dailynewsletter.data.remote.** { *; }
-keepclassmembers class com.dailynewsletter.data.remote.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod
