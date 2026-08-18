$possiblePaths = @(
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Users\trasa\.gradle\jdks",
    "C:\Users\trasa\.gradle\wrapper\dists"
)

foreach ($p in $possiblePaths) {
    if (Test-Path $p) {
        Write-Host "Found: $p"
        Get-ChildItem -Path $p -Recurse -Name "java.exe" -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "  Java: $p\$_"
        }
    }
}

# Also search for any java.exe on the system
$javaExes = Get-ChildItem -Path "C:\" -Recurse -Name "java.exe" -ErrorAction SilentlyContinue | Select-Object -First 10
if ($javaExes) {
    Write-Host "`nFound java.exe at:"
    $javaExes | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "`nNo java.exe found in common locations"
}
