# A real Home Assistant to develop against

Everything here runs Home Assistant in Docker with its built-in `demo` integration: six
lights, two switches, temperature, humidity and power sensors and more, all fake, all
controllable from HA's own dashboard. Nothing about it is specific to this app — it is a
stock Home Assistant with one line of configuration.

```sh
./up.sh
```

pulls the image, starts it on port 8123, completes onboarding with a `deixis` /
`deixis-demo` owner account, mints a long-lived access token and prints the base URL and
token to type into the app's Settings tab. The token is also written to `.token`
(git-ignored). Re-running `up.sh` on an existing instance just logs in and mints a fresh
token.

Open <http://localhost:8123> for the dashboard: flip *Decorative Lights* there and the marker
bound to it changes in AR; flip it in AR and the dashboard follows within a poll.

| File | What |
|---|---|
| `docker-compose.yml` | the container, port 8123, `config/` mounted as `/config` |
| `config/configuration.yaml` | `demo:` plus a name and units |
| `bootstrap.py` | onboarding + token, run *inside* the container so the host needs nothing but Docker |
| `up.sh` | the one command |

The same setup runs in CI: [`home-assistant.yml`](../.github/workflows/home-assistant.yml)
boots it on every push and runs `HomeAssistantLiveTest` against it.

```sh
docker compose down -v      # stop and forget everything, including the owner account
```

## Caveats

- HA's demo switches have no power readings, so the plug card shows "—" for power, current
  and energy. The 2019 TP-Link plug the thesis used put those on the switch entity itself;
  the mapping still reads them if present.
- The phone must reach this machine: same Wi-Fi, and the base URL is the machine's LAN
  address (printed by `up.sh`), not `localhost`.
