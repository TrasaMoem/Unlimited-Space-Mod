package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.worldgen.geography.GeographyMetrics;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroGeography;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.geography.ProvinceArchetype;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyPalette;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVisualRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialZoneMap;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetBiomeIdentity;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialRoleSelector;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategorySelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * R22 HEADLESS MAP PREVIEW (dev/test only - NOT part of the production runtime).
 *
 * <p>Renders 5 maps per planet (biome / height / temperature / humidity / material zones)
 * as simple PNGs so the large-scale geography, region borders and palette coherence can be
 * judged BY EYE without launching Minecraft. Deterministic: same inputs -> same images.
 *
 * <p>PHASE 9: each preview folder also carries {@code facts.txt} - the canonical thermal /
 * water-phase facts produced by the very models the runtime worldgen uses.
 */
public final class MapPreview {

    private MapPreview() {}

    /** Renders one planet into {@code outDir/<name>/*.png} and returns the output dir. */
    public static File render(String name, PlanetPhysicalProfile profile,
                              PlanetReliefProfile relief, long seed,
                              int gridSize, int step, File outDir) throws IOException {
        return render(name, profile, relief, null, seed, gridSize, step, outDir);
    }

    /**
     * PHASE 9 overload: the same preview, but carrying the planet's REAL stellar thermal context so
     * the fact sheet quotes the canonical values instead of "no stellar context".
     */
    public static File render(String name, PlanetPhysicalProfile profile,
                              PlanetReliefProfile relief, PlanetThermal thermal, long seed,
                              int gridSize, int step, File outDir) throws IOException {
        return render(name, profile, relief, thermal, null, seed, gridSize, step, outDir);
    }

    /**
     * ACT 1 overload: the preview of a REAL planet of the galaxy (canonical properties + thermal
     * context) with the COMPLETE stellar thermal chain written into {@code facts.txt} - star type,
     * star luminosity, orbital distance, eccentricity, received flux, equilibrium temperature,
     * greenhouse multiplier, internal contribution, final surface temperature and thermal class.
     * The relief profile is derived exactly like the runtime geology profile does.
     */
    public static File render(String name, StarSystem system, Planet planet,
                              int gridSize, int step, File outDir) throws IOException {
        PlanetProperties props = planet.properties();
        long planetSeed = props.seed().value();
        PlanetPhysicalProfile profile =
                PlanetPhysicalProfileFactory.create(planetSeed, props);
        PlanetReliefProfile relief = PlanetReliefProfile.create(planetSeed, profile);
        java.util.List<String> facts = new java.util.ArrayList<>(
                thermalChainFacts(system, props));
        facts.addAll(habitabilityFacts(system, planet));
        return render(name, profile, relief, props.thermal(),
                facts, planetSeed, gridSize, step, outDir);
    }

    /**
     * ACT 1: the star &rarr; distance &rarr; flux &rarr; T_eq &rarr; greenhouse &rarr; internal
     * &rarr; surface chain of a REAL planet, formatted for the fact sheet. Every value is READ from
     * the canonical sources (the star list and {@link PlanetThermal}); nothing is re-derived here.
     */
    public static java.util.List<String> thermalChainFacts(StarSystem system,
                                                          PlanetProperties props) {
        PlanetThermal t = props.thermal();
        java.util.List<String> facts = new java.util.ArrayList<>();
        StringBuilder stars = new StringBuilder();
        double total = 0.0;
        for (int i = 0; i < system.stars().size(); i++) {
            Star star = system.star(i);
            total += Math.max(0.0, star.luminosity());
            if (i > 0) stars.append(" + ");
            stars.append(star.type()).append(' ')
                    .append(String.format(Locale.ROOT, "%.4g", star.luminosity())).append(" Lsun");
        }
        facts.add("ACT 1 STELLAR THERMAL CHAIN:");
        facts.add("  star type            : " + system.star().type().displayName());
        facts.add(String.format(Locale.ROOT,
                "  star luminosity      : %.4g Lsun %s",
                system.star().luminosity(),
                system.stars().size() > 1
                        ? "(system total " + String.format(Locale.ROOT, "%.4g", total) + " Lsun)"
                        : "(single star)"));
        facts.add("  stars                : " + stars);
        facts.add(String.format(Locale.ROOT, "  orbital distance     : %.3f AU (e %.3f)",
                t.orbitAU(), t.eccentricity()));
        facts.add(String.format(Locale.ROOT, "  received flux        : %.4gx solar (orbit-averaged)",
                t.stellarFlux()));
        facts.add("  equilibrium temp     : " + StellarThermalModel.temperatureText(t.equilibriumK()));
        facts.add(String.format(Locale.ROOT, "  greenhouse multiplier: x%.3f", t.greenhouse()));
        facts.add(String.format(Locale.ROOT, "  internal contribution: %+.1f K", t.internalK()));
        facts.add("  surface temperature  : "
                + StellarThermalModel.temperatureText(props.temperature()));
        facts.add("  thermal class        : " + t.thermalClass().displayName());
        return facts;
    }

