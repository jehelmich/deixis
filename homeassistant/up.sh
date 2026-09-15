#!/bin/sh
# Start the demo Home Assistant, finish its onboarding and print what to type into the app.
set -eu
cd "$(dirname "$0")"

docker compose up -d
printf 'Waiting for Home Assistant'
until docker compose exec -T homeassistant curl -sf http://localhost:8123/api/onboarding >/dev/null 2>&1; do
  printf '.'; sleep 3
done
echo

token=$(docker compose exec -T homeassistant python3 - < bootstrap.py)
printf '%s' "$token" > .token
chmod 600 .token

lan_ip=$(ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || echo '<this machine>')
cat <<MSG

Home Assistant is up.
  Dashboard : http://localhost:8123   (user: deixis / deixis-demo)
  For the app: base URL  http://$lan_ip:8123
               token     $token
The token is also in homeassistant/.token (git-ignored).
MSG
