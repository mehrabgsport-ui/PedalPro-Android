package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

final class NativeAppReleaseAdminController {
    static final int REQ_APK=6101;

    private final NativeSiteSettingsActivity activity;
    private final PedalProApiClient api;
    private final ExecutorService io;
    private JSONObject bootstrap;
    private Uri apkUri;
    private String apkName="";
    private long apkSize=0;
    private TextView selectedFile;
    private EditText versionName;
    private EditText versionCode;
    private Spinner mandatory;
    private EditText releaseNotes;
    private Button publish;
    private LinearLayout history;

    NativeAppReleaseAdminController(
            NativeSiteSettingsActivity activity,
            PedalProApiClient api,
            ExecutorService io) {
        this.activity=activity;
        this.api=api;
        this.io=io;
    }

    void render(LinearLayout host,JSONObject data){
        bootstrap=data;
        host.removeAllViews();

        LinearLayout release=activity.card();
        release.addView(activity.text("مدیریت نسخه اپلیکیشن",18,true,NativeScreenBase.TEXT));
        TextView intro=activity.text(
                "APK جدید را همین‌جا منتشر کنید؛ کاربران از داخل اپ نسخه جدید را می‌بینند و دانلود می‌کنند.",
                11,false,NativeScreenBase.MUTED);
        intro.setPadding(0,activity.dp(5),0,activity.dp(8));
        release.addView(intro);

        LinearLayout stats=new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView latest=stat("آخرین نسخه","—");
        TextView code=stat("Version Code","—");
        TextView published=stat("زمان انتشار","—");
        stats.addView(latest,new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(code,new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(published,new LinearLayout.LayoutParams(0,-2,1f));
        release.addView(stats);

        versionName=activity.input("شماره نسخه — مثلاً 1.2.1");
        versionCode=activity.input("Version Code — مثلاً 4");
        versionCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        mandatory=spinner(new String[]{"اختیاری","اجباری"});
        releaseNotes=activity.input("توضیحات نسخه");
        releaseNotes.setSingleLine(false);
        releaseNotes.setMinLines(4);

        Button choose=activity.normal("انتخاب فایل APK");
        choose.setOnClickListener(v->pickApk());
        selectedFile=activity.text("فایلی انتخاب نشده است.",11,false,NativeScreenBase.MUTED);
        selectedFile.setPadding(0,activity.dp(6),0,activity.dp(6));

        release.addView(versionName);
        release.addView(versionCode);
        release.addView(mandatory);
        release.addView(choose);
        release.addView(selectedFile);
        release.addView(activity.text(
                "فایل به قطعات ۵۱۲ کیلوبایتی ارسال می‌شود؛ محدودیت ۲MB هاست مانع آپلود APK نمی‌شود.",
                11,false,NativeScreenBase.MUTED));
        release.addView(releaseNotes);

        publish=activity.primary("انتشار نسخه جدید");
        publish.setOnClickListener(v->publish());
        release.addView(publish);

        TextView installNote=activity.text(
                "Android برای امنیت اجازه نصب کاملاً بی‌صدا را به اپ عادی نمی‌دهد. PedalPro فایل را خودش دانلود می‌کند و سپس صفحه رسمی نصب Android را باز می‌کند؛ کاربر فقط تأیید نهایی «Install» را می‌زند. بار اول نیز باید اجازه «Install unknown apps» برای PedalPro فعال شود.",
                11,false,NativeScreenBase.TEXT);
        installNote.setPadding(activity.dp(12),activity.dp(11),activity.dp(12),activity.dp(11));
        installNote.setBackground(activity.round(
                android.graphics.Color.argb(8,255,255,255),14,
                android.graphics.Color.argb(28,255,255,255)));
        release.addView(installNote,activity.cardLp());
        host.addView(release,activity.cardLp());

        LinearLayout notifications=activity.card();
        notifications.addView(activity.text("ارسال اعلان به اعضا",18,true,NativeScreenBase.TEXT));
        notifications.addView(activity.text(
                "اعلان در اپ PedalPro نمایش داده می‌شود. اگر Push Firebase فعال باشد اعلان بلافاصله ارسال می‌شود؛ Polling داخل اپ نیز به‌عنوان مسیر پشتیبان باقی می‌ماند.",
                11,false,NativeScreenBase.MUTED));

        EditText title=activity.input("عنوان اعلان — مثلاً برنامه جمعه");
        EditText body=activity.input("متن اعلان برای اعضا");
        body.setSingleLine(false);
        body.setMinLines(4);
        Spinner target=spinner(new String[]{"همه کاربران","فقط اعضا","فقط مدیران","یک کاربر مشخص"});
        Spinner user=usersSpinner(data);
        user.setVisibility(View.GONE);
        target.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                user.setVisibility(pos==3?View.VISIBLE:View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        EditText actionUrl=activity.input("لینک داخلی اختیاری — مثلاً /?page=activities");

        Button send=activity.primary("ارسال اعلان");
        send.setOnClickListener(v->sendNotification(title,body,target,user,actionUrl,send));
        Button refresh=activity.normal("↻ تاریخچه");
        refresh.setOnClickListener(v->loadHistory());

        notifications.addView(title);
        notifications.addView(body);
        notifications.addView(target);
        notifications.addView(user);
        notifications.addView(actionUrl);
        notifications.addView(send);
        notifications.addView(refresh);

        history=new LinearLayout(activity);
        history.setOrientation(LinearLayout.VERTICAL);
        notifications.addView(history);
        host.addView(notifications,activity.cardLp());

        loadStatus(latest,code,published);
        loadHistory();
        VazirmatnLoader.apply(activity,host);
    }

    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=REQ_APK)return false;
        if(resultCode!=Activity.RESULT_OK)return true;
        Uri uri=data==null?null:data.getData();
        if(uri==null)return true;
        apkUri=uri;
        queryFile(uri);
        selectedFile.setText(apkName+" · "+String.format(Locale.US,"%.1f MB",apkSize/1024.0/1024.0));
        return true;
    }

    private void loadStatus(TextView latest,TextView code,TextView published){
        io.execute(()->{
            try{
                JSONObject d=api.get("app_release_status");
                JSONObject r=d.optJSONObject("release");
                activity.runOnUiThread(()->{
                    if(r==null){
                        latest.setText("آخرین نسخه\n—");
                        code.setText("Version Code\n—");
                        published.setText("زمان انتشار\n—");
                    }else{
                        latest.setText("آخرین نسخه\n"+r.optString("version_name","—"));
                        code.setText("Version Code\n"+r.optInt("version_code",0));
                        published.setText("زمان انتشار\n"+JalaliDate.format(r.optString("published_at",""),true));
                        versionName.setText(r.optString("version_name",""));
                        versionCode.setText(String.valueOf(r.optInt("version_code",0)+1));
                    }
                    selectedFile.setHint("آپلود تکه‌ای فعال است — محدودیت PHP: upload_max_filesize="+
                            d.optString("php_upload_max_filesize","؟")+" / post_max_size="+
                            d.optString("php_post_max_size","؟"));
                });
            }catch(Exception e){activity.runOnUiThread(()->activity.toast(e.getMessage()));}
        });
    }

    private void pickApk(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        activity.startActivityForResult(i,REQ_APK);
    }

    private void publish(){
        if(apkUri==null){activity.toast("فایل APK را انتخاب کنید.");return;}
        String name=versionName.getText().toString().trim();
        int code=number(versionCode,0);
        if(name.isBlank()||code<=0){activity.toast("شماره نسخه و Version Code را وارد کنید.");return;}
        if(!apkName.toLowerCase(Locale.ROOT).endsWith(".apk")){activity.toast("فایل باید APK باشد.");return;}
        if(apkSize>150L*1024L*1024L){activity.toast("حجم APK بیش از ۱۵۰ مگابایت است.");return;}

        new android.app.AlertDialog.Builder(activity)
                .setMessage("نسخه "+name+" منتشر شود؟")
                .setPositiveButton("انتشار",(d,w)->doPublish(name,code))
                .setNegativeButton("انصراف",null)
                .show();
    }

    private void doPublish(String name,int code){
        publish.setEnabled(false);
        final long chunk=512L*1024L;
        final int total=(int)Math.ceil(apkSize/(double)chunk);
        final String uploadId=UUID.randomUUID().toString().replace("-","");
        io.execute(()->{
            try{
                for(int i=0;i<total;i++){
                    final int index=i;
                    long offset=i*chunk;
                    long length=Math.min(chunk,apkSize-offset);
                    Map<String,String> fields=new HashMap<>();
                    fields.put("upload_id",uploadId);
                    fields.put("chunk_index",String.valueOf(i));
                    fields.put("total_chunks",String.valueOf(total));
                    activity.runOnUiThread(()->publish.setText("در حال آپلود… "+Math.round(index/(double)total*100)+"٪"));
                    api.postMultipartChunk(
                            "app_release_upload_chunk",fields,apkUri,
                            "chunk","chunk-"+i+".part","application/octet-stream",offset,length);
                }

                activity.runOnUiThread(()->publish.setText("در حال نهایی‌سازی…"));
                JSONObject payload=new JSONObject()
                        .put("upload_id",uploadId)
                        .put("total_chunks",total)
                        .put("original_name",apkName)
                        .put("version_name",name)
                        .put("version_code",code)
                        .put("release_notes",releaseNotes.getText().toString())
                        .put("is_mandatory",mandatory.getSelectedItemPosition()==1?1:0);
                JSONObject done=api.post("app_release_upload_finalize",payload);
                activity.runOnUiThread(()->{
                    publish.setEnabled(true);
                    publish.setText("انتشار نسخه جدید");
                    apkUri=null;apkName="";apkSize=0;
                    selectedFile.setText("فایلی انتخاب نشده است.");
                    activity.toast("نسخه "+done.optJSONObject("release").optString("version_name",name)+" منتشر شد");
                    activity.reloadActiveTab();
                });
            }catch(Exception e){
                try{api.post("app_release_upload_abort",new JSONObject().put("upload_id",uploadId));}catch(Exception ignored){}
                activity.runOnUiThread(()->{
                    publish.setEnabled(true);
                    publish.setText("انتشار نسخه جدید");
                    activity.toast(e.getMessage());
                });
            }
        });
    }

    private void sendNotification(EditText title,EditText body,Spinner target,Spinner user,EditText url,Button send){
        String t=title.getText().toString().trim(),b=body.getText().toString().trim();
        if(t.isBlank()||b.isBlank()){activity.toast("عنوان و متن اعلان را وارد کنید.");return;}
        String[] targets={"all","members","admins","user"};
        int pos=Math.max(0,Math.min(3,target.getSelectedItemPosition()));
        String value=pos==3?String.valueOf(userId(user)):"";
        if(pos==3&&"0".equals(value)){activity.toast("کاربر مقصد را انتخاب کنید.");return;}

        send.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("app_notification_send",new JSONObject()
                        .put("title",t).put("body",b)
                        .put("action_url",url.getText().toString().trim())
                        .put("target_type",targets[pos]).put("target_value",value));
                activity.runOnUiThread(()->{
                    send.setEnabled(true);
                    title.setText("");body.setText("");url.setText("");
                    activity.toast("اعلان برای حدود "+d.optInt("target_count",0)+" کاربر ثبت شد");
                    loadHistory();
                });
            }catch(Exception e){activity.runOnUiThread(()->{send.setEnabled(true);activity.toast(e.getMessage());});}
        });
    }

    private void loadHistory(){
        if(history==null)return;
        history.removeAllViews();
        history.addView(activity.text("در حال دریافت…",11,false,NativeScreenBase.MUTED));
        io.execute(()->{
            try{
                JSONObject d=api.get("app_notifications_admin_list");
                JSONArray rows=d.optJSONArray("items");
                activity.runOnUiThread(()->paintHistory(rows));
            }catch(Exception e){activity.runOnUiThread(()->{history.removeAllViews();history.addView(activity.text(e.getMessage(),11,false,NativeScreenBase.RED));});}
        });
    }

    private void paintHistory(JSONArray rows){
        history.removeAllViews();
        if(rows==null||rows.length()==0){
            history.addView(activity.text("هنوز اعلانی ارسال نشده است.",11,false,NativeScreenBase.MUTED));
            return;
        }
        for(int i=0;i<rows.length();i++){
            JSONObject n=rows.optJSONObject(i);if(n==null)continue;
            LinearLayout row=activity.card();
            row.addView(activity.text(n.optString("title","اعلان"),13,true,NativeScreenBase.TEXT));
            row.addView(activity.text(n.optString("target_label","")+" · "+JalaliDate.format(n.optString("created_at",""),true),10,false,NativeScreenBase.MUTED));
            row.addView(activity.text(n.optString("body",""),12,false,NativeScreenBase.TEXT));
            if(!n.optString("action_url","").isBlank())row.addView(activity.text(n.optString("action_url"),10,false,NativeScreenBase.MUTED));
            Button toggle=activity.normal(n.optInt("is_active",1)==1?"مخفی کردن":"فعال کردن");
            int id=n.optInt("id",0);boolean next=n.optInt("is_active",1)!=1;
            toggle.setOnClickListener(v->toggleNotification(id,next,toggle));
            row.addView(toggle);
            history.addView(row,activity.cardLp());
        }
    }

    private void toggleNotification(int id,boolean active,Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                api.post("app_notification_toggle",new JSONObject().put("id",id).put("is_active",active));
                activity.runOnUiThread(this::loadHistory);
            }catch(Exception e){activity.runOnUiThread(()->{b.setEnabled(true);activity.toast(e.getMessage());});}
        });
    }

    private TextView stat(String title,String value){
        TextView t=activity.text(title+"\n"+value,12,true,NativeScreenBase.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(activity.dp(8),activity.dp(12),activity.dp(8),activity.dp(12));
        return t;
    }

    private Spinner spinner(String[] labels){
        Spinner s=new Spinner(activity);
        ArrayAdapter<String> a=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels);
        s.setAdapter(a);
        s.setBackground(activity.round(android.graphics.Color.argb(122,5,25,40),12,android.graphics.Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,activity.dp(50));lp.setMargins(0,activity.dp(6),0,activity.dp(6));s.setLayoutParams(lp);
        return s;
    }

    private Spinner usersSpinner(JSONObject data){
        ArrayList<String> labels=new ArrayList<>();JSONArray ids=new JSONArray();
        labels.add("انتخاب کاربر");ids.put(0);
        JSONArray users=data==null?null:data.optJSONArray("admin_users");
        if(users==null)users=data==null?null:data.optJSONArray("members");
        if(users!=null)for(int i=0;i<users.length();i++){
            JSONObject u=users.optJSONObject(i);if(u==null)continue;
            labels.add(u.optString("display_name",u.optString("username","#"+u.optInt("id",0))));
            ids.put(u.optInt("id",0));
        }
        Spinner s=spinner(labels.toArray(new String[0]));s.setTag(ids);return s;
    }

    private int userId(Spinner s){Object tag=s.getTag();return tag instanceof JSONArray?((JSONArray)tag).optInt(s.getSelectedItemPosition(),0):0;}
    private int number(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}

    private void queryFile(Uri uri){
        apkName="release.apk";apkSize=0;
        try(Cursor c=activity.getContentResolver().query(uri,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si=c.getColumnIndex(OpenableColumns.SIZE);
                if(ni>=0)apkName=c.getString(ni);
                if(si>=0&&!c.isNull(si))apkSize=c.getLong(si);
            }
        }
    }
}
