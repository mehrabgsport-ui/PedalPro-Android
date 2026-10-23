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
expect "$NATIVE/NeshanDirectionsClient.java" 'https://api.neshan.org/v2/direction' "Official Neshan road-direction endpoint"
expect "$NATIVE/NeshanDirectionsClient.java" 'Api-Key' "Neshan service header"
expect "$NATIVE/NeshanDirectionsClient.java" 'overview_polyline' "Neshan encoded road-geometry decoder"
expect "$NATIVE/NeshanServiceCredentials.java" 'AndroidKeyStore' "Device-encrypted Neshan service credentials"
expect "$NATIVE/NativeRouteView.java" 'setNavigationRoute(JSONArray rows)' "Separate suggestion route from recorded GPS"
expect "$NATIVE/PedalNeshanMapSurface.java" 'public void setNavigationRoute(JSONArray rows)' "Shared Neshan street route visual surface"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'CyclingDirectionsClient.fetch(api' "Moving-group bicycle route via verified server contract"
expect "$NATIVE/NativeLiveNavigationActivity.java" '45_000L' "Directions requests are throttled"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.clearNavigationRoute();' "Straight-distance fallback"
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
expect "$NATIVE/NativeRouteView.java" 'viewTransform.setConcat(pitch,rotate)' "Navigation camera follows heading under perspective tilt"
expect "$NATIVE/NativeRouteView.java" 'pitch.setPolyToPoly(source,0,dest,0,4)' "Navigation is projective 2.5D instead of flat Y squeeze"
expect "$NATIVE/PedalMapTiles.java" 'inverseProjection.mapPoints(corners)' "Tile viewport uses inverse perspective coverage"
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
expect "$NATIVE/NativeRouteView.java" 'float[] riderAt={w*.5f,h*.68f};' "Rider appears below the map center"
expect "$NATIVE/NativeRouteView.java" 'navigationZoom = Math.max(3, Math.min(18, navigationZoom + amount));' "Navigation zoom does not disable follow"
expect "$NATIVE/NativeRouteView.java" 'PedalMapTiles.drawAttribution(this,canvas)' "Map attribution stays upright"
expect "$NATIVE/PedalNeshanMapSurface.java" 'overlay.setHeadingUpEnabled(!overlay.isHeadingUpEnabled())' "Heading toggle is not a follow toggle"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.setNavigationMode(true)' "Live/group map follows current rider"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.setHeadingDegrees(loc.getBearing())' "Live navigation heading from an actual location"
expect "$NATIVE/PedalMapTiles.java" 'inverseProjection.mapPoints(corners)' "Perspective-aware tile viewport bounds requests"
expect "$NATIVE/PedalMapTiles.java" 'CACHE_SCOPE = Integer.toHexString(TILE_URL.hashCode())' "Tiles are provider-scoped"

expect "$NATIVE/NativeMainActivity.java" 'PedalNeshanMapSurface keepMap=shownRideId>0' "Recreate ride UI without discarding loaded map"
expect "$NATIVE/NativeMainActivity.java" 'now-rideRouteLastFetchElapsed<20_000L' "Prevent repeated ride-route backend fetch"
expect "$NATIVE/NativeMainActivity.java" 'seedDisplayMapFromLastLocation(activeRideRouteView)' "Seed map from recent device location"
expect "$NATIVE/NativeRouteView.java" 'inverseViewTransform.mapPoints(riderAt)' "Rider anchored accurately through pitch perspective"
expect "$NATIVE/NativeRouteView.java" 'canvas.concat(viewTransform)' "2.5D pitch applied to all overlay layers"
expect "$NATIVE/PedalMapTiles.java" 'static final int TILE = 512;' "Raster tiles not undersized on high-density devices"
expect "$NATIVE/PedalMapTiles.java" 'needed.sort(Comparator.comparingDouble' "Center-priority tile scheduling"
expect "$NATIVE/PedalMapTiles.java" 'for(int level=1;level<=2' "Low-resolution parent tile placeholders"
expect "$NATIVE/PedalMapTiles.java" 'for(View view:WATCHERS)' "Visible maps redraw after shared tile completion"

