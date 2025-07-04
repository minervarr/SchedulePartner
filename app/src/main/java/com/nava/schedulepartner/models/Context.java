package com.nava.schedulepartner.models;

/**
 * Represents the physical or situational context where activities take place.
 * <p>
 * Context is the "WHERE" component of the Context+Activity system. Different
 * contexts have unique constraints and opportunities that affect scheduling.
 * For example, studying at home allows for more flexible break times than
 * studying at a library, where silence and minimal movement are expected.
 * <p>
 * Each context influences:
 * - Available activities (can't exercise at library)
 * - Notification behavior (auto-mute at library/university)
 * - Schedule timing (longer breaks at home vs office)
 * - Audio settings (quieter or muted in public spaces)
 *
 * @author Nava
 * @version 1.0
 * @since API Level 30
 */
public enum Context {
    /**
     * Home context - Most flexible environment.
     * <p>
     * Characteristics:
     * - Full control over environment (lighting, temperature, noise)
     * - Access to all amenities (kitchen, bathroom, rest areas)
     * - Can play audio alerts at normal volume
     * - Longer breaks possible without social pressure
     * - More relaxed dress code allows for comfortable clothing
     * <p>
     * Ideal for: Deep study, creative work, rest, exercise
     */
    HOME("home", true, false, 1.0f),

    /**
     * University/School context - Academic environment.
     * <p>
     * Characteristics:
     * - Structured time blocks (class schedules)
     * - Limited break flexibility (between classes)
     * - Quiet zones require muted or low audio
     * - Access to academic resources (library, labs)
     * - Social environment may introduce distractions
     * <p>
     * Ideal for: Attending classes, group study, research
     */
    UNIVERSITY("university", false, true, 0.3f),

    /**
     * Office context - Professional work environment.
     * <p>
     * Characteristics:
     * - Fixed working hours with some flexibility
     * - Professional behavior expectations
     * - Meetings and collaborative work
     * - Usually has designated break areas
     * - Audio should be minimal or use headphones
     * <p>
     * Ideal for: Focused work, meetings, professional development
     */
    OFFICE("office", false, false, 0.5f),

    /**
     * Library context - Quiet study environment.
     * <p>
     * Characteristics:
     * - Strict silence requirements (audio always muted)
     * - Minimal movement to avoid disturbing others
     * - Ideal for deep focus without distractions
     * - Limited break options (must leave area)
     * - Access to reference materials
     * <p>
     * Ideal for: Deep study, research, reading
     */
    LIBRARY("library", false, true, 0.0f),

    /**
     * Gym/Fitness context - Physical activity environment.
     * <p>
     * Characteristics:
     * - High energy environment
     * - Flexible timing between exercises
     * - Audio can be louder (ambient noise)
     * - Hydration and rest breaks essential
     * - Different rules for different areas (weights vs cardio)
     * <p>
     * Ideal for: Exercise, physical training, active recovery
     */
    GYM("gym", true, false, 0.8f),

    /**
     * Travel context - Mobile or transit environment.
     * <p>
     * Characteristics:
     * - Unpredictable conditions
     * - Limited control over environment
     * - May have connectivity issues
     * - Requires adaptive scheduling
     * - Audio depends on transport type (car vs public)
     * <p>
     * Ideal for: Light work, reading, planning, rest
     */
    TRAVEL("travel", true, false, 0.6f);

    /**
     * Identifier used for template file naming.
     * <p>
     * This string is used to construct template filenames like
     * "Study@Home.csv" where "home" is the identifier.
     */
    private final String identifier;

    /**
     * Whether audio alerts are allowed by default in this context.
     * <p>
     * Can be overridden by user preference, but provides sensible
     * defaults for each environment type.
     */
    private final boolean allowsAudio;

    /**
     * Whether to automatically enable Do Not Disturb in this context.
     * <p>
     * Contexts like LIBRARY and UNIVERSITY benefit from automatic
     * DND to minimize disruptions in quiet environments.
     */
    private final boolean autoEnableDND;

    /**
     * Default audio volume multiplier for this context (0.0 to 1.0).
     * <p>
     * Adjusts system audio volume to appropriate levels:
     * - 0.0 = Muted (Library)
     * - 0.3 = Very quiet (University)
     * - 0.5 = Medium (Office)
     * - 0.8 = Normal-loud (Gym)
     * - 1.0 = Full volume (Home)
     */
    private final float defaultVolumeMultiplier;

    /**
     * Constructor for Context enum values.
     *
     * @param identifier String identifier for file naming
     * @param allowsAudio Whether audio is allowed by default
     * @param autoEnableDND Whether to auto-enable Do Not Disturb
     * @param defaultVolumeMultiplier Volume adjustment factor (0.0-1.0)
     */
    Context(String identifier, boolean allowsAudio, boolean autoEnableDND,
            float defaultVolumeMultiplier) {
        this.identifier = identifier;
        this.allowsAudio = allowsAudio;
        this.autoEnableDND = autoEnableDND;
        this.defaultVolumeMultiplier = defaultVolumeMultiplier;
    }

    /**
     * Gets the identifier used in template filenames.
     *
     * @return The context identifier string
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Checks if audio alerts are allowed in this context.
     *
     * @return True if audio is allowed by default
     */
    public boolean allowsAudio() {
        return allowsAudio;
    }

    /**
     * Checks if Do Not Disturb should auto-enable in this context.
     *
     * @return True if DND should be automatically enabled
     */
    public boolean shouldAutoEnableDND() {
        return autoEnableDND;
    }

    /**
     * Gets the default volume multiplier for this context.
     *
     * @return Volume multiplier between 0.0 and 1.0
     */
    public float getDefaultVolumeMultiplier() {
        return defaultVolumeMultiplier;
    }

    /**
     * Finds a Context by its identifier string.
     * <p>
     * Used when loading templates or parsing user selections.
     * Case-insensitive matching for user convenience.
     *
     * @param identifier The context identifier to find
     * @return The matching Context, or null if not found
     */
    public static Context fromIdentifier(String identifier) {
        for (Context context : values()) {
            if (context.identifier.equalsIgnoreCase(identifier)) {
                return context;
            }
        }
        return null;
    }

    /**
     * Gets a user-friendly display name for this context.
     * <p>
     * Returns the identifier with first letter capitalized.
     * In a full implementation, this would use string resources
     * for proper internationalization.
     *
     * @return Display-friendly context name
     */
    public String getDisplayName() {
        return identifier.substring(0, 1).toUpperCase() +
                identifier.substring(1).toLowerCase();
    }
}