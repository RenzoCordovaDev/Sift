# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the proguardFiles
# setting in build.gradle.kts.
#
# For more details, see:
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers in stack traces for crash reporting
-keepattributes SourceFile,LineNumberTable

# Hilt — keep generated component and module classes
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Room — keep entity and DAO classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# libphonenumber — keep metadata resources
-keep class com.google.i18n.phonenumbers.** { *; }
