#!/usr/bin/env python3
"""Publish the signed Bazaar flavor through Pishkhan v1. Never print/store the API secret."""
import argparse
import os
import pathlib
import sys

import requests

BASE = "https://api.pishkhan.cafebazaar.ir/v1/apps/releases"

def call(session, method, url, **kwargs):
    response = session.request(method, url, timeout=kwargs.pop("timeout", 90), **kwargs)
    if response.status_code not in (200, 201):
        raise RuntimeError(f"Pishkhan {method} {url.rsplit('/', 2)[-2]} HTTP {response.status_code}: {response.text[:500]}")
    return response.json() if response.content else {}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--auto-publish", action="store_true")
    args = parser.parse_args()
    key = os.getenv("BAZAAR_API_SECRET", "").strip()
    if not key:
        raise SystemExit("BAZAAR_API_SECRET missing (configure GitHub Actions repository secret)")
    if not args.apk.is_file():
        raise SystemExit("signed APK not found")
    session = requests.Session()
    session.headers["CAFEBAZAAR-PISHKHAN-API-SECRET"] = key
    check = session.get(BASE + "/last-uncommitted/", timeout=45)
    if check.status_code == 404 or (check.status_code == 200 and not check.json()):
        call(session, "POST", BASE + "/")
    elif check.status_code != 200:
        raise RuntimeError(f"Cannot check existing draft: HTTP {check.status_code}")
    with args.apk.open("rb") as fh:
        call(session, "POST", BASE + "/upload/",
             files={"apk": (args.apk.name, fh, "application/vnd.android.package-archive")},
             data={"architecture": "all"}, timeout=300)
    commit = {
        "changelog_fa": "اصلاح باز شدن دوربین، کنترل نمایش ابزار عیب‌یابی و بهبود بروزرسانی اختصاصی بازار",
        "changelog_en": "Camera capture, administrator-only diagnostics UI and store update fixes",
        "developer_note": "CAMERA permission is requested only on user action in Profile Photo, Bicycle Photo, and Ride/Event Album via the Camera button. ACTION_IMAGE_CAPTURE launches the device camera and FileProvider safely returns a captured image. Gallery selection is separate. No background camera capture. Bazaar APK updates only via CafeBazaar store.",
        "staged_rollout_percentage": 100,
        "auto_publish": bool(args.auto_publish),
    }
    call(session, "POST", BASE + "/commit/", json=commit)
    print("CafeBazaar Pishkhan release submitted successfully.")

if __name__ == "__main__":
    main()