expect "$NATIVE/PedalNeshanMapSurface.java" 'overlay.setVisibility(View.GONE)' "No misprojected Canvas overlay or stolen SDK gestures"
expect "$NATIVE/PedalNeshanMapSurface.java" 'sdkBridge.setRecordedTrack(rows)' "Recorded GPS route uses native SDK polyline"
expect "$NATIVE/PedalNeshanMapSurface.java" 'sdkBridge.setSuggestedRoute(rows)' "Proposed directions use separate SDK polyline"
expect "$NATIVE/PedalNeshanMapSurface.java" 'sdkBridge.setCurrent(lat,lng)' "Current rider location updates SDK marker"
expect "$NATIVE/PedalNeshanMapSurface.java" 'NeshanMapLibreBridge' "Current native Neshan provider instead of expired SDK"
expect "$NATIVE/PedalNeshanMapSurface.java" 'sdkBridge.setNight(night)' "Neshan native dark/light map styles"
expect "$NATIVE/PedalNeshanMapSurface.java" 'applyCameraMode();' "Real map camera orientation updates"
expect "$ROOT/app/build.gradle" 'org.neshan.maplibre:android-sdk-opengl:13.4.1' "Current official SDK dependency"
expect "$ROOT/app/build.gradle" "java.srcDir 'src/neshanmaplibre/java'" "Current SDK conditional source set"
expect "$ROOT/app/build.gradle" 'NESHAN_MOBILE_API_KEY' "Official mobile key from CI environment"
expect "$ROOT/app/src/main/AndroidManifest.xml" 'org.maplibre.android.API_KEY' "MapLibre API key metadata"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'MapLibre.getInstance(context)' "SDK initialized"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'view.onCreate(null)' "MapLibre view initialized with lifecycle"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" '.tilt(57d).bearing(bearing)' "Native camera pitch and bearing"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'map.addPolyline(new PolylineOptions()' "Native route overlays"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'styles/' "Official Neshan map styles"

expect "$NATIVE/NativeMainActivity.java" 'private void installNativeSplash()' "Native startup follows actual bootstrap"
expect "$NATIVE/NativeMainActivity.java" 'report.setOrientation(LinearLayout.HORIZONTAL)' "Home stats share a horizontal row"
expect "$NATIVE/NativeMainActivity.java" '"عضویت در پدال پرو"' "Home top CTA is membership"
expect "$NATIVE/NativeMainActivity.java" '"ثبت‌نام در برنامه"' "Registration CTA is inside upcoming program card"
expect "$NATIVE/NativeMainActivity.java" 'private void enterPage(String next)' "Navigation history and position retention"
expect "$NATIVE/NativeMainActivity.java" 'private void addMemberMedals(' "Member medals visible"
expect "$NATIVE/NativeMainActivity.java" 'new SocialNetworkIconView(' "Social marks are brand-specific vectors"
expect "$NATIVE/NativeMainActivity.java" 'new Intent(this,NativeChatActivity.class)' "Unified group/private chat screen"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'api.postMultipart("medal_image_upload",fields,uri,"photo"' "Medal PNG is uploaded using confirmed PHP contract"
expect "$NATIVE/NativeChatActivity.java" 'messagesScroll=new ScrollView(this);' "Bounded chat message viewport"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'private void chooseDestination()' "Saved GPS route can choose start/end"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'main.postDelayed(this,"group".equals(mode)?10000L:3000L)' "Group live location refreshes each 10 seconds"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'private void beginGroupRideIfNeeded(Button button)' "Group catch ride recording uses existing contract"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" '.icon(icon).title("رکاب‌زن")' "Current position displayed as top-down reference-style rider"
expect "$ROOT/app/src/main/AndroidManifest.xml" 'NativeSavedRouteFollowActivity' "Saved route follower registered"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/PedalProFirebaseMessagingService.java" 'NativeMainActivity.class' "Push links open native app not WebView"

expect "$NATIVE/NativeSavedRouteFollowActivity.java" '"شروع GPS و بازکردن نشان"' "Saved-route destination requires explicit GPS preflight"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'beginTrackingThen(this::launchNeshanDestination)' "Recorded ride launched before external navigator"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'api.post("start_ride"' "Reuses verified start-ride server action"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'service.setAction(TrackingService.ACTION_START)' "Reuses existing GPS TrackingService"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'int id=active==null?0:active.optInt' "No second ride created for active rider"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.clearTarget();' "Drop stale group location rather than navigating to old leader point"

# RC13: last-mile functional protections, without modifying GPS persistence.
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'beginTrackingThen(this::activateFollowing)' "At saved-route start, GPS is required for tracked auto-follow"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'if(recording()){' "Existing GPS recording auto-continues at route start"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'arrivalCandidateFixTime' "Group arrival requires distinct fixes"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'fixAge>12000L' "Stale GPS cannot confirm group arrival"
expect "$NATIVE/NativeChatActivity.java" 'private boolean appendMessages(JSONArray incoming)' "Chat drops duplicate live events"
expect "$NATIVE/NativeChatActivity.java" 'if(changed||receiptsChanged)renderMessages();' "Idle chat polls avoid expensive redraws"
expect "$NATIVE/NativeMainActivity.java" 'String attendedAt,boolean completed)' "Attendance waits for server acknowledgement"
expect "$NATIVE/NativeMainActivity.java" '"گزارشات من"' "Personal reports linked from account"
expect "$NATIVE/NativeMainActivity.java" '"مشاور هوشمند"' "AI advisor linked from account"

