package com.lunartag.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;

import com.lunartag.app.R;

/**
 * A utility class with static methods for rendering the watermark onto a photo.
 * UPDATED: Fixed build error by removing reference to missing mipmap resource.
 */
public class WatermarkUtils {

    // Private constructor to prevent instantiation
    private WatermarkUtils() {}

    /**
     * Renders the complete watermark block onto the provided Bitmap.
     * @param context The Android Context (needed to load the logo resource).
     * @param originalBitmap The original, mutable photo bitmap.
     * @param mapBitmap The small, pre-rendered bitmap of the map preview.
     * @param lines An array of strings, with each string representing one line of the watermark text.
     */
    public static void addWatermark(Context context, Bitmap originalBitmap, Bitmap mapBitmap, String[] lines) {
        if (originalBitmap == null || lines == null || lines.length == 0) {
            return;
        }

        Canvas canvas = new Canvas(originalBitmap);
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // --- 1. Configure Main Text Paint ---
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(width / 40.0f); // Slightly smaller text for address to fit better
        textPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        // --- 2. Configure Branding Paint (App Name) ---
        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.YELLOW); // Yellow color looks professional on black
        brandPaint.setTextSize(width / 35.0f); // Slightly larger/bolder than body text
        brandPaint.setFakeBoldText(true);
        brandPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(140); // Semi-transparent black

        // --- 3. Calculate Dimensions ---
        float textHeight = textPaint.descent() - textPaint.ascent();
        // Height is roughly text lines + padding. Added extra padding for the Logo header.
        float blockHeight = (textHeight * lines.length) + (lines.length * 12) + 40;

        // Ensure block is tall enough for the map if map exists
        if (mapBitmap != null && mapBitmap.getHeight() + 20 > blockHeight) {
            blockHeight = mapBitmap.getHeight() + 40;
        }

        float watermarkTop = height - blockHeight;

        // --- 4. Draw Background ---
        Rect backgroundRect = new Rect(0, (int) watermarkTop, width, height);
        canvas.drawRect(backgroundRect, backgroundPaint);

        // --- 5. Draw Map Bitmap (if provided) ---
        float mapLeft = 20;
        float mapTop = watermarkTop + 20;
        if (mapBitmap != null) {
            canvas.drawBitmap(mapBitmap, mapLeft, mapTop, null);
        }

        // --- 6. Draw Branding (Top-Right Corner) ---
        // Load the Logo. We assume 'lunartag' is the file name in drawable folder.
        Bitmap logo = null;
        if (context != null) {
            // FIXED: Removed the fallback to R.mipmap.ic_launcher because it doesn't exist.
            // We strictly use your provided logo.
            logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.lunartag);
        }

        if (logo != null) {
            // Resize logo to be small (e.g., 8% of screen width)
            int targetLogoSize = (int) (width * 0.08); 
            // Ensure it's at least a visible size
            if (targetLogoSize < 50) targetLogoSize = 50; 

            Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, targetLogoSize, targetLogoSize, true);

            float logoX = width - targetLogoSize - 30; // 30px padding from right
            float logoY = watermarkTop + 20; // 20px padding from top of black box

            canvas.drawBitmap(scaledLogo, logoX, logoY, null);

            // Draw App Name "Lunar Tag" to the LEFT of the logo
            String appName = "Lunar Tag";
            float textWidth = brandPaint.measureText(appName);
            float brandTextX = logoX - textWidth - 20;
            // Center text vertically relative to logo
            float brandTextY = logoY + (targetLogoSize / 2f) - ((brandPaint.descent() + brandPaint.ascent()) / 2f);

            canvas.drawText(appName, brandTextX, brandTextY, brandPaint);
        }

        // --- 7. Draw Main Text Lines ---
        float textLeft = (mapBitmap != null) ? mapBitmap.getWidth() + 50 : 40;
        // Start text lower to account for the Branding Header we just drew
        float currentY = watermarkTop + textHeight + 40; 

        for (String line : lines) {
            if (line != null) {
                canvas.drawText(line, textLeft, currentY, textPaint);
                currentY += (textHeight + 10); // Add line spacing
            }
        }
    }
}