package com.jambgenius.web.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout offlineBanner;
    private TextView retryText;
    
    private static final String TAG = "JambGenius";
    private static final String BASE_URL = "https://jambgenius.vercel.app";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_PICKER_CODE = 101;
    private static final int VOICE_RECORD_CODE = 102;
    private static final String PREFS_NAME = "JambGeniusPrefs";
    private static final String KEY_USER_SESSION = "user_session";
    private static final String KEY_CACHED_USER = "cached_user";
    
    private boolean isOffline = false;
    private boolean hasShownOfflinePage = false;
    private boolean isPageLoaded = false;
    private boolean isReceiverRegistered = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_main);
        
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        initViews();
        setupWebView();
        setupSwipeRefresh();
        setupOfflineBanner();
        
        loadWebsite();
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        offlineBanner = findViewById(R.id.offline_banner);
        retryText = findViewById(R.id.retry_text);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("JambGeniusApp/1.1 Android");
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        
        setupModernCaching(settings);
        
        webView.addJavascriptInterface(new AppBridge(), "AndroidApp");
        webView.addJavascriptInterface(new AuthBridge(), "AndroidAuth");

        webView.setWebViewClient(new JambGeniusWebViewClient());
        webView.setWebChromeClient(new JambGeniusWebChromeClient());
        
        setupDownloadListener();
    }

    private void setupModernCaching(WebSettings settings) {
        if (hasInternetConnection()) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
        
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, android.R.color.holo_blue_dark),
            ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        );
        
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (hasInternetConnection()) {
                    webView.reload();
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    showOfflineBanner(true);
                    Toast.makeText(MainActivity.this, "No internet connection", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                swipeRefreshLayout.setEnabled(scrollY == 0);
            }
        });
    }

    private void setupNetworkListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onNetworkAvailable();
                        }
                    });
                }

                @Override
                public void onLost(Network network) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onNetworkLost();
                        }
                    });
                }
            };
            
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }
    }

    private void unregisterNetworkListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                // Already unregistered or not registered
            }
            networkCallback = null;
        }
    }

    private void onNetworkAvailable() {
        isOffline = false;
        showOfflineBanner(false);
        
        if (hasShownOfflinePage && isPageLoaded) {
            Toast.makeText(this, "Back online! Refreshing...", Toast.LENGTH_SHORT).show();
            hasShownOfflinePage = false;
            webView.loadUrl(BASE_URL);
        }
    }

    private void onNetworkLost() {
        isOffline = true;
        showOfflineBanner(true);
    }

    private void setupOfflineBanner() {
        retryText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasInternetConnection()) {
                    showOfflineBanner(false);
                    webView.reload();
                } else {
                    Toast.makeText(MainActivity.this, "Still offline. Please check your connection.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showOfflineBanner(boolean show) {
        offlineBanner.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupDownloadListener() {
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                handleDownload(url, contentDisposition, mimetype);
            }
        });
    }

    private void handleDownload(String url, String contentDisposition, String mimetype) {
        String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
        
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            request.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());
            request.setDescription("Downloading " + filename);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            
            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.enqueue(request);
                Toast.makeText(this, "Downloading " + filename + "...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            // Fallback: open in browser
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Cannot download file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupDownloadReceiver() {
        if (isReceiverRegistered) return;
        
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    Toast.makeText(context, "Download complete!", Toast.LENGTH_SHORT).show();
                }
            }
        };
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
        isReceiverRegistered = true;
    }

    private void unregisterDownloadReceiver() {
        if (isReceiverRegistered && downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // Already unregistered
            }
            isReceiverRegistered = false;
        }
    }

    private void loadWebsite() {
        if (hasInternetConnection()) {
            showOfflineBanner(false);
            webView.loadUrl(BASE_URL);
        } else {
            showOfflinePage();
        }
    }

    private class JambGeniusWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            if (url.startsWith("jambgenius://")) {
                handleDeepLink(url);
                return true;
            }
            
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
            
            if (!url.contains("jambgenius") && !url.contains("vercel.app") && 
                !url.contains("google.com") && !url.contains("gstatic.com") &&
                !url.contains("firebaseapp.com") && !url.contains("paystack")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
            
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            hasShownOfflinePage = false;
            isPageLoaded = true;
            
            restoreUserSession();
            injectAppDetection();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                handleWebError(error);
            }
        }
    }

    private class JambGeniusWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
            
            if (newProgress == 100) {
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                    }
                }, 200);
            }
        }
    }

    private void injectAppDetection() {
        String script = "localStorage.setItem('isInApp', 'true'); " +
                       "window.isJambGeniusApp = true; " +
                       "console.log('JambGenius App detected');";
        webView.evaluateJavascript(script, null);
    }

    private class AppBridge {
        @JavascriptInterface
        public void saveUserSession(String sessionData) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(KEY_USER_SESSION, sessionData).apply();
        }
        
        @JavascriptInterface
        public String getUserSession() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            return prefs.getString(KEY_USER_SESSION, "");
        }
        
        @JavascriptInterface
        public void saveCachedUser(String userData) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(KEY_CACHED_USER, userData).apply();
        }
        
        @JavascriptInterface
        public String getCachedUser() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            return prefs.getString(KEY_CACHED_USER, "");
        }
        
        @JavascriptInterface
        public boolean isOnline() {
            return hasInternetConnection();
        }
        
        @JavascriptInterface
        public void clearSession() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().clear().apply();
        }

        @JavascriptInterface
        public void showToast(String message) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String getAppVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.0.0";
            }
        }
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
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("javascript:window.authToken = '" + token + "'", null);
                }
            });
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
    
    private void restoreUserSession() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedUser = prefs.getString(KEY_CACHED_USER, "");
        
        if (!cachedUser.isEmpty()) {
            String script = "if (window.AndroidApp && typeof window.restoreOfflineSession === 'function') { " +
                          "window.restoreOfflineSession('" + cachedUser.replace("'", "\\'") + "'); }";
            webView.evaluateJavascript(script, null);
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
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && 
                           (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
                }
            } else {
                NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        }
        return false;
    }

    private void showOfflinePage() {
        if (hasShownOfflinePage) return;
        hasShownOfflinePage = true;
        isOffline = true;
        showOfflineBanner(true);
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedUser = prefs.getString(KEY_CACHED_USER, "");
        boolean hasSession = !cachedUser.isEmpty();
        
        String userName = "";
        if (hasSession) {
            try {
                int nameStart = cachedUser.indexOf("\"displayName\":\"");
                if (nameStart > 0) {
                    nameStart += 15;
                    int nameEnd = cachedUser.indexOf("\"", nameStart);
                    if (nameEnd > nameStart) {
                        userName = cachedUser.substring(nameStart, nameEnd);
                    }
                }
            } catch (Exception e) {
                userName = "";
            }
        }
        
        String greeting = hasSession && !userName.isEmpty() ? 
            "<p class='greeting'>Welcome back, " + userName + "!</p>" : "";
        
        String offlineContent = hasSession ? 
            "<div class='feature-list'>" +
            "<div class='feature-item'><span class='icon'>📚</span><span>Review your bookmarked questions</span></div>" +
            "<div class='feature-item'><span class='icon'>📊</span><span>View your practice history</span></div>" +
            "<div class='feature-item'><span class='icon'>💡</span><span>Study offline with saved content</span></div>" +
            "</div>" +
            "<button class='offline-practice-btn' onclick='startOfflinePractice()'>Practice Offline</button>" :
            "<p class='subtitle'>Sign in when you're online to access offline features</p>";
        
        String offlinePage = "<!DOCTYPE html><html><head>" +
            "<meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
            "<style>" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
            "background: linear-gradient(180deg, #1e3a5f 0%, #2c5282 50%, #3182ce 100%); " +
            "min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; color: white; }" +
            ".logo { width: 100px; height: 100px; background: white; border-radius: 24px; display: flex; align-items: center; justify-content: center; margin-bottom: 24px; box-shadow: 0 8px 32px rgba(0,0,0,0.3); }" +
            ".logo-icon { font-size: 48px; }" +
            ".app-name { font-size: 32px; font-weight: 700; margin-bottom: 8px; text-shadow: 0 2px 8px rgba(0,0,0,0.3); }" +
            ".greeting { font-size: 18px; opacity: 0.9; margin-bottom: 16px; }" +
            ".status-card { background: rgba(255,255,255,0.15); backdrop-filter: blur(10px); border-radius: 16px; padding: 24px; text-align: center; max-width: 320px; width: 100%; margin-bottom: 24px; }" +
            ".status-icon { font-size: 48px; margin-bottom: 16px; }" +
            ".status-title { font-size: 20px; font-weight: 600; margin-bottom: 8px; }" +
            ".status-message { font-size: 14px; opacity: 0.9; line-height: 1.5; }" +
            ".feature-list { margin-top: 20px; text-align: left; }" +
            ".feature-item { display: flex; align-items: center; padding: 12px 0; border-top: 1px solid rgba(255,255,255,0.2); }" +
            ".feature-item:first-child { border-top: none; }" +
            ".feature-item .icon { font-size: 24px; margin-right: 12px; }" +
            ".feature-item span:last-child { font-size: 14px; }" +
            ".retry-btn { background: white; color: #2c5282; border: none; padding: 16px 40px; border-radius: 12px; font-size: 16px; font-weight: 600; margin-top: 16px; cursor: pointer; box-shadow: 0 4px 16px rgba(0,0,0,0.2); transition: transform 0.2s; }" +
            ".retry-btn:active { transform: scale(0.98); }" +
            ".offline-practice-btn { background: rgba(255,255,255,0.2); border: 2px solid white; color: white; padding: 14px 32px; border-radius: 12px; font-size: 14px; font-weight: 600; margin-top: 16px; cursor: pointer; width: 100%; }" +
            ".subtitle { font-size: 14px; opacity: 0.8; margin-top: 12px; }" +
            ".tip { background: rgba(255,255,255,0.1); border-radius: 12px; padding: 16px; margin-top: 24px; max-width: 320px; text-align: center; }" +
            ".tip-icon { font-size: 20px; margin-bottom: 8px; }" +
            ".tip-text { font-size: 13px; opacity: 0.9; line-height: 1.4; }" +
            ".loading { display: none; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; }" +
            ".spinner { width: 40px; height: 40px; border: 4px solid rgba(255,255,255,0.3); border-top-color: white; border-radius: 50%; animation: spin 1s linear infinite; }" +
            "@keyframes spin { to { transform: rotate(360deg); } }" +
            "</style>" +
            "<script>" +
            "function retryConnection() { " +
            "  var btn = document.querySelector('.retry-btn');" +
            "  btn.textContent = 'Checking...';" +
            "  btn.disabled = true;" +
            "  setTimeout(function() { location.reload(); }, 500);" +
            "}" +
            "function startOfflinePractice() { " +
            "  document.querySelector('.loading').style.display = 'flex';" +
            "  document.querySelector('.main-content').style.display = 'none';" +
            "  setTimeout(function() { location.reload(); }, 1500);" +
            "}" +
            "</script>" +
            "</head><body>" +
            "<div class='loading'>" +
            "<div class='spinner'></div>" +
            "<p style='margin-top:16px;font-size:16px;'>Loading offline content...</p>" +
            "</div>" +
            "<div class='main-content' style='display:flex;flex-direction:column;align-items:center;'>" +
            "<div class='logo'><span class='logo-icon'>🎓</span></div>" +
            "<h1 class='app-name'>JambGenius</h1>" +
            greeting +
            "<div class='status-card'>" +
            "<div class='status-icon'>📶</div>" +
            "<h2 class='status-title'>You're Offline</h2>" +
            "<p class='status-message'>Connect to the internet to access all features and sync your progress.</p>" +
            offlineContent +
            "</div>" +
            "<button class='retry-btn' onclick='retryConnection()'>Try Again</button>" +
            "<div class='tip'>" +
            "<div class='tip-icon'>💡</div>" +
            "<p class='tip-text'>Tip: Turn on WiFi or mobile data to continue learning</p>" +
            "</div>" +
            "</div>" +
            "</body></html>";
        
        webView.loadDataWithBaseURL(null, offlinePage, "text/html", "utf-8", null);
    }

    private void handleWebError(WebResourceError error) {
        if (error != null && !hasShownOfflinePage) {
            int errorCode = error.getErrorCode();
            
            if (errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                errorCode == WebViewClient.ERROR_CONNECT ||
                errorCode == WebViewClient.ERROR_TIMEOUT ||
                errorCode == WebViewClient.ERROR_IO ||
                errorCode == WebViewClient.ERROR_UNKNOWN) {
                
                showOfflinePage();
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

    @Override
    protected void onStart() {
        super.onStart();
        setupNetworkListener();
        setupDownloadReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterNetworkListener();
        unregisterDownloadReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        
        if (isOffline && hasInternetConnection()) {
            onNetworkAvailable();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
