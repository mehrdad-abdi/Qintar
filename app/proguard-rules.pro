# Keep rules are added as needed.

# Type-safe Navigation looks up enum route arguments (e.g. ReaderEntry) by their fully qualified
# class name at run time. R8 renamed them, and the release build crashed on its first screen.
-keep class io.github.mehrdad_abdi.qintar.ui.navigation.** { *; }

# DataStore Preferences reads its protobuf fields (value_, …) by reflection. Without this, R8 in
# full mode strips them and the first preferences write crashes the release build.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
