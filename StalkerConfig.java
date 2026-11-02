package com.stalker;

/**
 * Tweak these and rebuild. All "dread" values are in seconds of accumulated fear.
 * Dread rises while you are underground, in the dark, or out at night, and
 * falls in bright daylight, while sleeping, or when other players are nearby.
 */
public final class StalkerConfig {
    /** Dread needed before the first distant sighting. */
    public static final int SIGHTING_DREAD = 45;
    /** Stage 1: it follows you and hides behind cover. */
    public static final int STAGE1_DREAD = 150;
    /** Stage 2: it creeps closer whenever you look away. */
    public static final int STAGE2_DREAD = 330;
    /** Stage 3: the hunt. It wants you dead. */
    public static final int STAGE3_DREAD = 540;
    /** How long the hunt lasts before it loses interest (seconds). */
    public static final int HUNT_SECONDS = 100;
    /** Chase speed multiplier (1.0 = base movement speed attribute). */
    public static final double HUNT_SPEED = 1.25;

    /** Torches near the Stalker get snuffed out (stage 2+). */
    public static final boolean EXTINGUISH_TORCHES = true;
    /** Vanilla darkness pulse when it is close (stage 2+). */
    public static final boolean DARKNESS_PULSE = true;

    private StalkerConfig() {}
}
