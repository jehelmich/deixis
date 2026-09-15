"""
Finish Home Assistant's onboarding and mint a long-lived access token for Deixis.

Runs *inside* the Home Assistant container, which has aiohttp; the host only needs Docker:

    docker compose exec -T homeassistant python3 - < bootstrap.py

Idempotent: on a fresh instance it creates the owner, on an onboarded one it logs in with
the same credentials. Either way it prints a fresh long-lived token on the last line.
"""
import asyncio
import json
import os
import sys

import aiohttp

BASE = os.environ.get("HA_BASE", "http://localhost:8123")
USERNAME = os.environ.get("HA_USERNAME", "deixis")
PASSWORD = os.environ.get("HA_PASSWORD", "deixis-demo")
NAME = "Deixis"
# Any URL-shaped client id works; HA's indieauth check only insists it is an http(s) URL.
CLIENT_ID = "http://deixis.local/"


async def main() -> None:
    async with aiohttp.ClientSession(BASE) as http:
        steps = await (await http.get("/api/onboarding")).json()
        user_done = next(s["done"] for s in steps if s["step"] == "user")

        if not user_done:
            resp = await http.post("/api/onboarding/users", json={
                "client_id": CLIENT_ID, "name": NAME, "username": USERNAME,
                "password": PASSWORD, "language": "en",
            })
            resp.raise_for_status()
            code = (await resp.json())["auth_code"]
            tokens = await exchange(http, {"grant_type": "authorization_code", "code": code})
            headers = {"Authorization": f"Bearer {tokens['access_token']}"}
            for step in ("core_config", "analytics"):
                (await http.post(f"/api/onboarding/{step}", json={}, headers=headers)).raise_for_status()
            resp = await http.post("/api/onboarding/integration", json={
                "client_id": CLIENT_ID, "redirect_uri": CLIENT_ID,
            }, headers=headers)
            resp.raise_for_status()
            print("Onboarding completed; owner user is", USERNAME, file=sys.stderr)
        else:
            tokens = await login(http)
            print("Already onboarded; logged in as", USERNAME, file=sys.stderr)

        token = await long_lived_token(http, tokens["access_token"])
        print(token)


async def exchange(http: aiohttp.ClientSession, form: dict) -> dict:
    resp = await http.post("/auth/token", data={"client_id": CLIENT_ID, **form})
    resp.raise_for_status()
    return await resp.json()


async def login(http: aiohttp.ClientSession) -> dict:
    """The browser login flow, by hand: start a flow, post credentials, swap the code."""
    resp = await http.post("/auth/login_flow", json={
        "client_id": CLIENT_ID, "handler": ["homeassistant", None], "redirect_uri": CLIENT_ID,
    })
    resp.raise_for_status()
    flow_id = (await resp.json())["flow_id"]
    resp = await http.post(f"/auth/login_flow/{flow_id}", json={
        "client_id": CLIENT_ID, "username": USERNAME, "password": PASSWORD,
    })
    resp.raise_for_status()
    result = await resp.json()
    if result.get("type") != "create_entry":
        sys.exit(f"Login failed: {json.dumps(result)}")
    return await exchange(http, {"grant_type": "authorization_code", "code": result["result"]})


async def long_lived_token(http: aiohttp.ClientSession, access_token: str) -> str:
    """Long-lived tokens are only issued over the websocket API."""
    async with http.ws_connect("/api/websocket") as ws:
        assert (await ws.receive_json())["type"] == "auth_required"
        await ws.send_json({"type": "auth", "access_token": access_token})
        assert (await ws.receive_json())["type"] == "auth_ok"
        await ws.send_json({
            "id": 1, "type": "auth/long_lived_access_token",
            "client_name": "Deixis", "lifespan": 3650,
        })
        reply = await ws.receive_json()
        if not reply.get("success"):
            sys.exit(f"Token creation failed: {json.dumps(reply)}")
        return reply["result"]


asyncio.run(main())
