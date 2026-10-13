package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

/** Native session storage. Replaces WebView cookie/session ownership. */
public final class SessionStore {
    private static final String PREFS = "pedalpro_native_session";
    private static final String KEY_TOKEN = "access_token";
    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token == null ? "" : token).apply();
    }

    public String token() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public boolean isSignedIn() {
        String token = token();
        return token != null && !token.isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