    /**
     * ACT 2: the canonical habitability & life facts of one planet - system pattern, candidacy,
     * PHYSICAL habitability (validator verdict + rejection reasons), ACTUAL habitability, the
     * derived life state, the 20% mob lottery and the physical inputs (water phase, pressure,
     * gravity, radiation, atmosphere, surface/type). Every value is READ from the canonical
     * sources - nothing is re-derived here.
     */
    public static java.util.List<String> habitabilityFacts(StarSystem system, Planet planet) {
        PlanetProperties props = planet.properties();
        int orbit = planet.id().orbitIndex();
        com.modscreating.unlimitedspace.core.habitability.SystemHabitability.Result result =
                com.modscreating.unlimitedspace.core.habitability.SystemHabitability.of(system);
        com.modscreating.unlimitedspace.core.habitability.HabitabilityAnswer answer =
                com.modscreating.unlimitedspace.core.habitability.HabitabilityValidator.validate(
                        com.modscreating.unlimitedspace.core.habitability.HabitabilityProfile
                                .ofPlanet(props));
        boolean actual = result.isActuallyHabitable(orbit);
        com.modscreating.unlimitedspace.core.habitability.LifeState life =
                com.modscreating.unlimitedspace.core.habitability.LifeState.of(actual,
                        com.modscreating.unlimitedspace.core.habitability.MobEcologyProfile
                                .of(props.seed().value(), actual).mobsEnabled());
        PlanetPhysicalProfile physical = PlanetPhysicalProfileFactory.create(props.seed().value(), props);
        java.util.List<String> facts = new java.util.ArrayList<>();
        facts.add("ACT 2 HABITABILITY & LIFE:");
        facts.add("  system pattern       : " + result.pattern() + " (N=" + result.planetCount() + ")");
        facts.add("  candidate orbits     : " + result.candidateOrbitIndexes());
        facts.add("  candidate (this)     : " + result.isCandidate(orbit));
        facts.add("  physically habitable : " + answer.physicallyHabitable()
                + " [" + answer.describe() + "]");
        facts.add("  actually habitable   : " + actual);
        facts.add("  life enabled         : " + life.actualHabitable()
                + " vegetation=" + life.vegetationPermitted()
                + " structures=" + life.structureEligible());
        facts.add("  mobs enabled (20%)   : " + life.mobsEnabled()
                + (life.mobsEnabled()
                        ? " placeholder=" + com.modscreating.unlimitedspace.core.habitability
                                .MobEcologyProfile.PLACEHOLDER_SPECIES
                        : ""));
        facts.add("  water phase (global) : " + WaterPhaseModel.ofProperties(props.temperature(),
                props.atmosphere(), props.atmosphericDensity(),
                PlanetPhysicalProfileFactory.waterAbundance(props.waterCoverage(), props.humidity()))
                .displayName());
        facts.add(String.format(Locale.ROOT,
                "  pressure / gravity   : %s / %.2f g",
                physical.pressureClass(), props.gravity()));
        facts.add(String.format(Locale.ROOT,
                "  radiation / atmos    : %.2f / %s (%.0f%%)",
                physical.radiation(), props.atmosphere(), props.atmosphericDensity() * 100.0));
        facts.add("  surface / type       : " + props.surface() + " / " + props.type());
        return facts;
    }

    /**
     * ACT 2: the canonical habitability & worldgen identity facts of one MOON - parent actual
     * habitability, the moon's own 30% lottery, the moon's own physical verdict, the moon's own
     * temperature / water phase and the moon-specific worldgen identity (its OWN seeds).
     */
    public static java.util.List<String> moonHabitabilityFacts(long worldSeed, StarSystem system,
                                                              Planet planet, Moon moon) {
        MoonProperties mp = moon.properties();
        com.modscreating.unlimitedspace.core.habitability.SystemHabitability.Result result =
                com.modscreating.unlimitedspace.core.habitability.SystemHabitability.of(system);
        com.modscreating.unlimitedspace.core.habitability.MoonHabitability.MoonResult moonResult =
                com.modscreating.unlimitedspace.core.habitability.MoonHabitability.of(
                        result, planet.id(), moon);
        com.modscreating.unlimitedspace.core.worldgen.MoonWorldgenProfile worldgen =
                com.modscreating.unlimitedspace.core.worldgen.MoonWorldgenProfile
                        .view(moon.id(), worldSeed);
        java.util.List<String> facts = new java.util.ArrayList<>();
        facts.add("ACT 2 MOON HABITABILITY:");
        facts.add("  parent actually habitable : "
                + result.isActuallyHabitable(planet.id().orbitIndex()));
        facts.add("  moon candidate (30%)      : " + moonResult.lotterySelected());
        facts.add("  moon physically habitable : " + moonResult.physical().physicallyHabitable()
                + " [" + moonResult.physical().describe() + "]");
        facts.add("  moon actually habitable   : " + moonResult.actuallyHabitable());
        facts.add("  moon temperature          : "
                + StellarThermalModel.temperatureText(mp.temperature()));
        facts.add("  moon water phase          : " + WaterPhaseModel.ofProperties(mp.temperature(),
                mp.atmosphere(), mp.atmosphericDensity(), mp.waterCoverage()).displayName());
        facts.add("  moon mobs enabled         : " + moonResult.life().mobsEnabled());
        facts.add("  moon worldgen identity    : moonSeed=" + moon.seed().value()
                + " terrainSeed=" + worldgen.worldgen().terrainSeed()
                + " materialSeed=" + worldgen.worldgen().materialSeed());
        return facts;
    }

