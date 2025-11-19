package com.lunartag.app;

import android.app.Application;

// Import the core FirebaseApp class
import com.google.firebase.FirebaseApp;

/**
 * The custom Application class for Lunar Tag.
 * This is the entry point of the application process.
 */
public class LunarTagApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Manually initialize Firebase. This MUST be the first Firebase call.
        // This allows Remote Config to function correctly.
        FirebaseApp.initializeApp(this);
    }
}