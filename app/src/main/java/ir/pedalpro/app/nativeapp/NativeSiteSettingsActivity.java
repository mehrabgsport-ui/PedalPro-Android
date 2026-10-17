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
    private static final int REQ_MEDAL_IMAGE = 2911;
    private int pendingMedalImageId;
    private final ArrayList<Button> tabButtons = new ArrayList<>();
    private String activeTab = "general";
    private int tabScrollX;
    @Override protected boolean useStickyHeader(){return true;}
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
        hsv.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->tabScrollX=x);
        if(stickyHeader!=null){
            stickyHeader.removeAllViews();
            stickyHeader.addView(hsv,new LinearLayout.LayoutParams(-1,dp(58)));
        }else content.addView(hsv,new LinearLayout.LayoutParams(-1,dp(58)));
        hsv.post(()->hsv.scrollTo(tabScrollX,0));
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
        box.addView(text("طبق SDK جدید نشان (MapLibre)، فایل License قدیمی لازم نیست. کلید MOBILE باید در GitHub Actions Secret با نام NESHAN_MOBILE_API_KEY تنظیم شود تا در Manifest نسخه امضاشده ثبت گردد؛ ذخیره کلید فقط در گوشی Manifest را تغییر نمی‌دهد. سرویس مسیریابی خودرو، مسیر تأییدشده دوچرخه نیست.",11,false,ORANGE));
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


    // All Bale operations below are backed by the verified web 4.7.46 PHP actions.
    // The token is write-only: never populate its editor from any API response.
    private String baleSearch = "";
    private int balePage = 1;

    private void renderBale() {
        LinearLayout loading=card();
        loading.addView(text("ربات بله",18,true,TEXT));
        loading.addView(text("دریافت وضعیت، اعضای متصل و گزارش ارسال از سرور…",12,false,MUTED));
        content.addView(loading,cardLp());
        refreshBalePanel();
    }

    private void refreshBalePanel() {
        final String search=baleSearch;
        final int page=balePage;
        io.execute(()->{
            try {
                JSONObject status=api.get("bale_status");
                JSONObject users=api.post("bale_connected_users",
                        new JSONObject().put("search",search).put("page",page));
                JSONObject history=api.get("bale_custom_history");
                main.post(()->{
                    if(!"bale".equals(activeTab))return;
                    content.removeAllViews();
                    renderTabs();
                    renderBalePanel(status,users,history);
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception failure){
                main.post(()->{
                    if(!"bale".equals(activeTab))return;
                    content.removeAllViews();renderTabs();
                    addCard("خطای دریافت تنظیمات بله",failure.getMessage());
                    Button retry=normal("تلاش مجدد");
                    retry.setOnClickListener(v->refreshBalePanel());
                    content.addView(retry);
                });
            }
        });
    }

    private void renderBalePanel(JSONObject status,JSONObject listing,JSONObject history) {
        JSONObject settings=status.optJSONObject("settings");
        if(settings==null){addCard("خطا","ساختار تنظیمات بله از سرور دریافت نشد.");return;}

        LinearLayout form=card();
        form.addView(text("تنظیمات مدیریتی ربات بله",18,true,TEXT));
        form.addView(text("متصل: "+status.optInt("connected_count",0)+
                " · توکن: "+(settings.optBoolean("token_configured")?"تنظیم شده":"تنظیم نشده")+
                " · Webhook: "+(settings.optBoolean("webhook_configured")?"تنظیم شده":"تنظیم نشده"),
                12,false,MUTED));
        CheckBox enabled=new CheckBox(this);
        enabled.setText("ربات بله فعال است");
        enabled.setTextColor(TEXT);enabled.setChecked(settings.optBoolean("enabled",false));
        form.addView(enabled);
        EditText name=input("نام ربات");
        name.setText(settings.optString("name",""));
        form.addView(name);
        EditText username=input("نام کاربری ربات");
        username.setText(settings.optString("username",""));
        form.addView(username);
        EditText token=input("توکن جدید (برای حفظ توکن قبلی خالی بماند)");
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        form.addView(token);
        EditText adminChat=input("Chat ID مدیر");
        adminChat.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_NORMAL);
        adminChat.setText(settings.optString("admin_chat_id",""));
        form.addView(adminChat);
        EditText reminder=input("ساعت یادآوری پیش از برنامه");
        reminder.setInputType(InputType.TYPE_CLASS_NUMBER);
        reminder.setText(String.valueOf(settings.optInt("reminder_hours",24)));
        form.addView(reminder);

        JSONObject templateValues=settings.optJSONObject("templates");
        java.util.LinkedHashMap<String,EditText> templates=new java.util.LinkedHashMap<>();
        if(templateValues!=null){
            form.addView(text("قالب‌های پیام · متغیرها: {{name}} {{event}} {{date}} {{time}} {{medal}} {{record}}",12,true,TEXT));
            java.util.Iterator<String> keys=templateValues.keys();
            while(keys.hasNext()){
                String key=keys.next();
                EditText value=input("قالب: "+key);
                value.setSingleLine(false);value.setMinLines(2);
                value.setText(templateValues.optString(key,""));
                form.addView(value);
                templates.put(key,value);
            }
        }
        Button save=primary("ذخیره تنظیمات ربات بله");
        save.setOnClickListener(v->{
            JSONObject payload=new JSONObject();
            try{
                payload.put("enabled",enabled.isChecked());
                payload.put("name",name.getText().toString().trim());
                payload.put("username",username.getText().toString().trim());
                payload.put("admin_chat_id",adminChat.getText().toString().trim());
                payload.put("reminder_hours",intValue(reminder,24));
                if(!token.getText().toString().trim().isEmpty())
                    payload.put("token",token.getText().toString().trim());
                JSONObject messageTemplates=new JSONObject();
                for(java.util.Map.Entry<String,EditText> entry:templates.entrySet())
                    messageTemplates.put(entry.getKey(),entry.getValue().getText().toString());
                payload.put("templates",messageTemplates);
            }catch(Exception e){toast("اطلاعات معتبر نیست.");return;}
            save.setEnabled(false);
            io.execute(()->{
                try{
                    api.post("bale_save_settings",payload);
                    main.post(()->{token.setText("");toast("تنظیمات بله ذخیره شد.");refreshBalePanel();});
                }catch(Exception e){main.post(()->{save.setEnabled(true);toast(e.getMessage());});}
            });
        });
        form.addView(save);

        Button webhook=normal("ثبت Webhook بله");
        webhook.setOnClickListener(v->confirmBaleAdminAction(
                "ثبت Webhook","این عملیات Webhook واقعی ربات را در سرویس بله تنظیم می‌کند.",
                "bale_set_webhook"));
        form.addView(webhook);
        Button test=normal("ارسال پیام تست به مدیر");
        test.setOnClickListener(v->confirmBaleAdminAction(
                "ارسال پیام تست","پیام واقعی به Chat ID مدیر ارسال می‌شود.","bale_test"));
        form.addView(test);
        Button queue=normal("پردازش صف پیام‌ها");
        queue.setOnClickListener(v->confirmBaleAdminAction(
                "اجرای صف بله","این عملیات پیام‌های آماده ارسال را واقعاً ارسال می‌کند.","bale_run_queue"));
        form.addView(queue);
        content.addView(form,cardLp());

        LinearLayout connected=card();
        connected.addView(text("اعضای متصل به ربات",17,true,TEXT));
        connected.addView(text("تعداد نتیجه: "+listing.optInt("filtered_count",0)+
                " · صفحه "+listing.optInt("page",pageOrOne())+" از "+listing.optInt("pages",1),12,false,MUTED));
        EditText search=input("جست‌وجوی نام، نام کاربری یا شماره");
        search.setText(baleSearch);
        connected.addView(search);
        Button doSearch=normal("جست‌وجو");
        doSearch.setOnClickListener(v->{baleSearch=search.getText().toString().trim();balePage=1;refreshBalePanel();});
        connected.addView(doSearch);
        JSONArray users=listing.optJSONArray("users");
        if(users!=null)for(int i=0;i<users.length();i++){
            JSONObject person=users.optJSONObject(i);if(person==null)continue;
            int uid=person.optInt("user_id",0);
            connected.addView(text(person.optString("display_name","")+
                    " · @"+person.optString("username","")+
                    (person.optBoolean("is_active",true)?"":" · غیرفعال"),12,false,TEXT));
            Button sendOne=normal("ارسال پیام به این عضو");
            sendOne.setOnClickListener(v->composeBaleMessage("one",uid,person.optString("display_name","عضو")));
            connected.addView(sendOne);
        }
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button previous=normal("قبلی");
        previous.setEnabled(listing.optInt("page",1)>1);
        previous.setOnClickListener(v->{balePage=Math.max(1,balePage-1);refreshBalePanel();});
        Button next=normal("بعدی");
        next.setEnabled(listing.optInt("page",1)<listing.optInt("pages",1));
        next.setOnClickListener(v->{balePage++;refreshBalePanel();});
        nav.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1f));
        nav.addView(next,new LinearLayout.LayoutParams(0,dp(48),1f));
        connected.addView(nav);
        Button sendAll=primary("ارسال پیام به همه اعضای متصل");
        sendAll.setOnClickListener(v->composeBaleMessage("all",0,"همه اعضا"));
        connected.addView(sendAll);
        content.addView(connected,cardLp());

        LinearLayout logs=card();
        logs.addView(text("تاریخچه ارسال پیام بله",17,true,TEXT));
        JSONArray entries=history.optJSONArray("history");
        if(entries!=null&&entries.length()>0)for(int i=0;i<entries.length();i++){
            JSONObject e=entries.optJSONObject(i);if(e==null)continue;
            logs.addView(text(e.optString("created_at","")+" · "+
                    e.optString("preview","")+"\nگیرندگان "+e.optInt("recipients")+
                    " · ارسال "+e.optInt("sent")+" · خطا "+e.optInt("failed")+
                    " · در انتظار "+e.optInt("waiting")+" · رد "+e.optInt("skipped"),
                    11,false,MUTED));
        }else logs.addView(text("پیام دستی ثبت نشده است.",11,false,MUTED));
        Button refresh=normal("بروزرسانی وضعیت و تاریخچه");
        refresh.setOnClickListener(v->refreshBalePanel());
        logs.addView(refresh);
        content.addView(logs,cardLp());
    }

    private int pageOrOne(){return Math.max(1,balePage);}

    private void confirmBaleAdminAction(String title,String description,String action) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(title).setMessage(description)
                .setNegativeButton("انصراف",null)
                .setPositiveButton("تأیید",(dialog,which)->io.execute(()->{
                    try{
                        api.post(action,new JSONObject());
                        main.post(()->{toast("عملیات انجام شد.");refreshBalePanel();});
                    }catch(Exception e){main.post(()->toast(e.getMessage()));}
                })).show();
    }

    private void composeBaleMessage(String audience,int userId,String recipient) {
        EditText message=input("متن پیام به "+recipient);
        message.setSingleLine(false);message.setMinLines(4);
        String nonce=java.util.UUID.randomUUID().toString().replace("-","");
        new android.app.AlertDialog.Builder(this)
                .setTitle("ارسال پیام بله")
                .setView(message)
                .setNegativeButton("انصراف",null)
                .setPositiveButton("ادامه",(dialog,which)->{
                    String body=message.getText().toString().trim();
                    if(body.length()<1||body.length()>3000||body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>11000) {
                        toast("متن باید بین ۱ تا ۳۰۰۰ نویسه و حداکثر ۱۱۰۰۰ بایت باشد.");return;
                    }
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("تأیید ارسال واقعی")
                            .setMessage("گیرنده: "+recipient+"\nاین پیام واقعاً از طریق ربات بله ارسال می‌شود.")
                            .setNegativeButton("انصراف",null)
                            .setPositiveButton("ارسال",(d,w)->{
                                io.execute(()->{
                                    try {
                                        JSONObject payload=new JSONObject()
                                                .put("text",body).put("audience",audience).put("nonce",nonce);
                                        if("one".equals(audience))payload.put("user_id",userId);
                                        JSONObject ack=api.post("bale_custom_send",payload);
                                        main.post(()->{
                                            toast("ارسال ثبت شد: "+ack.optString("delivery","queued"));
                                            refreshBalePanel();
                                        });
                                    }catch(Exception e){main.post(()->toast("ارسال تأیید نشد: "+e.getMessage()));}
                                });
                            }).show();
                }).show();
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
                String safe=safeBaleValue(key,val);
                if(safe==null)continue;
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
                String safe=safeBaleValue(key,value);
                if(safe==null)continue;
                box.addView(text(key+" : "+safe,12,false,TEXT));
                shown++;
            }
        }
        return shown;
    }

    /** The Bale bootstrap is not a documented public schema: display only safe status primitives. */
    private String safeBaleValue(String key,Object value){
        if(isPrivateBaleField(key)||value==null||value==JSONObject.NULL)return null;
        if(value instanceof Boolean||value instanceof Number)return String.valueOf(value);
        if(!(value instanceof String))return null;
        String field=key.toLowerCase(Locale.ROOT);
        String safe=((String)value).trim();
        boolean knownStatus=field.equals("status")||field.equals("state")||
                field.equals("mode")||field.equals("connection_status")||
                field.equals("webhook_status")||field.equals("bot_status")||
                field.equals("last_check_status");
        boolean publicIdentity=field.equals("bot_name")||field.equals("bot_username")||
                field.equals("username")||field.equals("version");
        if(!knownStatus&&!publicIdentity)return "••••";
        if(safe.length()>72||safe.contains("://")||safe.contains("\\")||
                safe.contains("/")||safe.contains("=")||safe.contains("\n")||
                safe.contains("\r")||safe.contains(" "))return "••••";
        return safe;
    }

    private boolean isPrivateBaleField(String key){
        String name=key==null?"":key.toLowerCase(Locale.ROOT);
        if(name.equals("webhook_status")||name.equals("last_check_status"))return false;
        return name.contains("token")||name.contains("secret")||name.contains("pass")
                ||name.contains("key")||name.contains("auth")||name.contains("webhook")
                ||name.contains("chat_id")||name.contains("phone")||name.contains("session")
                ||name.contains("callback")||name.contains("endpoint")||name.contains("url")
                ||name.contains("cookie")||name.contains("header")||name.contains("bearer")
                ||name.contains("credential")||name.contains("access")||name.contains("contact")
                ||name.contains("peer")||name.contains("user_id")||name.contains("otp");
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
        head.addView(text("معیار، مقدار هدف، بازه زمانی، تصویر و ترتیب نمایش مدال‌ها", 11, false, MUTED));
        head.addView(text("تصویر مدال را از فایل PNG انتخاب و مستقیم به سرور ارسال کنید. ابتدا مدال را ذخیره کنید.",11,false,MUTED));
        Button recalc = normal("محاسبه مجدد همه");
        recalc.setOnClickListener(v -> postAndReload("medals_recalculate", new JSONObject(), recalc, "مدال‌ها دوباره محاسبه شدند"));
        head.addView(recalc);
        content.addView(head, cardLp());

        JSONArray rows = data.optJSONArray("medal_definitions");
        if (rows != null) {
            for (int i=0;i<rows.length();i++) {
                JSONObject m = rows.optJSONObject(i); if (m == null) continue;
                LinearLayout card = card();
                String medalPhoto=m.optString("image_path","");
                if(!medalPhoto.isBlank()){
                    android.widget.ImageView photo=new android.widget.ImageView(this);
                    photo.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    RemoteImageLoader.load(this,photo,medalPhoto,ir.pedalpro.app.R.drawable.pedalpro_logo_transparent);
                    card.addView(photo,new LinearLayout.LayoutParams(-1,dp(82)));
                }
                card.addView(text(m.optString("title","مدال"), 15, true, TEXT));
                String meta = m.optString("metric_type","") + " · هدف " + m.optString("target_value","") +
                        " · " + (m.optInt("is_active",1)==1 ? "فعال" : "غیرفعال") +
                        " · ترتیب " + m.optInt("sort_order",100);
                card.addView(text(meta, 11, false, MUTED));
                int id = m.optInt("id",0);
                Button del = danger("حذف مدال");
                del.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                        .setTitle("حذف مدال")
                        .setMessage("حذف این مدال، سوابق اعطای آن به اعضا را نیز حذف می‌کند. تأیید می‌کنید؟")
                        .setNegativeButton("انصراف",null)
                        .setPositiveButton("حذف",(dialog,which)->{
                            JSONObject payload = new JSONObject();
                            try { payload.put("id", id); } catch(Exception ignored){}
                            postAndReload("medal_delete", payload, del, "مدال حذف شد");
                        }).show());
                Button image=normal("بارگذاری تصویر PNG");
                image.setOnClickListener(v -> {
                    pendingMedalImageId=id;
                    Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.setType("image/png");
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(pick, REQ_MEDAL_IMAGE);
                });
                card.addView(image);
                if (!medalPhoto.isBlank()) {
                    Button clear=normal("حذف تصویر مدال");
                    clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                            .setTitle("حذف تصویر مدال")
                            .setMessage("فقط تصویر حذف می‌شود؛ خود مدال و امتیازهای اعضا باقی می‌ماند.")
                            .setNegativeButton("انصراف",null)
                            .setPositiveButton("حذف تصویر",(dialog,which)->{
                                JSONObject payload=new JSONObject();
                                try {payload.put("medal_id",id);}catch(Exception ignored){}
                                postAndReload("medal_image_clear",payload,clear,"تصویر مدال حذف شد");
                            }).show());
                    card.addView(clear);
                }
                card.addView(del);
                content.addView(card, cardLp());
            }
        }

        LinearLayout form = card();
        form.addView(text("مدال جدید / ویرایش", 16, true, TEXT));
        EditText id = input("ID برای ویرایش؛ خالی = جدید");
        EditText title = input("عنوان مدال");
        EditText desc = input("توضیح"); desc.setSingleLine(false); desc.setMinLines(2);
        // Only expose criteria implemented by the existing server, plus any
        // historical criterion already returned in medal_definitions.
        final ArrayList<String> metricCodes=new ArrayList<>();
        final ArrayList<String> metricLabels=new ArrayList<>();
        String[] builtins={"ride_count","distance_km","calories_kcal","ascent_m"};
        String[] descriptions={"تعداد رکاب‌زنی (مرتبه)","مسافت رکاب‌زنی (کیلومتر)",
                "کالری مصرف‌شده (kcal)","ارتفاع صعود (متر)"};
        for(int i=0;i<builtins.length;i++){
            metricCodes.add(builtins[i]);metricLabels.add(descriptions[i]);
        }
        if(rows!=null)for(int i=0;i<rows.length();i++){
            JSONObject saved=rows.optJSONObject(i);
            if(saved==null)continue;
            String type=saved.optString("metric_type","").trim();
            if(!type.isBlank()&&!metricCodes.contains(type)){
                metricCodes.add(type);
                metricLabels.add("معیار ثبت‌شده: "+type);
            }
        }
        android.widget.Spinner metric=new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> metricAdapter=new android.widget.ArrayAdapter<>(
                this,android.R.layout.simple_spinner_item,metricLabels);
        metricAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        metric.setAdapter(metricAdapter);
        form.addView(text("انتخاب معیار مدال",12,true,TEXT));
        EditText target = input("مقدار هدف");
        EditText window = input("بازه زمانی (روز)؛ ۰ = بدون محدودیت");
        EditText sort = input("ترتیب نمایش"); sort.setText("100");
        EditText match = input("فیلتر عنوان (اختیاری)");
         EditText active = input("وضعیت: 1 فعال / 0 غیرفعال"); active.setText("1");
        form.addView(id);form.addView(title);form.addView(desc);form.addView(metric);form.addView(target);form.addView(window);form.addView(sort);form.addView(match);form.addView(active);
        id.setOnFocusChangeListener((view,focused)->{
            if(focused||rows==null)return;
            int selectedId=intValue(id,0);
            for(int i=0;i<rows.length();i++){
                JSONObject saved=rows.optJSONObject(i);
                if(saved==null||saved.optInt("id",0)!=selectedId)continue;
                int index=metricCodes.indexOf(saved.optString("metric_type",""));
                if(index>=0)metric.setSelection(index);
                return;
            }
        });
        Button save = primary("ذخیره مدال");
        save.setOnClickListener(v -> {
            JSONObject p = new JSONObject();
            try {
                p.put("id", intValue(id,0));
                p.put("title", title.getText().toString());
                p.put("description", desc.getText().toString());
                p.put("metric_type", metricCodes.get(Math.max(0,metric.getSelectedItemPosition())));
                p.put("target_value", decimalValue(target,0));
                p.put("window_days", intValue(window,0));
                p.put("sort_order", intValue(sort,100));
                p.put("match_text", match.getText().toString());
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
        final java.util.LinkedHashMap<Integer,TextView> baleStatuses=new java.util.LinkedHashMap<>();
        JSONArray members = data.optJSONArray("members");
        if (members != null) {
            for (int i=0;i<members.length();i++) {
                JSONObject u = members.optJSONObject(i); if (u == null) continue;
                LinearLayout row = card();
                row.addView(text(u.optString("display_name",u.optString("username","کاربر")), 14, true, TEXT));
                row.addView(text("@"+u.optString("username","")+" · "+u.optString("role","member")+" · "+(u.optInt("is_active",1)==1?"فعال":"غیرفعال"), 10, false, MUTED));
                row.addView(text("شماره تماس: "+UiSafeText.display(u.optString("phone","")),11,false,MUTED));
                TextView baleStatus=text("ربات بله: در حال بررسی…",10,false,MUTED);
                row.addView(baleStatus);
                if(u.optInt("id",0)>0)baleStatuses.put(u.optInt("id",0),baleStatus);
                Button editUser=normal("ویرایش مشخصات");
                row.addView(editUser);
                editUserButtons.add(editUser);
                editableUsers.add(u);
                content.addView(row, cardLp());
            }
        }

        // Paginated bulk lookup: never generate one network request per member.
        if(!baleStatuses.isEmpty())io.execute(()->{
            java.util.HashSet<Integer> linkedIds=new java.util.HashSet<>();
            boolean complete=false;
            try{
                int page=1;
                while(page<=20){
                    JSONObject listing=api.post("bale_connected_users",
                            new JSONObject().put("search","").put("page",page));
                    JSONArray people=listing.optJSONArray("users");
                    if(people!=null)for(int i=0;i<people.length();i++){
                        JSONObject item=people.optJSONObject(i);
                        if(item!=null&&item.optInt("user_id",0)>0)
                            linkedIds.add(item.optInt("user_id",0));
                    }
                    int pages=Math.max(1,listing.optInt("pages",1));
                    if(page>=pages){complete=true;break;}
                    page++;
                }
            }catch(Exception ignored){}
            final boolean allPages=complete;
            main.post(()->{
                if(!"users".equals(activeTab))return;
                for(java.util.Map.Entry<Integer,TextView> e:baleStatuses.entrySet()){
                    boolean found=linkedIds.contains(e.getKey());
                    e.getValue().setText(found?"ربات بله: متصل":
                            (allPages?"ربات بله: متصل نیست":"ربات بله: وضعیت نامشخص"));
                }
            });
        });

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
        if (requestCode == REQ_MEDAL_IMAGE) {
            int id=pendingMedalImageId;
            pendingMedalImageId=0;
            if (resultCode==RESULT_OK && intent!=null && intent.getData()!=null && id>0) {
                Uri uri=intent.getData();
                try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                uploadMedalImage(id,uri);
            }
            return;
        }
        if(appReleaseAdmin!=null && appReleaseAdmin.onActivityResult(requestCode,resultCode,intent)) return;
        if(fcmAdmin!=null) fcmAdmin.onActivityResult(requestCode,resultCode,intent);
    }

    private void uploadMedalImage(int medalId,Uri uri) {
        setSubtitle("در حال آپلود تصویر مدال…",MUTED);
        io.execute(()->{
            try {
                String type=getContentResolver().getType(uri);
                if (type!=null && !"image/png".equalsIgnoreCase(type))
                    throw new IllegalArgumentException("فقط تصویر PNG مجاز است.");
                java.util.LinkedHashMap<String,String> fields=new java.util.LinkedHashMap<>();
                fields.put("medal_id",String.valueOf(medalId));
                api.postMultipart("medal_image_upload",fields,uri,"photo","medal-"+medalId+".png","image/png");
                JSONObject updated=api.bootstrap();
                main.post(()->{data=updated;toast("تصویر مدال ذخیره شد.");render();});
            }catch(Exception e){main.post(()->{setSubtitle("خطای آپلود تصویر مدال",RED);toast(e.getMessage());});}
        });
    }

    private void postAndReload(String action,JSONObject payload,Button button,String success){
        button.setEnabled(false);
        io.execute(()->{try{api.post(action,payload);JSONObject d=api.bootstrap();data=d;main.post(()->{button.setEnabled(true);toast(success);render();});}catch(Exception e){main.post(()->{button.setEnabled(true);toast(e.getMessage());});}});
    }

    private int intValue(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}
    private double decimalValue(EditText e,double fallback){try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception x){return fallback;}}
}