    /** ACT 1: same as above, with the extra fact lines appended to {@code facts.txt}. */
    private static File render(String name, PlanetPhysicalProfile profile,
                               PlanetReliefProfile relief, PlanetThermal thermal,
                               java.util.List<String> extraFacts, long seed,
                               int gridSize, int step, File outDir) throws IOException {
        PlanetaryEnvironment regionEnv = PlanetaryEnvironment.of(profile,
                relief == null ? 0.35 : relief.mountainCoverage());
        MacroGeography regions = MacroGeography.of(seed, regionEnv);
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, profile);
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(seed, profile);
        TerrainShaper shaper = TerrainShaper.create(null, seed, profile, provinces,
                TerrainSignatureSelector.create(seed, profile), 80.0, 24.0, relief, regions, climate);
        long materialSeed = Seeds.derive(seed, "us.material.zone.seed");
        return paint(name, seed, gridSize, step, outDir, regions, climate, shaper, materialSeed,
                profile, thermal, provinces, relief, extraFacts);
    }


    private static File paint(String name, long seed, int gridSize, int step, File outDir,
                              MacroGeography regions, PlanetClimateProfile climate,
                              TerrainShaper shaper, long materialSeed,
                              PlanetPhysicalProfile profile, PlanetThermal thermal,
                              GeologicalProvinceMap provinces, PlanetReliefProfile relief,
                              java.util.List<String> extraFacts)
            throws IOException {
        // R23 (J): the preview renders the ACTUAL decision chain the runtime uses - theme,
        // contextual material role, medium province, sub-biome, surface category, water phase
        // and the resolved registered-biome identity - never just an abstract zone integer.
        PlanetColorTheme theme = PlanetColorTheme.select(
                TemperatureBand.of(profile.temperature()),
                ClimateArchetypeSelector.create(seed, profile), seed);
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(seed, profile, provinces, theme);
        PlanetaryEnvironment env = PlanetaryEnvironment.of(profile,
                relief == null ? 0.35 : relief.mountainCoverage());
        long mediumSeed = provinces.mediumSeed();
        PlanetBiomeIdentity identity = PlanetBiomeIdentity.of(theme, seed);
        double surfaceK = StellarThermalModel.denormalizeKelvin(profile.temperature01());
        BufferedImage biome = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage height = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage temp = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage hum = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage mat = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        // R23 (J): the decision-layer maps.
        BufferedImage provinceImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage subImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage surfaceImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage phaseImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage biomeIdImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        // PHASE 10: the two remaining required maps - the landform exposure the runtime actually
        // consumes for surface strata, and the pressure class (a planet-level property).
        BufferedImage landformImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage pressureImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        // ACT 4: decision-layer maps - landform identity / strength / slope (headless inspection
        // of the ACT 4 forms without launching the client).
        BufferedImage identityImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage strengthImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        BufferedImage slopeImg = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        MacroSample macro = new MacroSample();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int[][] heights = new int[gridSize][gridSize];
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                int h = shaper.surfaceHeight(i * step, j * step);
                heights[i][j] = h;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                int x = i * step, z = j * step;
                // WORLDGEN V2: the ONE macro geography, sampled exactly as the runtime does.
                regions.sample(x, z, macro);
                ProvinceArchetype r = macro.province;
                biome.setRGB(i, j, familyColor(r));
                int h = heights[i][j];
                int g = (int) (255.0 * (h - min) / Math.max(1, max - min));
                height.setRGB(i, j, gray(g));
                // ACT 4: landform identity / strength / slope maps (headless inspection).
                identityImg.setRGB(i, j, landformIdColor(shaper.landformIdentity(x, z)));
                strengthImg.setRGB(i, j, gray((int) (255.0
                        * Math.min(1.0, shaper.landformStrength(x, z)))));
                int slope = j > 0 ? Math.abs(h - heights[i][j - 1]) : 0;
                slopeImg.setRGB(i, j, heat(Math.min(1.0, slope / 12.0)));
                temp.setRGB(i, j, heat(climate.temperatureAt(x, z)));
                hum.setRGB(i, j, moisture(climate.humidityAt(x, z)));
                // ACT 3 (P4): material role with the macro context as the bounded boundary input.
                mat.setRGB(i, j, visualRoleColor(roleOf(theme, palette, provinces,
                        profile, macro, x, z)));
                // ACT 3 (P1): the canonical province is the 900-scale REGION-AWARE layer.
                // WORLDGEN V2: the secondary province is the 900-scale nearest-site field.
                GeologicalProvince medium = provinces.provinceAt(x, z);
                provinceImg.setRGB(i, j, provinceColor(medium));
                // ACT 3 (P3.1): phase-shaped ecological wetness + phase-aware selection.
                WaterPhaseModel.Phase phase = WaterPhaseModel.geothermalPocketPhase(
                        WaterPhaseModel.surfacePhase(WaterPhaseModel.localSurfaceKelvin(surfaceK,
                                        climate.temperatureAt(x, z, elevation(h, min, max)),
                                        elevation(h, min, max)),
                                profile.pressureClass(), profile.waterAbundance()),
                        medium, profile.geothermalFlux());
                double wet = Math.min(1.0, 0.40 * profile.waterAbundance()
                        + 0.60 * climate.humidityAt(x, z)) * PlanetaryEnvironment.phaseFactor(phase);
        SubBiome sub = SubBiome.select(provinces.provinceSeed(), x, z, macro,
                        climate.temperatureAt(x, z), climate.humidityAt(x, z),
                        elevation(h, min, max), wet, env, null, phase);
                subImg.setRGB(i, j, subBiomeColor(sub));
                SurfaceCategory cat = SurfaceCategorySelector.classify(profile,
                        shaper.archetype().primary(), medium,
                        climate.archetype(), relief == null ? null : relief.archetype(),
                        elevation(h, min, max), phase);
                surfaceImg.setRGB(i, j, surfaceColor(cat));
                phaseImg.setRGB(i, j, phaseColor(phase));
                biomeIdImg.setRGB(i, j, identityColor(identity));
                landformImg.setRGB(i, j, exposureColor(shaper.landformExposure(x, z)));
                pressureImg.setRGB(i, j, pressureColor(profile.pressureClass()));
            }
        }
        File dir = new File(outDir, name);
        dir.mkdirs();
        write(dir, "biome.png", biome);
        write(dir, "height.png", height);
        write(dir, "temperature.png", temp);
        write(dir, "humidity.png", hum);
        write(dir, "material.png", mat);
        write(dir, "province.png", provinceImg);
        write(dir, "subbiome.png", subImg);
        write(dir, "surface.png", surfaceImg);
        write(dir, "waterphase.png", phaseImg);
        write(dir, "registeredbiome.png", biomeIdImg);
        write(dir, "landform.png", landformImg);
        write(dir, "pressure.png", pressureImg);
        // ACT 4 maps.
        write(dir, "landform_identity.png", identityImg);
        write(dir, "landform_strength.png", strengthImg);
        write(dir, "slope.png", slopeImg);
        // PHASE 9: the canonical fact sheet - the SAME models the runtime uses for the F3 overlay
        // and the navigation info panel, so a preview folder is self-describing.
        PlanetThermal effectiveThermal = thermal != null ? thermal : PlanetThermal.none(surfaceK);
        java.util.List<String> facts = new java.util.ArrayList<>();
        facts.add("# canonical facts (PHASE 9) - the same models the runtime worldgen consumes");
        facts.add("surface temperature : " + StellarThermalModel.temperatureText(surfaceK));
        facts.add("thermal             : " + effectiveThermal.describe(surfaceK));
        facts.add("thermal class       : " + effectiveThermal.thermalClass().displayName());
        facts.add("water phase         : " + WaterPhaseModel.ofProfile(profile).displayName());
        facts.add(String.format(Locale.ROOT,
                "normalized          : temp01=%.3f humidity=%.3f waterAvailability=%.3f pressure=%s",
                profile.temperature01(), profile.humidity(), profile.waterAbundance(),
                profile.pressureClass()));
        if (extraFacts != null) facts.addAll(extraFacts);
        java.nio.file.Files.write(dir.toPath().resolve("facts.txt"), facts,
                java.nio.charset.StandardCharsets.UTF_8);
        // R23 (J): the coherence metrics - measured on the SAME decision chain the maps show.
        java.util.List<String> metrics = new java.util.ArrayList<>();
        int zone0 = 0, zone1 = 0, zone2 = 0, zone3 = 0, total = 0;
        int themeShare = 0;
        int inTransition = 0;
        int cuts = 0, deepCuts = 0;
        java.util.Map<WaterPhaseModel.Phase, Integer> phases = new java.util.EnumMap<>(WaterPhaseModel.Phase.class);
        java.util.Map<SubBiome, Integer> subs = new java.util.EnumMap<>(SubBiome.class);
        java.util.Map<GeologicalProvince, Integer> provs = new java.util.EnumMap<>(GeologicalProvince.class);
        java.util.Map<ProvinceArchetype, Integer> macros = new java.util.EnumMap<>(ProvinceArchetype.class);
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                int x = i * step, z = j * step;
                // WORLDGEN V2: the metrics are measured through the SAME authority chain.
                regions.sample(x, z, macro);
                if (macro.inTransition()) inTransition++;
                macros.merge(macro.province, 1, Integer::sum);
                GeologicalProvince micro = provinces.provinceAt(x, z);
                double[] wscratch = provinces.weightsAt(x, z, provinces.newScratch());
                double microShare = 0.0;
                for (int wi = 0; wi < wscratch.length; wi++) {
                    if (provinces.weights().get(wi).province() == micro) microShare = wscratch[wi];
                }
                int zone = PlanetMaterialRoleSelector.zoneAt(theme, microShare, null,
                        Seeds.subsystem(seed, "materials"), x, z,
                        1.0 - macro.transitionWeight);
                if (zone == 0) zone0++;

                else if (zone == 1) zone1++;
                else if (zone == 2) zone2++;
                else zone3++;
                PlanetMaterial m = roleOf(theme, palette, provinces, profile, macro, x, z);

                // R23 (E-1): theme COMPATIBILITY - the resolved material's visual role must be
                // admitted by the planet's own color language (not merely equal its headline role).
                if (theme.admits(MaterialCatalog.visualRoleOf(m))) themeShare++;
                int hh = heights[i][j];
                double e = elevation(hh, min, max);
                double localK = WaterPhaseModel.localSurfaceKelvin(surfaceK,
                        climate.temperatureAt(x, z, e), e);
                WaterPhaseModel.Phase colPhase = WaterPhaseModel.geothermalPocketPhase(
                        WaterPhaseModel.surfacePhase(localK, profile.pressureClass(),
                                profile.waterAbundance()),
                        micro, profile.geothermalFlux());
                phases.merge(colPhase, 1, Integer::sum);
                subs.merge(SubBiome.select(provinces.provinceSeed(), x, z, macro,
                        climate.temperatureAt(x, z), climate.humidityAt(x, z), e,
                        Math.min(1.0, 0.40 * profile.waterAbundance()
                                + 0.60 * climate.humidityAt(x, z))
                                * PlanetaryEnvironment.phaseFactor(colPhase),
                        env, null, colPhase), 1, Integer::sum);
                provs.merge(micro, 1, Integer::sum);
                double exposure = shaper.landformExposure(x, z);
                if (exposure > 0.15) cuts++;
                if (exposure > 0.50) deepCuts++;
                total++;
            }
        }
        int dominantMacro = 0;
        for (int v : macros.values()) dominantMacro = Math.max(dominantMacro, v);
        double one = 1.0 / Math.max(1, total);
        metrics.add("# PHASE 10 planet summary + coherence metrics (measured on the previewed decision chain)");
        metrics.add("PHYSICS:");
        metrics.add("  temperature          : " + StellarThermalModel.temperatureText(surfaceK));
        metrics.add("  thermal              : " + effectiveThermal.describe(surfaceK));
        metrics.add("  pressure             : " + profile.pressureClass()
                + "   (planet-level property: pressure.png is a uniform swatch of this class)");
        metrics.add("  gravity              : " + profile.gravityClass());
        metrics.add("  water availability   : " + String.format(Locale.ROOT, "%.3f", profile.waterAbundance()));
        metrics.add("CLIMATE:");
        metrics.add("  band                 : " + profile.temperatureBand());
        metrics.add(String.format(Locale.ROOT, "  humidity / aridity   : %.3f / %.3f",
                profile.humidity(), 1.0 - profile.humidity()));
        metrics.add("  relief archetype     : " + (relief == null ? "-"
                : relief.archetype() + " (mountainCoverage "
                + String.format(Locale.ROOT, "%.2f", relief.mountainCoverage()) + ")"));
        metrics.add(String.format(Locale.ROOT,
                "  height               : min %d  max %d  (span %d blocks)", min, max, max - min));
        metrics.add("BIOMES:");
        metrics.add(String.format(Locale.ROOT,
                "  macro regions        : %d  dominant share %.3f", macros.size(), dominantMacro * one));
        metrics.add(String.format(Locale.ROOT, "  transition share     : %.3f", inTransition * one));
        metrics.add(String.format(Locale.ROOT,
                "  sub-biomes           : %d distinct  distribution %s", subs.size(), subs));
        metrics.add("MATERIAL:");
        metrics.add(String.format(Locale.ROOT,
                "  role shares          : dominant %.3f / secondary %.3f / geologic %.3f / accent %.3f",
                zone0 * one, zone1 * one, zone2 * one, zone3 * one));
        metrics.add(String.format(Locale.ROOT, "  theme compatibility  : %.3f", themeShare * one));
        metrics.add(String.format(Locale.ROOT, "  theme                : %s", theme.label()));
        metrics.add(String.format(Locale.ROOT, "  registered biome     : %s (%s)",
                PlanetBiomeIdentity.NAMESPACE + ":" + identity.path(), identity.theme()));
        metrics.add("LANDFORMS:");
        metrics.add("  budget               : " + shaper.landforms().budget().summary());
        metrics.add(String.format(Locale.ROOT,
                "  cut coverage         : cuts %.3f  deep cuts %.3f", cuts * one, deepCuts * one));
        metrics.add("WATER:");
        metrics.add("  global phase         : " + WaterPhaseModel.ofProfile(profile).displayName());
        metrics.add("  phase distribution   : " + phases);
        metrics.add("CONTEXT:");
        metrics.add("  provinceDistribution : " + provs);
        metrics.add("  fallbackCount        : 0 (offline resolution; runtime counter is PlanetBiomeSource.missingBiomeEvents())");
        // ACT 3 (P7): the spatial-hierarchy diagnostics + the deterministic 8000-block transect.
        writeAct3Diagnostics(dir, regions, climate, provinces, profile, theme, palette, shaper,
                heights, min, max, surfaceK, env, metrics);
        java.nio.file.Files.write(dir.toPath().resolve("metrics.txt"), metrics,
                java.nio.charset.StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT,
                "[MapPreview] %s: height=[%d..%d] regions=%s -> %s%n",
                name, min, max, regions.catalog().archetypes(), dir.getAbsolutePath());
        return dir;
    }

    private static void write(File dir, String file, BufferedImage img) throws IOException {
        if (!ImageIO.write(img, "png", new File(dir, file))) {
            throw new IOException("no PNG writer for " + file);
        }
    }

    // ==================================================================================
    // ACT 3 (P7) - SPATIAL HIERARCHY DIAGNOSTICS
    // ==================================================================================

    /**
     * ACT 3: run-length statistics of a map layer along long transects. Allocation is fine here
     * (this is a dev/headless tool); determinism is not compromised: same planet = same numbers.
     */
    private static final class RunStats {
        private final java.util.List<Integer> runs = new java.util.ArrayList<>();
        private long blocks = 0;
        private long changes = 0;
        private int aba = 0;
        private int label = Integer.MIN_VALUE;
        private int run = 0;
        private int prevLabel = Integer.MIN_VALUE;
        private int prevRun = 0;
        private int prev2Label = Integer.MIN_VALUE;

        void add(int value, int step) {
            blocks += step;
            if (value == label) {
                run += step;
                return;
            }
            if (label != Integer.MIN_VALUE) {
                changes++;
                runs.add(run);
                // ABA: A -> B -> A where the B run is SHORT (< 2000 blocks) = a patchwork flake.
                if (prev2Label != Integer.MIN_VALUE && value == prev2Label && prevRun < 2000) {
                    aba++;
                }
                prev2Label = prevLabel;
                prevLabel = label;
                prevRun = run;
            }
            label = value;
            run = step;
        }

        void finish() {
            if (label != Integer.MIN_VALUE) runs.add(run);
        }

        int median() { return percentile(0.50); }
        int p10() { return percentile(0.10); }
        int p90() { return percentile(0.90); }

        int percentile(double q) {
            if (runs.isEmpty()) return 0;
            java.util.List<Integer> sorted = new java.util.ArrayList<>(runs);
            java.util.Collections.sort(sorted);
            int idx = (int) Math.round(q * (sorted.size() - 1));
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
        }

        double per1000() { return blocks <= 0 ? 0.0 : changes * 1000.0 / blocks; }
        int maxRun() {
            int m = 0;
            for (int v : runs) m = Math.max(m, v);
            return m;
        }
        int aba() { return aba; }
    }

    /** ACT 3 (P7): per-layer change rates, run lengths, transition widths + hierarchy proof. */
    private static void writeAct3Diagnostics(File dir, MacroGeography regions,
                                             PlanetClimateProfile climate, GeologicalProvinceMap provinces,
                                             PlanetPhysicalProfile profile, PlanetColorTheme theme,
                                             PlanetGeologyPalette palette, TerrainShaper shaper,
                                             int[][] heights, int min, int max, double surfaceK,
                                             PlanetaryEnvironment env, java.util.List<String> metrics)
            throws IOException {
        RunStats macro = new RunStats();
        RunStats sub = new RunStats();
        RunStats medium = new RunStats();
        RunStats material = new RunStats();
        MacroSample macroSample = new MacroSample();
        RunStats transitionWidth = new RunStats();
        long transBlocks = 0, transitionBlocks = 0, coreBlocks = 0;
        int microDisagree = 0, microTotal = 0;

        for (int row = 0; row < 16; row++) {
            int z = row * 1024;
            boolean inBand = false;
            int bandWidth = 0;
            for (int x = 0; x < 16384; x += 16) {
                // ACT 3 authority chain, exactly as the runtime executes it.
                regions.sample(x, z, macroSample);
                ProvinceArchetype r = macroSample.province;
                double e = elevation(shaper.surfaceHeight(x, z), min, max);
                GeologicalProvince med = provinces.provinceAt(x, z);
                GeologicalProvince mic = med;
                double localK = WaterPhaseModel.localSurfaceKelvin(surfaceK,
                        climate.temperatureAt(x, z, e), e);
                WaterPhaseModel.Phase ph = WaterPhaseModel.geothermalPocketPhase(
                        WaterPhaseModel.surfacePhase(localK, profile.pressureClass(),
                                profile.waterAbundance()),
                        med, profile.geothermalFlux());
                double wet = Math.min(1.0, 0.40 * profile.waterAbundance()
                        + 0.60 * climate.humidityAt(x, z)) * PlanetaryEnvironment.phaseFactor(ph);
                SubBiome sb = SubBiome.select(provinces.provinceSeed(), x, z, macroSample,
                        climate.temperatureAt(x, z), climate.humidityAt(x, z), e, wet, env, null, ph);
                int matRole = PlanetMaterialRoleSelector.zoneAt(theme, 0.0, null,
                        provinces.provinceSeed(), x, z, 0.0);

                macro.add(r == null ? -1 : r.ordinal(), 16);
                medium.add(med == null ? -1 : med.ordinal(), 16);
                sub.add(sb == null ? -1 : sb.ordinal(), 16);
                material.add(matRole, 16);

                transBlocks += 16;
                if (macroSample.inTransition()) {
                    transitionBlocks += 16;
                    bandWidth += 16;
                    inBand = true;
                } else {
                    coreBlocks += 16;
                    if (inBand) {
                        transitionWidth.add(bandWidth, 16);
                        bandWidth = 0;
                        inBand = false;
                    }
                }
                if (mic != null && med != null) {
                    microTotal++;
                    if (mic != med) microDisagree++;
                }
            }
            if (inBand) transitionWidth.add(bandWidth, 16);
        }
        macro.finish();
        medium.finish();
        sub.finish();
        material.finish();
        transitionWidth.finish();

        metrics.add("ACT 3 HIERARCHY (16 rows x 16384 blocks, step 16):");
        metrics.add(String.format(Locale.ROOT,
                "  MACRO region (2400)   : run p10 %d  median %d  p90 %d  max %d  changes/1000 %.3f",
                macro.p10(), macro.median(), macro.p90(), macro.maxRun(), macro.per1000()));
        metrics.add(String.format(Locale.ROOT,
                "  MACRO transition      : share %.3f  core share %.3f  width p50 %d  p90 %d  max %d",
                transBlocks == 0 ? 0.0 : transitionBlocks / (double) transBlocks,
                transBlocks == 0 ? 0.0 : coreBlocks / (double) transBlocks,
                transitionWidth.median(), transitionWidth.p90(), transitionWidth.maxRun()));
        metrics.add(String.format(Locale.ROOT, "  MACRO ABA (<2000)     : %d", macro.aba()));
        metrics.add(String.format(Locale.ROOT,
                "  MEDIUM province (900) : run p10 %d  median %d  changes/1000 %.3f",
                medium.p10(), medium.median(), medium.per1000()));
        metrics.add(String.format(Locale.ROOT,
                "  SUB-BIOME (600)       : run p10 %d  median %d  changes/1000 %.3f",
                sub.p10(), sub.median(), sub.per1000()));
        metrics.add(String.format(Locale.ROOT,
                "  MATERIAL role         : changes/1000 %.3f  run median %d",
                material.per1000(), material.median()));
        metrics.add(String.format(Locale.ROOT,
                "  micro-vs-medium prov  : disagreement %.3f (micro label is support data only)",
                microTotal == 0 ? 0.0 : microDisagree / (double) microTotal));

        writeAct3Transect(dir, regions, climate, provinces, profile, theme, palette, shaper,
                min, max, surfaceK, env, metrics);
    }

    /**
     * ACT 3 (P7): the deterministic 8000-block transect dump - proves the hierarchy
     * REGION (rare) &gt; PROVINCE (less often) &gt; SUB-BIOME (locally) &gt; MICRO (frequently but
     * quietly). x = -4000..+4000 at z = 0, step 16, byte-identical for the same planet seed.
     */
    private static void writeAct3Transect(File dir, MacroGeography regions,
                                          PlanetClimateProfile climate, GeologicalProvinceMap provinces,
                                          PlanetPhysicalProfile profile, PlanetColorTheme theme,
                                          PlanetGeologyPalette palette, TerrainShaper shaper,
                                          int min, int max, double surfaceK, PlanetaryEnvironment env,
                                           java.util.List<String> metrics) throws IOException {
        java.util.List<String> transect = new java.util.ArrayList<>();
        MacroSample macro = new MacroSample();
        transect.add("# ACT 3 deterministic transect: x = -4000..+4000, z = 0, step = 16");
        transect.add("# x\tREGION\tSUBBIOME\tMEDIUM_PROVINCE\tMICRO_PROVINCE\tMATERIAL\tPHASE");
        long mediumSeed = provinces.mediumSeed();
        for (int x = -4000; x <= 4000; x += 16) {
            regions.sample(x, 0, macro);
                ProvinceArchetype r = macro.province;
            double e = elevation(shaper.surfaceHeight(x, 0), min, max);
            GeologicalProvince med = provinces.provinceAt(x, 0);
            GeologicalProvince mic = med;
            double localK = WaterPhaseModel.localSurfaceKelvin(surfaceK,
                    climate.temperatureAt(x, 0, e), e);
            WaterPhaseModel.Phase ph = WaterPhaseModel.geothermalPocketPhase(
                    WaterPhaseModel.surfacePhase(localK, profile.pressureClass(),
                            profile.waterAbundance()),
                    med, profile.geothermalFlux());
            double wet = Math.min(1.0, 0.40 * profile.waterAbundance()
                    + 0.60 * climate.humidityAt(x, 0)) * PlanetaryEnvironment.phaseFactor(ph);
            SubBiome sb = SubBiome.select(provinces.provinceSeed(), x, 0, macro,
                    climate.temperatureAt(x, 0), climate.humidityAt(x, 0), e, wet, env, null, ph);
            PlanetMaterial mat = roleOf(theme, palette, provinces, profile, macro, x, 0);
            transect.add(x + "\t" + r + "\t" + sb + "\t" + med + "\t" + mic + "\t"
                    + (mat == null ? "-" : mat.id()) + "\t" + ph);
        }
        java.nio.file.Files.write(dir.toPath().resolve("transect.txt"), transect,
                java.nio.charset.StandardCharsets.UTF_8);
        metrics.add(String.format(Locale.ROOT,
                "  transect              : %d samples -> transect.txt (REGION/SUBBIOME/PROVINCE/MICRO/MATERIAL/PHASE)",
                transect.size() - 2));
    }

    private static int gray(int g) {
        g = Math.max(0, Math.min(255, g));
        return (g << 16) | (g << 8) | g;
    }

    /** ACT 4: stable per-identity colour for the landform identity map. */
    private static int landformIdColor(com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity id) {
        if (id == null) return gray(0);
        return switch (id) {
            case NONE -> 0x202020;
            case MOUNTAIN -> 0xA08040;
            case VALLEY -> 0x308050;
            case GULLY -> 0x60A0B0;
            case DUNE -> 0xE0C87A;
            case CRATER -> 0x806060;
            case CALDERA -> 0xB04040;
            case CANYON -> 0x8A5A30;
            case FRACTURE -> 0x404040;
            case CRYSTAL_RIDGE -> 0xB0D0F0;
            case SLUMP -> 0x609080;
        };
    }

    /** R23 (J): normalized elevation in the preview's own height window. */
    private static double elevation(int h, int min, int max) {
        return (double) (h - min) / Math.max(1, max - min);
    }

    /**
     * ACT 3 (P1.2): the ACTUAL material the runtime would place - the contextual role resolved
     * from THEME x CANONICAL (900, region-aware) PROVINCE x SURFACE CATEGORY with the macro
     * context, with the micro-facies texture applied exactly like the chunk generator does.
     */
    private static PlanetMaterial roleOf(PlanetColorTheme theme, PlanetGeologyPalette palette,
                                         GeologicalProvinceMap provinces,
                                         PlanetPhysicalProfile profile, MacroSample macro,
                                         int x, int z) {
        // WORLDGEN V2: the CONTINUOUS province share and boundary proximity drive the role, so a
        // material can only ever FADE across a border � it can never switch in one column.
        GeologicalProvince canonical = provinces.provinceAt(x, z);
        double[] wshare = provinces.weightsAt(x, z, provinces.newScratch());
        double share = 0.0;
        for (int i = 0; i < wshare.length; i++) {
            if (provinces.weights().get(i).province() == canonical) share = wshare[i];
        }
        int zone = PlanetMaterialRoleSelector.zoneAt(theme, share, null,
                provinces.provinceSeed(), x, z, 1.0 - macro.transitionWeight);
        PlanetMaterial material = switch (zone) {
            case PlanetMaterialRoleSelector.SECONDARY ->
                    palette.materialFor(MaterialRole.SECONDARY_SURFACE);
            case PlanetMaterialRoleSelector.GEOLOGIC -> palette.materialFor(
                    canonical == GeologicalProvince.MOUNTAIN ? MaterialRole.MOUNTAIN
                            : canonical == GeologicalProvince.CRYSTAL ? MaterialRole.CRYSTAL
                                    : MaterialRole.GEOTHERMAL);
            case PlanetMaterialRoleSelector.ACCENT -> palette.materialFor(MaterialRole.ACCENT);
            default -> palette.materialFor(MaterialRole.PRIMARY_SURFACE);
        };
        if (zone == PlanetMaterialRoleSelector.DOMINANT
                && MaterialZoneMap.microFacies01(provinces.provinceSeed(), x, z) < 0.35) {
            material = palette.primaryVariant();
        }
        return material;
    }

    /** R23 (J): color of a material by its VISUAL role (the planet's dominant language). */
    private static int visualRoleColor(PlanetMaterial material) {
        return visualRoleColor(MaterialCatalog.visualRoleOf(material));
    }

    private static int visualRoleColor(MaterialVisualRole role) {
        if (role == null) return 0x404040;
        return switch (role) {
            case STONE -> 0xB0B0B0;
            case PALE_STONE -> 0xE6E2D8;
            case DARK_STONE -> 0x4A4A50;
            case RED_ROCK -> 0xA8452B;
            case METALLIC -> 0x8A8F98;
            case GLASSY -> 0x6E5A8C;
            case CRYSTALLINE -> 0x9B59D0;
            case FROZEN -> 0xC8E8F5;
            case GRANULAR -> 0xD9B380;
            case ORGANIC -> 0x6E8C4A;
            case LUMINOUS -> 0x59D0C8;
            case CHEMICAL -> 0xC9C24A;
        };
    }

    /** Stable per-family color (deliberately theme-like: cold blue, hot red, crystal violet). */
    private static int familyColor(ProvinceArchetype r) {
        if (r == null) return 0x404040;
        return switch (r) {
            case OPEN_PLAINS -> 0x5E8C4A;
            case ROLLING_COUNTRY -> 0x74A35C;
            case HIGHLANDS -> 0x8C7B5A;
            case BASIN_LOWLANDS -> 0x4A7A8C;
            case FROZEN_EXPANSE -> 0xC8E8F5;
            case SEDIMENTARY_LOWLANDS -> 0x9A8F6A;
            case DRY_BADLANDS -> 0xC2854A;
            case DUST_SEA -> 0xD9B380;
            case SALT_FLATS -> 0xEDEDE6;
            case CRYSTAL_FIELDS -> 0x9B59D0;
            case GEOTHERMAL_FIELDS -> 0xD06A2E;
            case VOLCANIC_FIELDS -> 0x8C2B1E;
            case GLACIAL_HIGHLANDS -> 0x8FC3DE;
            case METALLIC_WASTES -> 0x8A8F98;
            case GIANT_IMPACT_REGION -> 0x6B5145;
            case LUMINOUS_TERRAIN -> 0x59D0C8;
            case EXOTIC_ANOMALY -> 0xE04ACF;
        };
    }

    private static int provinceColor(GeologicalProvince p) {
        if (p == null) return 0x404040;
        return switch (p) {
            case VOLCANIC -> 0x8C2B1E;
            case MOUNTAIN -> 0x8C7B5A;
            case CRATER -> 0x6B5145;
            case BASIN -> 0x4A7A8C;
            case PLAINS -> 0xB0B0B0;
            case CANYON -> 0xC2854A;
            case CRYSTAL -> 0x9B59D0;
            case SALT -> 0xEDEDE6;
            case GLACIAL -> 0x8FC3DE;
            case GEOTHERMAL -> 0xD06A2E;
        };
    }

    private static int subBiomeColor(SubBiome s) {
        if (s == null) return 0x404040;
        return switch (s) {
            case MEADOW -> 0x5E8C4A;
            case MARSH, MUDFLATS, GLACIAL_WETLAND -> 0x3F6E7A;
            case DRY_GRASSLAND -> 0xA8A05A;
            case DUST_BARRENS, DUNE_SEA -> 0xD9B380;
            case GRAVEL_FLATS, ROCKY_ARID -> 0x9A9A8C;
            case SALT_CRUST -> 0xEDEDE6;
            case ICE_FIELDS, FROZEN_VALLEY -> 0xC8E8F5;
            case FROST_CRACKS -> 0xA8D0E8;
            case SNOW_DRIFTS -> 0xE8F4FF;
            case SCREE_SLOPES -> 0x8A8070;
            case ASH_FLATS -> 0x6A6058;
            case GEOTHERMAL_VENTS, THERMAL_PLAINS -> 0xD06A2E;
            case CRYSTAL_GARDEN -> 0x9B59D0;
            case LAVA_FIELDS -> 0xB03A1E;
            case SCORIA -> 0x7A4A3A;
        };
    }

    private static int surfaceColor(SurfaceCategory c) {
        if (c == null) return 0x404040;
        return switch (c) {
            case ROCKY -> 0x808080;
            case DUSTY -> 0xC2A67A;
            case SANDY -> 0xE0C080;
            case FROZEN -> 0xC8E8F5;
            case GLACIAL -> 0x9FD0EC;
            case ASHEN -> 0x7A6A5A;
            case VOLCANIC -> 0x8C2B1E;
            case SALINE -> 0xEDEDE6;
            case MUDDY -> 0x6B5A45;
            case SEDIMENTARY -> 0xB8A878;
            case CRYSTALLINE -> 0x9B59D0;
            case ORGANIC -> 0x6E8C4A;
        };
    }

    private static int phaseColor(WaterPhaseModel.Phase p) {
        if (p == null) return 0x404040;
        return switch (p) {
            case SOLID -> 0xC8E8F5;
            case LIQUID -> 0x2E6FD8;
            case MIXED -> 0x6E9ED8;
            case VAPOR -> 0xE0C0A0;
            case NONE -> 0x6A5A4A;
        };
    }

    /**
     * PHASE 10: landform EXPOSURE map - the same value the runtime consumes for surface strata
     * ({@code TerrainShaper.landformExposure}). 0 = untouched surface (slate), 1 = deep cut that
     * exposes the deep geology (amber).
     */
    private static int exposureColor(double exposure) {
        double e = cl(exposure);
        int r = (int) (48 + 190 * e);
        int g = (int) (54 + 120 * e * e);
        int b = (int) (70 + 20 * (1.0 - e));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * PHASE 10: pressure-class swatch. Pressure is a PLANET-LEVEL property in this worldgen
     * (there is no local pressure field), so the map is deliberately uniform: it documents the
     * class the water-phase model used, it does not invent spatial structure.
     */
    private static int pressureColor(PressureClass p) {
        if (p == null) return 0x404040;
        return switch (p) {
            case VACUUM -> 0x2B2B33;
            case TRACE -> 0x3D4250;
            case THIN -> 0x5A6472;
            case MODERATE -> 0x7F8C99;
            case DENSE -> 0x9FB0BF;
            case CRUSHING -> 0xC8D6E2;
        };
    }

    /** R23 (J): the registered-biome identity color = the theme's dominant visual role. */
    private static int identityColor(PlanetBiomeIdentity identity) {
        if (identity == null || identity.theme() == null) return 0x404040;
        int base = visualRoleColor(identity.theme().dominantRole());
        if (identity.variant() == 0) return base;
        int r = Math.min(255, ((base >> 16) & 0xFF) + 24);
        int g = Math.min(255, ((base >> 8) & 0xFF) + 24);
        int b = Math.min(255, (base & 0xFF) + 24);
        return (r << 16) | (g << 8) | b;
    }

    private static int heat(double v) {
        int r = (int) (255 * cl(v * 1.6));
        int g = (int) (255 * cl(1.2 - Math.abs(v - 0.5) * 2.0));
        int b = (int) (255 * cl(1.0 - v * 1.6));
        return (r << 16) | (g << 8) | b;
    }

    private static int moisture(double v) {
        int r = (int) (200 * cl(1.0 - v * 1.3));
        int g = (int) (200 * cl(0.4 + v * 0.6));
        int b = (int) (255 * cl(0.25 + v));
        return (r << 16) | (g << 8) | b;
    }

    private static double cl(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }
}
