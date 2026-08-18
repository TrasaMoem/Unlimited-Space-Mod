package com.modscreating.unlimitedspace.client;

/**
 * Client-side visualisation of a celestial body visible from the current orbit (R12.3).
 *
 * <p>Every other planet / moon of the destination star system is rendered as a distant,
 * square-pixel body in the sky, with an apparent size that shrinks the further it is from
 * the player. The body actually being orbited is drawn separately (large, below the camera)
 * by {@link com.modscreating.unlimitedspace.client.graphics.PlanetSphereRenderer}, so it is
 * deliberately absent from this list.
 *
 * <p>Pure data (no Minecraft types) — directly unit-testable.
 *
 * @param bodyCode       stable body code (planet or moon) for cache/identity keys
 * @param surfaceColorArgb solid-surface colour of the body
 * @param waterColorArgb water colour (for oceans on the body)
 * @param waterBlend     volume of water on the surface (0..1)
 * @param iceBlend       how ice-capped the body is (0..1)
 * @param radiusProfile  normalised size of the body (planet/moon scale)
 * @param azimuthDeg     fixed sky azimuth of the body
 * @param elevationDeg   fixed sky elevation of the body
 * @param apparentSize   rendered half-size of the body in blocks (distance-scaled)
 */
public record SiblingBody(
        String bodyCode,
        int surfaceColorArgb,
        int waterColorArgb,
        float waterBlend,
        float iceBlend,
        float radiusProfile,
        float azimuthDeg,
        float elevationDeg,
        float apparentSize) {
}