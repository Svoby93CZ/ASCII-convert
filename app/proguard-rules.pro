# The app has no reflection-based code of its own. AndroidX, Compose, CameraX, DataStore and
# kotlinx.serialization (type-safe navigation routes) ship their own consumer R8 rules.

# Keep line numbers so crash reports from release builds remain readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