# RC19: admin late attendance, join timestamp, and server GPS accuracy configuration.
expect "$NATIVE/NativeMainActivity.java" 'api.post("event_late_add"' "Admin can add a late member with server timestamp"
expect "$NATIVE/NativeMainActivity.java" 'bindAttendanceToggle(attended,regId,' "Retroactive attendance requires a timestamp"
expect "$NATIVE/NativeMainActivity.java" 'request.put("attended_at",attendedAt)' "Attendance sends true arrival time"
expect "$NATIVE/NativeMainActivity.java" 'rideConfig.optDouble("gps_max_accuracy_m",25d)' "Ride accuracy reads server configuration"
python3 - "$NATIVE/NativeMainActivity.java" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
assert 'bindAttendanceToggle(attended,regId,' in s
assert 'if(checked && completed)promptRetrospectiveAttendance(registrationId,control);' in s
assert 'api.post("event_late_add",new JSONObject()' in s
assert 'showLateMemberPicker(event, dialog)' in s
print('PASS RC19 late-member check-in and historical attendance timestamp UI')
PY

# RC22: all six user-reported ride and group features.
expect "$NATIVE/NativeMainActivity.java" 'useAlternativeTiles(true)' "Manual Neshan base-map failure fallback in program detail"
expect "$NATIVE/PedalNeshanMapSurface.java" 'useAlternativeTiles(boolean enabled)' "Fallback map tiles within Native screen"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'map.removeMarker(rider)' "Old rider marker removed on style reset"
expect "$NATIVE/RideSpeedDisplayFilter.java" 'class RideSpeedDisplayFilter' "Live speed uses confidence-aware display filtering"
expect "$NATIVE/LowPowerRideActivity.java" 'speedFilter.update(' "OLED ride speed never blindly displays rejected GPS zero"
expect "$NATIVE/NativeMainActivity.java" 'FLAG_KEEP_SCREEN_ON' "Native active ride keeps the display on"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'FLAG_KEEP_SCREEN_ON' "Native live navigation keeps display on"
expect "$NATIVE/RemoteImageLoader.java" 'setInstanceFollowRedirects(false)' "Private cookies cannot follow remote image redirects"
expect "$NATIVE/SessionStore.java" 'AES/GCM/NoPadding' "Native session protected in Android Keystore"
expect "$NATIVE/PedalProApiClient.java" 'RideImageCompressor.prepare(context,fileUri)' "Photo optimization before upload"
expect "$NATIVE/NativeRideDetailActivity.java" 'Keep the map directly above' "Ride profile positioned next to map"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'metricCodes.get(' "Medal criteria must be a controlled selector"
expect "$NATIVE/NativeScreenBase.java" 'useStickyHeader()' "Settings tabs can be pinned above content scrolling"
expect "$NATIVE/NativeMainActivity.java" 'routeView.setTag(Integer.valueOf(rideId))' "Stale route responses are discarded"
expect "$NATIVE/NativeRouteView.java" 'setOnMapTapListener(' "Program start can be selected on native map"
expect "$NATIVE/PedalNeshanMapSurface.java" 'setAmoledBatterySaver(amoled)' "Native battery saver is wired"
test -f "$ROOT/app/src/main/res/drawable-nodpi/pedalpro_logo_transparent.png"
expect "$NATIVE/NativeMainActivity.java" 'R.drawable.pedalpro_logo_transparent' "User-facing logos use transparent PNG"
expect "$NATIVE/NativeMainActivity.java" 'this::showGroups' "Home groups tile opens native discovery"
expect "$NATIVE/NativeMainActivity.java" 'private void showGroups()' "Group and event discovery Native UI exists"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'applyAmoledStyle(style)' "Neshan AMOLED style transformation exists"
expect "$NATIVE/NativeMainActivity.java" 'showEventPointPicker(' "Program map selection is hooked up"
expect "$NATIVE/NativeMainActivity.java" 'speedFilter.update(' "Full ride speed never blindly displays rejected GPS zero"
expect "$NATIVE/NativeSpeedChartView.java" 'positive.size()>=2' "Speed chart fixes isolated zero samples"
expect "$NATIVE/NativeRideProfileView.java" 'enum Mode { ELEVATION, SPEED, TIME }' "Elevation speed and time modes"
expect "$NATIVE/NativeRideDetailActivity.java" 'profileMap.focusOn(latitude,longitude)' "Profile scrub synchronizes map marker"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'setNearbyRiders(others)' "Group navigation shows permitted nearby riders"
expect "$NATIVE/NativeMainActivity.java" 'api.get("pp_live_riders")' "Main ride map fetches live riders"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'updateNearbyRiderMarkers()' "Refresh other riders on Neshan vector map"
tmp_speed_qa="$(mktemp -d)"
javac -d "$tmp_speed_qa" "$NATIVE/RideSpeedDisplayFilter.java" "$ROOT/ci/RideSpeedDisplayFilterTest.java"
java -cp "$tmp_speed_qa" ir.pedalpro.app.nativeapp.RideSpeedDisplayFilterTest
javac -d "$tmp_speed_qa" "$NATIVE/RideProfileTime.java" "$ROOT/ci/RideProfileTimeTest.java"
java -cp "$tmp_speed_qa" ir.pedalpro.app.nativeapp.RideProfileTimeTest
rm -rf "$tmp_speed_qa"

