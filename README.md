# MediChain: essential medicine stock-out prediction and redistribution

A drug stock monitoring system for district health facilities: stock position, demand forecast,
stock-out risk, expiry watch, inter-facility transfer advice and indent (procurement) advice.

- **Two Spring Boot microservices** talking over REST, each with its own database.
- **Web client** with sign-in and ten screens, in plain HTML, CSS and JavaScript (no libraries).
- **Simulated data** for 53 facilities in 10 Tamil Nadu districts, with a dispensing feed that keeps
  stock, the ledger and today's figures moving while the system runs.

This is a proof of concept with simulated data. It is not an official government system.

## Run it

### Google Colab
Open `MediChain_Colab.ipynb` in Colab and run the cells top to bottom. It installs Java 17 and
Maven, builds and starts both services, and gives a link to the dashboard.

### Your own machine
Needs JDK 17+ and Maven 3.9+.

```
cd inventory-service && mvn spring-boot:run      # terminal 1, port 8081 (start this first)
cd forecast-service  && mvn spring-boot:run      # terminal 2, port 8082
```

Open **http://localhost:8082** and sign in. On Windows `run-windows.bat` starts both.

### Without Maven
`lite/medichain-lite.jar` runs the same controllers, services and web client with only Java
(`java -jar lite/medichain-lite.jar`, or double-click `lite/start.bat`). It replaces Spring's web
server and the H2 database with the JDK's own server and in-memory tables, so use it to try the
system, not to meet a Spring Boot requirement. See `lite/README.txt`.

## Sign-in

| User ID | Password | Role | Access |
|---|---|---|---|
| `admin` | `Admin@123` | State administrator | every district, master data |
| `dp.vellore` | `Vellore@123` | District pharmacist | Vellore only |
| `dp.<district>` | `<District>@123` | District pharmacist | that district only |

Choose the district on the sign-in page. Accounts are created on first sign-in and kept in the
`app_user` table with salted PBKDF2 password hashes. A sign-in returns a session token that the
browser sends as `Authorization: Bearer ...`; `AuthInterceptor` checks it on every `/api` call and
enforces the district limit. These fixed passwords are for the PoC only.

## Architecture

```
Browser (index.html, app.css, app.js)
      |  REST + bearer token
forecast-service :8082  ------ REST (RestClient) ------>  inventory-service :8081
  auth: users, sessions, access check                       facility and drug masters
  query parser                                              batches with mfg. and expiry dates
  demand forecast, stock-out risk, expiry                   daily consumption, disease case trends
  transfer advice, indent advice, analytics                 stock ledger, transfers
  gateway to inventory for the web client                   simulated dispensing feed (@Scheduled)
  H2: recommendation, app_user                              H2: 7 tables
```

End-to-end flow:

1. **Query**: a selection on Stock Status, or a typed question on the Dashboard (`QueryParser`).
2. **Retrieve**: `InventoryClient` calls `GET /api/snapshot` on inventory-service.
3. **Analyse**: `DemandForecaster` and `AnalysisEngine` forecast demand, simulate stock, score risk.
4. **Recommend**: transfer advice is saved in the `recommendation` table as PROPOSED.
5. **Explain**: every advice carries its basis: trend, days of stock, expiry dates, distance.
6. **Act**: approving calls `POST /api/transfers` on inventory-service, which moves the batches
   and writes both sides to the stock ledger. The next analysis shows the new position.

## Screens

| Group | Screen | Contents |
|---|---|---|
| Monitoring | Dashboard | Status counts, query, stock status by drug, projected stock-outs, live stock movement, fill-rate charts |
| | Stock Status | One drug across the district: SOH, AMC, MOS, forecast, days of stock, status, risk; per-facility batches, consumption, forecast, risk basis; transfer advice |
| | Forecast & Risk | Every stock line, plus the expiry watch |
| Stores | Stores & Ledger | Batches at a facility, receipt entry, issue entry, latest ledger entries |
| | Transfers & Indents | Transfer advice to approve or reject, indent advice, transfer register |
| Masters | Facilities, Drug Master | Lists and add forms (state administrator only) |
| Reports | Analytics | Fill rate, shortages, wastage, outcome of transfer advice |
| | Reports | CSV downloads |
| Administration | Settings | Session, service status, analysis rules |

### Where the vocabulary comes from
The screens follow the conventions of working public-health logistics systems rather than a
generic dashboard:

- **OpenLMIS** stock status and stock imbalance reports: stock on hand, average monthly
  consumption (AMC), months of stock (MOS), and the categories stocked out, understocked,
  adequately stocked, overstocked; a district view counting facilities in each category.
- **e-Aushadhi / DVDMS** style systems used by Indian states: indent, issue, receipt, re-order
  level, expiry alerts, inter-store drug transfer.

Thresholds here are set for facility-level stock in days (understocked under 15 days, overstocked
over 90), because the task is short-horizon stock-out prediction. Nothing is copied from those
systems; only the terms and the table-first layout are borrowed.

## Live figures

`LiveFeedSimulator` in inventory-service issues stock every 5 seconds at each stock line's recent
consumption rate times `medichain.live-feed.speed` (default 12). Each issue goes through the same
service method as a manual issue: it reduces the earliest-expiring batch, updates today's
consumption and writes a ledger line. On start-up the previous 45 minutes are filled in.

