package com.nava.schedulepartner.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.util.Log;

import com.nava.schedulepartner.models.AlertLevel;
import com.nava.schedulepartner.models.EventType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages audio playback for discipline coaching alerts.
 * <p>
 * This singleton class implements the hardcoded audio-event mapping system
 * that keeps schedule data clean and separated from audio configuration.
 * Audio files are mapped to event types through code rather than CSV files,
 * ensuring maintainability and preventing broken audio references.
 * <p>
 * Key features:
 * - Hardcoded event-to-audio filename mapping
 * - Support for MP3, WAV, OGG, and FLAC formats
 * - Global and context-based volume control
 * - Zip import for bulk audio file management
 * - Fallback to system sounds if custom audio unavailable
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class AudioManager {
    /**
     * Tag for logging debug information.
     */
    private static final String TAG = "AudioManager";

    /**
     * Directory name for storing audio files.
     */
    private static final String AUDIO_DIR = "audio";

    /**
     * SharedPreferences key for global audio enabled state.
     */
    private static final String PREF_AUDIO_ENABLED = "audio_enabled";

    /**
     * SharedPreferences key for global volume level.
     */
    private static final String PREF_AUDIO_VOLUME = "audio_volume";

    /**
     * Default volume level (0.0 to 1.0).
     */
    private static final float DEFAULT_VOLUME = 0.7f;

    /**
     * Singleton instance of AudioManager.
     */
    private static AudioManager instance;

    /**
     * Application context for file operations.
     */
    private final Context context;

    /**
     * Android AudioManager for system audio control.
     */
    private final android.media.AudioManager systemAudioManager;

    /**
     * Directory for audio files.
     */
    private final File audioDir;

    /**
     * Currently playing MediaPlayer instance.
     * <p>
     * Kept as reference to allow stopping previous audio
     * when new alert plays.
     */
    private MediaPlayer currentPlayer;

    /**
     * Global audio enabled state.
     * <p>
     * When false, no audio will play regardless of other settings.
     */
    private boolean audioEnabled;

    /**
     * Global volume multiplier (0.0 to 1.0).
     */
    private float globalVolume;

    /**
     * Hardcoded mapping of EventType to audio filenames.
     * <p>
     * This is the core of the audio system - each event type
     * maps to a specific filename (without extension). The actual
     * file extension is determined at runtime by checking which
     * format exists.
     */
    private static final Map<EventType, String> AUDIO_MAPPINGS = new HashMap<>();

    static {
        // Initialize hardcoded audio mappings
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

    /**
     * Supported audio file extensions in order of preference.
     * <p>
     * FLAC is preferred for quality, then MP3 for compatibility,
     * then WAV for simplicity, then OGG for open format.
     */
    private static final String[] SUPPORTED_EXTENSIONS = {
            ".flac", ".mp3", ".wav", ".ogg"
    };

    /**
     * Private constructor for singleton pattern.
     *
     * @param context Application context
     */
    private AudioManager(Context context) {
        this.context = context.getApplicationContext();
        this.systemAudioManager = (android.media.AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);

        // Create audio directory if it doesn't exist
        this.audioDir = new File(context.getFilesDir(), AUDIO_DIR);
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }

        // Load preferences
        loadPreferences();
    }

    /**
     * Gets the singleton instance of AudioManager.
     *
     * @param context Application context
     * @return The AudioManager instance
     */
    public static synchronized AudioManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioManager(context);
        }
        return instance;
    }

    /**
     * Loads audio preferences from SharedPreferences.
     */
    private void loadPreferences() {
        SharedPreferences prefs = context.getSharedPreferences(
                "DisciplineCoachPrefs", Context.MODE_PRIVATE);

        audioEnabled = prefs.getBoolean(PREF_AUDIO_ENABLED, true);
        globalVolume = prefs.getFloat(PREF_AUDIO_VOLUME, DEFAULT_VOLUME);
    }

    /**
     * Saves audio preferences to SharedPreferences.
     */
    private void savePreferences() {
        SharedPreferences prefs = context.getSharedPreferences(
                "DisciplineCoachPrefs", Context.MODE_PRIVATE);

        prefs.edit()
                .putBoolean(PREF_AUDIO_ENABLED, audioEnabled)
                .putFloat(PREF_AUDIO_VOLUME, globalVolume)
                .apply();
    }

    /**
     * Plays audio for the specified event type.
     * <p>
     * Uses the hardcoded mapping to find the appropriate audio file.
     * Respects global audio settings and alert level configurations.
     * Falls back to system notification sound if custom audio unavailable.
     *
     * @param eventType The type of event to play audio for
     * @param alertLevel The alert level (affects volume and behavior)
     * @param contextVolume Context-specific volume multiplier (0.0-1.0)
     */
    public void playAudio(EventType eventType, AlertLevel alertLevel,
                          float contextVolume) {
        // Check if audio should play
        if (!shouldPlayAudio(alertLevel)) {
            Log.d(TAG, "Audio disabled for alert level: " + alertLevel);
            return;
        }

        // Get audio filename from hardcoded mapping
        String baseFilename = AUDIO_MAPPINGS.get(eventType);
        if (baseFilename == null) {
            Log.w(TAG, "No audio mapping for event type: " + eventType);
            playSystemNotificationSound();
            return;
        }

        // Find actual audio file with extension
        File audioFile = findAudioFile(baseFilename);
        if (audioFile == null || !audioFile.exists()) {
            Log.w(TAG, "Audio file not found for: " + baseFilename);
            playSystemNotificationSound();
            return;
        }

        // Play the audio file
        playAudioFile(audioFile, alertLevel, contextVolume);
    }

    /**
     * Determines if audio should play based on settings and alert level.
     *
     * @param alertLevel The alert level to check
     * @return True if audio should play
     */
    private boolean shouldPlayAudio(AlertLevel alertLevel) {
        // Check global audio enabled
        if (!audioEnabled) {
            return false;
        }

        // Check alert level audio setting
        return alertLevel.playsAudio();
    }

    /**
     * Finds the audio file with supported extension.
     * <p>
     * Checks for files in order of extension preference.
     *
     * @param baseFilename Filename without extension
     * @return The audio file, or null if not found
     */
    private File findAudioFile(String baseFilename) {
        for (String extension : SUPPORTED_EXTENSIONS) {
            File file = new File(audioDir, baseFilename + extension);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Plays the specified audio file.
     *
     * @param audioFile The audio file to play
     * @param alertLevel The alert level (affects audio attributes)
     * @param contextVolume Context-specific volume multiplier
     */
    private void playAudioFile(File audioFile, AlertLevel alertLevel,
                               float contextVolume) {
        // Stop any currently playing audio
        stopCurrentAudio();

        try {
            currentPlayer = new MediaPlayer();

            // Set audio attributes based on alert level
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(getAudioUsage(alertLevel))
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            currentPlayer.setAudioAttributes(attributes);

            // Set data source and prepare
            FileInputStream fis = new FileInputStream(audioFile);
            currentPlayer.setDataSource(fis.getFD());
            fis.close();

            // Calculate final volume
            float finalVolume = globalVolume * contextVolume;
            currentPlayer.setVolume(finalVolume, finalVolume);

            // Set completion listener
            currentPlayer.setOnCompletionListener(mp -> {
                mp.release();
                if (currentPlayer == mp) {
                    currentPlayer = null;
                }
            });

            // Prepare and start playback
            currentPlayer.prepare();
            currentPlayer.start();

            Log.d(TAG, "Playing audio: " + audioFile.getName() +
                    " at volume: " + finalVolume);

        } catch (IOException e) {
            Log.e(TAG, "Error playing audio file: " + audioFile.getName(), e);
            playSystemNotificationSound();
        }
    }

    /**
     * Gets the appropriate audio usage type for the alert level.
     * <p>
     * CRITICAL alerts use USAGE_ALARM to bypass DND.
     * Other alerts use USAGE_NOTIFICATION_EVENT.
     *
     * @param alertLevel The alert level
     * @return Android audio usage constant
     */
    private int getAudioUsage(AlertLevel alertLevel) {
        if (alertLevel == AlertLevel.CRITICAL) {
            return AudioAttributes.USAGE_ALARM;
        } else {
            return AudioAttributes.USAGE_NOTIFICATION_EVENT;
        }
    }

    /**
     * Plays system default notification sound as fallback.
     */
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

    /**
     * Stops any currently playing audio.
     */
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

    /**
     * Sets the global audio enabled state.
     *
     * @param enabled True to enable audio, false to disable
     */
    public void setAudioEnabled(boolean enabled) {
        this.audioEnabled = enabled;
        savePreferences();

        if (!enabled) {
            stopCurrentAudio();
        }
    }

    /**
     * Gets the global audio enabled state.
     *
     * @return True if audio is enabled
     */
    public boolean isAudioEnabled() {
        return audioEnabled;
    }

    /**
     * Sets the global volume level.
     *
     * @param volume Volume level (0.0 to 1.0)
     */
    public void setGlobalVolume(float volume) {
        this.globalVolume = Math.max(0.0f, Math.min(1.0f, volume));
        savePreferences();
    }

    /**
     * Gets the global volume level.
     *
     * @return Volume level (0.0 to 1.0)
     */
    public float getGlobalVolume() {
        return globalVolume;
    }

    /**
     * Imports audio files from a zip archive.
     * <p>
     * Extracts all supported audio files from the zip and places
     * them in the audio directory. Validates filenames match
     * expected format for event mapping.
     *
     * @param zipFile The zip file containing audio files
     * @return Number of files successfully imported
     */
    public int importAudioZip(File zipFile) {
        int importedCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String filename = new File(entry.getName()).getName();

                    // Check if it's a supported audio file
                    if (isAudioFile(filename) && isValidAudioFilename(filename)) {
                        File outputFile = new File(audioDir, filename);

                        // Extract file
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

    /**
     * Checks if a filename represents a supported audio file.
     *
     * @param filename The filename to check
     * @return True if it's a supported audio file
     */
    private boolean isAudioFile(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates that audio filename matches expected format.
     * <p>
     * Filename (without extension) must match one of the values
     * in AUDIO_MAPPINGS to be useful.
     *
     * @param filename The filename to validate
     * @return True if filename is valid for mapping
     */
    private boolean isValidAudioFilename(String filename) {
        // Remove extension
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            String baseName = filename.substring(0, dotIndex);
            return AUDIO_MAPPINGS.containsValue(baseName);
        }
        return false;
    }

    /**
     * Checks if audio file exists for the given event type.
     *
     * @param eventType The event type to check
     * @return True if audio file exists
     */
    public boolean hasAudioForEvent(EventType eventType) {
        String baseFilename = AUDIO_MAPPINGS.get(eventType);
        if (baseFilename == null) {
            return false;
        }

        File audioFile = findAudioFile(baseFilename);
        return audioFile != null && audioFile.exists();
    }

    /**
     * Deletes all imported audio files.
     * <p>
     * Used when user wants to reset to default sounds or
     * re-import a different audio set.
     */
    public void clearAllAudio() {
        File[] files = audioDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
        Log.d(TAG, "Cleared all audio files");
    }
}