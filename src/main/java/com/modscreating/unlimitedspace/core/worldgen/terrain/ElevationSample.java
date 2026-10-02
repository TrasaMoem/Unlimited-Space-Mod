package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * Multi-layer elevation sample produced by {@link ElevationField}.
 *
 * <p>Carries the composed surface height together with the distinct contributions
 * across scales: Level 0 (continental), Level 1 (tectonic/mountains/valleys),
 * Level 2 (dunes/landforms/calderas), Level 3 (hydrology/carve/erosion), Level 4 (local detail).
 */
public record ElevationSample(
        int height,
        double continental,
        double tectonic,
        double landform,
        double hydrology,
        double localDetail,
        double mountainEnvelope,
        double foothillEnvelope,
        double valleyEnvelope,
        double basinEnvelope,
        double duneRelief,
        double glacialRelief,
        double volcanicRelief,
        double elevation01
) {
}
