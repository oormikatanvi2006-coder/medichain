@echo off
rem Rebuilds medichain-lite.jar from source. Needs only a JDK (17+), no Maven.
cd /d %~dp0
if exist build rmdir /s /q build
mkdir build\static
dir /s /b standins\*.java src\*.java ..\inventory-service\src\main\java\*.java ..\forecast-service\src\main\java\*.java > build\sources.txt
javac --release 17 -encoding UTF-8 -nowarn -d build @build\sources.txt || goto :fail
copy /y ..\forecast-service\src\main\resources\static\* build\static\ > nul
echo Main-Class: com.medichain.lite.LiteServer> build\manifest.txt
jar --create --file medichain-lite.jar --manifest build\manifest.txt -C build com -C build org -C build jakarta -C build static || goto :fail
rmdir /s /q build
echo Built medichain-lite.jar
goto :eof
:fail
echo Build failed.
pause
