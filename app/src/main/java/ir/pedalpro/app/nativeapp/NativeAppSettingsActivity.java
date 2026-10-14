package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Native on-device settings hub. Local Neshan keys do not modify the public
 * PedalPro backend or expose site-administration privileges to regular members.
 */
public final class NativeAppSettingsActivity extends NativeScreenBase {
    public static final String EXTRA_SECTION="section";
    public static final String SECTION_MAP="map";
    public static final String PREFS="pedalpro_local_map_preferences";
    public static final String PREF_PREFER_ROADS="prefer_neshan_road_routes";

    private boolean isAdmin;
    private LinearLayout adminHost;
    private TextView mobileKeyStatus;
    private TextView serviceKeyStatus;

    @Override protected String screenTitle() { return "تنظیمات اپلیکیشن"; }

    @Override protected void onScreenReady() {
        setSubtitle("نقشه نشان، مسیریابی، GPS، حساب و تنظیمات مدیریت", GREEN);
        render();
        io.execute(()->{
            try{
                JSONObject boot=api.bootstrap();
                boolean admin=boot.optBoolean("is_admin",false) || boot.optInt("is_admin",0)==1;
                main.post(()->{
                    if(isFinishing())return;
                    isAdmin=admin;
                    renderAdminOptions();
                });
            }catch(Exception ignored) {
                // Local app preferences remain available without admin/bootstrap access.
            }
        });
    }

    private void render(){
        content.removeAllViews();
        LinearLayout intro=card();
        intro.addView(text("تنظیمات PedalPro",20,true,TEXT));
        intro.addView(text("کلیدهای نشان فقط روی همین دستگاه رمزگذاری می‌شوند. تنظیمات وب‌سایت و ابزارهای مدیریتی برای مدیر از بخش پایین قابل دسترسی هستند.",12,false,MUTED));
        content.addView(intro,cardLp());

        renderMapSettings();
        renderDeviceSettings();

        adminHost=new LinearLayout(this);
        adminHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(adminHost,cardLp());
        renderAdminOptions();
        VazirmatnLoader.apply(this,content);
    }

    private void renderMapSettings(){
        LinearLayout map=accentCard(Color.rgb(27,213,237));
        map.addView(text("نقشه و مسیریابی نشان",18,true,TEXT));
        map.addView(text("کلید MOBILE برای نمایش نقشه و کلید «سرویس‌ها» برای درخواست مسیر خیابانی دو دسترسی متفاوت‌اند.",12,false,MUTED));

        mobileKeyStatus=text("",12,true,GREEN);
        mobileKeyStatus.setPadding(0,dp(11),0,dp(6));
        map.addView(mobileKeyStatus);
        EditText mobile=input("کلید MOBILE نشان");
        mobile.setSingleLine(true);
        mobile.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mobile.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        mobile.setContentDescription("کلید MOBILE نشان؛ مقدار قبلی نمایش داده نمی‌شود");
        map.addView(mobile);
        Button saveMobile=primary("ذخیره کلید MOBILE");
        saveMobile.setOnClickListener(v->{
            try {
                String value=mobile.getText().toString().trim();
                if(value.isEmpty()) {toast("برای جایگزینی، کلید MOBILE جدید را وارد کنید.");return;}
                NeshanServiceCredentials.saveMobile(this,value);
                mobile.setText("");
                updateKeyStatus();
                toast("کلید MOBILE در Android Keystore رمزگذاری شد.");
            }catch(Exception e){toast(e.getMessage());}
        });
        map.addView(saveMobile);
        Button clearMobile=normal("حذف کلید MOBILE از گوشی");
        clearMobile.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
                .setTitle("حذف کلید MOBILE")
                .setMessage("کلید نقشه ذخیره‌شده فقط از این گوشی پاک شود؟")
                .setNegativeButton("انصراف",null)
                .setPositiveButton("حذف",(d,w)->{
                    NeshanServiceCredentials.clearMobile(this);
                    mobile.setText("");
                    updateKeyStatus();
                }).show());
        map.addView(clearMobile);
        TextView mapNotice=text("وضعیت: کلید MOBILE ذخیره می‌شود؛ نسخه پیش‌فرض نقشه هنوز از XYZ استفاده می‌کند. نقشه رسمی نشان به اتصال SDK/MapLibre معتبر و مجوز مخصوص نیاز دارد؛ ذخیره این کلید به‌تنهایی نقشه را تغییر نمی‌دهد.",11,false,ORANGE);
        mapNotice.setPadding(0,dp(10),0,dp(10));
        map.addView(mapNotice);

        serviceKeyStatus=text("",12,true,GREEN);
        serviceKeyStatus.setPadding(0,dp(15),0,dp(6));
        map.addView(serviceKeyStatus);
        EditText routing=input("کلید تب «سرویس‌ها»ی نشان (Routing API)");
        routing.setSingleLine(true);
        routing.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        routing.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        routing.setContentDescription("کلید سرویس مسیریابی نشان؛ مقدار قبلی نمایش داده نمی‌شود");
        map.addView(routing);
        Button saveRoute=primary("ذخیره کلید مسیریابی");
        saveRoute.setOnClickListener(v->{
            try {
                String value=routing.getText().toString().trim();
                if(value.isEmpty()) {toast("برای جایگزینی، کلید جدید سرویس را وارد کنید.");return;}
                NeshanServiceCredentials.save(this,value);
                routing.setText("");
                updateKeyStatus();
                toast("کلید مسیریابی رمزگذاری و ذخیره شد.");
            }catch(Exception e){toast(e.getMessage());}
        });
        map.addView(saveRoute);

