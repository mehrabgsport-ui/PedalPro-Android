package ir.pedalpro.app.nativeapp;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.UUID;

/** Dedicated authenticated group room; membership must be checked for every GET/POST in PHP. */
public final class NativeGroupChatActivity extends NativeScreenBase {
    private int groupId;
    private String groupName;
    private LinearLayout rows;
    private EditText composer;
    private boolean active;
    private int generation;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!active||isFinishing())return;
            fetch();
            main.postDelayed(this,10000);
        }
    };
    @Override protected String screenTitle(){return "گفت‌وگوی گروه";}
    @Override protected void onScreenReady(){
        groupId=getIntent().getIntExtra("group_id",0);
        groupName=getIntent().getStringExtra("group_name");
        if(groupId<=0){addCard("گروه نامعتبر","شناسه گروه معتبر نیست.");return;}
        setSubtitle(groupName==null?"":groupName,MUTED);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows,cardLp());
        composer=input("پیام برای اعضای گروه");
        composer.setMinLines(2);
        content.addView(composer,cardLp());
        Button send=primary("ارسال پیام");
        send.setOnClickListener(v->send(send));
        content.addView(send,cardLp());
    }
    @Override protected void onResume(){
        super.onResume();active=true;main.removeCallbacks(tick);main.post(tick);
    }
    @Override protected void onPause(){
        active=false;generation++;main.removeCallbacks(tick);super.onPause();
    }
    @Override protected void onDestroy(){
        active=false;main.removeCallbacks(tick);super.onDestroy();
    }
    private void fetch(){
        final int sequence=++generation;
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_group_messages",Collections.singletonMap("group_id",groupId));
                JSONArray messages=d.optJSONArray("messages");
                main.post(()->{
                    if(!active||sequence!=generation||rows==null)return;
                    rows.removeAllViews();
                    if(messages==null||messages.length()==0){
                        rows.addView(text("هنوز پیامی ثبت نشده است.",12,false,MUTED));return;
                    }
                    int first=Math.max(0,messages.length()-80);
                    for(int i=first;i<messages.length();i++){
                        JSONObject m=messages.optJSONObject(i);if(m==null)continue;
                        LinearLayout bubble=card();
                        bubble.addView(text(UiSafeText.display(m.optString("sender_name","عضو"))+
                            " · "+JalaliDate.format(m.optString("created_at",""),true),10,true,ACCENT));
                        bubble.addView(text(UiSafeText.display(m.optString("body","")),13,false,TEXT));
                        rows.addView(bubble,cardLp());
                    }
                    VazirmatnLoader.apply(this,rows);
                });
            }catch(Exception e){main.post(()->{
                if(active&&sequence==generation)rows.addView(text(
                    "دریافت پیام‌ها ناموفق بود: "+UiSafeText.display(e.getMessage()),12,false,RED));
            });}
        });
    }
    private void send(Button button){
        String value=composer.getText().toString().trim();
        if(value.isEmpty()||value.length()>4000){toast("پیام باید بین ۱ تا ۴۰۰۰ نویسه باشد.");return;}
        button.setEnabled(false);
        JSONObject data=new JSONObject().put("group_id",groupId).put("body",value)
            .put("client_nonce",UUID.randomUUID().toString());
        io.execute(()->{
            try{
                api.post("pp_group_message_send",data);
                main.post(()->{
                    button.setEnabled(true);
                    // Only clear if user has not edited the draft during request.
                    if(value.equals(composer.getText().toString().trim()))composer.setText("");
                    fetch();
                });
            }catch(Exception e){main.post(()->{
                button.setEnabled(true);toast("پیام ارسال نشد: "+e.getMessage());
            });}
        });
    }
}
