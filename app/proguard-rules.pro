# R8 rules for the release (and therefore benchmark) build.
#
# Most libraries ship their own consumer rules inside the AAR (Mapbox Maps/Navigation/Search,
# ML Kit, Hilt/Dagger, Room, WorkManager, CameraX, Compose) — only what they can't know about
# our code, or what was seen failing on the tablet, belongs here.

# Readable stack traces from the tablet (logcat) without needing mapping.txt at hand.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Our enums: their names are persisted / sent over the wire --------------------------------
# DataStore stores LauncherStyle / DockPosition / WidgetLayoutTemplate .name, Room 2.6 stores
# enum columns (NoteCategory, …) as the constant name, and GroqClient sends NoteCategory names
# to the LLM. Obfuscated names would silently reset settings and break the voice-notes DB.
-keepclassmembers,allowoptimization enum com.example.carlauncher.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Room ---------------------------------------------------------------------------------------
# Room's generated *_Impl classes are found by name via reflection (Room.databaseBuilder).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- WorkManager + Hilt workers -----------------------------------------------------------------
# WorkManager's default factory instantiates workers reflectively by class name; HiltWorkerFactory
# goes through generated assisted factories, but keep the constructors for the fallback path.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Mapbox -------------------------------------------------------------------------------------
# The SDKs ship consumer rules; their native (JNI) side looks up Java classes by name, and the
# Search SDK's core is a native library with Java peers. Belt and braces for the JNI peers.
-keep class com.mapbox.bindgen.** { *; }
-keep class com.mapbox.common.** { *; }
-keep class com.mapbox.maps.** { *; }
-keep class com.mapbox.navigator.** { *; }
-keep class com.mapbox.search.internal.bindgen.** { *; }
-dontwarn com.mapbox.**

# --- ML Kit (bundled text recognition) -----------------------------------------------------------
-keep class com.google.mlkit.vision.text.bundled.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled.** { *; }

# Optional deps referenced from library code, absent at runtime.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
