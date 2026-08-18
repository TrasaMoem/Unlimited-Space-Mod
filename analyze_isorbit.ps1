$base = "C:\temp\cs_extract"
$c = "$base\com\rae\creatingspace\content\planets\CSDimensionUtil.class"
$bytes = [System.IO.File]::ReadAllBytes($c)
$str = [System.Text.Encoding]::UTF8.GetString($bytes)

# Find all method descriptors with their access flags
Write-Host "=== Method and field signatures in CSDimensionUtil ==="
$pattern = "[\x20-\x7e]{8,}"
$matches = [regex]::Matches($str, $pattern)
$seen = @{}
foreach ($m in $matches) {
    $val = $m.Value
    if (-not $seen.ContainsKey($val)) {
        $seen[$val] = $true
        # Filter for interesting method/descriptor patterns
        if ($val -match "isOrbit|planetUnder|gravity|orbitedBody|ResourceKey|ResourceLocation|getLevel|arrivalHeight|getPlanets|getTravel" -and $val -notmatch "^[a-z]" -and $val.Length -gt 10) {
            Write-Host "  $val"
        }
    }
}

# Also look specifically at the RocketAccessibleDimension class
Write-Host "`n=== RocketAccessibleDimension class ==="
$rdClass = "$base\com\rae\creatingspace\api\planets\RocketAccessibleDimension.class"
if (Test-Path $rdClass) {
        $rdBytes = [System.IO.File]::ReadAllBytes($rdClass)
    $rdStr = [System.Text.Encoding]::UTF8.GetString($rdBytes)
    $matches2 = [regex]::Matches($rdStr, "[\x20-\x7e]{8,}")
    $seen2 = @{}
    foreach ($m in $matches2) {
        $val = $m.Value
        if (-not $seen2.ContainsKey($val)) {
            $seen2[$val] = $true
            if ($val -match "gravity|orbitedBody|arrivalHeight|adjacent|distance|Record|accessor|get") {
                Write-Host "  $val"
            }
        }
    }
}