# RC21: no direct raw GPS start point may bypass the TrackingService filter.
if grep -Fq 'api.post("track_point"' "$NATIVE/NativeMainActivity.java"; then
  echo "Raw UI GPS point bypasses the V6 recording filter" >&2
  exit 1
fi
# RC21: GPS quality controls are tested in a standalone pure-Java harness.
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'initialLockFix' "Startup waits for two consecutive accurate GNSS fixes"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'GpsTrackQuality.stationaryJitter(' "Suppress jitter from standing bicycle"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'emitAcceptedPoint(new Location(candidate), false, 0.0);' "Recorded points stay unsnapped after acceptance"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" '.setMinUpdateDistanceMeters(0f)' "Receive stationary GPS fix for initial lock"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'rider.setIcon(icon)' "Marker rotates with rider GPS heading"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'cleanRecordedGeometry(parse(recordedPoints))' "Historical polyline display rejects obvious spikes"
expect "$ROOT/app/src/main/res/drawable/pedal_cyclist_topdown.xml" 'android:viewportHeight="160"' "Top-down rider drawable is bundled"
tmp_gps_qa="$(mktemp -d)"
trap 'rm -rf "$tmp_gps_qa"' EXIT
javac -d "$tmp_gps_qa" "$ROOT/app/src/main/java/ir/pedalpro/app/GpsTrackQuality.java" "$ROOT/ci/GpsTrackQualityTest.java"
java -cp "$tmp_gps_qa" ir.pedalpro.app.GpsTrackQualityTest

# RC20 chat-open endpoint was not read-only: migrate to CSRF-protected POST.
expect "$NATIVE/NativeChatActivity.java" 'api.post("chat_open_private",new JSONObject().put("user_id",userId))' "Private chat creation uses CSRF-protected POST and JSON user_id"
if grep -Fq 'api.get("chat_open_private"' "$NATIVE/NativeChatActivity.java"; then
  echo "Private chat must not use GET; server rejects it with HTTP 405" >&2
  exit 1
fi
# RC20 medal-image source-backed contracts.
expect "$NATIVE/NativeSiteSettingsActivity.java" 'postAndReload("medal_image_clear"' "Manager can clear an uploaded medal image without deleting the medal"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'm.optString("metric_type","")' "Correct display of medal definition metric"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'new android.app.AlertDialog.Builder(this)' "Destructive admin actions require confirmation"

# RC18 bicycle-only navigation and route-bound GNSS identifiers.
expect "$NATIVE/CyclingDirectionsClient.java" 'api.get("pp_cycling_directions",query)' "Backend bicycle-only routing adapter"
expect "$NATIVE/CyclingDirectionsClient.java" '"cycling-regular".equals(data.optString("profile"))' "Car profiles are never treated as bike routes"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'mapView.setNavigationRoute(route.points)' "Navigation draws bicycle approach to selected route"
expect "$NATIVE/NativeSavedRouteFollowActivity.java" 'arrivalCandidateFixTime=loc.getTime()' "Saved route arrival requires multiple GPS fixes"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'groupTargetFresh(g)' "Stale leader target never used"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'pedal_cyclist_topdown' "Reference-style top-down cyclist drawable used as marker"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'p.put("client_point_id", java.util.UUID.randomUUID().toString());' "Stable per-point idempotency key"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'payload.optInt("ride_id",rideId) != rideId' "Offline points cannot be credited to another ride"
python3 - "$NATIVE/NativeSavedRouteFollowActivity.java" "$NATIVE/NativeLiveNavigationActivity.java" <<'PY'
import pathlib,sys
route,group=[pathlib.Path(p).read_text() for p in sys.argv[1:]]
assert 'NeshanDirectionsClient.fetch(' not in group
assert group.count('CyclingDirectionsClient.fetch(')==1
assert route.index('private void startFollow()') < route.index('private void activateFollowing()')
assert 'beginTrackingThen(this::updateLocation)' in route
assert 'phase=PHASE_TO_TARGET;' in route
assert 'routeView.clearTarget();routeView.clearNavigationRoute();' in group
print('PASS RC18 bicycle-only route and stale group position guards')
PY

