-keepclassmembers class * {
    public static void main(java.lang.String[]);
}

-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }
