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
/** Protects the native session with AndroidKeyStore AES-GCM and migrates legacy plaintext. */
public final class SessionStore {
    public static final String PREFS="pedalpro_native_session";
    private static final String ALIAS="pedalpro_native_session_aes_v2";
    private final SharedPreferences p;
    public SessionStore(Context c){p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        KeyStore.Entry entry=ks.getEntry(ALIAS,null);
        if(entry instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return gen.generateKey();
    }
    private static String seal(String x)throws Exception{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+
                Base64.encodeToString(c.doFinal(x.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    private static String open(String x)throws Exception{
        int at=x.indexOf(':');if(at<1)throw new IllegalArgumentException("Invalid envelope");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(x.substring(0,at),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(x.substring(at+1),Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    private synchronized String read(String k){
        String enc=p.getString("protected_"+k,"");
        if(!enc.isBlank()){try{return open(enc);}catch(Exception e){return "";}}
        String old=p.getString(k,"");
        if(old.isBlank())return "";
        try{
            if(p.edit().putString("protected_"+k,seal(old)).remove(k).commit())return old;
        }catch(Exception ignored){}
        return "";
    }
    private synchronized void write(String k,String value){
        try{
            if(!p.edit().putString("protected_"+k,seal(value)).remove(k).commit())
                throw new IllegalStateException("Encrypted storage failed");
        }catch(Exception e){throw new IllegalStateException("AndroidKeystore session unavailable",e);}
    }
    public String cookie(){return read("cookie");}
    public String csrf(){return read("csrf");}
    public String userJson(){return read("user_json");}
    public void saveCookie(String x){if(x!=null&&!x.isBlank())write("cookie",x);}
    public void saveCsrf(String x){if(x!=null&&!x.isBlank())write("csrf",x);}
    public void saveUserJson(String x){write("user_json",x==null?"":x);}
    public boolean isSignedIn(){return !cookie().isBlank()&&!userJson().isBlank();}
    public void clear(){p.edit().clear().commit();}
}
