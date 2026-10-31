#!/usr/bin/env python3
"""tv-hero control client (protocol 2) for using the TV's API from a computer. See docs/PROTOCOL.md.

    tvhero_client.py <tv-ip> info
    tvhero_client.py <tv-ip> pair               # compare the code with the TV, press Allow there, then the parent password
    tvhero_client.py <tv-ip> status
    tvhero_client.py <tv-ip> volume [<level>|+|-|mute|unmute]
    tvhero_client.py <tv-ip> sleep | wake
    tvhero_client.py <tv-ip> apps
    tvhero_client.py <tv-ip> block <package>
    tvhero_client.py <tv-ip> unblock <package>  # asks for the parent password
    tvhero_client.py <tv-ip> launch <package>
    tvhero_client.py <tv-ip> key home|back|...
    tvhero_client.py <tv-ip> open <https://link>            (2.10, home Wi-Fi only)
    tvhero_client.py <tv-ip> search [--youtube] <words>
    tvhero_client.py <tv-ip> type <text>                     (types it into the TV's focused box, then Enter)
    tvhero_client.py <tv-ip> devices
    tvhero_client.py <tv-ip> forget             # removes this computer from the TV (no password needed for yourself)
    tvhero_client.py <tv-ip> get <path>         # any GET over the encrypted channel, e.g. /v1/log
    tvhero_client.py <tv-ip> post <path> '<json>'
    tvhero_client.py --self-test                # checks the pairing maths against the Java test vectors

How it's protected: commands go over mutual TLS to port 8766. This computer has its own EC P-256 key
(in ~/.tvhero/, readable only by you); the TV's certificate is pinned at pairing and nothing else is
accepted. Needs the `cryptography` package.
"""
import base64
import datetime
import getpass
import hashlib
import json
import os
import socket
import ssl
import struct
import sys
import tempfile
import time
import urllib.error
import urllib.request

PAIRING_PORT = 8765
SECURE_PORT = 8766
NONCE_BYTES = 32
STATE_DIR = os.path.expanduser("~/.tvhero")


# ---------- pairing maths (same as protocol/Pairing.java) ----------

def _h(label, *parts):
    d = b""
    for p in (label.encode(),) + parts:
        d += struct.pack(">I", len(p)) + p
    return hashlib.sha256(d).digest()


def commitment(tv_cert, phone_cert, tv_nonce):
    return _h("tvhero-commit-v1", tv_cert, phone_cert, tv_nonce)


def code(phone_cert, tv_cert, phone_nonce, tv_nonce):
    h = _h("tvhero-sas-v1", phone_cert, tv_cert, phone_nonce, tv_nonce)
    return "%06d" % (int.from_bytes(h[:4], "big") % 1_000_000)


def fingerprint(der):
    return hashlib.sha256(der).hexdigest()


def self_test():
    tv, ph, np_, nt = b"tv-cert", b"phone-cert", bytes([1]) * 32, bytes([2]) * 32
    assert commitment(tv, ph, nt).hex() == "659fd388420840209fabe277ddbf358a44ad050367704cd77fc78285cbcd99a6"
    assert code(ph, tv, np_, nt) == "569378"
    print("pairing vectors match protocol/PairingTest.java")


# ---------- this computer's identity ----------

def identity():
    """EC P-256 key and self-signed certificate, made once and kept in ~/.tvhero/ (mode 600)."""
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID

    os.makedirs(STATE_DIR, mode=0o700, exist_ok=True)
    key_path, cert_path = os.path.join(STATE_DIR, "key.pem"), os.path.join(STATE_DIR, "cert.pem")
    if not os.path.exists(key_path):
        key = ec.generate_private_key(ec.SECP256R1())
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "tv-hero client " + socket.gethostname())])
        now = datetime.datetime.now(datetime.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
                .serial_number(x509.random_serial_number())
                .not_valid_before(now - datetime.timedelta(days=1))
                .not_valid_after(now + datetime.timedelta(days=3650))
                .sign(key, hashes.SHA256()))
        fd = os.open(key_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "wb") as f:
            f.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        with open(cert_path, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))
    cert = x509.load_pem_x509_certificate(open(cert_path, "rb").read())
    return key_path, cert_path, cert.public_bytes(serialization.Encoding.DER)


