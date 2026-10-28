#!/usr/bin/env python3
"""tv-hero control client for testing the TV's API from a computer (docs/PROTOCOL.md).

    tvhero_client.py <tv-ip> info
    tvhero_client.py <tv-ip> pair              # shows a code on the TV; asks for it and the parent password
    tvhero_client.py <tv-ip> apps
    tvhero_client.py <tv-ip> block <package>
    tvhero_client.py <tv-ip> unblock <package>   # asks for the parent password
    tvhero_client.py <tv-ip> home
    tvhero_client.py <tv-ip> launch <package>
    tvhero_client.py <tv-ip> key home|back
    tvhero_client.py <tv-ip> get <path>         # any signed GET, e.g. /v1/log
    tvhero_client.py <tv-ip> post <path> '<json>'  # any signed POST, e.g. /v1/lock '{"locked":true}'

Pairing state is kept in ~/.tvhero/<tv-ip>.json. Needs the `cryptography` package.
"""
import base64
import getpass
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request

PORT = 8765
STATE_DIR = os.path.expanduser("~/.tvhero")


def pbkdf2(password: str, salt: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha1", password.encode(), salt, 20000, 32)


def open_box(key: bytes, box: bytes) -> bytes:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    return AESGCM(key).decrypt(box[:12], box[12:], None)


def sign(secret: bytes, method: str, target: str, ts: str, body: bytes) -> str:
    msg = f"{method}\n{target}\n{ts}\n{hashlib.sha256(body).hexdigest()}"
    return base64.b64encode(hmac.new(secret, msg.encode(), hashlib.sha256).digest()).decode()


class Tv:
    def __init__(self, host):
        self.host = host
        self.state_file = os.path.join(STATE_DIR, f"{host}.json")
        self.state = json.load(open(self.state_file)) if os.path.exists(self.state_file) else {}

    def call(self, method, path, body=None, signed=True, raw=False):
        data = b"" if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json"}
        if signed:
            if "device_id" not in self.state:
                sys.exit("not paired: run `pair` first")
            ts = str(int(time.time() * 1000))
            secret = base64.b64decode(self.state["secret"])
            headers.update({"X-Device-Id": self.state["device_id"], "X-Timestamp": ts,
                            "X-Signature": sign(secret, method, path, ts, data)})
        req = urllib.request.Request(f"http://{self.host}:{PORT}{path}", data=data if method == "POST" else None,
                                     method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                payload = r.read()
                return payload if raw else json.loads(payload)
        except urllib.error.HTTPError as e:
            return {"status": e.code, **json.loads(e.read() or b"{}")}

    def pair(self, name="computer"):
        start = self.call("POST", "/v1/pair/start", {"device_name": name}, signed=False)
        print("Code is on the TV now." + ("" if start.get("password_set") else " No parent password yet: the one you type becomes it."))
        code = input("Code on TV: ").strip().replace(" ", "")
        pw = getpass.getpass("Parent password: ")
        fin = self.call("POST", "/v1/pair/finish", {"device_name": name, "code": code, "password": pw}, signed=False)
        if "box" not in fin:
            sys.exit(f"pairing failed: {fin}")
        secret = open_box(pbkdf2(f"{code}:{pw}", base64.b64decode(start["salt"])), base64.b64decode(fin["box"]))
        self.state = {"device_id": fin["device_id"], "secret": base64.b64encode(secret).decode()}
        os.makedirs(STATE_DIR, exist_ok=True)
        with open(self.state_file, "w") as f:
            json.dump(self.state, f)
        os.chmod(self.state_file, 0o600)
        print("paired as", fin["device_id"])


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    tv, cmd, args = Tv(sys.argv[1]), sys.argv[2], sys.argv[3:]
    if cmd == "info":
        out = tv.call("GET", "/v1/info", signed=False)
    elif cmd == "pair":
        tv.pair(args[0] if args else "computer")
        return
    elif cmd == "apps":
        out = tv.call("GET", "/v1/apps")
        for a in out.get("apps", []):
            print(f"{a['state']:8} {a['package']:45} {a['label']}")
        return
    elif cmd == "block":
        out = tv.call("POST", "/v1/apps/block", {"package": args[0]})
    elif cmd in ("unblock", "approve"):
        out = tv.call("POST", f"/v1/apps/{cmd}", {"package": args[0], "password": getpass.getpass("Parent password: ")})
    elif cmd == "home":
        out = tv.call("GET", "/v1/home")
    elif cmd == "launch":
        out = tv.call("POST", "/v1/launch", {"package": args[0]})
    elif cmd == "key":
        out = tv.call("POST", "/v1/key", {"action": args[0]})
    elif cmd == "get":
        out = tv.call("GET", args[0])
    elif cmd == "post":            # post <path> '<json>'
        out = tv.call("POST", args[0], json.loads(args[1]) if len(args) > 1 else {})
    else:
        sys.exit(__doc__)
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    main()
