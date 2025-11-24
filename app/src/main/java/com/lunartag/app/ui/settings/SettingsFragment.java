package com.lunartag.app.ui.settings;

import android.app.TimePickerDialog;
import android.content.Context;
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

import java.util.Calendar;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    // General Settings Storage
    private static final String PREFS_NAME = "LunarTagSettings";
    private static final String KEY_COMPANY_NAME = "company_name";
    private static final String KEY_SHIFT_START = "shift_start";
    private static final String KEY_SHIFT_END = "shift_end";
    private static final String KEY_WHATSAPP_GROUP = "whatsapp_group";

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
    }

    private void loadSettings() {
        // 1. Load General Settings
        String companyName = settingsPrefs.getString(KEY_COMPANY_NAME, "");
        String shiftStart = settingsPrefs.getString(KEY_SHIFT_START, "00:00 AM");
        String shiftEnd = settingsPrefs.getString(KEY_SHIFT_END, "00:00 AM");
        String whatsappGroup = settingsPrefs.getString(KEY_WHATSAPP_GROUP, "");

        binding.editTextCompanyName.setText(companyName);
        binding.editTextShiftStart.setText(shiftStart);
        binding.editTextShiftEnd.setText(shiftEnd);
        binding.editTextWhatsappGroup.setText(whatsappGroup);

        // 2. Load Robot Target App Name
        // This allows you to see what is currently set (e.g. "WhatsApp(Clone)")
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
        editor.apply();

        // 2. Save Robot Target App Name
        // This overwrites whatever was selected in the Apps tab, giving you manual control
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

    /**
     * This method checks for the admin feature toggle and configures the UI accordingly.
     */
    private void setupAdminFeatures() {
        // Access the feature toggles preferences that are set by the Firebase service
        SharedPreferences featureTogglePrefs = requireActivity().getSharedPreferences("LunarTagFeatureToggles", Context.MODE_PRIVATE);
        boolean isAdminModeEnabled = featureTogglePrefs.getBoolean("customTimestampEnabled", false);

        // THIS IS THE IMPORTANT DEBUG LINE
        Toast.makeText(getContext(), "Admin Flag is: " + isAdminModeEnabled, Toast.LENGTH_LONG).show();

        if (isAdminModeEnabled) {
            // If the flag is true, make the admin button visible
            binding.buttonAdminScheduleEditor.setVisibility(View.VISIBLE);

            // Add a click listener to the button to navigate to the schedule editor screen
            binding.buttonAdminScheduleEditor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavHostFragment.findNavController(SettingsFragment.this)
                            .navigate(R.id.action_settings_to_schedule_editor);
                }
            });
        } else {
            // If the flag is false, ensure the button is hidden from regular users
            binding.buttonAdminScheduleEditor.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}