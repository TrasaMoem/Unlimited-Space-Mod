$base = "C:\temp\cs_extract"

# Check what fields RocketAccessibleDimension exposes
$rdClass = "$base\com\rae\creatingspace\api\planets\RocketAccessibleDimension.class"
$rdBytes = [System.IO.File]::ReadAllBytes($rdClass)
$rdStr = [System.Text.Encoding]::UTF8.GetString($rdBytes)

Write-Host "=== RocketAccessibleDimension accessor methods ==="
foreach ($line in $rdStr -split '\n') {
    if ($line -match "Lcom/rae/creatingspace/api/planets/RocketAccessibleDimension;") { continue }
    # Look for method names that return float (F), int (I), or ResourceLocation
    $patterns = @(
        "gravity", "arrivalHeight", "orbitedBody", "distanceToOrbitingBody", "adjacentDimensions",
        "isOrbit", "getPlanets", "getTravelMap"
    )
    foreach ($p in $patterns) {
        if ($line -match $p) {
            Write-Host "  MATCH: $line"
        }
    }
}

# Now check what isOrbit does by looking at the constant pool method table
Write-Host "`n=== CSDimensionUtil: isOrbit-related entries ==="
$c = "$base\com\rae\creatingspace\content\planets\CSDimensionUtil.class"
$bytes = [System.IO.File]::ReadAllBytes($c)
$str = [System.Text.Encoding]::UTF8.GetString($bytes)

# Find isOrbit context - what methods are called near it
$isOrbitIdx = $str.IndexOf("isOrbit")
if ($isOrbitIdx -ge 0) {
    $context = $str.Substring($isOrbitIdx - 200, 600)
    Write-Host "Context around isOrbit:"
    Write-Host "  $context"
}

# Find planetUnder context
$planetUnderIdx = $str.IndexOf("planetUnder")
if ($planetUnderIdx -ge 0) {
    $context = $str.Substring($planetUnderIdx - 200, 600)
    Write-Host "`nContext around planetUnder:"
    Write-Host "  $context"
}

# Find getPlanets context
$getPlanetsIdx = $str.IndexOf("getPlanets")
if ($getPlanetsIdx -ge 0) {
    $context = $str.Substring($getPlanetsIdx - 200, 600)
    Write-Host "`nContext around getPlanets:"
    Write-Host "  $context"
}
