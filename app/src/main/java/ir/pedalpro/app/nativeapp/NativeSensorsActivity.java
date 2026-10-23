package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import ir.pedalpro.app.CyclingSensorManager;

public final class NativeSensorsActivity extends NativeScreenBase {
    private static final int REQ_BLE=3201;

    private CyclingSensorManager cycling;
    private LinearLayout deviceList;
    private TextView cadence;
    private TextView power;
    private TextView status;

    @Override protected String screenTitle(){return "Cadence / Power";}

    @Override protected void onScreenReady(){
        cycling=new CyclingSensorManager(this,new CyclingSensorManager.Listener(){
            @Override public void onStatus(String json){main.post(()->applyStatus(json));}
            @Override public void onMeasurement(String json){main.post(()->applyMeasurement(json));}
        });
        loadLatest();
    }

    private void loadLatest(){
        setSubtitle("",MUTED);
        io.execute(()->{
            JSONObject latest=null;
            try{latest=api.get("pp_sensor_latest");}catch(Exception ignored){}
            JSONObject finalLatest=latest;
            main.post(()->render(finalLatest==null?null:finalLatest.optJSONObject("sample")));
        });
    }

    private void render(JSONObject latest){
        content.removeAllViews();

        LinearLayout grid1=new LinearLayout(this);
        grid1.setOrientation(LinearLayout.HORIZONTAL);
        grid1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout cadenceCard=metricCard("Cadence",
                latest!=null&&!latest.isNull("cadence_rpm")?String.format(java.util.Locale.US,"%.0f rpm",latest.optDouble("cadence_rpm",0)):"— rpm",
                "دور پدال در دقیقه");
        cadence=(TextView)cadenceCard.getChildAt(1);

        LinearLayout powerCard=metricCard("Power",
                latest!=null&&!latest.isNull("power_w")?Math.round(latest.optDouble("power_w",0))+" W":"— W",
                "توان لحظه‌ای");
        power=(TextView)powerCard.getChildAt(1);

        LinearLayout.LayoutParams gp1=new LinearLayout.LayoutParams(0,-2,1f);gp1.setMargins(dp(5),0,0,dp(10));
        LinearLayout.LayoutParams gp2=new LinearLayout.LayoutParams(0,-2,1f);gp2.setMargins(0,0,dp(5),dp(10));
        grid1.addView(cadenceCard,gp1);grid1.addView(powerCard,gp2);content.addView(grid1);

        LinearLayout grid2=new LinearLayout(this);
        grid2.setOrientation(LinearLayout.HORIZONTAL);
        grid2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout statusCard=metricCard("وضعیت","آماده اتصال","Bluetooth LE Cycling Speed/Cadence + Cycling Power");
        status=(TextView)statusCard.getChildAt(1);
        LinearLayout heartCard=metricCard("Heart Rate","فعلاً غیرفعال","اتصال ضربان را کنار گذاشته‌ایم؛ GPS و نقشه دست نخورده است.");
        grid2.addView(statusCard,gp1);grid2.addView(heartCard,gp2);content.addView(grid2);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button scan=primary("جستجوی Cadence / Power");
        Button disconnect=normal("قطع اتصال");
        scan.setOnClickListener(v->scan());
        disconnect.setOnClickListener(v->{cycling.disconnect();status.setText("آماده اتصال");});
        actions.addView(scan,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(50),1f);dlp.setMargins(dp(8),0,0,0);actions.addView(disconnect,dlp);
        content.addView(actions,cardLp());

        deviceList=new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        content.addView(deviceList,cardLp());
        deviceList.addView(empty("سنسور استاندارد Cadence یا Power پیدا نشده است."));

        VazirmatnLoader.apply(this,content);
    }

