Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = "C:\Users\trasa\.gradle\caches\modules-2\files-2.1\maven.modrinth\creating-space\1.7.18\b979ff298fdf6a61dabeb9325d1adda7b4db8da8\creating-space-1.7.18.jar"
$destPath = "C:\temp\cs_extract"
[System.IO.Compression.ZipFile]::ExtractToDirectory($jarPath, $destPath)
Write-Host "Extraction complete"
Get-ChildItem "$destPath\com\rae\creatingspace\content\rocket\CustomTeleporter.class" -ErrorAction SilentlyContinue
Get-ChildItem "$destPath\com\rae\creatingspace\content\event\CSEventHandler.class" -ErrorAction SilentlyContinue
Get-ChildItem "$destPath\com\rae\creatingspace\content\rocket\RocketContraptionEntity.class" -ErrorAction SilentlyContinue


