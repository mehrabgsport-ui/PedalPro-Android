package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Per-device encrypted Neshan service credential. Never put a service key in BuildConfig,
 * source control, crash logs or an exported Intent. Production should proxy on the server.
 */
final class NeshanServiceCredentials {
    private static final String STORE = "pedalpro_neshan_nav";
    private static final String ALIAS = "pedalpro_neshan_service_aes_v1";
    private static final String VALUE = "secret";
    private static final String IV = "nonce";
    private static final String MOBILE_VALUE = "mobile_secret";
    private static final String MOBILE_IV = "mobile_nonce";

    private NeshanServiceCredentials() {}

    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry existing=store.getEntry(ALIAS,null);
        if(existing instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    static boolean hasKey(Context c) {
        return !c.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString(VALUE,"").isEmpty();
    }

    static String read(Context c) {
        try {
            SharedPreferences prefs=c.getSharedPreferences(STORE,Context.MODE_PRIVATE);
            String encrypted=prefs.getString(VALUE,""),nonce=prefs.getString(IV,"");
            if(encrypted.isEmpty() || nonce.isEmpty())return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(nonce,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception ignored) {
            return ""; // Wrong/restored Android keystore never leaks a stored credential.
        }
    }

    static void save(Context c,String raw) throws Exception {
        String value=raw == null ? "" : raw.trim();
        if(value.isEmpty()) { clear(c); return; }
        if(value.length()<12 || value.length()>512 || value.contains(" ") ||
                value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("کلید سرویس نشان معتبر نیست.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        String ciphertext=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
        String nonce=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        if(!c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
                .putString(VALUE,ciphertext).putString(IV,nonce).commit())
            throw new IllegalStateException("ذخیره کلید سرویس ممکن نشد.");
    }

    static void clear(Context c) {
        c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove(VALUE).remove(IV).apply();
    }
    static boolean hasMobileKey(Context c) {
        return !c.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString(MOBILE_VALUE,"").isEmpty();
    }

    static String readMobile(Context c) {
        try {
            SharedPreferences prefs=c.getSharedPreferences(STORE,Context.MODE_PRIVATE);
            String encrypted=prefs.getString(MOBILE_VALUE,""),nonce=prefs.getString(MOBILE_IV,"");
            if(encrypted.isEmpty() || nonce.isEmpty())return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),
                    new GCMParameterSpec(128,Base64.decode(nonce,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception ignored) { return ""; }
    }

    static void saveMobile(Context c,String raw) throws Exception {
        String value=raw == null ? "" : raw.trim();
        if(value.isEmpty()) { clearMobile(c); return; }
        if(value.length()<12 || value.length()>512 || value.contains(" ") ||
                value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("کلید MOBILE نشان معتبر نیست.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        String ciphertext=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
        String nonce=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        if(!c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
                .putString(MOBILE_VALUE,ciphertext).putString(MOBILE_IV,nonce).commit())
            throw new IllegalStateException("ذخیره کلید MOBILE ممکن نشد.");
    }

    static void clearMobile(Context c) {
        c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
                .remove(MOBILE_VALUE).remove(MOBILE_IV).apply();
    }

}
