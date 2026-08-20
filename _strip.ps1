$utf8NoBom = New-Object System.Text.UTF8Encoding($false,$true)
$files = @(
  "src/main/java/com/modscreating/unlimitedspace/nav/AdminNav.java",
  "src/main/java/com/modscreating/unlimitedspace/command/GalaxyCommands.java",
  "src/test/java/com/modscreating/unlimitedspace/core/nav/DestinationSurfacePlayabilityRegressionTest.java"
)
foreach($f in $files){
  $c=[IO.File]::ReadAllText($f,[Text.Encoding]::UTF8)
  [IO.File]::WriteAllText($f,$c,$utf8NoBom)
  $b=[IO.File]::ReadAllBytes($f)[0..3]
  "{0}  bytes={1}" -f (Split-Path $f -Leaf), (($b|%{("{0:x2}"-f $_)})-join ' ')
}


