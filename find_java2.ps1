# Find Java
$javaPaths = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Users\trasa\.gradle\jdks"
)

foreach ($p in $javaPaths) {
    if (Test-Path $p) {
        Get-ChildItem -Path $p -Directory | ForEach-Object {
            $jv = "$($_.FullName)\bin\java.exe"
            if (Test-Path $jv) {
                Write-Host "JAVA: $jv"
            }
        }
    }
}

# Also check gradle wrapper
$gradleProps = "C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\gradle\wrapper\gradle-wrapper.properties"
if (Test-Path $gradleProps) {
    Write-Host "`nGradle wrapper config:"
    Get-Content $gradleProps
}

# Check .gradle config for java location
$gradleHome = "C:\Users\trasa\.gradle"
if (Test-Path $gradleHome) {
    Write-Host "`nGradle home:"
    Get-ChildItem -Path $gradleHome -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "  $($_.Name)"
    }
}
