package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.provider.Settings;
import android.net.Uri;

/**
 * Legacy settings entrypoint: administrator controls now live exclusively
 * in the site-settings icon next to the account on NativeMainActivity.
 * No Neshan keys are exposed to non-admin users from this screen.
 */
public final class NativeAppSettingsActivity extends NativeScreenBase {
    public static final String EXTRA_SECTION="section";
    public static final String SECTION_MAP="map";
    public static final String PREFS="pedalpro_local_map_preferences";
    public static final String PREF_PREFER_ROADS="prefer_neshan_road_routes";

    @Override protected String screenTitle() { return "تنظیمات PedalPro"; }

    @Override protected void onScreenReady() {
        setSubtitle("بررسی مجوز دسترسی به تنظیمات…",MUTED);
        io.execute(()->{
            boolean admin=false;
            try{
                org.json.JSONObject boot=api.bootstrap();
                admin=boot.optBoolean("is_admin",false)||boot.optInt("is_admin",0)==1;
            }catch(Exception ignored){}
            final boolean allowed=admin;
            main.post(()->{
                if(isFinishing())return;
                if(allowed){
                    Intent settings=new Intent(this,NativeSiteSettingsActivity.class);
                    settings.putExtra("section","neshan");
                    startActivity(settings);
                    finish();
                }else{
                    setSubtitle("تنظیمات دستگاه",MUTED);
                    addCard("مجوزها و باتری",
                            "تنظیمات سایت، کلیدهای نشان و ربات بله فقط برای مدیر در دکمه تنظیمات کنار حساب کاربری قرار دارند.");
                    android.widget.Button permission=normal("تنظیمات مجوزهای Android");
                    permission.setOnClickListener(v->{
                        Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:"+getPackageName()));
                        startActivity(i);
                    });
                    content.addView(permission,cardLp());
                }
            });
        });
    }
}
