gc '_c2.log' | ? {$_ -match 'error:|BOM|illegal character|BUILD SUCCESS|BUILD FAILED|FAILURE:|cannot find|symbol|AdminNav\.java|GalaxyCommands\.java|PlanetarySurface'} | select -Last 40
"=== tail ==="
gc '_c2.log' | select -Last 20

