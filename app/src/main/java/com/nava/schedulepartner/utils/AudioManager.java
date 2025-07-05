package com.nava.schedulepartner.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.nava.schedulepartner.models.AlertLevel;
import com.nava.schedulepartner.models.EventType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AudioManager {
    private static final String TAG = "AudioManager";
    private static final String AUDIO_DIR = "audio";
    private static final String PREF_AUDIO_ENABLED = "audio_enabled";
    private static final String PREF_AUDIO_VOLUME = "audio_volume";
    private static final float DEFAULT_VOLUME = 0.7f;
    private static AudioManager instance;
    private final Context context;
    private final File audioDir;
    private MediaPlayer currentPlayer;
    private boolean audioEnabled;
    private float globalVolume;
    private static final Map<EventType, String> AUDIO_MAPPINGS = new HashMap<>();

    static {
        AUDIO_MAPPINGS.put(EventType.WAKE_UP, "wake_up");
        AUDIO_MAPPINGS.put(EventType.NO_COFFEE, "no_coffee");
        AUDIO_MAPPINGS.put(EventType.MEAL_TIME, "meal_time");
        AUDIO_MAPPINGS.put(EventType.MEAL_REMINDER_5MIN, "meal_5min");
        AUDIO_MAPPINGS.put(EventType.MEAL_REMINDER_10MIN, "meal_10min");
        AUDIO_MAPPINGS.put(EventType.MEAL_REMINDER_15MIN, "meal_15min");
        AUDIO_MAPPINGS.put(EventType.STUDY_START, "study_start");
        AUDIO_MAPPINGS.put(EventType.STUDY_REMAINING_30MIN, "study_remaining_30min");
        AUDIO_MAPPINGS.put(EventType.STUDY_REMAINING_1H, "study_remaining_1h");
        AUDIO_MAPPINGS.put(EventType.STUDY_REMAINING_2H, "study_remaining_2h");
        AUDIO_MAPPINGS.put(EventType.BREAK_REMINDER, "break_reminder");
        AUDIO_MAPPINGS.put(EventType.WORK_START, "work_start");
        AUDIO_MAPPINGS.put(EventType.WORK_END, "work_end");
        AUDIO_MAPPINGS.put(EventType.WORK_REMAINING_1H, "work_remaining_1h");
        AUDIO_MAPPINGS.put(EventType.WORK_REMAINING_2H, "work_remaining_2h");
        AUDIO_MAPPINGS.put(EventType.EXERCISE_START, "exercise_start");
        AUDIO_MAPPINGS.put(EventType.EXERCISE_END, "exercise_end");
        AUDIO_MAPPINGS.put(EventType.STOP_SCREENS, "stop_screens");
        AUDIO_MAPPINGS.put(EventType.BEDTIME, "bedtime");
        AUDIO_MAPPINGS.put(EventType.TRANSITION, "transition");
        AUDIO_MAPPINGS.put(EventType.CUSTOM, "custom");
    }

    private static final String[] SUPPORTED_EXTENSIONS = {
            ".flac", ".mp3", ".wav", ".ogg"
    };

    private AudioManager(Context context) {
        this.context = context.getApplicationContext();
        this.audioDir = new File(context.getFilesDir(), AUDIO_DIR);
        if (!audioDir.exists()) {
            if (!audioDir.mkdirs()) {
                Log.e(TAG, "Failed to create audio directory");
            }
        }
        loadPreferences();
    }

    public static synchronized AudioManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioManager(context);
        }
        return instance;
    }

    private void loadPreferences() {
        SharedPreferences prefs = context.getSharedPreferences(
                "DisciplineCoachPrefs", Context.MODE_PRIVATE);
        audioEnabled = prefs.getBoolean(PREF_AUDIO_ENABLED, true);
        globalVolume = prefs.getFloat(PREF_AUDIO_VOLUME, DEFAULT_VOLUME);
    }

    private void savePreferences() {
        SharedPreferences prefs = context.getSharedPreferences(
                "DisciplineCoachPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_AUDIO_ENABLED, audioEnabled)
                .putFloat(PREF_AUDIO_VOLUME, globalVolume)
                .apply();
    }

    public void playAudio(EventType eventType, AlertLevel alertLevel,
                          float contextVolume) {
        if (!shouldPlayAudio(alertLevel)) {
            Log.d(TAG, "Audio disabled for alert level: " + alertLevel);
            return;
        }

        String baseFilename = AUDIO_MAPPINGS.get(eventType);
        if (baseFilename == null) {
            Log.w(TAG, "No audio mapping for event type: " + eventType);
            playSystemNotificationSound();
            return;
        }

        File audioFile = findAudioFile(baseFilename);
        if (audioFile == null || !audioFile.exists()) {
            Log.w(TAG, "Audio file not found for: " + baseFilename);
            playSystemNotificationSound();
            return;
        }

        playAudioFile(audioFile, alertLevel, contextVolume);
    }

    private boolean shouldPlayAudio(AlertLevel alertLevel) {
        return audioEnabled && alertLevel.playsAudio();
    }

    private File findAudioFile(String baseFilename) {
        for (String extension : SUPPORTED_EXTENSIONS) {
            File file = new File(audioDir, baseFilename + extension);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private void playAudioFile(File audioFile, AlertLevel alertLevel,
                               float contextVolume) {
        stopCurrentAudio();
        try {
            currentPlayer = new MediaPlayer();
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(getAudioUsage(alertLevel))
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            currentPlayer.setAudioAttributes(attributes);
            FileInputStream fis = new FileInputStream(audioFile);
            currentPlayer.setDataSource(fis.getFD());
            fis.close();
            float finalVolume = globalVolume * contextVolume;
            currentPlayer.setVolume(finalVolume, finalVolume);
            currentPlayer.setOnCompletionListener(mp -> {
                mp.release();
                if (currentPlayer == mp) {
                    currentPlayer = null;
                }
            });
            currentPlayer.prepare();
            currentPlayer.start();
            Log.d(TAG, "Playing audio: " + audioFile.getName() +
                    " at volume: " + finalVolume);
        } catch (IOException e) {
            Log.e(TAG, "Error playing audio file: " + audioFile.getName(), e);
            playSystemNotificationSound();
        }
    }

    private int getAudioUsage(AlertLevel alertLevel) {
        if (alertLevel == AlertLevel.CRITICAL) {
            return AudioAttributes.USAGE_ALARM;
        } else {
            return AudioAttributes.USAGE_NOTIFICATION_EVENT;
        }
    }

    private void playSystemNotificationSound() {
        try {
            MediaPlayer player = MediaPlayer.create(context,
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            if (player != null) {
                player.start();
                player.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing system notification sound", e);
        }
    }

    public void stopCurrentAudio() {
        if (currentPlayer != null) {
            try {
                if (currentPlayer.isPlaying()) {
                    currentPlayer.stop();
                }
                currentPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio", e);
            }
            currentPlayer = null;
        }
    }

    public void setAudioEnabled(boolean enabled) {
        this.audioEnabled = enabled;
        savePreferences();
        if (!enabled) {
            stopCurrentAudio();
        }
    }

    public boolean isAudioEnabled() {
        return audioEnabled;
    }

    public void setGlobalVolume(float volume) {
        this.globalVolume = Math.max(0.0f, Math.min(1.0f, volume));
        savePreferences();
    }

    public float getGlobalVolume() {
        return globalVolume;
    }

    public int importAudioZip(File zipFile) {
        int importedCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String filename = new File(entry.getName()).getName();
                    if (isAudioFile(filename) && isValidAudioFilename(filename)) {
                        File outputFile = new File(audioDir, filename);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            importedCount++;
                            Log.d(TAG, "Imported audio file: " + filename);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error importing audio zip", e);
        }
        return importedCount;
    }

    private boolean isAudioFile(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidAudioFilename(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            String baseName = filename.substring(0, dotIndex);
            return AUDIO_MAPPINGS.containsValue(baseName);
        }
        return false;
    }

    public void clearAllAudio() {
        File[] files = audioDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    if (!file.delete()) {
                        Log.e(TAG, "Failed to delete audio file: " + file.getName());
                    }
                }
            }
        }
        Log.d(TAG, "Cleared all audio files");
    }
}