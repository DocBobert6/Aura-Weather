# Aura Weather R8 rules (3.5). The default
# proguard-android-optimize.txt already keeps manifest entry points
# (activities, services, receivers). These rules cover the
# reflection-heavy libraries: Moshi codegen, Room, Retrofit,
# Glance, WorkManager.

# Keep line numbers for readable stack traces.
-keepattributes SourceFile,LineNumberTable

# Enums: keep values()/valueOf() (used by Moshi, Room, and the app).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Moshi codegen: the generated *JsonAdapter classes are looked up by
# name at runtime, and the @JsonClass models are instantiated by them.
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
}
-keep class **JsonAdapter {
    <init>(...);
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Room: keep the database, entities, and DAOs (referenced by
# generated code).
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Retrofit: keep service interface methods and their annotations, and
# the generic signatures R8 needs to resolve response types.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# WorkManager: keep the workers (instantiated by name).
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# Glance: keep the widget receiver and the widget itself.
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
