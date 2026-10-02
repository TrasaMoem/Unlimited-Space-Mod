package com.modscreating.unlimitedspace.core.worldgen.climate;

import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;

/**
 * Unified ClimateField abstraction (Stage 4).
 *
 * <p>Exposes continuous channels:
 * - temperature
 * - humidity
 * - precipitation
 * - wetness
 * - continentalness
 * - water proximity
 * - wind speed and direction
 * - lapse / elevation cooling
 */
public final class ClimateField {

    private final PlanetClimateProfile climateProfile;
    private final PlanetCharacter character;
    private final WindDirectionField windField;

    public ClimateField(PlanetClimateProfile climateProfile, PlanetCharacter character, WindDirectionField windField) {
        this.climateProfile = climateProfile;
        this.character = character;
        this.windField = windField;
    }

    public PlanetClimateProfile profile() {
        return climateProfile;
    }

    public PlanetCharacter character() {
        return character;
    }

    public WindDirectionField windField() {
        return windField;
    }

    /**
     * Continuous local temperature [0, 1] accounting for latitude, macro noise, and elevation lapse rate.
     */
    public double temperatureAt(int x, int z, double elevation01) {
        if (climateProfile == null) return character.profile().temperature01();
        return climateProfile.temperatureAt(x, z, elevation01);
    }

    /**
     * Continuous local humidity [0, 1].
     */
    public double humidityAt(int x, int z) {
        if (climateProfile == null) return character.profile().humidity();
        return climateProfile.humidityAt(x, z);
    }

    /**
     * Continuous precipitation [0, 1] derived from humidity and atmospheric density.
     */
    public double precipitationAt(int x, int z, double elevation01) {
        double hum = humidityAt(x, z);
        double temp = temperatureAt(x, z, elevation01);
        // Orograhic precipitation enhancement on mountain slopes
        double orographic = Math.max(0.0, elevation01 - 0.45) * 0.35;
        return Math.max(0.0, Math.min(1.0, hum * 0.8 + orographic + character.weights().wetWeight() * 0.2));
    }

    /**
     * Combined ecological wetness in [0, 1].
     */
    public double wetnessAt(int x, int z, double elevation01) {
        double precip = precipitationAt(x, z, elevation01);
        if (!character.waterPhase().allowsLiquid()) {
            return precip * 0.25; // Frozen/dry worlds have minimal liquid wetness
        }
        return Math.max(0.0, Math.min(1.0, precip * 0.7 + character.profile().waterAbundance() * 0.3));
    }
}
