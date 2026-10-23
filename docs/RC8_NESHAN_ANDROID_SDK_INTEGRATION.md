# Superseded by current Neshan SDK (RC9)

**IMPORTANT — prior claim corrected:** The preceding RC8 documentation
referenced the expired `/docs/sdk/android/legacy/installation/` Android SDK.

Official CURRENT installation:
https://platform.neshan.org/docs/sdk/android/installation/

- Maven Central: `org.neshan.maplibre:android-sdk-opengl:13.4.1`
- Android manifest metadata: `org.maplibre.android.API_KEY`
- MOBILE API key is required at build time; an old `neshan.license` is not.
- Neshan official vector styles: `light.json`, `dark.json`.
- Bridge: `app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java`
- CI input: GitHub Actions repository secret `NESHAN_MOBILE_API_KEY`.
- When this secret is missing, released APK uses explicitly identified XYZ **fallback**, not a genuine Neshan map.
- No automatic market publishing, and PR #29 remains Draft.
