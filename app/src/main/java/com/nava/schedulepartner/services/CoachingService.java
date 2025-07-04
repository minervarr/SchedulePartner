package com.nava.schedulepartner.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.nava.schedulepartner.R;
import com.nava.schedulepartner.activities.CoachingActivity;
import com.nava.schedulepartner.models.Activity;
import com.nava.schedulepartner.models.AlertLevel;
import com.nava.schedulepartner.models.Schedule;
import com.nava.schedulepartner.models.ScheduleEvent;
import com.nava.schedulepartner.utils.AudioManager;
import com.nava.schedulepartner.utils.ScheduleManager;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Background service that manages coaching notifications and event tracking.
 * <p>
 * This service runs during active coaching sessions to ensure timely
 * notifications even when the app is in the background. It implements
 * the three-tier alert system (CRITICAL, TRANSITION, REFERENCE) with
 * battery-optimized notification strategies.
 * <p>
 * Key responsibilities:
 * - Track current time and trigger events
 * - Show notifications based on alert levels
 * - Manage wake locks for critical events
 * - Play audio alerts through AudioManager
 * - Maintain foreground service notification
 * - Handle pause/resume functionality
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class CoachingService extends Service {
    /**
     * Tag for logging debug information.
     */
    private static final String TAG = "CoachingService";

    /**
     * Intent extra key for context identifier.
     */
    public static final String EXTRA_CONTEXT = "extra_context";

    /**
     * Intent extra key for activity identifier.
     */
    public static final String EXTRA_ACTIVITY = "extra_activity";

    /**
     * Notification channel ID for coaching alerts.
     */
    private static final String CHANNEL_ID = "discipline_coach_alerts";

    /**
     * Notification ID for the foreground service.
     */
    private static final int NOTIFICATION_ID_FOREGROUND = 1001;

    /**
     * Base notification ID for event alerts.
     */
    private static final int NOTIFICATION_ID_ALERT_BASE = 2000;

    /**
     * Check interval for event triggers in milliseconds.
     * <p>
     * Set to 30 seconds to balance accuracy with battery life.
     * Events are scheduled to the minute, so checking twice per
     * minute ensures no events are missed.
     */
    private static final long CHECK_INTERVAL_MS = 30000;

    /**
     * Interface for activity callbacks.
     */
    public interface CoachingListener {
        void onEventTriggered(ScheduleEvent event);
    }

    /**
     * Binder for local service binding.
     */
    public class LocalBinder extends Binder {
        public CoachingService getService() {
            return CoachingService.this;
        }
    }

    // Service components
    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;

    // Session data
    private com.nava.schedulepartner.models.Context sessionContext;
    private Activity sessionActivity;
    private Schedule schedule;
    private boolean isPaused = false;
    private CoachingListener listener;

    // Event tracking
    private final Map<String, Boolean> triggeredEvents = new HashMap<>();
    private LocalTime lastCheckTime;

    // Update handler
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkEventsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPaused) {
                checkAndTriggerEvents();
            }
            updateHandler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    /**
     * Initializes the service when created.
     * <p>
     * Sets up notification channels, acquires wake lock, and
     * prepares audio manager for event alerts.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        audioManager = AudioManager.getInstance(this);

        // Create notification channel
        createNotificationChannel();

        // Acquire partial wake lock for reliable event triggering
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DisciplineCoach:CoachingService"
        );

        Log.d(TAG, "Service created");
    }

    /**
     * Handles service start with session parameters.
     * <p>
     * Loads the schedule and starts foreground mode with a
     * persistent notification showing session status.
     *
     * @param intent The intent with session parameters
     * @param flags Start flags
     * @param startId Start ID
     * @return START_STICKY to restart if killed
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Load session parameters
        String contextId = intent.getStringExtra(EXTRA_CONTEXT);
        String activityId = intent.getStringExtra(EXTRA_ACTIVITY);

        if (contextId == null || activityId == null) {
            Log.e(TAG, "Missing session parameters");
            stopSelf();
            return START_NOT_STICKY;
        }

        sessionContext = com.nava.schedulepartner.models.Context.fromIdentifier(contextId);
        sessionActivity = Activity.fromIdentifier(activityId);

        if (sessionContext == null || sessionActivity == null) {
            Log.e(TAG, "Invalid session parameters");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Load schedule
        ScheduleManager scheduleManager = ScheduleManager.getInstance(this);
        schedule = scheduleManager.loadSchedule(sessionContext, sessionActivity);

        if (schedule == null) {
            Log.e(TAG, "Failed to load schedule");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start foreground service
        startForeground(NOTIFICATION_ID_FOREGROUND, createForegroundNotification());

        // Acquire wake lock
        if (!wakeLock.isHeld()) {
            // Acquire wake lock for the session duration with 12 hour timeout
            wakeLock.acquire(12 * 60 * 60 * 1000L); // 12 hours in milliseconds
        }

        // Start event checking
        lastCheckTime = LocalTime.now();
        updateHandler.post(checkEventsRunnable);

        Log.d(TAG, "Service started for: " + sessionActivity + "@" + sessionContext);
        return START_STICKY;
    }

    /**
     * Creates the notification channel for Android O+.
     * <p>
     * Sets up channel with high importance for critical alerts
     * to ensure they bypass Do Not Disturb when needed.
     */
    private void createNotificationChannel() {
        // Always create channel since minSdk is 30 (Android 11)

        NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setShowBadge(false);
            channel.enableVibration(true);

            notificationManager.createNotificationChannel(channel);
    }

    /**
     * Creates the persistent foreground notification.
     * <p>
     * Shows current session info and provides quick access
     * back to the coaching screen.
     *
     * @return The foreground notification
     */
    private Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, CoachingActivity.class);
        notificationIntent.putExtra(CoachingActivity.EXTRA_CONTEXT,
                sessionContext.getIdentifier());
        notificationIntent.putExtra(CoachingActivity.EXTRA_ACTIVITY,
                sessionActivity.getIdentifier());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String sessionInfo = sessionActivity.getDisplayName() + " @ " +
                sessionContext.getDisplayName();
        String status = isPaused ? getString(R.string.session_paused) :
                getString(R.string.session_active);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Discipline Coach")
                .setContentText(sessionInfo + " - " + status)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Checks for and triggers events based on current time.
     * <p>
     * Runs every CHECK_INTERVAL_MS to find events that should
     * trigger. Handles both point-in-time and duration events.
     */
    private void checkAndTriggerEvents() {
        LocalTime now = LocalTime.now();

        // Check each event in schedule
        for (ScheduleEvent event : schedule.getEvents()) {
            String eventKey = generateEventKey(event);

            // Check if event should trigger
            if (shouldTriggerEvent(event, now) && !triggeredEvents.containsKey(eventKey)) {
                triggerEvent(event);
                triggeredEvents.put(eventKey, true);
            }
        }

        // Clean up old triggered events (older than 1 hour)
        cleanupTriggeredEvents(now);

        lastCheckTime = now;
    }

    /**
     * Generates a unique key for event tracking.
     *
     * @param event The event to generate key for
     * @return Unique event key
     */
    private String generateEventKey(ScheduleEvent event) {
        return event.getTime().toString() + "_" + event.getEventType().name();
    }

    /**
     * Determines if an event should trigger.
     * <p>
     * Checks if current time falls within the event's trigger
     * window since the last check.
     *
     * @param event The event to check
     * @param now Current time
     * @return True if event should trigger
     */
    private boolean shouldTriggerEvent(ScheduleEvent event, LocalTime now) {
        LocalTime eventTime = event.getTime();

        // Check if event time is between last check and now
        if (lastCheckTime != null) {
            return !eventTime.isBefore(lastCheckTime) && !eventTime.isAfter(now);
        }

        // First check - only trigger if within 1 minute
        return event.getMinutesUntil(now) >= -1 && event.getMinutesUntil(now) <= 0;
    }

    /**
     * Triggers an event with appropriate notification and audio.
     *
     * @param event The event to trigger
     */
    private void triggerEvent(ScheduleEvent event) {
        Log.d(TAG, "Triggering event: " + event);

        // Check alert level for notification
        if (event.getAlertLevel().triggersNotification()) {
            showEventNotification(event);
        }

        // Play audio if appropriate
        if (event.getAlertLevel().playsAudio()) {
            float contextVolume = sessionContext.getDefaultVolumeMultiplier();
            audioManager.playAudio(
                    event.getEventType(),
                    event.getAlertLevel(),
                    contextVolume
            );
        }

        // Notify listener (activity if bound)
        if (listener != null) {
            listener.onEventTriggered(event);
        }

        // Acquire temporary wake lock for critical events
        if (event.getAlertLevel() == AlertLevel.CRITICAL) {
            acquireTemporaryWakeLock();
        }
    }

    /**
     * Shows a notification for the triggered event.
     *
     * @param event The event to show notification for
     */
    private void showEventNotification(ScheduleEvent event) {
        // Build notification based on event type and priority
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(event.getDisplayMessage())
                .setContentText(event.getTimeString())
                .setAutoCancel(true)
                .setPriority(getNotificationPriority(event.getAlertLevel()));

        // Set color based on event type
        if (event.getEventType().isRestriction()) {
            builder.setColor(getColor(R.color.red_label));
        } else {
            builder.setColor(getColor(R.color.blue_label));
        }

        // Add intent to open coaching activity
        Intent intent = new Intent(this, CoachingActivity.class);
        intent.putExtra(CoachingActivity.EXTRA_CONTEXT, sessionContext.getIdentifier());
        intent.putExtra(CoachingActivity.EXTRA_ACTIVITY, sessionActivity.getIdentifier());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        // Show notification
        int notificationId = NOTIFICATION_ID_ALERT_BASE + event.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    /**
     * Maps alert level to Android notification priority.
     *
     * @param alertLevel The alert level
     * @return Android notification priority constant
     */
    private int getNotificationPriority(AlertLevel alertLevel) {
        switch (alertLevel) {
            case CRITICAL:
                return NotificationCompat.PRIORITY_MAX;
            case TRANSITION:
                return NotificationCompat.PRIORITY_DEFAULT;
            case REFERENCE:
                return NotificationCompat.PRIORITY_LOW;
            default:
                return NotificationCompat.PRIORITY_DEFAULT;
        }
    }

    /**
     * Acquires a temporary wake lock for critical events.
     * <p>
     * Ensures device wakes up for 30 seconds to handle the event
     * and allow user to see/hear the alert.
     */
    private void acquireTemporaryWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock tempWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DisciplineCoach:CriticalEvent"
        );
        tempWakeLock.acquire(30000); // 30 seconds
    }

    /**
     * Cleans up old entries from triggered events map.
     *
     * @param now Current time for comparison
     */
    private void cleanupTriggeredEvents(LocalTime now) {
        triggeredEvents.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            String timeStr = key.substring(0, key.indexOf('_'));
            LocalTime eventTime = LocalTime.parse(timeStr);

            // Remove if more than 1 hour old
            return eventTime.plusHours(1).isBefore(now);
        });
    }

    /**
     * Sets the pause state of the service.
     *
     * @param paused True to pause, false to resume
     */
    public void setPaused(boolean paused) {
        this.isPaused = paused;

        // Update foreground notification
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND,
                createForegroundNotification());

        if (paused) {
            audioManager.stopCurrentAudio();
        }

        Log.d(TAG, "Service paused: " + paused);
    }

    /**
     * Sets the listener for event callbacks.
     *
     * @param listener The listener to set (usually CoachingActivity)
     */
    public void setCoachingListener(CoachingListener listener) {
        this.listener = listener;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop event checking
        updateHandler.removeCallbacks(checkEventsRunnable);

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Stop audio
        audioManager.stopCurrentAudio();

        // Clear notifications
        notificationManager.cancelAll();

        Log.d(TAG, "Service destroyed");
    }
}