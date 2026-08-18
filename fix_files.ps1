$javaPath = "C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\src\main\java\com\modscreating\unlimitedspace\UnlimitedSpace.java"
$java = Get-Content $javaPath -Raw

# Fix the asteroid diagnostics section - replace the whole block from AsteroidCluster to the first LOGGER.info
$oldBlock = "            // --- R11 asteroid cluster diagnostics (read-only; no travel/world mutation) ---"
$idx = $java.IndexOf($oldBlock)
if ($idx -lt 0) { Write-Host "Could not find block marker"; exit }

# Find the end of the block (the second LOGGER.info call, ending with the new csGravity/csIsOrbit/csOrbitedBody line)
$endMarker = "astGravity, astIsOrbitCS, astOrbitedBody);"
$endIdx = $java.IndexOf($endMarker, $idx)
if ($endIdx -lt 0) { Write-Host "Could not find end marker"; exit }
$endIdx = $endIdx + $endMarker.Length

$oldCode = $java.Substring($idx, $endIdx - $idx)

$newCode = @"
            // --- R11 asteroid cluster diagnostics (read-only; no travel/world mutation) ---
            try {
                AsteroidCluster cluster = Galaxy.from(worldSeed)
                        .getStarSystem(StarSystemId.of(0)).asteroid(0);
                AsteroidGenerationProfile prof = cluster.profile();
                AsteroidFieldGeometry geom = new AsteroidFieldGeometry(cluster.seed().value(), prof);
                ResourceLocation astRl = AsteroidWorldBinding.location(cluster.id());
                boolean dimRegistered = event.getServer().registryAccess()
                        .registryOrThrow(Registries.LEVEL_STEM).containsKey(astRl);
                boolean csReg = registry.containsKey(astRl);
                ServerLevel astLevel = event.getServer().getLevel(AsteroidWorldBinding.level(cluster.id()));
                String astGen = astLevel == null ? "NULL"
                        : astLevel.getChunkSource().getGenerator().getClass().getSimpleName();
                int[] spawn = geom.spawnAt();
                RocketAccessibleDimension astEntry = csReg ? registry.get(astRl) : null;
                float astGravity = astEntry == null ? Float.NaN : astEntry.gravity();
                boolean astIsOrbitCS = astEntry != null && astGravity == 0.0f;
                ResourceLocation astOrbitedBody = astEntry == null ? null : astEntry.orbitedBody();
                LOGGER.info("[unlimitedspace] Asteroid R11: id={} seed={} shape={} density={} "
                                + "asteroidCount={} sizeRange={}-{} dominantOre={} primaryMaterial={}",
                        cluster.id().code(), cluster.seed().value(), prof.shapePattern(),
                        String.format("%.2f", prof.density()), prof.asteroidCount(),
                        String.format("%.1f", prof.sizeRangeMin()), String.format("%.1f", prof.sizeRangeMax()),
                        prof.dominantOre(), prof.material().primary().blockId());
                LOGGER.info("[unlimitedspace] Asteroid R11: dimensionRegistered={} serverLevelResolved={} "
                                + "generator={} csDestinationRegistered={} destRl={} arrival(geom)=({},{},{}) "
                                + "csGravity={} csIsOrbit={} csOrbitedBody={}",
                        dimRegistered, astLevel != null, astGen, csReg, astRl, spawn[0], spawn[1], spawn[2],
                        astGravity, astIsOrbitCS, astOrbitedBody);
"@

$java = $java.Replace($oldCode, $newCode)
Set-Content -Path $javaPath -Value $java -Encoding UTF8
Write-Host "Java file fixed. Verifying..."

# Verify the fix
$verify = Get-Content $javaPath | Select-String -Pattern "csGravity|csIsOrbit|csOrbitedBody|boolean csReg"
$verify | ForEach-Object { Write-Host "  L$($_.LineNumber): $($_.Line)" }


