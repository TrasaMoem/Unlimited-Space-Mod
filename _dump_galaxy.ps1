$p='src/main/java/com/modscreating/unlimitedspace/command/GalaxyCommands.java'
$lines=gc $p
for($i=0;$i -le 40;$i++){ if($i -ge $lines.Count){break}; $l=$lines[$i]-replace '\r$',''; $lead=$l.Length-$l.TrimStart(' ').Length; "L{0:00} lead={1} |{2}" -f ($i+1),$lead,$l }
"--- runNav ---"
for($i=146;$i -le 170;$i++){ if($i -ge $lines.Count){break}; $l=$lines[$i]-replace '\r$',''; $lead=$l.Length-$l.TrimStart(' ').Length; "L{0:00} lead={1} |{2}" -f ($i+1),$lead,$l }
