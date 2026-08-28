# Proguard rules for AxilBox

# Room
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Data Models
-keep class com.axilbox.app.model.** { *; }
