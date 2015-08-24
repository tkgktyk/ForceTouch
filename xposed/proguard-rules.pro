# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/tkgktyk/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
-keep class com.google.**
-dontwarn com.google.**

# for ButterKnife
-keep class butterknife.** { *; }
-dontwarn butterknife.internal.**
-keep class **$$ViewBinder { *; }

-keepclasseswithmembernames class * {
    @butterknife.* <fields>;
}

-keepclasseswithmembernames class * {
    @butterknife.* <methods>;
}

# Xposed
-keep class jp.tkgktyk.xposed.forcetouchdetector.Mod

# for version name
-keep class jp.tkgktyk.lib.BaseApplication

# for gson
-keep class jp.tkgktyk.xposed.forcetouchdetector.app.util.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
