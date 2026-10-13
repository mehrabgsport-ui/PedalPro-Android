package ir.pedalpro.app.nativeapp;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NativeChatActivity extends NativeScreenBase {
    private int roomId=0;
    private long lastId=0;
    private LinearLayout messages;
    private EditText input;
    private boolean loading=false;

    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(!isFinishing())loadMessages(false);
            main.postDelayed(this,3000);
        }
    };

    @Override protected String screenTitle(){return "گفتگو";}

    @Override protected void onScreenReady(){
        LinearLayout composer=card();
        messages=new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        content.addView(messages,cardLp());

        input=input("پیام…");
        Button send=primary("ارسال");
        send.setOnClickListener(v->send());
        composer.addView(input);
        composer.addView(send);
        content.addView(composer,cardLp());

        setSubtitle("در حال اتصال به چت گروه…",MUTED);
        io.execute(()->{
            try{
                JSONObject boot=api.get("chat_bootstrap");
                JSONObject group=boot.optJSONObject("group");
                roomId=group==null?0:group.optInt("id",0);
                String title=group==null?"چت گروه PedalPro":group.optString("title","چت گروه PedalPro");
                main.post(()->{
                    setSubtitle(title,roomId>0?GREEN:ORANGE);
                    if(roomId<=0)addCard("گفتگو","چت گروه در دسترس نیست.");
                    else loadMessages(true);
                });
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا در اتصال",RED);addCard("خطا",e.getMessage());});
            }
        });
        main.postDelayed(poll,3000);
    }

    private void loadMessages(boolean full){
        if(roomId<=0||loading)return;
        loading=true;
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();
                q.put("room_id",roomId);
                q.put("after",full?0:lastId);
                q.put("limit",full?120:80);
                JSONObject d=api.get("chat_messages",q);
                JSONArray items=d.optJSONArray("items");
                main.post(()->renderMessages(items,full));
            }catch(Exception e){
                if(full)main.post(()->toast(e.getMessage()));
            }finally{loading=false;}
        });
    }

    private void renderMessages(JSONArray items,boolean full){
        if(full){messages.removeAllViews();lastId=0;}
        if(items==null)return;
        for(int i=0;i<items.length();i++){
            JSONObject m=items.optJSONObject(i);if(m==null)continue;
            long id=m.optLong("id",0);if(id<=lastId&&!full)continue;
            lastId=Math.max(lastId,id);
            String who=m.optString("display_name",m.optString("sender_name","عضو"));
            String body=m.optString("body","");
            LinearLayout bubble=card();
            bubble.addView(text(who,12,true,ACCENT));
            bubble.addView(text(body,14,false,TEXT));
            String time=m.optString("created_at","");
            if(!time.isBlank())bubble.addView(text(time,10,false,MUTED));
            messages.addView(bubble,cardLp());
        }
    }

    private void send(){
        if(roomId<=0)return;
        String body=input.getText().toString().trim();
        if(body.isBlank())return;
        input.setText("");
        io.execute(()->{
            try{
                JSONObject d=api.post("chat_send",new JSONObject()
                        .put("room_id",roomId)
                        .put("body",body)
                        .put("reply_to_id",0)
                        .put("client_token",UUID.randomUUID().toString()));
                main.post(()->loadMessages(false));
            }catch(Exception e){
                main.post(()->{input.setText(body);toast(e.getMessage());});
            }
        });
    }

    @Override protected void onDestroy(){
        main.removeCallbacks(poll);
        super.onDestroy();
    }
}
