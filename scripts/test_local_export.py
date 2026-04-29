#!/usr/bin/env python3
"""
test_local_export.py

Safe local export test script.
- Authenticates to local Spring Boot
- Calls /api/exports/getAll
- Saves response JSON to a file

No DB reset, no schema changes, no imports.

Usage:
    python3 scripts/test_local_export.py
    python3 scripts/test_local_export.py --uid toby --password 'your-password'
    python3 scripts/test_local_export.py --base-url http://localhost:8585
"""

import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from http.cookies import SimpleCookie

import requests

PROJECT_ROOT = Path(__file__).parent.parent
DEFAULT_BASE_URL = "http://localhost:8585"
DEFAULT_UID = "toby"


def load_env_value(key):
    """Load a single key from .env (best-effort)."""
    env_file = PROJECT_ROOT / ".env"
    if not env_file.exists():
        return None

    with open(env_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            if k.strip() == key:
                return v.strip().strip('"').strip("'")

    return None


def extract_jwt_from_response(response):
    """Extract jwt_java_spring token from Set-Cookie header."""
    set_cookie = response.headers.get("Set-Cookie", "")
    cookie = SimpleCookie()
    cookie.load(set_cookie)
    morsel = cookie.get("jwt_java_spring")
    return morsel.value if morsel else None


def authenticate_local(session, base_url, uid, password):
    """Authenticate to local server and set JWT cookie in session."""
    auth_url = f"{base_url}/authenticate"
    payload = {"uid": uid, "password": password}

    # Don't follow redirects during auth to avoid infinite loops
    resp = session.post(auth_url, json=payload, timeout=15, allow_redirects=False)
    
    # Accept 200 or 302 (redirect after successful auth is normal)
    if resp.status_code not in (200, 302):
        preview = (resp.text or "")[:250]
        raise RuntimeError(
            f"Local auth failed: HTTP {resp.status_code}. "
            f"Response preview: {preview}"
        )

    # Extract token from response or cookies
    token = (
        session.cookies.get("jwt_java_spring")
        or resp.cookies.get("jwt_java_spring")
        or extract_jwt_from_response(resp)
    )
    
    if not token:
        raise RuntimeError("Auth succeeded but jwt_java_spring cookie was not found.")

    session.cookies.set("jwt_java_spring", token)
    session.headers["Cookie"] = f"jwt_java_spring={token}"
    return token


def fetch_export(session, base_url):
    """Fetch full export payload from local export endpoint."""
    url = f"{base_url}/api/exports/getAll"
    resp = session.get(url, timeout=60, allow_redirects=False)

    if resp.status_code == 401:
        raise RuntimeError("Unauthorized (401) on /api/exports/getAll. Check admin credentials.")

    if resp.status_code in (301, 302, 303):
        redirect_url = resp.headers.get("Location")
        if redirect_url:
            print(f"  Following redirect to: {redirect_url}")
            resp = session.get(redirect_url, timeout=60, allow_redirects=False)
    
    resp.raise_for_status()

    content_type = resp.headers.get("Content-Type", "")
    if "application/json" not in content_type.lower():
        preview = (resp.text or "")[:250]
        raise RuntimeError(
            f"Expected JSON from /api/exports/getAll but got {content_type or 'unknown content type'}. "
            f"Response preview: {preview}"
        )

    return resp.json()


def default_output_path():
    backups_dir = PROJECT_ROOT / "volumes" / "backups"
    backups_dir.mkdir(parents=True, exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    return backups_dir / f"local_export_test_{ts}.json"


def parse_args():
    parser = argparse.ArgumentParser(description="Test local export API and save JSON output")
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--uid", default=os.getenv("LOCAL_ADMIN_UID") or load_env_value("LOCAL_ADMIN_UID") or DEFAULT_UID)
    parser.add_argument("--password", default=os.getenv("ADMIN_PASSWORD") or load_env_value("ADMIN_PASSWORD"))
    parser.add_argument("--out", default=None, help="Output JSON file path")
    return parser.parse_args()


def main():
    args = parse_args()

    if not args.password:
        print("Error: Missing password. Set ADMIN_PASSWORD in .env or pass --password.", file=sys.stderr)
        sys.exit(1)

    out_file = Path(args.out) if args.out else default_output_path()

    print("Testing local export API")
    print(f"Base URL: {args.base_url}")
    print(f"UID: {args.uid}")

    try:
        session = requests.Session()
        # Set reasonable defaults
        session.headers.update({
            "User-Agent": "test-local-export/1.0",
            "Accept": "application/json",
        })
        
        authenticate_local(session, args.base_url.rstrip("/"), args.uid, args.password)
        data = fetch_export(session, args.base_url.rstrip("/"))

        with open(out_file, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

        table_count = len(data) if isinstance(data, dict) else 0
        print("Export succeeded")
        print(f"Tables exported: {table_count}")
        print(f"Saved file: {out_file}")

        if isinstance(data, dict):
            print("Table summary:")
            for name in sorted(data.keys()):
                rows = data.get(name)
                row_count = len(rows) if isinstance(rows, list) else 0
                print(f"  - {name}: {row_count}")

    except requests.RequestException as e:
        print(f"HTTP error: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