The web client reads `GET /api/live` every 5 seconds (today's totals, issues per minute, latest
ledger lines) and re-runs the full analysis every 30 seconds. Settings in
`inventory-service/src/main/resources/application.properties`:

```
medichain.live-feed.enabled=true
medichain.live-feed.speed=12
medichain.live-feed.interval-ms=5000
```

## The analysis (forecast-service, package `engine`)

| Step | Method |
|---|---|
| Demand forecast | Weekday pattern removed, then damped-trend Holt exponential smoothing. Alpha and beta fitted per series by grid search on one-step-ahead squared error. |
| Seasonal disease signal | Last 7 days of district cases against the 3 weeks before; the ratio lifts the forecast (capped at +40%). |
| Accuracy | Back-test: the last 14 days are hidden, forecast and compared (WAPE). |
| Days of stock | Day-by-day simulation, earliest-expiring batch first, expired batches written off. |
| Stock-out probability | Chance that 14-day demand exceeds usable stock, demand treated as normal around the forecast. |
| Stock status | Stocked out, understocked (< 15 days), adequately stocked, overstocked (> 90 days), from days of stock. |
| Expiry watch | Units still on the shelf on a batch's expiry date in the simulation. |
| Transfer advice | Deficit = 30 days of forecast demand minus stock. Surplus = stock beyond 45 days of own demand, or stock that would expire. Most urgent deficit first; prefer one donor that covers the need, then stock that would otherwise expire, then the nearest (haversine). Batches under 21 days shelf life are not moved. 120 km limit. |
| Indent advice | Re-order level = lead-time demand + safety stock (1.65 x daily error x sqrt(lead time)); indent up to lead time + 30 days, in packs of 100. |

No language model is called. Every figure comes from these calculations.

## REST API

inventory-service (8081), internal:

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/districts`, `/api/facilities?district=`, `/api/medicines` | master data |
| POST | `/api/facilities`, `/api/medicines` | add master data |
| GET, POST | `/api/batches` | batches at a facility; receive stock |
| GET, POST | `/api/consumption` | consumption history; record an issue |
| GET | `/api/snapshot?district=&days=` | everything for a district in one call |
| GET | `/api/live?district=&minutes=` | today's movement from the stock ledger |
| GET, POST | `/api/transfers` | list or execute a transfer |

forecast-service (8082), used by the web client; all but the first three need a session token:

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/regions` | state and districts for the sign-in page |
| POST | `/api/auth/login` | body `{"username","password","district"}`, returns the session |
| GET | `/api/health` | service status |
| GET, POST | `/api/auth/me`, `/api/auth/logout` | current session; sign out |
| GET | `/api/analysis?district=&medicineId=&horizonDays=` | stock status, risk, forecast, advice |
| POST | `/api/query` | typed question, body `{"query": "...", "district": "Vellore"}` |
| GET | `/api/recommendations` | transfer register |
| POST | `/api/recommendations/{id}/accept`, `/reject` | decide a transfer |
| GET | `/api/analytics?district=` | fill rate, shortages, wastage, outcomes, daily series |
| GET | `/api/live?district=` | forwarded to inventory-service |
| GET, POST | `/api/inventory/...` | facilities, drugs, batches, issues, forwarded to inventory-service |

Database consoles: http://localhost:8081/h2-console (`jdbc:h2:mem:inventorydb`) and
http://localhost:8082/h2-console (`jdbc:h2:mem:forecastdb`), user `sa`, empty password. Both
databases are in memory and reload the demo data on every start.

## Demo data

`DemoDataFactory` builds 53 facilities across Vellore, Tiruvannamalai, Chennai, Coimbatore,
Madurai, Tiruchirappalli, Salem, Tirunelveli, Thanjavur and Kancheepuram; 10 drugs named after
India's National List of Essential Medicines 2022; 120 days of consumption shaped by dengue,
diarrhoea, respiratory infection and malaria curves; batch-level stock; a few expired batches and
past stock-outs. Facility names are real places with approximate coordinates. All quantities are
simulated.

Vellore is scripted: Government Hospital Vellore is about 4 days from running out of paracetamol
and PHC Katpadi, 12 km away, holds surplus with a batch expiring in 48 days. Other districts get
a fixed, unscripted mix. To add a district, add `new Facility(...)` lines to
`DemoDataFactory.facilities()`, or use Facilities > Add facility as the state administrator.

## Demo script (4 minutes)

1. Sign in as `admin` for Vellore. Point at the status strip and the live stock movement panel.
2. Run the query on the Dashboard: query, retrieve, analyse, recommend, explain.
3. Stock Status, Paracetamol: Government Hospital Vellore is Critical. Open Batches, Consumption,
   Forecast, Risk basis.
4. Approve the transfer from PHC Katpadi. The table reruns; the ledger shows transfer out and in.
5. Stores & Ledger: save a receipt or an issue and watch the balance change.
6. Analytics and Reports: fill rate by day, outcome of advice, CSV download.
7. Sign out, sign in as `dp.salem`: one district only, no master-data forms.
8. Show the two H2 consoles.
