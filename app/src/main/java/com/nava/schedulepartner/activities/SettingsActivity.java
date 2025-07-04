package com.nava.schedulepartner.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.nava.schedulepartner.R;
import com.nava.schedulepartner.utils.AudioManager;
import com.nava.schedulepartner.utils.ScheduleManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Settings activity for managing app configuration and resources.
 * <p>
 * This activity provides access to:
 * - Schedule template import/export functionality
 * - Audio file management with zip import
 * - Global audio settings (enable/disable, volume)
 * - App information and help resources
 * <p>
 * The settings follow the app's minimalist design philosophy with
 * a black background and simple controls for easy navigation.
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class SettingsActivity extends AppCompatActivity {

    /**
     * Result launchers for file picker intents.
     * <p>
     * Using the new ActivityResult API for better lifecycle handling
     * and type safety compared to onActivityResult.
     */
    private ActivityResultLauncher<String> importScheduleLauncher;
    private ActivityResultLauncher<String> importAudioZipLauncher;
    private ActivityResultLauncher<String> exportScheduleLauncher;

    // UI Components
    private TextView tvSettingsTitle;
    private Button btnImportSchedule;
    private Button btnExportSchedule;
    private Button btnImportAudio;
    private Button btnClearAudio;
    private Switch switchAudioEnabled;
    private SeekBar seekBarVolume;
    private TextView tvVolumeLevel;
    private TextView tvAppVersion;

    // Managers
    private ScheduleManager scheduleManager;
    private AudioManager audioManager;

    /**
     * Initializes the settings interface.
     * <p>
     * Sets up file pickers, loads current settings, and configures
     * all interactive elements with appropriate listeners.
     *
     * @param savedInstanceState Saved state bundle (unused)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Set action bar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize managers
        scheduleManager = ScheduleManager.getInstance(this);
        audioManager = AudioManager.getInstance(this);

        // Initialize views
        initializeViews();

        // Set up file pickers
        setupFilePickers();

        // Load current settings
        loadCurrentSettings();

        // Set up listeners
        setupListeners();
    }

    /**
     * Finds and stores references to all UI components.
     */
    private void initializeViews() {
        tvSettingsTitle = findViewById(R.id.tvSettingsTitle);
        btnImportSchedule = findViewById(R.id.btnImportSchedule);
        btnExportSchedule = findViewById(R.id.btnExportSchedule);
        btnImportAudio = findViewById(R.id.btnImportAudio);
        btnClearAudio = findViewById(R.id.btnClearAudio);
        switchAudioEnabled = findViewById(R.id.switchAudioEnabled);
        seekBarVolume = findViewById(R.id.seekBarVolume);
        tvVolumeLevel = findViewById(R.id.tvVolumeLevel);
        tvAppVersion = findViewById(R.id.tvAppVersion);

        // Set strings
        tvSettingsTitle.setText(R.string.settings);
        btnImportSchedule.setText(R.string.import_schedule);
        btnExportSchedule.setText(R.string.export_schedule);
        btnImportAudio.setText(R.string.manage_audio);
        switchAudioEnabled.setText(R.string.global_audio_toggle);

        // Set app version
        tvAppVersion.setText("Version 1.0");
    }

    /**
     * Sets up activity result launchers for file operations.
     * <p>
     * Creates launchers for importing schedules, audio zips, and
     * exporting schedules using the Storage Access Framework.
     */
    private void setupFilePickers() {
        // Schedule import launcher
        importScheduleLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleScheduleImport(uri);
                    }
                }
        );

        // Audio zip import launcher
        importAudioZipLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleAudioZipImport(uri);
                    }
                }
        );

        // Schedule export launcher
        exportScheduleLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> {
                    if (uri != null) {
                        handleScheduleExport(uri);
                    }
                }
        );
    }

    /**
     * Loads and displays current app settings.
     * <p>
     * Retrieves audio enabled state and volume level from
     * AudioManager and updates UI to match.
     */
    private void loadCurrentSettings() {
        // Audio enabled state
        switchAudioEnabled.setChecked(audioManager.isAudioEnabled());

        // Volume level
        float volume = audioManager.getGlobalVolume();
        int volumePercent = (int) (volume * 100);
        seekBarVolume.setProgress(volumePercent);
        updateVolumeDisplay(volumePercent);

        // Enable/disable volume based on audio state
        seekBarVolume.setEnabled(audioManager.isAudioEnabled());
    }

    /**
     * Sets up click and change listeners for all controls.
     */
    private void setupListeners() {
        // Import/Export buttons
        btnImportSchedule.setOnClickListener(v -> importSchedule());
        btnExportSchedule.setOnClickListener(v -> exportSchedule());
        btnImportAudio.setOnClickListener(v -> importAudioZip());
        btnClearAudio.setOnClickListener(v -> clearAudioFiles());

        // Audio switch
        switchAudioEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            audioManager.setAudioEnabled(isChecked);
            seekBarVolume.setEnabled(isChecked);

            String message = isChecked ? "Audio enabled" : "Audio disabled";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        // Volume seekbar
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float volume = progress / 100.0f;
                    audioManager.setGlobalVolume(volume);
                    updateVolumeDisplay(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Play test sound at new volume
                audioManager.playAudio(
                        com.nava.schedulepartner.models.EventType.TRANSITION,
                        com.nava.schedulepartner.models.AlertLevel.TRANSITION,
                        1.0f
                );
            }
        });
    }

    /**
     * Updates the volume level display text.
     *
     * @param volumePercent Volume as percentage (0-100)
     */
    private void updateVolumeDisplay(int volumePercent) {
        tvVolumeLevel.setText("Volume: " + volumePercent + "%");
    }

    /**
     * Launches file picker for schedule import.
     */
    private void importSchedule() {
        importScheduleLauncher.launch("text/*");
    }

    /**
     * Handles the selected schedule file for import.
     * <p>
     * Copies the file to temporary location, validates it as
     * a schedule, and imports if valid.
     *
     * @param uri The URI of the selected file
     */
    private void handleScheduleImport(Uri uri) {
        try {
            // Copy file to temp location
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File tempFile = new File(getCacheDir(), "import_schedule.csv");

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            inputStream.close();

            // Import the schedule
            com.nava.schedulepartner.models.Schedule schedule =
                    scheduleManager.importSchedule(tempFile);

            if (schedule != null) {
                Toast.makeText(this,
                        "Imported: " + schedule.getName(),
                        Toast.LENGTH_LONG).show();

                // Notify main activity to refresh
                setResult(RESULT_OK);
            } else {
                Toast.makeText(this,
                        getString(R.string.error_invalid_csv),
                        Toast.LENGTH_LONG).show();
            }

            // Clean up temp file
            tempFile.delete();

        } catch (IOException e) {
            Toast.makeText(this,
                    "Error importing schedule: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Launches dialog to select schedule for export.
     * <p>
     * In a full implementation, this would show a list of
     * available schedules to choose from. For now, it exports
     * the first available schedule as a demo.
     */
    private void exportSchedule() {
        // For demo, export Study@Home if it exists
        com.nava.schedulepartner.models.Context context =
                com.nava.schedulepartner.models.Context.HOME;
        com.nava.schedulepartner.models.Activity activity =
                com.nava.schedulepartner.models.Activity.STUDY;

        com.nava.schedulepartner.models.Schedule schedule =
                scheduleManager.loadSchedule(context, activity);

        if (schedule != null) {
            String filename = schedule.getFilename();
            exportScheduleLauncher.launch(filename);
        } else {
            Toast.makeText(this,
                    "No schedules available to export",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handles schedule export to selected location.
     *
     * @param uri The URI to export to
     */
    private void handleScheduleExport(Uri uri) {
        // For demo, export Study@Home
        com.nava.schedulepartner.models.Context context =
                com.nava.schedulepartner.models.Context.HOME;
        com.nava.schedulepartner.models.Activity activity =
                com.nava.schedulepartner.models.Activity.STUDY;

        com.nava.schedulepartner.models.Schedule schedule =
                scheduleManager.loadSchedule(context, activity);

        if (schedule != null) {
            try {
                FileOutputStream fos = (FileOutputStream)
                        getContentResolver().openOutputStream(uri);
                fos.write(schedule.toCsv().getBytes());
                fos.close();

                Toast.makeText(this,
                        "Schedule exported successfully",
                        Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this,
                        "Error exporting schedule: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Launches file picker for audio zip import.
     */
    private void importAudioZip() {
        importAudioZipLauncher.launch("application/zip");
    }

    /**
     * Handles the selected audio zip file for import.
     *
     * @param uri The URI of the selected zip file
     */
    private void handleAudioZipImport(Uri uri) {
        try {
            // Copy zip to temp location
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File tempFile = new File(getCacheDir(), "audio_import.zip");

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            inputStream.close();

            // Import audio files
            int importedCount = audioManager.importAudioZip(tempFile);

            if (importedCount > 0) {
                Toast.makeText(this,
                        "Imported " + importedCount + " audio files",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        "No valid audio files found in zip",
                        Toast.LENGTH_LONG).show();
            }

            // Clean up temp file
            tempFile.delete();

        } catch (IOException e) {
            Toast.makeText(this,
                    "Error importing audio: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Clears all imported audio files after confirmation.
     */
    private void clearAudioFiles() {
        // In production, show confirmation dialog
        // For now, just clear
        audioManager.clearAllAudio();
        Toast.makeText(this,
                "All audio files cleared",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Handles navigation up button.
     *
     * @return True to indicate event handled
     */
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}