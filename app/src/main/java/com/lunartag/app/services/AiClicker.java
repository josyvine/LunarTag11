package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AI CLICKER - THE ROBOT EYES
 * 
 * Function:
 * 1. Takes a screenshot of the current screen.
 * 2. Uses Google ML Kit (AI) to read the text.
 * 3. Finds the X,Y coordinates of the target (e.g., "Clone").
 * 4. Simulates a physical finger tap on those coordinates.
 */
public class AiClicker {

    private final AccessibilityService service;
    private final TextRecognizer recognizer;
    private final Executor executor;
    private boolean isProcessing = false;

    public AiClicker(AccessibilityService service) {
        this.service = service;
        // Initialize the AI with Default Latin (English) options
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        // Create a background thread for image processing
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * MAIN FUNCTION: Scans the screen visually and clicks the text if found.
     * 
     * @param targetText The text to find (e.g., "Clone" or "Send to")
     * @param callback   Returns TRUE if clicked, FALSE if not found.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void scanAndClickVisual(String targetText, OnScanListener callback) {
        if (isProcessing) {
            // Prevent spamming. If busy, return false immediately.
            if (callback != null) callback.onResult(false);
            return; 
        }
        
        isProcessing = true;

        // 1. TAKE SCREENSHOT (Requires Android 11+ API 30+)
        // This captures exactly what the user sees, including hidden/custom text.
        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, new AccessibilityService.TakeScreenshotCallback() {
            @Override
            public void onSuccess(@NonNull AccessibilityService.ScreenshotResult screenshotResult) {
                try {
                    // Convert hardware buffer to a usable Bitmap image
                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                    
                    if (bitmap != null) {
                        // Send image to AI
                        processImage(bitmap, targetText, callback);
                        
                        // Close the buffer to prevent memory leaks
                        screenshotResult.getHardwareBuffer().close();
                    } else {
                        finish(callback, false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    finish(callback, false);
                }
            }

            @Override
            public void onFailure(int i) {
                // Screenshot failed (e.g., protected content or screen off)
                System.out.println("LUNARTAG_AI: Screenshot Failed code " + i);
                finish(callback, false);
            }
        });
    }

    /**
     * FEEDS IMAGE TO GOOGLE ML KIT
     */
    private void processImage(Bitmap bitmap, String targetText, OnScanListener callback) {
        // Convert to ML Kit format
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 2. RUN AI RECOGNITION
        recognizer.process(image)
            .addOnSuccessListener(visionText -> {
                // 3. SEARCH RESULTS
                boolean found = false;

                // Loop through every block of text the AI found
                for (Text.TextBlock block : visionText.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        String lineText = line.getText().toLowerCase();
                        
                        // Check match (Case Insensitive)
                        if (lineText.contains(targetText.toLowerCase())) {
                            
                            // FOUND IT! Get the box (X, Y, Width, Height)
                            Rect box = line.getBoundingBox();
                            
                            if (box != null) {
                                System.out.println("LUNARTAG_AI: Found '" + targetText + "' at " + box.centerX() + "," + box.centerY());
                                
                                // 4. CLICK THE COORDINATES
                                performTap(box.centerX(), box.centerY());
                                found = true;
                                finish(callback, true);
                                return; // Stop searching
                            }
                        }
                    }
                }

                if (!found) {
                    System.out.println("LUNARTAG_AI: Text '" + targetText + "' not visible on screen.");
                    finish(callback, false);
                }
            })
            .addOnFailureListener(e -> {
                e.printStackTrace();
                finish(callback, false);
            });
    }

    /**
     * SIMULATES A PHYSICAL FINGER TAP
     */
    private void performTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        
        // Create a tap gesture: Touch down, hold 100ms, lift up.
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
        
        // Dispatch to Android System
        service.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                System.out.println("LUNARTAG_AI: Tap dispatched successfully.");
            }
        }, null);
    }

    private void finish(OnScanListener callback, boolean success) {
        isProcessing = false;
        // Return result on Main Thread so Service can update UI/Logs
        new Handler(Looper.getMainLooper()).post(() -> {
            if (callback != null) callback.onResult(success);
        });
    }

    // Interface to send results back to the main Service
    public interface OnScanListener {
        void onResult(boolean success);
    }
}