@echo off
setlocal
set JAVAP="C:\Program Files\Android\openjdk\jdk-21.0.8\bin\javap.exe"
set CP="C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\csextract"

%JAVAP% -p -c -constants -classpath %CP% "com.rae.creatingspace.content.planets.CustomDimensionEffects$EarthOrbitEffects" > "C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\earth_orbit_javap.txt" 2>&1

%JAVAP% -p -c -constants -classpath %CP% "com.rae.creatingspace.content.planets.CustomDimensionEffects$GenericCelestialOrbitEffect" > "C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder\generic_orbit_javap.txt" 2>&1

echo Done