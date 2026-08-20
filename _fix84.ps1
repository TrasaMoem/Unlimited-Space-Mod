$p='src/main/java/com/modscreating/unlimitedspace/nav/AdminNav.java'
$l=gc $p
for($i=0;$i -lt $l.Count;$i++){
  if($l[$i] -match '^\s+public static NavResult classify\('){
    $l[$i]="    public static NavResult classify(NavResult nav, DestinationCatalog catalog) {"
  }
}
[IO.File]::WriteAllLines($p,$l,[Text.Encoding]::UTF8)
