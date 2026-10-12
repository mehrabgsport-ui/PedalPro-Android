# Huawei Health Service Kit setup for PedalPro

Package: ir.pedalpro.app
Android release certificate SHA-256:
69:08:5C:67:2A:B5:C1:E4:ED:A8:AA:4A:BC:12:A2:94:0A:EB:53:F1:15:07:C5:F9:F8:7B:B2:19:D9:A1:D6:F7

Minimum scopes requested:
1. Health data > Heart rate > Read
   https://www.huawei.com/healthkit/heartrate.read
2. Extended capability > Real-time heart rate > Read
   HEALTHKIT_EXTEND_REALTIME_HEART_READ

Purpose:
PedalPro is a cycling application. During an active ride, the user can explicitly connect Huawei Health and read live heart-rate samples from a paired Huawei wearable. The heart-rate value is used for workout intensity, heart-rate display, energy/calorie analysis, and training analytics. It is not used to alter map geometry or infer medical conditions.

Data handling:
- Read-only.
- User explicitly initiates connection.
- Live reading can be stopped at any time.
- Samples are linked only to the signed-in PedalPro user's active ride.
- No heart-rate write permission is requested.
- No blood pressure, SpO2, sleep, reproductive, or other unrelated health scopes are requested.

Huawei Developer Console:
- Create/choose Android app with package ir.pedalpro.app.
- Add the release SHA-256 fingerprint above.
- Enable Health Service Kit.
- Apply only for the two scopes listed above.
- Add the resulting App ID to the GitHub Actions secret HUAWEI_APP_ID.
- For test phase, add the tester Huawei IDs required by the Health Service Kit console.
- Complete Huawei verification before public rollout if the console requires it.

Runtime:
Huawei Health must be installed and signed in with the same HUAWEI ID used to grant Health Service Kit access.
Huawei Watch GT must be paired/synced in Huawei Health.
