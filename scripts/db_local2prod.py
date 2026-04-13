#!/usr/bin/env python3

"""
db_local2prod.py
Uploads local database to production server

Usages:
    python3 scripts/db_local2prod.py
"""

import requests
import json
import os
import sys
from datetime import datetime
from pathlib import Path

# Configuration
LOCAL_URL = "http://localhost:8585"
PROD_URL = "https://spring.opencodingsociety.com"
PROD_LOGIN_URL = f"{PROD_URL}/login"
PROD_IMPORT_URL = f"{PROD_URL}/api/imports/manual"
LOCAL_LOGIN_URL = f"{LOCAL_URL}/login"
LOCAL_EXPORT_URL = f"{LOCAL_URL}/api/exports/getAll"


# --- Credential loading ---

def load_env_credentials():
    """Load credentials from .env file"""
    env_file = Path(__file__).parent.parent / ".env"

    if not env_file.exists():
        print(f" Error: .env file not found at {env_file}")
        sys.exit(1)

    credentials = {}
    required_keys = ["ADMIN_PASSWORD", "LOCAL_ADMIN_UID", "PROD_ADMIN_UID"]

    with open(env_file, "r") as f:
        for line in f:
            line = line.strip()
            for key in required_keys:
                if line.startswith(f"{key}="):
                    value = line.split("=", 1)[1].strip().strip('"').strip("'")
                    credentials[key] = value

    missing_keys = [key for key in required_keys if key not in credentials]
    if missing_keys:
        print(f" Error: Missing required credentials in .env file: {', '.join(missing_keys)}")
        sys.exit(1)

    return credentials


# --- Local server ---

def check_local_server():
    """Return True if local Spring Boot server is reachable"""
    try:
        response = requests.get(f"{LOCAL_URL}/api/exports/getAll", timeout=5)
        return response.status_code != 404
    except requests.exceptions.RequestException:
        return False


def extract_jwt_from_response(response):
    """
    Extract jwt_java_spring token from Set-Cookie header.
    Spring sets it via ResponseEntity header, which requests may not parse into response.cookies.
    """
    set_cookie = response.headers.get("Set-Cookie", "")
    for part in set_cookie.split(";"):
        part = part.strip()
        if part.startswith("jwt_java_spring="):
            return part.split("=", 1)[1]
    return None


def authenticate_to_local(credentials):
    """Authenticate to local server via JWT; return session with JWT cookie"""
    session = requests.Session()
    auth_data = {
        "uid": credentials["LOCAL_ADMIN_UID"],
        "password": credentials["ADMIN_PASSWORD"],
    }
    auth_resp = session.post(f"{LOCAL_URL}/authenticate", json=auth_data, timeout=10)

    if auth_resp.status_code != 200:
        msg_preview = auth_resp.text[:200] if auth_resp.text else "No response body"
        print(f" Error: Local authentication failed (HTTP {auth_resp.status_code}).")
        print("   Ensure LOCAL_ADMIN_UID and ADMIN_PASSWORD are correct.")
        if msg_preview:
            print(f"   Response preview: {msg_preview}")
        sys.exit(1)

    # Try standard cookie jar first, then fall back to parsing the raw Set-Cookie header
    token = (
        session.cookies.get("jwt_java_spring")
        or auth_resp.cookies.get("jwt_java_spring")
        or extract_jwt_from_response(auth_resp)
    )
    if not token:
        print(" Error: Local authentication succeeded but no JWT token received.")
        print(f"   Response headers: {dict(auth_resp.headers)}")
        sys.exit(1)

    session.cookies.set("jwt_java_spring", token, path="/api")
    return session


def fetch_local_export(session):
    """Fetch export JSON from the authenticated local session"""
    response = session.get(LOCAL_EXPORT_URL, timeout=30)
    if response.status_code == 401:
        print(" Error: Unauthorized (401) when accessing local export endpoint.")
        print("   This endpoint requires login; authentication may have failed.")
        sys.exit(1)
    response.raise_for_status()
    return response.json()


def save_export_to_file(data):
    """Write export data to a timestamped JSON file; return the Path"""
    backups_dir = Path(__file__).parent.parent / "volumes" / "backups"
    backups_dir.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    export_file = backups_dir / f"local_export_{timestamp}.json"

    with open(export_file, "w") as f:
        json.dump(data, f, indent=2)

    return export_file


