from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from pathlib import Path
import json, secrets, socket, hmac

ROOT = Path(__file__).resolve().parent
CONFIG = ROOT / "mobile_bridge_config.json"
LOCAL_NICO = "http://127.0.0.1:8765"
PORT = 8766


def load_config():
    if CONFIG.exists():
        try:
            data = json.loads(CONFIG.read_text(encoding="utf-8"))
            if data.get("token"):
                return data
        except Exception:
            pass
    data = {"token": secrets.token_urlsafe(24), "port": PORT}
    CONFIG.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return data


CFG = load_config()
TOKEN = str(CFG["token"])
PORT = int(CFG.get("port", PORT))


def local_request(method, path, payload=None):
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = Request(LOCAL_NICO + path, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    try:
        with urlopen(req, timeout=10) as r:
            raw = r.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(raw or f"Nico OS HTTP {e.code}")
    except URLError as e:
        raise RuntimeError("Nico OS local n'est pas joignable sur le PC") from e


def all_tasks():
    data = local_request("GET", "/api/tasks")
    return data if isinstance(data, list) else []


def next_task():
    open_tasks = [x for x in all_tasks() if not bool(x.get("done"))]
    def key(t):
        due = (t.get("due") or "").strip()
        return (1 if not due else 0, due or "9999-12-31", int(t.get("id") or 0))
    open_tasks.sort(key=key)
    return open_tasks[0] if open_tasks else None


def task_by_id(task_id):
    for t in all_tasks():
        if int(t.get("id") or -1) == int(task_id):
            return t
    return None


def backup_local():
    try:
        with urlopen(LOCAL_NICO + "/api/backup", timeout=15) as r:
            r.read(1)
    except Exception:
        pass


def save_task(t):
    payload = {
        "id": int(t["id"]) if t.get("id") is not None else None,
        "title": t.get("title") or "Tâche",
        "project_id": t.get("project_id"),
        "priority": t.get("priority") or "Moyenne",
        "due": t.get("due") or "",
        "duration": int(t.get("duration") or 30),
        "done": bool(t.get("done")),
        "notes": t.get("notes") or "",
    }
    if payload["id"] is None:
        payload.pop("id")
    return local_request("POST", "/api/crud/task", payload)


def lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


class Handler(BaseHTTPRequestHandler):
    server_version = "NicoAlertLocal/1.0"

    def log_message(self, fmt, *args):
        return

    def json_out(self, obj, code=200):
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def auth_ok(self):
        supplied = self.headers.get("X-Nico-Key", "")
        return bool(supplied) and hmac.compare_digest(supplied, TOKEN)

    def body(self):
        n = int(self.headers.get("Content-Length", "0"))
        if not n:
            return {}
        return json.loads(self.rfile.read(n).decode("utf-8"))

    def do_GET(self):
        if self.path == "/health":
            if not self.auth_ok():
                return self.json_out({"error": "Clé invalide"}, 401)
            try:
                local_request("GET", "/api/core")
                return self.json_out({"ok": True, "mode": "LOCAL", "pc": socket.gethostname()})
            except Exception as e:
                return self.json_out({"ok": False, "error": str(e)}, 503)

        if self.path == "/next":
            if not self.auth_ok():
                return self.json_out({"error": "Clé invalide"}, 401)
            try:
                t = next_task()
                if not t:
                    return self.json_out({"ok": True, "empty": True})
                return self.json_out({"ok": True, "empty": False, "task": t})
            except Exception as e:
                return self.json_out({"error": str(e)}, 503)

        return self.json_out({"error": "Route inconnue"}, 404)

    def do_POST(self):
        if not self.auth_ok():
            return self.json_out({"error": "Clé invalide"}, 401)
        try:
            b = self.body()
            if self.path == "/done":
                tid = int(b.get("id") or 0)
                t = task_by_id(tid)
                if not t:
                    return self.json_out({"error": "Tâche introuvable"}, 404)
                backup_local()
                t["done"] = True
                save_task(t)
                pid = t.get("project_id")
                remaining = sum(1 for x in all_tasks() if not bool(x.get("done")) and x.get("project_id") == pid)
                return self.json_out({"ok": True, "project_id": pid, "priority": t.get("priority"), "project_remaining": remaining})

            if self.path == "/create":
                title = str(b.get("title") or "").strip()
                if not title:
                    return self.json_out({"error": "Titre requis"}, 400)
                backup_local()
                t = {
                    "title": title,
                    "project_id": b.get("project_id"),
                    "priority": b.get("priority") or "Moyenne",
                    "due": b.get("due") or "",
                    "duration": int(b.get("duration") or 30),
                    "done": False,
                    "notes": b.get("notes") or "",
                }
                result = save_task(t)
                return self.json_out({"ok": True, **(result if isinstance(result, dict) else {})})

            return self.json_out({"error": "Route inconnue"}, 404)
        except Exception as e:
            return self.json_out({"error": str(e)}, 500)


if __name__ == "__main__":
    ip = lan_ip()
    print("=" * 62)
    print(" NICO ALERT <-> NICO OS LOCAL")
    print("=" * 62)
    print(f"Adresse à saisir dans Nico Alert : http://{ip}:{PORT}")
    print(f"Clé de liaison                 : {TOKEN}")
    print()
    print("Nico OS doit être lancé sur ce PC.")
    print("Laisse cette fenêtre ouverte pendant l'utilisation mobile.")
    print("=" * 62)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
