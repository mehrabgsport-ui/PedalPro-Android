# RC28 Native map point selection and route presentation
- Program start location is selected on a native tappable raster map, with movable point, GPS location button, confirmation and automatic latitude/longitude save to the existing save_event PHP contract.
- Existing program coordinates can be edited without typing raw coordinates. No GPS tracking service changes.
- Suggested navigation routes are now cyan/white cased in native fallback and cyan in Neshan MapLibre overlay.
- Neshan SDK-specific map picker and physical device verification are still pending. Route directions still depend on real cycling API; no turn instruction is invented.
