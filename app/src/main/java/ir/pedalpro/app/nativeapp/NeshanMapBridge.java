package ir.pedalpro.app.nativeapp;

import android.view.View;
import org.json.JSONArray;

/**
 * Adapter contract isolating the licensed Neshan SDK from the default APK.
 * A map key or routing API key alone never enables an SDK licence.
 */
public interface NeshanMapBridge {
    View view();
    void setNavigation(boolean enabled, boolean headingUp, float courseDegrees, boolean courseValid);
    void setCurrent(double lat, double lng);
    void setRecordedTrack(JSONArray track);
    void setSuggestedRoute(JSONArray route);
    void setTarget(double lat, double lng);
    void focusOn(double lat, double lng);
    void setNearbyRiders(JSONArray riders);
    void clearTarget();
    void zoomBy(int delta);
    void recenter();
    void setNight(boolean enabled);
    void setTraffic(boolean enabled);
}