# RC17 Backend audit verified API contracts (4.7.46).
expect "$NATIVE/NativeMainActivity.java" 'api.post("event_group_start"' "Group segment created on personal ride"
expect "$NATIVE/NativeMainActivity.java" 'api.post("event_group_stop"' "Group segment ends without ending personal ride"
expect "$NATIVE/NativeMainActivity.java" '.put("event_id", 0)' "Personal ride always starts without legacy event_id"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'p.put("metric_type"' "Medal metric matches PHP medal_save"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'p.put("match_text"' "Medal match field matches PHP medal_save"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'api.get("bale_status")' "Verified Bale management status"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'api.post("bale_connected_users"' "Verified connected Bale user search"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'api.post("bale_custom_send",payload)' "Verified Bale one/all messaging with nonce"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'api.get("bale_custom_history")' "Verified Bale batch history"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'new ir.pedalpro.app.nativeapp.PedalProApiClient(this).bootstrap()' "TrackingService refreshes native session CSRF"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/nativeapp/PedalProApiClient.java" 'if(status==419&&!retried419)' "Multipart honors CSRF expiration"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'if (ignored)' "Server-discarded GPS points are identified"

# RC16: paused rides with unsent GPS points must not be closed silently.
expect "$NATIVE/NativeMainActivity.java" 'if (unsentBefore && (!tracking.getBoolean("active", false)' "Paused ride cannot discard unsent GPS points at finish"

# RC16: the service must drain the acknowledged queue before server stop_ride.
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'ACTION_PREPARE_FINISH' "Foreground GPS service supports graceful final sync"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" '.putBoolean("finish_prepared", true).apply();' "Final GPS batch enqueued before release acknowledgement"
expect "$NATIVE/NativeMainActivity.java" 'if (!synchronizedTrack)' "Finish fails safely rather than dropping queued GPS"
expect "$NATIVE/NativeMainActivity.java" 'serverStopAttempted = true;' "Server stop occurs after GPS queue drain"
python3 - "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" "$NATIVE/NativeMainActivity.java" <<'PY'
import pathlib, sys
service, activity = [pathlib.Path(x).read_text() for x in sys.argv[1:]]
assert service.index('flushRoadMatchBufferRaw();',service.index('ACTION_PREPARE_FINISH.equals')) < service.index('.putBoolean("finish_prepared", true).apply();')
block = activity[activity.index('private void finishRide('):activity.index('private boolean isTrackingServiceActive()')]
assert block.index('if (!synchronizedTrack)') < block.index('api.post("stop_ride"') < block.index('stop.setAction(TrackingService.ACTION_STOP)')
assert 'startTrackingService(rideId);' in block
print('PASS graceful GPS finish flush precedes server stop and safely resumes on sync failure')
PY

# RC16: native GPS authentication, server-rejection persistence, and recorded track drawing.
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'return nativeSessionCookie();' "GPS uses same Native PHP session as API client"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'answer.optBoolean("ok", true)' "HTTP 200 rejection cannot drop queued GPS points"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'lastPendingRetryAt >= 15000L' "GPS pending queue retries even when rider stops"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" 'out.put("recorded", recorded);' "Accepted and stationary GPS fixes are distinguishable"
expect "$NATIVE/NativeMainActivity.java" 'activeRideRouteView.appendPoints(new JSONArray().put(' "Accepted live path draws on map"
expect "$NATIVE/NativeMainActivity.java" 'liveTrackRevision==revisionAtRequest' "Stale server snapshot never erases live track"
python3 - "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" "$NATIVE/NativeMainActivity.java" <<'PY'
import pathlib, sys
service, activity = (pathlib.Path(p).read_text() for p in sys.argv[1:])
assert 'import android.webkit.CookieManager;' not in service
assert 'getCookie("https://pedalpro.ir/")' not in service
assert 'broadcastPoint(p, null, true)' in service
assert 'broadcastPoint(locationJson(frozen), null, false)' in service
assert 'if (answer.optBoolean("ok", true)) return new SendResult(true, body);' in service
assert 'if (incomingRideId <= 0 || incomingRideId != currentRideId) return;' in activity
assert 'if (timestamp > lastLiveTrackPointTime)' in activity
print('PASS RC16 accepted recorded points append; stale cookie and discarded rejected points barred')
PY

