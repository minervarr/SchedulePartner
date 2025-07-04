package com.nava.schedulepartner.models;

/**
 * Represents the type of activity or task the user wants to accomplish.
 * <p>
 * Activity is the "WHAT" component of the Context+Activity system. Each activity
 * has different requirements for focus, duration, and energy management. The same
 * activity performed in different contexts will have different optimal schedules.
 * <p>
 * Activities determine:
 * - Work/rest ratios (Study needs more breaks than Rest)
 * - Focus requirements (Deep work vs casual reading)
 * - Energy management (High-intensity work in morning)
 * - Notification filtering (Fewer interruptions during Study)
 * - Duration limits (Maximum continuous work time)
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public enum Activity {
    /**
     * Study activity - Deep learning and academic work.
     * <p>
     * Characteristics:
     * - Requires high mental energy and focus
     * - Benefits from Pomodoro-style time blocks
     * - Needs regular breaks to maintain retention
     * - Best performed during peak mental hours
     * - Minimal interruptions for optimal learning
     * <p>
     * Typical schedule elements:
     * - 45-90 minute focused blocks
     * - 10-15 minute breaks between blocks
     * - Subject rotation to prevent fatigue
     * - Review periods for retention
     */
    STUDY("study", 90, 15, true, 4),

    /**
     * Work activity - Professional or productive tasks.
     * <p>
     * Characteristics:
     * - Mix of deep and shallow work
     * - May include meetings and collaboration
     * - Flexible break timing based on task flow
     * - Email/communication checks between blocks
     * - Energy management throughout workday
     * <p>
     * Typical schedule elements:
     * - 60-120 minute work blocks
     * - 5-10 minute micro-breaks
     * - Longer lunch break mid-day
     * - End-of-day review and planning
     */
    WORK("work", 120, 10, true, 6),

    /**
     * Rest activity - Recovery and relaxation.
     * <p>
     * Characteristics:
     * - Low mental and physical demands
     * - Flexible timing with minimal structure
     * - Focus on recovery and enjoyment
     * - No productivity pressure
     * - Prepares mind/body for next activity
     * <p>
     * Typical schedule elements:
     * - Loose time suggestions rather than strict blocks
     * - Meal reminders remain critical
     * - Gentle transitions between activities
     * - Sleep hygiene reminders in evening
     */
    REST("rest", 0, 0, false, 0),

    /**
     * Exercise activity - Physical training and fitness.
     * <p>
     * Characteristics:
     * - High physical energy expenditure
     * - Structured workout phases (warm-up, main, cool-down)
     * - Hydration reminders essential
     * - Rest periods between sets/exercises
     * - Post-workout nutrition timing important
     * <p>
     * Typical schedule elements:
     * - 5-10 minute warm-up period
     * - 30-90 minute main workout
     * - Rest intervals based on intensity
     * - Cool-down and stretching
     * - Post-workout meal timing
     */
    EXERCISE("exercise", 60, 5, false, 2),

    /**
     * Research activity - Deep investigation and analysis.
     * <p>
     * Characteristics:
     * - Requires sustained deep focus
     * - Longer uninterrupted blocks than regular study
     * - Information synthesis and note-taking
     * - May involve multiple resources/tools
     * - Benefits from flow state maintenance
     * <p>
     * Typical schedule elements:
     * - 2-3 hour deep work blocks
     * - 20-30 minute breaks between blocks
     * - Dedicated note consolidation time
     * - Reference material organization
     * - Findings review at end of session
     */
    RESEARCH("research", 180, 30, true, 3);

    /**
     * Identifier used for template file naming.
     * <p>
     * Combined with Context identifier to create template
     * filenames like "Study@Home.csv".
     */
    private final String identifier;

    /**
     * Default work block duration in minutes.
     * <p>
     * Suggested length for focused work periods before breaks.
     * Can be customized in individual templates. Zero indicates
     * no structured blocks (like REST activity).
     */
    private final int defaultBlockMinutes;

    /**
     * Default break duration in minutes.
     * <p>
     * Suggested break length between work blocks. Should be
     * proportional to block intensity and duration.
     */
    private final int defaultBreakMinutes;

    /**
     * Whether this activity requires focus mode.
     * <p>
     * Activities requiring deep concentration should minimize
     * distractions through DND and notification filtering.
     */
    private final boolean requiresFocus;

    /**
     * Maximum recommended hours per day for this activity.
     * <p>
     * Helps prevent burnout and ensures balanced daily schedules.
     * Zero indicates no limit (like REST activity).
     */
    private final int maxDailyHours;

    /**
     * Constructor for Activity enum values.
     *
     * @param identifier String identifier for file naming
     * @param defaultBlockMinutes Default work block duration
     * @param defaultBreakMinutes Default break duration
     * @param requiresFocus Whether focus mode is recommended
     * @param maxDailyHours Maximum recommended daily hours
     */
    Activity(String identifier, int defaultBlockMinutes, int defaultBreakMinutes,
             boolean requiresFocus, int maxDailyHours) {
        this.identifier = identifier;
        this.defaultBlockMinutes = defaultBlockMinutes;
        this.defaultBreakMinutes = defaultBreakMinutes;
        this.requiresFocus = requiresFocus;
        this.maxDailyHours = maxDailyHours;
    }

    /**
     * Gets the identifier used in template filenames.
     *
     * @return The activity identifier string
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Gets the default work block duration for this activity.
     *
     * @return Duration in minutes, or 0 if no structured blocks
     */
    public int getDefaultBlockMinutes() {
        return defaultBlockMinutes;
    }

    /**
     * Gets the default break duration for this activity.
     *
     * @return Break duration in minutes
     */
    public int getDefaultBreakMinutes() {
        return defaultBreakMinutes;
    }

    /**
     * Checks if this activity requires focus mode.
     *
     * @return True if focus mode should be enabled
     */
    public boolean requiresFocus() {
        return requiresFocus;
    }

    /**
     * Gets the maximum recommended daily hours.
     *
     * @return Maximum hours, or 0 if unlimited
     */
    public int getMaxDailyHours() {
        return maxDailyHours;
    }

    /**
     * Calculates optimal break frequency for this activity.
     * <p>
     * Returns how many work blocks should occur before a longer
     * break is recommended. Based on activity intensity and duration.
     *
     * @return Number of blocks before long break
     */
    public int getBlocksBeforeLongBreak() {
        if (defaultBlockMinutes == 0) return 0;

        // More intense activities need long breaks sooner
        switch (this) {
            case STUDY:
                return 3;  // Long break every 3 study blocks
            case RESEARCH:
                return 2;  // Long break every 2 research blocks
            case WORK:
                return 4;  // Long break every 4 work blocks
            case EXERCISE:
                return 1;  // Each exercise session is self-contained
            default:
                return 0;
        }
    }

    /**
     * Finds an Activity by its identifier string.
     * <p>
     * Used when loading templates or parsing user selections.
     * Case-insensitive matching for user convenience.
     *
     * @param identifier The activity identifier to find
     * @return The matching Activity, or null if not found
     */
    public static Activity fromIdentifier(String identifier) {
        for (Activity activity : values()) {
            if (activity.identifier.equalsIgnoreCase(identifier)) {
                return activity;
            }
        }
        return null;
    }

    /**
     * Gets a user-friendly display name for this activity.
     * <p>
     * Returns the identifier with first letter capitalized.
     * In a full implementation, this would use string resources
     * for proper internationalization.
     *
     * @return Display-friendly activity name
     */
    public String getDisplayName() {
        return identifier.substring(0, 1).toUpperCase() +
                identifier.substring(1).toLowerCase();
    }
}