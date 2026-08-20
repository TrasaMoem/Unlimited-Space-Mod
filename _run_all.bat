@echo off
cd /d C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder
powershell -NoProfile -File "_fix_import.ps1"
start "gradle-test2" cmd /c "gradlew --offline --no-daemon test > _test2.log 2>&1"
echo launched
