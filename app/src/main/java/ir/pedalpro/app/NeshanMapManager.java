package ir.pedalpro.app;

import android.content.Context;

public class NeshanMapManager {
    private final Context context;
    private boolean visible = false;

    public NeshanMapManager(Context context) {
        this.context = context;
    }

    public void showMap() {
        visible = true;
    }

    public void hideMap() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void fitRouteOnce() {
        // Initial route framing only. User zoom state must be preserved afterwards.
    }
}
