$base = "C:\temp\cs_extract"
$c = "$base\com\rae\creatingspace\content\planets\CSDimensionUtil.class"

if (Test-Path $c) {
    Write-Host "=== STRINGS in CSDimensionUtil ==="
    $bytes = [System.IO.File]::ReadAllBytes($c)
    $str = [System.Text.Encoding]::UTF8.GetString($bytes)
    $ascii = "[\x20-\x7e]{4,}"
    $matches = [regex]::Matches($str, $ascii)
    $seen = @{}
    foreach ($m in $matches) {
        $val = $m.Value
        if (-not $seen.ContainsKey($val)) {
            $seen[$val] = $true
            Write-Host "  $val"
        }
    }
    
    Write-Host "`n=== CONSTANT POOL (method/field refs) ==="
    # Try to extract UTF-8 constant pool entries
    $hex = [System.BitConverter]::ToString($bytes)
    Write-Host "File size: $($bytes.Length) bytes"
} else {
    Write-Host "NOT FOUND: CSDimensionUtil.class"
    # Search for it
    Get-ChildItem $base -Recurse -Filter "CSDimensionUtil.class" | ForEach-Object {
        Write-Host "Found: $($_.FullName)"
    }
}

# Also check RocketContraptionEntity for destination/arrival logic
$c2 = "$base\com\rae\creatingspace\content\rocket\RocketContraptionEntity.class"
if (Test-Path $c2) {
    Write-Host "`n=== STRINGS in RocketContraptionEntity (destination related) ==="
    $bytes2 = [System.IO.File]::ReadAllBytes($c2)
    $str2 = [System.Text.Encoding]::UTF8.GetString($bytes2)
    $matches2 = [regex]::Matches($str2, "[\x20-\x7e]{4,}")
    $seen2 = @{}
    foreach ($m in $matches2) {
        $val = $m.Value
        if (-not $seen2.ContainsKey($val)) {
            $seen2[$val] = $true
            if ($val -match "destination|arrival|orbit|planet|dimension|getLevel|transition|getTransition|changeDim|planetUnder|isOrbit|getPlanets") {
                Write-Host "  >>> $val"
            }
        }
    }
}

