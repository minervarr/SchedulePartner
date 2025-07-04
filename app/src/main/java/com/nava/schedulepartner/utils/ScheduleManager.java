package com.nava.schedulepartner.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.nava.schedulepartner.models.Activity;
import com.nava.schedulepartner.models.Schedule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages schedule template storage, loading, and caching.
 * <p>
 * This singleton class handles all schedule-related file operations including:
 * - Loading default templates from assets
 * - Managing user-imported templates in internal storage
 * - Caching loaded schedules for performance
 * - Import/export functionality for template sharing
 * <p>
 * The manager uses a two-tier storage system:
 * 1. Default templates in assets (read-only, always available)
 * 2. User templates in internal storage (read-write, customizable)
 * User templates override defaults when both exist.
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class ScheduleManager {
    /**
     * Tag for logging debug information.
     */
    private static final String TAG = "ScheduleManager";

    /**
     * Directory name for storing user templates.
     * <p>
     * Created in app's internal storage to ensure privacy
     * and avoid external storage permissions.
     */
    private static final String TEMPLATES_DIR = "templates";

    /**
     * Directory name for default templates in assets.
     */
    private static final String ASSETS_TEMPLATES_DIR = "default_templates";

    /**
     * File extension for schedule template files.
     */
    private static final String TEMPLATE_EXTENSION = ".csv";

    /**
     * Singleton instance of ScheduleManager.
     */
    private static ScheduleManager instance;

    /**
     * Application context for file operations.
     */
    private final Context context;

    /**
     * Cache of loaded schedules to avoid repeated file I/O.
     * <p>
     * Key format: "activity@context" (lowercase)
     * Cleared when refreshSchedules() is called.
     */
    private final Map<String, Schedule> scheduleCache;

    /**
     * Directory for user-imported templates.
     */
    private final File templatesDir;

    /**
     * Private constructor for singleton pattern.
     *
     * @param context Application context
     */
    private ScheduleManager(Context context) {
        this.context = context.getApplicationContext();
        this.scheduleCache = new HashMap<>();

        // Create templates directory if it doesn't exist
        this.templatesDir = new File(context.getFilesDir(), TEMPLATES_DIR);
        if (!templatesDir.exists()) {
            templatesDir.mkdirs();
        }

        // Copy default templates on first run
        copyDefaultTemplatesIfNeeded();
    }

    /**
     * Gets the singleton instance of ScheduleManager.
     *
     * @param context Application context
     * @return The ScheduleManager instance
     */
    public static synchronized ScheduleManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScheduleManager(context);
        }
        return instance;
    }

    /**
     * Loads a schedule for the given context and activity.
     * <p>
     * First checks cache, then user templates, then default templates.
     * Returns null if no matching template is found.
     *
     * @param context The context to load for
     * @param activity The activity to load for
     * @return The loaded Schedule, or null if not found
     */
    public Schedule loadSchedule(com.nava.schedulepartner.models.Context context,
                                 Activity activity) {
        String key = generateCacheKey(context, activity);

        // Check cache first
        if (scheduleCache.containsKey(key)) {
            Log.d(TAG, "Loading schedule from cache: " + key);
            return scheduleCache.get(key);
        }

        // Try loading from user templates
        Schedule schedule = loadUserSchedule(context, activity);

        // Fall back to default templates
        if (schedule == null) {
            schedule = loadDefaultSchedule(context, activity);
        }

        // Cache the result (even if null to avoid repeated lookups)
        if (schedule != null) {
            scheduleCache.put(key, schedule);
            Log.d(TAG, "Cached schedule: " + key);
        }

        return schedule;
    }

    /**
     * Generates a cache key for the given context and activity.
     *
     * @param context The context
     * @param activity The activity
     * @return Cache key in format "activity@context"
     */
    private String generateCacheKey(com.nava.schedulepartner.models.Context context,
                                    Activity activity) {
        return activity.getIdentifier() + "@" + context.getIdentifier();
    }

    /**
     * Generates a filename for the given context and activity.
     *
     * @param context The context
     * @param activity The activity
     * @return Filename in format "Activity@Context.csv"
     */
    private String generateFilename(com.nava.schedulepartner.models.Context context,
                                    Activity activity) {
        return activity.getIdentifier() + "@" +
                context.getIdentifier() + TEMPLATE_EXTENSION;
    }

    /**
     * Loads a schedule from user templates directory.
     *
     * @param context The context to load for
     * @param activity The activity to load for
     * @return The loaded Schedule, or null if not found or error
     */
    private Schedule loadUserSchedule(com.nava.schedulepartner.models.Context context,
                                      Activity activity) {
        String filename = generateFilename(context, activity);
        File templateFile = new File(templatesDir, filename);

        if (!templateFile.exists()) {
            return null;
        }

        try {
            String csvContent = readFileContent(templateFile);
            return Schedule.fromCsv(csvContent);
        } catch (IOException e) {
            Log.e(TAG, "Error loading user schedule: " + filename, e);
            return null;
        }
    }

    /**
     * Loads a schedule from default templates in assets.
     *
     * @param context The context to load for
     * @param activity The activity to load for
     * @return The loaded Schedule, or null if not found or error
     */
    private Schedule loadDefaultSchedule(com.nava.schedulepartner.models.Context context,
                                         Activity activity) {
        String filename = generateFilename(context, activity);
        String assetPath = ASSETS_TEMPLATES_DIR + "/" + filename;

        try {
            AssetManager assets = this.context.getAssets();
            InputStream inputStream = assets.open(assetPath);
            String csvContent = readStreamContent(inputStream);
            return Schedule.fromCsv(csvContent);
        } catch (IOException e) {
            Log.d(TAG, "No default template found: " + assetPath);
            return null;
        }
    }

    /**
     * Saves a schedule to user templates directory.
     * <p>
     * Overwrites existing template if present. Clears cache
     * to ensure fresh data on next load.
     *
     * @param schedule The schedule to save
     * @return True if saved successfully, false otherwise
     */
    public boolean saveSchedule(Schedule schedule) {
        String filename = generateFilename(schedule.getContext(),
                schedule.getActivity());
        File templateFile = new File(templatesDir, filename);

        try {
            String csvContent = schedule.toCsv();
            writeFileContent(templateFile, csvContent);

            // Clear cache entry to force reload
            String key = generateCacheKey(schedule.getContext(),
                    schedule.getActivity());
            scheduleCache.remove(key);

            Log.d(TAG, "Saved schedule: " + filename);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving schedule: " + filename, e);
            return false;
        }
    }

    /**
     * Imports a schedule from external CSV file.
     * <p>
     * Validates the schedule before saving to ensure it's
     * properly formatted and contains required information.
     *
     * @param csvFile The CSV file to import
     * @return The imported Schedule if successful, null otherwise
     */
    public Schedule importSchedule(File csvFile) {
        try {
            String csvContent = readFileContent(csvFile);
            Schedule schedule = Schedule.fromCsv(csvContent);

            // Save to user templates
            if (saveSchedule(schedule)) {
                return schedule;
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error importing schedule: " + csvFile.getName(), e);
            return null;
        }
    }

    /**
     * Exports a schedule to external storage.
     *
     * @param schedule The schedule to export
     * @param exportFile The file to export to
     * @return True if exported successfully, false otherwise
     */
    public boolean exportSchedule(Schedule schedule, File exportFile) {
        try {
            String csvContent = schedule.toCsv();
            writeFileContent(exportFile, csvContent);
            Log.d(TAG, "Exported schedule to: " + exportFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error exporting schedule", e);
            return false;
        }
    }

    /**
     * Clears the schedule cache and forces reload on next access.
     * <p>
     * Called when templates are imported or modified to ensure
     * fresh data is loaded.
     */
    public void refreshSchedules() {
        scheduleCache.clear();
        Log.d(TAG, "Schedule cache cleared");
    }

    /**
     * Checks if a schedule exists for the given combination.
     *
     * @param context The context to check
     * @param activity The activity to check
     * @return True if a template exists, false otherwise
     */
    public boolean hasSchedule(com.nava.schedulepartner.models.Context context,
                               Activity activity) {
        String filename = generateFilename(context, activity);

        // Check user templates first
        File userTemplate = new File(templatesDir, filename);
        if (userTemplate.exists()) {
            return true;
        }

        // Check default templates
        try {
            String assetPath = ASSETS_TEMPLATES_DIR + "/" + filename;
            context.getAssets().open(assetPath).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Deletes a user template (cannot delete default templates).
     *
     * @param context The context of the template to delete
     * @param activity The activity of the template to delete
     * @return True if deleted successfully, false otherwise
     */
    public boolean deleteUserSchedule(com.nava.schedulepartner.models.Context context,
                                      Activity activity) {
        String filename = generateFilename(context, activity);
        File templateFile = new File(templatesDir, filename);

        if (templateFile.exists() && templateFile.delete()) {
            // Clear from cache
            String key = generateCacheKey(context, activity);
            scheduleCache.remove(key);

            Log.d(TAG, "Deleted user template: " + filename);
            return true;
        }

        return false;
    }

    /**
     * Copies default templates from assets on first run.
     * <p>
     * This ensures users have working templates immediately without
     * requiring manual import. Only copies if templates directory
     * is empty to avoid overwriting user modifications.
     */
    private void copyDefaultTemplatesIfNeeded() {
        // Check if templates directory is empty
        File[] existingFiles = templatesDir.listFiles();
        if (existingFiles != null && existingFiles.length > 0) {
            // User already has templates, don't overwrite
            return;
        }

        try {
            AssetManager assets = context.getAssets();
            String[] templateFiles = assets.list(ASSETS_TEMPLATES_DIR);

            if (templateFiles != null) {
                for (String filename : templateFiles) {
                    if (filename.endsWith(TEMPLATE_EXTENSION)) {
                        copyDefaultTemplate(filename);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error listing default templates", e);
        }
    }

    /**
     * Copies a single default template from assets to user directory.
     *
     * @param filename The template filename to copy
     */
    private void copyDefaultTemplate(String filename) {
        try {
            String assetPath = ASSETS_TEMPLATES_DIR + "/" + filename;
            InputStream inputStream = context.getAssets().open(assetPath);
            String content = readStreamContent(inputStream);

            File outputFile = new File(templatesDir, filename);
            writeFileContent(outputFile, content);

            Log.d(TAG, "Copied default template: " + filename);
        } catch (IOException e) {
            Log.e(TAG, "Error copying default template: " + filename, e);
        }
    }

    /**
     * Reads content from a file.
     *
     * @param file The file to read
     * @return The file content as string
     * @throws IOException if read fails
     */
    private String readFileContent(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        return readStreamContent(fis);
    }

    /**
     * Reads content from an input stream.
     *
     * @param inputStream The stream to read
     * @return The stream content as string
     * @throws IOException if read fails
     */
    private String readStreamContent(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Writes content to a file.
     *
     * @param file The file to write to
     * @param content The content to write
     * @throws IOException if write fails
     */
    private void writeFileContent(File file, String content) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}