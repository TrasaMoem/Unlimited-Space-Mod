package com.modscreating.unlimitedspace.cs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R14.6 virtual SERVER_DATA datapack that publishes the minecraft:overworld routing override
 * for the creatingspace:rocket_accessible_dimension registry through the OFFICIAL datapack
 * registry lifecycle (no travelMap mutation, no reflection, no mixin).
 *
 * <p>The registry is a WORLDGEN-layer datapack registry loaded at WorldStem creation
 * (R14.5.4), i.e. BEFORE the world seed is decoded. Therefore this pack can only publish
 * seed-independent entries; the only one it publishes is the minecraft:overworld override.
 * All procedural bodies are published seed-aware at runtime by {@link ProceduralCsRuntime}
 * (ServerStartedEvent) from the actual procedural domain state (Galaxy/Planet/Moon/...),
 * preserving the single seed-derived source of truth.
 *
 * <p>Everything is lazy: the entry list is computed once, JSON bytes are generated on demand.
 * Creating metadata here NEVER creates a ServerLevel.
 */
public final class ProceduralCsPack {

    /** Datapack id (also used as the pack-repository id and KnownPack id). */
    public static final String PACK_ID = "unlimitedspace:procedural_cs";

    /** Registry folder for creatingspace:rocket_accessible_dimension (elementsDirPath). */
    public static final String REGISTRY_DIR = "creatingspace/rocket_accessible_dimension";

    /** Datapack format for Minecraft 1.21.1 data packs. */
    public static final int DATA_PACK_FORMAT = 48;

    private static final String MCMETA_JSON = "{\"pack\":{\"pack_format\":" + DATA_PACK_FORMAT
            + ",\"description\":\"Unlimited Space procedural Creating Space metadata\"}}";

    private static final String OVERWORLD_NAMESPACE = "minecraft";
    private static final String OVERWORLD_FILE = "overworld";

    private ProceduralCsPack() {
    }

    /**
     * Register the pack finder on the MOD bus (AddPackFindersEvent is an IModBusEvent
     * fired inside ResourcePackLoader.populatePackRepository, i.e. before WorldStem
     * load - exactly the right time for a WORLDGEN-layer registry pack).
     */
    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(onLoad -> {
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(PACK_ID, Component.literal("Unlimited Space Procedural CS"),
                            PackSource.BUILT_IN,
                            Optional.of(new KnownPack("unlimitedspace", PACK_ID, "1.0.0"))),
                    new Supplier(),
                    PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) {
                onLoad.accept(pack);
            }
        });
    }

    /** Pack.ResourcesSupplier - returns the same lazy resources for primary and full opens. */
    public static final class Supplier implements Pack.ResourcesSupplier {
        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new Resources(location);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new Resources(location);
        }
    }
    /** PackResources for the virtual datapack. */
    public static final class Resources implements PackResources {

        private final PackLocationInfo location;
        private volatile Map<String, String> jsonCache;

        Resources(PackLocationInfo location) {
            this.location = location;
        }

        /** Lazily build registry-key -> json for every generated entry (+ overworld override). */
        private Map<String, String> entries() {
            Map<String, String> cache = jsonCache;
            if (cache == null) {
                synchronized (this) {
                    cache = jsonCache;
                    if (cache == null) {
                        Map<String, String> map = new LinkedHashMap<>(1);
                        map.put(OVERWORLD_NAMESPACE + ":" + OVERWORLD_FILE, overworldJson());
                        cache = map;
                        jsonCache = cache;
                    }
                }
            }
            return cache;
        }

        /**
         * Minimal overworld routing override for the frozen registry: the launch dimension keeps the
         * CS earth_orbit edge. The seed-aware full routing edges (every procedural body) are added by
         * the runtime bridge at ServerStartedEvent (seed known).
         */
        private static String overworldJson() {
            return "{\n"
                    + "  \"adjacentDimensions\": {\n"
                    + "    \"creatingspace:earth_orbit\": { \"deltaV\": 1500 }\n"
                    + "  },\n"
                    + "  \"arrivalHeight\": 200,\n"
                    + "  \"gravity\": 9.81,\n"
                    + "  \"orbitedBody\": \"sun\",\n"
                    + "  \"distanceToOrbitingBody\": 1500\n"
                    + "}";
        }

        /** Map a registry key (unlimitedspace:planet/...) to its file path under the registry dir. */
        private static String filePathOf(String key) {
            int colon = key.indexOf(":");
            return REGISTRY_DIR + "/" + key.substring(colon + 1) + ".json";
        }
        @Override
        public IoSupplier<InputStream> getRootResource(String... elements) {
            if (elements.length == 1 && "pack.mcmeta".equals(elements[0])) {
                return () -> new ByteArrayInputStream(MCMETA_JSON.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            if (type != PackType.SERVER_DATA) {
                return null;
            }
            String namespace = location.getNamespace();
            String path = location.getPath();
            if (!path.startsWith(REGISTRY_DIR + "/") || !path.endsWith(".json")) {
                return null;
            }
            String entryPath = path.substring(REGISTRY_DIR.length() + 1, path.length() - ".json".length());
            String key = namespace + ":" + entryPath;
            String json = entries().get(key);
            if (json == null) {
                return null;
            }
            return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != PackType.SERVER_DATA || !path.equals(REGISTRY_DIR)) {
                return;
            }
            Map<String, String> all = entries();
            if (namespace.equals("unlimitedspace")) {
                for (Map.Entry<String, String> e : all.entrySet()) {
                    String key = e.getKey();
                    if (key.startsWith("unlimitedspace:")) {
                        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                                "unlimitedspace", filePathOf(key));
                        String json = e.getValue();
                        output.accept(file, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            } else if (namespace.equals("minecraft")) {
                String json = all.get("minecraft:overworld");
                if (json != null) {
                    ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                            "minecraft", REGISTRY_DIR + "/overworld.json");
                    String finalJson = json;
                    output.accept(file, () -> new ByteArrayInputStream(finalJson.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            if (type == PackType.SERVER_DATA) {
                return Set.of("minecraft");
            }
            return Set.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            if (serializer.getMetadataSectionName().equals("pack")) {
                return (T) new PackMetadataSection(Component.literal("Unlimited Space procedural CS"), DATA_PACK_FORMAT);
            }
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return location;
        }

        @Override
        public void close() {
        }
    }
}