def export_local_database(credentials):
    """Orchestrate local export: check server, authenticate, fetch, save"""
    print(" Exporting local database...")

    if not check_local_server():
        print(" Error: Local Spring Boot server is not running on port 8585")
        print("   Please start it first: ./mvnw spring-boot:run")
        sys.exit(1)

    try:
        session = authenticate_to_local(credentials)
        data = fetch_local_export(session)
        export_file = save_export_to_file(data)

        print(f" Local database exported to: {export_file}")
        print(f"  Tables exported: {len(data)}")
        print(f"  File size: {export_file.stat().st_size / 1024:.2f} KB")

        return export_file, data

    except requests.exceptions.RequestException as e:
        print(f" Error exporting local database: {e}")
        sys.exit(1)
    except Exception as e:
        print(f" Unexpected error: {e}")
        sys.exit(1)


# --- Production server ---

def authenticate_to_production(credentials):
    """Authenticate to production via JWT; return authenticated session"""
    print("\n Authenticating to production server...")

    auth_url = f"{PROD_URL}/authenticate"
    auth_data = {
        "uid": credentials["PROD_ADMIN_UID"],
        "password": credentials["ADMIN_PASSWORD"],
    }

    try:
        session = requests.Session()
        response = session.post(auth_url, json=auth_data, timeout=10)

        if response.status_code == 200:
            token = (
                session.cookies.get("jwt_java_spring")
                or response.cookies.get("jwt_java_spring")
                or extract_jwt_from_response(response)
            )
            if token:
                session.cookies.set("jwt_java_spring", token, path="/api")
                print(f" Authenticated as '{credentials['PROD_ADMIN_UID']}'")
                return session
            else:
                print(" Authentication succeeded but no JWT token received")
                print(f"   Response body: {response.text[:200]}")
                sys.exit(1)
        else:
            print(f" Authentication failed: HTTP {response.status_code}")
            print(f"   Response body: {response.text[:200]}")
            sys.exit(1)

    except requests.exceptions.HTTPError as e:
        print(f" Authentication failed: HTTP {e.response.status_code}")
        sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f" Error connecting to production server: {e}")
        sys.exit(1)


def upload_to_production(export_file, session):
    """POST the export file to the production import endpoint"""
    print(f"\n Uploading database to production...")
    print(f"   File: {export_file.name}")
    print(f"   Target: {PROD_IMPORT_URL}")

    try:
        with open(export_file, "rb") as f:
            files = {"file": (export_file.name, f, "application/json")}
            response = session.post(
                PROD_IMPORT_URL,
                files=files,
                timeout=1200,
            )
            response.raise_for_status()

        # Check if we were redirected to a login page (auth failure)
        if "/login" in response.url:
            print(" Upload failed: redirected to login page — authentication was rejected")
            sys.exit(1)

        print(f"   Response status: {response.status_code}")

        content_type = response.headers.get("Content-Type", "")
        if "text/html" in content_type:
            body_lower = response.text.lower()
            if "db_error" in response.text or ("error" in body_lower and "success" not in body_lower):
                print(" Import status: ERROR (check production logs)")
                print(f"   Response preview: {response.text[:500]}")
                sys.exit(1)
            else:
                print(" Database uploaded successfully!")
                print("   Import status: SUCCESS")
        else:
            body_lower = response.text.lower()
            if "error" in body_lower:
                print(" Import status: ERROR")
                print(f"   Response: {response.text[:500]}")
                sys.exit(1)
            print(" Database uploaded successfully!")
            print(f"   Response: {response.text[:200]}")

    except requests.exceptions.HTTPError as e:
        print(f" Upload failed: HTTP {e.response.status_code}")
        print(f" Response content: {e.response.text[:500]}")
        sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f" Error uploading to production: {e}")
        sys.exit(1)


# --- Entry point ---

def main():
    print("=" * 60)
    print("DATABASE RESTORE: Local → Production")
    print("=" * 60)
    print()

    credentials = load_env_credentials()
    export_file, _ = export_local_database(credentials)
    session = authenticate_to_production(credentials)
    upload_to_production(export_file, session)

    print()
    print("=" * 60)
    print("RESTORE COMPLETE")
    print("=" * 60)
    print()
    print(f"Local database has been uploaded to production:")
    print(f"  Production URL: {PROD_URL}")
    print(f"  Backup saved: {export_file}")
    print()


if __name__ == "__main__":
    main()
