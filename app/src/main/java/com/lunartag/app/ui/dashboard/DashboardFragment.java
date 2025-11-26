package com.lunartag.app.ui.dashboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.lunartag.app.data.AppDatabase;
import com.lunartag.app.data.PhotoDao;
import com.lunartag.app.databinding.FragmentDashboardBinding;
import com.lunartag.app.model.Photo;
import com.lunartag.app.ui.gallery.GalleryAdapter;
import com.lunartag.app.utils.Scheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;

    // SharedPreferences to store the Shift State permanently
    private static final String PREFS_SHIFT = "LunarTagShiftPrefs";
    private static final String KEY_IS_SHIFT_ACTIVE = "is_shift_active";
    private static final String KEY_LAST_ACTION_TIME = "last_action_time";

    // --- DB Components ---
    private ExecutorService databaseExecutor;

    // Two separate adapters for the two boxes
    private GalleryAdapter scheduledAdapter;
    private GalleryAdapter recentAdapter;

    // Data lists
    private List<Photo> scheduledPhotoList;
    private List<Photo> recentPhotoList;

    // Track which adapter is currently in selection mode
    private GalleryAdapter activeSelectionAdapter = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Executor for DB operations
        databaseExecutor = Executors.newSingleThreadExecutor();
        scheduledPhotoList = new ArrayList<>();
        recentPhotoList = new ArrayList<>();

        // --- 1. Setup Top Box (Scheduled Sends) ---
        LinearLayoutManager scheduledManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.recyclerViewScheduledSends.setLayoutManager(scheduledManager);
        scheduledAdapter = new GalleryAdapter(getContext(), scheduledPhotoList);
        binding.recyclerViewScheduledSends.setAdapter(scheduledAdapter);

        // --- 2. Setup Bottom Box (Recent Photos) ---
        LinearLayoutManager recentManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.recyclerViewRecentPhotos.setLayoutManager(recentManager);
        recentAdapter = new GalleryAdapter(getContext(), recentPhotoList);
        binding.recyclerViewRecentPhotos.setAdapter(recentAdapter);

        // --- 3. Setup Selection Logic ---
        setupSelectionListeners();

        // --- 4. Setup Shift Button ---
        binding.buttonToggleShift.setOnClickListener(v -> toggleShiftState());
    }

    private void setupSelectionListeners() {
        // Listener for Scheduled Adapter
        scheduledAdapter.setSelectionListener(count -> {
            if (count > 0) {
                if (activeSelectionAdapter != scheduledAdapter) {
                    // If we switched lists, clear the other one
                    if (recentAdapter != null) recentAdapter.clearSelection();
                    activeSelectionAdapter = scheduledAdapter;
                }
                showSelectionToolbar(count);
            } else {
                // If this list is empty and it was the active one, hide toolbar
                if (activeSelectionAdapter == scheduledAdapter) {
                    hideSelectionToolbar();
                    activeSelectionAdapter = null;
                }
            }
        });

        // Listener for Recent Adapter
        recentAdapter.setSelectionListener(count -> {
            if (count > 0) {
                if (activeSelectionAdapter != recentAdapter) {
                    // If we switched lists, clear the other one
                    if (scheduledAdapter != null) scheduledAdapter.clearSelection();
                    activeSelectionAdapter = recentAdapter;
                }
                showSelectionToolbar(count);
            } else {
                if (activeSelectionAdapter == recentAdapter) {
                    hideSelectionToolbar();
                    activeSelectionAdapter = null;
                }
            }
        });

        // Toolbar Button Actions
        binding.btnCloseSelection.setOnClickListener(v -> {
            if (activeSelectionAdapter != null) activeSelectionAdapter.clearSelection();
            hideSelectionToolbar();
        });

        binding.btnSelectAll.setOnClickListener(v -> {
            if (activeSelectionAdapter != null) activeSelectionAdapter.selectAll();
        });

        binding.btnDeleteSelection.setOnClickListener(v -> {
            confirmDeletion();
        });
    }

    private void showSelectionToolbar(int count) {
        binding.cardSelectionToolbar.setVisibility(View.VISIBLE);
        binding.textSelectionCount.setText(count + " Selected");
    }

    private void hideSelectionToolbar() {
        binding.cardSelectionToolbar.setVisibility(View.GONE);
    }

    private void confirmDeletion() {
        if (activeSelectionAdapter == null) return;

        int count = activeSelectionAdapter.getSelectedIds().size();
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Photos?")
                .setMessage("Are you sure you want to delete " + count + " photo(s)? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedPhotos())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedPhotos() {
        if (activeSelectionAdapter == null) return;

        List<Long> idsToDelete = activeSelectionAdapter.getSelectedIds();
        activeSelectionAdapter.clearSelection(); // Clear UI immediately
        hideSelectionToolbar();

        databaseExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getContext());
            PhotoDao dao = db.photoDao();

            for (Long id : idsToDelete) {
                // 1. Get Photo details to find the file and cancel alarm
                Photo photo = dao.getPhotoById(id);
                if (photo != null) {
                    // 2. Cancel Alarm (Crucial for Scheduled photos)
                    Scheduler.cancelPhotoSend(getContext(), photo.getId());

                    // 3. Delete Physical File
                    try {
                        String filePath = photo.getFilePath();
                        
                        // FIXED: Check for Custom Folder (Content URI) vs Standard File
                        if (filePath != null && filePath.startsWith("content://")) {
                            // Delete using ContentResolver for SAF
                            getContext().getContentResolver().delete(Uri.parse(filePath), null, null);
                        } else {
                            // Delete standard file
                            File file = new File(filePath);
                            if (file.exists()) {
                                file.delete();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // 4. Delete from Database
            dao.deletePhotos(idsToDelete);

            // 5. Refresh UI
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getContext(), "Photos Deleted", Toast.LENGTH_SHORT).show();
                loadDashboardData(); // Reload everything
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        loadDashboardData();
        // Reset selection on resume
        if (scheduledAdapter != null) scheduledAdapter.clearSelection();
        if (recentAdapter != null) recentAdapter.clearSelection();
        hideSelectionToolbar();
    }

    /**
     * Query database for BOTH Scheduled (Pending) and Recent photos.
     */
    private void loadDashboardData() {
        if (getContext() == null) return;

        databaseExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getContext());

            // 1. Get Pending Photos (For Top Box)
            List<Photo> pendingPhotos = db.photoDao().getPendingPhotos();

            // 2. Get Recent Photos (For Bottom Box) - Limit to 10
            List<Photo> recentPhotos = db.photoDao().getRecentPhotos(10);

            // Update UI on Main Thread
            new Handler(Looper.getMainLooper()).post(() -> {
                if (binding != null) {
                    // Update Scheduled List
                    scheduledPhotoList.clear();
                    if (pendingPhotos != null) {
                        scheduledPhotoList.addAll(pendingPhotos);
                    }
                    if (scheduledAdapter != null) {
                        scheduledAdapter.notifyDataSetChanged();
                    }

                    // Handle Empty State for Scheduled
                    if (scheduledPhotoList.isEmpty()) {
                        binding.textNoScheduled.setVisibility(View.VISIBLE);
                        binding.recyclerViewScheduledSends.setVisibility(View.GONE);
                    } else {
                        binding.textNoScheduled.setVisibility(View.GONE);
                        binding.recyclerViewScheduledSends.setVisibility(View.VISIBLE);
                    }

                    // Update Recent List
                    recentPhotoList.clear();
                    if (recentPhotos != null) {
                        recentPhotoList.addAll(recentPhotos);
                    }
                    if (recentAdapter != null) {
                        recentAdapter.notifyDataSetChanged();
                    }
                }
            });
        });
    }

    /**
     * Reads the current state from SharedPreferences and updates the Button and Text.
     */
    private void updateUI() {
        if (getContext() == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_SHIFT, Context.MODE_PRIVATE);
        boolean isShiftActive = prefs.getBoolean(KEY_IS_SHIFT_ACTIVE, false);
        long lastActionTime = prefs.getLong(KEY_LAST_ACTION_TIME, 0);

        if (isShiftActive) {
            binding.textShiftStatus.setText("Status: ON DUTY");
            binding.buttonToggleShift.setText("End Shift");
        } else {
            binding.textShiftStatus.setText("Status: OFF DUTY");
            binding.buttonToggleShift.setText("Start Shift");
        }
    }

    private void toggleShiftState() {
        if (getContext() == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_SHIFT, Context.MODE_PRIVATE);
        boolean isCurrentlyActive = prefs.getBoolean(KEY_IS_SHIFT_ACTIVE, false);
        SharedPreferences.Editor editor = prefs.edit();

        if (isCurrentlyActive) {
            editor.putBoolean(KEY_IS_SHIFT_ACTIVE, false);
            editor.putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis());
            editor.apply();
            Toast.makeText(getContext(), "Shift Ended. Good job!", Toast.LENGTH_SHORT).show();
        } else {
            editor.putBoolean(KEY_IS_SHIFT_ACTIVE, true);
            editor.putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis());
            editor.apply();
            Toast.makeText(getContext(), "Shift Started. Tracking active.", Toast.LENGTH_SHORT).show();
        }

        updateUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
        }
    }
}