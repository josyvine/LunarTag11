package com.lunartag.app.ui.settings;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.lunartag.app.R;
import com.lunartag.app.databinding.FragmentSettingsBinding;
import com.lunartag.app.services.OverlayService;

import java.util.Calendar;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    // General Settings Storage
    private static final String PREFS_NAME = "LunarTagSettings";
    private static final String KEY_COMPANY_NAME = "company_name";
    private static final String KEY_SHIFT_START = "shift_start";
    private static final String KEY_SHIFT_END = "shift_end";
    private static final String KEY_WHATSAPP_GROUP = "whatsapp_group";
    
    // NEW: WhatsApp Method (Option A vs Option B)
    private static final String KEY_WA_METHOD = "wa_automation_method"; // "red_box" or "coordinate"

    // Robot Settings Storage (AccessPrefs)
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_APP_LABEL = "target_app_label";

    private FragmentSettingsBinding binding;
    private SharedPreferences settingsPrefs;
    private SharedPreferences accessPrefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        settingsPrefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        accessPrefs = requireActivity().getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadSettings();
        setupClickListeners();

        // This method will now show a toast with the admin flag's value
        setupAdminFeatures();
    }

    private void setupClickListeners() {
        // Listener for the Save button
        binding.buttonSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        // Listener for the Shift Start time picker
        binding.editTextShiftStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(true);
            }
        });

        // Listener for the Shift End time picker
        binding.editTextShiftEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(false);
            }
        });

        // --- EXISTING: CALIBRATE SHARE ICON (Sequence 0) ---
        binding.buttonCalibrateShareIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTraining("MODE_SHARE");
                Toast.makeText(getContext(), "Open Share Sheet & Drag Target to Icon!", Toast.LENGTH_LONG).show();
            }
        });

        // --- NEW: TRAIN GROUP (Sequence 1) ---
        binding.buttonTrainGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTraining("MODE_GROUP");
                Toast.makeText(getContext(), "Open WhatsApp List & Drag to Group!", Toast.LENGTH_LONG).show();
            }
        });

        // --- NEW: TRAIN CHAT ICON (Sequence 2) ---
        binding.buttonTrainChatSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTraining("MODE_CHAT");
                Toast.makeText(getContext(), "Open Chat & Drag to Send/Attach Icon!", Toast.LENGTH_LONG).show();
            }
        });

        // --- NEW: TRAIN PREVIEW SEND (Sequence 3) ---
        binding.buttonTrainPreviewSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTraining("MODE_PREVIEW");
                Toast.makeText(getContext(), "Open Image Preview & Drag to Send Button!", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startTraining(String mode) {
        // 1. Minimize the App (Go to Home)
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);

        // 2. Start Overlay Service with specific MODE
        Intent intent = new Intent(requireContext(), OverlayService.class);
        intent.setAction("ACTION_START_TRAINING");
        intent.putExtra("TRAIN_MODE", mode); // Pass the ID so OverlayService knows what to save
        requireContext().startService(intent);
    }

    private void loadSettings() {
        // 1. Load General Settings
        String companyName = settingsPrefs.getString(KEY_COMPANY_NAME, "");
        String shiftStart = settingsPrefs.getString(KEY_SHIFT_START, "00:00 AM");
        String shiftEnd = settingsPrefs.getString(KEY_SHIFT_END, "00:00 AM");
        String whatsappGroup = settingsPrefs.getString(KEY_WHATSAPP_GROUP, "");
        
        // Load Method Choice (Default: Red Box)
        String waMethod = settingsPrefs.getString(KEY_WA_METHOD, "red_box");

        binding.editTextCompanyName.setText(companyName);
        binding.editTextShiftStart.setText(shiftStart);
        binding.editTextShiftEnd.setText(shiftEnd);
        binding.editTextWhatsappGroup.setText(whatsappGroup);

        if (waMethod.equals("coordinate")) {
            binding.radioMethodCoordinate.setChecked(true);
        } else {
            binding.radioMethodRedBox.setChecked(true);
        }

        // 2. Load Robot Target App Name
        String targetApp = accessPrefs.getString(KEY_TARGET_APP_LABEL, "");
        binding.editTextTargetApp.setText(targetApp);
    }

    private void saveSettings() {
        // 1. Save General Settings
        SharedPreferences.Editor editor = settingsPrefs.edit();
        editor.putString(KEY_COMPANY_NAME, binding.editTextCompanyName.getText().toString().trim());
        editor.putString(KEY_SHIFT_START, binding.editTextShiftStart.getText().toString());
        editor.putString(KEY_SHIFT_END, binding.editTextShiftEnd.getText().toString());
        editor.putString(KEY_WHATSAPP_GROUP, binding.editTextWhatsappGroup.getText().toString().trim());
        
        // Save Method Choice
        if (binding.radioMethodCoordinate.isChecked()) {
            editor.putString(KEY_WA_METHOD, "coordinate");
        } else {
            editor.putString(KEY_WA_METHOD, "red_box");
        }
        
        editor.apply();

        // 2. Save Robot Target App Name
        SharedPreferences.Editor accessEditor = accessPrefs.edit();
        accessEditor.putString(KEY_TARGET_APP_LABEL, binding.editTextTargetApp.getText().toString().trim());
        accessEditor.apply();

        Toast.makeText(getContext(), "All Settings Saved!", Toast.LENGTH_SHORT).show();
    }

    private void showTimePickerDialog(final boolean isStartTime) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        String amPm;
                        int hourFormatted;

                        if (hourOfDay >= 12) {
                            amPm = "PM";
                            hourFormatted = (hourOfDay == 12) ? 12 : hourOfDay - 12;
                        } else {
                            amPm = "AM";
                            hourFormatted = (hourOfDay == 0) ? 12 : hourOfDay;
                        }

                        String timeString = String.format(Locale.US, "%02d:%02d %s", hourFormatted, minute, amPm);

                        if (isStartTime) {
                            binding.editTextShiftStart.setText(timeString);
                        } else {
                            binding.editTextShiftEnd.setText(timeString);
                        }
                    }
                }, hour, minute, false); // false for 12-hour format

        timePickerDialog.show();
    }

    private void setupAdminFeatures() {
        SharedPreferences featureTogglePrefs = requireActivity().getSharedPreferences("LunarTagFeatureToggles", Context.MODE_PRIVATE);
        boolean isAdminModeEnabled = featureTogglePrefs.getBoolean("customTimestampEnabled", false);

        Toast.makeText(getContext(), "Admin Flag is: " + isAdminModeEnabled, Toast.LENGTH_LONG).show();

        if (isAdminModeEnabled) {
            binding.buttonAdminScheduleEditor.setVisibility(View.VISIBLE);
            binding.buttonAdminScheduleEditor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavHostFragment.findNavController(SettingsFragment.this)
                            .navigate(R.id.action_settings_to_schedule_editor);
                }
            });
        } else {
            binding.buttonAdminScheduleEditor.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}