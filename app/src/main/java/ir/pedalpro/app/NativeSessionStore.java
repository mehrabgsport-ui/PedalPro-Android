package ir.pedalpro.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class NativeSessionStore {
    private static final String PREFS = "pedalpro_native_session";
    private static final String KEY_COOKIE = "cookie";
    private static final String KEY_CSRF = "csrf";

    private final SharedPreferences prefs;

    public NativeSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String cookie() { return prefs.getString(KEY_COOKIE, ""); }
    public String csrf() { return prefs.getString(KEY_CSRF, ""); }

    public void setCookie(String value) {
        if (value == null || value.isBlank()) return;
        String cookie = value.split(";", 2)[0].trim();
        if (!cookie.isEmpty()) prefs.edit().putString(KEY_COOKIE, cookie).apply();
    }

    public void setCsrf(String value) {
        if (value == null) return;
        prefs.edit().putString(KEY_CSRF, value).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public static String cookie(Context context) {
        return new NativeSessionStore(context).cookie();
    }

    public static String csrf(Context context) {
        return new NativeSessionStore(context).csrf();
    }
}
