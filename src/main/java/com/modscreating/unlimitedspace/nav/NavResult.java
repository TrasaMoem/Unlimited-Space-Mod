package com.modscreating.unlimitedspace.nav;

import com.modscreating.unlimitedspace.core.nav.ResolvedDestination;
import net.minecraft.resources.ResourceLocation;

/**
 * Full result of an R13 admin-navigation step: domain resolution, resource-location mapping
 * and playability classification. Immutable. This is the Minecraft/CS adapter-layer result;
 * the pure-domain {@link ResolvedDestination} stays inside for diagnostics.
 */
public final class NavResult {

    private final NavStatus status;
    private final String message;
    private final ResolvedDestination resolved;
    private final ResourceLocation resourceLocation;

    private NavResult(NavStatus status, String message, ResolvedDestination resolved,
                      ResourceLocation resourceLocation) {
        this.status = status;
        this.message = message;
        this.resolved = resolved;
        this.resourceLocation = resourceLocation;
    }

    public static NavResult ready(ResolvedDestination resolved, ResourceLocation resourceLocation) {
        return new NavResult(NavStatus.OK_READY, null, resolved, resourceLocation);
    }

    public static NavResult fail(NavStatus status, String message) {
        return new NavResult(status, message, null, null);
    }

    public static NavResult resolved(NavStatus status, String message, ResolvedDestination resolved,
                                     ResourceLocation resourceLocation) {
        return new NavResult(status, message, resolved, resourceLocation);
    }

    public NavStatus status() {
        return status;
    }

    public boolean ok() {
        return status.ok();
    }

    public boolean isError() {
        return !status.ok();
    }

    /**
     * User-facing message, or {@code null} when {@link #ok()}. Falls back to the status's
     * static message when an explicit one was not supplied.
     */
    public String message() {
        if (message != null) {
            return message;
        }
        return status.message();
    }

    public ResolvedDestination resolved() {
        return resolved;
    }

    /** The mapped resource location, non-null only for non-star playable-mapped destinations. */
    public ResourceLocation resourceLocation() {
        return resourceLocation;
    }
}