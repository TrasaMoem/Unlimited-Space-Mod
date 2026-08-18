$java8 = "C:\Program Files\Java\jre-1.8\bin\java.exe"
$cfrUrl = "https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar"
$cfrJar = "C:\temp\cfr.jar"

Write-Host "Downloading CFR decompiler..."
try {
    Invoke-WebRequest -Uri $cfrUrl -OutFile $cfrJar -ErrorAction Stop
    Write-Host "CFR downloaded to $cfrJar"
} catch {
    Write-Host "Download failed: $_"
    # Try alternate URL
    try {
        Invoke-WebRequest -Uri "https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar" -OutFile $cfrJar -ErrorAction Stop
        Write-Host "CFR downloaded (alternate) to $cfrJar"
    } catch {
        Write-Host "Alternate download also failed: $_"
    }
}

$java8 = "C:\Program Files\Java\jre-1.8\bin\java.exe"
$cfrJar = "C:\temp\cfr.jar"
$outDir = "C:\temp\cs_decompiled"

# Decompile key classes
$classes = @(
    "C:\temp\cs_extract\com\rae\creatingspace\content\planets\CSDimensionUtil.class",
    "C:\temp\cs_extract\com\rae\creatingspace\content\event\CSEventHandler.class",
    "C:\temp\cs_extract\com\rae\creatingspace\content\rocket\CustomTeleporter.class",
    "C:\temp\cs_extract\com\rae\creatingspace\api\planets\RocketAccessibleDimension.class"
)

foreach ($c in $classes) {
    Write-Host "Decompiling $([System.IO.Path]::GetFileNameWithoutExtension($c))..."
    & $java8 -jar $cfrJar $c --outputdir $outDir 2>$null
}

Write-Host "`n=== Decompiled files ==="
Get-ChildItem -Path $outDir -Recurse -Filter "*.java" | ForEach-Object {
    Write-Host "  $($_.FullName.Replace($outDir + '\', ''))"
}


