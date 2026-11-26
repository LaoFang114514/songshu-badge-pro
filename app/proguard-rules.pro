# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# WebView 与 JS 交互（如有 JS 接口需保留）
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
