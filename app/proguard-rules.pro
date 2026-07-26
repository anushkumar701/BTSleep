# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class com.smartbluetoothsleeptracker.data.db.** { <fields>; }
-keep interface com.smartbluetoothsleeptracker.data.db.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Generic Android
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
-keepattributes SourceFile,LineNumberTable
