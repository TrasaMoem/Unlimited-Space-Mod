package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic procedural visual profile for one planet (R8).
 *
 * <p>Derives planet-specific {@code skyColor / waterColor / fogColor / sunTint}
 * from the planet seed + properties + atmosphere. No Minecraft client classes
 * are referenced here — these are pure {@code int ARGB} values resolved in the
 * Minecraft adapter layer later.
 *
 * <p>Styling rules (climate-driven, not random RGB):
 * <ul>
 *   <li>ice/cold worlds → cool blue sky, dark/cold water;</li>
 *   <li>hot/dry worlds → warm amber sky, hazy fog;</li>
 *   <li>dense atmosphere → stronger fog tint;</li>
 *   <li>ocean worlds → richer water tint.</li>
 * </ul>
 *
 * <p>Pure domain object, deterministic, stable across restarts.
 */
public record PlanetVisualProfile(
        int skyColor,
        int waterColor,
        int fogColor,
        int sunTint,
        int cloudColor
) {

    public static PlanetVisualProfile create(long planetSeed, PlanetProperties p) {
        double temp = clamp01(p.temperature());   // 0..1, cold→hot
        double humidity = p.humidity();
        double water = p.waterCoverage();
        double density = p.atmosphericDensity();
        boolean gas = p.surface() == PlanetSurface.GASEOUS;

        long skySeed = Seeds.derive(planetSeed, "us.visual.sky");
        long waterSeed = Seeds.derive(planetSeed, "us.visual.water");
        long fogSeed = Seeds.derive(planetSeed, "us.visual.fog");
        long sunSeed = Seeds.derive(planetSeed, "us.visual.sun");
        long cloudSeed = Seeds.derive(planetSeed, "us.visual.cloud");

        // --- sky color: temperature axis ---
        int skyR = (int) (30 + 160 * temp);                 // cold→dark blue, hot→orange
        int skyG = (int) (60 + 140 * Math.pow(temp, 1.4));  // green rises with warmth
        int skyB = (int) (120 + 135 * (1.0 - temp));        // cold→cyan, hot→magenta
        if (gas) { skyB = (int) (skyB * 0.7); }
        int sky = argb(255, skyR, skyG, skyB);

        // --- water color: tinted by temperature + moisture ---
        double waterT = gas ? 0.2 : (0.25 + 0.5 * temp * humidity + 0.25 * water);
        int waterR = (int) (20 + 80 * waterT);
        int waterG = (int) (60 + 140 * (1.0 - waterT * 0.7));
        int waterB = (int) (120 + 135 * waterT);
        int waterColor = argb(255, waterR, waterG, waterB);

        // --- fog color: denser atmosphere → more haze ---
        double fogT = 0.15 + 0.6 * density + 0.25 * temp;
        int fog = argb((int) (170 + 85 * density),
                (int) (220 * fogT), (int) (210 * fogT), (int) (230 * (1.0 - fogT * 0.3)));

        // --- sun tint: cold→blue-white, hot→yellow ---
        int sunR = (int) (200 + 55 * temp);
        int sunG = (int) (200 + 55 * Math.pow(temp, 0.8));
        int sunB = (int) (220 + 35 * (1.0 - temp));
        int sun = argb(255, sunR, sunG, sunB);

        // --- cloud tint ---
        int cloudR = (int) (200 + 55 * temp);
        int cloudG = (int) (200 + 55 * Math.pow(temp, 0.8));
        int cloudB = (int) (220 + 35 * (1.0 - temp));
        int cloud = argb((int) (230 - 30 * density), cloudR, cloudG, cloudB);

        // Add deterministic, minimal per-planet noise so visuals differ by seed.
        int skyNoise = (int) ((Seeds.fraction(skySeed, 88001L) - 0.5) * 30);
        int waterNoise = (int) ((Seeds.fraction(waterSeed, 88002L) - 0.5) * 20);
        int fogNoise = (int) ((Seeds.fraction(fogSeed, 88003L) - 0.5) * 15);
        sky = tint(sky, skyNoise);
        waterColor = tint(waterColor, waterNoise);
        fog = tint(fog, fogNoise);

        return new PlanetVisualProfile(sky, waterColor, fog, sun, cloud);
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static int argb(int a, int r, int g, int b) {
        a = clamp8(a); r = clamp8(r); g = clamp8(g); b = clamp8(b);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static int tint(int argb, int noise) {
        int a = (argb >> 24) & 0xff;
        int r = clamp8(((argb >> 16) & 0xff) + noise);
        int g = clamp8(((argb >> 8) & 0xff) + noise / 2);
        int b = clamp8((argb & 0xff) + noise / 3);
        return argb(a, r, g, b);
    }
}

