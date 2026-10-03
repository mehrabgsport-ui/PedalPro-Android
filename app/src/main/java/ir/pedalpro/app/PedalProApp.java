package ir.pedalpro.app;

import android.app.Application;

public class PedalProApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        FirebaseConfigManager.sync(getApplicationContext());
    }
}
