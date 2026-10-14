#!/usr/bin/env bash
# Structural regression guards for the Native UI migration.
# This is not visual/pixel QA and does not verify Web 4.7.46 screenshots.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE="$ROOT/app/src/main/java/ir/pedalpro/app/nativeapp"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
failed=0

pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; failed=1; }
check_file() {
  if [[ -f "$NATIVE/$1" ]]; then pass "file $1"; else fail "missing $1"; fi
}
expect() {
  local file="$1" pattern="$2" message="$3"
  if grep -Fq -- "$pattern" "$file"; then pass "$message"; else fail "$message"; fi
}
for file in \
  NativeMainActivity.java NativeScreenBase.java NativeRideDetailActivity.java \
  LowPowerRideActivity.java NativeLiveNavigationActivity.java NativeSafetyActivity.java \
  NativeRidePhotoController.java NativeRidePrivacyDialog.java NativeSensorsActivity.java \
  NativeAdvancedActivity.java NativeSocialActivity.java NativeHeatmapActivity.java \
  NativeSegmentLeaderboardActivity.java NativeSiteSettingsActivity.java \
  NativeAppReleaseAdminController.java NativeFcmAdminController.java \
  PedalIconView.java PedalActionView.java
do
  check_file "$file"
done

if grep -RnE 'new[[:space:]]+WebView[[:space:]]*\(|import[[:space:]]+android\.webkit\.WebView|android\.webkit\.WebView[[:space:]]*\(' "$NATIVE" --include='*.java'; then
  fail "WebView reference in Native source"
else
  pass "Native sources have no instantiated WebView"
fi

