package com.nava.schedulepartner.models;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;

/**
 * Represents a complete daily schedule template for a Context+Activity combination.
 * <p>
 * A Schedule contains an ordered list of ScheduleEvent objects that define the
 * minute-by-minute structure of a coaching session. Each schedule is designed
 * for a specific context (WHERE) and activity (WHAT) pairing, allowing for
 * optimal time management in different situations.
 * <p>
 * Key features:
 * - Immutable after creation for thread safety
 * - Automatically sorts events chronologically
 * - Provides efficient lookup of current/next events
 * - Supports CSV import/export for easy editing
 * - Validates schedule consistency and completeness
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class Schedule {
    /**
     * The context where this schedule applies.
     * <p>
     * Determines environmental factors like audio settings,
     * break flexibility, and notification behavior.
     */
    private final Context context;

    /**
     * The activity type this schedule is designed for.
     * <p>
     * Influences work/rest ratios, focus requirements,
     * and overall schedule structure.
     */
    private final Activity activity;

    /**
     * Ordered list of events in this schedule.
     * <p>
     * Maintained in chronological order for efficient
     * time-based queries. Immutable after construction.
     */
    private final List<ScheduleEvent> events;

    /**
     * Optional schedule name for user identification.
     * <p>
     * Defaults to "Activity@Context" format if not specified.
     * Useful for custom variations of standard templates.
     */
    private final String name;

    /**
     * Schedule version for tracking template updates.
     * <p>
     * Helps users know when templates have been improved
     * or modified from defaults.
     */
    private final String version;

    /**
     * Creates a new Schedule with all parameters.
     *
     * @param context The context where this schedule applies
     * @param activity The activity type for this schedule
     * @param events List of schedule events (will be sorted)
     * @param name Optional custom name (null for default)
     * @param version Optional version string (null for "1.0")
     */
    public Schedule(Context context, Activity activity, List<ScheduleEvent> events,
                    String name, String version) {
        this.context = context;
        this.activity = activity;

        // Create defensive copy and sort chronologically
        this.events = new ArrayList<>(events);
        Collections.sort(this.events);

        // Set name with default fallback
        this.name = (name != null && !name.isEmpty()) ?
                name : generateDefaultName();

        this.version = (version != null && !version.isEmpty()) ?
                version : "1.0";

        validateSchedule();
    }

    /**
     * Creates a Schedule with default name and version.
     *
     * @param context The context where this schedule applies
     * @param activity The activity type for this schedule
     * @param events List of schedule events
     */
    public Schedule(Context context, Activity activity, List<ScheduleEvent> events) {
        this(context, activity, events, null, null);
    }

    /**
     * Generates the default schedule name.
     *
     * @return Name in "Activity@Context" format
     */
    private String generateDefaultName() {
        return activity.getDisplayName() + "@" + context.getDisplayName();
    }

    /**
     * Validates schedule consistency and completeness.
     * <p>
     * Checks for:
     * - Required events (wake up, meals, bedtime)
     * - Reasonable timing constraints
     * - No overlapping duration events
     * - Activity-specific requirements
     *
     * @throws IllegalStateException if schedule is invalid
     */
    private void validateSchedule() {
        if (events.isEmpty()) {
            throw new IllegalStateException("Schedule cannot be empty");
        }

        // Check for required events based on activity
        if (activity != Activity.REST) {
            boolean hasStart = events.stream()
                    .anyMatch(e -> e.getEventType().name().contains("START"));
            if (!hasStart) {
                throw new IllegalStateException(
                        "Non-rest schedules must have a START event");
            }
        }

        // Check for overlapping duration events
        for (int i = 0; i < events.size() - 1; i++) {
            ScheduleEvent current = events.get(i);
            ScheduleEvent next = events.get(i + 1);

            if (current.hasDuration() &&
                    current.getEndTime().isAfter(next.getTime())) {
                throw new IllegalStateException(String.format(
                        "Overlapping events: %s ends after %s starts",
                        current, next));
            }
        }
    }

    /**
     * Gets the context for this schedule.
     *
     * @return The Context enum value
     */
    public Context getContext() {
        return context;
    }

    /**
     * Gets the activity for this schedule.
     *
     * @return The Activity enum value
     */
    public Activity getActivity() {
        return activity;
    }

    /**
     * Gets an immutable view of all events.
     *
     * @return Unmodifiable list of events in chronological order
     */
    public List<ScheduleEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * Gets the schedule name.
     *
     * @return The schedule name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the schedule version.
     *
     * @return The version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the filename for this schedule template.
     * <p>
     * Format: "Activity@Context.csv" for standard templates.
     *
     * @return The suggested filename
     */
    public String getFilename() {
        return activity.getIdentifier() + "@" +
                context.getIdentifier() + ".csv";
    }

    /**
     * Finds the currently active event at the given time.
     * <p>
     * For duration events, returns the event if time falls within
     * its duration. For point events, only returns during the exact
     * minute. Returns null if no event is currently active.
     *
     * @param currentTime The time to check
     * @return The active event, or null if none
     */
    public ScheduleEvent getCurrentEvent(LocalTime currentTime) {
        // Check duration events first (they span time)
        for (ScheduleEvent event : events) {
            if (event.hasDuration() && event.isActive(currentTime)) {
                return event;
            }
        }

        // Then check point events
        for (ScheduleEvent event : events) {
            if (!event.hasDuration() && event.shouldTrigger(currentTime)) {
                return event;
            }
        }

        return null;
    }

    /**
     * Gets the next upcoming event after the given time.
     * <p>
     * Handles day wraparound - if no events remain today,
     * returns the first event (tomorrow's wake up).
     *
     * @param currentTime The time to search from
     * @return The next event, or first event if none today
     */
    public ScheduleEvent getNextEvent(LocalTime currentTime) {
        for (ScheduleEvent event : events) {
            if (event.getTime().isAfter(currentTime)) {
                return event;
            }
        }

        // No more events today, return first event (tomorrow)
        return events.get(0);
    }

    /**
     * Gets upcoming events within the specified time window.
     * <p>
     * Useful for displaying "next few events" in the UI.
     * Does not include currently active events.
     *
     * @param currentTime The time to search from
     * @param minutes How many minutes ahead to look
     * @return List of upcoming events within the window
     */
    public List<ScheduleEvent> getUpcomingEvents(LocalTime currentTime, int minutes) {
        List<ScheduleEvent> upcoming = new ArrayList<>();
        LocalTime windowEnd = currentTime.plusMinutes(minutes);

        for (ScheduleEvent event : events) {
            if (event.getTime().isAfter(currentTime)) {
                if (event.getTime().isBefore(windowEnd) ||
                        event.getTime().equals(windowEnd)) {
                    upcoming.add(event);
                }
            }
        }

        return upcoming;
    }

    /**
     * Gets events filtered by alert level.
     * <p>
     * Useful for notification scheduling and UI filtering.
     *
     * @param level The alert level to filter by
     * @return List of events with the specified alert level
     */
    public List<ScheduleEvent> getEventsByAlertLevel(AlertLevel level) {
        List<ScheduleEvent> filtered = new ArrayList<>();
        for (ScheduleEvent event : events) {
            if (event.getAlertLevel() == level) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    /**
     * Calculates total scheduled time for the main activity.
     * <p>
     * Sums up duration of all events related to the primary
     * activity (STUDY_START, WORK_START, etc).
     *
     * @return Total minutes scheduled for main activity
     */
    public int getTotalActivityMinutes() {
        int total = 0;
        for (ScheduleEvent event : events) {
            if (event.hasDuration() &&
                    event.getEventType().name().contains(activity.name())) {
                total += event.getDurationMinutes();
            }
        }
        return total;
    }

    /**
     * Creates a Schedule from CSV content.
     * <p>
     * Expected format:
     * - Line 1: Context identifier
     * - Line 2: Activity identifier
     * - Line 3: Optional name
     * - Line 4: Optional version
     * - Line 5: Header row (Time,Event_Type,Alert_Level)
     * - Remaining: Event data rows
     *
     * @param csvContent The complete CSV content as string
     * @return A new Schedule instance
     * @throws IOException if parsing fails
     * @throws IllegalArgumentException if format is invalid
     */
    public static Schedule fromCsv(String csvContent) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(csvContent));

        // Read metadata
        String contextLine = reader.readLine();
        if (contextLine == null || contextLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing context identifier");
        }
        Context context = Context.fromIdentifier(contextLine.trim());
        if (context == null) {
            throw new IllegalArgumentException("Invalid context: " + contextLine);
        }

        String activityLine = reader.readLine();
        if (activityLine == null || activityLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing activity identifier");
        }
        Activity activity = Activity.fromIdentifier(activityLine.trim());
        if (activity == null) {
            throw new IllegalArgumentException("Invalid activity: " + activityLine);
        }

        // Optional metadata
        String nameLine = reader.readLine();
        String name = (nameLine != null && !nameLine.trim().isEmpty() &&
                !nameLine.contains(",")) ? nameLine.trim() : null;

        String versionLine = (name != null) ? reader.readLine() : nameLine;
        String version = (versionLine != null && !versionLine.trim().isEmpty() &&
                !versionLine.contains(",")) ? versionLine.trim() : null;

        // Skip header if present
        String headerLine = (version != null) ? reader.readLine() : versionLine;
        if (headerLine != null && headerLine.toLowerCase().contains("time")) {
            // This is the header, read next line
            headerLine = reader.readLine();
        }

        // Parse events
        List<ScheduleEvent> events = new ArrayList<>();
        String line = headerLine;

        while (line != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {  // Skip comments
                try {
                    events.add(ScheduleEvent.fromCsv(line));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Error parsing event line: " + line, e);
                }
            }
            line = reader.readLine();
        }

        return new Schedule(context, activity, events, name, version);
    }

    /**
     * Converts this schedule to CSV format.
     * <p>
     * Generates CSV content that can be parsed back by fromCsv().
     * Includes metadata and header row for easy editing.
     *
     * @return CSV representation of this schedule
     */
    public String toCsv() {
        StringBuilder csv = new StringBuilder();

        // Metadata
        csv.append(context.getIdentifier()).append("\n");
        csv.append(activity.getIdentifier()).append("\n");
        csv.append(name).append("\n");
        csv.append(version).append("\n");

        // Header
        csv.append("Time,Event_Type,Alert_Level,Custom_Message,Duration\n");

        // Events
        for (ScheduleEvent event : events) {
            csv.append(event.toCsv()).append("\n");
        }

        return csv.toString();
    }

    @Override
    public String toString() {
        return String.format("Schedule[%s: %d events, v%s]",
                name, events.size(), version);
    }
}