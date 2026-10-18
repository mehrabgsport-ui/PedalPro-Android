package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Color;

/**
 * Shared UI-only OLED preference. Affects presentation, not GPS recording or
 * route geometry. The settings key is already used by PedalNeshanMapSurface.
 */
public final class OledBatteryTheme {
    public static final String PREFS = "pedalpro_map_display";
    public static final String KEY_ENABLED = "amoled_enabled";
    public static final int PANEL = Color.BLACK;
    public static final int BORDER = Color.argb(64, 170, 188, 198);

    private OledBatteryTheme() {}

    public static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }
}
