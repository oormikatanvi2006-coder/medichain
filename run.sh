#!/bin/bash
# Starts both services. Needs JDK 17+ and Maven on PATH.
cd "$(dirname "$0")"
(cd inventory-service && mvn -q spring-boot:run) &
sleep 40
(cd forecast-service && mvn -q spring-boot:run) &
echo "Open http://localhost:8082 once both services have started"
wait
