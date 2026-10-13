package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import ir.pedalpro.app.CyclingSensorManager;
import ir.pedalpro.app.HeartRateBleManager;

public final class NativeSensorsActivity extends NativeScreenBase {
    private static final int REQ_BLE=3201;

    private CyclingSensorManager cycling;
    private HeartRateBleManager heart;
    private LinearLayout cyclingDevices;
    private LinearLayout heartDevices;
    private TextView cadence;
    private TextView power;
    private TextView bpm;
    private TextView cyclingStatus;
    private TextView heartStatus;

    @Override protected String screenTitle(){return "سنسورها";}

    @Override protected void onScreenReady(){
        cycling=new CyclingSensorManager(this,new CyclingSensorManager.Listener(){
            @Override public void onStatus(String json){main.post(()->applyCycling(json));}
            @Override public void onMeasurement(String json){main.post(()->applyCycling(json));}
        });
        heart=new HeartRateBleManager(this,new HeartRateBleManager.Listener(){
            @Override public void onStatus(String json){main.post(()->applyHeart(json));}
            @Override public void onHeartRate(String json){main.post(()->applyHeart(json));}
        });

        render();
    }

    private void render(){
        content.removeAllViews();
        setSubtitle("Bluetooth Low Energy · Native",GREEN);

        LinearLayout live=card();
        live.addView(text("داده زنده",17,true,TEXT));
        cadence=text("Cadence: 0 rpm",16,true,ACCENT);
        power=text("Power: 0 W",16,true,ACCENT);
        bpm=text("Heart Rate: 0 bpm",16,true,ACCENT);
        live.addView(cadence);live.addView(power);live.addView(bpm);
        content.addView(live,cardLp());

        LinearLayout c=card();
        c.addView(text("Cadence / Power",16,true,TEXT));
        cyclingStatus=text("آماده جستجو",11,false,MUTED);
        c.addView(cyclingStatus);
        Button scanC=primary("جستجوی سنسورهای Cadence / Power");
        scanC.setOnClickListener(v->scanCycling());
        c.addView(scanC);
        cyclingDevices=new LinearLayout(this);cyclingDevices.setOrientation(LinearLayout.VERTICAL);
        c.addView(cyclingDevices);
        content.addView(c,cardLp());

        LinearLayout h=card();
        h.addView(text("Heart Rate",16,true,TEXT));
        heartStatus=text("آماده جستجو",11,false,MUTED);
        h.addView(heartStatus);
        Button scanH=primary("جستجوی سنسور ضربان");
        scanH.setOnClickListener(v->scanHeart());
        h.addView(scanH);
        heartDevices=new LinearLayout(this);heartDevices.setOrientation(LinearLayout.VERTICAL);
        h.addView(heartDevices);
        content.addView(h,cardLp());
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

    private void scanCycling(){
        if(!hasBlePerms()){requestBle();return;}
        cyclingDevices.removeAllViews();
        cycling.startScan();
        cyclingStatus.setText("در حال جستجو…");
        main.postDelayed(this::renderCyclingDevices,1800);
        main.postDelayed(this::renderCyclingDevices,4500);
        main.postDelayed(this::renderCyclingDevices,9000);
    }

    private void scanHeart(){
        if(!hasBlePerms()){requestBle();return;}
        heartDevices.removeAllViews();
        heart.startScan();
        heartStatus.setText("در حال جستجو…");
        main.postDelayed(this::renderHeartDevices,1800);
        main.postDelayed(this::renderHeartDevices,4500);
        main.postDelayed(this::renderHeartDevices,9000);
    }

    private void renderCyclingDevices(){
        try{
            JSONArray a=new JSONArray(cycling.devicesJson());
            cyclingDevices.removeAllViews();
            if(a.length()==0){cyclingDevices.addView(text("سنسور استانداردی پیدا نشد.",11,false,MUTED));return;}
            for(int i=0;i<a.length();i++){
                JSONObject d=a.optJSONObject(i);if(d==null)continue;
                String name=d.optString("name","").isBlank()?"سنسور دوچرخه":d.optString("name");
                String meta=(d.optBoolean("cadence")?"Cadence ":"")+(d.optBoolean("power")?"Power ":"")+" · RSSI "+d.optInt("rssi");
                Button b=normal(name+" · "+meta);
                String address=d.optString("address","");
                b.setOnClickListener(v->cycling.connect(address));
                cyclingDevices.addView(b);
            }
        }catch(Exception ignored){}
    }

    private void renderHeartDevices(){
        try{
            JSONArray a=new JSONArray(heart.devicesJson());
            heartDevices.removeAllViews();
            if(a.length()==0){heartDevices.addView(text("سنسور Heart Rate استانداردی پیدا نشد.",11,false,MUTED));return;}
            for(int i=0;i<a.length();i++){
                JSONObject d=a.optJSONObject(i);if(d==null)continue;
                String name=d.optString("name","").isBlank()?"سنسور ضربان":d.optString("name");
                Button b=normal(name+" · RSSI "+d.optInt("rssi"));
                String address=d.optString("address","");
                b.setOnClickListener(v->heart.connect(address));
                heartDevices.addView(b);
            }
        }catch(Exception ignored){}
    }

    private void applyCycling(String json){
        try{
            JSONObject d=new JSONObject(json);
            cadence.setText(String.format(java.util.Locale.US,"Cadence: %.0f rpm",d.optDouble("cadenceRpm",0)));
            power.setText("Power: "+d.optInt("powerW",0)+" W");
            String msg=d.optString("message",d.optBoolean("connected")?"متصل":"آماده");
            cyclingStatus.setText(msg+(d.optString("deviceName","").isBlank()?"":" · "+d.optString("deviceName")));
        }catch(Exception ignored){}
    }

    private void applyHeart(String json){
        try{
            JSONObject d=new JSONObject(json);
            bpm.setText("Heart Rate: "+d.optInt("bpm",0)+" bpm");
            String msg=d.optString("message",d.optBoolean("connected")?"متصل":"آماده");
            heartStatus.setText(msg+(d.optString("deviceName","").isBlank()?"":" · "+d.optString("deviceName")));
        }catch(Exception ignored){}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_BLE){
            toast(hasBlePerms()?"مجوز Bluetooth فعال شد.":"مجوز Bluetooth لازم است.");
        }
    }

    @Override protected void onDestroy(){
        if(cycling!=null){try{cycling.stopScan();cycling.disconnect();}catch(Throwable ignored){}}
        if(heart!=null){try{heart.stopScan();heart.disconnect();}catch(Throwable ignored){}}
        super.onDestroy();
    }
}
