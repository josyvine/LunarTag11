package com.lunartag.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    // Match this to your CSS animation duration (3.5s)
    private static final int SPLASH_DELAY_MS = 3500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Make it Full Screen (Hide Status Bar & Navigation Bar)
        // This ensures the mechanical shutter fills the entire physical display.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_splash);

        // 2. Setup WebView to render the HTML asset
        WebView webView = findViewById(R.id.webview_splash);
        
        // Performance settings for smooth animation
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        
        // Load the mechanical shutter animation
        webView.loadUrl("file:///android_asset/splash.html");

        // 3. Start Timer to open Dashboard
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                navigateToDashboard();
            }
        }, SPLASH_DELAY_MS);
    }

    private void navigateToDashboard() {
        // Launch Main Activity
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        
        // Kill Splash Activity so user cannot go back to it
        finish();
        
        // Disable standard transition animation for a seamless "reveal" effect
        overridePendingTransition(0, 0);
    }
}