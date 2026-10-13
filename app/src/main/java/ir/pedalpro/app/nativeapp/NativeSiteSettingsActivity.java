package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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

    @Override protected String screenTitle() { return "تنظیمات سایت"; }

    @Override protected void onScreenReady() { load("general"); }

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
        addTab(row, "appearance", "ظاهر سایت و اپ");
        addTab(row, "medals", "مدال‌ها");
        addTab(row, "users", "کاربران");
        addTab(row, "sms", "پیامک");
        addTab(row, "appmanager", "اپلیکیشن و اعلان‌ها");
        addTab(row, "push", "Push");
        addTab(row, "system", "سیستم");

        hsv.addView(row);
        content.addView(hsv, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void addTab(LinearLayout row, String key, String label) {
        Button b = normal(label);
        b.setTag(key);
        b.setTextColor(key.equals(activeTab) ? Color.rgb(4,36,24) : TEXT);
        if (key.equals(activeTab)) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.rgb(23,201,145), Color.rgb(46,230,166)});
            g.setCornerRadius(dp(12));
            b.setBackground(g);
        }
        b.setOnClickListener(v -> load(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(label.length() > 10 ? 142 : 104), dp(44));
        lp.setMargins(dp(4), dp(5), dp(4), dp(5));
        row.addView(b, lp);
        tabButtons.add(b);
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

        JSONArray members = data.optJSONArray("members");
        if (members != null) {
            for (int i=0;i<members.length();i++) {
                JSONObject u = members.optJSONObject(i); if (u == null) continue;
                LinearLayout row = card();
                row.addView(text(u.optString("display_name",u.optString("username","کاربر")), 14, true, TEXT));
                row.addView(text("@"+u.optString("username","")+" · "+u.optString("role","member")+" · "+(u.optInt("is_active",1)==1?"فعال":"غیرفعال"), 10, false, MUTED));
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
            JSONObject p=new JSONObject();
            try{
                p.put("id",intValue(id,0));p.put("display_name",name.getText().toString());p.put("username",username.getText().toString());
                p.put("phone",phone.getText().toString());p.put("role",role.getText().toString().isBlank()?"member":role.getText().toString());
                p.put("password",password.getText().toString());p.put("age",age.getText().toString());p.put("height_cm",height.getText().toString());
                p.put("weight_kg",weight.getText().toString());p.put("profile_public",true);p.put("bio",bio.getText().toString());p.put("is_active",true);
            }catch(Exception ignored){}
            postAndReload("admin_user_save",p,save,"کاربر ذخیره شد");
        });
        form.addView(save);content.addView(form,cardLp());

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
        LinearLayout release=card();release.addView(text("مدیریت نسخه اپلیکیشن",18,true,TEXT));
        TextView state=text("در حال بررسی وضعیت…",12,false,MUTED);release.addView(state);
        Button refresh=normal("↻ بررسی وضعیت");
        refresh.setOnClickListener(v->loadReleaseStatus(state,refresh));
        release.addView(refresh);content.addView(release,cardLp());loadReleaseStatus(state,refresh);

        LinearLayout notif=card();notif.addView(text("ارسال اعلان به اعضا",18,true,TEXT));
        EditText title=input("عنوان اعلان"),body=input("متن اعلان"),target=input("مخاطب: all / members / admins / user"),userId=input("شناسه کاربر (برای user)"),url=input("لینک داخلی اختیاری");
        body.setSingleLine(false);body.setMinLines(3);target.setText("all");
        Button send=primary("ارسال اعلان");
        send.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("title",title.getText().toString());p.put("body",body.getText().toString());p.put("target",target.getText().toString());p.put("user_id",intValue(userId,0));p.put("url",url.getText().toString());}catch(Exception ignored){}postAndReload("app_notification_send",p,send,"اعلان ارسال شد");});
        notif.addView(title);notif.addView(body);notif.addView(target);notif.addView(userId);notif.addView(url);notif.addView(send);content.addView(notif,cardLp());
    }

    private void loadReleaseStatus(TextView state,Button b){
        b.setEnabled(false);io.execute(()->{try{JSONObject d=api.get("app_release_status");JSONObject r=d.optJSONObject("release");main.post(()->{b.setEnabled(true);if(r==null){state.setText("نسخه منتشرشده‌ای ثبت نشده است.");return;}state.setText("آخرین نسخه: "+r.optString("version_name","—")+"\nVersion Code: "+r.optInt("version_code",0)+"\nزمان انتشار: "+r.optString("published_at","—"));});}catch(Exception e){main.post(()->{b.setEnabled(true);state.setText(e.getMessage());});}});
    }

    private void renderPush(){
        LinearLayout box=card();box.addView(text("Firebase Cloud Messaging",18,true,TEXT));
        TextView state=text("در حال دریافت وضعیت Push…",12,false,MUTED);box.addView(state);
        Button refresh=normal("بررسی وضعیت");
        refresh.setOnClickListener(v->loadFcmStatus(state,refresh));
        Button test=primary("ارسال تست به خودم");
        test.setOnClickListener(v->postAndReload("fcm_test",new JSONObject(),test,"اعلان تست ارسال شد"));
        box.addView(refresh);box.addView(test);content.addView(box,cardLp());loadFcmStatus(state,refresh);
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

    private void postAndReload(String action,JSONObject payload,Button button,String success){
        button.setEnabled(false);
        io.execute(()->{try{api.post(action,payload);JSONObject d=api.bootstrap();data=d;main.post(()->{button.setEnabled(true);toast(success);render();});}catch(Exception e){main.post(()->{button.setEnabled(true);toast(e.getMessage());});}});
    }

    private int intValue(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}
    private double decimalValue(EditText e,double fallback){try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception x){return fallback;}}
}
