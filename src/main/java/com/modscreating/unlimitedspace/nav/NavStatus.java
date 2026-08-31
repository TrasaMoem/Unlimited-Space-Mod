package com.modscreating.unlimitedspace.nav;

/**
 * Outcome status of an R13 admin-navigation resolution / playability check / travel attempt.
 * Messages are explicit and user-facing; nothing is silently clamped.
 */
public enum NavStatus {

    /** Fully resolved and playable/registered; ready to feed into the CS travel bridge. */
    OK_READY(""),

    /** The pure-domain resolver rejected the request (invalid system/object/destination). */
    RESOLVE_ERROR(null),

        /** System exists in the domain but is outside the configured finite navigation scope. */
    OUT_OF_SCOPE("System exists in domain, but is not available in current navigation scope."),

    /** The requested system index does not resolve to a real star system in the procedural galaxy. */
    SYSTEM_NOT_FOUND("System does not exist in the procedural galaxy."),

    /** Star destinations are not currently supported as playable worlds by this architecture. */
    STAR_NOT_SUPPORTED(
            "Star destinations exist in the procedural galaxy, but are not registered as playable Minecraft worlds."),

    /** Domain body exists but no Minecraft world/destination is bound to it. */
    NOT_PLAYABLE(
            "Destination exists in procedural Galaxy, but is not currently registered as a playable Minecraft world."),

    /** The Creating Space rocket_accessible_dimension registry does not contain this destination. */
    NOT_REGISTERED_IN_CS("Destination not registered in Creating Space"),

    /** No LevelStem is registered for this destination. */
    NO_LEVEL_STEM("Destination has no LevelStem"),

    /** No rocket is currently associated with the player to attach the travel to. */
    NO_ROCKET("No rocket found for the player"),

    /** The Creating Space travel pipeline was successfully handed the destination. */
    TRAVEL_STARTED(""),

    /** The Creating Space public bridge could not be driven for the current state. */
    TRAVEL_BLOCKED("Creating Space travel could not be initiated for this destination."),

    /** R30: the target system lies beyond the hard flight range (1600 ly). */
    OUT_OF_RANGE("Destination system is beyond the 1600 ly flight range.");

    private final String message;

    NavStatus(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public boolean ok() {
        return this == OK_READY || this == TRAVEL_STARTED;
    }
}