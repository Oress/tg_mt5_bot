"""Read-only validation against a real worker. Passwords are prompted, never logged."""
import argparse
import asyncio
import getpass
import json
import os
import uuid

from websockets.asyncio.client import connect


async def request(websocket, action, params=None):
    request_id = str(uuid.uuid4())
    await websocket.send(json.dumps({"id": request_id, "action": action, "params": params or {}}))
    async with asyncio.timeout(45):
        while True:
            response = json.loads(await websocket.recv())
            if response.get("id") != request_id:
                continue
            if not response.get("ok"):
                raise RuntimeError(response.get("error"))
            return response["result"]


async def validate(websocket, account, server, password):
    params = {"account_number": account, "server": server, "password": password}
    info = await request(websocket, "login", params)
    if (info["login"], info["server"]) != (account, server):
        raise RuntimeError("Terminal account identity does not match the request")
    await request(websocket, "positions-get-all", params)
    await request(websocket, "subscribe", params)
    async with asyncio.timeout(15):
        while True:
            event = json.loads(await websocket.recv())
            if (event.get("type") == "heartbeat" and event.get("account_number") == account
                    and event.get("server") == server):
                break
    async with asyncio.timeout(15):
        while True:
            status = await request(websocket, "ping")
            if status["connected"] and status["trading_ready"] and status["feed_ready"]:
                break
            await asyncio.sleep(0.25)
    print(f"Account {account}: login, reads, EA heartbeat and trading-permission checks passed")
    await request(websocket, "unsubscribe")


async def main(args):
    token = os.environ["MT5_GATEWAY_TOKEN"]
    first = getpass.getpass(f"Password for demo account {args.account}: ")
    second = getpass.getpass(f"Password for demo account {args.second_account}: ") if args.second_account else None
    async with connect(args.url, additional_headers={"Authorization": "Bearer " + token},
                       proxy=None, max_size=4 * 1024 * 1024) as websocket:
        await validate(websocket, args.account, args.server, first)
        if args.second_account:
            await validate(websocket, args.second_account, args.second_server or args.server, second)
            await validate(websocket, args.account, args.server, first)
    print("Read-only smoke test passed. No orders were submitted.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="ws://127.0.0.1:8010/ws")
    parser.add_argument("--account", type=int, required=True)
    parser.add_argument("--server", required=True)
    parser.add_argument("--second-account", type=int)
    parser.add_argument("--second-server")
    asyncio.run(main(parser.parse_args()))
