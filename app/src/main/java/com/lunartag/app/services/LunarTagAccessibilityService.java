package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * LUNARTAG ROBOT - FINAL "HARD RESET" EDITION
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_AUTO_MODE = "automation_mode"; 
    private static final String KEY_TARGET_GROUP = "target_group_name";

    // States
    private static final int STATE_IDLE = 0;
    private static final int STATE_WAITING_FOR_SHARE_SHEET = 1;
    private static final int STATE_SEARCHING_GROUP = 2;
    private static final int STATE_CLICKING_SEND = 3;

    private int currentState = STATE_IDLE;
    private String lastPackageName = "";
    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        // Configure to listen to EVERYTHING
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK; 
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 0; 
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        currentState = STATE_IDLE;

        // 1. DEBUG TOAST: PROOF OF LIFE
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(getApplicationContext(), "🤖 ROBOT CONNECTED & LISTENING", Toast.LENGTH_LONG).show());

        performBroadcastLog("🔴 SYSTEM READY. Mode: Checking...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_AUTO_MODE, "semi");

        // 1. TRACK PACKAGE CHANGES (The "Everytime" Fix)
        String pkgName = "unknown";
        if (event.getPackageName() != null) {
            pkgName = event.getPackageName().toString().toLowerCase();
        }

        // IF APP CHANGED, HARD RESET THE BRAIN
        if (!pkgName.equals(lastPackageName)) {
            if (!lastPackageName.isEmpty()) {
                performBroadcastLog("🔄 App Switch Detected: " + pkgName);
                // Don't reset if we are just transitioning to share sheet
                if (!pkgName.equals("android") && !pkgName.contains("launcher")) {
                     currentState = STATE_IDLE; 
                }
            }
            lastPackageName = pkgName;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        // ====================================================================
        // FULL AUTOMATIC LOGIC (ADDED - DOES NOT TOUCH SEMI AUTO)
        // ====================================================================
        if (mode.equals("full")) {
            // AlarmReceiver has opened the "Original vs Clone" dialog.
            // We are likely in package "android" or "com.android.intentresolver"
            if (!pkgName.contains("whatsapp") && root != null) {
                
                // Find all items labeled "WhatsApp"
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("WhatsApp");
                
                if (nodes != null && !nodes.isEmpty()) {
                    // IF WE SEE 2 WHATSAPPS -> CLICK THE 2ND ONE (CLONE)
                    if (nodes.size() >= 2) {
                        performBroadcastLog("✅ Full Auto: 2 Apps Found. Clicking Index 1 (Clone).");
                        
                        // Try clicking the node, or its parent if the node isn't clickable
                        AccessibilityNodeInfo target = nodes.get(1); 
                        performClick(target); 
                        
                        currentState = STATE_SEARCHING_GROUP; // Prepare for next step inside WA
                        return;
                    }
                    // IF WE SEE 1 WHATSAPP -> CLICK IT
                    else if (nodes.size() == 1) {
                         performBroadcastLog("✅ Full Auto: 1 App Found. Clicking Index 0.");
                         AccessibilityNodeInfo target = nodes.get(0);
                         performClick(target);
                         currentState = STATE_SEARCHING_GROUP;
                         return;
                    }
                }
            }
        }

        // ====================================================================
        // SEMI-AUTOMATIC & FULL: WHATSAPP HANDLING (ORIGINAL CODE UNTOUCHED)
        // ====================================================================
        if (pkgName.contains("whatsapp")) {

            // SEMI-AUTO WAKE UP TRIGGER
            // If we are in Semi mode, and just entered WhatsApp, Force Search Mode.
            if (mode.equals("semi")) {
                // We check if we are seeing the "Send to..." list to confirm we are sharing
                if (currentState == STATE_IDLE) {
                    performBroadcastLog("⚡ Semi-Auto: WhatsApp Detected. Starting Search...");
                    currentState = STATE_SEARCHING_GROUP;
                }
            }

            // EXECUTE SEARCH
            if (currentState == STATE_SEARCHING_GROUP) {
                if (root == null) return;
                String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");

                if (targetGroup.isEmpty()) {
                    performBroadcastLog("⚠️ Error: No Group Name Saved!");
                    return;
                }

                // Try finding the group
                if (scanAndClick(root, targetGroup)) {
                    performBroadcastLog("✅ Found Group: " + targetGroup);
                    currentState = STATE_CLICKING_SEND; // Move to next step
                    return;
                }

                // Scroll and retry
                performBroadcastLog("🔎 Searching for group...");
                performScroll(root);
            }

            // EXECUTE SEND
            else if (currentState == STATE_CLICKING_SEND) {
                if (root == null) return;

                boolean sent = false;
                // 1. Check Content Description
                if (scanAndClickContentDesc(root, "Send")) sent = true;

                // 2. Check View ID
                if (!sent) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
                    if (!nodes.isEmpty()) {
                        performClick(nodes.get(0));
                        sent = true;
                    }
                }

                if (sent) {
                    performBroadcastLog("🚀 SENT! Job Complete.");
                    currentState = STATE_IDLE; // Reset for the next photo
                }
            }
        }
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    private void performBroadcastLog(String msg) {
        try {
            // Log to System Out just in case Broadcast fails
            System.out.println("LUNARTAG_LOG: " + msg);

            Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
            intent.putExtra("log_msg", msg);
            intent.setPackage(getPackageName());
            // Use Application Context to ensure stability
            getApplicationContext().sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;
        // Case insensitive search
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClick(node)) return true;
            }
        }
        // Fallback for partial matches manually
        return recursiveSearch(root, text);
    }

    private boolean recursiveSearch(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && node.getText().toString().toLowerCase().contains(text.toLowerCase())) {
            return performClick(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveSearch(node.getChild(i), text)) return true;
        }
        return false;
    }

    private boolean scanAndClickContentDesc(AccessibilityNodeInfo root, String desc) {
        if (root == null || desc == null) return false;
        if (root.getContentDescription() != null && 
            root.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return performClick(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanAndClickContentDesc(root.getChild(i), desc)) return true;
        }
        return false;
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
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 600);
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

    @Override
    public void onInterrupt() {
        performBroadcastLog("⚠️ Interrupted");
    }
}