        Button clearRoute=normal("حذف کلید مسیریابی از گوشی");
        clearRoute.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
                .setTitle("حذف کلید مسیریابی")
                .setMessage("کلید سرویس نشان فقط از این گوشی پاک شود؟")
                .setNegativeButton("انصراف",null)
                .setPositiveButton("حذف",(d,w)->{
                    NeshanServiceCredentials.clear(this);
                    routing.setText("");
                    updateKeyStatus();
                }).show());
        map.addView(clearRoute);

        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        CheckBox preferRoad=new CheckBox(this);
        preferRoad.setText("مسیریابی خیابانی نشان هنگام باز کردن مسیریابی زنده فعال باشد");
        preferRoad.setTextSize(13);
        preferRoad.setTextColor(TEXT);
        preferRoad.setChecked(prefs.getBoolean(PREF_PREFER_ROADS,false));
        preferRoad.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(PREF_PREFER_ROADS,checked).apply());
        map.addView(preferRoad);
        TextView note=text("نکته: خروجی سرویس مسیریابی نشان ممکن است مسیر خودرو باشد؛ عبورپذیری و ETA دوچرخه را تضمین نمی‌کند. کلید سرویس را برای استفاده عمومی کاربران باید در سرور نگهداری کرد، نه به‌صورت مشترک در APK.",11,false,MUTED);
        note.setPadding(0,dp(7),0,0);
        map.addView(note);

        content.addView(map,cardLp());
        updateKeyStatus();
    }

    private void updateKeyStatus(){
        if(mobileKeyStatus!=null){
            mobileKeyStatus.setText(NeshanServiceCredentials.hasMobileKey(this)?
                    "MOBILE: ذخیره‌شده روی گوشی":"MOBILE: تنظیم نشده");
            mobileKeyStatus.setTextColor(NeshanServiceCredentials.hasMobileKey(this)?GREEN:ORANGE);
        }
        if(serviceKeyStatus!=null){
            serviceKeyStatus.setText(NeshanServiceCredentials.hasKey(this)?
                    "Routing API: ذخیره‌شده روی گوشی":"Routing API: تنظیم نشده");
            serviceKeyStatus.setTextColor(NeshanServiceCredentials.hasKey(this)?GREEN:ORANGE);
        }
    }

    private void renderDeviceSettings(){
        LinearLayout device=card();
        device.addView(text("تنظیمات گوشی و GPS",17,true,TEXT));
        device.addView(text("این بخش فقط مجوزهای سیستم‌عامل را باز می‌کند و تنظیمات ثبت GPS را بازنویسی نمی‌کند.",11,false,MUTED));
        device.addView(text("Android "+Build.VERSION.RELEASE+" · "+Build.MANUFACTURER+" "+Build.MODEL,12,false,TEXT));
        Button nativeSettings=normal("باز کردن تنظیمات و مجوزهای PedalPro");
        nativeSettings.setOnClickListener(v->{
            Intent i=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:"+getPackageName()));
            try{startActivity(i);}catch(Exception e){toast("تنظیمات Android در دسترس نیست.");}
        });
        device.addView(nativeSettings);
        content.addView(device,cardLp());
    }

    private void renderAdminOptions(){
        if(adminHost==null)return;
        adminHost.removeAllViews();
        if(!isAdmin)return;
        LinearLayout admin=accentCard(Color.rgb(255,190,90));
        admin.addView(text("مدیریت سایت و اپلیکیشن",18,true,TEXT));
        admin.addView(text("این همان پنل مدیریتی PedalPro است؛ تمام بخش‌های موجود Native از همین‌جا باز می‌شوند. دسترسی برای اعضای عادی نمایش داده نمی‌شود.",12,false,MUTED));
        addAdminLink(admin,"عمومی، محتوای سایت و SEO","general");
        addAdminLink(admin,"SEO و Robots (ایندکس، متا و HEAD)","seo");
        addAdminLink(admin,"ظاهر سایت و اپلیکیشن","appearance");
        addAdminLink(admin,"مدال‌ها و محاسبه مجدد","medals");
        addAdminLink(admin,"کاربران و نقش‌ها","users");
        addAdminLink(admin,"پیامک، OTP و گزارش‌ها","sms");
        addAdminLink(admin,"نسخه‌ها، انتشار APK و اعلان‌ها","appmanager");
        addAdminLink(admin,"Push و Firebase FCM","push");
        addAdminLink(admin,"وضعیت سیستم","system");
        adminHost.addView(admin,cardLp());
        VazirmatnLoader.apply(this,adminHost);
    }

    private void addAdminLink(LinearLayout parent,String title,String tab){
        Button link=normal(title+"  ←");
        link.setOnClickListener(v->{
            Intent i=new Intent(this,NativeSiteSettingsActivity.class);
            i.putExtra("section",tab);
            startActivity(i);
        });
        parent.addView(link);
    }
}
