package ir.pedalpro.app.nativeapp;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NativeChatActivity extends NativeScreenBase {
    private int roomId;
    private long lastId;
    private int myUserId;
    private JSONObject boot;
    private JSONArray messages=new JSONArray();
    private LinearLayout messagesHost;
    private ScrollView messagesScroll;
    private int lastRenderedCount;
    private EditText input;
    private TextView composeContext;
    private Button cancelCompose;
    private int replyTo;
    private int editId;
    private boolean loading;
    private boolean pollInFlight;
    private int roomGeneration;
    private int privateRequestGeneration;
    private boolean isChatVisible;
    private String mode="group";

    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(isFinishing()||!isChatVisible)return;
            if(roomId>0)livePoll();
            main.postDelayed(this,3000);
        }
    };

    @Override protected boolean useFixedContentViewport(){return true;}
    @Override protected String screenTitle(){return "ارتباطات";}

    @Override protected void onScreenReady(){
        setSubtitle("چت گروهی و خصوصی",MUTED);
        content.addView(text("در حال دریافت گفتگوها…",13,false,MUTED),cardLp());
        io.execute(()->{
            try{
                JSONObject b=api.get("chat_bootstrap");
                JSONObject me=api.bootstrap().optJSONObject("user");
                myUserId=me==null?0:me.optInt("id",0);
                boot=b;
                JSONObject group=b.optJSONObject("group");
                roomId=group==null?0:group.optInt("id",0);
                main.post(this::renderShell);
            }catch(Exception e){main.post(()->{content.removeAllViews();addCard("خطا",e.getMessage());});}
        });
    }

    private void renderShell(){
        content.removeAllViews();

        HorizontalScrollView tabsScroll=new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button group=normal("گروه PedalPro");
        group.setOnClickListener(v->openGroup());
        tabs.addView(group,new LinearLayout.LayoutParams(dp(150),dp(48)));

        JSONArray threads=boot==null?null:boot.optJSONArray("private_threads");
        if(threads!=null)for(int i=0;i<threads.length();i++){
            JSONObject t=threads.optJSONObject(i);if(t==null)continue;
            String name=t.optString("other_name",t.optString("display_name","گفتگوی خصوصی"));
            int other=t.optInt("other_id",0);
            int unread=Math.max(0,t.optInt("unread_count",0));
            Button b=normal(name+(unread>0?" · "+JalaliDate.fa(unread):""));
            b.setOnClickListener(v->openPrivate(other));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(150),dp(48));lp.setMargins(dp(5),0,0,0);tabs.addView(b,lp);
        }
        Button newPrivate=normal("گفتگوی جدید");
        newPrivate.setOnClickListener(v->showMemberPicker());
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(130),dp(48));np.setMargins(dp(5),0,0,0);tabs.addView(newPrivate,np);
        tabsScroll.addView(tabs);content.addView(tabsScroll,cardLp());

        LinearLayout messagesCard=card();
        messagesCard.setBackground(round(android.graphics.Color.rgb(9,32,50),24,STROKE));
        messagesScroll=new ScrollView(this);
        messagesScroll.setFillViewport(false);
        messagesScroll.setSmoothScrollingEnabled(true);
        messagesScroll.setVerticalScrollBarEnabled(true);
        messagesHost=new LinearLayout(this);
        messagesHost.setOrientation(LinearLayout.VERTICAL);
        messagesHost.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        messagesScroll.addView(messagesHost);
        messagesCard.addView(messagesScroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,0,1f);
        messageLp.setMargins(0,dp(8),0,dp(8));
        content.addView(messagesCard,messageLp);

        LinearLayout composer=card();
        composeContext=text("",11,false,ACCENT);composeContext.setVisibility(View.GONE);
        input=input("پیام…");input.setMinLines(2);input.setMaxLines(4);
        Button send=primary("ارسال");
        Button cancel=normal("لغو پاسخ / ویرایش");cancel.setVisibility(View.GONE);
        cancelCompose=cancel;
        composer.addView(composeContext);composer.addView(input);composer.addView(send);composer.addView(cancel);
        composer.setBackground(round(android.graphics.Color.rgb(9,32,50),20,STROKE));
        content.addView(composer,cardLp());

        cancel.setOnClickListener(v->resetComposition());
        send.setOnClickListener(v->sendMessage(send,cancel));

        loadMessages(true);
        main.removeCallbacks(poll);main.postDelayed(poll,3000);
        VazirmatnLoader.apply(this,content);
    }

    /** A reply or edit ID must never be carried into another chat room. */
    private void resetComposition(){
        replyTo=0;
        editId=0;
        if(input!=null){input.setText("");input.clearFocus();}
        if(composeContext!=null){
            composeContext.setText("");
            composeContext.setVisibility(View.GONE);
        }
        if(cancelCompose!=null)cancelCompose.setVisibility(View.GONE);
    }

    private void openGroup(){
        privateRequestGeneration++; // Cancel any pending private-chat navigation.
        JSONObject group=boot==null?null:boot.optJSONObject("group");
        roomGeneration++;
        roomId=group==null?0:group.optInt("id",0);mode="group";lastId=0;messages=new JSONArray();
        resetComposition();
        loading=false;
        pollInFlight=false;
        if(messagesHost!=null)messagesHost.removeAllViews();
        lastRenderedCount=0;
        setSubtitle(group==null?"چت گروهی":group.optString("title","چت گروهی"),GREEN);
        loadMessages(true);
    }

    private void openPrivate(int userId){
        if(userId<=0)return;
        final int request=++privateRequestGeneration;
        setSubtitle("در حال بازکردن گفتگوی خصوصی…",MUTED);
        io.execute(()->{
            try{
                JSONObject d=api.post("chat_open_private",new JSONObject().put("user_id",userId));
                JSONObject room=d.optJSONObject("room"),other=d.optJSONObject("other");
                int rid=room==null?0:room.optInt("id",0);
                main.post(()->{
                    if(request!=privateRequestGeneration)return;
                    if(rid<=0){toast("گفتگو ایجاد نشد.");return;}
                    roomGeneration++;
                    roomId=rid;mode="private";lastId=0;messages=new JSONArray();
                    resetComposition();
                    loading=false;
                    pollInFlight=false;
                    if(messagesHost!=null)messagesHost.removeAllViews();
                    lastRenderedCount=0;
                    setSubtitle(other==null?"گفتگوی خصوصی":other.optString("display_name","گفتگوی خصوصی"),GREEN);
                    loadMessages(true);
                });
            }catch(Exception e){main.post(()->{if(request==privateRequestGeneration)toast(e.getMessage());});}
        });
    }

    private void showMemberPicker(){
        JSONArray members=boot==null?null:boot.optJSONArray("members");
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=card();box.addView(text("شروع گفتگوی خصوصی",18,true,TEXT));
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        if(members!=null)for(int i=0;i<members.length();i++){
            JSONObject m=members.optJSONObject(i);if(m==null||m.optInt("id",0)==myUserId)continue;
            int id=m.optInt("id",0);Button b=normal(m.optString("display_name",m.optString("username","عضو")));
            b.setOnClickListener(v->{d.dismiss();openPrivate(id);});list.addView(b);
        }
        scroll.addView(list);box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(420)));d.setContentView(box);d.show();
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));WindowManager.LayoutParams lp=new WindowManager.LayoutParams();lp.copyFrom(w.getAttributes());lp.width=-1;lp.height=-2;lp.gravity=Gravity.BOTTOM;w.setAttributes(lp);}
        VazirmatnLoader.apply(this,box);
    }

    private void loadMessages(boolean full){
        if(roomId<=0||loading||messagesHost==null)return;
        loading=true;
        final int selectedRoom=roomId;
        final int generation=roomGeneration;
        final long after=full?0:lastId;
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();
                q.put("room_id",selectedRoom);
                q.put("after",after);
                q.put("limit",full?160:80);
                JSONObject d=api.get("chat_messages",q);
                JSONArray items=d.optJSONArray("items");
                JSONArray receipts=d.optJSONArray("receipts");
                main.post(()->{
                    if(generation!=roomGeneration||selectedRoom!=roomId)return;
                    loading=false;
                    if(full){messages=new JSONArray();lastId=0;}
                    appendMessages(items);
                    mergeReceipts(receipts);
                    renderMessages();
                });
            }catch(Exception e){
                main.post(()->{
                    if(generation!=roomGeneration||selectedRoom!=roomId)return;
                    loading=false;
                    if(full){
                        messagesHost.removeAllViews();
                        messagesHost.addView(text("دریافت پیام‌ها انجام نشد: "+e.getMessage(),11,false,RED));
                    }
                });
            }
        });
    }

    private void livePoll(){
        if(roomId<=0||loading||pollInFlight)return;
        final int selectedRoom=roomId;
        final int generation=roomGeneration;
        final long after=lastId;
        pollInFlight=true;
        io.execute(()->{
            try{
                JSONObject d=api.post("chat_live",new JSONObject()
                        .put("room_id",selectedRoom).put("after",after).put("typing",false));
                JSONArray items=d.optJSONArray("items");
                JSONObject live=d.optJSONObject("live");
                JSONArray receipts=live==null?null:live.optJSONArray("receipts");
                main.post(()->{
                    if(generation!=roomGeneration||selectedRoom!=roomId||loading)return;
                    boolean changed=appendMessages(items);
                    boolean receiptsChanged=mergeReceipts(receipts);
                    if(changed||receiptsChanged)renderMessages();
                });
            }catch(Exception ignored){}
            finally{
                main.post(()->{
                    if(generation==roomGeneration&&selectedRoom==roomId)pollInFlight=false;
                });
            }
        });
    }

    /** Keep only unique live messages; the server is the source for full history. */
    private boolean appendMessages(JSONArray incoming){
        if(incoming==null||incoming.length()==0)return false;
        boolean changed=false;
        for(int i=0;i<incoming.length();i++){
            JSONObject m=incoming.optJSONObject(i);
            if(m==null)continue;
            long id=m.optLong("id",0L);
            if(id<=0L)continue;
            boolean duplicate=false;
            for(int j=messages.length()-1;j>=0;j--){
                JSONObject existing=messages.optJSONObject(j);
                if(existing!=null&&existing.optLong("id",0L)==id){duplicate=true;break;}
            }
            if(duplicate)continue;
            messages.put(m);
            lastId=Math.max(lastId,id);
            changed=true;
        }
        if(messages.length()>240){
            JSONArray recent=new JSONArray();
            for(int j=messages.length()-240;j<messages.length();j++)
                recent.put(messages.optJSONObject(j));
            messages=recent;
        }
        return changed;
    }

    private boolean mergeReceipts(JSONArray receipts){
        if(receipts==null||receipts.length()==0)return false;
        boolean changed=false;
        for(int i=0;i<receipts.length();i++){
            JSONObject r=receipts.optJSONObject(i);
            if(r==null)continue;
            long id=r.optLong("id",0);
            for(int j=0;j<messages.length();j++){
                JSONObject m=messages.optJSONObject(j);
                if(m==null||m.optLong("id",0)!=id)continue;
                for(String key:new String[]{"delivered_at","read_at"}){
                    if(!r.has(key))continue;
                    Object previous=m.opt(key),next=r.opt(key);
                    if(!java.util.Objects.equals(previous,next)){
                        try{m.put(key,next);changed=true;}
                        catch(Exception ignored){}
                    }
                }
                break;
            }
        }
        return changed;
    }

    private void renderMessages(){
        if(messagesHost==null)return;
        int previous=lastRenderedCount;
        boolean wasAtBottom=messagesScroll==null||
                messagesScroll.getScrollY()+messagesScroll.getHeight()>=
                messagesHost.getHeight()-dp(90);
        messagesHost.removeAllViews();
        if(messages.length()==0){
            lastRenderedCount=0;
            messagesHost.addView(text("هنوز پیامی در این گفتگو نیست.",12,false,MUTED));
            return;
        }
        // Cap native bubble views; full history stays on server and avoids freezes.
        for(int i=Math.max(0,messages.length()-110);i<messages.length();i++){
            JSONObject m=messages.optJSONObject(i);if(m==null)continue;
            addMessageBubble(m);
        }
        lastRenderedCount=messages.length();
        VazirmatnLoader.apply(this,messagesHost);
        if(messagesScroll!=null&&(wasAtBottom||previous==0))
            messagesScroll.post(()->messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageBubble(JSONObject m){
        boolean mine=m.optInt("sender_user_id",0)==myUserId;
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(mine?Gravity.END:Gravity.START);
        LinearLayout bubble=accentCard(mine?ACCENT:STROKE);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,.86f);

        String avatar=m.optString("sender_avatar","");
        if(!avatar.isBlank()){
            ImageView av=new ImageView(this);av.setScaleType(ImageView.ScaleType.CENTER_CROP);RemoteImageLoader.load(this,av,avatar,ir.pedalpro.app.R.drawable.pedalpro_app_icon);
            bubble.addView(av,new LinearLayout.LayoutParams(dp(34),dp(34)));
        }
        if(m.optLong("reply_to_id",0)>0){
            LinearLayout reply=card();reply.setPadding(dp(9),dp(7),dp(9),dp(7));
            reply.addView(text("پاسخ به "+m.optString("reply_sender_name","پیام قبلی"),10,true,MUTED));
            reply.addView(text(m.optString("reply_body","پیام در دسترس نیست"),10,false,MUTED));
            bubble.addView(reply);
        }
        bubble.addView(text(mine?"شما":m.optString("sender_name","عضو"),11,true,mine?ACCENT:ORANGE));
        String body=m.isNull("body")?"":m.optString("body","");
        bubble.addView(text(m.optBoolean("deleted_at",false)?"پیام حذف شده است.":("null".equalsIgnoreCase(body.trim())?"":body),13,false,TEXT));
        String meta=JalaliDate.format(m.optString("created_at",""),true);
        if(m.opt("edited_at")!=null&&!String.valueOf(m.opt("edited_at")).isBlank())meta+=" · ویرایش‌شده";
        if("private".equals(mode)&&mine){
            if(!m.optString("read_at","").isBlank())meta+=" · خوانده شد";
            else if(!m.optString("delivered_at","").isBlank())meta+=" · تحویل شد";
            else meta+=" · ارسال شد";
        }
        bubble.addView(text(meta,9,false,MUTED));

        if(!m.optString("body","").isBlank()){
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button reply=normal("پاسخ");reply.setOnClickListener(v->beginReply(m));
            actions.addView(reply,new LinearLayout.LayoutParams(0,dp(42),1f));
            if(mine){
                Button edit=normal("ویرایش");edit.setOnClickListener(v->beginEdit(m));
                Button del=normal("حذف");del.setOnClickListener(v->deleteMessage(m.optInt("id",0)));
                actions.addView(edit,new LinearLayout.LayoutParams(0,dp(42),1f));actions.addView(del,new LinearLayout.LayoutParams(0,dp(42),1f));
            }
            bubble.addView(actions);
        }
        row.addView(bubble,bp);messagesHost.addView(row,cardLp());
    }

    private void beginReply(JSONObject m){
        replyTo=m.optInt("id",0);editId=0;
        composeContext.setVisibility(View.VISIBLE);
        composeContext.setText("پاسخ به "+m.optString("sender_name","پیام"));
        if(cancelCompose!=null)cancelCompose.setVisibility(View.VISIBLE);
        input.requestFocus();
    }
    private void beginEdit(JSONObject m){
        editId=m.optInt("id",0);replyTo=0;
        composeContext.setVisibility(View.VISIBLE);
        composeContext.setText("ویرایش پیام");
        if(cancelCompose!=null)cancelCompose.setVisibility(View.VISIBLE);
        input.setText(m.optString("body",""));input.requestFocus();
    }

    private void sendMessage(Button send,Button cancel){
        String body=input.getText().toString().trim();if(body.isBlank()||roomId<=0)return;
        send.setEnabled(false);
        final int selectedRoom=roomId;
        final int generation=roomGeneration;
        int rid=replyTo,eid=editId;
        io.execute(()->{
            try{
                if(eid>0)api.post("chat_edit_message",new JSONObject().put("id",eid).put("body",body));
                else api.post("chat_send",new JSONObject().put("room_id",selectedRoom).put("body",body).put("reply_to_id",rid).put("client_token",UUID.randomUUID().toString()));
                main.post(()->{
                    send.setEnabled(true);
                    if(generation!=roomGeneration||selectedRoom!=roomId)return;
                    input.setText("");replyTo=0;editId=0;
                    composeContext.setVisibility(View.GONE);cancel.setVisibility(View.GONE);
                    lastId=0;loadMessages(true);
                });
            }catch(Exception e){main.post(()->{
                send.setEnabled(true);
                if(generation==roomGeneration&&selectedRoom==roomId)toast(e.getMessage());
            });}
        });
    }

    private void deleteMessage(int id){
        if(id<=0)return;
        final int selectedRoom=roomId;
        final int generation=roomGeneration;
        io.execute(()->{
            try{api.post("chat_delete_message",new JSONObject().put("id",id));main.post(()->{
                if(generation!=roomGeneration||selectedRoom!=roomId)return;
                lastId=0;loadMessages(true);
            });}
            catch(Exception e){main.post(()->{
                if(generation==roomGeneration&&selectedRoom==roomId)toast(e.getMessage());
            });}
        });
    }

    @Override protected void onResume(){
        super.onResume();
        isChatVisible=true;
        if(messagesHost!=null){main.removeCallbacks(poll);main.postDelayed(poll,3000);}
    }

    @Override protected void onPause(){
        isChatVisible=false;
        main.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onDestroy(){
        roomGeneration++;
        privateRequestGeneration++;
        main.removeCallbacks(poll);
        super.onDestroy();
    }
}