# RC15: changing chat rooms must discard reply/edit IDs and stale compose state.
expect "$NATIVE/NativeChatActivity.java" 'private void resetComposition()' "Room-scoped chat composer reset exists"
python3 - "$NATIVE/NativeChatActivity.java" <<'PY'
import pathlib, re, sys
src = pathlib.Path(sys.argv[1]).read_text()
def method_start(name):
    match = re.search(r'private void ' + re.escape(name) + r'\([^)]*\)\s*\{', src)
    if not match:
        raise AssertionError('missing ' + name)
    return match.end()
def block_from(offset):
    level = 1
    for i in range(offset, len(src)):
        if src[i] == '{': level += 1
        elif src[i] == '}':
            level -= 1
            if level == 0: return src[offset:i]
    raise AssertionError('unterminated method')
reset = block_from(method_start('resetComposition'))
assert all(x in reset for x in ('replyTo=0;', 'editId=0;', 'input.setText("")', 'composeContext.setVisibility(View.GONE)', 'cancelCompose.setVisibility(View.GONE)'))
for name in ('openGroup', 'openPrivate'):
    body = block_from(method_start(name))
    assert 'resetComposition();' in body, name + ' must clear composer when opening a room'
print('PASS Chat group/private changes clear stale compose IDs and text')
PY

# RC14: chat composer stays in its own fixed pane, even with the IME open.
expect "$NATIVE/NativeScreenBase.java" 'protected boolean useFixedContentViewport(){return false;}' "Fixed viewport is opt-in and does not affect other screens"
expect "$NATIVE/NativeScreenBase.java" 'WindowInsetsCompat.Type.ime()' "Chat responds to soft-keyboard insets"
expect "$NATIVE/NativeScreenBase.java" 'if(useFixedContentViewport()){' "Chat bypasses outer page ScrollView"
expect "$NATIVE/NativeChatActivity.java" 'protected boolean useFixedContentViewport(){return true;}' "Native chat uses fixed screen body"
expect "$NATIVE/NativeChatActivity.java" 'new LinearLayout.LayoutParams(-1,0,1f)' "Chat messages expand into remaining viewport"
expect "$NATIVE/NativeChatActivity.java" 'cancelCompose.setVisibility(View.VISIBLE)' "Reply and edit can always be cancelled"

# RC36: start must activate GPS after confirmed server creation without
# depending on bootstrap refresh, and finish must keep a successful stop.
python3 - "$NATIVE/NativeMainActivity.java" <<'PY'
import sys
from pathlib import Path
source = Path(sys.argv[1]).read_text(encoding="utf-8")
start = source.split("private void createRideAndStartTracking(", 1)[1].split("private JSONObject findChallengeById(", 1)[0]
assert start.count('api.post("start_ride", body)') == 1, "one create request per tap"
assert start.index('startTrackingService(rideId);') < start.index('api.bootstrap();'), "GPS startup must precede optional bootstrap"
assert 'serverRide.optInt("id", 0) == rideId' in start, "refresh must not substitute another ride"
assert 'فعالیت جدیدی شروع نکنید' in start, "tracker retry must not create duplicate server rides"
assert 'message.contains("شماره موبایل")' in start, "phone verification rejection needs visible guidance"
stop = source.split("private void finishRide(Button button, String notes)", 1)[1].split("private boolean isTrackingServiceActive()", 1)[0]
assert 'api.post("stop_ride"' in stop and 'A subsequent bootstrap outage' in stop, "saved ride survives bootstrap outage"
print("PASS RC36 GPS start and finish resilience")
PY

# RC37: avoid resubmitting old GPS and rebuilding an expensive native map on each
# live ride-detail poll. Production GPS algorithms and persisted points remain unchanged.
python3 - "$NATIVE/NativeMainActivity.java" "$NATIVE/NativeRideDetailActivity.java" <<'PY'
import sys
from pathlib import Path
main=Path(sys.argv[1]).read_text(encoding="utf-8")
detail=Path(sys.argv[2]).read_text(encoding="utf-8")
start=main.split("private void createRideAndStartTracking(",1)[1].split("private JSONObject findChallengeById(",1)[0]
assert "age > 12000L" in start and "accuracy > 35f" in start, "preflight must expire before ride create"
assert "toast(\"برای شروع، GPS را دوباره بررسی کنید.\");" in start, "stale GPS error must be visible"
assert 'q.put("point_mode","live");' in detail, "historical detail should request bounded preview"
assert 'JSONObject b=fromPoll?null:api.bootstrap();' in detail, "live polling must not download full site bootstrap"
assert 'updateLiveRide();' in detail, "live ride details must refresh in place"
update=detail.split("private void updateLiveRide()",1)[1].split("private LinearLayout deferredMap()",1)[0]
assert 'profileMap.setPoints(points);' in update
assert 'profile.setPoints(points);' in update
assert 'new PedalNeshanMapSurface' not in update, "poll should not recreate native map renderer"
poll=detail.split("private final Runnable livePoll",1)[1].split("};",1)[0]
assert 'main.postDelayed(this' not in poll, "avoid overlapping live-poll requests"
print("PASS RC37 stale GPS preflight and bounded in-place detail refresh")
PY

