# Los Compadres TV — keep WebView / JS bridges if added later
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
