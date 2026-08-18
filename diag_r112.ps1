Set-StrictMode -Off
$root = "C:/Users/trasa/OneDrive/Desktop/unlimitedSpaceFolder"
Set-Location $root

# 1) Locate + extract Creating Space jar
$jar = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches" -Recurse -File -Filter 'creating-space-1.7.18.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
"CS_JAR=" + $jar.FullName

if ($jar) {
    if (Test-Path "csextract") { Remove-Item -Recurse -Force "csextract" }
    try { Expand-Archive -LiteralPath $jar.FullName -DestinationPath "csextract" -Force; "EXTRACTED_OK" }
    catch { "EXTRACT_FAILED: $_" }
}

# 2) Region files for asteroid dimension (try both normalized names)
"=== region files ==="
$found = $null
foreach ($candidate in @("run/saves/Phase 11_1 test 2/dimensions/unlimitedspace/asteroid/system_0000_asteroid_00/region",
                          "run/saves/Phase 11.1 test 2/dimensions/unlimitedspace/asteroid/system_0000_asteroid_00/region")) {
    if (Test-Path $candidate) { $found = $candidate; break }
}
if ($found) {
    Get-ChildItem $found -Filter "r.*.mca" | Select-Object Name, Length
} else {
    "ASTEROID_REGION_NOT_FOUND; listing any saves dirs:"
    Get-ChildItem "run/saves" -Directory | Select-Object -First 10 Name
}

# 3) Relevant CS class files (by name patterns)
"=== matching class files (by name) ==="
$csextract = Resolve-Path "csextract"
if (Test-Path $csextract) {
    Get-ChildItem -LiteralPath $csextract -Recurse -Filter "*.class" |
        Where-Object { $_.Name -match "Teleport|Arrival|Platform|Spawn|Transition|Rocket|Travel|Dimension|Column|Surface|Fall|Respawn|CustomTele" } |
        ForEach-Object { $_.FullName.Replace($csextract.Path + "\","") }
}