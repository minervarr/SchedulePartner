package com.nava.schedulepartner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nava.schedulepartner.activities.CoachingActivity;
import com.nava.schedulepartner.activities.SettingsActivity;
import com.nava.schedulepartner.models.Activity;
import com.nava.schedulepartner.models.Context;
import com.nava.schedulepartner.models.Schedule;
import com.nava.schedulepartner.utils.ScheduleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Discipline Coach application.
 * <p>
 * This activity implements the Context+Activity selection system that forms
 * the foundation of personalized scheduling. Users select WHERE they are
 * (Context) and WHAT they want to do (Activity) to load the appropriate
 * schedule template for their coaching session.
 * <p>
 * Key responsibilities:
 * - Present context and activity selection UI
 * - Load and validate schedule templates
 * - Launch coaching sessions with selected parameters
 * - Provide access to settings and schedule management
 * - Remember user's last selection for quick access
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class MainActivity extends AppCompatActivity {

    /**
     * SharedPreferences key for storing app preferences.
     * <p>
     * Used to remember user's last context/activity selection
     * and global app settings.
     */
    private static final String PREFS_NAME = "DisciplineCoachPrefs";

    /**
     * Key for storing last selected context.
     */
    private static final String PREF_LAST_CONTEXT = "last_context";

    /**
     * Key for storing last selected activity.
     */
    private static final String PREF_LAST_ACTIVITY = "last_activity";

    /**
     * Request code for settings activity result.
     */
    private static final int REQUEST_SETTINGS = 1001;

    // UI Components
    private TextView tvWelcome;
    private TextView tvSelectContext;
    private Spinner spinnerContext;
    private TextView tvSelectActivity;
    private Spinner spinnerActivity;
    private Button btnStartSession;
    private Button btnSettings;

    // Data
    private Context selectedContext;
    private Activity selectedActivity;
    private ScheduleManager scheduleManager;

    /**
     * Initializes the activity and sets up the UI.
     * <p>
     * Creates the context/activity selection interface and restores
     * the user's previous selection if available. Also initializes
     * the schedule management system.
     *
     * @param savedInstanceState Saved state bundle (unused in this implementation)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize schedule manager
        scheduleManager = ScheduleManager.getInstance(this);

        // Initialize UI components
        initializeViews();

        // Set up spinners with data
        setupSpinners();

        // Restore last selection
        restoreLastSelection();

        // Set up click listeners
        setupClickListeners();

        // Request notification permission if needed (Android 13+)
        requestNotificationPermission();
    }

    /**
     * Finds and stores references to all UI components.
     * <p>
     * Called once during onCreate to avoid repeated findViewById calls.
     * All view references are stored as instance variables for easy access.
     */
    private void initializeViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvSelectContext = findViewById(R.id.tvSelectContext);
        spinnerContext = findViewById(R.id.spinnerContext);
        tvSelectActivity = findViewById(R.id.tvSelectActivity);
        spinnerActivity = findViewById(R.id.spinnerActivity);
        btnStartSession = findViewById(R.id.btnStartSession);
        btnSettings = findViewById(R.id.btnSettings);

        // Set welcome message from strings
        tvWelcome.setText(R.string.welcome_message);
        tvSelectContext.setText(R.string.select_context);
        tvSelectActivity.setText(R.string.select_activity);
        btnStartSession.setText(R.string.start_session);
        btnSettings.setText(R.string.settings);
    }

    /**
     * Configures the context and activity spinners with data.
     * <p>
     * Populates spinners with available contexts and activities,
     * using display names for user-friendly presentation. Sets up
     * selection listeners to track user choices.
     */
    private void setupSpinners() {
        // Create context list
        List<String> contextNames = new ArrayList<>();
        for (Context context : Context.values()) {
            contextNames.add(getContextDisplayName(context));
        }

        // Set up context spinner
        ArrayAdapter<String> contextAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                contextNames
        );
        contextAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        spinnerContext.setAdapter(contextAdapter);

        // Context selection listener
        spinnerContext.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedContext = Context.values()[position];
                updateStartButton();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedContext = null;
                updateStartButton();
            }
        });

        // Create activity list
        List<String> activityNames = new ArrayList<>();
        for (Activity activity : Activity.values()) {
            activityNames.add(getActivityDisplayName(activity));
        }

        // Set up activity spinner
        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                activityNames
        );
        activityAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        spinnerActivity.setAdapter(activityAdapter);

        // Activity selection listener
        spinnerActivity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedActivity = Activity.values()[position];
                updateStartButton();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedActivity = null;
                updateStartButton();
            }
        });
    }

    /**
     * Gets the localized display name for a context.
     * <p>
     * Uses string resources for proper internationalization.
     * Falls back to enum name if resource not found.
     *
     * @param context The context to get display name for
     * @return Localized display name
     */
    private String getContextDisplayName(Context context) {
        switch (context) {
            case HOME:
                return getString(R.string.context_home);
            case UNIVERSITY:
                return getString(R.string.context_university);
            case OFFICE:
                return getString(R.string.context_office);
            case LIBRARY:
                return getString(R.string.context_library);
            case GYM:
                return getString(R.string.context_gym);
            case TRAVEL:
                return getString(R.string.context_travel);
            default:
                return context.getDisplayName();
        }
    }

    /**
     * Gets the localized display name for an activity.
     * <p>
     * Uses string resources for proper internationalization.
     * Falls back to enum name if resource not found.
     *
     * @param activity The activity to get display name for
     * @return Localized display name
     */
    private String getActivityDisplayName(Activity activity) {
        switch (activity) {
            case STUDY:
                return getString(R.string.activity_study);
            case WORK:
                return getString(R.string.activity_work);
            case REST:
                return getString(R.string.activity_rest);
            case EXERCISE:
                return getString(R.string.activity_exercise);
            case RESEARCH:
                return getString(R.string.activity_research);
            default:
                return activity.getDisplayName();
        }
    }

    /**
     * Restores the user's last context/activity selection.
     * <p>
     * Reads from SharedPreferences and sets spinner selections
     * to match previous session. Helps users quickly restart
     * their most common coaching configurations.
     */
    private void restoreLastSelection() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String lastContext = prefs.getString(PREF_LAST_CONTEXT, null);
        if (lastContext != null) {
            Context context = Context.fromIdentifier(lastContext);
            if (context != null) {
                spinnerContext.setSelection(context.ordinal());
            }
        }

        String lastActivity = prefs.getString(PREF_LAST_ACTIVITY, null);
        if (lastActivity != null) {
            Activity activity = Activity.fromIdentifier(lastActivity);
            if (activity != null) {
                spinnerActivity.setSelection(activity.ordinal());
            }
        }
    }

    /**
     * Saves the current context/activity selection.
     * <p>
     * Stores in SharedPreferences for restoration on next app launch.
     * Called when user starts a coaching session.
     */
    private void saveCurrentSelection() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (selectedContext != null) {
            editor.putString(PREF_LAST_CONTEXT, selectedContext.getIdentifier());
        }

        if (selectedActivity != null) {
            editor.putString(PREF_LAST_ACTIVITY, selectedActivity.getIdentifier());
        }

        editor.apply();
    }

    /**
     * Sets up click listeners for all buttons.
     * <p>
     * Configures actions for starting coaching sessions and
     * accessing settings. Validates selections before proceeding.
     */
    private void setupClickListeners() {
        btnStartSession.setOnClickListener(v -> startCoachingSession());
        btnSettings.setOnClickListener(v -> openSettings());
    }

    /**
     * Updates the start button enabled state.
     * <p>
     * Button is only enabled when both context and activity
     * are selected, preventing incomplete session starts.
     */
    private void updateStartButton() {
        btnStartSession.setEnabled(
                selectedContext != null && selectedActivity != null
        );
    }

    /**
     * Starts a coaching session with selected parameters.
     * <p>
     * Validates that a schedule exists for the selected combination,
     * saves the selection for next time, and launches the coaching
     * activity with appropriate parameters.
     */
    private void startCoachingSession() {
        if (selectedContext == null || selectedActivity == null) {
            Toast.makeText(this,
                    getString(R.string.please_select_both),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if schedule exists
        Schedule schedule = scheduleManager.loadSchedule(
                selectedContext, selectedActivity);

        if (schedule == null) {
            Toast.makeText(this,
                    getString(R.string.error_no_template),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Save selection
        saveCurrentSelection();

        // Launch coaching activity
        Intent intent = new Intent(this, CoachingActivity.class);
        intent.putExtra(CoachingActivity.EXTRA_CONTEXT,
                selectedContext.getIdentifier());
        intent.putExtra(CoachingActivity.EXTRA_ACTIVITY,
                selectedActivity.getIdentifier());
        startActivity(intent);
    }

    /**
     * Opens the settings activity.
     * <p>
     * Launches settings for schedule management, audio configuration,
     * and other app preferences. Uses startActivityForResult to
     * handle any changes that might affect the main screen.
     */
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_SETTINGS);
    }

    /**
     * Handles results from other activities.
     * <p>
     * Currently used for settings activity to refresh data
     * if schedules were imported or modified.
     *
     * @param requestCode The request code from startActivityForResult
     * @param resultCode The result code from the called activity
     * @param data Any returned data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            // Refresh schedule manager in case templates were imported
            scheduleManager.refreshSchedules();
        }
    }

    /**
     * Requests notification permission for Android 13+.
     * <p>
     * Required for posting notifications on newer Android versions.
     * Shows system permission dialog if not already granted.
     */
    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1002
                );
            }
        }
    }

    /**
     * Handles permission request results.
     * <p>
     * Shows appropriate message if notification permission is denied,
     * as the app requires notifications for core functionality.
     *
     * @param requestCode The request code from requestPermissions
     * @param permissions The requested permissions
     * @param grantResults The grant results for each permission
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1002) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this,
                        "Notifications enabled",
                        Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this,
                        "Notifications are required for coaching alerts",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}