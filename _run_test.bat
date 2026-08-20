@echo off
cd /d C:\Users\trasa\OneDrive\Desktop\unlimitedSpaceFolder
start "gradle-test" cmd /c "gradlew --offline --no-daemon test > _test.log 2>&1"
echo launched
