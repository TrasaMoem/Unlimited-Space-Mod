$csExtract = "C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\csextract\data\creatingspace\creatingspace\rocket_accessible_dimension"
$usExtract = "C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\src\main\resources\data\unlimitedspace\creatingspace\rocket_accessible_dimension"

Write-Host "=== Creating Space built-in rocket_accessible_dimension JSONs ==="
Get-ChildItem -Path $csExtract -Recurse -Filter "*.json" | ForEach-Object {
    Write-Host "--- $($_.FullName.Replace($csExtract + '\', '')) ---"
    Get-Content $_.FullName | ForEach-Object { Write-Host "  $_" }
}

Write-Host "`n=== Unlimited Space rocket_accessible_dimension JSONs ==="
Get-ChildItem -Path $usExtract -Recurse -Filter "*.json" | ForEach-Object {
    Write-Host "--- $($_.FullName.Replace($usExtract + '\', '')) ---"
    Get-Content $_.FullName | ForEach-Object { Write-Host "  $_" }
}
