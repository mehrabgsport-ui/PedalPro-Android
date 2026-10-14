package ir.pedalpro.app.nativeapp;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

public final class NativeAiActivity extends NativeScreenBase {
    private LinearLayout messages;
    private EditText input;
    private Spinner mode;
    private Button sendButton;
    private boolean conversationInteracted;

    @Override protected String screenTitle(){return "مشاور هوش مصنوعی";}

    @Override protected void onScreenReady(){
        setSubtitle("مشاور PedalPro · Native",GREEN);

        messages=new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        content.addView(messages,cardLp());

        LinearLayout composer=card();
        mode=new Spinner(this);
        String[] modes={"coach","analysis","nutrition","recovery"};
        mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));
        input=input("سؤال یا درخواست خود را بنویسید…");
        input.setMinLines(3);
        sendButton=primary("ارسال");
        sendButton.setOnClickListener(v->send());
        composer.addView(mode);
        composer.addView(input);
        composer.addView(sendButton);
        content.addView(composer,cardLp());

        loadHistory();
    }

    private void loadHistory(){
        io.execute(()->{
            try{
                JSONObject d=api.get("ai_history");
                JSONArray items=d.optJSONArray("items");
                if(items==null)items=d.optJSONArray("messages");
                JSONArray finalItems=items;
                main.post(()->{
                    // A late history response must not erase messages sent during loading.
                    if(!conversationInteracted)renderHistory(finalItems);
                });
            }catch(Exception ignored){}
        });
    }

    private void renderHistory(JSONArray items){
        messages.removeAllViews();
        if(items==null||items.length()==0){
            addBubble("PedalPro","برای تحلیل تمرین، مسیر، ریکاوری یا برنامه رکاب‌زنی پیام بفرستید.",false);
            return;
        }
        for(int i=0;i<items.length();i++){
            JSONObject m=items.optJSONObject(i);if(m==null)continue;
            String role=m.optString("role","assistant");
            String body=m.optString("content",m.optString("message",m.optString("body","")));
            addBubble("user".equals(role)?"شما":"PedalPro",body,"user".equals(role));
        }
    }

    private LinearLayout addBubble(String who,String body,boolean mine){
        LinearLayout box=card();
        box.addView(text(who,11,true,mine?GREEN:ACCENT));
        box.addView(text(body,14,false,TEXT));
        messages.addView(box,cardLp());
        return box;
    }

    private void send(){
        String prompt=input.getText().toString().trim();
        if(prompt.isBlank()||!sendButton.isEnabled())return;
        final String selectedMode=String.valueOf(mode.getSelectedItem());
        conversationInteracted=true;
        sendButton.setEnabled(false);
        sendButton.setText("در حال ارسال…");
        input.setText("");
        final LinearLayout pending=addBubble("شما",prompt,true);
        setSubtitle("در حال دریافت پاسخ…",ORANGE);
        io.execute(()->{
            try{
                JSONObject d=api.post("ai_chat",new JSONObject()
                        .put("message",prompt)
                        .put("mode",selectedMode)
                        .put("use_profile_context",true));
                String answer=d.optString("answer","پاسخی دریافت نشد.");
                main.post(()->{
                    addBubble("PedalPro",answer,false);
                    sendButton.setEnabled(true);
                    sendButton.setText("ارسال");
                    setSubtitle("مشاور PedalPro · Native",GREEN);
                });
            }catch(Exception e){
                main.post(()->{
                    messages.removeView(pending);
                    if(input.getText().toString().isBlank())input.setText(prompt);
                    sendButton.setEnabled(true);
                    sendButton.setText("ارسال");
                    setSubtitle("خطا در دریافت پاسخ",RED);
                    toast(e.getMessage());
                });
            }
        });
    }
}
