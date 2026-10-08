package ir.pedalpro.app;

import android.app.Activity;
import android.os.Bundle;

public class RouteHistoryActivity extends Activity {

    private boolean mapLoaded = false;
    private final RouteRepository repository = new RouteRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lightweight route list only. Map is intentionally not initialized here.
    }

    public void showMap(long routeId) {
        if (!mapLoaded) {
            mapLoaded = true;
            NeshanMapManager.loadRoute(routeId);
        }
    }

    public void deleteRoute(long routeId) {
        repository.delete(routeId);
    }
}