expect "$MANIFEST" 'android:name=".nativeapp.NativeMainActivity"' "Native launcher declaration"
expect "$MANIFEST" 'android:name=".nativeapp.LowPowerRideActivity"' "OLED registered"
expect "$MANIFEST" 'android:name=".nativeapp.NativeRideDetailActivity"' "Ride details registered"
expect "$MANIFEST" 'android:name=".nativeapp.NativeSiteSettingsActivity"' "Native admin registered"
expect "$NATIVE/NativeMainActivity.java" 'EXTRA_BASE_SECONDS, displayedRideSeconds()' "OLED receives current display clock"
expect "$NATIVE/NativeMainActivity.java" 'if(requestCode==REQ_LOW_POWER)' "OLED result handled"
expect "$NATIVE/LowPowerRideActivity.java" 'EXTRA_RESULT_PAUSED' "OLED returns pause UI state"
expect "$NATIVE/LowPowerRideActivity.java" 'EXTRA_RESULT_SECONDS' "OLED returns visible elapsed time"
expect "$NATIVE/PedalIconView.java" 'case HEADING:' "Native heading icon"
expect "$NATIVE/PedalIconView.java" 'case HEART:' "Native Kudos icon"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'PedalIconView.Icon.HEADING' "Live/group navigation native heading"
expect "$NATIVE/NativeSafetyActivity.java" 'api.post("pp_beacon_create"' "Beacon backend action preserved"
expect "$NATIVE/NativeSafetyActivity.java" 'api.post("pp_sos_start"' "SOS backend action preserved"
expect "$NATIVE/NativeRideDetailActivity.java" 'api.get("ride_details",q)' "Ride details backend action preserved"
expect "$NATIVE/NativeSocialActivity.java" 'api.post("pp_follow_toggle"' "Follow backend action preserved"
expect "$NATIVE/NativeSocialActivity.java" 'api.post("pp_kudos_toggle"' "Kudos backend action preserved"
expect "$NATIVE/NativeAdvancedActivity.java" 'pp_segment_create_from_ride' "Segment action preserved"
expect "$NATIVE/NativeAdvancedActivity.java" 'pp_route_pack' "Offline pack action preserved"
expect "$NATIVE/NativeRouteView.java" 'PedalMapTiles.draw(this, canvas, cameraLat, cameraLng, cameraZoom,' "Native raster basemap present"
expect "$NATIVE/NativeRouteView.java" 'new ScaleGestureDetector' "Native pinch zoom"
expect "$NATIVE/PedalNeshanMapSurface.java" 'Class.forName("org.neshan.mapsdk.MapView")' "Licensed Neshan Android MapView adapter"
expect "$NATIVE/NeshanDirectionsClient.java" 'https://api.neshan.org/v2/direction' "Official Neshan road-direction endpoint"
expect "$NATIVE/NeshanDirectionsClient.java" 'Api-Key' "Neshan service header"
expect "$NATIVE/NeshanDirectionsClient.java" 'overview_polyline' "Neshan encoded road-geometry decoder"
expect "$NATIVE/NeshanServiceCredentials.java" 'AndroidKeyStore' "Device-encrypted Neshan service credentials"
expect "$NATIVE/NativeRouteView.java" 'setNavigationRoute(JSONArray rows)' "Separate suggestion route from recorded GPS"
expect "$NATIVE/PedalNeshanMapSurface.java" 'public void setNavigationRoute(JSONArray rows)' "Shared Neshan street route visual surface"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'NeshanDirectionsClient.fetch(secret' "Moving-group route refresh"
expect "$NATIVE/NativeLiveNavigationActivity.java" '45_000L' "Directions requests are throttled"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.clearNavigationRoute();' "Straight-distance fallback"
expect "$NATIVE/PedalNeshanMapSurface.java" 'setTrafficEnabled' "Neshan traffic layer control"
expect "$NATIVE/PedalNeshanMapSurface.java" 'setMapStyle' "Neshan day/night map control"
expect "$NATIVE/NativeRouteView.java" 'setExternalBasemap(boolean useNativeProvider)' "Transparent Neshan-compatible track overlay"
expect "$NATIVE/NativeMainActivity.java" 'activeRideRouteView = new PedalNeshanMapSurface(this);' "Active ride uses unified map surface"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'new PedalNeshanMapSurface(this);' "Group/live uses unified map surface"
expect "$NATIVE/NativeMainActivity.java" 'dock.addView(navRide, rideLp);' "Complete center ride CTA inside layout bounds"
expect "$NATIVE/NativeScreenBase.java" 'nav.addView(ride,rp);' "Secondary-screen ride CTA unclipped"
expect "$NATIVE/NativeRouteView.java" 'new GestureDetector' "Native double tap and reset gestures"
expect "$NATIVE/NativeRouteView.java" 'getParent().requestDisallowInterceptTouchEvent(true)' "Map gestures don't conflict with page scrolling"
expect "$NATIVE/NativeRouteView.java" 'userCamera = false;' "Return to auto-framed route"
expect "$NATIVE/NativeRouteView.java" 'PedalMapTiles.worldX(lng,cameraZoom)' "Mercator route alignment"
expect "$NATIVE/PedalMapTiles.java" 'BuildConfig.PEDALPRO_MAP_TILE_TEMPLATE' "Configured Android XYZ tile source"
expect "$NATIVE/PedalMapTiles.java" 'BuildConfig.PEDALPRO_MAP_ATTRIBUTION' "Configured map attribution"
expect "$NATIVE/PedalMapTiles.java" 'User-Agent' "Identified native tile requests"
expect "$NATIVE/NativeMainActivity.java" 'activeRideRouteView.setCurrent(fix.getLatitude(), fix.getLongitude());' "Ride start uses validated GPS UI camera"
expect "$NATIVE/NativeMainActivity.java" 'bottomGlyphItem(PedalIconView.Icon.HOME' "Native home dock icon"
expect "$NATIVE/NativeMainActivity.java" 'drawerIcon(labels[i])' "Native drawer item icons"
expect "$MANIFEST" 'android:name=".nativeapp.NativeAppSettingsActivity"' "Non-exported app settings registered"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'NeshanServiceCredentials.saveMobile(this,value)' "Admin Neshan MOBILE key encrypted on device"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'NeshanServiceCredentials.save(this,value)' "Admin Neshan routing key encrypted on device"
expect "$NATIVE/NativeAppSettingsActivity.java" 'NativeSiteSettingsActivity.class' "Legacy app settings redirects admin to unified site settings"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'addTab(row, "bale", "ربات بله")' "Bale bot section present in unified admin settings"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'case "seo": renderSeo(); break;' "SEO Robots native admin editor"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'case "neshan": renderNeshan(); break;' "Neshan controls in unified admin settings"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'case "bale": renderBale(); break;' "Bale bot controls in unified admin settings"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'private boolean isPrivateBaleField(String key)' "Bale credentials hidden from admin readout"
expect "$NATIVE/NativeMainActivity.java" 'activeRideRouteView.setNavigationMode(true)' "Active ride starts rider-follow navigation presentation"
expect "$NATIVE/NativeMainActivity.java" 'j.optDouble("bearing",Double.NaN)' "Navigation heading comes from TrackingService bearing"
expect "$NATIVE/NativeRouteView.java" 'canvas.rotate(-navigationBearing' "Navigation canvas is heading-up"
expect "$NATIVE/NativeRouteView.java" 'canvas.scale(1f,.86f' "Navigation has lightweight 2.5D tilt"
expect "$NATIVE/PedalMapTiles.java" 'Math.hypot(w,h) * 1.05' "Rotated tile viewport has diagonal coverage"
expect "$NATIVE/PedalNeshanMapSurface.java" 'public void setNavigationMode(boolean enabled)' "Shared native map has follow-mode control"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'case "other": renderUnmirroredSiteSettings(); break;' "Additional backend fields audited"
expect "$NATIVE/NativeMainActivity.java" 'headerAdminSettings = iconSquare(PedalIconView.Icon.SETTINGS, this::showSiteSettingsPage' "Administrator settings consolidated next to account"
expect "$NATIVE/NativeMainActivity.java" 'private void renderImmersiveRide(' "Full-screen ride map presenter"
expect "$NATIVE/NativeMainActivity.java" 'content.removeView(activeRideRouteView);' "Map detached from 280dp scroll card"
expect "$NATIVE/NativeMainActivity.java" 'immersive.addView(activeRideRouteView,new FrameLayout.LayoutParams(-1,-1));' "Ride map fills viewport"
expect "$NATIVE/NativeMainActivity.java" 'hideImmersiveRideOverlay();' "Ride overlay cleared upon page navigation"
expect "$NATIVE/PedalNeshanMapSurface.java" 'public void setFullRideMode(boolean enabled)' "Map control panel can float for full ride"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'NativeAppSettingsActivity.PREF_PREFER_ROADS' "Road routing preference centralized"
expect "$NATIVE/NativeMainActivity.java" 'safeEventValue(event,"finish_location"' "No literal null finish locations"
expect "$NATIVE/NativeMainActivity.java" 'i.putExtra("section", "sms")' "Full SMS admin entry"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'load(isValidSection(requested) ? requested : "general")' "Full admin tab deep entry"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'new NativeAppReleaseAdminController' "Native release manager wired"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'new NativeFcmAdminController' "Native FCM manager wired"
expect "$NATIVE/NativeAdvancedActivity.java" 'replayIndex=next+1;' "Replay advances to next frame"
expect "$NATIVE/NativeAdvancedActivity.java" 'generation!=replayLoadGeneration' "Replay ignores stale tab responses"
expect "$NATIVE/NativeAdvancedActivity.java" 'generation!=flyLoadGeneration' "Flyover ignores stale tab responses"
expect "$NATIVE/NativeAdvancedActivity.java" '@Override protected void onStop()' "Replay/Flyover stop offscreen"
expect "$NATIVE/NativeAiActivity.java" 'if(!conversationInteracted)renderHistory(finalItems)' "AI history does not replace pending messages"
expect "$NATIVE/NativeAiActivity.java" 'final String selectedMode=String.valueOf(mode.getSelectedItem())' "AI mode captured on UI thread"
expect "$NATIVE/NativeMainActivity.java" 'Boolean.TRUE.equals(host.getTag())' "Home AI prevents stale history overwrite"
expect "$NATIVE/NativeChatActivity.java" 'final int selectedRoom=roomId;' "Chat room id captured before worker"
expect "$NATIVE/NativeChatActivity.java" 'generation!=roomGeneration||selectedRoom!=roomId' "Chat rejects stale room responses"
expect "$NATIVE/NativeChatActivity.java" 'if(isFinishing()||!isChatVisible)return;' "Chat polling stops offscreen"
expect "$NATIVE/NativeFcmAdminController.java" 'final String credentials=serviceAccountJson;' "Firebase credentials snapshotted"
expect "$NATIVE/NativeSocialActivity.java" 'PedalIconView.Icon.HEART' "Kudos native icon"

