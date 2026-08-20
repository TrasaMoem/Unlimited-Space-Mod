$p='src/main/java/com/modscreating/unlimitedspace/nav/AdminNav.java'
$l=gc $p
for($i=0;$i -lt $l.Count;$i++){
  if($i+1 -lt $l.Count -and $l[$i] -match '^        /\*\*\s*$' -and $l[$i+1] -match '\* R14\.3\.1'){
    $l[$i]='    /**'
  }
}
[IO.File]::WriteAllLines($p,$l,[Text.Encoding]::UTF8)
