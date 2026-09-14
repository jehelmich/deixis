# Connecting to Home Assistant

The app talks to Home Assistant's [REST API](https://developers.home-assistant.io/docs/api/rest/)
over your local network; nothing goes through the cloud.

1. In the HA web UI open your profile (bottom-left) → **Security** → **Long-lived access
   tokens** → *Create token*. Copy it — HA shows it once.
2. In Coeus, **Settings** → *Home Assistant*. Enter the base URL exactly as you reach HA in
   a browser, e.g. `http://homeassistant.local:8123` or `http://192.168.1.20:8123`, and
   paste the token.
3. **Test connection** should answer `API running. N usable device(s) found.` Then **Save**.

The phone and the HA instance must be on the same network (or a VPN into it). Plain `http`
is allowed by the manifest (`usesCleartextTraffic`) because that is how most home
installations run; use `https` if yours has it.

## What shows up

| HA entity | Coeus device | Card shows |
|---|---|---|
| `switch.*` | Smart plug | on/off switch; power, current, voltage and energy **if** the switch entity carries `current_power_w`, `current_a`, `voltage`, `today_energy_kwh`, `total_energy_kwh` attributes (2019-era TP-Link integration) |
| `light.*` | Light | on/off switch; brightness slider if the entity reports `brightness` |
| `sensor.*` with `device_class: temperature` or `humidity` | Climate sensor | the reading |
| anything else | — | not listed |

Commands map to services: toggle → `{domain}/toggle`; brightness → `light/turn_on` with
`brightness_pct`, or `light/turn_off` at zero.

## Token storage

The token is kept in the app's private DataStore, unencrypted at rest. That is acceptable
for a demo on your own phone; a production app would seal it with the Android Keystore.
Revoke the token from the same HA page when you are done.