expect "$NATIVE/NativeSiteSettingsActivity.java" 'private String safeBaleValue(String key,Object value)' "Undocumented Bale fields are redacted"
expect "$NATIVE/NativeRouteView.java" 'public void setHeadingUpEnabled(boolean enabled)' "North-Up and Heading-Up can be switched while following"
expect "$NATIVE/NativeRouteView.java" 'double ahead=h*.18d;' "Rider appears below the map center"
expect "$NATIVE/NativeRouteView.java" 'navigationZoom = Math.max(3, Math.min(18, navigationZoom + amount));' "Navigation zoom does not disable follow"
expect "$NATIVE/NativeRouteView.java" 'PedalMapTiles.drawAttribution(this,canvas)' "Map attribution stays upright"
expect "$NATIVE/PedalNeshanMapSurface.java" 'overlay.setHeadingUpEnabled(!overlay.isHeadingUpEnabled())' "Heading toggle is not a follow toggle"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.setNavigationMode(true)' "Live/group map follows current rider"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.setHeadingDegrees(loc.getBearing())' "Live navigation heading from an actual location"
expect "$NATIVE/PedalMapTiles.java" 'double rangeW = overscan > 1.01f ? (cos*w + sin*h)*1.08 : w;' "Rotated raster viewport bounds requests"
expect "$NATIVE/PedalMapTiles.java" 'CACHE_SCOPE = Integer.toHexString(TILE_URL.hashCode())' "Tiles are provider-scoped"

if [[ "$failed" -ne 0 ]]; then
  printf 'Native parity structural guards FAILED.\n' >&2
  exit 1
fi
printf 'Native parity structural guards passed. Build/device and source-reference QA remain separate gates.\n'
