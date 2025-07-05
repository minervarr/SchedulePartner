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
import com.nava.schedulepartner.models.Schedule;
import com.nava.schedulepartner.models.ScheduleEvent;
import com.nava.schedulepartner.services.CoachingService;
import com.nava.schedulepartner.utils.AudioManager;
import com.nava.schedulepartner.utils.ScheduleManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class CoachingActivity extends AppCompatActivity implements CoachingService.CoachingListener {

    public static final String EXTRA_CONTEXT = "extra_context";
    public static final String EXTRA_ACTIVITY = "extra_activity";
    private static final long UPDATE_INTERVAL_MS = 1000;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

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

    private com.nava.schedulepartner.models.Context sessionContext;
    private Activity sessionActivity;
    private Schedule schedule;
    private ScheduleEvent currentEvent;
    private AudioManager audioManager;
    private CoachingService coachingService;
    private boolean serviceBound = false;
    private boolean sessionPaused = false;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateUI();
            updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coaching);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!loadSessionParameters()) {
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();

        if (!loadSchedule()) {
            finish();
            return;
        }

        audioManager = AudioManager.getInstance(this);
        updateAudioButton();

        startCoachingService();

        enableDndIfRequired();

        updateHandler.post(updateRunnable);
    }

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

        String sessionInfo = sessionActivity.getDisplayName() + " @ " +
                sessionContext.getDisplayName();
        tvSessionInfo.setText(sessionInfo);
    }

    private void setupClickListeners() {
        btnMuteAudio.setOnClickListener(v -> toggleAudioMute());
        btnPauseSession.setOnClickListener(v -> togglePause());
        btnEndSession.setOnClickListener(v -> confirmEndSession());
    }

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

    private void startCoachingService() {
        Intent serviceIntent = new Intent(this, CoachingService.class);
        serviceIntent.putExtra(CoachingService.EXTRA_CONTEXT, sessionContext.getIdentifier());
        serviceIntent.putExtra(CoachingService.EXTRA_ACTIVITY, sessionActivity.getIdentifier());

        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void enableDndIfRequired() {
        if (sessionContext.shouldAutoEnableDND() || sessionActivity.requiresFocus()) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager.isNotificationPolicyAccessGranted()) {
                notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            } else {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            }
        }
    }

    private void updateUI() {
        if (sessionPaused) {
            return;
        }

        LocalTime now = LocalTime.now();
        tvCurrentTime.setText(now.format(TIME_FORMATTER));
        ScheduleEvent newCurrentEvent = schedule.getCurrentEvent(now);

        if (hasEventChanged(newCurrentEvent)) {
            handleEventChange(newCurrentEvent);
        }

        updateCurrentEventDisplay(now);
        updateUpcomingEvents(now);
        updateTimeUntilNext(now);
    }

    private boolean hasEventChanged(ScheduleEvent newEvent) {
        if (currentEvent == null && newEvent == null) return false;
        if (currentEvent == null || newEvent == null) return true;
        return !currentEvent.equals(newEvent);
    }

    private void handleEventChange(ScheduleEvent newEvent) {
        currentEvent = newEvent;
        if (currentEvent != null && !sessionPaused) {
            float contextVolume = sessionContext.getDefaultVolumeMultiplier();
            audioManager.playAudio(
                    currentEvent.getEventType(),
                    currentEvent.getAlertLevel(),
                    contextVolume
            );
        }
    }

    private void updateCurrentEventDisplay(LocalTime now) {
        if (currentEvent != null) {
            layoutCurrentEvent.setVisibility(View.VISIBLE);
            tvCurrentEventLabel.setText(currentEvent.getDisplayMessage());
            int color = currentEvent.getEventType().isRestriction() ?
                    R.color.red_label : R.color.blue_label;
            tvCurrentEventLabel.setTextColor(ContextCompat.getColor(this, color));

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

    private void updateUpcomingEvents(LocalTime now) {
        layoutUpcomingEvents.removeAllViews();
        List<ScheduleEvent> upcoming = schedule.getUpcomingEvents(now, 180);
        int count = Math.min(5, upcoming.size());

        for (int i = 0; i < count; i++) {
            ScheduleEvent event = upcoming.get(i);
            View eventView = createUpcomingEventView(event, now);
            layoutUpcomingEvents.addView(eventView);
        }

        if (count == 0) {
            TextView noEvents = new TextView(this);
            noEvents.setText(getString(R.string.no_upcoming_events));
            noEvents.setTextColor(ContextCompat.getColor(this, R.color.white_secondary));
            noEvents.setTextSize(16);
            noEvents.setPadding(0, 16, 0, 16);
            layoutUpcomingEvents.addView(noEvents);
        }
    }

    private View createUpcomingEventView(ScheduleEvent event, LocalTime now) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0, 8, 0, 8);

        TextView timeText = new TextView(this);
        timeText.setText(event.getTimeString());
        timeText.setTextColor(ContextCompat.getColor(this, R.color.white_secondary));
        timeText.setTextSize(14);
        timeText.setWidth(120);
        layout.addView(timeText);

        TextView labelText = new TextView(this);
        labelText.setText(event.getDisplayMessage());
        int color = event.getEventType().isRestriction() ?
                R.color.red_label : R.color.blue_label;
        labelText.setTextColor(ContextCompat.getColor(this, color));
        labelText.setTextSize(16);
        layout.addView(labelText);

        return layout;
    }

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

    private void toggleAudioMute() {
        boolean newState = !audioManager.isAudioEnabled();
        audioManager.setAudioEnabled(newState);
        updateAudioButton();

        String message = newState ?
                getString(R.string.unmute_audio) :
                getString(R.string.mute_audio);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateAudioButton() {
        int iconRes = audioManager.isAudioEnabled() ?
                android.R.drawable.ic_lock_silent_mode_off :
                android.R.drawable.ic_lock_silent_mode;
        btnMuteAudio.setImageResource(iconRes);
    }

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

        if (serviceBound) {
            coachingService.setPaused(sessionPaused);
        }
    }

    private void confirmEndSession() {
        endSession();
    }

    private void endSession() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        Intent stopIntent = new Intent(this, CoachingService.class);
        stopService(stopIntent);

        disableDndIfEnabled();

        finish();
    }

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

    @Override
    public void onEventTriggered(ScheduleEvent event) {
        runOnUiThread(() -> {
            updateUI();
        });
    }

    @Override
    public void onBackPressed() {
        confirmEndSession();
    }
}