package ir.pedalpro.app;

import android.app.Activity;
import android.os.Bundle;

public class RouteHistoryActivity extends Activity {
    private boolean mapLoaded = false;
    private final RouteRepository repository = new RouteRepository();
    private NeshanMapManager mapManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mapManager = new NeshanMapManager(this);
        // Route history stays lightweight. The map is created only after showMap().
    }

    public void showMap(long routeId) {
        if (mapLoaded) return;
        mapLoaded = true;
        mapManager.showMap();
        mapManager.fitRouteOnce();
    }

    public void deleteRoute(long routeId) {
        repository.delete(routeId);
    }
}
