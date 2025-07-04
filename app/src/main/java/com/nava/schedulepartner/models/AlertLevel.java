package com.nava.schedulepartner.models;

/**
 * Defines the three-tier alert priority system for notifications.
 * <p>
 * This enum implements the smart notification system that balances between
 * keeping users informed and avoiding notification fatigue. Each level has
 * specific behaviors regarding interruptions, audio playback, and battery
 * optimization.
 * <p>
 * The alert level system is designed to:
 * - Preserve focus during deep work sessions
 * - Ensure critical events are never missed
 * - Optimize battery life by reducing unnecessary wake-ups
 * - Allow user customization per template/context
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public enum AlertLevel {
    /**
     * Critical alerts that always notify, even during focus sessions.
     * <p>
     * These alerts represent time-sensitive events that have real consequences
     * if missed. They will:
     * - Always trigger a full notification with sound
     * - Wake the device from sleep
     * - Bypass Do Not Disturb settings (using Alarm channel)
     * - Display prominently on the lock screen
     * <p>
     * Examples include:
     * - Meal times (health-critical for maintaining energy)
     * - Sleep time (critical for next day's performance)
     * - Caffeine cutoff (affects sleep quality)
     * - External commitments (meetings, classes)
     */
    CRITICAL(1, true, true, true),

    /**
     * Transition alerts with context-dependent behavior.
     * <p>
     * These alerts can be configured differently per template. In some contexts
     * they behave like CRITICAL alerts (e.g., during exam preparation), while
     * in others they're more like REFERENCE alerts (e.g., during casual reading).
     * <p>
     * Default behavior:
     * - Show notification without sound
     * - Don't wake device from sleep
     * - Respect Do Not Disturb settings
     * - Can be overridden in template configuration
     * <p>
     * Examples include:
     * - Subject switches during study sessions
     * - Minor break reminders
     * - Activity transitions within same context
     */
    TRANSITION(2, true, false, false),

    /**
     * Reference alerts that only appear when checking the app.
     * <p>
     * These are informational updates that don't require immediate action.
     * They optimize battery life by never waking the device or triggering
     * system notifications. Users see these when they manually open the app
     * to check their progress.
     * <p>
     * Behavior:
     * - No system notifications
     * - No audio playback
     * - Only visible in-app
     * - Updated in real-time when app is open
     * <p>
     * Examples include:
     * - Time remaining in current activity
     * - Progress statistics
     * - Permission checks ("Can I have coffee now?")
     * - Completed milestone acknowledgments
     */
    REFERENCE(3, false, false, false);

    /**
     * Numeric priority level for sorting and comparison.
     * <p>
     * Lower numbers indicate higher priority. Used when multiple
     * alerts occur simultaneously to determine which takes precedence.
     */
    private final int priority;

    /**
     * Whether this alert level triggers system notifications.
     * <p>
     * True for CRITICAL and TRANSITION, false for REFERENCE.
     * This determines if the Android notification system is engaged.
     */
    private final boolean triggersNotification;

    /**
     * Whether this alert level plays audio by default.
     * <p>
     * Can be overridden by global audio mute setting or context-specific
     * configuration. Only CRITICAL alerts play audio by default.
     */
    private final boolean playsAudio;

    /**
     * Whether this alert can wake the device from sleep.
     * <p>
     * Only CRITICAL alerts have wake lock permission to ensure
     * time-sensitive events aren't missed due to device sleep.
     */
    private final boolean wakesDevice;

    /**
     * Constructor for AlertLevel enum values.
     *
     * @param priority Numeric priority (lower = higher priority)
     * @param triggersNotification Whether to show system notifications
     * @param playsAudio Whether to play audio alerts by default
     * @param wakesDevice Whether to wake device from sleep
     */
    AlertLevel(int priority, boolean triggersNotification,
               boolean playsAudio, boolean wakesDevice) {
        this.priority = priority;
        this.triggersNotification = triggersNotification;
        this.playsAudio = playsAudio;
        this.wakesDevice = wakesDevice;
    }

    /**
     * Gets the numeric priority of this alert level.
     *
     * @return Priority value (1=highest, 3=lowest)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Checks if this alert level triggers system notifications.
     *
     * @return True if notifications should be shown
     */
    public boolean triggersNotification() {
        return triggersNotification;
    }

    /**
     * Checks if this alert level plays audio by default.
     * <p>
     * Note: This can be overridden by global mute settings or
     * context-specific configuration in templates.
     *
     * @return True if audio should play by default
     */
    public boolean playsAudio() {
        return playsAudio;
    }

    /**
     * Checks if this alert level can wake the device.
     *
     * @return True if wake locks should be acquired
     */
    public boolean wakesDevice() {
        return wakesDevice;
    }

    /**
     * Determines if this alert level should be shown during focus mode.
     * <p>
     * Only CRITICAL alerts bypass focus mode restrictions to ensure
     * important events aren't missed during deep work sessions.
     *
     * @return True if alert should bypass focus mode
     */
    public boolean bypassesFocusMode() {
        return this == CRITICAL;
    }

    /**
     * Gets the Android notification importance level for this alert.
     * <p>
     * Maps our alert levels to Android's NotificationManager importance
     * constants for proper system integration.
     *
     * @return Android notification importance constant
     */
    public int getAndroidImportance() {
        switch (this) {
            case CRITICAL:
                // IMPORTANCE_HIGH - Makes sound and appears on screen
                return 4;
            case TRANSITION:
                // IMPORTANCE_DEFAULT - Makes sound but doesn't pop on screen
                return 3;
            case REFERENCE:
                // IMPORTANCE_LOW - No sound or visual interruption
                return 2;
            default:
                return 3;
        }
    }
}