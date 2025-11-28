package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String PREFS_SETTINGS = "LunarTagSettings"; // To read Option A vs B

    private static final String KEY_AUTO_MODE = "automation_mode"; 
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_WA_METHOD = "wa_automation_method"; // "red_box" or "coordinate"

    // Coordinates (Share Sheet)
    private static final String KEY_ICON_X = "share_icon_x";
    private static final String KEY_ICON_Y = "share_icon_y";

    // Coordinates (Option B - WhatsApp)
    private static final String KEY_GROUP_X = "group_x";
    private static final String KEY_GROUP_Y = "group_y";
    private static final String KEY_CHAT_X = "chat_send_x";
    private static final String KEY_CHAT_Y = "chat_send_y";
    private static final String KEY_PREVIEW_X = "preview_send_x";
    private static final String KEY_PREVIEW_Y = "preview_send_y";

    // TOKENS
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_FORCE_RESET = "force_reset_logic";

    // LOGIC FLAGS (General)
    private boolean isClickingPending = false; 
    private boolean isScrolling = false;
    private long lastToastTime = 0;

    // Safety flag to prevent Share Sheet loop
    private boolean shareSheetClicked = false;

    // Safety flags for Option B (Coordinate Mode)
    // These ensure we only click each sequence ONCE per job
    private boolean groupCoordinateClicked = false;
    private boolean chatSendCoordinateClicked = false;
    private boolean previewSendCoordinateClicked = false;

    // Fixed variables
    private static final int STATE_IDLE = 0;
    private int currentState = STATE_IDLE;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK; 
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 0; 
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        // Force Start Overlay
        try {
            Intent intent = new Intent(this, OverlayService.class);
            startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        performBroadcastLog("🔴 ROBOT ONLINE. INFINITE MODE READY.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkgName = event.getPackageName().toString().toLowerCase();

        // 1. STRICT PACKAGE FILTER (Protects Chrome/Other Apps)
        boolean isSafePackage = pkgName.contains("whatsapp") || 
                                pkgName.equals("android") || 
                                pkgName.contains("chooser") || 
                                pkgName.contains("systemui");

        if (!isSafePackage) return; 

        AccessibilityNodeInfo root = getRootInActiveWindow();
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        SharedPreferences settings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");
        String waMethod = settings.getString(KEY_WA_METHOD, "red_box"); // Default Option A

        // 2. BRAIN WIPE CHECK (From Camera - New Job Started)
        // This acts as a secondary backup reset
        if (prefs.getBoolean(KEY_FORCE_RESET, false)) {
            isClickingPending = false;
            shareSheetClicked = false;

            // Reset Option B Sequence Flags
            groupCoordinateClicked = false;
            chatSendCoordinateClicked = false;
            previewSendCoordinateClicked = false;

            prefs.edit().putBoolean(KEY_FORCE_RESET, false).apply();
            performBroadcastLog("🔄 NEW JOB DETECTED. MEMORY WIPED.");
        }

        if (root == null) return;
        if (isClickingPending) return;

        // ====================================================================
        // 3. SHARE SHEET LOGIC (Coordinate Click - One Shot)
        // ====================================================================
        boolean isShareSheet = hasText(root, "Cancel") || pkgName.equals("android") || pkgName.contains("chooser");

        // Reset the local Share Sheet flag if we are NOT on the share sheet
        if (!isShareSheet) {
            shareSheetClicked = false;
        }

        if (mode.equals("full") && isShareSheet && !pkgName.contains("whatsapp")) {

            // Only click if Job is TRUE AND we haven't clicked this specific instance yet.
            if (prefs.getBoolean(KEY_JOB_PENDING, false) && !shareSheetClicked) {
                int x = prefs.getInt(KEY_ICON_X, 0);
                int y = prefs.getInt(KEY_ICON_Y, 0);

                if (x > 0 && y > 0) {
                    if (OverlayService.getInstance() != null) {
                        OverlayService.getInstance().showMarkerAtCoordinate(x, y);
                    }

                    performBroadcastLog("✅ Share Sheet. Clicking X=" + x + " Y=" + y);

                    // Mark as clicked so we don't loop/flash while the sheet is closing
                    shareSheetClicked = true;
                    isClickingPending = true;

                    // Delay 500ms for animation
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        dispatchGesture(createClickGesture(x, y), null, null);
                        isClickingPending = false; 
                    }, 500);
                }
            }
            return;
        }

        // ====================================================================
        // 4. WHATSAPP LOGIC (FLUID / INFINITE)
        // ====================================================================
        if (pkgName.contains("whatsapp")) {

            // CRITICAL GUARD: Robot only works if JOB_PENDING is true.
            if (prefs.getBoolean(KEY_JOB_PENDING, false)) {

                // -----------------------------------------------------------
                // BRANCH: CHECK METHOD (Option A: Red Box vs Option B: Coordinate)
                // -----------------------------------------------------------

                if (waMethod.equals("coordinate")) {
                    // >>> OPTION B: MANUAL COORDINATE MODE <<<
                    performCoordinateLogic(root, prefs);
                    return; // Exit here, do not run Red Box logic
                }

                // >>> OPTION A: EXISTING RED BOX LOGIC (DEFAULT) <<<

                // --- SEARCH FOR ANY SEND BUTTON ---
                boolean sendFound = false;

                // 1. Standard Chat IDs
                if (findMarkerAndClickID(root, "com.whatsapp:id/conversation_send_arrow")) sendFound = true;
                if (!sendFound && findMarkerAndClickID(root, "com.whatsapp:id/send")) sendFound = true;

                // 2. Floating Button (Preview Screen)
                if (!sendFound && findMarkerAndClickID(root, "com.whatsapp:id/fab")) sendFound = true;

                // 3. Content Description Search (Green Button Fix)
                if (!sendFound && findMarkerAndClickContentDescription(root, "Send")) sendFound = true;

                if (sendFound) {
                    performBroadcastLog("🚀 SEND BUTTON FOUND. CLICKING...");

                    // SUCCESS! NOW we turn off the job token.
                    prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();

                    new Handler(Looper.getMainLooper()).postDelayed(() -> 
                        Toast.makeText(getApplicationContext(), "🚀 MESSAGE SENT", Toast.LENGTH_SHORT).show(), 500);
                    return; // Stop here, job done.
                }

                // VISUAL STATUS
                if (System.currentTimeMillis() - lastToastTime > 3000) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        Toast.makeText(getApplicationContext(), "🤖 Robot Searching: " + targetGroup, Toast.LENGTH_SHORT).show());
                    lastToastTime = System.currentTimeMillis();
                }

                // --- SEARCH FOR GROUP NAME ---
                if (!targetGroup.isEmpty()) {
                    if (findMarkerAndClick(root, targetGroup, true)) {
                        performBroadcastLog("✅ GROUP FOUND. CLICKING...");
                        return; // Clicked group, wait for screen change.
                    }

                    // If group not found, Scroll.
                    if (!isScrolling) performScroll(root);
                }
            }
        }
    }

    // ====================================================================
    // NEW LOGIC: OPTION B (COORDINATE SEQUENCES)
    // ====================================================================
    private void performCoordinateLogic(AccessibilityNodeInfo root, SharedPreferences prefs) {

        // --- SEQUENCE 1: GROUP SELECTION ---
        // Condition: We haven't clicked the group yet.
        if (!groupCoordinateClicked) {
            int x = prefs.getInt(KEY_GROUP_X, 0);
            int y = prefs.getInt(KEY_GROUP_Y, 0);

            if (x > 0 && y > 0) {
                performBroadcastLog("📍 Coord Mode: Step 1 (Group). Clicking...");
                executeCoordinateClick(x, y);
                groupCoordinateClicked = true; // LOCK SEQUENCE 1
            }
            return;
        }

        // --- SEQUENCE 2: GREEN ARROW (Contact List Next) ---
        // Condition: Group is done, but Arrow not clicked.
        // Screen Logic: We are on the same screen (Contact List). Wait for event.
        if (groupCoordinateClicked && !chatSendCoordinateClicked) {

            int x = prefs.getInt(KEY_CHAT_X, 0);
            int y = prefs.getInt(KEY_CHAT_Y, 0);

            if (x > 0 && y > 0) {
                performBroadcastLog("📍 Coord Mode: Step 2 (Next Arrow). Clicking...");
                executeCoordinateClick(x, y);
                chatSendCoordinateClicked = true; // LOCK SEQUENCE 2
            }
            return;
        }

        // --- SEQUENCE 3: FINAL PREVIEW SEND ---
        // Condition: Arrow is done, but Final Send not clicked.
        // Screen Check: Look for Caption Box or Filter/Crop/Send IDs to confirm we are on Preview.
        if (chatSendCoordinateClicked && !previewSendCoordinateClicked) {

            // Check if we are on preview screen (Caption box is usually present)
            List<AccessibilityNodeInfo> captionNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/caption");
            List<AccessibilityNodeInfo> sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
            List<AccessibilityNodeInfo> doodleNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/doodle");

            if ((captionNodes != null && !captionNodes.isEmpty()) || 
                (sendNodes != null && !sendNodes.isEmpty()) || 
                (doodleNodes != null && !doodleNodes.isEmpty())) {

                int x = prefs.getInt(KEY_PREVIEW_X, 0);
                int y = prefs.getInt(KEY_PREVIEW_Y, 0);

                if (x > 0 && y > 0) {
                    performBroadcastLog("📍 Coord Mode: Step 3 (Final Send). Clicking...");
                    executeCoordinateClick(x, y);
                    previewSendCoordinateClicked = true; // LOCK SEQUENCE 3

                    // --- SUCCESS! JOB DONE. ---
                    // 1. Disable the Job Ticket
                    prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();

                    // 2. *** FIX: INSTANT MEMORY CLEANING ***
                    // Reset all flags to FALSE immediately so we are ready for the next message.
                    groupCoordinateClicked = false;
                    chatSendCoordinateClicked = false;
                    previewSendCoordinateClicked = false;
                    shareSheetClicked = false;
                    // ---------------------------------------

                    new Handler(Looper.getMainLooper()).postDelayed(() -> 
                        Toast.makeText(getApplicationContext(), "🚀 SEQUENCE COMPLETE", Toast.LENGTH_SHORT).show(), 500);
                }
            }
            return;
        }
    }

    private void executeCoordinateClick(int x, int y) {
        if (isClickingPending) return;
        isClickingPending = true;

        // Show Visual Marker
        if (OverlayService.getInstance() != null) {
            OverlayService.getInstance().showMarkerAtCoordinate(x, y);
        }

        // Execute Click after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            dispatchGesture(createClickGesture(x, y), null, null);
            isClickingPending = false; 
        }, 500);
    }

    // ====================================================================
    // EXISTING UTILITIES (UNTOUCHED)
    // ====================================================================

    private GestureDescription createClickGesture(int x, int y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke = 
                new GestureDescription.StrokeDescription(clickPath, 0, 80);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    private boolean hasText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;
        String cleanTarget = cleanString(text);
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(cleanTarget);
        if (nodes != null && !nodes.isEmpty()) return true;
        return recursiveCheckText(root, cleanTarget);
    }

    private boolean recursiveCheckText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && cleanString(node.getText().toString()).contains(text)) return true;
        if (node.getContentDescription() != null && cleanString(node.getContentDescription().toString()).contains(text)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveCheckText(node.getChild(i), text)) return true;
        }
        return false;
    }

    private boolean findMarkerAndClick(AccessibilityNodeInfo root, String text, boolean isTextSearch) {
        if (root == null || text == null || text.isEmpty()) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable() || node.getParent().isClickable()) {
                    executeVisualClick(node);
                    return true;
                }
            }
        }
        return recursiveSearchAndClick(root, text);
    }

    private boolean findMarkerAndClickID(AccessibilityNodeInfo root, String viewId) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            executeVisualClick(nodes.get(0));
            return true;
        }
        return false;
    }

    private boolean findMarkerAndClickContentDescription(AccessibilityNodeInfo root, String desc) {
        if (root == null || desc == null) return false;
        String target = desc.toLowerCase();

        if (root.getContentDescription() != null) {
            String nodeDesc = root.getContentDescription().toString().toLowerCase();
            if (nodeDesc.equals(target) || nodeDesc.contains(target)) {
                 if (root.isClickable()) {
                     executeVisualClick(root);
                     return true;
                 } else if (root.getParent() != null && root.getParent().isClickable()) {
                     executeVisualClick(root.getParent());
                     return true;
                 }
            }
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            if (findMarkerAndClickContentDescription(root.getChild(i), desc)) return true;
        }
        return false;
    }

    private boolean recursiveSearchAndClick(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        boolean match = false;
        String cleanTarget = cleanString(text);
        if (node.getText() != null && cleanString(node.getText().toString()).contains(cleanTarget)) match = true;
        if (!match && node.getContentDescription() != null && cleanString(node.getContentDescription().toString()).contains(cleanTarget)) match = true;

        if (match) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) {
                clickable = clickable.getParent();
            }
            if (clickable != null) {
                executeVisualClick(clickable);
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveSearchAndClick(node.getChild(i), text)) return true;
        }
        return false;
    }

    private String cleanString(String input) {
        if (input == null) return "";
        return input.toLowerCase().replace(" ", "").replace("\n", "").trim();
    }

    private void executeVisualClick(AccessibilityNodeInfo node) {
        if (isClickingPending) return;
        isClickingPending = true;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (OverlayService.getInstance() != null) {
            OverlayService.getInstance().showMarkerAt(bounds);
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            performClick(node);
            isClickingPending = false;
        }, 500); 
    }

    private boolean performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        int attempts = 0;
        while (target != null && attempts < 6) {
            if (target.isClickable()) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            target = target.getParent();
            attempts++;
        }
        return false;
    }

    private void performScroll(AccessibilityNodeInfo root) {
        if (isScrolling) return;
        AccessibilityNodeInfo scrollable = findScrollable(root);
        if (scrollable != null) {
            isScrolling = true;
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 800);
        }
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo res = findScrollable(node.getChild(i));
            if (res != null) return res;
        }
        return null;
    }

    private void performBroadcastLog(String msg) {
        try {
            System.out.println("LUNARTAG_LOG: " + msg);
            String type = "info";
            if (msg != null && (msg.toLowerCase().contains("error") || msg.toLowerCase().contains("fail") || msg.toLowerCase().contains("missing"))) {
                type = "error";
            }
            Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
            intent.putExtra("log_msg", msg);
            intent.putExtra("log_type", type);
            intent.setPackage(getPackageName());
            getApplicationContext().sendBroadcast(intent);
        } catch (Exception e) {}
    }

    @Override
    public void onInterrupt() {
        currentState = STATE_IDLE;
        if (OverlayService.getInstance() != null) OverlayService.getInstance().hideMarker();
    }
}