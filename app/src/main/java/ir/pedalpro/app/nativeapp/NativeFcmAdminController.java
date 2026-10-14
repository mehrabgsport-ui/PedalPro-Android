package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

final class NativeFcmAdminController {
    static final int REQ_GOOGLE=6201;
    static final int REQ_SERVICE=6202;

    private final NativeSiteSettingsActivity activity;
    private final PedalProApiClient api;
    private final ExecutorService io;

    private Spinner enabled;
    private EditText project;
    private EditText appId;
    private EditText apiKey;
    private EditText sender;
    private TextView state;
    private TextView devices;
    private TextView users;
    private TextView googleState;
    private TextView serviceState;
    private String serviceAccountJson="";
    private boolean serviceSaved=false;

    NativeFcmAdminController(
            NativeSiteSettingsActivity activity,
            PedalProApiClient api,
            ExecutorService io) {
        this.activity=activity;
        this.api=api;
        this.io=io;
    }

    void render(LinearLayout host){
        host.removeAllViews();

        LinearLayout card=activity.card();
        LinearLayout head=new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout copy=new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(activity.text("Push آنی Firebase",18,true,NativeScreenBase.TEXT));
        copy.addView(activity.text(
                "اعلان لحظه‌ای برای اعضا، پیام خصوصی و رویدادها حتی وقتی اپ در پس‌زمینه است.",
                11,false,NativeScreenBase.MUTED));
        head.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        Button refresh=activity.normal("↻ وضعیت");
        refresh.setOnClickListener(v->loadStatus());
        head.addView(refresh,new LinearLayout.LayoutParams(activity.dp(96),activity.dp(44)));
        card.addView(head);

        LinearLayout stats=new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        state=stat("وضعیت Push","—");
        devices=stat("دستگاه‌های ثبت‌شده","0");
        users=stat("کاربران متصل","0");
        stats.addView(state,new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(devices,new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(users,new LinearLayout.LayoutParams(0,-2,1f));
        card.addView(stats);

        enabled=spinner(new String[]{"غیرفعال","فعال"});
        project=activity.input("Project ID");
        appId=activity.input("Firebase App ID — 1:...:android:...");
        apiKey=activity.input("Web API Key");
        sender=activity.input("Sender ID");

        card.addView(enabled);
        card.addView(project);
        card.addView(appId);
        card.addView(apiKey);
        card.addView(sender);

        Button googleFile=activity.normal("انتخاب google-services.json");
        googleFile.setOnClickListener(v->pickJson(REQ_GOOGLE));
        googleState=activity.text("اطلاعات عمومی Firebase را از فایل یا دستی وارد کنید.",11,false,NativeScreenBase.MUTED);
        googleState.setPadding(0,activity.dp(5),0,activity.dp(8));
        card.addView(googleFile);
        card.addView(googleState);

        Button serviceFile=activity.normal("انتخاب Service Account JSON");
        serviceFile.setOnClickListener(v->pickJson(REQ_SERVICE));
        serviceState=activity.text("اگر قبلاً ذخیره شده باشد لازم نیست دوباره فایل را انتخاب کنید.",11,false,NativeScreenBase.MUTED);
        serviceState.setPadding(0,activity.dp(5),0,activity.dp(8));
        card.addView(serviceFile);
        card.addView(serviceState);

        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save=activity.primary("ذخیره تنظیمات Push");
        Button test=activity.normal("ارسال تست به خودم");
        actions.addView(save,new LinearLayout.LayoutParams(0,activity.dp(50),1f));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,activity.dp(50),1f);
        tlp.setMargins(activity.dp(8),0,0,0);
        actions.addView(test,tlp);
        card.addView(actions);

        Button clear=activity.danger("حذف Service Account");
        card.addView(clear);

        TextView help=activity.text(
                "اطلاعات عمومی Firebase توسط اپ از سرور دریافت می‌شود؛ کلید خصوصی Service Account فقط روی سرور ذخیره می‌شود و به اپ یا کاربران ارسال نمی‌شود.",
                11,false,NativeScreenBase.MUTED);
        help.setPadding(0,activity.dp(8),0,0);
        card.addView(help);

        save.setOnClickListener(v->save(save));
        test.setOnClickListener(v->test(test));
        clear.setOnClickListener(v->confirmClear());

        host.addView(card,activity.cardLp());
        loadStatus();
        VazirmatnLoader.apply(activity,host);
    }

    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=REQ_GOOGLE&&requestCode!=REQ_SERVICE)return false;
        if(resultCode!=Activity.RESULT_OK)return true;
        Uri uri=data==null?null:data.getData();
        if(uri==null)return true;
        if(requestCode==REQ_GOOGLE)readGoogle(uri);
        else readService(uri);
        return true;
    }

    private void loadStatus(){
        io.execute(()->{
            try{
                JSONObject d=api.get("fcm_status");
                JSONObject s=d.optJSONObject("status");
                if(s==null)s=new JSONObject();
                JSONObject finalS=s;
                activity.runOnUiThread(()->{
                    boolean on=finalS.optBoolean("enabled",false);
                    enabled.setSelection(on?1:0);
                    project.setText(finalS.optString("project_id",""));
                    appId.setText(finalS.optString("app_id",""));
                    apiKey.setText(finalS.optString("api_key",""));
                    sender.setText(finalS.optString("sender_id",""));
                    devices.setText("دستگاه‌های ثبت‌شده\n"+finalS.optInt("registered_devices",0));
                    users.setText("کاربران متصل\n"+finalS.optInt("registered_users",0));
                    state.setText("وضعیت Push\n"+(on?"فعال":"غیرفعال"));
                    state.setTextColor(on?NativeScreenBase.GREEN:android.graphics.Color.rgb(255,207,101));
                    serviceSaved=finalS.optBoolean("service_account_saved",false);
                    serviceState.setText(serviceSaved
                            ?"Service Account روی سرور ذخیره شده است. برای حفظ آن نیازی به انتخاب مجدد فایل نیست."
                            :"Service Account هنوز ذخیره نشده است.");
                    serviceAccountJson="";
                });
            }catch(Exception e){
                activity.runOnUiThread(()->activity.toast(e.getMessage()));
            }
        });
    }

    private void save(Button b){
        boolean on=enabled.getSelectedItemPosition()==1;
        String p=project.getText().toString().trim();
        String a=appId.getText().toString().trim();
        String k=apiKey.getText().toString().trim();
        String s=sender.getText().toString().trim();
        final String credentials=serviceAccountJson;

        if(p.isBlank()||a.isBlank()||k.isBlank()||s.isBlank()){
            activity.toast("ابتدا google-services.json را انتخاب کن یا چهار مقدار Firebase را کامل وارد کن.");
            return;
        }
        if(on&&credentials.isBlank()&&!serviceSaved){
            activity.toast("برای فعال‌سازی Push، فایل Service Account JSON را هم انتخاب کن.");
            return;
        }

        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject payload=new JSONObject()
                        .put("enabled",on)
                        .put("project_id",p)
                        .put("app_id",a)
                        .put("api_key",k)
                        .put("sender_id",s)
                        .put("service_account_json",credentials);
                api.post("fcm_settings_save",payload);
                activity.runOnUiThread(()->{
                    serviceAccountJson="";
                    b.setEnabled(true);
                    activity.toast(on?"Push Firebase ذخیره و فعال شد":"تنظیمات Firebase ذخیره شد");
                    loadStatus();
                });
            }catch(Exception e){
                activity.runOnUiThread(()->{b.setEnabled(true);activity.toast(e.getMessage());});
            }
        });
    }

    private void test(Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("fcm_test",new JSONObject());
                JSONObject p=d.optJSONObject("push");
                if(p==null)p=new JSONObject();
                JSONObject finalP=p;
                activity.runOnUiThread(()->{
                    b.setEnabled(true);
                    if(!finalP.optBoolean("enabled",false)){
                        activity.toast("Push هنوز فعال نیست.");
                    }else if(finalP.optInt("devices",0)<1){
                        activity.toast("هنوز روی هیچ حسابی توکن دستگاه ثبت نشده است.");
                    }else{
                        activity.toast("تست Push: "+finalP.optInt("sent",0)+" موفق، "+finalP.optInt("failed",0)+" ناموفق");
                    }
                    loadStatus();
                });
            }catch(Exception e){
                activity.runOnUiThread(()->{b.setEnabled(true);activity.toast(e.getMessage());});
            }
        });
    }

    private void confirmClear(){
        new android.app.AlertDialog.Builder(activity)
                .setMessage("Service Account ذخیره‌شده حذف و Push غیرفعال شود؟")
                .setPositiveButton("حذف",(d,w)->clear())
                .setNegativeButton("انصراف",null)
                .show();
    }

    private void clear(){
        // Read Android form fields on the main thread before clearing the remote credentials.
        final String projectId=project.getText().toString().trim();
        final String applicationId=appId.getText().toString().trim();
        final String publicKey=apiKey.getText().toString().trim();
        final String senderId=sender.getText().toString().trim();
        io.execute(()->{
            try{
                api.post("fcm_settings_save",new JSONObject()
                        .put("enabled",false)
                        .put("project_id",projectId)
                        .put("app_id",applicationId)
                        .put("api_key",publicKey)
                        .put("sender_id",senderId)
                        .put("clear_service_account",true));
                activity.runOnUiThread(()->{
                    serviceAccountJson="";
                    activity.toast("Service Account حذف شد");
                    loadStatus();
                });
            }catch(Exception e){activity.runOnUiThread(()->activity.toast(e.getMessage()));}
        });
    }

    private void pickJson(int request){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        activity.startActivityForResult(i,request);
    }

    private void readGoogle(Uri uri){
        io.execute(()->{
            try{
                JSONObject j=new JSONObject(readText(uri));
                JSONObject pi=j.optJSONObject("project_info");
                JSONArray clients=j.optJSONArray("client");
                JSONObject client=null;
                if(clients!=null){
                    for(int i=0;i<clients.length();i++){
                        JSONObject c=clients.optJSONObject(i);
                        JSONObject info=c==null?null:c.optJSONObject("client_info");
                        JSONObject android=info==null?null:info.optJSONObject("android_client_info");
                        if(android!=null&&"ir.pedalpro.app".equals(android.optString("package_name"))){client=c;break;}
                        if(client==null)client=c;
                    }
                }
                JSONObject ci=client==null?null:client.optJSONObject("client_info");
                JSONArray keys=client==null?null:client.optJSONArray("api_key");
                JSONObject key=keys==null?null:keys.optJSONObject(0);
                String projectId=pi==null?"":pi.optString("project_id","");
                String senderId=pi==null?"":pi.optString("project_number","");
                String app=ci==null?"":ci.optString("mobilesdk_app_id","");
                String webKey=key==null?"":key.optString("current_key","");
                if(projectId.isBlank()||senderId.isBlank()||app.isBlank()||webKey.isBlank()){
                    throw new IllegalStateException("این google-services.json کامل نیست یا مربوط به PedalPro نیست.");
                }
                activity.runOnUiThread(()->{
                    project.setText(projectId);
                    sender.setText(senderId);
                    appId.setText(app);
                    apiKey.setText(webKey);
                    googleState.setText("✓ اطلاعات Firebase از فایل خوانده شد");
                    googleState.setTextColor(NativeScreenBase.GREEN);
                    activity.toast("اطلاعات عمومی Firebase تکمیل شد");
                });
            }catch(Exception e){activity.runOnUiThread(()->activity.toast(e.getMessage()));}
        });
    }

    private void readService(Uri uri){
        io.execute(()->{
            try{
                String raw=readText(uri);
                JSONObject j=new JSONObject(raw);
                if(!"service_account".equals(j.optString("type"))
                        ||j.optString("client_email","").isBlank()
                        ||j.optString("private_key","").isBlank()
                        ||j.optString("project_id","").isBlank()){
                    throw new IllegalStateException("این فایل Service Account معتبر نیست.");
                }
                serviceAccountJson=raw;
                String email=j.optString("client_email");
                activity.runOnUiThread(()->{
                    serviceState.setText("✓ Service Account آماده ذخیره روی سرور است: "+email);
                    serviceState.setTextColor(NativeScreenBase.GREEN);
                    activity.toast("Service Account معتبر است");
                });
            }catch(Exception e){
                serviceAccountJson="";
                activity.runOnUiThread(()->activity.toast(e.getMessage()));
            }
        });
    }

    private String readText(Uri uri)throws Exception{
        StringBuilder out=new StringBuilder();
        try(InputStream in=activity.getContentResolver().openInputStream(uri);
            BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String line;
            while((line=br.readLine())!=null)out.append(line).append('\n');
        }
        return out.toString();
    }

    private TextView stat(String title,String value){
        TextView t=activity.text(title+"\n"+value,12,true,NativeScreenBase.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(activity.dp(8),activity.dp(12),activity.dp(8),activity.dp(12));
        t.setBackground(activity.round(
                android.graphics.Color.rgb(9,26,41),13,
                android.graphics.Color.rgb(41,70,95)));
        return t;
    }

    private Spinner spinner(String[] labels){
        Spinner s=new Spinner(activity);
        s.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels));
        s.setBackground(activity.round(
                android.graphics.Color.argb(122,5,25,40),12,
                android.graphics.Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,activity.dp(50));
        lp.setMargins(0,activity.dp(6),0,activity.dp(6));
        s.setLayoutParams(lp);
        return s;
    }
}
