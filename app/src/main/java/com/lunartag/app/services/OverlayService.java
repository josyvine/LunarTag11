package com.lunartag.app.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.lunartag.app.R;

public class OverlayService extends Service {

    private static OverlayService instance;
    private WindowManager windowManager;
    private View overlayView;
    private View markerBox;
    private WindowManager.LayoutParams params;
    private boolean isViewAttached = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Inflate the Red Light Layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_marker_view, null);
        markerBox = overlayView.findViewById(R.id.marker_box);

        // Configure the Overlay Window (Transparent, Click-through)
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // Allow clicks to pass through
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
    }

    /**
     * STATIC ACCESS: Allows the Robot to call this easily
     */
    public static OverlayService getInstance() {
        return instance;
    }

    /**
     * Moves the Red Light to the specific coordinates and blinks
     */
    public void showMarkerAt(Rect bounds) {
        if (overlayView == null || windowManager == null) return;

        handler.post(() -> {
            try {
                // Update Position
                params.x = bounds.left;
                params.y = bounds.top;
                
                // Resize box to match the target icon size (Optional, or keep fixed)
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) markerBox.getLayoutParams();
                layoutParams.width = bounds.width();
                layoutParams.height = bounds.height();
                markerBox.setLayoutParams(layoutParams);

                if (!isViewAttached) {
                    windowManager.addView(overlayView, params);
                    isViewAttached = true;
                } else {
                    windowManager.updateViewLayout(overlayView, params);
                }

                // Start Blink Animation
                startBlinking();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void startBlinking() {
        markerBox.setVisibility(View.VISIBLE);
        
        // Blink OFF after 200ms
        handler.postDelayed(() -> markerBox.setVisibility(View.INVISIBLE), 200);
        
        // Blink ON after 400ms
        handler.postDelayed(() -> markerBox.setVisibility(View.VISIBLE), 400);
        
        // Blink OFF after 600ms
        handler.postDelayed(() -> markerBox.setVisibility(View.INVISIBLE), 600);
        
        // Remove completely after 800ms
        handler.postDelayed(this::hideMarker, 800);
    }

    public void hideMarker() {
        handler.post(() -> {
            if (isViewAttached && overlayView != null) {
                try {
                    windowManager.removeView(overlayView);
                    isViewAttached = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideMarker();
        instance = null;
    }
}