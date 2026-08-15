package com.modscreating.unlimitedspace.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture test: the pure-domain {@code core} package must NOT depend on
 * Minecraft/NeoForge. It scans every {@code .java} source of the core package and
 * asserts none imports {@code net.minecraft.*}, {@code net.neoforged.*} or
 * {@code com.mojang.*} (Mojang serialisation types are Minecraft-adjacent).
 */
class CoreArchitectureTest {

    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "net.minecraft.",
            "net.neoforged.",
            "com.mojang.");

    @Test
    void coreSourcesNeverImportMinecraftOrNeoForge() throws IOException {
        Path coreRoot = Paths.get("src/main/java/com/modscreating/unlimitedspace/core");
        assertTrue(Files.isDirectory(coreRoot), "core source dir not found: " + coreRoot.toAbsolutePath());

        List<Path> offending;
        try (Stream<Path> walk = Files.walk(coreRoot)) {
            offending = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> importsForbidden(p))
                    .collect(Collectors.toList());
        }
        assertTrue(offending.isEmpty(),
                "core package must not import Minecraft/NeoForge, found in: "
                        + offending.stream().map(Path::toString).collect(Collectors.joining(", ")));
    }

    private static boolean importsForbidden(Path javaFile) {
        try {
            String content = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
            return FORBIDDEN_PREFIXES.stream().anyMatch(p -> content.contains("import " + p));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
