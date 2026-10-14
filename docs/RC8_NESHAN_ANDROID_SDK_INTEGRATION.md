# PedalPro — Neshan Native Provider Setup (RC8)
- Repository: mehrabgsport-ui/PedalPro-Android
- Branch: native/web-parity (PR #29, Draft)
- Package Name: ir.pedalpro.app
- Version: 2.0.0-native-rc8 / versionCode 52
- Backup: backup/pre-neshan-sdk-rc8-20261014

## License prerequisite (do not paste secret into chat)
Official Neshan Android SDK requires an Android licence generated for:
1. Android package `ir.pedalpro.app`.
2. SHA1 certificate of the established signing keystore in GitHub Actions.
3. Licence from Neshan Platform (download `neshan.licence`).

Enter the complete file's base64 into the **GitHub Actions repository secret** `NESHAN_ANDROID_LICENCE_BASE64`. Do not commit it.
The workflows decode it only on the Actions runner to `app/src/main/res/raw/neshan.licence` (ignored by Git). On a local licensed build:
```bash
mkdir -p app/src/main/res/raw
# Place your authentic neshan.licence in that directory manually.
gradle :app:assembleBazaarRelease :app:assembleMyketRelease -PpedalproNeshanAndroidSdk=true
```

## Provider integration
- App runtime loads `LicensedNeshanMapBridge` when `BuildConfig.PEDALPRO_NESHAN_ANDROID_ENABLED` is true.
- Uses real Neshan MapView plus native camera, marker and polyline APIs; does not embed WebView.
- Routes drawn on SDK as independently managed turquoise recorded and yellow suggested polylines.
- Navigation angle is driven only by already-filtered TrackingService bearing. Camera focuses ~40m ahead while following; native zoom, recenter, tilt ~55 degrees and North-Up/Heading-Up.
- Native map captures pan and pinch; the old custom bitmap Canvas is not layered over it.
- The MOBILE credential stored locally is not used as a substitute for the licence. Routing service API key remains separate in Android Keystore.
- If no licence secret exists, signed APK remains **XYZ fallback with explicit warning** and cannot be described as containing the Neshan map.

## QA gates
1. Native RC QA and Android Store Release Build must succeed at same SHA.
2. Check workflow step `Configure licensed Neshan Native Map`: `true` is a licensed build. `false` is not.
3. Validate certificate SHA1, package, v2/v3 signatures and `versionCode 52` on both market artifacts.
4. On phone: verify map boots, Street labels, native traffic/day-night, true pitch/bearing, pan, pinch, rider marker, route overlays, group navigation, background GPS stability and offline behavior.
5. Car-routing results are not certified bicycle routes. Actual turn-by-turn cycling navigation remains separately gated.
6. No main merge or market publish before sign-off.

Official SDK: https://github.com/NeshanMaps/android-neshan-maps-starter-master-new
