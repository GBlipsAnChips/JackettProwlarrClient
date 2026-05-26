# Keep WebView-related classes
-keep public class * extends android.webkit.WebViewClient { *; }
-keep public class * extends android.webkit.WebChromeClient { *; }
-keep class android.webkit.** { *; }

# Keep our engine classes
-keep class com.aggregatorx.app.engine.** { *; }
-keep interface com.aggregatorx.app.engine.** { *; }
-keep class com.aggregatorx.app.ui.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Keep model classes
-keep class com.aggregatorx.app.data.model.** { *; }

# Keep coroutine suspending functions
-keepclasseswithmembernames class * {
    *** *(...) throws <any>;
}

# Don't obfuscate
-dontobfuscate
