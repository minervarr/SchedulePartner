package com.nava.schedulepartner.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.util.Locale;

import com.nava.schedulepartner.R;
import com.nava.schedulepartner.activities.CoachingActivity;
import com.nava.schedulepartner.models.Activity;
import com.nava.schedulepartner.models.Schedule;
import com.nava.schedulepartner.models.ScheduleEvent;
import com.nava.schedulepartner.utils.AudioManager;
import com.nava.schedulepartner.utils.ScheduleManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Home screen widget provider for quick status checks.
 * <p>
 * This widget displays the current coaching session status without requiring
 * the user to open the full app. It shows the active event (if any) and the
 * next upcoming event, maintaining the app's red/blue color scheme for quick
 * visual recognition.
 * <p>
 * The widget updates every minute during active sessions to ensure accurate
 * time display. When no session is active, it shows the last used context
 * and activity for quick session restart.
 * <p>
 * Features:
 * - Current event display with red/blue coloring
 * - Next event preview with time remaining
 * - Quick mute toggle button
 * - Tap to open coaching screen
 * - Minimal battery impact with smart updates
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class DisciplineCoachWidget extends AppWidgetProvider {

    /**
     * SharedPreferences name for widget data.
     */
    private static final String PREFS_NAME = "DisciplineCoachWidgetPrefs";

    /**
     * Key for storing active session context.
     */
    private static final String PREF_ACTIVE_CONTEXT = "widget_active_context";

    /**
     * Key for storing active session activity.
     */
    private static final String PREF_ACTIVE_ACTIVITY = "widget_active_activity";

    /**
     * Key for storing session active state.
     */
    private static final String PREF_SESSION_ACTIVE = "widget_session_active";

    /**
     * Intent action for mute toggle from widget.
     */
    private static final String ACTION_TOGGLE_MUTE = "com.nava.schedulepartner.TOGGLE_MUTE";

    /**
     * Standard time formatter for consistent display and parsing.
     * <p>
     * Uses 24-hour format (HH:mm) for clarity and international
     * compatibility.
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Alternative formatter for parsing 12-hour format with AM/PM.
     * <p>
     * Supports times like "05:00:00 PM" from CSV files.
     */
    private static final DateTimeFormatter TIME_FORMATTER_12H =
            DateTimeFormatter.ofPattern("hh:mm:ss a");

    /**
     * Updates all widget instances with current data.
     * <p>
     * Called when widgets need refresh due to time change,
     * configuration change, or explicit update request.
     *
     * @param context The application context
     * @param appWidgetManager The widget manager
     * @param appWidgetIds Array of widget IDs to update
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        // Update each widget instance
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    /**
     * Updates a single widget instance.
     * <p>
     * Loads current session data and updates the widget's
     * RemoteViews with appropriate content and styling.
     *
     * @param context The application context
     * @param appWidgetManager The widget manager
     * @param appWidgetId The widget ID to update
     */
    private void updateWidget(Context context, AppWidgetManager appWidgetManager,
                              int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_discipline_coach);

        // Load session data
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
        boolean sessionActive = prefs.getBoolean(PREF_SESSION_ACTIVE, false);

        if (sessionActive) {
            // Show active session info
            updateActiveSession(context, views, prefs);
        } else {
            // Show inactive state
            updateInactiveState(context, views);
        }

        // Set up click handlers
        setupClickHandlers(context, views);

        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * Updates widget for active coaching session.
     * <p>
     * Shows current event, next event, and time information
     * based on the active schedule.
     *
     * @param context The application context
     * @param views The RemoteViews to update
     * @param prefs SharedPreferences with session data
     */
    private void updateActiveSession(Context context, RemoteViews views,
                                     SharedPreferences prefs) {
        String contextId = prefs.getString(PREF_ACTIVE_CONTEXT, null);
        String activityId = prefs.getString(PREF_ACTIVE_ACTIVITY, null);

        if (contextId == null || activityId == null) {
            updateInactiveState(context, views);
            return;
        }

        // Load context and activity
        com.nava.schedulepartner.models.Context sessionContext =
                com.nava.schedulepartner.models.Context.fromIdentifier(contextId);
        Activity sessionActivity = Activity.fromIdentifier(activityId);

        if (sessionContext == null || sessionActivity == null) {
            updateInactiveState(context, views);
            return;
        }

        // Load schedule
        ScheduleManager scheduleManager = ScheduleManager.getInstance(context);
        Schedule schedule = scheduleManager.loadSchedule(sessionContext, sessionActivity);

        if (schedule == null) {
            updateInactiveState(context, views);
            return;
        }

        // Get current time and events
        LocalTime now = LocalTime.now();
        ScheduleEvent currentEvent = schedule.getCurrentEvent(now);
        ScheduleEvent nextEvent = schedule.getNextEvent(now);

        // Update session info
        String sessionInfo = sessionActivity.getDisplayName() + " @ " +
                sessionContext.getDisplayName();
        views.setTextViewText(R.id.widgetSessionInfo, sessionInfo);

        // Update current event
        if (currentEvent != null) {
            views.setTextViewText(R.id.widgetCurrentEvent,
                    currentEvent.getDisplayMessage());

            // Set color based on event type
            int color = currentEvent.getEventType().isRestriction() ?
                    context.getColor(R.color.red_label) :
                    context.getColor(R.color.blue_label);
            views.setTextColor(R.id.widgetCurrentEvent, color);

            views.setViewVisibility(R.id.widgetCurrentEvent,
                    android.view.View.VISIBLE);
        } else {
            views.setTextViewText(R.id.widgetCurrentEvent, "No active event");
            views.setTextColor(R.id.widgetCurrentEvent,
                    context.getColor(R.color.white_secondary));
            views.setViewVisibility(R.id.widgetCurrentEvent,
                    android.view.View.VISIBLE);
        }

        // Update next event
        if (nextEvent != null) {
            long minutesUntil = nextEvent.getMinutesUntil(now);
            String nextText = String.format(Locale.US,
                    context.getString(R.string.widget_next_format),
                    nextEvent.getDisplayMessage(),
                    Math.max(0, minutesUntil));

            views.setTextViewText(R.id.widgetNextEvent, nextText);
            views.setViewVisibility(R.id.widgetNextEvent,
                    android.view.View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widgetNextEvent,
                    android.view.View.GONE);
        }

        // Update time
        views.setTextViewText(R.id.widgetTime, now.format(TIME_FORMATTER));

        // Update mute button
        updateMuteButton(context, views);
    }

    /**
     * Updates widget for inactive state (no session).
     *
     * @param context The application context
     * @param views The RemoteViews to update
     */
    private void updateInactiveState(Context context, RemoteViews views) {
        views.setTextViewText(R.id.widgetSessionInfo,
                context.getString(R.string.widget_no_session));
        views.setTextViewText(R.id.widgetCurrentEvent,
                "Tap to start session");
        views.setTextColor(R.id.widgetCurrentEvent,
                context.getColor(R.color.white_secondary));
        views.setViewVisibility(R.id.widgetNextEvent,
                android.view.View.GONE);

        // Update time
        LocalTime now = LocalTime.now();
        views.setTextViewText(R.id.widgetTime, now.format(TIME_FORMATTER));

        // Hide mute button when no session
        views.setViewVisibility(R.id.widgetBtnMute,
                android.view.View.GONE);
    }

    /**
     * Updates the mute button appearance based on audio state.
     *
     * @param context The application context
     * @param views The RemoteViews to update
     */
    private void updateMuteButton(Context context, RemoteViews views) {
        AudioManager audioManager = AudioManager.getInstance(context);
        boolean muted = !audioManager.isAudioEnabled();

        int iconRes = muted ?
                android.R.drawable.ic_lock_silent_mode :
                android.R.drawable.ic_lock_silent_mode_off;

        views.setImageViewResource(R.id.widgetBtnMute, iconRes);
        views.setViewVisibility(R.id.widgetBtnMute,
                android.view.View.VISIBLE);
    }

    /**
     * Sets up click handlers for widget elements.
     *
     * @param context The application context
     * @param views The RemoteViews to configure
     */
    private void setupClickHandlers(Context context, RemoteViews views) {
        // Main widget click - open app
        Intent intent = new Intent(context, CoachingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Add session parameters if active
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_SESSION_ACTIVE, false)) {
            String contextId = prefs.getString(PREF_ACTIVE_CONTEXT, null);
            String activityId = prefs.getString(PREF_ACTIVE_ACTIVITY, null);

            if (contextId != null && activityId != null) {
                intent.putExtra(CoachingActivity.EXTRA_CONTEXT, contextId);
                intent.putExtra(CoachingActivity.EXTRA_ACTIVITY, activityId);
            }
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent);

        // Mute button click
        Intent muteIntent = new Intent(context, DisciplineCoachWidget.class);
        muteIntent.setAction(ACTION_TOGGLE_MUTE);
        PendingIntent mutePendingIntent = PendingIntent.getBroadcast(
                context, 0, muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widgetBtnMute, mutePendingIntent);
    }

    /**
     * Handles broadcast intents including custom actions.
     *
     * @param context The application context
     * @param intent The broadcast intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE_MUTE.equals(intent.getAction())) {
            // Toggle mute state
            AudioManager audioManager = AudioManager.getInstance(context);
            audioManager.setAudioEnabled(!audioManager.isAudioEnabled());

            // Update all widgets
            updateAllWidgets(context);
        }
    }

    /**
     * Updates session state in widget preferences.
     * <p>
     * Called by CoachingActivity/Service to keep widget in sync
     * with active coaching sessions.
     *
     * @param context The application context
     * @param active Whether a session is active
     * @param contextId The context identifier (if active)
     * @param activityId The activity identifier (if active)
     */
    public static void updateSessionState(Context context, boolean active,
                                          String contextId, String activityId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(PREF_SESSION_ACTIVE, active);

        if (active && contextId != null && activityId != null) {
            editor.putString(PREF_ACTIVE_CONTEXT, contextId);
            editor.putString(PREF_ACTIVE_ACTIVITY, activityId);
        } else {
            editor.remove(PREF_ACTIVE_CONTEXT);
            editor.remove(PREF_ACTIVE_ACTIVITY);
        }

        editor.apply();

        // Update widgets
        updateAllWidgets(context);
    }

    /**
     * Forces update of all widget instances.
     *
     * @param context The application context
     */
    public static void updateAllWidgets(Context context) {
        Intent intent = new Intent(context, DisciplineCoachWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context,
                DisciplineCoachWidget.class);
        int[] widgetIds = widgetManager.getAppWidgetIds(componentName);

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        context.sendBroadcast(intent);
    }

    /**
     * Handles widget enable event.
     * <p>
     * Called when the first widget instance is added to home screen.
     * Could be used to start a periodic update service if needed.
     *
     * @param context The application context
     */
    @Override
    public void onEnabled(Context context) {
        // Could start a service for periodic updates if needed
        // For now, relying on system updates and app triggers
    }

    /**
     * Handles widget disable event.
     * <p>
     * Called when the last widget instance is removed from home screen.
     *
     * @param context The application context
     */
    @Override
    public void onDisabled(Context context) {
        // Clean up any resources if needed
    }
}