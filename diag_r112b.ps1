Set-StrictMode -Off
$root = "C:/Users/trasa/OneDrive/Desktop/unlimitedSpaceFolder"
Set-Location $root

$jar = "C:/Users/trasa/OneDrive/Desktop/unlimitedSpaceFolder/csextract/../csextract" # placeholder
# locate jar
$csjar = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches" -Recurse -File -Filter 'creating-space-1.7.18.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
Write-Output ("CSJAR=" + $csjar.FullName)

if ($csjar) {
    $zipPath = "$root/csextract.zip"
    Copy-Item -LiteralPath $csjar.FullName -Destination $zipPath -Force
    if (Test-Path "csextract") { Remove-Item -Recurse -Force "csextract" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, "$root/csextract")
    Write-Output "EXTRACTED_VIA_ZIPFILE"
}

"=== CustomTeleporter + CSEventHandler class paths ==="
$base = Resolve-Path "csextract"
Get-ChildItem -LiteralPath $base -Recurse -Filter '*.class' |
    Where-Object { $_.Name -eq 'CustomTeleporter.class' -or $_.Name -eq 'CSEventHandler.class' } |
    ForEach-Object { "FOUND: " + $_.FullName.Replace($base.Path + "\",'') }

"=== class files mentioning arrival/transition/destination ==="
Get-ChildItem -LiteralPath $base -Recurse -Filter '*.class' |
    ForEach-Object {
        $name = $_.Name
        if ($name -match 'Teleport|Arrival|Platform|Spawn|Transition|Travel|Dimension|Respawn|Fall|Rocket|Column|Surface|Column') { $name }
    } | Sort-Object -Unique | Select-Object -First 60

"=== strings in ALL class files (platform/bedrock/stone/floor/landing/spawn/deepslate/basalt) ==="
$allClasses = Get-ChildItem -LiteralPath $base -Recurse -Filter '*.class'
$needles = @('platform','landing','spawn','floor','bedrock','smooth_basalt','minecraft:stone','minecraft:deepslate','minecraft:basalt','minecraft:gravel','minecraft:obsidian','minecraft:smooth_stone','minecraft:stone','minecraft:smooth_basalt','minecraft:blackstone','minecraft:packed_ice','minecraft:water','minecraft:gravel','minecraft:dirt','minecraft:cobble','minecraft:andesite','minecraft:diorite','minecraft:granite','minecraft:tuff','minecraft:calcite','minecraft:dripstone','minecraft:amethyst')
$seen = @{}
foreach ($c in $allClasses) {
    $bytes = [System.IO.File]::ReadAllBytes($c.FullName)
    try { $s = [System.Text.Encoding]::UTF8.GetString($bytes) } catch { continue }
    foreach ($n in $needles) {
        if ($s.IndexOf($n) -ge 0) {
            if (-not $seen.ContainsKey($n)) { $seen[$n] = @() }
            $seen[$n] += $c.Name.Replace('.class','')
        }
    }
}
$seen.GetEnumerator() | Sort-Object Name | ForEach-Object { "{0}: {1}" -f $_.Key, ($_.Value | Select-Object -Unique) -join ', ' }