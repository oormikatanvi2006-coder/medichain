MediChain Lite
==============

The MediChain dashboard (front end) and its REST API (back end) in one file.
Needs only Java 17 or newer. No Maven, no database, no internet.

RUN
  Windows:  double-click start.bat
  Any OS:   java -jar medichain-lite.jar

  Your browser opens http://localhost:8082 after a few seconds.
  Sign in:  admin / Admin@123  (district Vellore), or dp.vellore / Vellore@123.
  Keep the black window open while you use the app. Close it (or press Ctrl+C) to stop.
  If port 8082 is busy:  java -jar medichain-lite.jar 8090   then open http://localhost:8090

WHAT IS INSIDE
  Front end  static/index.html, app.css, app.js: the ten-screen dashboard, plain HTML/CSS/JavaScript.
  Back end   the same Java classes as the Spring Boot project: inventory and forecast controllers,
             services, demand forecasting, stock-out risk, expiry risk, redistribution, procurement.
  REST API   same paths as the Spring Boot build, e.g.
             /api/auth/login, /api/analysis, /api/analytics, /api/live (session token required)

HOW IT DIFFERS FROM THE SPRING BOOT PROJECT
  - Web server: the JDK's built-in HTTP server instead of Spring Boot.
  - Storage: in-memory tables instead of the H2 database. Data resets every time you start it.
  - One process: the forecast code calls the inventory code directly instead of over HTTP.
  - The dispensing feed runs on a timer thread instead of Spring's @Scheduled.
  The jar carries small stand-in classes for the few Spring types the shared code refers to.
  For the hackathon requirement (Spring Boot, two microservices, database) use the full project
  in the inventory-service and forecast-service folders.

REBUILD FROM SOURCE (optional, needs the JDK only)
  build-lite.bat (Windows) or build-lite.sh
