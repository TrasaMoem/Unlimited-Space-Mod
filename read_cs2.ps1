$content = Get-Content "C:\temp\cs_decompiled\com\rae\creatingspace\content\event\CSEventHandler.java" -Raw
Write-Host "=== CSEventHandler.java (lines 73-105) ==="
$lines = $content -split "`n"
for ($i = 72; $i -lt [Math]::Min(105, $lines.Count); $i++) {
    Write-Host "$($i+1): $($lines[$i])"
}
