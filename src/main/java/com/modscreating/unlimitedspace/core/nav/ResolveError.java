package com.modscreating.unlimitedspace.core.nav;

/**
 * Failure status produced by the {@link DestinationResolver}. Explicit errors only — the
 * resolver never silently clamps invalid input. A value of {@link #NONE} means the request
 * resolved successfully.
 */
public enum ResolveError {

    /** Resolution succeeded; no error. */
    NONE(""),

    /** System index does not reference a valid, generated system. */
    INVALID_SYSTEM("Invalid System"),

    /** Object index is outside the system's canonical object list. */
    INVALID_OBJECT("Invalid Object"),

    /** Destination index is invalid for the resolved object kind. */
    INVALID_DESTINATION("Invalid Destination");

    private final String message;

    ResolveError(String message) {
        this.message = message;
    }

    /** Predefined, explicit error message for command/GUI reporting. */
    public String message() {
        return message;
    }

    public boolean ok() {
        return this == NONE;
    }
}