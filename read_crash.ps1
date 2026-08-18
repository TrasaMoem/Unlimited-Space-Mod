$all = Get-Content .\crash_copy.txt
Write-Host "=== TOTAL LINES: $($all.Count) ==="
for ($i = 0; $i -lt $all.Count; $i++) {
    $ln = $i + 1
    $line = $all[$i]
    if ($ln -ge 24 -and $ln -le 107) {
                Write-Host "L$ln : $line"
    }
}