    private LinearLayout metricCard(String title,String value,String sub){
        LinearLayout c=card();
        c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.addView(text(title,14,true,TEXT));
        TextView v=text(value,27,true,Color.rgb(234,252,255));
        LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,-2);vlp.setMargins(0,dp(7),0,0);c.addView(v,vlp);
        TextView s=text(sub,11,false,MUTED);s.setLineSpacing(0,1.5f);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.setMargins(0,dp(4),0,0);c.addView(s,slp);
        return c;
    }

    private TextView empty(String body){
        TextView t=text(body,12,false,MUTED);
        t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(20),dp(12),dp(20));
        t.setBackground(round(Color.argb(7,255,255,255),14,Color.argb(18,255,255,255)));
        return t;
    }

    private boolean hasBlePerms(){
        if(Build.VERSION.SDK_INT<31)return ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        return ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }

    private void requestBle(){
        if(Build.VERSION.SDK_INT>=31){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ_BLE);
        }else{
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_BLE);
        }
    }

    private void scan(){
        if(!hasBlePerms()){requestBle();return;}
        deviceList.removeAllViews();
        deviceList.addView(empty("در حال جستجو…"));
        cycling.startScan();
        toast("جستجوی سنسور آغاز شد");
        main.postDelayed(this::paintDevices,1800);
        main.postDelayed(this::paintDevices,3500);
        main.postDelayed(this::paintDevices,6500);
    }

    private void paintDevices(){
        if(deviceList==null)return;
        try{
            JSONArray a=new JSONArray(cycling.devicesJson());
            deviceList.removeAllViews();
            if(a.length()==0){deviceList.addView(empty("سنسور استاندارد Cadence یا Power پیدا نشده است."));return;}
            for(int i=0;i<a.length();i++){
                JSONObject x=a.optJSONObject(i);if(x==null)continue;
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setPadding(dp(12),dp(12),dp(12),dp(12));
                row.setBackground(round(Color.argb(9,255,255,255),15,Color.argb(23,255,255,255)));

                LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.END);
                String name=x.optString("name","").isBlank()?"Cycling Sensor":x.optString("name");
                copy.addView(text(name,14,true,TEXT));
                String meta=(x.optBoolean("cadence")?"Cadence ":"")+(x.optBoolean("power")?"Power ":"")+" · "+x.optString("address","");
                copy.addView(text(meta,11,false,MUTED));
                row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));

                Button connect=normal("اتصال");
                String address=x.optString("address","");
                connect.setOnClickListener(v->{cycling.connect(address);toast("در حال اتصال سنسور...");});
                row.addView(connect,new LinearLayout.LayoutParams(dp(82),dp(42)));
                deviceList.addView(row,cardLp());
            }
            VazirmatnLoader.apply(this,deviceList);
        }catch(Exception e){deviceList.removeAllViews();deviceList.addView(empty("سنسور استاندارد Cadence یا Power پیدا نشده است."));}
    }

    private void applyStatus(String raw){
        try{
            JSONObject d=new JSONObject(raw);
            if(status!=null)status.setText(d.optBoolean("connected")?"متصل":"آماده اتصال");
            if(d.has("message")&&!d.optString("message","").isBlank())toast(d.optString("message"));
        }catch(Exception ignored){}
    }

    private void applyMeasurement(String raw){
        try{
            JSONObject d=new JSONObject(raw);
            double cadenceRpm=d.has("cadence_rpm")?d.optDouble("cadence_rpm",Double.NaN):d.optDouble("cadenceRpm",Double.NaN);
            double powerW=d.has("power_w")?d.optDouble("power_w",Double.NaN):d.optDouble("powerW",Double.NaN);
            if(cadence!=null&&Double.isFinite(cadenceRpm))cadence.setText(String.format(java.util.Locale.US,"%.0f rpm",cadenceRpm));
            if(power!=null&&Double.isFinite(powerW))power.setText(Math.round(powerW)+" W");
        }catch(Exception ignored){}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_BLE)toast(hasBlePerms()?"مجوز Bluetooth فعال شد.":"مجوز Bluetooth لازم است.");
    }

    @Override protected void onDestroy(){
        if(cycling!=null){try{cycling.stopScan();cycling.disconnect();}catch(Throwable ignored){}}
        super.onDestroy();
    }
}