class Tv:
    def __init__(self, host):
        self.host = host
        self.state_file = os.path.join(STATE_DIR, f"{host}.json")
        self.state = json.load(open(self.state_file)) if os.path.exists(self.state_file) else {}

    def save(self):
        os.makedirs(STATE_DIR, mode=0o700, exist_ok=True)
        with open(self.state_file, "w") as f:
            json.dump(self.state, f, indent=1)

    # Plain HTTP to the pairing port: info and pairing only.
    def open_call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(f"http://{self.host}:{PAIRING_PORT}{path}", data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            sys.exit(f"{e.code}: {e.read().decode(errors='replace')}")

    # Mutual TLS to the secure port, TV certificate pinned by fingerprint.
    def call(self, method, path, body=None, raw=False):
        if "tv_fingerprint" not in self.state:
            sys.exit("not paired: run `pair` first")
        key_path, cert_path, _ = identity()
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE        # no CAs: the pin below is the check
        ctx.load_cert_chain(cert_path, key_path)
        data = b"" if body is None else json.dumps(body).encode()
        with socket.create_connection((self.host, SECURE_PORT), timeout=10) as raw_sock:
            with ctx.wrap_socket(raw_sock) as s:
                if fingerprint(s.getpeercert(binary_form=True)) != self.state["tv_fingerprint"]:
                    sys.exit("the TV's certificate doesn't match the one paired: refusing to talk to it")
                head = (f"{method} {path} HTTP/1.1\r\nHost: {self.host}\r\nContent-Type: application/json\r\n"
                        f"Content-Length: {len(data)}\r\nConnection: close\r\n\r\n").encode()
                s.sendall(head + data)
                resp = b""
                while True:
                    chunk = s.recv(65536)
                    if not chunk:
                        break
                    resp += chunk
                tls = s.version()
        head, _, payload = resp.partition(b"\r\n\r\n")
        status = int(head.split(b" ")[1])
        if raw:
            return status, payload, tls
        out = json.loads(payload) if payload else {}
        if status >= 400:
            sys.exit(f"{status}: {out.get('error', out)}")
        return out

    def pair(self, name):
        _, _, my_cert = identity()
        start = self.open_call("POST", "/v1/pair/start", {"device_name": name, "cert": base64.b64encode(my_cert).decode()})
        tv_cert = base64.b64decode(start["tv_cert"])
        my_nonce = os.urandom(NONCE_BYTES)
        tv_nonce = base64.b64decode(self.open_call("POST", "/v1/pair/reveal",
                                                   {"pair_id": start["pair_id"], "nonce": base64.b64encode(my_nonce).decode()})["nonce"])
        if not commitment(tv_cert, my_cert, tv_nonce) == base64.b64decode(start["commitment"]):
            sys.exit("the TV's answer doesn't match its commitment: someone may be in the middle. Not pairing.")
        c = code(my_cert, tv_cert, my_nonce, tv_nonce)
        print(f"\n  Code: {c[:3]} {c[3:]}\n\nCheck the TV shows the same code, then choose Allow on the TV.")
        while True:
            time.sleep(1)
            st = self.open_call("GET", f"/v1/pair/status?pair_id={start['pair_id']}")["state"]
            if st == "allowed":
                break
            if st in ("denied", "expired", "none"):
                sys.exit(f"pairing {st}")
        self.state = {"tv_fingerprint": fingerprint(tv_cert)}
        self.save()
        if start.get("password_set"):
            pw = getpass.getpass("Parent password: ")
        else:
            pw = getpass.getpass("Choose a parent password (at least 6 characters): ")
            if getpass.getpass("Again: ") != pw:
                sys.exit("passwords differ")
        r = self.call("POST", "/v1/pair/complete", {"password": pw})
        self.state["device_id"] = r["device_id"]
        self.save()
        print(f"Paired as {r['name']} ({r['device_id']}).")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    tv, cmd, args = Tv(sys.argv[1]), sys.argv[2], sys.argv[3:]
    out = None
    if cmd == "info":
        out = tv.open_call("GET", "/v1/info")
    elif cmd == "pair":
        tv.pair(args[0] if args else socket.gethostname()[:40])
    elif cmd == "status":
        out = tv.call("GET", "/v1/status")
    elif cmd == "volume":
        a = args[0] if args else ""
        body = ({"step": 1} if a == "+" else {"step": -1} if a == "-" else {"mute": True} if a == "mute"
                else {"mute": False} if a == "unmute" else {"level": int(a)} if a else None)
        out = tv.call("POST", "/v1/volume", body) if body else tv.call("GET", "/v1/volume")
    elif cmd in ("sleep", "wake"):
        out = tv.call("POST", "/v1/power", {"action": cmd})
    elif cmd == "apps":
        for a in tv.call("GET", "/v1/apps")["apps"]:
            print(f"{a['state']:8} {a['package']:45} {a['label']}")
    elif cmd == "block":
        out = tv.call("POST", "/v1/apps/block", {"package": args[0]})
    elif cmd in ("unblock", "approve"):
        out = tv.call("POST", f"/v1/apps/{cmd}", {"package": args[0], "password": getpass.getpass("Parent password: ")})
    elif cmd == "launch":
        out = tv.call("POST", "/v1/launch", {"package": args[0]})
    elif cmd == "key":
        out = tv.call("POST", "/v1/key", {"action": args[0]})
    elif cmd == "devices":
        out = tv.call("GET", "/v1/devices")
    elif cmd == "forget":
        out = tv.call("POST", "/v1/devices/remove", {})
        os.remove(tv.state_file)
    elif cmd == "open":   # 2.10, home Wi-Fi only
        out = tv.call("POST", "/v1/open", {"url": args[0]})
    elif cmd == "search":
        yt = args[:1] == ["--youtube"]
        out = tv.call("POST", "/v1/search", {"query": " ".join(args[1:] if yt else args), "where": "youtube" if yt else "web"})
    elif cmd == "type":
        tv.call("POST", "/v1/type/start", {})
        out = tv.call("POST", "/v1/type", {"text": " ".join(args), "enter": True})
        tv.call("POST", "/v1/type/stop", {})
    elif cmd == "get":
        out = tv.call("GET", args[0])
    elif cmd == "post":
        out = tv.call("POST", args[0], json.loads(args[1]) if len(args) > 1 else {})
    else:
        sys.exit(__doc__)
    if out is not None:
        print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main()
