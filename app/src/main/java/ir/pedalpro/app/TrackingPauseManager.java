package ir.pedalpro.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class TrackingPauseManager {
    private static final String PREFS = "pedalpro_pause_state";
    private static final String KEY_PAUSED = "paused";

    private TrackingPauseManager() {}

    public static void pause(Context context) {
        prefs(context).edit().putBoolean(KEY_PAUSED, true).apply();
    }

    public static void resume(Context context) {
        prefs(context).edit().putBoolean(KEY_PAUSED, false).apply();
    }

    public static boolean isPaused(Context context) {
        return prefs(context).getBoolean(KEY_PAUSED, false);
    }

    public static float displaySpeed(float currentSpeed) {
        return isPausedFlag ? 0f : currentSpeed;
    }

    private static boolean isPausedFlag = false;

    public static void setDisplayPaused(boolean paused) {
        isPausedFlag = paused;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
