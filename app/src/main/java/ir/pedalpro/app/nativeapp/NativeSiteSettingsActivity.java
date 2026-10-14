package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.text.InputType;
import android.content.SharedPreferences;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Native mirror of PedalPro web #settings.
 * Labels, tab order and API actions follow the 4.7.46 website.
 */
public final class NativeSiteSettingsActivity extends NativeScreenBase {
    private JSONObject data;
    private final ArrayList<Button> tabButtons = new ArrayList<>();
    private String activeTab = "general";
    private NativeAppReleaseAdminController appReleaseAdmin;
    private NativeFcmAdminController fcmAdmin;

    @Override protected String screenTitle() { return "تنظیمات سایت"; }

    @Override protected void onScreenReady() {
        appReleaseAdmin = new NativeAppReleaseAdminController(this, api, io);
        fcmAdmin = new NativeFcmAdminController(this, api, io);
        String requested = getIntent().getStringExtra("section");
        load(isValidSection(requested) ? requested : "general");
    }

    private boolean isValidSection(String tab) {
        if (tab == null) return false;
        switch (tab) {
            case "general": case "seo": case "appearance": case "medals":
            case "users": case "sms": case "appmanager": case "push": case "system":
            case "neshan": case "bale": case "other":
                return true;
            default: return false;
        }
    }

    private void load(String tab) {
        activeTab = tab == null ? "general" : tab;
        content.removeAllViews();
        setSubtitle("در حال دریافت تنظیمات…", MUTED);
        io.execute(() -> {
            try {
                JSONObject d = api.bootstrap();
                if (!(d.optBoolean("is_admin", false) || d.optInt("is_admin", 0) == 1)) {
                    main.post(() -> {
                        setSubtitle("دسترسی مدیر لازم است", RED);
                        addCard("تنظیمات سایت", "این بخش فقط برای مدیر نمایش داده می‌شود.");
                    });
                    return;
                }
                data = d;
                main.post(this::render);
            } catch (Exception e) {
                main.post(() -> {
                    setSubtitle("خطا در دریافت تنظیمات", RED);
                    addCard("خطا", e.getMessage());
                });
            }
        });
    }

    private void render() {
        content.removeAllViews();
        setSubtitle("مدیریت PedalPro", GREEN);
        renderTabs();
        switch (activeTab) {
            case "neshan": renderNeshan(); break;
            case "bale": renderBale(); break;
            case "other": renderUnmirroredSiteSettings(); break;
            case "seo": renderSeo(); break;
            case "appearance": renderAppearance(); break;
            case "medals": renderMedals(); break;
            case "users": renderUsers(); break;
            case "sms": renderSms(); break;
            case "appmanager": renderAppManager(); break;
            case "push": renderPush(); break;
            case "system": renderSystem(); break;
            default: renderGeneral(); break;
        }
        VazirmatnLoader.apply(this, content);
    }

    private void renderTabs() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tabButtons.clear();

        addTab(row, "general", "عمومی");
        addTab(row, "seo", "SEO / Robots");
        addTab(row, "neshan", "نقشه نشان");
        addTab(row, "bale", "ربات بله");
        addTab(row, "appearance", "ظاهر سایت و اپ");
        addTab(row, "medals", "مدال‌ها");
        addTab(row, "users", "کاربران");
        addTab(row, "sms", "پیامک");
        addTab(row, "appmanager", "اپلیکیشن و اعلان‌ها");
        addTab(row, "push", "Push");
        addTab(row, "system", "سیستم");
        addTab(row, "other", "سایر تنظیمات سایت");