# RC39: OLED affects active-ride HUD only, not home/secondary menus.
python3 - "$NATIVE" "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" <<'PY'
import sys
from pathlib import Path
native=Path(sys.argv[1])
bridge=Path(sys.argv[2]).read_text(encoding="utf-8")
get=lambda name:(native/name).read_text(encoding="utf-8")
theme=get("OledBatteryTheme.java")
surface=get("PedalNeshanMapSurface.java")
route=get("NativeRouteView.java")
main=get("NativeMainActivity.java")
screen=get("NativeScreenBase.java")
button=get("PedalActionView.java")
splash=get("SplashBrandLogo.java")
assert '"pedalpro_map_display"' in theme and '"amoled_enabled"' in theme
assert "OledBatteryTheme.enabled(context)" in surface
assert 'putBoolean(OledBatteryTheme.KEY_ENABLED,amoled)' in surface
assert "refreshControlPanels();" in surface and "onBatterySaverChanged(amoled)" in surface
assert "fullRideMode&&amoled?OledBatteryTheme.PANEL" in surface
assert "setOledRideMode(boolean enabled)" in button and "if(oledRideMode)" in button
assert "OledBatteryTheme.enabled" not in button and "OledBatteryTheme.enabled" not in screen
assert 'stageRoot.setBackgroundColor(BG);' in main
assert "siteBackground.setVisibility(OledBatteryTheme.enabled(this)" not in main
assert "for(PedalActionView control:new PedalActionView[]{live,pause,low,details,stop})" in main
assert "rideOverlayPanel(" in main and "setOledRideMode(oledRide)" in main
assert "OledBatteryTheme.enabled(this)?new int[]{Color.BLACK,Color.BLACK}" not in main
assert 'appRoot.setClipChildren(true);' in main
assert 'mainScroll.setClipToPadding(true);' in main
assert 'mainScroll.setClipChildren(true);' in main
assert 'webHeader.setElevation(dp(12));' in main
assert 'logo.setImageDrawable(SplashBrandLogo.load(this));' in main
assert 'R.drawable.pedalpro_app_icon' in splash
assert 'hideImmersiveRideOverlay();' in main and "showRide(); // Re-skin" in main
assert "routeGlow" in route and "routeHalo" in route and "if(amoledBatterySaver)" in route
assert "recordedGlow" in bridge and "recordedHalo" in bridge
assert "if(amoled){" in bridge and "recordedGlow=map.addPolyline" in bridge
assert "road?Color.rgb(222,241,247):Color.rgb(24,39,45)" in bridge
assert 'api.post("pause_ride"' not in main
print("PASS RC39 scoped OLED ride HUD, restored pages, clipped scroll header, transparent official logo and neon GPS")
PY

# RC40: attendance history and GPS ride deletion must be robust.
python3 - "$NATIVE/NativeMainActivity.java" "$ROOT/app/src/main/java/ir/pedalpro/app/TrackingService.java" <<'PY'
from pathlib import Path
import sys
main=Path(sys.argv[1]).read_text(encoding="utf-8")
service=Path(sys.argv[2]).read_text(encoding="utf-8")
assert 'bindAttendanceToggle(attended,regId,' in main
assert 'if(checked && completed)promptRetrospectiveAttendance(registrationId,control);' in main
assert 'bindAttendanceToggle(control,registrationId,true);' in main
assert 'bindAttendanceToggle(control,registrationId,completed);' in main
assert 'request.put("attended_at",attendedAt)' in main
section=main.split('private void discardRide(Button button)',1)[1].split('private void toggleManualPause',1)[0]
assert section.index('api.post("ride_delete"') < section.index('TrackingService.ACTION_DISCARD')
assert 'discard.putExtra("ride_id", rideId);' in section
assert 'TrackingService.ACTION_STOP' not in section
assert 'activeRide=null; // Deletion succeeded; refresh is non-critical.' in section
assert 'setProxyEnabled(button,true);' in section
assert 'discardTracking(intent.getIntExtra("ride_id", 0));' in service
discard=service.split('private void discardTracking(int expectedRideId)',1)[1].split('private void stopAllLocationUpdates',1)[0]
assert 'removePendingForRide(expectedRideId);' in discard
assert 'deleteRawLogForRide(expectedRideId);' in discard
assert discard.index('if (currentRideId != expectedRideId)') < discard.index('.putBoolean("active", false)')
assert '.remove("pending")' not in discard
assert 'if (rawWriterRideId == rideId) closeRawWriter();' in service
assert 'removeFirstPending(p);' in service
assert 'arr.getJSONObject(0).toString().equals(acknowledged.toString())' in service
print("PASS RC40 attendance, server-first ID-scoped delete and in-flight GPS queue safety")
PY

