$f = 'C:/Users/trasa/OneDrive/Desktop/unlimitedSpaceFolder/_dd_impl.txt'
$lines = [System.IO.File]::ReadAllLines($f)
$sb = New-Object System.Text.StringBuilder
for ($i = 0; $i -lt $lines.Length; $i++) {
    $l = $lines[$i]
    if ($l -match 'loadDynamicDimension|createDynamicDimension|createDynamicLevel|dynamicDimensionExists|getLevel|registerLevel|dynamicdimensions|exists|return null|new ServerLevel|getStorageSource') {
        $sb.AppendLine(($i + 1).ToString() + ': ' + $l)
    }
}
[System.IO.File]::WriteAllText('C:/Users/trasa/OneDrive/Desktop/unlimitedSpaceFolder/_dd_scan.txt', $sb.ToString(), [System.Text.Encoding]::UTF8)
Write-Host 'W'