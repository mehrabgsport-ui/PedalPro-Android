package ir.pedalpro.app.nativeapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Native group membership, invitations, scheduling and group scores.
 * Requires server-side ACL + CSRF for all pp_group_* endpoints. There is no
 * client-side assumption that a returned "admin" or "private" flag grants rights.
 */
public final class NativeGroupsActivity extends NativeScreenBase {
    private JSONArray groups=new JSONArray(),events=new JSONArray(),invites=new JSONArray();
    private int selectedGroupId=0, generation=0;
    private boolean apiReady=false;
    private EditText query;
    private LinearLayout results;

    @Override protected String screenTitle(){return "گروه‌ها";}
    @Override protected void onScreenReady(){load();}
    private static JSONObject obj(Object... pairs){
        JSONObject output=new JSONObject();
        try{
            for(int i=0;i+1<pairs.length;i+=2)output.put((String)pairs[i],pairs[i+1]);
        }catch(org.json.JSONException e){throw new IllegalArgumentException("Unable to serialize group request",e);}
        return output;
    }
    private JSONObject o(int id){return obj("group_id",id);}
    private static String title(JSONObject o){return o.optString("name",o.optString("title","گروه"));}
    private void load(){
        final int request=++generation;
        content.removeAllViews();
        setSubtitle("در حال دریافت گروه‌ها و دعوت‌نامه‌ها…",MUTED);
        content.addView(text("در حال دریافت اطلاعات مجاز…",13,false,MUTED));
        io.execute(()->{
            try{
                JSONObject r=api.get("pp_groups_list");
                main.post(()->{
                    if(request!=generation||isFinishing())return;
                    apiReady=true;
                    groups=r.optJSONArray("groups")==null?new JSONArray():r.optJSONArray("groups");
                    events=r.optJSONArray("events")==null?new JSONArray():r.optJSONArray("events");
                    invites=r.optJSONArray("invitations")==null?new JSONArray():r.optJSONArray("invitations");
                    render();
                });
            }catch(Exception failure){
                main.post(()->{
                    if(request!=generation||isFinishing())return;
                    apiReady=false;content.removeAllViews();
                    setSubtitle("اتصال گروه‌ها به Backend بررسی شود",ORANGE);
                    addCard("سرویس گروه‌ها فعال نیست",
                        "API گروه‌ها روی سرور پاسخ معتبر نداد. تا تکمیل و بررسی سمت سرور، دعوت، عضویت و پیام‌ها فعال نمی‌شوند. "+
                        UiSafeText.display(failure.getMessage()));
                    Button retry=normal("تلاش دوباره");retry.setOnClickListener(v->load());
                    content.addView(retry,cardLp());
                });
            }
        });
    }
    private void render(){
        if(!apiReady)return;
        content.removeAllViews();
        setSubtitle("کشف گروه‌ها، عضویت و برنامه‌ریزی",GREEN);
        LinearLayout top=card();
        Button create=primary("ایجاد گروه جدید");
        create.setOnClickListener(v->createGroup());
        top.addView(create);
        query=input("جست‌وجوی گروه و برنامه");
        top.addView(query);
        content.addView(top,cardLp());
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results,cardLp());
        query.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){drawResults();}
            @Override public void afterTextChanged(Editable e){}
        });
        drawResults();
    }
    private void drawResults(){
        if(results==null)return;
        results.removeAllViews();
        String filter=query.getText().toString().trim().toLowerCase(Locale.ROOT);
        results.addView(text("دعوت‌نامه‌های من",18,true,TEXT));
        int pending=0;
        for(int i=0;i<invites.length();i++){
            JSONObject inv=invites.optJSONObject(i);if(inv==null)continue;
            if(!"pending".equals(inv.optString("status","pending")))continue;
            int inviteId=inv.optInt("id",0);
            if(inviteId<=0)continue;
            LinearLayout card=card();
            card.addView(text("دعوت به «"+UiSafeText.display(inv.optString("group_name","گروه"))+"»",14,true,TEXT));
            Button accept=primary("پذیرش دعوت");
            Button decline=normal("رد دعوت");
            accept.setOnClickListener(v->answerInvite(inviteId,true,accept));
            decline.setOnClickListener(v->answerInvite(inviteId,false,decline));
            card.addView(accept);card.addView(decline);
            results.addView(card,cardLp());pending++;
        }
        if(pending==0)results.addView(text("دعوت‌نامه در انتظار پاسخ ندارید.",11,false,MUTED),cardLp());
        results.addView(text("گروه‌ها",18,true,TEXT));
        int count=0;
        for(int i=0;i<groups.length()&&count<120;i++){
            JSONObject g=groups.optJSONObject(i);if(g==null)continue;
            String name=title(g),desc=g.optString("description","");
            if(!filter.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(filter)
                    &&!desc.toLowerCase(Locale.ROOT).contains(filter))continue;
            int groupId=g.optInt("id",0);
            if(groupId<=0)continue;
            LinearLayout card=card();
            card.addView(text(UiSafeText.display(name),16,true,TEXT));
            card.addView(text(UiSafeText.display(desc)+" · "+
                    JalaliDate.fa(g.optInt("member_count",0))+" عضو",11,false,MUTED));
            if(g.has("total_points"))card.addView(text("امتیاز گروه: "+
                    JalaliDate.fa(g.optLong("total_points",0)),12,true,ACCENT));
            Button open=normal("مشاهده گروه و اعضا");
            open.setOnClickListener(v->openGroup(groupId));
            card.addView(open);
            if(g.optBoolean("can_join",false)){
                Button join=primary("عضویت در گروه");
                join.setOnClickListener(v->post("pp_group_join",o(groupId),join,()->openGroup(groupId)));
                card.addView(join);
            }
            results.addView(card,cardLp());count++;
        }
        if(count==0)results.addView(text("گروهی با این نام پیدا نشد.",12,false,MUTED),cardLp());
        results.addView(text("برنامه‌های گروه‌ها",18,true,TEXT));
        int countEvents=0;
        for(int i=0;i<events.length()&&countEvents<120;i++){
            JSONObject e=events.optJSONObject(i);if(e==null)continue;
            String name=e.optString("title","برنامه"),group=e.optString("group_name","");
            if(!filter.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(filter)
                &&!group.toLowerCase(Locale.ROOT).contains(filter))continue;
            LinearLayout card=card();
            card.addView(text(UiSafeText.display(name),14,true,TEXT));
            card.addView(text("گروه "+UiSafeText.display(group)+" · "+
                JalaliDate.format(e.optString("event_date",""),true),11,false,MUTED));
            results.addView(card,cardLp());countEvents++;
        }
        if(countEvents==0)results.addView(text("برنامه‌ای یافت نشد.",11,false,MUTED),cardLp());
        VazirmatnLoader.apply(this,results);
    }
    private void createGroup(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText name=input("نام گروه");EditText description=input("توضیحات");
        android.widget.CheckBox privateGroup=new android.widget.CheckBox(this);
        privateGroup.setText("گروه خصوصی");privateGroup.setTextColor(TEXT);
        form.addView(name);form.addView(description);form.addView(privateGroup);
        new AlertDialog.Builder(this).setTitle("ایجاد گروه")
                .setView(form).setNegativeButton("انصراف",null)
                .setPositiveButton("ثبت",(d,w)->{
                    String n=name.getText().toString().trim();
                    if(n.length()<3||n.length()>80){toast("نام گروه باید بین ۳ تا ۸۰ کاراکتر باشد.");return;}
                    JSONObject data=obj("name",n,"description",
                        description.getText().toString().trim(),"visibility",
                        privateGroup.isChecked()?"private":"public");
                    post("pp_group_create",data,null,this::load);
                }).show();
    }
    private void openGroup(int id){
        selectedGroupId=id;
        final int request=++generation;
        content.removeAllViews();setSubtitle("در حال دریافت جزئیات گروه…",MUTED);
        io.execute(()->{
            try{
                JSONObject response=api.get("pp_group_detail",java.util.Collections.singletonMap("group_id",id));
                main.post(()->{
                    if(request!=generation||isFinishing())return;
                    renderDetails(id,response);
                });
            }catch(Exception e){
                main.post(()->{
                    if(request!=generation||isFinishing())return;
                    content.removeAllViews();addCard("خطا در نمایش گروه",e.getMessage());
                    Button back=normal("بازگشت");back.setOnClickListener(v->load());
                    content.addView(back);
                });
            }
        });
    }
    private void renderDetails(int id,JSONObject body){
        content.removeAllViews();
        JSONObject group=body.optJSONObject("group");
        if(group==null){addCard("داده نامعتبر","پاسخ گروه در دسترس نیست.");return;}
        setSubtitle(UiSafeText.display(title(group)),GREEN);
        LinearLayout summary=card();
        summary.addView(text(UiSafeText.display(title(group)),20,true,TEXT));
        summary.addView(text(UiSafeText.display(group.optString("description","")),12,false,MUTED));
        summary.addView(text("تعداد اعضا: "+JalaliDate.fa(group.optInt("member_count",0)),12,false,MUTED));
        if(group.has("total_points"))summary.addView(text("امتیاز گروه: "+
                JalaliDate.fa(group.optLong("total_points",0)),13,true,ACCENT));
        content.addView(summary,cardLp());
        boolean member=group.optBoolean("viewer_member",false);
        boolean admin=group.optBoolean("viewer_admin",false);
        if(member){
            Button chat=primary("گفت‌وگوی گروه");
            chat.setOnClickListener(v->{
                Intent i=new Intent(this,NativeGroupChatActivity.class);
                i.putExtra("group_id",id);i.putExtra("group_name",title(group));startActivity(i);
            });
            content.addView(chat,cardLp());
        }
        if(admin){
            Button invite=normal("دعوت عضو با جست‌وجو");
            invite.setOnClickListener(v->inviteMember(id));
            content.addView(invite,cardLp());
            Button link=normal("ایجاد لینک دعوت");
            link.setOnClickListener(v->createInviteLink(id));
            content.addView(link,cardLp());
            Button schedule=primary("برنامه‌ریزی رکاب‌زنی گروه");
            schedule.setOnClickListener(v->schedule(id));
            content.addView(schedule,cardLp());
        }
        Button scores=normal("امتیازها و رتبه گروه");
        scores.setOnClickListener(v->showScores(id));
        content.addView(scores,cardLp());
        JSONArray users=body.optJSONArray("members");
        if(users!=null){
            content.addView(text("اعضا",17,true,TEXT),cardLp());
            for(int i=0;i<users.length();i++){
                JSONObject u=users.optJSONObject(i);
                if(u!=null)content.addView(text(UiSafeText.display(u.optString("display_name","عضو"))+
                    (u.optBoolean("is_admin",false)?" · مدیر گروه":""),12,false,MUTED),cardLp());
            }
        }
        JSONArray events=body.optJSONArray("events");
        if(events!=null){
            content.addView(text("برنامه‌های آینده",17,true,TEXT),cardLp());
            for(int i=0;i<events.length();i++){
                JSONObject e=events.optJSONObject(i);
                if(e!=null)content.addView(text(UiSafeText.display(e.optString("title","برنامه"))+
                    " · "+JalaliDate.format(e.optString("event_date",""),true),12,false,MUTED),cardLp());
            }
        }
        Button back=normal("بازگشت به گروه‌ها");back.setOnClickListener(v->load());
        content.addView(back,cardLp());VazirmatnLoader.apply(this,content);
    }
    private void inviteMember(int id){
        EditText username=input("نام کاربری عضو");
        new AlertDialog.Builder(this).setTitle("دعوت عضو").setView(username)
            .setNegativeButton("انصراف",null)
            .setPositiveButton("ارسال دعوت",(d,w)->{
                String value=username.getText().toString().trim();
                if(value.isBlank())return;
                JSONObject p=obj("group_id",id,"username",value);
                post("pp_group_invite",p,null,()->openGroup(id));
            }).show();
    }
    private void createInviteLink(int id){
        io.execute(()->{
            try{
                JSONObject result=api.post("pp_group_invite_link",o(id));
                String url=result.optString("invite_url","");
                Uri uri=Uri.parse(url);
                if(!"https".equalsIgnoreCase(uri.getScheme())||
                     !("pedalpro.ir".equalsIgnoreCase(uri.getHost())||
                        "www.pedalpro.ir".equalsIgnoreCase(uri.getHost())))
                    throw new IllegalStateException("لینک دعوت معتبر نیست.");
                main.post(()->{
                    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                    if(clipboard!=null)clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("PedalPro group invitation",url));
                    toast("لینک اختصاصی دعوت کپی شد.");
                });
            }catch(Exception e){main.post(()->toast(e.getMessage()));}
        });
    }
    private void answerInvite(int invitationId,boolean accept,Button trigger){
        JSONObject p=obj("invitation_id",invitationId,"accept",accept);
        post("pp_group_invite_answer",p,trigger,this::load);
    }
    private void schedule(int id){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18),dp(8),dp(18),dp(8));
        EditText title=input("عنوان برنامه");EditText start=input("تاریخ و ساعت شمسی");
        EditText place=input("محل شروع");
        final String[] datetime={""};
        start.setFocusable(false);
        start.setOnClickListener(v->NativeJalaliDateTimePicker.show(this,datetime[0],true,
                (iso,jalali)->{datetime[0]=iso;start.setText(jalali);}));
        form.addView(title);form.addView(start);form.addView(place);
        new AlertDialog.Builder(this).setTitle("برنامه گروهی").setView(form)
          .setNegativeButton("انصراف",null)
          .setPositiveButton("ذخیره برنامه",(d,w)->{
              if(title.getText().toString().trim().isBlank()||datetime[0].isBlank()){
                  toast("عنوان و تاریخ برنامه الزامی‌اند.");return;
              }
              JSONObject p=obj("group_id",id,"title",title.getText().toString().trim(),
                      "event_date",datetime[0],
                      "meeting_location",place.getText().toString().trim(),
                      "request_id",UUID.randomUUID().toString());
              post("pp_group_event_save",p,null,()->openGroup(id));
          }).show();
    }
    private void showScores(int id){
        io.execute(()->{
            try{
                JSONObject result=api.get("pp_group_points",
                    java.util.Collections.singletonMap("group_id",id));
                main.post(()->{
                    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
                    box.setPadding(dp(16),dp(12),dp(16),dp(12));
                    box.addView(text("امتیاز کل: "+JalaliDate.fa(result.optLong("total_points",0)),16,true,TEXT));
                    JSONArray breakdown=result.optJSONArray("components");
                    if(breakdown!=null)for(int i=0;i<breakdown.length();i++){
                        JSONObject component=breakdown.optJSONObject(i);
                        if(component!=null)box.addView(text(
                            UiSafeText.display(component.optString("title","امتیاز"))+
                            ": "+JalaliDate.fa(component.optLong("points",0)),13,false,MUTED));
                    }
                    new AlertDialog.Builder(this).setTitle("امتیاز گروه").setView(box)
                        .setPositiveButton("بستن",null).show();
                });
            }catch(Exception e){main.post(()->toast(e.getMessage()));}
        });
    }
    private void post(String action,JSONObject values,Button trigger,Runnable after){
        if(trigger!=null)trigger.setEnabled(false);
        io.execute(()->{
            try{
                api.post(action,values);
                main.post(()->{
                    if(trigger!=null)trigger.setEnabled(true);
                    toast("عملیات ثبت شد.");after.run();
                });
            }catch(Exception e){main.post(()->{
                if(trigger!=null)trigger.setEnabled(true);toast("خطا: "+e.getMessage());
            });}
        });
    }
}
