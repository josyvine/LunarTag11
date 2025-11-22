package com.lunartag.app.ui.robot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lunartag.app.R;

/**
 * The Robot Fragment.
 * Allows the user to select between "Semi-Automatic" and "Full-Automatic" modes.
 * UPDATED: Fixed the bug where both buttons remained selected by handling logic manually.
 */
public class RobotFragment extends Fragment {

    // Must match LunarTagAccessibilityService constants
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_AUTO_MODE = "automation_mode"; // Values: "semi" or "full"

    private RadioButton radioSemi;
    private RadioButton radioFull;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_robot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // We bind to the buttons directly to bypass RadioGroup nesting issues
        radioSemi = view.findViewById(R.id.radio_semi);
        radioFull = view.findViewById(R.id.radio_full);

        // 1. Load saved state
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        String currentMode = prefs.getString(KEY_AUTO_MODE, "semi");

        // 2. Update UI based on saved state
        if (currentMode.equals("full")) {
            radioFull.setChecked(true);
            radioSemi.setChecked(false);
        } else {
            radioSemi.setChecked(true);
            radioFull.setChecked(false);
        }

        // 3. LISTENERS (Manual Exclusion Logic)
        // We use OnClickListener instead of OnCheckedChange to ensure
        // completely reliable toggling regardless of XML layout structure.

        radioSemi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Uncheck the other one
                radioFull.setChecked(false);
                
                // Save State
                prefs.edit().putString(KEY_AUTO_MODE, "semi").apply();
                
                Toast.makeText(getContext(), "Mode: Semi-Automatic (Human Verified)", Toast.LENGTH_SHORT).show();
            }
        });

        radioFull.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Uncheck the other one
                radioSemi.setChecked(false);
                
                // Save State
                prefs.edit().putString(KEY_AUTO_MODE, "full").apply();
                
                Toast.makeText(getContext(), "Mode: Full-Automatic (Zero Click)", Toast.LENGTH_SHORT).show();
            }
        });
    }
}