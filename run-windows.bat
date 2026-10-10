@echo off
rem Starts both services in separate windows. Needs JDK 17+ and Maven on PATH.
start "inventory-service :8081" cmd /k "cd /d %~dp0inventory-service && mvn spring-boot:run"
timeout /t 40 /nobreak
start "forecast-service :8082" cmd /k "cd /d %~dp0forecast-service && mvn spring-boot:run"
echo Open http://localhost:8082 once both windows show "Started ...Application"
