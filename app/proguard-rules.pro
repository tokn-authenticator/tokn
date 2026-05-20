-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# SQLCipher (net.zetetic:sqlcipher-android)
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }
-keepclasseswithmembernames class net.zetetic.database.** { native <methods>; }
-dontwarn net.zetetic.database.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
}

# BouncyCastle (used directly in feature:sync JPAKE and feature:backup SCrypt)
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# OTP account model must survive R8 for Room
-keepclassmembers class me.diamondforge.tokn.data.db.entity.OtpAccountEntity { *; }
-keepclassmembers class me.diamondforge.tokn.domain.model.OtpAccount { *; }
-keepnames class me.diamondforge.tokn.domain.model.OtpAlgorithm
-keepnames class me.diamondforge.tokn.domain.model.OtpType
