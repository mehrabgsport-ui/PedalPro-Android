#!/usr/bin/env python3
"""Publish one signed, version-checked PedalPro APK via Cafe Bazaar Pishkhan API.

Never store a Pishkhan token in source code or inside the Android APK.
This script is intended to run only in the guarded release GitHub Action.
"""
import argparse
import os
from pathlib import Path
import sys
import requests

BASE = "https://api.pishkhan.cafebazaar.ir/v1"

def check_response(response, operation):
    if response.status_code != 200:
        # Do not emit request headers, access tokens, or raw response bodies.
        raise RuntimeError(f"Pishkhan {operation} failed: HTTP {response.status_code}")
    try:
        return response.json()
    except ValueError:
        raise RuntimeError(f"Pishkhan {operation}: invalid JSON response") from None

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--rollout", default=10, type=int)
    args = parser.parse_args()

    token = os.environ.get("BAZAAR_PISHKHAN_API_SECRET", "").strip()
    if not token:
        raise RuntimeError("Missing GitHub Actions secret BAZAAR_PISHKHAN_API_SECRET")
    if args.version_code != 82:
        raise RuntimeError("RC38 release accepts only versionCode 82")
    if not (1 <= args.rollout <= 100):
        raise RuntimeError("Invalid rollout percentage")
    if not args.apk.is_file() or args.apk.stat().st_size < 1024 * 1024:
        raise RuntimeError("Signed Bazaar APK missing or unexpectedly small")

    session = requests.Session()
    session.headers.update({"CAFEBAZAAR-PISHKHAN-API-SECRET": token})
    def url(route):
        return f"{BASE}/apps/releases/{route}"

    # Existing draft may be reused. Authentication/server failures must not
    # be mistaken for a missing draft.
    result = session.get(url("last-uncommitted/"), timeout=30)
    if result.status_code == 404:
        check_response(session.post(url(""), timeout=45), "create-draft")
    else:
        check_response(result, "last-uncommitted")
    print("Pishkhan draft ready.")

    with args.apk.open("rb") as apk_file:
        response = session.post(
            url("upload/"),
            files={"apk": (args.apk.name, apk_file, "application/vnd.android.package-archive")},
            data={"architecture": "all"},
            timeout=600,
        )
    check_response(response, "upload-apk")
    print("Signed APK accepted for upload by Pishkhan.")

    payload = {
        "changelog_fa": (
            "نسخه بومی اندروید پدال‌پرو؛ بهبود پایداری رکاب‌زنی، "
            "حالت ذخیره باتری OLED با زمینه مشکی، "
            "و نمایش واضح‌تر مسیر رکاب‌زنی به‌صورت فیروزه‌ای نئونی."
        ),
        "changelog_en": "Native ride stability; true-black OLED mode and clearer neon ride route.",
        "developer_note": "PedalPro Native RC38, versionCode 82, staged rollout after review.",
        "staged_rollout_percentage": args.rollout,
        "auto_publish": True,
    }
    check_response(session.post(url("commit/"), json=payload, timeout=60), "commit-release")
    print(f"Pishkhan release committed: versionCode 82; auto_publish after review; staged rollout {args.rollout}%.")
    print("Market review/approval and subsequent public availability remain controlled by Cafe Bazaar.")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"::error::{exc}", file=sys.stderr)
        sys.exit(1)
