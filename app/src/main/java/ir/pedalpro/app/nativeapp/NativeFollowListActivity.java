package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;

/** Read-only followers/following directory; follow toggle uses existing authorized API. */
public final class NativeFollowListActivity extends NativeScreenBase {
    public static final String EXTRA_USER_ID="user_id",EXTRA_KIND="kind";
    private int userId,offset;
    private String kind;
    private LinearLayout results;
    private boolean busy=false;

    @Override protected String screenTitle(){return "following".equals(kind)?"دنبال‌شوندگان":"دنبال‌کنندگان";}
    @Override protected void onScreenReady(){
        userId=getIntent().getIntExtra(EXTRA_USER_ID,0);
        kind=getIntent().getStringExtra(EXTRA_KIND);
        if(!"following".equals(kind))kind="followers";
        if(userId<=0){addCard("حساب نامعتبر","شناسه کاربر معتبر نیست.");return;}
        setSubtitle("افرادی که این حساب را دنبال می‌کنند یا دنبال شده‌اند.",MUTED);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results,cardLp());
        request(true);
    }
    private void request(boolean reset){
        if(busy)return;
        busy=true;
        if(reset)offset=0;
        LinkedHashMap<String,Object> args=new LinkedHashMap<>();
        args.put("user_id",userId);args.put("kind",kind);
        args.put("limit",30);args.put("offset",offset);
        io.execute(()->{
            try{
                JSONObject response=api.get("pp_follow_list",args);
                JSONArray people=response.optJSONArray("users");
                int count=people==null?0:people.length();
                main.post(()->{
                    if(isFinishing())return;
                    busy=false;
                    if(reset)results.removeAllViews();
                    if(count==0&&offset==0)results.addView(text("فهرست خالی است.",12,false,MUTED));
                    if(people!=null)for(int i=0;i<people.length();i++){
                        JSONObject u=people.optJSONObject(i);if(u==null)continue;
                        int uid=u.optInt("id",u.optInt("user_id",0));
                        if(uid<=0)continue;
                        LinearLayout card=card();
                        card.addView(text(UiSafeText.display(u.optString("display_name","عضو")),15,true,TEXT));
                        card.addView(text("@"+UiSafeText.display(u.optString("username","")),11,false,MUTED));
                        Button profile=normal("پروفایل");
                        profile.setOnClickListener(v->{
                            Intent open=new Intent(this,NativeMainActivity.class);
                            open.putExtra("page","member");open.putExtra("member_id",uid);
                            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(open);
                        });
                        card.addView(profile);
                        if(u.optBoolean("can_follow",false)){
                            Button follow=primary(u.optBoolean("viewer_follows",false)?"لغو دنبال‌کردن":"دنبال‌کردن");
                            follow.setOnClickListener(v->{
                                follow.setEnabled(false);
                                io.execute(()->{
                                    try{
                                        JSONObject responseToggle=api.post("pp_follow_toggle",new JSONObject().put("user_id",uid));
                                        main.post(()->{
                                            follow.setEnabled(true);
                                            follow.setText(responseToggle.optBoolean("following",false)?"لغو دنبال‌کردن":"دنبال‌کردن");
                                        });
                                    }catch(Exception e){main.post(()->{follow.setEnabled(true);toast(e.getMessage());});}
                                });
                            });
                            card.addView(follow);
                        }
                        results.addView(card,cardLp());
                    }
                    offset+=count;
                    if(response.optBoolean("has_more",false)){
                        Button more=normal("نمایش افراد بیشتر");
                        more.setOnClickListener(v->{
                            results.removeView(more);request(false);
                        });results.addView(more,cardLp());
                    }
                    VazirmatnLoader.apply(this,results);
                });
            }catch(Exception failure){main.post(()->{
                busy=false;
                if(reset)results.removeAllViews();
                addCard("خطا در دریافت فهرست",
                    "این بخش به پاسخ معتبر API فهرست دنبال‌کنندگان نیاز دارد. "+
                    UiSafeText.display(failure.getMessage()));
            });}
        });
    }
}