expect "$NATIVE/NativeGroupsActivity.java" 'pp_group_create' "Groups creation uses authenticated server endpoint"
expect "$NATIVE/NativeGroupsActivity.java" 'pp_group_invite_answer' "Group invite responses submitted to server"
expect "$NATIVE/NativeGroupChatActivity.java" 'pp_group_message_send' "Group chat submit is authenticated"

expect "$NATIVE/NativeFollowListActivity.java" 'pp_follow_list' "Profile follower lists server-authorized"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'ربات بله: متصل' "Admin member Bale status displayed"

expect "$NATIVE/NativeGroupsActivity.java" 'catch(org.json.JSONException e)' "Group JSON serialization errors are handled"

expect "$NATIVE/NativeGroupPointPicker.java" 'setOnMapTapListener' "Program selection on map"
expect "$NATIVE/NativeGroupsActivity.java" 'pp_group_invite_redeem' "Invitation link requires explicit user acceptance"

# RC42: owner-reported live-group issues, inbox and battery warning.
expect "$NATIVE/NativeMainActivity.java" 'authorizedOthers(result.optJSONArray("riders"))' "Own ride filtered out of nearby map"
expect "$NATIVE/NativeMainActivity.java" 'api.get("ride_live_points",args)' "Nearby peers fetch server-authorized live GPS history"
expect "$NATIVE/NativeMainActivity.java" 'peer.put("track_points",bounded)' "Separate peer track appended for map renderer"
expect "$NATIVE/NativeMainActivity.java" 'headerUnreadChat.setOnClickListener(v->showGroupChat());' "Header unread button opens native inbox"
expect "$NATIVE/NativeMainActivity.java" 'api.get("chat_bootstrap")' "Unread state sourced from authenticated chat"
expect "$NATIVE/NativeMainActivity.java" 'FirebaseConfigManager.sync(getApplicationContext())' "FCM token re-registered after signed-in bootstrap"
expect "$NATIVE/NativeMainActivity.java" 'Manifest.permission.POST_NOTIFICATIONS' "Android 13 notification permission requested"
expect "$NATIVE/NativeScreenBase.java" 'headerUnreadChat.setOnClickListener(v->openMain("chat"))' "Unread control visible on secondary screens"
expect "$NATIVE/NativeMainActivity.java" 'Settings.ACTION_APPLICATION_DETAILS_SETTINGS' "Pre-ride battery warning opens app settings"
expect "$NATIVE/NativeMainActivity.java" 'openLiveLinkInBrowser(deepLink)' "Public Beacon/SOS links not swallowed into home"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'roadEnabled="group".equals(mode);' "Group navigation defaults to cycling routing"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'routeView.setNearbyRiders(new JSONArray().put(peer))' "Live viewer shows selected rider separately"
expect "$NATIVE/NativeLiveNavigationActivity.java" 'selected.put("ride_id",rideId)' "Live viewer retains watched rider's route"
expect "$NATIVE/NativeSiteSettingsActivity.java" 'openAdminBaleReconnect(settings)' "Admin Bale reconnect available"
expect "$ROOT/app/src/neshanmaplibre/java/ir/pedalpro/app/nativeapp/NeshanMapLibreBridge.java" 'otherRiderRoutes.put(id,line)' "Authorized peer polylines drawn in Neshan map"
expect "$ROOT/app/src/main/java/ir/pedalpro/app/PedalProFirebaseMessagingService.java" 'group_message".equalsIgnoreCase(eventType)' "FCM chat notification routes to native inbox"

if [[ "$failed" -ne 0 ]]; then
  printf 'Native parity structural guards FAILED.\n' >&2
  exit 1
fi
printf 'Native parity structural guards passed. Build/device and source-reference QA remain separate gates.\n'
