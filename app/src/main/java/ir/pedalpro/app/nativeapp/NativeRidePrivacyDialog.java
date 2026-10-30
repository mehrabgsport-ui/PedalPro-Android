package ir.pedalpro.app.nativeapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

final class NativeRidePrivacyDialog {
    interface Listener { void onSaved(boolean isPublic); }

    static void show(
            NativeRideDetailActivity activity,
            PedalProApiClient api,
            ExecutorService io,
            JSONObject ride,
            JSONObject boot,
            Listener listener) {

        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll=new ScrollView(activity);
        LinearLayout root=new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity,15),dp(activity,15),dp(activity,15),dp(activity,15));
        root.setBackground(round(activity,Color.rgb(10,29,45),18,Color.rgb(38,65,85)));
        scroll.addView(root);

        LinearLayout head=new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=text(activity,"انتشار و حریم مسیر",20,true,Color.rgb(245,247,251));
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        Button close=button(activity,"×",false);
        close.setOnClickListener(v->dialog.dismiss());
        head.addView(close,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,44)));
        root.addView(head);

        RadioGroup modes=new RadioGroup(activity);
        modes.setOrientation(RadioGroup.VERTICAL);
        RadioButton pub=privacyRadio(activity,"عمومی","همه اعضا مسیر و جزئیات این رکاب را می‌بینند.");
        RadioButton priv=privacyRadio(activity,"خصوصی","فقط خودت و اعضای انتخابی مسیر را می‌بینید.");
        modes.addView(pub);
        modes.addView(priv);
        root.addView(modes);
        (ride.optInt("public_tracking",0)==1?pub:priv).setChecked(true);

        LinearLayout privateBox=new LinearLayout(activity);
        privateBox.setOrientation(LinearLayout.VERTICAL);
        EditText search=input(activity,"جستجوی عضو...");
        privateBox.addView(search);

        LinearLayout membersHost=new LinearLayout(activity);
        membersHost.setOrientation(LinearLayout.VERTICAL);
        privateBox.addView(membersHost);

        TextView note=text(activity,"خصوصی بودن مسیر، کیلومتر رکاب‌زده را از مجموع فعالیت پروفایل حذف نمی‌کند.",11,false,Color.rgb(159,178,200));
        note.setPadding(0,dp(activity,8),0,dp(activity,8));
        privateBox.addView(note);
        root.addView(privateBox);

        Set<Integer> selected=new HashSet<>();
        JSONArray viewers=ride.optJSONArray("viewer_user_ids");
        if(viewers!=null)for(int i=0;i<viewers.length();i++)selected.add(viewers.optInt(i));

        Runnable paint=()->paintMembers(activity,membersHost,search.getText().toString(),selected,ride,boot);
        paint.run();

        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){paint.run();}
            @Override public void afterTextChanged(android.text.Editable e){}
        });

        modes.setOnCheckedChangeListener((g,id)->privateBox.setVisibility(pub.isChecked()?View.GONE:View.VISIBLE));
        privateBox.setVisibility(pub.isChecked()?View.GONE:View.VISIBLE);

        LinearLayout foot=new LinearLayout(activity);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save=button(activity,"ذخیره",true);
        Button cancel=button(activity,"انصراف",false);
        cancel.setOnClickListener(v->dialog.dismiss());
        foot.addView(save,new LinearLayout.LayoutParams(0,dp(activity,50),1f));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(activity,50),1f);
        cp.setMargins(dp(activity,8),0,0,0);
        foot.addView(cancel,cp);
        root.addView(foot);

        save.setOnClickListener(v->{
            boolean isPublic=pub.isChecked();
            save.setEnabled(false);
            save.setText("در حال ذخیره…");
            JSONArray ids=new JSONArray();
            if(!isPublic)for(Integer id:selected)ids.put(id);
            io.execute(()->{
                try{
                    api.post("toggle_tracking",new JSONObject()
                            .put("ride_id",ride.optInt("id",0))
                            .put("public_tracking",isPublic)
                            .put("viewer_user_ids",ids));
                    activity.runOnUiThread(()->{
                        dialog.dismiss();
                        activity.toast(isPublic?"مسیر عمومی شد.":"مسیر خصوصی شد؛ فقط افراد انتخابی آن را می‌بینند.");
                        if(listener!=null)listener.onSaved(isPublic);
                    });
                }catch(Exception e){
                    activity.runOnUiThread(()->{
                        save.setEnabled(true);
                        save.setText("ذخیره");
                        activity.toast(e.getMessage());
                    });
                }
            });
        });

        dialog.setContentView(scroll);
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width=WindowManager.LayoutParams.MATCH_PARENT;
            lp.height=Math.min(activity.getResources().getDisplayMetrics().heightPixels-dp(activity,40),dp(activity,720));
            lp.gravity=Gravity.CENTER;
            lp.dimAmount=.66f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        VazirmatnLoader.apply(activity,root);
    }

    private static void paintMembers(
            NativeRideDetailActivity a,
            LinearLayout host,
            String term,
            Set<Integer> selected,
            JSONObject ride,
            JSONObject boot) {
        host.removeAllViews();
        JSONArray members=boot==null?null:boot.optJSONArray("members");
        String needle=term==null?"":term.trim().toLowerCase(Locale.ROOT);
        int count=0;
        if(members!=null){
            for(int i=0;i<members.length();i++){
                JSONObject m=members.optJSONObject(i);
                if(m==null)continue;
                int id=m.optInt("id",0);
                if(id==ride.optInt("owner_user_id",0))continue;
                String display=m.optString("display_name",m.optString("username","عضو"));
                String username=m.optString("username","");
                if(!needle.isBlank()
                        &&!display.toLowerCase(Locale.ROOT).contains(needle)
                        &&!username.toLowerCase(Locale.ROOT).contains(needle))continue;

                CheckBox cb=new CheckBox(a);
                cb.setText(display+(username.isBlank()?"":"  @"+username));
                cb.setTextColor(Color.rgb(245,247,251));
                cb.setTextSize(12);
                cb.setChecked(selected.contains(id));
                cb.setOnCheckedChangeListener((b,checked)->{
                    if(checked)selected.add(id);else selected.remove(id);
                });
                host.addView(cb);
                count++;
            }
        }
        if(count==0){
            TextView empty=text(a,"عضوی پیدا نشد.",12,false,Color.rgb(159,178,200));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(a,10),dp(a,18),dp(a,10),dp(a,18));
            host.addView(empty);
        }
    }

    private static RadioButton privacyRadio(NativeRideDetailActivity a,String title,String sub){
        RadioButton r=new RadioButton(a);
        r.setText(title+"\n"+sub);
        r.setTextColor(Color.rgb(245,247,251));
        r.setTextSize(12);
        r.setPadding(dp(a,10),dp(a,11),dp(a,10),dp(a,11));
        r.setBackground(round(a,Color.argb(8,255,255,255),14,Color.argb(28,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(a,6),0,0);
        r.setLayoutParams(lp);
        return r;
    }

    private static EditText input(NativeRideDetailActivity a,String hint){
        EditText e=new EditText(a);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(159,178,200));
        e.setTextColor(Color.rgb(245,247,251));
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setPadding(dp(a,12),dp(a,11),dp(a,12),dp(a,11));
        e.setBackground(round(a,Color.argb(122,5,25,40),15,Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,50));
        lp.setMargins(0,dp(a,8),0,dp(a,8));
        e.setLayoutParams(lp);
        return e;
    }

    private static Button button(NativeRideDetailActivity a,String value,boolean primary){
        Button b=new Button(a);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setStateListAnimator(null);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        if(primary){
            b.setTextColor(Color.rgb(5,40,55));
            GradientDrawable g=new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(19,220,232),Color.rgb(37,200,244)});
            g.setCornerRadius(dp(a,17));
            b.setBackground(g);
        }else{
            b.setTextColor(Color.rgb(245,247,251));
            b.setBackground(round(a,Color.argb(18,255,255,255),17,Color.argb(46,255,255,255)));
        }
        return b;
    }

    private static TextView text(NativeRideDetailActivity a,String value,int sp,boolean bold,int color){
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.END);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private static GradientDrawable round(NativeRideDetailActivity a,int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a,radius));
        if(stroke!=0)g.setStroke(Math.max(1,dp(a,1)),stroke);
        return g;
    }

    private static int dp(NativeRideDetailActivity a,int v){
        return Math.round(v*a.getResources().getDisplayMetrics().density);
    }
}
