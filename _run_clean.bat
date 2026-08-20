@echo off
cd /d C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder
start "gradle-clean" cmd /c "gradlew --offline --no-daemon clean test > _clean_test.log 2>&1"
echo launched
