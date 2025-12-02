package com.jambgenius.web.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Build;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.graphics.Color;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String TAG = "JambGenius";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_PICKER_CODE = 101;
    private static final int VOICE_RECORD_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Prevent screenshots and screen recording
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        
        // Configure WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("JambGeniusApp/1.0");
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());

        // Add JavaScript interface for auth handling
        webView.addJavascriptInterface(new AuthBridge(), "AndroidAuth");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("jambgenius://")) {
                    handleDeepLink(url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                handleWebError(error);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    webView.evaluateJavascript("javascript:console.log('Page loaded')", null);
                }
                super.onProgressChanged(view, newProgress);
            }
        });

        webView.loadUrl("https://jambgenius.vercel.app");
    }

    private class AuthBridge {
        @JavascriptInterface
        public void openGoogleSignIn() {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth?client_id=YOUR_GOOGLE_CLIENT_ID&redirect_uri=jambgenius://auth/callback&response_type=code&scope=email%20profile"));
            startActivity(intent);
        }

        @JavascriptInterface
        public void setAuthToken(String token) {
            webView.evaluateJavascript("javascript:window.authToken = '" + token + "'", null);
        }

        @JavascriptInterface
        public void requestImagePicker() {
            checkAndRequestPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, 
                android.Manifest.permission.CAMERA, FILE_PICKER_CODE);
        }

        @JavascriptInterface
        public void requestVoiceRecord() {
            checkAndRequestPermission(android.Manifest.permission.RECORD_AUDIO, 
                null, VOICE_RECORD_CODE);
        }

        @JavascriptInterface
        public boolean isInternetAvailable() {
            return hasInternetConnection();
        }
    }

    private void handleDeepLink(String url) {
        Uri uri = Uri.parse(url);
        String token = uri.getQueryParameter("token");
        if (token != null) {
            webView.evaluateJavascript("javascript:window.handleAuthCallback('" + token + "')", null);
        }
    }

    private void checkAndRequestPermission(String permission, String secondaryPermission, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED ||
                (secondaryPermission != null && ContextCompat.checkSelfPermission(this, secondaryPermission) != PackageManager.PERMISSION_GRANTED)) {
                
                String[] permissions = secondaryPermission != null ? 
                    new String[]{permission, secondaryPermission} : new String[]{permission};
                ActivityCompat.requestPermissions(this, permissions, requestCode);
            } else {
                onPermissionGranted(requestCode);
            }
        } else {
            onPermissionGranted(requestCode);
        }
    }

    private void onPermissionGranted(int requestCode) {
        if (requestCode == FILE_PICKER_CODE) {
            webView.evaluateJavascript("javascript:window.onImagePermissionGranted && window.onImagePermissionGranted()", null);
        } else if (requestCode == VOICE_RECORD_CODE) {
            webView.evaluateJavascript("javascript:window.onVoicePermissionGranted && window.onVoicePermissionGranted()", null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            onPermissionGranted(requestCode);
        } else {
            webView.evaluateJavascript("javascript:window.onPermissionDenied && window.onPermissionDenied()", null);
        }
    }

    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    private void handleWebError(WebResourceError error) {
        if (error != null) {
            int errorCode = error.getErrorCode();
            
            // Check if it's a connection error
            if (errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                errorCode == WebViewClient.ERROR_CONNECT ||
                errorCode == WebViewClient.ERROR_TIMEOUT ||
                errorCode == WebViewClient.ERROR_IO ||
                errorCode == WebViewClient.ERROR_UNKNOWN) {
                
                String errorPage = "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 100vh; }" +
                    ".error-container { background: white; border-radius: 12px; padding: 30px; max-width: 400px; box-shadow: 0 10px 40px rgba(0,0,0,0.2); text-align: center; }" +
                    ".error-icon { font-size: 60px; margin-bottom: 20px; }" +
                    "h1 { color: #333; font-size: 24px; margin: 20px 0; }" +
                    "p { color: #666; line-height: 1.6; margin: 15px 0; }" +
                    ".error-code { color: #999; font-size: 12px; margin-top: 20px; }" +
                    ".retry-btn { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; padding: 12px 30px; border-radius: 6px; font-size: 16px; cursor: pointer; margin-top: 20px; }" +
                    ".retry-btn:active { opacity: 0.8; }" +
                    "</style></head><body>" +
                    "<div class='error-container'>" +
                    "<div class='error-icon'>📡</div>" +
                    "<h1>No Internet Connection</h1>" +
                    "<p>Please check your internet connection and try again.</p>" +
                    "<p>Make sure WiFi or mobile data is enabled on your device.</p>" +
                    "<button class='retry-btn' onclick='location.reload()'>Retry</button>" +
                    "<div class='error-code'>Error: " + errorCode + "</div>" +
                    "</div>" +
                    "</body></html>";
                
                webView.loadData(errorPage, "text/html; charset=utf-8", "utf-8");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
