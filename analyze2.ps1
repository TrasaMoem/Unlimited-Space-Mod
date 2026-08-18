$base = "C:\temp\cs_extract"

Write-Host "=== ALL STRINGS in RocketAccessibleDimension ==="
$rdClass = "$base\com\rae\creatingspace\api\planets\RocketAccessibleDimension.class"
if (Test-Path $rdClass) {
    $rdBytes = [System.IO.File]::ReadAllBytes($rdClass)
    $rdStr = [System.Text.Encoding]::UTF8.GetString($rdBytes)
    $rdMatches = [regex]::Matches($rdStr, "[\x20-\x7e]{4,}")
    $rdSeen = @{}
    foreach ($m in $rdMatches) {
        $val = $m.Value
        if (-not $rdSeen.ContainsKey($val)) {
            $rdSeen[$val] = $true
            Write-Host "  RAD: $val"
        }
    }
}

Write-Host "`n=== CSEventHandler key strings ==="
$chClass = "$base\com\rae\creatingspace\content\event\CSEventHandler.class"
$chBytes = [System.IO.File]::ReadAllBytes($chClass)
$chStr = [System.Text.Encoding]::UTF8.GetString($chBytes)
$chMatches = [regex]::Matches($chStr, "[\x20-\x7e]{3,}")
$chSeen = @{}
foreach ($m in $chMatches) {
    $val = $m.Value
    if (-not $chSeen.ContainsKey($val)) {
        $chSeen[$val] = $true
        if ($val -match "isOrbit|planetUnder|getLevel|getTransition|changeDimension|minY|getY|orbitedBody|gravity|arrival|getInitial|ServerLevel|ResourceKey|ResourceLocation|Dimension|level") {
            Write-Host "  CH: $val"
        }
    }
}
