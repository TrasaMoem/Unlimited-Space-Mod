package com.modscreating.unlimitedspace.client.nav;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * R15.3: ~30 deterministic MINIATURE SYSTEM ICONS for the galaxy map.
 *
 * <p>Instead of drawing every system as a plain star dot, each system renders as a tiny
 * stylized "system portrait": central star + orbiting planet specks (and optionally a
 * small asteroid scatter). The variants are FIXED (built once from a hard-coded seed) so
 * nothing is generated per world; a system picks its variant deterministically from its
 * own stable system seed, so the same system always looks the same.
 *
 * <p>Pure client rendering helper; no world generation, no CS coupling.
 */
public final class SystemIconVariants {

    /** One miniature layout. */
    public record Mini(int starColor, int planetCount, int moonBodies,
                       boolean asteroids, float[] angles, float[] radii, int[] tints) {}

    private static final int VARIANT_COUNT = 30;
    private static final Mini[] VARIANTS = build();

    private SystemIconVariants() {}

    private static Mini[] build() {
        Random r = new Random(0x5EEDC0DE); // fixed - identical on every launch
        Mini[] out = new Mini[VARIANT_COUNT];
        int[] palette = {
                0xFFF6D8A8, // warm K-star
                0xFFCFE4FF, // hot blue-white
                0xFFFFD9B0, // orange
                0xFFFFF3C4, // yellow
                0xFFE8B7FF, // exotic violet-tinted
                0xFFBFE9C9  // pale teal dwarf
        };
        for (int v = 0; v < VARIANT_COUNT; v++) {
            int planets = 1 + r.nextInt(5);              // 1..5 planets
            boolean asteroids = r.nextBoolean();
            int moons = planets > 1 && r.nextBoolean() ? 1 + r.nextInt(2) : 0;
            float[] angles = new float[planets];
            float[] radii = new float[planets];
            int[] tints = new int[planets];
            for (int p = 0; p < planets; p++) {
                angles[p] = (float) (-Math.PI / 2 + p * (Math.PI * 2 / planets)
                        + (r.nextFloat() - 0.5f) * 0.8f);
                radii[p] = 0.55f + 0.45f * ((p + 1f) / planets) + r.nextFloat() * 0.15f;
                tints[p] = switch (r.nextInt(4)) {
                    case 0 -> 0xFF9FD8FF;
                    case 1 -> 0xFFFFC89B;
                    case 2 -> 0xFFB9E8A5;
                    default -> 0xFFD8CFFF;
                };
            }
            out[v] = new Mini(palette[r.nextInt(palette.length)],
                    planets, moons, asteroids, angles, radii, tints);
        }
        return out;
    }

    /**
     * Deterministic variant for a system. {@code systemSeed} is the stable per-system
     * seed already used everywhere else (same system -> same icon forever).
     */
    public static Mini pick(long systemSeed) {
        return VARIANTS[Math.floorMod(Long.hashCode(systemSeed), VARIANT_COUNT)];
    }

    /**
     * Draw the miniature centered at (cx, cy). {@code unit} is the pixel size of one
     * orbit step; the whole icon spans roughly [-unit*1.6, +unit*1.6].
     */
    public static void draw(GuiGraphics g, long systemSeed, int cx, int cy, float unit,
                            boolean selected) {
        Mini m = pick(systemSeed);

        // central star glow + core
        int glowR = Math.max(2, (int) (unit * 0.7f));
        int glow = (selected ? 0x50 : 0x30) << 24 | (m.starColor & 0x00FFFFFF);
        g.fill(cx - glowR, cy - glowR, cx + glowR, cy + glowR, glow);
        int coreR = Math.max(1, (int) (unit * 0.32f));
        g.fill(cx - coreR, cy - coreR, cx + coreR, cy + coreR, m.starColor);
        int whiteR = Math.max(0, coreR - 1);
        if (whiteR > 0) {
            g.fill(cx - whiteR, cy - whiteR, cx + whiteR, cy + whiteR, 0xFFFFFFFF);
        }

        // asteroid scatter (drawn first so planets sit on top)
        if (m.asteroids && unit > 1.6f) {
            for (int i = 0; i < 4; i++) {
                float ang = 1.1f + i * 1.9f;
                float rr = unit * (1.25f + 0.18f * ((i % 2 == 0) ? 1f : -1f));
                int ax = (int) Math.round(cx + rr * Math.cos(ang));
                int ay = (int) Math.round(cy + rr * Math.sin(ang));
                g.fill(ax, ay, ax + 1, ay + 1, 0xFF9A8F80);
            }
        }

        // planets on their orbits
        for (int p = 0; p < m.planetCount(); p++) {
            float rr = unit * 1.05f * m.radii()[p];
            int px = (int) Math.round(cx + rr * Math.cos(m.angles()[p]));
            int py = (int) Math.round(cy + rr * Math.sin(m.angles()[p]));
            int pr = Math.max(1, (int) (unit * 0.22f));
            // faint orbit ring pixel hint (single outline square, very dim)
            if (unit > 2.2f) {
                int ro = (int) rr;
                g.renderOutline(cx - ro, cy - ro, ro * 2, ro * 2, 0x184FD8FF);
            }
            g.fill(px - pr, py - pr, px + pr, py + pr, m.tints()[p]);
            // optional moon speck hugging its planet
            if (m.moonBodies() > 0 && p % 2 == 0 && unit > 2.6f) {
                g.fill(px + pr + 1, py, px + pr + 2, py + 1, 0xFFDDDDDD);
            }
        }
    }
}
