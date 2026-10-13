package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent native HTTP session state.
 * No WebView or browser cookie ownership is required.
 */
public final class SessionStore {
    public static final String PREFS = "pedalpro_native_session";
    private static final String KEY_COOKIE = "cookie";
    private static final String KEY_CSRF = "csrf";
    private static final String KEY_USER_JSON = "user_json";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String cookie() { return prefs.getString(KEY_COOKIE, ""); }
    public String csrf() { return prefs.getString(KEY_CSRF, ""); }
    public String userJson() { return prefs.getString(KEY_USER_JSON, ""); }

    public void saveCookie(String value) {
        if (value != null && !value.isBlank()) prefs.edit().putString(KEY_COOKIE, value).apply();
    }

    public void saveCsrf(String value) {
        if (value != null && !value.isBlank()) prefs.edit().putString(KEY_CSRF, value).apply();
    }

    public void saveUserJson(String value) {
        prefs.edit().putString(KEY_USER_JSON, value == null ? "" : value).apply();
    }

    public boolean isSignedIn() {
        return !cookie().isBlank() && !userJson().isBlank();
    }

    public void clear() { prefs.edit().clear().apply(); }
}