        hsv.addView(row);
        content.addView(hsv, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void addTab(LinearLayout row, String key, String label) {
        Button b = normal(label);
        b.setTag(key);
        b.setTextColor(key.equals(activeTab) ? Color.rgb(5,34,53) : TEXT);
        if (key.equals(activeTab)) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(20,223,237), Color.rgb(36,185,237)});
            g.setCornerRadius(dp(999));
            b.setBackground(g);
        }
        b.setOnClickListener(v -> load(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(label.length() > 10 ? 142 : 104), dp(44));
        lp.setMargins(dp(4), dp(5), dp(4), dp(5));
        row.addView(b, lp);
        tabButtons.add(b);
    }

    /** Device-local API credentials: never present, log, or post a stored secret to bootstrap. */
    private void renderNeshan() {
        LinearLayout box=card();
        box.addView(text("تنظیمات نقشه و مسیریابی نشان",18,true,TEXT));
        box.addView(text("کلید MOBILE و کلید سرویس مسیریابی جدا هستند. ذخیره آن‌ها در Android Keystore فقط برای همین دستگاه است و به‌تنهایی SDK نشان را فعال نمی‌کند.",12,false,MUTED));
        TextView mobileStatus=text(
                NeshanServiceCredentials.hasMobileKey(this)?"MOBILE: ذخیره‌شده":"MOBILE: تنظیم نشده",
                12,true,NeshanServiceCredentials.hasMobileKey(this)?GREEN:ORANGE);
        box.addView(mobileStatus);
        EditText mobile=input("کلید MOBILE نشان");
        mobile.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mobile.setSingleLine(true);
        mobile.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        box.addView(mobile);
        Button saveMobile=primary("ذخیره کلید MOBILE");
        saveMobile.setOnClickListener(v->{
            try {
                String value=mobile.getText().toString().trim();
                if(value.isEmpty()){toast("کلید MOBILE را وارد کنید.");return;}
                NeshanServiceCredentials.saveMobile(this,value);
                mobile.setText("");
                mobileStatus.setText("MOBILE: ذخیره‌شده");
                mobileStatus.setTextColor(GREEN);
                toast("کلید MOBILE روی این دستگاه ذخیره شد.");
            }catch(Exception e){toast(e.getMessage());}
        });
        box.addView(saveMobile);

        TextView routingStatus=text(
                NeshanServiceCredentials.hasKey(this)?"Routing API: ذخیره‌شده":"Routing API: تنظیم نشده",
                12,true,NeshanServiceCredentials.hasKey(this)?GREEN:ORANGE);
        routingStatus.setPadding(0,dp(10),0,0);
        box.addView(routingStatus);
        EditText routing=input("کلید تب سرویس‌های نشان (Routing API)");
        routing.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        routing.setSingleLine(true);
        routing.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        box.addView(routing);
        Button saveRouting=primary("ذخیره کلید مسیریابی");
        saveRouting.setOnClickListener(v->{
            try {
                String value=routing.getText().toString().trim();
                if(value.isEmpty()){toast("کلید Routing API را وارد کنید.");return;}
                NeshanServiceCredentials.save(this,value);
                routing.setText("");
                routingStatus.setText("Routing API: ذخیره‌شده");
                routingStatus.setTextColor(GREEN);
                toast("کلید مسیریابی روی این دستگاه ذخیره شد.");
            }catch(Exception e){toast(e.getMessage());}
        });
        box.addView(saveRouting);

        SharedPreferences prefs=getSharedPreferences(NativeAppSettingsActivity.PREFS,MODE_PRIVATE);
        CheckBox followRouting=new CheckBox(this);
        followRouting.setText("پیشنهاد مسیر خیابانی هنگام بازکردن مسیریابی زنده");
        followRouting.setTextColor(TEXT);
        followRouting.setChecked(prefs.getBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,false));
        followRouting.setOnCheckedChangeListener((b,checked)->
                prefs.edit().putBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,checked).apply());
        box.addView(followRouting);
        box.addView(text("کلید MOBILE ذخیره می‌شود اما APK فعلی بدون لایسنس Android SDK نشان، نقشه جایگزین XYZ را نشان می‌دهد. API مسیریابی، مسیر تأییدشده مخصوص دوچرخه نیست.",11,false,ORANGE));
        Button delete=normal("پاک‌کردن کلیدهای نشان از این گوشی");
        delete.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
                .setTitle("حذف کلیدهای نشان")
                .setMessage("هر دو کلید از حافظه امن همین گوشی حذف شوند؟")
                .setNegativeButton("انصراف",null)
                .setPositiveButton("حذف",(dialog,which)->{
                    NeshanServiceCredentials.clearMobile(this);
                    NeshanServiceCredentials.clear(this);
                    mobile.setText("");routing.setText("");
                    mobileStatus.setText("MOBILE: تنظیم نشده");
                    routingStatus.setText("Routing API: تنظیم نشده");
                    mobileStatus.setTextColor(ORANGE);routingStatus.setTextColor(ORANGE);
                }).show());
        box.addView(delete);
        content.addView(box,cardLp());
    }

    /**
     * Reflect only real Bale values already returned by the authenticated site bootstrap.
     * No guessed Bale API action or webhook mutation: we do not know the website's
     * bot-controller request/response contract from this Android repository.
     */
    private void renderBale() {
        LinearLayout box=card();
        box.addView(text("ربات بله",18,true,TEXT));
        box.addView(text("این قسمت مربوط به ربات پیام‌رسان بله است، نه Robots موتور جستجو. فقط داده‌های واقعی دریافتی از API مدیریتی سایت نمایش داده می‌شوند؛ توکن و Webhook محرمانه نمایش داده نمی‌شوند.",12,false,MUTED));
        int displayed=0;
        String[] candidates={"bale_bot_settings","bale_settings","bale_bot","bale","bale_status","bale_webhook","bale_bots","bot_bale"};
        for(String key:candidates) {
            if(data==null||data.isNull(key)||!data.has(key))continue;
            Object object=data.opt(key);
            if(object instanceof JSONObject) {
                displayed+=appendBaleFields(box,(JSONObject)object,key,0);
            } else if(object instanceof JSONArray) {
                JSONArray array=(JSONArray)object;
                for(int i=0;i<Math.min(array.length(),15);i++) {
                    JSONObject row=array.optJSONObject(i);
                    if(row!=null)displayed+=appendBaleFields(box,row,key+" / "+(i+1),0);
                }
            } else if(object!=null) {
                if(!isPrivateBaleField(key)) {
                    box.addView(text(key+": "+String.valueOf(object),12,false,TEXT));
                    displayed++;
                }
            }
        }
        // Some web backends expose Bale flags as flat fields of site/settings
        // instead of a dedicated object; discover only real non-secret fields.
        displayed+=appendBaleFlatFields(box,data,"bootstrap");
        displayed+=appendBaleFlatFields(box,data==null?null:data.optJSONObject("site"),"site");
        displayed+=appendBaleFlatFields(box,data==null?null:data.optJSONObject("ui_settings"),"ui_settings");
        JSONObject settings=data==null?null:data.optJSONObject("settings");
        displayed+=appendBaleFlatFields(box,settings,"settings");
        if(settings!=null)for(String key:candidates) {
            JSONObject value=settings.optJSONObject(key);
            if(value!=null)displayed+=appendBaleFields(box,value,key,0);
        }
        if(displayed==0) {
            box.addView(text("پاسخ فعلی سایت، تنظیمات ربات بله را در bootstrap برنمی‌گرداند؛ امکان نمایش یا ویرایش معتبر آن هنوز وجود ندارد. برای انتقال دقیق، قرارداد API یا فایل تنظیمات وب لازم است.",12,false,ORANGE));
        }else{
            box.addView(text("وضعیت بالا از سایت خوانده شده است. ذخیره/تغییر توکن و Webhook غیرفعال است تا API واقعی سایت مشخص شود؛ هیچ مقدار فرضی به سرور ارسال نمی‌شود.",11,false,ORANGE));
        }
        Button refresh=normal("دریافت مجدد تنظیمات از سایت");
        refresh.setOnClickListener(v->load("bale"));
        box.addView(refresh);
        content.addView(box,cardLp());
    }

    private int appendBaleFlatFields(LinearLayout box,JSONObject source,String scope){
        if(source==null)return 0;
        int count=0;
        java.util.Iterator<String> keys=source.keys();
        while(keys.hasNext()&&count<30){
            String key=keys.next();
            String normalized=key.toLowerCase(Locale.ROOT);
            if(!(normalized.contains("bale")||normalized.startsWith("bot_"))||
                    normalized.contains("robots")||isPrivateBaleField(key)||source.isNull(key))
                continue;
            Object val=source.opt(key);
            if(val instanceof Number||val instanceof Boolean||val instanceof String){
                String safe=String.valueOf(val);
                if(safe.length()>160)safe=safe.substring(0,160)+"…";
                box.addView(text(scope+"."+key+": "+safe,12,false,TEXT));
                count++;
            }
        }
        return count;
    }

    private int appendBaleFields(LinearLayout box,JSONObject values,String label,int depth){
        if(values==null||depth>1)return 0;
        box.addView(text(label,13,true,GREEN));
        int shown=0;
        java.util.Iterator<String> keys=values.keys();
        while(keys.hasNext()&&shown<60){
            String key=keys.next();
            if(isPrivateBaleField(key)||values.isNull(key))continue;
            Object value=values.opt(key);
            if(value instanceof JSONObject) {
                shown+=appendBaleFields(box,(JSONObject)value,key,depth+1);
            }else if(value instanceof Boolean||value instanceof Number||value instanceof String){
                box.addView(text(key+" : "+String.valueOf(value),12,false,TEXT));
                shown++;
            }
        }
        return shown;
    }

    private boolean isPrivateBaleField(String key){
        String name=key==null?"":key.toLowerCase(Locale.ROOT);
        return name.contains("token")||name.contains("secret")||name.contains("pass")
                ||name.contains("key")||name.contains("auth")||name.contains("webhook_url")
                ||name.contains("chat_id")||name.contains("phone")||name.contains("session");
    }

    private void renderUnmirroredSiteSettings(){
        LinearLayout box=card();
        box.addView(text("سایر تنظیمات واقعی سایت",18,true,TEXT));
        box.addView(text("فیلدهای اضافی که اکنون در تنظیمات عمومی/ظاهر مدیریت نمی‌شوند، فقط برای مشاهده و بررسی تطابق نمایش داده می‌شوند. مقادیر محرمانه حذف می‌شوند.",12,false,MUTED));
        JSONObject site=data==null?null:data.optJSONObject("site");
        JSONObject ui=data==null?null:data.optJSONObject("ui_settings");
        java.util.HashSet<String> known=new java.util.HashSet<>();
        java.util.Collections.addAll(known,
                "club_name","club_subtitle","about_title","about_body",
                "seo_title","seo_description","seo_keywords","seo_robots","seo_extra_head",
                "mobile_header_top_gap_px","home_eyebrow","home_tagline",
                "home_primary_button_text","home_secondary_button_text","ride_button_text");
        int count=appendExtraSettingFields(box,site,"site",known);
        count+=appendExtraSettingFields(box,ui,"ui_settings",known);
        if(count==0)box.addView(text("فیلد اضافیِ قابل مشاهده‌ای در پاسخ فعلی سایت وجود ندارد.",12,false,MUTED));
        content.addView(box,cardLp());
    }

    private int appendExtraSettingFields(LinearLayout box,JSONObject fields,String label,java.util.Set<String> known){
        if(fields==null)return 0;
        int count=0;
        java.util.Iterator<String> iter=fields.keys();
        while(iter.hasNext()&&count<100){
            String key=iter.next();
            if(known.contains(key)||isPrivateBaleField(key)||fields.isNull(key))continue;
            Object value=fields.opt(key);
            if(value instanceof String||value instanceof Number||value instanceof Boolean){
                String safe=String.valueOf(value);
                if(safe.length()>220)safe=safe.substring(0,220)+"…";
                box.addView(text(label+"."+key+" : "+safe,11,false,TEXT));
                count++;
            }
        }
        return count;
    }

    private void renderGeneral() {
        JSONObject site = data.optJSONObject("site");
        if (site == null) site = new JSONObject();

        LinearLayout box = card();
        box.addView(text("تنظیمات عمومی", 18, true, TEXT));
        EditText clubName = input("نام باشگاه");
        clubName.setText(site.optString("club_name", ""));
        EditText clubSubtitle = input("زیرعنوان باشگاه");
        clubSubtitle.setText(site.optString("club_subtitle", ""));
        EditText aboutTitle = input("عنوان صفحه درباره ما");
        aboutTitle.setText(site.optString("about_title", ""));
        EditText aboutBody = input("مطلب درباره ما");
        aboutBody.setSingleLine(false); aboutBody.setMinLines(5);
        aboutBody.setText(site.optString("about_body", ""));
        EditText seoTitle = input("SEO Title");
        seoTitle.setText(site.optString("seo_title", ""));
        EditText seoDescription = input("Meta Description");
        seoDescription.setSingleLine(false); seoDescription.setMinLines(3);
        seoDescription.setText(site.optString("seo_description", ""));
        EditText seoKeywords = input("Meta Keywords");
        seoKeywords.setText(site.optString("seo_keywords", ""));
        EditText seoRobots = input("Robots");
        seoRobots.setText(site.optString("seo_robots", "index,follow"));
        EditText seoExtra = input("کدهای اضافی داخل HEAD");
        seoExtra.setSingleLine(false); seoExtra.setMinLines(4);
        seoExtra.setText(site.optString("seo_extra_head", ""));

        box.addView(clubName); box.addView(clubSubtitle); box.addView(aboutTitle); box.addView(aboutBody);
        box.addView(seoTitle); box.addView(seoDescription); box.addView(seoKeywords); box.addView(seoRobots); box.addView(seoExtra);
        Button save = primary("ذخیره تنظیمات");
        save.setOnClickListener(v -> {
            JSONObject payload = new JSONObject();
            try {
                payload.put("club_name", clubName.getText().toString());
                payload.put("club_subtitle", clubSubtitle.getText().toString());
                payload.put("about_title", aboutTitle.getText().toString());
                payload.put("about_body", aboutBody.getText().toString());
                payload.put("seo_title", seoTitle.getText().toString());
                payload.put("seo_description", seoDescription.getText().toString());
                payload.put("seo_keywords", seoKeywords.getText().toString());
                payload.put("seo_robots", seoRobots.getText().toString());
                payload.put("seo_extra_head", seoExtra.getText().toString());
            } catch (Exception ignored) {}
            postAndReload("site_settings_save", payload, save, "تنظیمات ذخیره شد");
        });
        box.addView(save);
        content.addView(box, cardLp());
    }

    private void renderSeo() {
        JSONObject original=data.optJSONObject("site");
        if(original==null)original=new JSONObject();
        final JSONObject site=original;
        LinearLayout card=card();
        card.addView(text("SEO، Robots و تنظیمات جستجو",18,true,TEXT));
        card.addView(text("Robots مربوط به ربات‌های موتور جستجو است؛ تنظیمات بات‌ها یا سرویس‌های خارجی جداگانه‌اند. فقط فیلدهای شناخته‌شده سایت ذخیره می‌شوند.",11,false,MUTED));

        EditText title=input("عنوان SEO");
        title.setText(site.optString("seo_title",""));
        EditText description=input("Meta Description");
        description.setSingleLine(false);description.setMinLines(2);
        description.setText(site.optString("seo_description",""));
        EditText keywords=input("Meta Keywords");
        keywords.setText(site.optString("seo_keywords",""));
        EditText robots=input("Robots / index,follow / noindex,nofollow");
        robots.setText(site.optString("seo_robots","index,follow"));
        EditText extra=input("HEAD اضافی");
        extra.setSingleLine(false);extra.setMinLines(3);
        extra.setText(site.optString("seo_extra_head",""));
        card.addView(title);card.addView(description);card.addView(keywords);
        card.addView(robots);card.addView(extra);

        Button save=primary("ذخیره SEO و Robots");
        save.setOnClickListener(v->{
            // Preserve unchanged general settings; some backends treat omitted fields as empty.
            JSONObject payload=new JSONObject();
            try{
                payload.put("club_name",site.optString("club_name",""));
                payload.put("club_subtitle",site.optString("club_subtitle",""));
                payload.put("about_title",site.optString("about_title",""));
                payload.put("about_body",site.optString("about_body",""));
                payload.put("seo_title",title.getText().toString());
                payload.put("seo_description",description.getText().toString());
                payload.put("seo_keywords",keywords.getText().toString());
                payload.put("seo_robots",robots.getText().toString());
                payload.put("seo_extra_head",extra.getText().toString());
            }catch(Exception ignored){}
            postAndReload("site_settings_save",payload,save,"تنظیمات SEO و Robots ذخیره شد");
        });
        card.addView(save);
        content.addView(card,cardLp());
    }

    private void renderAppearance() {
        JSONObject ui = data.optJSONObject("ui_settings");
        if (ui == null) ui = new JSONObject();

        LinearLayout box = card();
        box.addView(text("ظاهر سایت و اپ", 18, true, TEXT));
        box.addView(text("تغییرات این بخش هم‌زمان روی وب‌سایت و نسخه اندروید اعمال می‌شوند.", 11, false, MUTED));

        EditText topGap = input("فاصله بالای منوی موبایل (px)");
        topGap.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        topGap.setText(String.valueOf(ui.optInt("mobile_header_top_gap_px", 14)));
        EditText eyebrow = input("عنوان کوچک صفحه اول");
        eyebrow.setText(ui.optString("home_eyebrow", "گروه دوچرخه سواری"));
        EditText tagline = input("زیرعنوان صفحه اول");
        tagline.setText(ui.optString("home_tagline", "رکاب بزن، طبیعت، مسیرهای جدید"));
        EditText primaryText = input("متن دکمه اصلی");
        primaryText.setText(ui.optString("home_primary_button_text", "ثبت نام برنامه"));
        EditText secondaryText = input("متن دکمه مسیرها");
        secondaryText.setText(ui.optString("home_secondary_button_text", "مشاهده مسیرها"));
        EditText rideText = input("متن دکمه شروع رکاب");
        rideText.setText(ui.optString("ride_button_text", "شروع رکاب"));

        box.addView(topGap); box.addView(eyebrow); box.addView(tagline); box.addView(primaryText); box.addView(secondaryText); box.addView(rideText);
        Button save = primary("ذخیره ظاهر سایت و اپ");
        save.setOnClickListener(v -> {
            JSONObject p = new JSONObject();
            try {
                p.put("mobile_header_top_gap_px", intValue(topGap, 14));
                p.put("home_eyebrow", eyebrow.getText().toString());
                p.put("home_tagline", tagline.getText().toString());
                p.put("home_primary_button_text", primaryText.getText().toString());
                p.put("home_secondary_button_text", secondaryText.getText().toString());
                p.put("ride_button_text", rideText.getText().toString());
            } catch (Exception ignored) {}
            postAndReload("ui_settings_save", p, save, "ظاهر سایت و اپ ذخیره شد");
        });
        box.addView(save);
        content.addView(box, cardLp());
    }

    private void renderMedals() {
        LinearLayout head = card();
        head.addView(text("مدیریت مدال‌ها", 18, true, TEXT));
        head.addView(text("معیار، مقدار هدف، بازه زمانی و ترتیب نمایش مدال‌ها", 11, false, MUTED));
        Button recalc = normal("محاسبه مجدد همه");
        recalc.setOnClickListener(v -> postAndReload("medals_recalculate", new JSONObject(), recalc, "مدال‌ها دوباره محاسبه شدند"));
        head.addView(recalc);
        content.addView(head, cardLp());

        JSONArray rows = data.optJSONArray("medal_definitions");
        if (rows != null) {
            for (int i=0;i<rows.length();i++) {
                JSONObject m = rows.optJSONObject(i); if (m == null) continue;
                LinearLayout card = card();
                card.addView(text(m.optString("title","مدال"), 15, true, TEXT));
                String meta = m.optString("metric","") + " · هدف " + m.optString("target_value","") +
                        " · " + (m.optInt("is_active",1)==1 ? "فعال" : "غیرفعال") +
                        " · ترتیب " + m.optInt("sort_order",100);
                card.addView(text(meta, 11, false, MUTED));
                int id = m.optInt("id",0);
                Button del = danger("حذف");
                del.setOnClickListener(v -> {
                    JSONObject p = new JSONObject(); try { p.put("id", id); } catch(Exception ignored){}
                    postAndReload("medal_delete", p, del, "مدال حذف شد");
                });
                card.addView(del);
                content.addView(card, cardLp());
            }
        }

        LinearLayout form = card();
        form.addView(text("مدال جدید / ویرایش", 16, true, TEXT));
        EditText id = input("ID برای ویرایش؛ خالی = جدید");
        EditText title = input("عنوان مدال");
        EditText desc = input("توضیح"); desc.setSingleLine(false); desc.setMinLines(2);
        EditText metric = input("معیار: distance_km / calories_kcal / ascent_m / ride_count / ...");
        EditText target = input("مقدار هدف");
        EditText window = input("بازه زمانی (روز)؛ ۰ = بدون محدودیت");
        EditText sort = input("ترتیب نمایش"); sort.setText("100");
        EditText match = input("فیلتر عنوان (اختیاری)");
        EditText active = input("وضعیت: 1 فعال / 0 غیرفعال"); active.setText("1");
        form.addView(id);form.addView(title);form.addView(desc);form.addView(metric);form.addView(target);form.addView(window);form.addView(sort);form.addView(match);form.addView(active);
        Button save = primary("ذخیره مدال");
        save.setOnClickListener(v -> {
            JSONObject p = new JSONObject();
            try {
                p.put("id", intValue(id,0));
                p.put("title", title.getText().toString());
                p.put("description", desc.getText().toString());
                p.put("metric", metric.getText().toString());
                p.put("target_value", decimalValue(target,0));
                p.put("window_days", intValue(window,0));
                p.put("sort_order", intValue(sort,100));
                p.put("match_title", match.getText().toString());
                p.put("is_active", intValue(active,1));
            } catch(Exception ignored){}
            postAndReload("medal_save", p, save, "مدال ذخیره شد");
        });
        form.addView(save);
        content.addView(form, cardLp());
    }

    private void renderUsers() {
        LinearLayout info = card();
        info.addView(text("کاربران و نقش‌ها", 18, true, TEXT));
        info.addView(text("مدیریت کاربران مطابق پنل وب PedalPro", 11, false, MUTED));
        content.addView(info, cardLp());

        final ArrayList<Button> editUserButtons = new ArrayList<>();
        final ArrayList<JSONObject> editableUsers = new ArrayList<>();
        JSONArray members = data.optJSONArray("members");
        if (members != null) {
            for (int i=0;i<members.length();i++) {
                JSONObject u = members.optJSONObject(i); if (u == null) continue;
                LinearLayout row = card();
                row.addView(text(u.optString("display_name",u.optString("username","کاربر")), 14, true, TEXT));
                row.addView(text("@"+u.optString("username","")+" · "+u.optString("role","member")+" · "+(u.optInt("is_active",1)==1?"فعال":"غیرفعال"), 10, false, MUTED));
                Button editUser=normal("ویرایش مشخصات");
                row.addView(editUser);
                editUserButtons.add(editUser);
                editableUsers.add(u);
                content.addView(row, cardLp());
            }
        }

        LinearLayout form = card();
        form.addView(text("ساخت / ویرایش کاربر", 16, true, TEXT));
        EditText id=input("ID؛ خالی = کاربر جدید"), name=input("نام نمایشی"), username=input("نام کاربری"),
                phone=input("شماره موبایل اختیاری"), role=input("نقش: member / admin"), password=input("رمز عبور"),
                age=input("سن"), height=input("قد (cm)"), weight=input("وزن (kg)"), bio=input("درباره کاربر");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        bio.setSingleLine(false); bio.setMinLines(2);
        form.addView(id);form.addView(name);form.addView(username);form.addView(phone);form.addView(role);form.addView(password);form.addView(age);form.addView(height);form.addView(weight);form.addView(bio);
        Button save=primary("ذخیره کاربر");
        save.setOnClickListener(v->{
            int userId=intValue(id,0);
            String displayName=name.getText().toString().trim();
            String userName=username.getText().toString().trim();
            String userRole=role.getText().toString().trim();
            if(userId<=0&&(displayName.isEmpty()||userName.isEmpty())){
                toast("نام نمایشی و نام کاربری برای ایجاد کاربر الزامی هستند.");
                return;
            }
            if(!userRole.isEmpty() && !userRole.equals("member") && !userRole.equals("admin")){
                toast("نقش باید member یا admin باشد.");
                return;
            }
            JSONObject p=new JSONObject();
            try{
                if(userId>0)p.put("id",userId);
                if(!displayName.isEmpty())p.put("display_name",displayName);
                if(!userName.isEmpty())p.put("username",userName);
                if(!phone.getText().toString().trim().isEmpty())p.put("phone",phone.getText().toString().trim());
                if(!userRole.isEmpty())p.put("role",userRole);
                else if(userId<=0)p.put("role","member");
                if(!password.getText().toString().isEmpty())p.put("password",password.getText().toString());
                if(!age.getText().toString().trim().isEmpty())p.put("age",age.getText().toString().trim());
                if(!height.getText().toString().trim().isEmpty())p.put("height_cm",height.getText().toString().trim());
                if(!weight.getText().toString().trim().isEmpty())p.put("weight_kg",weight.getText().toString().trim());
                if(!bio.getText().toString().trim().isEmpty())p.put("bio",bio.getText().toString());
            }catch(Exception ignored){}
            // Never force account status or privacy visibility when saving other fields.
            postAndReload("admin_user_save",p,save,"کاربر ذخیره شد");
        });
        form.addView(save);content.addView(form,cardLp());
        for(int i=0;i<editUserButtons.size();i++){
            JSONObject selected=editableUsers.get(i);
            editUserButtons.get(i).setOnClickListener(v->{
                id.setText(String.valueOf(selected.optInt("id",0)));
                name.setText(selected.optString("display_name",""));
                username.setText(selected.optString("username",""));
                phone.setText(selected.optString("phone",""));
                role.setText(selected.optString("role","member"));
                password.setText("");
                age.setText(selected.optString("age",""));
                height.setText(selected.optString("height_cm",""));
                weight.setText(selected.optString("weight_kg",""));
                bio.setText(selected.optString("bio",""));
                toast("اطلاعات کاربر در فرم ویرایش قرار گرفت؛ رمز عبور قبلی نمایش داده نمی‌شود.");
            });
        }

        LinearLayout roleForm=card();
        roleForm.addView(text("نقش‌ها",16,true,TEXT));
        EditText roleId=input("ID نقش؛ خالی = جدید"), roleName=input("نام نقش"), roleTitle=input("عنوان نمایشی"), permissions=input("مجوزها (JSON/CSV)");
        roleForm.addView(roleId);roleForm.addView(roleName);roleForm.addView(roleTitle);roleForm.addView(permissions);
        Button roleSave=primary("ذخیره نقش");
        roleSave.setOnClickListener(v->{
            JSONObject p=new JSONObject();
            try{p.put("id",intValue(roleId,0));p.put("name",roleName.getText().toString());p.put("title",roleTitle.getText().toString());p.put("permissions",permissions.getText().toString());}catch(Exception ignored){}
            postAndReload("admin_role_save",p,roleSave,"نقش ذخیره شد");
        });
        roleForm.addView(roleSave);content.addView(roleForm,cardLp());
    }

    private void renderSms() {
        JSONObject status=data.optJSONObject("sms_status");
        JSONObject otp=data.optJSONObject("sms_otp_patterns");
        if(status==null)status=new JSONObject(); if(otp==null)otp=new JSONObject();

        LinearLayout state=card();
        state.addView(text("وضعیت پیامک",18,true,TEXT));
        state.addView(text("ارائه‌دهنده: "+status.optString("provider","FarazSMS"),12,false,MUTED));
        state.addView(text("API: "+(status.optBoolean("configured",false)?"✓ تنظیم شده":"× ناقص"),12,true,status.optBoolean("configured",false)?GREEN:ORANGE));
        state.addView(text("خط فعال: "+status.optString("sender","ثبت نشده"),12,false,MUTED));
        Button test=normal("تست اتصال FarazSMS");
        test.setOnClickListener(v->postAndReload("sms_test_connection",new JSONObject(),test,"اتصال FarazSMS موفق است"));
        state.addView(test);content.addView(state,cardLp());

        LinearLayout sender=card();
        sender.addView(text("خط ارسال",16,true,TEXT));
        EditText line=input("شماره خط ارسال");line.setText(status.optString("sender",""));
        Button fetch=normal("دریافت خط‌ها");
        fetch.setOnClickListener(v->postAndReload("sms_fetch_lines",new JSONObject(),fetch,"خط‌ها دریافت شدند"));
        Button saveLine=primary("ذخیره خط ارسال");
        saveLine.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("sender",line.getText().toString());}catch(Exception ignored){}postAndReload("sms_save_sender",p,saveLine,"خط ارسال ذخیره شد");});
        sender.addView(line);sender.addView(fetch);sender.addView(saveLine);content.addView(sender,cardLp());

        LinearLayout otpCard=card();otpCard.addView(text("پترن‌های احراز هویت",16,true,TEXT));
        EditText signupCode=input("کد پترن ثبت‌نام");signupCode.setText(otp.optString("signup_pattern_code",""));
        EditText resetCode=input("کد پترن فراموشی رمز");resetCode.setText(otp.optString("reset_pattern_code",""));
        EditText signupText=input("متن پترن ثبت‌نام");signupText.setSingleLine(false);signupText.setMinLines(3);signupText.setText(otp.optString("signup_text",""));
        EditText resetText=input("متن پترن فراموشی رمز");resetText.setSingleLine(false);resetText.setMinLines(3);resetText.setText(otp.optString("reset_text",""));
        Button saveOtp=primary("ذخیره");
        saveOtp.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("signup_pattern_code",signupCode.getText().toString().trim());p.put("reset_pattern_code",resetCode.getText().toString().trim());p.put("signup_text",signupText.getText().toString());p.put("reset_text",resetText.getText().toString());}catch(Exception ignored){}postAndReload("sms_save_otp_patterns",p,saveOtp,"پترن‌های احراز هویت ذخیره شدند");});
        otpCard.addView(signupCode);otpCard.addView(resetCode);otpCard.addView(signupText);otpCard.addView(resetText);otpCard.addView(saveOtp);content.addView(otpCard,cardLp());

        LinearLayout custom=card();custom.addView(text("ارسال پیامک دستی",16,true,TEXT));
        EditText phone=input("شماره موبایل"),name=input("نام گیرنده"),message=input("متن پیامک");message.setSingleLine(false);message.setMinLines(3);
        Button send=primary("ارسال پیامک");
        send.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("phone",phone.getText().toString());p.put("name",name.getText().toString());p.put("message",message.getText().toString());}catch(Exception ignored){}postAndReload("sms_send_custom",p,send,"پیامک ارسال شد");});
        custom.addView(phone);custom.addView(name);custom.addView(message);custom.addView(send);content.addView(custom,cardLp());

        JSONArray logs=data.optJSONArray("sms_logs");
        if(logs!=null&&logs.length()>0){LinearLayout logCard=card();logCard.addView(text("تاریخچه ارسال",16,true,TEXT));for(int i=0;i<Math.min(50,logs.length());i++){JSONObject l=logs.optJSONObject(i);if(l==null)continue;logCard.addView(text(l.optString("sent_at","")+" · "+l.optString("recipient_name","")+" · "+l.optString("phone","")+" · "+l.optString("status",""),10,false,MUTED));}content.addView(logCard,cardLp());}
    }

    private void renderAppManager() {
        appReleaseAdmin.render(content, data);
    }

    private void loadReleaseStatus(TextView state,Button b){
        b.setEnabled(false);io.execute(()->{try{JSONObject d=api.get("app_release_status");JSONObject r=d.optJSONObject("release");main.post(()->{b.setEnabled(true);if(r==null){state.setText("نسخه منتشرشده‌ای ثبت نشده است.");return;}state.setText("آخرین نسخه: "+r.optString("version_name","—")+"\nVersion Code: "+r.optInt("version_code",0)+"\nزمان انتشار: "+r.optString("published_at","—"));});}catch(Exception e){main.post(()->{b.setEnabled(true);state.setText(e.getMessage());});}});
    }

    private void renderPush(){
        fcmAdmin.render(content);
    }

    private void loadFcmStatus(TextView state,Button b){
        b.setEnabled(false);io.execute(()->{try{JSONObject d=api.get("fcm_status");main.post(()->{b.setEnabled(true);JSONObject s=d.optJSONObject("status");if(s==null)s=d;state.setText("وضعیت Push: "+(s.optBoolean("configured",false)?"فعال":"غیرفعال")+"\nدستگاه‌های ثبت‌شده: "+s.optInt("device_count",0)+"\nکاربران متصل: "+s.optInt("user_count",0));});}catch(Exception e){main.post(()->{b.setEnabled(true);state.setText(e.getMessage());});}});
    }

    private void renderSystem(){
        LinearLayout box=card();box.addView(text("سیستم",18,true,TEXT));
        box.addView(text("اطلاعات و وضعیت فعلی PedalPro",11,false,MUTED));
        box.addView(text("نسخه وب: "+data.optString("version",data.optString("site_version","—")),12,false,TEXT));
        box.addView(text("کاربران: "+(data.optJSONArray("members")==null?0:data.optJSONArray("members").length()),12,false,TEXT));
        box.addView(text("برنامه‌ها: "+(data.optJSONArray("events")==null?0:data.optJSONArray("events").length()),12,false,TEXT));
        Button reload=primary("بروزرسانی اطلاعات");
        reload.setOnClickListener(v->load("system"));box.addView(reload);content.addView(box,cardLp());
    }

    void reloadActiveTab() {
        load(activeTab);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent intent) {
        super.onActivityResult(requestCode,resultCode,intent);
        if(appReleaseAdmin!=null && appReleaseAdmin.onActivityResult(requestCode,resultCode,intent)) return;
        if(fcmAdmin!=null) fcmAdmin.onActivityResult(requestCode,resultCode,intent);
    }

    private void postAndReload(String action,JSONObject payload,Button button,String success){
        button.setEnabled(false);
        io.execute(()->{try{api.post(action,payload);JSONObject d=api.bootstrap();data=d;main.post(()->{button.setEnabled(true);toast(success);render();});}catch(Exception e){main.post(()->{button.setEnabled(true);toast(e.getMessage());});}});
    }

    private int intValue(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}
    private double decimalValue(EditText e,double fallback){try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception x){return fallback;}}
}
