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

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.view.WindowManager;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int FILE_PICKER_CODE = 101;
    private static final int VOICE_RECORD_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent screenshots/screen recording
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

        // Modern cache handling (AppCache removed in Android)
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // JavaScript bridge
        webView.addJavascriptInterface(new AuthBridge(), "AndroidAuth");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("jambgenius://")) {
                    handleDeepLink(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                handleWebError(error);
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("https://jambgenius.vercel.app");
    }

    // JavaScript Interface
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
            checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, FILE_PICKER_CODE);
        }

        @JavascriptInterface
        public void requestVoiceRecord() {
            checkPermission(android.Manifest.permission.RECORD_AUDIO, VOICE_RECORD_CODE);
        }

        @JavascriptInterface
        public boolean isInternetAvailable() {
            return hasInternetConnection();
        }
    }

    private void checkPermission(String permission, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            } else {
                onPermissionGranted(requestCode);
            }
        } else {
            onPermissionGranted(requestCode);
        }
    }

    private void onPermissionGranted(int requestCode) {
        if (requestCode == FILE_PICKER_CODE) {
            webView.evaluateJavascript("window.onImagePermissionGranted && window.onImagePermissionGranted()", null);
        } else if (requestCode == VOICE_RECORD_CODE) {
            webView.evaluateJavascript("window.onVoicePermissionGranted && window.onVoicePermissionGranted()", null);
        }
    }

    private void handleDeepLink(String url) {
        Uri uri = Uri.parse(url);
        String token = uri.getQueryParameter("token");
        if (token != null) {
            webView.evaluateJavascript("window.handleAuthCallback('" + token + "')", null);
        }
    }

    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo net = cm.getActiveNetworkInfo();
            return net != null && net.isConnected();
        }
        return false;
    }

    private void handleWebError(WebResourceError error) {
        if (error == null) return;

        int errorCode = error.getErrorCode();

        // Only handle connection errors
        if (errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
            errorCode == WebViewClient.ERROR_CONNECT ||
            errorCode == WebViewClient.ERROR_TIMEOUT ||
            errorCode == WebViewClient.ERROR_IO) {

            String html = "<html><body style='display:flex;justify-content:center;align-items:center;height:100vh;background:#fafafa;font-family:sans-serif;text-align:center;'>"
                    + "<div><h2>No Internet</h2><p>Please check your connection.</p>"
                    + "<button onclick='location.reload()' style='padding:10px 20px;background:#4a6cf7;color:#fff;border:none;border-radius:6px;'>Retry</button>"
                    + "</div></body></html>";

            webView.loadData(html, "text/html", "UTF-8");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
