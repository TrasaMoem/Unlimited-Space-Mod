package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic procedural sky/water/atmosphere visual profile for one moon (R12).
 *
 * <p>Mirror of {@link PlanetVisualProfile} for the moon level of the hierarchy.
 * Pure domain object: derives {@code skyColor / waterColor / fogColor / sunTint /
 * cloudColor} from the moon's own seed + properties. No Minecraft types.
 */
public record MoonSkyProfile(
        int skyColor,
        int waterColor,
        int fogColor,
        int sunTint,
        int cloudColor
) {

    public static MoonSkyProfile create(long moonSeed, MoonProperties m) {
        double temp = clamp01((m.temperature() - 90.0) / 710.0); // 0..1 cold→hot
        double humidity = Seeds.fraction(moonSeed, 99000L); // deterministic per-moon humidity
        double water = m.waterCoverage();
        double density = m.atmosphericDensity();
        boolean gas = m.surface() == PlanetSurface.GASEOUS;

        long skySeed = Seeds.derive(moonSeed, "us.moon.visual.sky");
        long waterSeed = Seeds.derive(moonSeed, "us.moon.visual.water");
        long fogSeed = Seeds.derive(moonSeed, "us.moon.visual.fog");
        long sunSeed = Seeds.derive(moonSeed, "us.moon.visual.sun");
        long cloudSeed = Seeds.derive(moonSeed, "us.moon.visual.cloud");

        // thin atmospheres yield near-space darkness; only habitable-ish moons get a tinted sky
        int skyR = (int) (10 + 140 * temp);
        int skyG = (int) (20 + 130 * Math.pow(temp, 1.3));
        int skyB = (int) (50 + 160 * (1.0 - temp));
        if (density < 0.15) {
            skyR = (int) (skyR * 0.25);
            skyG = (int) (skyG * 0.25);
            skyB = (int) (skyB * 0.35);
        }
        if (gas) skyB = (int) (skyB * 0.7);
        int sky = argb(255, skyR, skyG, skyB);

        // water follows coverage + temperature, cooler = deeper blue
        double waterT = gas ? 0.2 : (0.25 + 0.45 * temp * humidity + 0.3 * water);
        int waterColor = argb(255,
                (int) (15 + 70 * waterT),
                (int) (50 + 135 * (1.0 - waterT * 0.7)),
                (int) (110 + 140 * waterT));

        // fog reflects density
        double fogT = 0.1 + 0.55 * density + 0.25 * temp;
        int fog = argb((int) (160 + 95 * density),
                (int) (210 * fogT), (int) (205 * fogT), (int) (225 * (1.0 - fogT * 0.3)));

        // sun tint follows temperature (cold → blue-white, hot → yellow), like planets
        int sun = argb(255,
                (int) (195 + 60 * temp),
                (int) (195 + 60 * Math.pow(temp, 0.8)),
                (int) (215 + 40 * (1.0 - temp)));

        int cloud = argb((int) (215 - 40 * density),
                (int) (195 + 55 * temp),
                (int) (195 + 55 * Math.pow(temp, 0.8)),
                (int) (215 + 40 * (1.0 - temp)));

        // deterministic seed noise so moons differ
        int skyNoise = (int) ((Seeds.fraction(skySeed, 99001L) - 0.5) * 24);
        int waterNoise = (int) ((Seeds.fraction(waterSeed, 99002L) - 0.5) * 16);
        int fogNoise = (int) ((Seeds.fraction(fogSeed, 99003L) - 0.5) * 12);
        sky = tint(sky, skyNoise);
        waterColor = tint(waterColor, waterNoise);
        fog = tint(fog, fogNoise);

        cloud = tint(cloud, (int) ((Seeds.fraction(cloudSeed, 99004L) - 0.5) * 14));

        return new MoonSkyProfile(sky, waterColor, fog, sun, cloud);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int argb(int a, int r, int g, int b) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int tint(int argb, int noise) {
        int r = clamp8(((argb >> 16) & 0xFF) + noise);
        int g = clamp8(((argb >> 8) & 0xFF) + noise / 2);
        int b = clamp8((argb & 0xFF) + noise / 3);
        return argb(255, r, g, b);
    }
}