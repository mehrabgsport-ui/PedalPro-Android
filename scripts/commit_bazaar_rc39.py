#!/usr/bin/env python3
"""Finalize the already uploaded RC39 Pishkhan release at 100% rollout.

The APK was accepted in GitHub run 39136239936. No new draft and no
second APK upload; do not log the Pishkhan secret or response headers.
"""
import os
import sys
import requests
from publish_bazaar_rc39 import BASE,check

def main():
    token=os.environ.get("BAZAAR_PISHKHAN_API_SECRET","").strip()
    if not token:
        raise RuntimeError("BAZAAR_PISHKHAN_API_SECRET is not set")
    client=requests.Session()
    client.headers["CAFEBAZAAR-PISHKHAN-API-SECRET"]=token
    draft=check(client.get(BASE+"last-uncommitted/",timeout=35),"last-uncommitted")
    if not isinstance(draft,dict):
        raise RuntimeError("No JSON draft available; not creating or uploading a new release")
    # Even if a provider uses a nonstandard wrapper, do not repeat the APK upload.
    print("Pishkhan draft state fetched; finalizing the already uploaded RC39 package.",flush=True)
    details={
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
            "PedalPro Android Native RC39, ir.pedalpro.app, versionCode 83. "
            "100% rollout (staged rollout unavailable due to low active installs). "
            "APK previously uploaded and accepted."
        ),
        "staged_rollout_percentage":100,
        "auto_publish":True,
    }
    check(client.post(BASE+"commit/",json=details,timeout=75),"commit-release-100-percent")
    print("RELEASE_COMMITTED: RC39 versionCode 83, 100% rollout after Bazaar review.",flush=True)

if __name__=="__main__":
    try:main()
    except Exception as exc:
        print("::error::"+str(exc),file=sys.stderr,flush=True)
        sys.exit(1)
