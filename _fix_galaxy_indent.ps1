$p='src/main/java/com/modscreating/unlimitedspace/command/GalaxyCommands.java'
$l=gc $p
for($i=0;$i -lt $l.Count;$i++){
  if($l[$i] -match '^                LOGGER\.info\("\[unlimitedspace\]\[NAV\] /unlimitedspace nav'){ $l[$i]='        LOGGER.info("[unlimitedspace][NAV] /unlimitedspace nav {} {} {} (worldSeed={})",' }
  if($l[$i] -match '^                if \(src\.getEntity\(\) instanceof ServerPlayer player\) \{'){ $l[$i]='        if (src.getEntity() instanceof ServerPlayer player) {' }
}
[IO.File]::WriteAllLines($p,$l,[Text.Encoding]::UTF8)
