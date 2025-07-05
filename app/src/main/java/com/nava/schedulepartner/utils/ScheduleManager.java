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

public class ScheduleManager {
    private static final String TAG = "ScheduleManager";
    private static final String TEMPLATES_DIR = "templates";
    private static final String ASSETS_TEMPLATES_DIR = "default_templates";
    private static final String TEMPLATE_EXTENSION = ".csv";
    private static ScheduleManager instance;
    private final Context context;
    private final Map<String, Schedule> scheduleCache;
    private final File templatesDir;

    private ScheduleManager(Context context) {
        this.context = context.getApplicationContext();
        this.scheduleCache = new HashMap<>();
        this.templatesDir = new File(context.getFilesDir(), TEMPLATES_DIR);
        if (!templatesDir.exists()) {
            if (!templatesDir.mkdirs()) {
                Log.e(TAG, "Failed to create templates directory");
            }
        }
        copyDefaultTemplatesIfNeeded();
    }

    public static synchronized ScheduleManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScheduleManager(context);
        }
        return instance;
    }

    public Schedule loadSchedule(com.nava.schedulepartner.models.Context context,
                                 Activity activity) {
        String key = generateCacheKey(context, activity);
        if (scheduleCache.containsKey(key)) {
            Log.d(TAG, "Loading schedule from cache: " + key);
            return scheduleCache.get(key);
        }

        Schedule schedule = loadUserSchedule(context, activity);
        if (schedule == null) {
            schedule = loadDefaultSchedule(context, activity);
        }

        if (schedule != null) {
            scheduleCache.put(key, schedule);
            Log.d(TAG, "Cached schedule: " + key);
        }

        return schedule;
    }

    private String generateCacheKey(com.nava.schedulepartner.models.Context context,
                                    Activity activity) {
        return activity.getIdentifier() + "@" + context.getIdentifier();
    }

    private String generateFilename(com.nava.schedulepartner.models.Context context,
                                    Activity activity) {
        return activity.getIdentifier() + "@" +
                context.getIdentifier() + TEMPLATE_EXTENSION;
    }

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

    public boolean saveSchedule(Schedule schedule) {
        String filename = generateFilename(schedule.getContext(),
                schedule.getActivity());
        File templateFile = new File(templatesDir, filename);

        try {
            String csvContent = schedule.toCsv();
            writeFileContent(templateFile, csvContent);
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

    public Schedule importSchedule(File csvFile) {
        try {
            String csvContent = readFileContent(csvFile);
            Schedule schedule = Schedule.fromCsv(csvContent);
            if (saveSchedule(schedule)) {
                return schedule;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error importing schedule: " + csvFile.getName(), e);
            return null;
        }
    }

    public void refreshSchedules() {
        scheduleCache.clear();
        Log.d(TAG, "Schedule cache cleared");
    }

    private void copyDefaultTemplatesIfNeeded() {
        File[] existingFiles = templatesDir.listFiles();
        if (existingFiles != null && existingFiles.length > 0) {
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

    private String readFileContent(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        return readStreamContent(fis);
    }

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

    private void writeFileContent(File file, String content) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}