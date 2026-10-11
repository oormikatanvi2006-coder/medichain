#!/bin/sh
# Works out where inventory-service is, keeps it awake, then starts forecast-service.
#   INVENTORY_URL  : full address of inventory-service (wins if set)
#   INVENTORY_HOST : Render service name; the public address is https://<name>.onrender.com
if [ -z "$INVENTORY_URL" ] && [ -n "$INVENTORY_HOST" ]; then
  INVENTORY_URL="https://$INVENTORY_HOST.onrender.com"
fi
INVENTORY_URL="${INVENTORY_URL:-http://localhost:8081}"
export INVENTORY_URL
echo "forecast-service will call inventory-service at $INVENTORY_URL"

# Free hosting puts an idle service to sleep. Call inventory-service at start and
# every 4 minutes so it is awake whenever this service is.
( while true; do
    curl -fsS -m 180 -o /dev/null "$INVENTORY_URL/api/districts" || echo "inventory-service not ready yet"
    sleep 240
  done ) &

exec java $JAVA_OPTS -jar /app/app.jar
