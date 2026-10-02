package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 R23 (B-1): the planet's REGISTERED Minecraft biome identity.

 <pre>
 PLANET -&gt; TemperatureBand (hard filter) -&gt; PlanetColorTheme -&gt; REGISTERED BIOME (this)
 </pre>

 <p>The registered biome is the planet's <b>global visual / ambient identity</b> (sky, fog, water,
 water fog, grass, foliage) and it is derived from the SAME theme that drives the planetary
 material palette — so the ambience and the blocks can never disagree. The macro GEOGRAPHY
 (which part of the planet is frozen, volcanic, oceanic…) stays in {@link
 com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap}; the registered biome does
 not replace it.

 <p>Two shipped variants per theme exist so different planets of the same theme still differ
 (different worlds must not be pixel-identical), and every identity is a pure function of
 {@code (theme, planetSeed)} — the same planet always resolves the same resource location.

 <p>Pure domain: no Minecraft types — the adapter turns {@link #path()} into a
 {@code ResourceLocation} in the {@value #NAMESPACE} namespace.
 */
public record PlanetBiomeIdentity(String path, PlanetColorTheme theme, int variant) {

 /** Namespace of every generated planet biome id. */
 public static final String NAMESPACE = "unlimitedspace";

 /** Number of shipped ambience variants per theme. */
 public static final int VARIANTS = 2;

    /**
     * The registered ambience theme that CARRIES each V3 classifier candidate.
     *
     * <p>The V3 classifier elects a <b>candidate</b> ({@code tundra}, {@code dune_sea},
     * {@code crystal_field}, …) from {@code BiomeMaskField#defaultCandidates()}, while the datapack
     * pool ships <b>ambience</b> identities ({@code planet_ice_a}, {@code planet_desert_b}, …). The
     * two id spaces are disjoint BY DESIGN, so without this binding an elected candidate could
     * never resolve a holder and every single column query would take the counted miss path.
     *
     * <p>The binding is a pure function of the candidate id: the same column always elects the same
     * theme, so the ambience (sky / fog / water / grass) agrees with the local environment instead
     * of collapsing to one planet-wide identity.
     */
    private static final Map<String, PlanetColorTheme> CANDIDATE_THEMES = buildCandidateThemes();

    private static Map<String, PlanetColorTheme> buildCandidateThemes() {
        Map<String, PlanetColorTheme> m = new HashMap<>();
        m.put("ice_sheet", PlanetColorTheme.ICE_THEME);
        m.put("tundra", PlanetColorTheme.ICE_THEME);
        m.put("alpine_snow", PlanetColorTheme.ICE_THEME);
        m.put("salt_flat", PlanetColorTheme.SALT_THEME);
        m.put("crystal_field", PlanetColorTheme.ALIEN_CRYSTAL_THEME);
        m.put("wetland", PlanetColorTheme.OCEANIC_THEME);
        m.put("dune_sea", PlanetColorTheme.DESERT_THEME);
        m.put("arid_rock", PlanetColorTheme.DESERT_THEME);
        m.put("rocky_highland", PlanetColorTheme.ASHEN_THEME);
        m.put("volcanic_plateau", PlanetColorTheme.ASHEN_THEME);
        m.put("steppe", PlanetColorTheme.TEMPERATE_THEME);
        m.put("temperate_lowlands", PlanetColorTheme.TEMPERATE_THEME);
        m.put("forest", PlanetColorTheme.TEMPERATE_THEME);
        return Map.copyOf(m);
    }

    /**
     * The ambience theme a classifier candidate belongs to, or {@code null} for an unknown id.
     *
     * <p>{@code null} is a real failure: it means the candidate catalogue grew without a theme, and
     * the caller must report it rather than silently picking an arbitrary ambience.
     */
    public static PlanetColorTheme themeForCandidate(String candidateId) {
        return candidateId == null ? null : CANDIDATE_THEMES.get(candidateId);
    }


 /** Deterministic identity of a planet: theme (already band-filtered) + seed variant. */
 public static PlanetBiomeIdentity of(PlanetColorTheme theme, long planetSeed) {
 PlanetColorTheme t = theme == null ? PlanetColorTheme.TEMPERATE_THEME : theme;
 double f = Seeds.fraction(Seeds.derive(planetSeed, "us.biome.identity"), 93001L);
        int variant = Math.min(VARIANTS - 1, (int) Math.floor(f * VARIANTS));
 return new PlanetBiomeIdentity(path(t, variant), t, variant);
 }

 /** Resource path of one theme variant, e.g. {@code planet_ice_a}. */
 public static String path(PlanetColorTheme theme, int variant) {
 return "planet_" + slug(theme) + "_" + (variant == 0 ? "a" : "b");
 }

 /** Stable lowercase slug of a theme used in resource paths ({@code ice}, {@code oceanic}…). */
 public static String slug(PlanetColorTheme theme) {
 if (theme == null) return "temperate";
 return theme.name().toLowerCase(Locale.ROOT).replace("_theme", "");
 }

 /** Every shipped identity path — the deterministic datapack pool. */
 public static List<String> allPaths() {
        List<String> out = new ArrayList<>(PlanetColorTheme.VALUES.length * VARIANTS);
 for (PlanetColorTheme t : PlanetColorTheme.VALUES) {
 for (int v = 0; v < VARIANTS; v++) out.add(path(t, v));
 }
 return List.copyOf(out);
 }

 /** True when this identity is part of the shipped pool (no silent fallback allowed). */
 public boolean shipped() {
 return allPaths().contains(path);
 }

 /** The other variant of the same theme (second ambience option of the planet). */
 public String otherVariantPath() {
 return path(theme, (variant + 1) % VARIANTS);
 }

 /** True when the identity's theme can dominate the given thermal band (R23/T-3 invariant). */
 public boolean fitsBand(TemperatureBand band) {
 return theme == null || theme.fitsBand(band);
 }

 /** Debug label, e.g. {@code planet_ice_a[ICE_THEME]}. */
 public String label() {
 return path + "[" + theme + "]";
 }
}
