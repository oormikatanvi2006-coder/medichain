#!/bin/bash
# Rebuilds medichain-lite.jar from source. Needs only a JDK (17+), no Maven.
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/static
javac --release 17 -encoding UTF-8 -nowarn -d build $(find standins src ../inventory-service/src/main/java ../forecast-service/src/main/java -name '*.java')
cp -r ../forecast-service/src/main/resources/static/. build/static/
printf 'Main-Class: com.medichain.lite.LiteServer\n' > build/manifest.txt
jar --create --file medichain-lite.jar --manifest build/manifest.txt -C build com -C build org -C build jakarta -C build static
rm -rf build
echo "Built medichain-lite.jar"
