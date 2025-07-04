package com.nava.schedulepartner.models;

/**
 * Enumeration of all possible event types in the discipline coaching system.
 * <p>
 * This enum defines every type of event that can occur during a coaching session.
 * Each event type corresponds to a specific notification or reminder that helps
 * users maintain their discipline throughout the day. Events are categorized by
 * their purpose and urgency level.
 * <p>
 * The event types are used throughout the application for:
 * - Mapping to audio files for spoken alerts
 * - Determining notification priorities
 * - Displaying appropriate UI colors (red for restrictions, blue for reminders)
 * - CSV/JSON schedule parsing and validation
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public enum EventType {
    /**
     * Morning wake-up event.
     * <p>
     * Triggers at the scheduled wake time to start the day.
     * This is always a CRITICAL event as it marks the beginning
     * of the daily discipline routine.
     */
    WAKE_UP("wake_up", true),

    /**
     * Caffeine restriction event.
     * <p>
     * Alerts user when they should stop consuming coffee/caffeine
     * to ensure proper sleep later. This is a health-critical restriction
     * displayed with red label.
     */
    NO_COFFEE("no_coffee", true),

    /**
     * Meal time event.
     * <p>
     * Critical reminder for scheduled meals to maintain proper
     * nutrition timing and energy levels throughout the day.
     */
    MEAL_TIME("meal_time", false),

    /**
     * Advance meal reminder.
     * <p>
     * Blue label reminder that appears 5-15 minutes before meal time,
     * allowing users to prepare or wrap up current activities.
     */
    MEAL_REMINDER_5MIN("meal_5min", false),
    MEAL_REMINDER_10MIN("meal_10min", false),
    MEAL_REMINDER_15MIN("meal_15min", false),

    /**
     * Study session start event.
     * <p>
     * Marks the beginning of a focused study period. Usually paired
     * with automatic DND activation to minimize distractions.
     */
    STUDY_START("study_start", false),

    /**
     * Study time remaining notifications.
     * <p>
     * Reference alerts showing how much study time is left in current
     * session. Helps with time awareness without breaking focus.
     */
    STUDY_REMAINING_30MIN("study_remaining_30min", false),
    STUDY_REMAINING_1H("study_remaining_1h", false),
    STUDY_REMAINING_2H("study_remaining_2h", false),

    /**
     * Break reminder event.
     * <p>
     * Suggests taking a break during long work/study sessions.
     * Can be configured as TRANSITION or CRITICAL based on context.
     */
    BREAK_REMINDER("break_reminder", false),

    /**
     * Work session events.
     * <p>
     * Similar to study events but for professional work contexts.
     * Includes start, end, and time remaining notifications.
     */
    WORK_START("work_start", false),
    WORK_END("work_end", false),
    WORK_REMAINING_1H("work_remaining_1h", false),
    WORK_REMAINING_2H("work_remaining_2h", false),

    /**
     * Exercise and physical activity events.
     * <p>
     * Reminders for scheduled physical activities, important for
     * maintaining energy and health throughout intensive work periods.
     */
    EXERCISE_START("exercise_start", false),
    EXERCISE_END("exercise_end", false),

    /**
     * Evening routine events.
     * <p>
     * Critical events for maintaining healthy sleep hygiene and
     * proper rest for next day's performance.
     */
    STOP_SCREENS("stop_screens", true),
    BEDTIME("bedtime", true),

    /**
     * Generic transition event.
     * <p>
     * Used for minor activity switches that don't fit other categories.
     * Alert level determined by specific template configuration.
     */
    TRANSITION("transition", false),

    /**
     * Custom user-defined event.
     * <p>
     * Allows for template-specific events not covered by standard types.
     * Requires corresponding audio file with matching identifier.
     */
    CUSTOM("custom", false);

    /**
     * The identifier used for audio file mapping.
     * <p>
     * This string must match the audio filename (without extension)
     * for the audio system to properly play alerts. For example,
     * NO_COFFEE maps to "no_coffee.mp3" or "no_coffee.flac".
     */
    private final String audioIdentifier;

    /**
     * Whether this event represents a restriction/prohibition.
     * <p>
     * True for events that should display with red labels (NO_COFFEE,
     * STOP_SCREENS), false for blue label reminders and notifications.
     */
    private final boolean isRestriction;

    /**
     * Constructor for EventType enum values.
     *
     * @param audioIdentifier The string identifier for audio file mapping
     * @param isRestriction True if this is a red-label restriction event
     */
    EventType(String audioIdentifier, boolean isRestriction) {
        this.audioIdentifier = audioIdentifier;
        this.isRestriction = isRestriction;
    }

    /**
     * Gets the audio identifier for this event type.
     * <p>
     * Used by the AudioManager to locate the corresponding audio file.
     * The actual file extension (.mp3, .flac, etc.) is determined at runtime.
     *
     * @return The audio file identifier string
     */
    public String getAudioIdentifier() {
        return audioIdentifier;
    }

    /**
     * Checks if this event is a restriction that should display in red.
     *
     * @return True if this is a restriction event, false otherwise
     */
    public boolean isRestriction() {
        return isRestriction;
    }

    /**
     * Finds an EventType by its audio identifier string.
     * <p>
     * Used when parsing CSV/JSON files to convert string identifiers
     * back to strongly-typed enum values.
     *
     * @param identifier The audio identifier to search for
     * @return The matching EventType, or null if not found
     */
    public static EventType fromAudioIdentifier(String identifier) {
        for (EventType type : values()) {
            if (type.audioIdentifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return null;
    }
}