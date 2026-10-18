#!/usr/bin/env python3
"""Submit the approved PedalPro RC39 signed APK to Cafe Bazaar via Pishkhan.

Secrets are read only from Actions environment. Never log tokens, cookies,
HTTP request headers, or raw API responses. No retries after upload/commit.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import requests

BASE="https://api.pishkhan.cafebazaar.ir/v1/apps/releases/"
EXPECTED_CODE=83

def redacted_issue(response):
    """Display bounded validation codes/messages without secrets or PII."""
    try:
        data=response.json()
    except ValueError:
        return "(API returned no JSON validation details)"
    allowed={"code","detail","message","error","errors","non_field_errors","apk","architecture","version_code","package_name","versionCode","signature"}
    def safe(value,level=0):
        if level>3:return "(nested)"
        if isinstance(value,dict):
            return {str(k):safe(v,level+1) for k,v in value.items() if str(k) in allowed}
        if isinstance(value,list):
            return [safe(v,level+1) for v in value[:5]]
        if isinstance(value,(int,float,bool)) or value is None:
            return value
        if isinstance(value,str):
            value=value[:300]
            value=re.sub(r"(?i)(bearer\s+\S+|eyJ[a-zA-Z0-9._=-]{18,}|[a-zA-Z0-9_-]{35,})","[REDACTED]",value)
            value=re.sub(r"[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}","[EMAIL]",value)
            return value
        return "(unknown)"
    sanitized=safe(data)
    return json.dumps(sanitized,ensure_ascii=False)[:1100]

def check(response,label,ok=(200,201,202,204)):
    if response.status_code not in ok:
        raise RuntimeError(f"{label}: HTTP {response.status_code}: {redacted_issue(response)}")
    if response.status_code==204:
        return {}
    try:return response.json()
    except ValueError:raise RuntimeError(f"{label}: response is not JSON") from None

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--apk",required=True,type=Path)
    parser.add_argument("--version-code",type=int,required=True)
    parser.add_argument("--rollout",type=int,default=10)
    args=parser.parse_args()
    if args.version_code!=EXPECTED_CODE:
        raise RuntimeError("Wrong version code; RC39 must be versionCode 83")
    if not (1<=args.rollout<=100):
        raise RuntimeError("Invalid rollout")
    token=os.environ.get("BAZAAR_PISHKHAN_API_SECRET","").strip()
    if not token:
        raise RuntimeError("BAZAAR_PISHKHAN_API_SECRET GitHub secret missing")
    if not args.apk.is_file() or args.apk.stat().st_size<1024*1024:
        raise RuntimeError("Signed APK missing or too small")

    session=requests.Session()
    session.headers["CAFEBAZAAR-PISHKHAN-API-SECRET"]=token

    draft=session.get(BASE+"last-uncommitted/",timeout=45)
    if draft.status_code==404:
        check(session.post(BASE,timeout=45),"create-draft")
        print("Created a new Pishkhan release draft.",flush=True)
    else:
        check(draft,"last-uncommitted")
        print("Found the existing Pishkhan release draft.",flush=True)

    with args.apk.open("rb") as fp:
        response=session.post(
            BASE+"upload/",
            files={"apk":(args.apk.name,fp,"application/vnd.android.package-archive")},
            data={"architecture":"all"},
            timeout=600,
        )
    uploaded=check(response,"upload-apk")
    print("Pishkhan APK upload accepted.",flush=True)

    commit={
        "changelog_fa":(
            "به‌روزرسانی نسخه بومی پدال‌پرو: بهبود کیفیت لوگوی اصلی در صفحه بارگذاری؛ "
            "بازگشت رنگ‌بندی قبلی صفحه خانه و منوها؛ محدودسازی پس‌زمینه OLED مشکی به "
            "پنل‌های حالت رکاب‌زنی؛ رفع هم‌پوشانی مطالب با هدر هنگام اسکرول؛ "
            "و مسیر فیروزه‌ای نئونی با وضوح بالاتر."
        ),
        "changelog_en":(
            "Improved splash logo, restored normal menu colors, scoped AMOLED black "
            "to active ride, fixed content overlap with header and improved neon route visibility."
        ),
        "developer_note":(
            "PedalPro Android Native RC39, package ir.pedalpro.app, versionCode 83. "
            "Native Java app without WebView; previous UI overlap and black menu regression fixed."
        ),
        "staged_rollout_percentage":args.rollout,
        "auto_publish":True,
    }
    check(session.post(BASE+"commit/",json=commit,timeout=75),"commit-release")
    print(f"RELEASE_COMMITTED: RC39 v83, Bazaar review/auto-publish requested, rollout={args.rollout}%.",flush=True)

if __name__=="__main__":
    try:main()
    except Exception as exc:
        print("::error::"+str(exc),file=sys.stderr,flush=True)
        sys.exit(1)
