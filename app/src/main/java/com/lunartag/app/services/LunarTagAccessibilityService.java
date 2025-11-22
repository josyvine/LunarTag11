package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * The Automation Brain.
 * UPDATED: Full Code with Build Fix (return types) and Manual List Scanning.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilityService";

    // --- Shared Memory Constants ---
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    
    // Keys must match what we will save in RobotFragment and AppsFragment
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode"; // "semi" or "full"
    private static final String KEY_TARGET_APP_LABEL = "target_app_label"; // e.g., "WhatsApp" or "WhatsApp (Clone)"

    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        // Configure service to see all views (interactive windows)
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        
        showDebugToast("🤖 Robot Ready & Waiting...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. Check if we have a pending job. If not, do nothing.
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);

        if (!isJobPending) {
            return;
        }

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, "").trim();
        String targetAppLabel = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");

        // Filter irrelevant events to save performance
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        CharSequence packageNameSeq = event.getPackageName();
        String packageName = (packageNameSeq != null) ? packageNameSeq.toString().toLowerCase() : "";

        // ---------------------------------------------------------
        // STEP 1: HANDLE NOTIFICATION (Full Automatic Only)
        // ---------------------------------------------------------
        if (mode.equals("full") && eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            // Check if this notification is from OUR app
            if (packageName.contains(getPackageName())) {
                Parcelable data = event.getParcelableData();
                if (data instanceof Notification) {
                    showDebugToast("🤖 Notification Detected. Clicking...");
                    try {
                        Notification notification = (Notification) data;
                        if (notification.contentIntent != null) {
                            notification.contentIntent.send();
                            return; // Wait for the next screen
                        }
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // STEP 2: HANDLE SHARE SHEET / APP SELECTION (Full Automatic Only)
        // ---------------------------------------------------------
        if (mode.equals("full") && !packageName.contains("whatsapp")) {
            
            // 1. Try finding the Target App Label
            if (scanAndClick(rootNode, targetAppLabel)) {
                showDebugToast("🤖 Clicked App: " + targetAppLabel);
                return;
            }

            // 2. Clone Logic: If target is "WhatsApp (Clone)" but not found, look for "WhatsApp"
            if (targetAppLabel.toLowerCase().contains("clone") || targetAppLabel.toLowerCase().contains("dual")) {
                 if (scanAndClick(rootNode, "WhatsApp")) {
                     showDebugToast("🤖 Clicking Parent 'WhatsApp'...");
                     return;
                 }
            }
            
            // 3. Scroll if nothing found
            performScroll(rootNode);
        }

        // ---------------------------------------------------------
        // STEP 3: HANDLE WHATSAPP (Chat List & Send Screen)
        // ---------------------------------------------------------
        if (packageName.contains("whatsapp")) {
            
            // A. Priority: Find "Send" Button FIRST (Paper Plane)
            if (scanAndClickContentDesc(rootNode, "Send")) {
                showDebugToast("🤖 SENT! Job Complete.");
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                return; 
            }

            // B. Secondary: Find the Group Name
            if (!targetGroupName.isEmpty()) {
                // Strategy 1: Standard Android Text Search
                if (scanAndClick(rootNode, targetGroupName)) {
                    showDebugToast("🤖 Found Group: " + targetGroupName);
                    return;
                }
                
                // Strategy 2: Manual List Scan (Fixes the "Blind Robot")
                // This iterates through list items one by one if standard search fails.
                if (scanListItemsManually(rootNode, targetGroupName)) {
                     showDebugToast("🤖 Found Group (Manual Scan): " + targetGroupName);
                     return;
                }

                // Strategy 3: Scroll List
                performScroll(rootNode);
            }
        }
    }

    /**
     * MANUAL LIST SCANNER
     * Iterates through RecyclerView children to find text that is otherwise hidden.
     */
    private boolean scanListItemsManually(AccessibilityNodeInfo root, String targetText) {
        if (root == null) return false;
        
        // Identify if this node is a List
        if (root.getClassName() != null && 
           (root.getClassName().toString().contains("RecyclerView") || 
            root.getClassName().toString().contains("ListView"))) {
            
            // Iterate children manually
            for (int i = 0; i < root.getChildCount(); i++) {
                AccessibilityNodeInfo child = root.getChild(i);
                if (recursiveTextCheck(child, targetText)) {
                    return true; // Clicked inside the helper
                }
            }
        }

        // Continue searching deeper for the list itself
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanListItemsManually(root.getChild(i), targetText)) return true;
        }
        return false;
    }

    /**
     * Recursive Text Check used by Manual Scanner.
     * Checks both Text and ContentDescription.
     */
    private boolean recursiveTextCheck(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;

        // Check visible text
        if (node.getText() != null && 
            node.getText().toString().toLowerCase().contains(target.toLowerCase())) {
            return tryClickingHierarchy(node);
        }
        
        // Check hidden text (Content Description)
        if (node.getContentDescription() != null && 
            node.getContentDescription().toString().toLowerCase().contains(target.toLowerCase())) {
            return tryClickingHierarchy(node);
        }

        // Check children
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveTextCheck(node.getChild(i), target)) return true;
        }
        return false;
    }

    /**
     * Helper: Finds a node by text and clicks it (or its parent).
     */
    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (tryClickingHierarchy(node)) return true;
            }
        }
        return false;
    }

    /**
     * Helper: Finds a node by Content Description (essential for Image Buttons like "Send").
     */
    private boolean scanAndClickContentDesc(AccessibilityNodeInfo root, String desc) {
        if (root == null) return false;
        if (root.getContentDescription() != null && 
            root.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return tryClickingHierarchy(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanAndClickContentDesc(root.getChild(i), desc)) return true;
        }
        return false;
    }

    /**
     * Helper: Checks if a node is clickable. If not, checks parent (up to 6 levels).
     */
    private boolean tryClickingHierarchy(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        int attempts = 0;
        // Increased depth to 6 for complex layouts like WhatsApp
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

    /**
     * Helper: Finds a scrollable container and scrolls forward.
     */
    private void performScroll(AccessibilityNodeInfo root) {
        if (isScrolling) return; // Debounce

        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            isScrolling = true;
            showDebugToast("🤖 Scrolling...");
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            
            // Reset flag after a delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1500);
        }
    }

    /**
     * FIXED: Changed return type from boolean to AccessibilityNodeInfo
     */
    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findScrollableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Live Log Helper: Shows visual confirmation of background actions on screen.
     */
    private void showDebugToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.");
    }
}