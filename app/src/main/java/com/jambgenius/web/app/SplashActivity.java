package com.jambgenius.web.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private static final int SPLASH_DURATION = 2000;
    private TextView loadingText;
    private String[] loadingMessages = {
        "Loading...",
        "Preparing your study materials...",
        "Getting things ready...",
        "Almost there..."
    };
    private int messageIndex = 0;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        setContentView(R.layout.activity_splash);
        
        loadingText = findViewById(R.id.loading_text);
        
        startLoadingAnimation();
        
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, SPLASH_DURATION);
    }

    private void startLoadingAnimation() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (loadingText != null && messageIndex < loadingMessages.length - 1) {
                    messageIndex++;
                    loadingText.setText(loadingMessages[messageIndex]);
                    handler.postDelayed(this, 600);
                }
            }
        }, 600);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
