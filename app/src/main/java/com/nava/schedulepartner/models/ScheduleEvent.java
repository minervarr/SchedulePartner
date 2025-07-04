package com.nava.schedulepartner.models;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Represents a single scheduled event in the discipline coaching system.
 * <p>
 * This class encapsulates all information needed for a single schedule entry,
 * including timing, event type, and alert configuration. Events are the atomic
 * units that make up a daily schedule template. Each event triggers at a specific
 * time with a specific notification behavior.
 * <p>
 * ScheduleEvent objects are:
 * - Immutable after creation for thread safety
 * - Comparable by time for easy sorting
 * - Serializable to/from CSV and JSON formats
 * - Self-contained with all display information
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public class ScheduleEvent implements Comparable<ScheduleEvent> {
    /**
     * The exact time when this event should trigger.
     * <p>
     * Uses 24-hour format for precision. Events are scheduled
     * to the minute for optimal time management.
     */
    private final LocalTime time;

    /**
     * The type of event that determines its behavior and appearance.
     * <p>
     * Links to audio files, display colors, and notification text
     * through the EventType enum.
     */
    private final EventType eventType;

    /**
     * The notification priority level for this event.
     * <p>
     * Determines whether and how the user is notified when this
     * event triggers.
     */
    private final AlertLevel alertLevel;

    /**
     * Optional custom message for this specific event instance.
     * <p>
     * Allows templates to override default event messages for
     * context-specific information. If null, default message is used.
     */
    private final String customMessage;

    /**
     * Optional duration in minutes for events that span time.
     * <p>
     * Used for block-based events like "Study for 90 minutes".
     * Zero or negative values indicate point-in-time events.
     */
    private final int durationMinutes;

    /**
     * Standard time formatter for consistent display and parsing.
     * <p>
     * Uses 24-hour format (HH:mm) for clarity and international
     * compatibility.
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Creates a new ScheduleEvent with all parameters.
     *
     * @param time The exact trigger time for this event
     * @param eventType The type of event determining its behavior
     * @param alertLevel The notification priority level
     * @param customMessage Optional custom display message (can be null)
     * @param durationMinutes Optional duration in minutes (0 for point events)
     */
    public ScheduleEvent(LocalTime time, EventType eventType, AlertLevel alertLevel,
                         String customMessage, int durationMinutes) {
        this.time = time;
        this.eventType = eventType;
        this.alertLevel = alertLevel;
        this.customMessage = customMessage;
        this.durationMinutes = Math.max(0, durationMinutes);
    }

    /**
     * Creates a simple ScheduleEvent without custom message or duration.
     * <p>
     * Convenience constructor for most common use case of point-in-time
     * events with default messaging.
     *
     * @param time The exact trigger time for this event
     * @param eventType The type of event determining its behavior
     * @param alertLevel The notification priority level
     */
    public ScheduleEvent(LocalTime time, EventType eventType, AlertLevel alertLevel) {
        this(time, eventType, alertLevel, null, 0);
    }

    /**
     * Gets the trigger time for this event.
     *
     * @return The event time as LocalTime
     */
    public LocalTime getTime() {
        return time;
    }

    /**
     * Gets the formatted time string for display.
     *
     * @return Time in HH:mm format (e.g., "14:30")
     */
    public String getTimeString() {
        return time.format(TIME_FORMATTER);
    }

    /**
     * Gets the event type.
     *
     * @return The EventType enum value
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Gets the alert level for notifications.
     *
     * @return The AlertLevel enum value
     */
    public AlertLevel getAlertLevel() {
        return alertLevel;
    }

    /**
     * Gets the custom message if set.
     *
     * @return Custom message string, or null if using default
     */
    public String getCustomMessage() {
        return customMessage;
    }

    /**
     * Gets the event duration in minutes.
     *
     * @return Duration in minutes, or 0 for point events
     */
    public int getDurationMinutes() {
        return durationMinutes;
    }

    /**
     * Checks if this is a duration-based event.
     *
     * @return True if event has a duration > 0
     */
    public boolean hasDuration() {
        return durationMinutes > 0;
    }

    /**
     * Gets the end time for duration-based events.
     * <p>
     * Only valid if hasDuration() returns true.
     *
     * @return End time, or start time if no duration
     */
    public LocalTime getEndTime() {
        return time.plusMinutes(durationMinutes);
    }

    /**
     * Gets the display message for this event.
     * <p>
     * Returns custom message if set, otherwise generates a default
     * message based on the event type. This will eventually use
     * string resources for internationalization.
     *
     * @return Display message for UI
     */
    public String getDisplayMessage() {
        if (customMessage != null && !customMessage.isEmpty()) {
            return customMessage;
        }

        // Generate default message based on event type
        // In production, these would come from strings.xml
        switch (eventType) {
            case WAKE_UP:
                return "Time to wake up!";
            case NO_COFFEE:
                return "No more caffeine";
            case MEAL_TIME:
                return "Meal time";
            case MEAL_REMINDER_5MIN:
                return "Meal in 5 minutes";
            case STUDY_START:
                return "Begin study session";
            case STUDY_REMAINING_2H:
                return "2 hours study remaining";
            case BREAK_REMINDER:
                return "Time for a break";
            case BEDTIME:
                return "Time for bed";
            case STOP_SCREENS:
                return "Turn off all screens";
            default:
                return eventType.name().replace('_', ' ');
        }
    }

    /**
     * Gets the label color for this event.
     * <p>
     * Red for restrictions, blue for reminders and activities.
     *
     * @return Color resource ID for label display
     */
    public int getLabelColor() {
        return eventType.isRestriction() ?
                android.R.color.holo_red_dark :
                android.R.color.holo_blue_dark;
    }

    /**
     * Calculates minutes until this event from current time.
     * <p>
     * Handles day wraparound for events scheduled tomorrow.
     * Returns negative values for past events.
     *
     * @param currentTime The current time to calculate from
     * @return Minutes until event (negative if past)
     */
    public long getMinutesUntil(LocalTime currentTime) {
        long minutes = ChronoUnit.MINUTES.between(currentTime, time);

        // Handle day wraparound (event tomorrow)
        if (minutes < -720) {  // More than 12 hours in past
            minutes += 1440;   // Add 24 hours
        }

        return minutes;
    }

    /**
     * Checks if this event should trigger at the given time.
     * <p>
     * Accounts for minute precision - triggers during the entire
     * minute of the scheduled time.
     *
     * @param currentTime The current time to check
     * @return True if event should trigger now
     */
    public boolean shouldTrigger(LocalTime currentTime) {
        return time.getHour() == currentTime.getHour() &&
                time.getMinute() == currentTime.getMinute();
    }

    /**
     * Checks if this event is currently active (for duration events).
     * <p>
     * Point events are only active at their exact minute.
     * Duration events are active throughout their time span.
     *
     * @param currentTime The current time to check
     * @return True if event is currently active
     */
    public boolean isActive(LocalTime currentTime) {
        if (!hasDuration()) {
            return shouldTrigger(currentTime);
        }

        return !currentTime.isBefore(time) &&
                currentTime.isBefore(getEndTime());
    }

    /**
     * Compares events by time for sorting.
     * <p>
     * Events are ordered chronologically. If times are equal,
     * CRITICAL alerts come before others.
     *
     * @param other The other event to compare to
     * @return Negative if this comes first, positive if other comes first
     */
    @Override
    public int compareTo(ScheduleEvent other) {
        int timeComparison = this.time.compareTo(other.time);
        if (timeComparison != 0) {
            return timeComparison;
        }

        // If same time, prioritize by alert level
        return this.alertLevel.getPriority() - other.alertLevel.getPriority();
    }

    /**
     * Creates a ScheduleEvent from a CSV row.
     * <p>
     * Expected format: "HH:mm,EVENT_TYPE,ALERT_LEVEL[,custom_message][,duration]"
     * Validates input and throws exception for invalid data.
     *
     * @param csvRow The CSV row to parse
     * @return A new ScheduleEvent instance
     * @throws IllegalArgumentException if CSV format is invalid
     */
    public static ScheduleEvent fromCsv(String csvRow) {
        String[] parts = csvRow.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Invalid CSV format. Expected: time,event_type,alert_level");
        }

        try {
            LocalTime time = LocalTime.parse(parts[0].trim(), TIME_FORMATTER);
            EventType eventType = EventType.valueOf(parts[1].trim().toUpperCase());
            AlertLevel alertLevel = AlertLevel.valueOf(parts[2].trim().toUpperCase());

            String customMessage = parts.length > 3 ? parts[3].trim() : null;
            int duration = parts.length > 4 ? Integer.parseInt(parts[4].trim()) : 0;

            return new ScheduleEvent(time, eventType, alertLevel, customMessage, duration);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Error parsing CSV row: " + e.getMessage(), e);
        }
    }

    /**
     * Converts this event to CSV format.
     * <p>
     * Generates a CSV row that can be parsed back by fromCsv().
     *
     * @return CSV representation of this event
     */
    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(getTimeString()).append(",");
        csv.append(eventType.name()).append(",");
        csv.append(alertLevel.name());

        if (customMessage != null && !customMessage.isEmpty()) {
            csv.append(",").append(customMessage);
        }

        if (durationMinutes > 0) {
            if (customMessage == null) {
                csv.append(",");  // Empty custom message field
            }
            csv.append(",").append(durationMinutes);
        }

        return csv.toString();
    }

    @Override
    public String toString() {
        return String.format("ScheduleEvent[%s: %s (%s) - %s]",
                getTimeString(), eventType, alertLevel,
                hasDuration() ? durationMinutes + " min" : "point event");
    }
}