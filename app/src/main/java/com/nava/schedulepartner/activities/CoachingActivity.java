package com.nava.schedulepartner.activities;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.nava.schedulepartner.R;
import com.nava.schedulepartner.models.Activity;
import com.nava.schedulepartner.models.AlertLevel;
import com.nava.schedulepartner.models.Schedule;
import com.nava.schedulepartner.models.ScheduleEvent;
import com.nava.schedulepartner.services.CoachingService;
import com.nava.schedulepartner.utils.AudioManager;
import com.nava.schedulepartner.utils.ScheduleManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The main coaching interface that displays real-time schedule guidance.
 * <p>
 * This activity implements the minute-by-minute coaching experience with
 * visual cues (red/blue labels) and audio alerts. It shows the current
 * event prominently and upcoming events in a chronological list. The
 * interface is optimized for AMOLED screens with a pure black background
 * and minimal UI elements.
 * <p>
 * Key features:
 * - Real-time event tracking and display
 * - Automatic event progression
 * - Audio mute toggle with visual feedback
 * - Do Not Disturb management for focus activities
 * - Emergency pause/stop functionality
 * - Background service binding for notifications
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class CoachingActivity extends AppCompatActivity implements CoachingService.CoachingListener {

    /**
     * Intent extra key for context identifier.
     */
    public static final String EXTRA_CONTEXT = "extra_context";

    /**
     * Intent extra key for activity identifier.
     */
    public static final String EXTRA_ACTIVITY = "extra_activity";

    /**
     * Update interval for UI refresh in milliseconds.
     * <p>
     * Set to 1 second for smooth minute transitions while
     * balancing battery efficiency.
     */
    private static final long UPDATE_INTERVAL_MS = 1000;

    /**
     * Time formatter for displaying current time.
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    // UI Components
    private TextView tvCurrentTime;
    private TextView tvSessionInfo;
    private LinearLayout layoutCurrentEvent;
    private TextView tvCurrentEventLabel;
    private TextView tvCurrentEventTime;
    private TextView tvTimeUntilNext;
    private LinearLayout layoutUpcomingEvents;
    private ImageButton btnMuteAudio;
    private Button btnPauseSession;
    private Button btnEndSession;

    // Data and Services
    private com.nava.schedulepartner.models.Context sessionContext;
    private Activity sessionActivity;
    private Schedule schedule;
    private ScheduleEvent currentEvent;
    private AudioManager audioManager;
    private CoachingService coachingService;
    private boolean serviceBound = false;
    private boolean sessionPaused = false;

    // UI Update Handler
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateUI();
            updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    /**
     * Service connection for binding to CoachingService.
     * <p>
     * Manages the connection lifecycle and provides access to
     * service methods for notification management.
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CoachingService.LocalBinder binder = (CoachingService.LocalBinder) service;
            coachingService = binder.getService();
            coachingService.setCoachingListener(CoachingActivity.this);
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    /**
     * Initializes the coaching session with selected parameters.
     * <p>
     * Sets up the full-screen black interface, loads the schedule,
     * starts the background service, and begins real-time updates.
     *
     * @param savedInstanceState Saved state bundle (unused)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coaching);

        // Keep screen on during coaching
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get session parameters from intent
        if (!loadSessionParameters()) {
            finish();
            return;
        }

        // Initialize components
        initializeViews();
        setupClickListeners();

        // Load schedule
        if (!loadSchedule()) {
            finish();
            return;
        }

        // Initialize audio manager
        audioManager = AudioManager.getInstance(this);
        updateAudioButton();

        // Start coaching service
        startCoachingService();

        // Enable DND if needed
        enableDndIfRequired();

        // Start UI updates
        updateHandler.post(updateRunnable);
    }

    /**
     * Loads context and activity from intent extras.
     *
     * @return True if parameters loaded successfully
     */
    private boolean loadSessionParameters() {
        String contextId = getIntent().getStringExtra(EXTRA_CONTEXT);
        String activityId = getIntent().getStringExtra(EXTRA_ACTIVITY);

        if (contextId == null || activityId == null) {
            Toast.makeText(this, "Missing session parameters", Toast.LENGTH_SHORT).show();
            return false;
        }

        sessionContext = com.nava.schedulepartner.models.Context.fromIdentifier(contextId);
        sessionActivity = Activity.fromIdentifier(activityId);

        if (sessionContext == null || sessionActivity == null) {
            Toast.makeText(this, "Invalid session parameters", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * Finds and initializes all UI components.
     */
    private void initializeViews() {
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvSessionInfo = findViewById(R.id.tvSessionInfo);
        layoutCurrentEvent = findViewById(R.id.layoutCurrentEvent);
        tvCurrentEventLabel = findViewById(R.id.tvCurrentEventLabel);
        tvCurrentEventTime = findViewById(R.id.tvCurrentEventTime);
        tvTimeUntilNext = findViewById(R.id.tvTimeUntilNext);
        layoutUpcomingEvents = findViewById(R.id.layoutUpcomingEvents);
        btnMuteAudio = findViewById(R.id.btnMuteAudio);
        btnPauseSession = findViewById(R.id.btnPauseSession);
        btnEndSession = findViewById(R.id.btnEndSession);

        // Set session info
        String sessionInfo = sessionActivity.getDisplayName() + " @ " +
                sessionContext.getDisplayName();
        tvSessionInfo.setText(sessionInfo);
    }

    /**
     * Sets up click listeners for all interactive elements.
     */
    private void setupClickListeners() {
        btnMuteAudio.setOnClickListener(v -> toggleAudioMute());
        btnPauseSession.setOnClickListener(v -> togglePause());
        btnEndSession.setOnClickListener(v -> confirmEndSession());
    }

    /**
     * Loads the schedule for the current session.
     *
     * @return True if schedule loaded successfully
     */
    private boolean loadSchedule() {
        ScheduleManager scheduleManager = ScheduleManager.getInstance(this);
        schedule = scheduleManager.loadSchedule(sessionContext, sessionActivity);

        if (schedule == null) {
            Toast.makeText(this, getString(R.string.error_loading_template),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    /**
     * Starts the coaching service for background notifications.
     */
    private void startCoachingService() {
        Intent serviceIntent = new Intent(this, CoachingService.class);
        serviceIntent.putExtra(CoachingService.EXTRA_CONTEXT, sessionContext.getIdentifier());
        serviceIntent.putExtra(CoachingService.EXTRA_ACTIVITY, sessionActivity.getIdentifier());

        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Enables Do Not Disturb if required by context or activity.
     * <p>
     * Requests DND access permission if not granted. Automatically
     * enables DND for focus activities and quiet contexts.
     */
    private void enableDndIfRequired() {
        if (sessionContext.shouldAutoEnableDND() || sessionActivity.requiresFocus()) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager.isNotificationPolicyAccessGranted()) {
                notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            } else {
                // Request DND access
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            }
        }
    }

    /**
     * Updates the entire UI based on current time and schedule.
     * <p>
     * Called every second to ensure accurate display of current
     * events and time remaining. Handles event transitions and
     * audio playback.
     */
    private void updateUI() {
        if (sessionPaused) {
            return;
        }

        LocalTime now = LocalTime.now();

        // Update current time
        tvCurrentTime.setText(now.format(TIME_FORMATTER));

        // Check for current event
        ScheduleEvent newCurrentEvent = schedule.getCurrentEvent(now);

        // Handle event change
        if (hasEventChanged(newCurrentEvent)) {
            handleEventChange(newCurrentEvent);
        }

        // Update current event display
        updateCurrentEventDisplay(now);

        // Update upcoming events
        updateUpcomingEvents(now);

        // Update time until next
        updateTimeUntilNext(now);
    }

    /**
     * Checks if the current event has changed.
     *
     * @param newEvent The potentially new current event
     * @return True if event has changed
     */
    private boolean hasEventChanged(ScheduleEvent newEvent) {
        if (currentEvent == null && newEvent == null) {
            return false;
        }

        if (currentEvent == null || newEvent == null) {
            return true;
        }

        return !currentEvent.equals(newEvent);
    }

    /**
     * Handles transition to a new event.
     * <p>
     * Plays audio alert and updates visual display when a new
     * event becomes active.
     *
     * @param newEvent The new current event (may be null)
     */
    private void handleEventChange(ScheduleEvent newEvent) {
        currentEvent = newEvent;

        if (currentEvent != null && !sessionPaused) {
            // Play audio for new event
            float contextVolume = sessionContext.getDefaultVolumeMultiplier();
            audioManager.playAudio(
                    currentEvent.getEventType(),
                    currentEvent.getAlertLevel(),
                    contextVolume
            );
        }
    }

    /**
     * Updates the current event display panel.
     *
     * @param now Current time for display calculations
     */
    private void updateCurrentEventDisplay(LocalTime now) {
        if (currentEvent != null) {
            layoutCurrentEvent.setVisibility(View.VISIBLE);

            // Set label text and color
            tvCurrentEventLabel.setText(currentEvent.getDisplayMessage());
            int color = currentEvent.getEventType().isRestriction() ?
                    R.color.red_label : R.color.blue_label;
            tvCurrentEventLabel.setTextColor(ContextCompat.getColor(this, color));

            // Show event time or duration
            if (currentEvent.hasDuration()) {
                String timeText = currentEvent.getTimeString() + " - " +
                        currentEvent.getEndTime().format(TIME_FORMATTER);
                tvCurrentEventTime.setText(timeText);
            } else {
                tvCurrentEventTime.setText(currentEvent.getTimeString());
            }
        } else {
            layoutCurrentEvent.setVisibility(View.GONE);
        }
    }

    /**
     * Updates the upcoming events list.
     *
     * @param now Current time for filtering upcoming events
     */
    private void updateUpcomingEvents(LocalTime now) {
        layoutUpcomingEvents.removeAllViews();

        // Get next 5 events within 3 hours
        List<ScheduleEvent> upcoming = schedule.getUpcomingEvents(now, 180);
        int count = Math.min(5, upcoming.size());

        for (int i = 0; i < count; i++) {
            ScheduleEvent event = upcoming.get(i);
            View eventView = createUpcomingEventView(event, now);
            layoutUpcomingEvents.addView(eventView);
        }

        // Show placeholder if no upcoming events
        if (count == 0) {
            TextView noEvents = new TextView(this);
            noEvents.setText(getString(R.string.no_upcoming_events));
            noEvents.setTextColor(ContextCompat.getColor(this, R.color.white_secondary));
            noEvents.setTextSize(16);
            noEvents.setPadding(0, 16, 0, 16);
            layoutUpcomingEvents.addView(noEvents);
        }
    }

    /**
     * Creates a view for an upcoming event in the list.
     *
     * @param event The event to display
     * @param now Current time for relative calculations
     * @return The created view
     */
    private View createUpcomingEventView(ScheduleEvent event, LocalTime now) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0, 8, 0, 8);

        // Time text
        TextView timeText = new TextView(this);
        timeText.setText(event.getTimeString());
        timeText.setTextColor(ContextCompat.getColor(this, R.color.white_secondary));
        timeText.setTextSize(14);
        timeText.setWidth(120);
        layout.addView(timeText);

        // Event label
        TextView labelText = new TextView(this);
        labelText.setText(event.getDisplayMessage());
        int color = event.getEventType().isRestriction() ?
                R.color.red_label : R.color.blue_label;
        labelText.setTextColor(ContextCompat.getColor(this, color));
        labelText.setTextSize(16);
        layout.addView(labelText);

        return layout;
    }

    /**
     * Updates the time until next event display.
     *
     * @param now Current time for calculation
     */
    private void updateTimeUntilNext(LocalTime now) {
        ScheduleEvent nextEvent = schedule.getNextEvent(now);
        if (nextEvent != null) {
            long minutesUntil = nextEvent.getMinutesUntil(now);

            if (minutesUntil > 0) {
                String timeText;
                if (minutesUntil >= 60) {
                    long hours = minutesUntil / 60;
                    long minutes = minutesUntil % 60;
                    timeText = String.format(Locale.US, "%dh %dm", hours, minutes);
                } else {
                    timeText = minutesUntil + " min";
                }

                tvTimeUntilNext.setText(getString(R.string.time_until_next_format, timeText));
                tvTimeUntilNext.setVisibility(View.VISIBLE);
            } else {
                tvTimeUntilNext.setVisibility(View.GONE);
            }
        } else {
            tvTimeUntilNext.setVisibility(View.GONE);
        }
    }

    /**
     * Toggles audio mute state.
     */
    private void toggleAudioMute() {
        boolean newState = !audioManager.isAudioEnabled();
        audioManager.setAudioEnabled(newState);
        updateAudioButton();

        String message = newState ?
                getString(R.string.unmute_audio) :
                getString(R.string.mute_audio);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Updates audio button icon based on mute state.
     */
    private void updateAudioButton() {
        int iconRes = audioManager.isAudioEnabled() ?
                android.R.drawable.ic_lock_silent_mode_off :
                android.R.drawable.ic_lock_silent_mode;
        btnMuteAudio.setImageResource(iconRes);
    }

    /**
     * Toggles session pause state.
     */
    private void togglePause() {
        sessionPaused = !sessionPaused;

        if (sessionPaused) {
            btnPauseSession.setText(getString(R.string.resume));
            String sessionInfo = sessionActivity.getDisplayName() + " @ " +
                    sessionContext.getDisplayName();
            tvSessionInfo.setText(getString(R.string.session_info_format,
                    sessionInfo, getString(R.string.session_paused)));
            audioManager.stopCurrentAudio();
        } else {
            btnPauseSession.setText(getString(R.string.pause));
            String sessionInfo = sessionActivity.getDisplayName() + " @ " +
                    sessionContext.getDisplayName();
            tvSessionInfo.setText(sessionInfo);
        }

        // Notify service
        if (serviceBound) {
            coachingService.setPaused(sessionPaused);
        }
    }

    /**
     * Shows confirmation dialog before ending session.
     */
    private void confirmEndSession() {
        // In production, show a proper dialog
        // For now, just end the session
        endSession();
    }

    /**
     * Ends the coaching session and returns to main screen.
     */
    private void endSession() {
        // Stop service
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        Intent stopIntent = new Intent(this, CoachingService.class);
        stopService(stopIntent);

        // Disable DND if we enabled it
        disableDndIfEnabled();

        // Return to main
        finish();
    }

    /**
     * Disables Do Not Disturb if it was enabled by the app.
     */
    private void disableDndIfEnabled() {
        if (sessionContext.shouldAutoEnableDND() || sessionActivity.requiresFocus()) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager.isNotificationPolicyAccessGranted()) {
                notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHandler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        updateHandler.removeCallbacks(updateRunnable);
    }

    /**
     * Called by service when an event triggers in background.
     *
     * @param event The event that triggered
     */
    @Override
    public void onEventTriggered(ScheduleEvent event) {
        runOnUiThread(() -> {
            // Refresh UI to show the new event
            updateUI();
        });
    }

    @Override
    public void onBackPressed() {
        // Require confirmation to exit during active session
        confirmEndSession();
        // Don't call super to prevent back navigation during session
    }
}